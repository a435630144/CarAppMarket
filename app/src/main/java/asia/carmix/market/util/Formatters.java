package asia.carmix.market.util;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** 展示格式化：数量、日期。 */
public class Formatters {

    public static String count(long n) {
        if (n < 10000) return String.valueOf(n);
        if (n < 100000000) {
            double w = n / 10000.0;
            return (w >= 100 ? String.valueOf(Math.round(w)) : String.format(Locale.CHINA, "%.1f", w)) + "万";
        }
        return String.format(Locale.CHINA, "%.1f", n / 100000000.0) + "亿";
    }

    /** 字节 → "830 KB" / "44.4 MB" / "1.2 GB" */
    public static String bytes(long b) {
        if (b < 1024) return b + " B";
        double kb = b / 1024.0;
        if (kb < 1024) return oneDecimal(kb) + " KB";
        double mb = kb / 1024.0;
        if (mb < 1024) return oneDecimal(mb) + " MB";
        return oneDecimal(mb / 1024.0) + " GB";
    }

    /** 下载速度：bytes(bps) + "/s" */
    public static String speed(long bytesPerSec) {
        return bytes(bytesPerSec) + "/s";
    }

    private static String oneDecimal(double v) {
        return v >= 100 ? String.valueOf(Math.round(v))
                : String.format(Locale.CHINA, "%.1f", v);
    }

    /** 毫秒 → "2026-10-25" */
    public static String dateMillis(long ms) {
        if (ms <= 0) return "";
        SimpleDateFormat out = new SimpleDateFormat("yyyy-MM-dd", Locale.CHINA);
        return out.format(new Date(ms));
    }

    /** 短日期：同年显示 "10-24"，跨年显示 "2025-12-30"；供窄卡片用 */
    public static String shortDate(String iso) {
        String full = date(iso);
        if (full.length() != 10) return full;
        String thisYear = String.valueOf(java.util.Calendar.getInstance().get(java.util.Calendar.YEAR));
        if (full.startsWith(thisYear + "-")) {
            return full.substring(5);
        }
        return full;
    }

    /** ISO8601 → millis（解析失败返回 0），供排序比较 */
    public static long parseIso(String iso) {
        if (iso == null || iso.length() < 10) return 0;
        try {
            String s = iso.replace('T', ' ').replace("Z", "");
            if (s.contains(".")) s = s.substring(0, s.indexOf('.'));
            SimpleDateFormat in = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US);
            Date d = in.parse(s.length() > 19 ? s.substring(0, 19) : s);
            return d == null ? 0 : d.getTime();
        } catch (Exception e) {
            return 0;
        }
    }

    /** ISO8601 → "2026-10-25"；解析失败返回空串 */
    public static String date(String iso) {
        if (iso == null || iso.length() < 10) return "";
        try {
            String s = iso.replace('T', ' ').replace("Z", "");
            if (s.contains(".")) s = s.substring(0, s.indexOf('.'));
            SimpleDateFormat in = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US);
            Date d = in.parse(s.length() > 19 ? s.substring(0, 19) : s);
            SimpleDateFormat out = new SimpleDateFormat("yyyy-MM-dd", Locale.CHINA);
            return d == null ? "" : out.format(d);
        } catch (Exception e) {
            return iso.length() >= 10 ? iso.substring(0, 10) : "";
        }
    }
}
