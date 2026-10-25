package asia.carmix.market.util;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.drawable.Drawable;

/**
 * 无图标时的字母占位图：圆角底色（按名字哈希取色）+ 名称首字符。
 * 实例按名字缓存复用，避免列表快速滚动时反复分配。
 */
public class LetterDrawable extends Drawable {

    private static final android.util.LruCache<String, LetterDrawable> sCache =
            new android.util.LruCache<String, LetterDrawable>(64);

    /** 取缓存实例（无则创建），绑定处调用避免重复分配 */
    public static LetterDrawable obtain(String name) {
        String key = name == null ? "?" : name;
        synchronized (sCache) {
            LetterDrawable d = sCache.get(key);
            if (d == null) {
                d = new LetterDrawable(name);
                sCache.put(key, d);
            }
            return d;
        }
    }

    private static final int[] PALETTE = {
            0xFF60A5FA, 0xFF34D399, 0xFFF472B6, 0xFFA78BFA,
            0xFFFBBF24, 0xFF38BDF8, 0xFFFB923C, 0xFF4ADE80,
    };

    private final Paint mBg = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mTxt = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final String mLetter;

    public LetterDrawable(String name) {
        String letter = "?";
        if (name != null && name.length() > 0) {
            letter = name.substring(0, 1);
        }
        mLetter = letter;
        int color = PALETTE[Math.abs(name == null ? 0 : name.hashCode()) % PALETTE.length];
        mBg.setColor(color);
        mTxt.setColor(0xFFFFFFFF);
        mTxt.setTextAlign(Paint.Align.CENTER);
    }

    @Override
    public void draw(Canvas canvas) {
        int w = getBounds().width();
        int h = getBounds().height();
        if (w <= 0 || h <= 0) return;
        float r = Math.min(w, h) * 0.22f;
        canvas.drawRoundRect(0, 0, w, h, r, r, mBg);
        mTxt.setTextSize(Math.min(w, h) * 0.46f);
        // 文字垂直居中：baseline = 中心 + (descent-ascent)/2 - descent
        android.graphics.Paint.FontMetrics fm = mTxt.getFontMetrics();
        float centerY = h / 2f - (fm.ascent + fm.descent) / 2f;
        canvas.drawText(mLetter, w / 2f, centerY, mTxt);
    }

    @Override
    public void setAlpha(int alpha) {
        mBg.setAlpha(alpha);
        mTxt.setAlpha(alpha);
    }

    @Override
    public void setColorFilter(ColorFilter cf) {
        mBg.setColorFilter(cf);
        mTxt.setColorFilter(cf);
    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }
}
