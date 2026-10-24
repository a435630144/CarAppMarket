package asia.carmix.market;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONObject;

import asia.carmix.market.model.User;

/**
 * 登录态：token 与用户信息保存在 SharedPreferences（对齐 Web 端 localStorage 的 community_token）。
 */
public class Session {

    private static final String PREFS = "session";
    private static final String K_TOKEN = "token";
    private static final String K_USER = "user";

    private static String sToken;
    private static User sUser;
    private static boolean sLoaded = false;

    private static void load(Context ctx) {
        if (sLoaded) return;
        SharedPreferences sp = ctx.getApplicationContext()
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        sToken = sp.getString(K_TOKEN, null);
        String json = sp.getString(K_USER, null);
        if (json != null) {
            try {
                sUser = User.fromJson(new JSONObject(json));
            } catch (Exception ignored) {
                sUser = null;
            }
        }
        if (sToken == null) sUser = null;
        sLoaded = true;
    }

    public static synchronized String getToken(Context ctx) {
        load(ctx);
        return sToken;
    }

    public static synchronized User getUser(Context ctx) {
        load(ctx);
        return sUser;
    }

    public static synchronized boolean isLoggedIn(Context ctx) {
        return getToken(ctx) != null;
    }

    public static synchronized void save(Context ctx, String token, User user) {
        sToken = token;
        sUser = user;
        ctx.getApplicationContext()
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putString(K_TOKEN, token)
                .putString(K_USER, user == null ? null : user.toJson().toString())
                .apply();
    }

    public static synchronized void clear(Context ctx) {
        sToken = null;
        sUser = null;
        sLoaded = true;
        ctx.getApplicationContext()
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .clear()
                .apply();
    }
}
