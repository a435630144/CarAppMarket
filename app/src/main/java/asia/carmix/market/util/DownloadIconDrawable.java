package asia.carmix.market.util;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.drawable.Drawable;

/**
 * 下载图标（Canvas 矢量绘制：圆形底 + 白色箭头入托盘）。
 * Android 4 无原生 SVG/VectorDrawable 支持，这里用 Paint 路径按比例绘制，
 * 效果等同矢量图，任意尺寸不失真，零依赖。
 */
public class DownloadIconDrawable extends Drawable {

    private final Paint mBg = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mFg = new Paint(Paint.ANTI_ALIAS_FLAG);

    public DownloadIconDrawable(int bgColor, int fgColor) {
        mBg.setColor(bgColor);
        mFg.setColor(fgColor);
        mFg.setStyle(Paint.Style.FILL);
    }

    @Override
    public void draw(Canvas canvas) {
        android.graphics.Rect b = getBounds();
        if (b.width() <= 0 || b.height() <= 0) return;
        float cx = b.exactCenterX();
        float cy = b.exactCenterY();
        float r = Math.min(b.width(), b.height()) / 2f;

        mBg.setStyle(Paint.Style.FILL);
        canvas.drawCircle(cx, cy, r, mBg);

        float u = r / 12f; // 归一化单位
        // 箭杆
        canvas.drawRect(cx - 1.6f * u, cy - 7.5f * u, cx + 1.6f * u, cy + 0.5f * u, mFg);
        // 箭头三角
        Path tri = new Path();
        tri.moveTo(cx - 5.5f * u, cy + 0.2f * u);
        tri.lineTo(cx + 5.5f * u, cy + 0.2f * u);
        tri.lineTo(cx, cy + 5.8f * u);
        tri.close();
        canvas.drawPath(tri, mFg);
        // 托盘
        mFg.setStyle(Paint.Style.STROKE);
        mFg.setStrokeWidth(2.4f * u);
        mFg.setStrokeCap(Paint.Cap.ROUND);
        canvas.drawLine(cx - 6.5f * u, cy + 9f * u, cx + 6.5f * u, cy + 9f * u, mFg);
        mFg.setStyle(Paint.Style.FILL);
    }

    @Override
    public void setAlpha(int alpha) {
        mBg.setAlpha(alpha);
        mFg.setAlpha(alpha);
    }

    @Override
    public void setColorFilter(ColorFilter cf) {
        mFg.setColorFilter(cf);
    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }
}
