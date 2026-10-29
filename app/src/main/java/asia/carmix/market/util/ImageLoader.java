package asia.carmix.market.util;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.os.AsyncTask;
import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;
import android.widget.ImageView;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.security.MessageDigest;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import asia.carmix.market.api.Api;

/**
 * 极简图片加载器：内存 LruCache + 磁盘缓存 + 按尺寸采样解码。
 * 避免引入 Picasso/Glide（其新版依赖 okhttp4 / 需要 API 21+）。
 */
public class ImageLoader {

    private static ImageLoader sInstance;

    private final LruCache<String, Bitmap> mMemory;
    private final File mCacheDir;
    private final ExecutorService mPool = Executors.newFixedThreadPool(2);
    private final Handler mMain = new Handler(Looper.getMainLooper());
    private final Set<String> mInFlight = new HashSet<String>();

    private ImageLoader(Context ctx) {
        int maxMem = (int) (Runtime.getRuntime().maxMemory() / 1024);
        mMemory = new LruCache<String, Bitmap>(maxMem / 6) {
            @Override
            protected int sizeOf(String key, Bitmap value) {
                return value.getRowBytes() * value.getHeight() / 1024;
            }
        };
        File dir = new File(ctx.getApplicationContext().getCacheDir(), "images");
        if (!dir.exists()) dir.mkdirs();
        mCacheDir = dir;
        trimDiskCache();
    }

    /** 磁盘缓存上限：超过后按最旧优先清理到 3/4（车机存储小，防无限增长） */
    private static final long DISK_CAP_BYTES = 64L * 1024 * 1024;

    private void trimDiskCache() {
        try {
            File[] fs = mCacheDir.listFiles();
            if (fs == null) return;
            long total = 0;
            for (File f : fs) total += f.length();
            if (total <= DISK_CAP_BYTES) return;
            java.util.Arrays.sort(fs, new java.util.Comparator<File>() {
                @Override
                public int compare(File a, File b) {
                    return Long.signum(a.lastModified() - b.lastModified());
                }
            });
            long target = DISK_CAP_BYTES * 3 / 4;
            for (File f : fs) {
                if (total <= target) break;
                long len = f.length();
                if (f.delete()) total -= len;
            }
        } catch (Throwable ignored) {
        }
    }

    public static synchronized ImageLoader get(Context ctx) {
        if (sInstance == null) {
            sInstance = new ImageLoader(ctx.getApplicationContext());
            asia.carmix.market.App.setImageLoader(sInstance);
        }
        return sInstance;
    }

    /** 列表图标加载：64~96dp 视图按 256px 上限解码（原 400px，省一半以上位图内存） */
    public void display(ImageView view, String url, int placeholderRes) {
        display(view, url, placeholderRes, 256);
    }

    public void display(final ImageView view, final String url, final int placeholderRes, final int maxDim) {
        if (view == null) return;
        if (url == null || url.length() == 0) {
            view.setImageResource(placeholderRes);
            return;
        }
        final String abs = Api.absolutize(url);
        final String key = md5(abs);
        view.setTag(key);
        Bitmap cached = mMemory.get(key);
        if (cached != null) {
            view.setImageBitmap(cached);
            return;
        }
        view.setImageResource(placeholderRes);
        synchronized (mInFlight) {
            if (mInFlight.contains(key)) return;
            mInFlight.add(key);
        }
        mPool.execute(new Runnable() {
            @Override
            public void run() {
                Bitmap bmp = decode(key, abs, maxDim);
                synchronized (mInFlight) {
                    mInFlight.remove(key);
                }
                if (bmp == null) return;
                mMemory.put(key, bmp);
                mMain.post(new Runnable() {
                    @Override
                    public void run() {
                        if (key.equals(view.getTag())) {
                            view.setImageBitmap(mMemory.get(key));
                        }
                    }
                });
            }
        });
    }

    /** 供 App.onTrimMemory 调用：低内存时清空位图缓存（按需自动重建） */
    public void trimMemory(int level) {
        if (level >= android.content.ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN) {
            mMemory.evictAll();
        }
    }

    /** 同步解码：内存 → 磁盘 → 网络（下载后写磁盘） */
    private Bitmap decode(String key, String absUrl, int maxDim) {
        File f = new File(mCacheDir, key);
        if (f.exists()) {
            Bitmap b = sampleDecode(f.getAbsolutePath(), maxDim);
            if (b != null) return b;
        }
        okhttp3.Response resp = null;
        try {
            okhttp3.Request req = new okhttp3.Request.Builder().url(absUrl).build();
            resp = Api.client().newCall(req).execute();
            if (!resp.isSuccessful()) return null;
            InputStream in = resp.body().byteStream();
            File tmp = new File(mCacheDir, key + ".tmp");
            FileOutputStream out = new FileOutputStream(tmp);
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            out.close();
            if (!tmp.renameTo(f)) {
                tmp.delete();
            }
            // 每次写盘后顺手查一次容量上限（已在后台线程，开销可忽略）
            trimDiskCache();
        } catch (Throwable ignored) {
            return null;
        } finally {
            // 无论成功失败都释放连接（此前失败分支泄漏）
            if (resp != null) resp.close();
        }
        return sampleDecode(f.getAbsolutePath(), maxDim);
    }

    private Bitmap sampleDecode(String path, int maxDim) {
        try {
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(path, o);
            int sample = 1;
            while (o.outWidth / (sample * 2) >= maxDim || o.outHeight / (sample * 2) >= maxDim) {
                sample *= 2;
            }
            BitmapFactory.Options o2 = new BitmapFactory.Options();
            o2.inSampleSize = sample;
            Bitmap src = BitmapFactory.decodeFile(path, o2);
            // 图标统一圆角：修复"占位图圆角而真图直角"的观感不一致
            return roundCorners(src, maxDim <= 256 ? 18f : 12f);
        } catch (Throwable e) {
            return null;
        }
    }

    /** 位图圆角遮罩（BitmapShader 绘制，兼容 API 14；原位图回收） */
    private static Bitmap roundCorners(Bitmap src, float radiusPx) {
        if (src == null || src.isRecycled()) return src;
        try {
            Bitmap out = Bitmap.createBitmap(src.getWidth(), src.getHeight(),
                    android.graphics.Bitmap.Config.ARGB_8888);
            Canvas c = new Canvas(out);
            android.graphics.Paint p = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
            p.setShader(new android.graphics.BitmapShader(src,
                    android.graphics.Shader.TileMode.CLAMP, android.graphics.Shader.TileMode.CLAMP));
            c.drawRoundRect(new android.graphics.RectF(0, 0,
                    src.getWidth(), src.getHeight()), radiusPx, radiusPx, p);
            if (src != out) src.recycle();
            return out;
        } catch (Throwable t) {
            return src; // 失败退回直角原图
        }
    }

    public static String md5(String s) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(s.getBytes("UTF-8"));
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            return String.valueOf(s.hashCode());
        }
    }
}
