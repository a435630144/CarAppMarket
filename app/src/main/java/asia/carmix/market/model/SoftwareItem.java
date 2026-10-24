package asia.carmix.market.model;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * 软件条目：列表接口与详情接口共用。
 * 所有字段防御式解析 —— 后端部分列允许为 null。
 */
public class SoftwareItem {

    public String id;
    public String name;
    public String description = "";
    public String category;
    public String type;              // 车机版 / 手机版
    public String version;
    public String author;
    public String systemRequirement;
    public String thumbnailUrl;      // 图标
    public List<String> screenshots = new ArrayList<String>();
    public String downloadUrl;       // 详情接口为 downloadDirectUrl（CDN 直链，跳过后端 302）
    public String downloadFileName;  // 期望保存的 APK 文件名
    public double averageRating;
    public int ratingCount;
    public boolean isHot;
    public String fileSize;          // 后端存的是字符串，如 "36.5MB"
    public long downloadCount;
    public long views;
    public boolean isOfficial;
    public String officialUrl;
    public String downloadLink;      // 外部下载直链（未上传安装包时）
    public String createdAt;

    public static SoftwareItem fromJson(JSONObject o) {
        if (o == null) return null;
        SoftwareItem s = new SoftwareItem();
        s.id = o.optString("id");
        s.name = o.optString("name", "");
        s.description = o.optString("description", "");
        s.category = o.optString("category", null);
        s.type = o.optString("type", null);
        s.version = o.optString("version", null);
        s.author = o.optString("author", null);
        s.systemRequirement = o.optString("systemRequirement", null);
        s.thumbnailUrl = o.optString("thumbnailUrl", null);
        // 详情接口返回 downloadDirectUrl（CDN 直链）优先；列表接口无此字段。
        // 注意后端 downloadUrl 常为相对路径（/api/uploads/...），必须补全域名
        String direct = o.optString("downloadDirectUrl", null);
        String plain = o.optString("downloadUrl", null);
        s.downloadUrl = absolutize(direct != null && direct.length() > 0 && !"null".equals(direct) ? direct : plain);
        s.downloadFileName = o.optString("downloadFileName", null);
        s.averageRating = o.optDouble("averageRating", 0);
        s.ratingCount = o.optInt("ratingCount", 0);
        s.isHot = o.optInt("isHot", 0) == 1;
        s.fileSize = o.optString("fileSize", null);
        s.downloadCount = o.optLong("downloadCount", 0);
        s.views = o.optLong("views", 0);
        s.isOfficial = o.optInt("isOfficial", 0) == 1;
        s.officialUrl = o.optString("officialUrl", null);
        s.downloadLink = absolutize(o.optString("downloadLink", null));
        s.createdAt = o.optString("createdAt", null);
        // 合集折叠路径（/software/hot）可能仍返回 JSON 字符串而非数组
        JSONArray arr = null;
        Object raw = o.opt("screenshotUrls");
        if (raw instanceof JSONArray) {
            arr = (JSONArray) raw;
        } else if (raw instanceof String) {
            try {
                arr = new JSONArray((String) raw);
            } catch (Exception ignored) {
            }
        }
        if (arr != null) {
            for (int i = 0; i < arr.length(); i++) {
                String u = arr.optString(i, null);
                if (u != null && u.length() > 0) s.screenshots.add(u);
            }
        }
        return s;
    }

    /** 未登录/无值时显示的版本与大小拼接 */
    public String metaLine() {
        StringBuilder sb = new StringBuilder();
        if (version != null && version.length() > 0) sb.append("v").append(version);
        if (fileSize != null && fileSize.length() > 0) {
            if (sb.length() > 0) sb.append(" · ");
            sb.append(fileSize);
        }
        if (sb.length() == 0) sb.append(category == null ? "应用" : category);
        return sb.toString();
    }

    /** 有无任何可下载地址 */
    public boolean downloadable() {
        return (downloadUrl != null && downloadUrl.length() > 0)
                || (downloadLink != null && downloadLink.length() > 0);
    }

    /** 相对 URL 补全为绝对地址（空/无效返回 null） */
    private static String absolutize(String url) {
        if (url == null || url.length() == 0 || "null".equals(url)) return null;
        if (url.startsWith("http://") || url.startsWith("https://")) return url;
        return asia.carmix.market.api.Api.SITE + (url.startsWith("/") ? url : "/" + url);
    }
}
