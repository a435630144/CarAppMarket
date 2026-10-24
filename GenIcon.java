import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.File;

/**
 * 生成 Carmix 应用商店启动图标 PNG（蓝色渐变圆角底 + 白色小车 + 白色 A 字）。
 * 用法: java GenIcon <输出目录>
 */
public class GenIcon {
    public static void main(String[] args) throws Exception {
        File outDir = new File(args.length > 0 ? args[0] : ".");
        if (!outDir.exists()) outDir.mkdirs();
        int[] sizes = {48, 72, 96, 144, 192}; // mdpi hdpi xhdpi xxhdpi xxxhdpi
        String[] names = {"mdpi", "hdpi", "xhdpi", "xxhdpi", "xxxhdpi"};
        for (int i = 0; i < sizes.length; i++) {
            int s = sizes[i];
            BufferedImage img = new BufferedImage(s, s, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = img.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            // 蓝色渐变圆角背景
            GradientPaint gp = new GradientPaint(0, 0, new Color(0x3B, 0x82, 0xF6),
                    s, s, new Color(0x1D, 0x4E, 0xD8));
            g.setPaint(gp);
            g.fill(new RoundRectangle2D.Float(0, 0, s, s, s * 0.22f, s * 0.22f));

            // 白色小车
            g.setColor(Color.WHITE);
            float u = s / 100f; // 单位
            // 车身
            g.fill(new RoundRectangle2D.Float(18 * u, 46 * u, 64 * u, 26 * u, 10 * u, 10 * u));
            // 车顶
            g.fill(new RoundRectangle2D.Float(32 * u, 30 * u, 36 * u, 20 * u, 8 * u, 8 * u));
            // 车窗（挖出背景色）
            g.setPaint(gp);
            g.fill(new RoundRectangle2D.Float(36 * u, 33 * u, 28 * u, 14 * u, 4 * u, 4 * u));
            // 车轮
            g.setColor(Color.WHITE);
            g.fillOval((int) (26 * u), (int) (64 * u), (int) (16 * u), (int) (16 * u));
            g.fillOval((int) (58 * u), (int) (64 * u), (int) (16 * u), (int) (16 * u));
            g.setPaint(gp);
            g.fillOval((int) (30 * u), (int) (68 * u), (int) (8 * u), (int) (8 * u));
            g.fillOval((int) (62 * u), (int) (68 * u), (int) (8 * u), (int) (8 * u));
            g.dispose();

            File dir = new File(outDir, "mipmap-" + names[i]);
            if (!dir.exists()) dir.mkdirs();
            File png = new File(dir, "ic_launcher.png");
            ImageIO.write(img, "png", png);
            System.out.println("written " + png.getAbsolutePath());
        }
    }
}
