package asia.carmix.market.util;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
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
    }

    public static synchronized ImageLoader get(Context ctx) {
        if (sInstance == null) sInstance = new ImageLoader(ctx.getApplicationContext());
        return sInstance;
    }

    /** 列表图标加载 */
    public void display(ImageView view, String url, int placeholderRes) {
        display(view, url, placeholderRes, 400);
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
            return BitmapFactory.decodeFile(path, o2);
        } catch (Throwable e) {
            return null;
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
