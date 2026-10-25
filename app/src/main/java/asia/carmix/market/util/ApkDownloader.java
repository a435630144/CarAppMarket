package asia.carmix.market.util;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import asia.carmix.market.R;
import asia.carmix.market.provider.ApkFileProvider;

/**
 * APK 下载器：OkHttp（Conscrypt TLS）流式下载到应用外部私有目录，
 * 期间显示进度通知，完成后点通知即弹出安装。
 *
 * 不用系统 DownloadManager 的原因：它是系统进程，无法享受本进程注入的
 * Conscrypt TLS 1.2，在 Android 4.x 上连不上 Cloudflare（TLS 1.2 only）。
 *
 * 车机网络兼容：网络类失败自动重试一次，并从已下载的 .tmp 断点续传（HTTP Range）。
 * 任务注册表（Task）供下载管理页展示进度/速度/取消；CenterListener 全部主线程回调。
 */
public class ApkDownloader {

    public static final int STATE_RUNNING = 1;
    public static final int STATE_FAILED = 3;
    public static final int STATE_CANCELLED = 4;

    /** 下载任务（供管理页渲染） */
    public static class Task {
        public String tag;          // 软件 id
        public String displayName;  // 软件名
        public String fileName;     // 目标文件名
        public volatile int state = STATE_RUNNING;
        public volatile long done;
        public volatile long total = -1;   // -1 = 未知大小
        public volatile long speedBps;     // 采样速度 字节/秒
        public volatile boolean cancelled;
        public final long createdAt = System.currentTimeMillis();
    }

    /** 单次下载的 UI 回调（主线程） */
    public interface Listener {
        void onProgress(String tag, int percent);      // percent<0 表示未知大小

        void onComplete(String tag, File file);

        void onError(String tag, String message);
    }

    /** 下载中心监听：任一任务状态/进度变化（主线程，节流至进度采样点） */
    public interface CenterListener {
        void onTasksChanged();
    }

    private static final String CHANNEL_ID = "apk_download";
    private static final Map<String, Task> sTasks = new HashMap<String, Task>();
    /** 下载中文件名预留：并发同名下载不会算出同一个名字互相写坏 tmp */
    private static final java.util.Set<String> sReservedNames =
            new java.util.HashSet<String>();
    private static final List<CenterListener> sCenterListeners =
            new ArrayList<CenterListener>();
    private static final android.os.Handler sMain =
            new android.os.Handler(android.os.Looper.getMainLooper());

    // ================================ 任务注册表 ================================

    public static boolean isActive(String tag) {
        synchronized (sTasks) {
            Task t = sTasks.get(tag);
            return t != null && t.state == STATE_RUNNING;
        }
    }

    /** 管理页用：未完成任务（RUNNING 在前、FAILED 在后，按时间） */
    public static List<Task> openTasks() {
        List<Task> out = new ArrayList<Task>();
        synchronized (sTasks) {
            out.addAll(sTasks.values());
        }
        Collections.sort(out, new Comparator<Task>() {
            @Override
            public int compare(Task a, Task b) {
                if (a.state != b.state) {
                    return a.state == STATE_RUNNING ? -1 : 1;
                }
                return Long.signum(a.createdAt - b.createdAt);
            }
        });
        return out;
    }

    public static int activeCount() {
        int n = 0;
        synchronized (sTasks) {
            for (Task t : sTasks.values()) {
                if (t.state == STATE_RUNNING) n++;
            }
        }
        return n;
    }

    public static void cancel(String tag) {
        synchronized (sTasks) {
            Task t = sTasks.get(tag);
            if (t != null && t.state == STATE_RUNNING) {
                t.cancelled = true;
            }
        }
    }

    public static void addCenterListener(CenterListener l) {
        synchronized (sCenterListeners) {
            if (!sCenterListeners.contains(l)) sCenterListeners.add(l);
        }
    }

    public static void removeCenterListener(CenterListener l) {
        synchronized (sCenterListeners) {
            sCenterListeners.remove(l);
        }
    }

    private static void fireCenter() {
        final List<CenterListener> ls;
        synchronized (sCenterListeners) {
            ls = new ArrayList<CenterListener>(sCenterListeners);
        }
        sMain.post(new Runnable() {
            @Override
            public void run() {
                for (CenterListener l : ls) l.onTasksChanged();
            }
        });
    }

    private static void finishTask(Task t, int finalState) {
        // 终态统一释放文件名预留（DONE 后文件在磁盘上，后续靠存在性检查递增）
        releaseReserved(t.fileName);
        synchronized (sTasks) {
            if (finalState == STATE_CANCELLED || t.state == STATE_RUNNING) {
                if (finalState == STATE_CANCELLED) {
                    sTasks.remove(t.tag);
                } else if (finalState != STATE_FAILED) {
                    sTasks.remove(t.tag);
                } else {
                    t.state = STATE_FAILED;
                }
            }
        }
        fireCenter();
    }

    // ================================ 目录 ================================

    public static File downloadsDir(Context ctx) {
        File dir = ctx.getApplicationContext()
                .getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
        if (dir == null) {
            dir = new File(ctx.getApplicationContext().getFilesDir(), "downloads");
        }
        if (!dir.exists()) dir.mkdirs();
        return dir;
    }

    /** 已落盘的安装包（.apk，排除 .tmp 分片） */
    public static List<File> downloadedApks(Context ctx) {
        List<File> out = new ArrayList<File>();
        File[] fs = downloadsDir(ctx).listFiles();
        if (fs != null) {
            for (File f : fs) {
                String n = f.getName().toLowerCase(java.util.Locale.US);
                if (n.endsWith(".apk")) out.add(f);
            }
        }
        Collections.sort(out, new Comparator<File>() {
            @Override
            public int compare(File a, File b) {
                return Long.signum(b.lastModified() - a.lastModified());
            }
        });
        return out;
    }

    // ================================ 启动下载 ================================

    public static void start(final Context ctx, final String tag, final String displayName,
                             final String url, final String fileName, final Listener listener) {
        synchronized (sTasks) {
            Task old = sTasks.get(tag);
            if (old != null && old.state == STATE_RUNNING) {
                Toast.makeText(ctx, R.string.downloading_already, Toast.LENGTH_SHORT).show();
                return;
            }
        }
        final Context app = ctx.getApplicationContext();
        // 同名安装包已存在（重复下载同一软件）→ 加 (1)(2) 后缀，绝不覆盖旧文件，
        // 保证每次下载的产物都可被管理页追踪，不产生脱离管理的遗留文件；
        // 并发下载同一文件名时按预留集去重（否则两个任务会算出同一个名字）
        final String uniqueName = reserveFileName(app, fileName);
        final Task task = new Task();
        task.tag = tag;
        task.displayName = displayName;
        task.fileName = uniqueName;
        synchronized (sTasks) {
            sTasks.put(tag, task);
        }
        fireCenter();

        final int notifId = uniqueName.hashCode();
        final NotificationManager nm = (NotificationManager) app.getSystemService(Context.NOTIFICATION_SERVICE);

        new Thread(new Runnable() {
            @Override
            public void run() {
                final File dir = downloadsDir(app);
                final File target = new File(dir, uniqueName);
                final File tmp = new File(dir, uniqueName + ".tmp");
                Exception error = null;
                for (int attempt = 0; attempt < 2; attempt++) {
                    if (task.cancelled) break;
                    boolean resume = attempt > 0 && tmp.exists() && tmp.length() > 0;
                    if (!resume && tmp.exists()) tmp.delete();
                    try {
                        executeDownload(app, nm, notifId, task, url, tmp, listener, resume);
                        if (target.exists()) target.delete();
                        if (!tmp.renameTo(target)) {
                            throw new IllegalStateException("保存文件失败");
                        }
                        notifyProgress(app, nm, notifId, task, 100, true);
                        postComplete(app, task, target, listener, nm, notifId);
                        finishTask(task, 0); // DONE：从注册表移除，由文件列表接管
                        return;
                    } catch (Exception e) {
                        error = e;
                        if (task.cancelled) break;
                        // 仅网络类错误重试（可续传）；其他错误直接放弃
                        if (!(e instanceof IOException)) break;
                    }
                }
                if (task.cancelled) {
                    tmp.delete();
                    notifyState(app, nm, notifId, task.displayName, "已取消下载");
                    finishTask(task, STATE_CANCELLED);
                } else {
                    tmp.delete();
                    notifyState(app, nm, notifId, task.displayName, "下载失败");
                    finishTask(task, STATE_FAILED);
                    final Exception fe = error;
                    if (listener != null) {
                        sMain.post(new Runnable() {
                            @Override
                            public void run() {
                                listener.onError(task.tag, fe == null || fe.getMessage() == null
                                        ? "下载失败" : fe.getMessage());
                            }
                        });
                    }
                }
            }
        }, "apk-download").start();
    }

    /** 目标名冲突时生成 base(1).apk / base(2).apk …，并对进行中的任务预留名字 */
    static String reserveFileName(Context ctx, String desired) {
        File dir = downloadsDir(ctx);
        String candidate = desired;
        String base = desired;
        String ext = "";
        int dot = desired.lastIndexOf('.');
        if (dot > 0) {
            base = desired.substring(0, dot);
            ext = desired.substring(dot);
        }
        synchronized (sReservedNames) {
            int i = 0;
            while (true) {
                if (!new File(dir, candidate).exists()
                        && !sReservedNames.contains(candidate)) {
                    sReservedNames.add(candidate);
                    return candidate;
                }
                i++;
                candidate = base + "(" + i + ")" + ext;
            }
        }
    }

    private static void releaseReserved(String fileName) {
        synchronized (sReservedNames) {
            sReservedNames.remove(fileName);
        }
    }

    /** 单次下载执行（含续传），完成后 tmp 即完整文件。异常时关闭并抛出。 */
    private static void executeDownload(Context app, NotificationManager nm, int notifId,
                                        final Task task, final String url, File tmp,
                                        final Listener listener,
                                        boolean resume) throws IOException {
        long existing = resume ? tmp.length() : 0;
        okhttp3.Request.Builder rb = new okhttp3.Request.Builder().url(url);
        if (existing > 0) {
            rb.header("Range", "bytes=" + existing + "-");
        }
        okhttp3.Response resp = asia.carmix.market.api.Api.client().newCall(rb.build()).execute();
        try {
            int code = resp.code();
            boolean resumed = code == 206 && existing > 0;
            if (code != 200 && !resumed) {
                throw new IOException("下载失败: HTTP " + code);
            }
            if (!resumed) existing = 0;
            long bodyLen = resp.body().contentLength();
            final long total = bodyLen >= 0 ? bodyLen + existing : -1;
            long done = existing;
            task.done = done;
            task.total = total;

            long sampleAt = System.currentTimeMillis();
            long sampleBytes = done;

            InputStream in = resp.body().byteStream();
            FileOutputStream out = new FileOutputStream(tmp, resumed);
            byte[] buf = new byte[16384];
            int lastPct = -1;
            int n;
            while ((n = in.read(buf)) > 0) {
                if (task.cancelled) {
                    throw new IOException("已取消");
                }
                out.write(buf, 0, n);
                done += n;
                task.done = done;
                int pct = total > 0 ? (int) (done * 100 / total) : -1;
                if (pct != lastPct && (pct < 0 || pct % 2 == 0 || pct == 100)) {
                    lastPct = pct;
                    long now = System.currentTimeMillis();
                    if (now - sampleAt >= 500) {
                        task.speedBps = (long) ((done - sampleBytes) * 1000.0 / (now - sampleAt));
                        sampleAt = now;
                        sampleBytes = done;
                    }
                    notifyProgress(app, nm, notifId, task, pct, false);
                    fireCenter();
                    if (listener != null) {
                        final int fpct = pct;
                        sMain.post(new Runnable() {
                            @Override
                            public void run() {
                                listener.onProgress(task.tag, fpct);
                            }
                        });
                    }
                }
            }
            out.close();
            in.close();
            if (total > 0 && done < total) {
                throw new IOException("下载不完整，请重试");
            }
        } finally {
            resp.close();
        }
    }

    private static void postComplete(final Context app, final Task task, final File file,
                                     final Listener listener,
                                     NotificationManager nm, int notifId) {
        // 通知改为“点按安装”
        Notification.Builder b = baseBuilder(app, "下载完成 · 点击安装")
                .setContentText(task.fileName);
        Intent install = installIntent(app, task.fileName);
        int piFlags = PendingIntent.FLAG_UPDATE_CURRENT
                | (Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0);
        b.setContentIntent(PendingIntent.getActivity(app, notifId, install, piFlags));
        nm.notify(notifId, b.build());

        if (listener != null) {
            sMain.post(new Runnable() {
                @Override
                public void run() {
                    listener.onComplete(task.tag, file);
                }
            });
        }
    }

    /**
     * 安装 Intent 双轨：
     * - API >= 24：必须 content://（targetSdk>=24 时 file:// 会触发 FileUriExposedException）
     * - API <  24：老 ROM 的系统安装器对 content:// 支持不稳，用 file:// 更可靠
     *   （FileUriExposedException 检查只存在于 7.0+ 系统，低版本无此限制）
     */
    public static Intent installIntent(Context ctx, String fileName) {
        Uri uri;
        if (Build.VERSION.SDK_INT >= 24) {
            uri = ApkFileProvider.uriFor(fileName);
        } else {
            uri = Uri.fromFile(new File(downloadsDir(ctx), fileName));
        }
        Intent i = new Intent(Intent.ACTION_VIEW);
        i.setDataAndType(uri, "application/vnd.android.package-archive");
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        return i;
    }

    private static void notifyProgress(Context app, NotificationManager nm, int id,
                                       Task task, int pct, boolean done) {
        Notification.Builder b = baseBuilder(app, done ? "下载完成" : "正在下载");
        String label = task.displayName == null ? task.fileName : task.displayName;
        if (pct >= 0) {
            b.setProgress(100, pct, false);
            b.setContentText(label + "  " + pct + "% · " + Formatters.speed(task.speedBps));
        } else {
            b.setProgress(0, 0, true);
            b.setContentText(label + "  " + Formatters.speed(task.speedBps));
        }
        nm.notify(id, b.build());
    }

    private static void notifyState(Context app, NotificationManager nm, int id,
                                    String displayName, String stateText) {
        Notification.Builder b = baseBuilder(app, stateText);
        b.setContentText(displayName);
        nm.notify(id, b.build());
    }

    private static Notification.Builder baseBuilder(Context app, String title) {
        NotificationManager nm = (NotificationManager) app.getSystemService(Context.NOTIFICATION_SERVICE);
        Notification.Builder b;
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel ch = nm.getNotificationChannel(CHANNEL_ID);
            if (ch == null) {
                ch = new NotificationChannel(CHANNEL_ID, "APK 下载", NotificationManager.IMPORTANCE_LOW);
                nm.createNotificationChannel(ch);
            }
            b = new Notification.Builder(app, CHANNEL_ID);
        } else {
            b = new Notification.Builder(app);
        }
        // Android 13+ 缺少 smallIcon 会直接抛 IllegalArgumentException
        b.setSmallIcon(R.drawable.ic_notify);
        b.setContentTitle(title);
        b.setOnlyAlertOnce(true);
        b.setAutoCancel(true);
        return b;
    }
}
