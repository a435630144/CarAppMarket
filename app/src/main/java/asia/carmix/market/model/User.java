package asia.carmix.market.model;

import org.json.JSONObject;

public class User {

    public String id;
    public String username;
    public String email;
    public String role;
    public String vehicleBrand;
    public String vehicleModel;
    public String vehicleTrim;

    public static User fromJson(JSONObject o) {
        if (o == null) return null;
        User u = new User();
        u.id = o.optString("id");
        u.username = o.optString("username", "车友");
        u.email = o.optString("email", null);
        u.role = o.optString("role", "member");
        u.vehicleBrand = nullable(o, "vehicleBrand");
        u.vehicleModel = nullable(o, "vehicleModel");
        u.vehicleTrim = nullable(o, "vehicleTrim");
        return u;
    }

    private static String nullable(JSONObject o, String key) {
        String v = o.optString(key, null);
        return (v == null || v.length() == 0 || "null".equals(v)) ? null : v;
    }

    public JSONObject toJson() {
        JSONObject o = new JSONObject();
        try {
            o.put("id", id);
            o.put("username", username);
            o.put("email", email);
            o.put("role", role);
            o.put("vehicleBrand", vehicleBrand == null ? "" : vehicleBrand);
            o.put("vehicleModel", vehicleModel == null ? "" : vehicleModel);
            o.put("vehicleTrim", vehicleTrim == null ? "" : vehicleTrim);
        } catch (Exception ignored) {
        }
        return o;
    }
}
