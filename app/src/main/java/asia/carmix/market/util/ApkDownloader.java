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
import java.util.HashMap;
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
 */
public class ApkDownloader {

    public interface Listener {
        void onProgress(String tag, int percent);      // percent<0 表示未知大小

        void onComplete(String tag, File file);

        void onError(String tag, String message);
    }

    private static final String CHANNEL_ID = "apk_download";
    private static final Map<String, Boolean> sActive = new HashMap<String, Boolean>();
    private static final android.os.Handler sMain =
            new android.os.Handler(android.os.Looper.getMainLooper());

    /** 是否正在下载（tag 通常是软件 id） */
    public static boolean isActive(String tag) {
        synchronized (sActive) {
            return Boolean.TRUE.equals(sActive.get(tag));
        }
    }

    public static File downloadsDir(Context ctx) {
        File dir = ctx.getApplicationContext()
                .getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
        if (dir == null) {
            dir = new File(ctx.getApplicationContext().getFilesDir(), "downloads");
        }
        if (!dir.exists()) dir.mkdirs();
        return dir;
    }

    public static void start(final Context ctx, final String tag, final String url,
                             final String fileName, final Listener listener) {
        if (isActive(tag)) {
            Toast.makeText(ctx, R.string.downloading_already, Toast.LENGTH_SHORT).show();
            return;
        }
        synchronized (sActive) {
            sActive.put(tag, Boolean.TRUE);
        }
        final Context app = ctx.getApplicationContext();
        final int notifId = fileName.hashCode();
        final NotificationManager nm = (NotificationManager) app.getSystemService(Context.NOTIFICATION_SERVICE);

        new Thread(new Runnable() {
            @Override
            public void run() {
                final File dir = downloadsDir(app);
                final File target = new File(dir, fileName);
                final File tmp = new File(dir, fileName + ".tmp");
                Exception error = null;
                for (int attempt = 0; attempt < 2; attempt++) {
                    boolean resume = attempt > 0 && tmp.exists() && tmp.length() > 0;
                    if (!resume && tmp.exists()) tmp.delete();
                    try {
                        executeDownload(app, nm, notifId, fileName, url, tmp, tag, listener, resume);
                        if (target.exists()) target.delete();
                        if (!tmp.renameTo(target)) {
                            throw new IllegalStateException("保存文件失败");
                        }
                        notifyProgress(app, nm, notifId, fileName, 100, true);
                        postComplete(app, tag, fileName, target, listener, nm, notifId);
                        markDone(tag);
                        return;
                    } catch (Exception e) {
                        error = e;
                        // 仅网络类错误重试（可续传）；其他错误直接放弃
                        if (!(e instanceof IOException)) break;
                    }
                }
                tmp.delete();
                notifyFail(app, nm, notifId, fileName);
                final Exception fe = error;
                if (listener != null) {
                    sMain.post(new Runnable() {
                        @Override
                        public void run() {
                            listener.onError(tag, fe == null || fe.getMessage() == null
                                    ? "下载失败" : fe.getMessage());
                        }
                    });
                }
                markDone(tag);
            }

            private void markDone(String t) {
                synchronized (sActive) {
                    sActive.put(t, Boolean.FALSE);
                }
            }
        }, "apk-download").start();
    }

    /** 单次下载执行（含续传），完成后 tmp 即完整文件。异常时关闭并抛出。 */
    private static void executeDownload(Context app, NotificationManager nm, int notifId,
                                        final String fileName, final String url, File tmp,
                                        final String tag, final Listener listener,
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

            InputStream in = resp.body().byteStream();
            FileOutputStream out = new FileOutputStream(tmp, resumed);
            byte[] buf = new byte[16384];
            int lastPct = -1;
            int n;
            while ((n = in.read(buf)) > 0) {
                out.write(buf, 0, n);
                done += n;
                int pct = total > 0 ? (int) (done * 100 / total) : -1;
                if (pct != lastPct && (pct < 0 || pct % 2 == 0 || pct == 100)) {
                    lastPct = pct;
                    notifyProgress(app, nm, notifId, fileName, pct, false);
                    if (listener != null) {
                        final int fpct = pct;
                        sMain.post(new Runnable() {
                            @Override
                            public void run() {
                                listener.onProgress(tag, fpct);
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

    private static void postComplete(final Context app, final String tag, final String fileName,
                                     final File file, final Listener listener,
                                     NotificationManager nm, int notifId) {
        // 通知改为“点按安装”
        Notification.Builder b = baseBuilder(app, "下载完成 · 点击安装")
                .setContentText(fileName);
        Intent install = installIntent(app, fileName);
        int piFlags = PendingIntent.FLAG_UPDATE_CURRENT
                | (Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0);
        b.setContentIntent(PendingIntent.getActivity(app, notifId, install, piFlags));
        nm.notify(notifId, b.build());

        if (listener != null) {
            sMain.post(new Runnable() {
                @Override
                public void run() {
                    listener.onComplete(tag, file);
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
                                       String fileName, int pct, boolean done) {
        Notification.Builder b = baseBuilder(app, done ? "下载完成" : "正在下载");
        if (pct >= 0) {
            b.setProgress(100, pct, false);
            b.setContentText(fileName + "  " + pct + "%");
        } else {
            b.setProgress(0, 0, true);
            b.setContentText(fileName);
        }
        nm.notify(id, b.build());
    }

    private static void notifyFail(Context app, NotificationManager nm, int id, String fileName) {
        Notification.Builder b = baseBuilder(app, "下载失败");
        b.setContentText(fileName);
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
