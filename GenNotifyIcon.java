import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.io.File;

/** 生成状态栏通知小图标（白色下载箭头，仅 alpha 通道风格） */
public class GenNotifyIcon {
    public static void main(String[] args) throws Exception {
        File outDir = new File(args[0]);
        if (!outDir.exists()) outDir.mkdirs();
        Object[][] sizes = {{24, "mdpi"}, {36, "hdpi"}, {48, "xhdpi"}, {72, "xxhdpi"}, {96, "xxxhdpi"}};
        for (Object[] si : sizes) {
            int s = (Integer) si[0];
            BufferedImage img = new BufferedImage(s, s, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = img.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setColor(Color.WHITE);
            float u = s / 24f;
            // 粗箭头：竖杆
            g.fill(new Rectangle2D.Float(10 * u, 3 * u, 4 * u, 9 * u));
            // 箭头头部（三角）
            Path2D tri = new Path2D.Float();
            tri.moveTo(5 * u, 11 * u);
            tri.lineTo(19 * u, 11 * u);
            tri.lineTo(12 * u, 19 * u);
            tri.closePath();
            g.fill(tri);
            // 底部托盘线
            g.setStroke(new BasicStroke(2.4f * u, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.draw(new Line2D.Float(4 * u, 22 * u, 20 * u, 22 * u));
            g.dispose();
            File dir = new File(outDir, "drawable-" + si[1]);            if (!dir.exists()) dir.mkdirs();
            ImageIO.write(img, "png", new File(dir, "ic_notify.png"));
        }
        System.out.println("ok");
    }
}
