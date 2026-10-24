package asia.carmix.market.model;

import org.json.JSONObject;

public class Review {

    public String id;
    public double rating;
    public String content = "";
    public String softwareVersion;
    public String originalContent; // 修改前的原文（灰字展示），未修改为 null
    public boolean isEdited;
    public boolean isHidden;
    public boolean isMine;
    public String createdAt;
    public int likeCount;
    public boolean isLiked;
    // 发布者
    public String username;
    public String vehicle; // 品牌+车型拼接

    public static Review fromJson(JSONObject o) {
        if (o == null) return null;
        Review r = new Review();
        r.id = o.optString("id");
        r.rating = o.optDouble("rating", 0);
        r.content = o.optString("content", "");
        r.softwareVersion = o.optString("softwareVersion", null);
        r.originalContent = o.optString("originalContent", null);
        r.isEdited = o.optBoolean("isEdited", false);
        r.isHidden = o.optBoolean("isHidden", false);
        r.isMine = o.optBoolean("isMine", false);
        r.createdAt = o.optString("createdAt", null);
        r.likeCount = o.optInt("likeCount", 0);
        r.isLiked = o.optBoolean("isLiked", false);
        JSONObject u = o.optJSONObject("user");
        if (u != null) {
            r.username = u.optString("username", "车友");
            String brand = u.optString("vehicleBrand", null);
            String model = u.optString("vehicleModel", null);
            StringBuilder sb = new StringBuilder();
            if (brand != null && brand.length() > 0 && !"null".equals(brand)) sb.append(brand);
            if (model != null && model.length() > 0 && !"null".equals(model)) {
                if (sb.length() > 0) sb.append(" ");
                sb.append(model);
            }
            r.vehicle = sb.length() > 0 ? sb.toString() : null;
        }
        if (r.username == null) r.username = "车友";
        return r;
    }
}
