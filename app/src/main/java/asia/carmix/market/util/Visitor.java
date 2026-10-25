package asia.carmix.market.util;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.UUID;

/** 访客稳定标识：首次生成 UUID 存本地，用于「需要升级」的匿名幂等反馈。 */
public class Visitor {

    private static final String PREFS = "visitor";
    private static String sId;

    public static synchronized String id(Context ctx) {
        if (sId != null) return sId;
        SharedPreferences sp = ctx.getApplicationContext()
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        sId = sp.getString("id", null);
        if (sId == null || sId.length() == 0) {
            sId = UUID.randomUUID().toString();
            sp.edit().putString("id", sId).apply();
        }
        return sId;
    }
}
