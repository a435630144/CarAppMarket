package asia.carmix.market.ui;

import android.app.Activity;
import android.content.Context;
import android.content.res.Configuration;
import android.os.Build;
import android.os.Bundle;

import asia.carmix.market.App;
import asia.carmix.market.R;

/**
 * 全部 Activity 的基类：昼夜模式注入。
 * - 手动深色/浅色：attachBaseContext 用 createConfigurationContext 覆盖 uiMode，
 *   让 @color 与 drawable-night 资源按所选模式解析（API 17+；14~16 回退浅色）
 * - 跟随系统：不覆盖，由系统 uiMode 决定（Android 10+ 深色主题自动生效）
 * - 深色时整体切到 AppThemeDark（Holo/Material 暗色基底，窗口底色仍走夜间资源）
 */
public abstract class BaseActivity extends Activity {

    @Override
    protected void attachBaseContext(Context newBase) {
        Context ctx = newBase;
        String mode = App.getThemeMode(newBase);
        if (!App.THEME_SYSTEM.equals(mode) && Build.VERSION.SDK_INT >= 17) {
            Configuration c = new Configuration(newBase.getResources().getConfiguration());
            int mask = Configuration.UI_MODE_NIGHT_MASK;
            c.uiMode = (c.uiMode & ~mask)
                    | (App.THEME_DARK.equals(mode)
                    ? Configuration.UI_MODE_NIGHT_YES
                    : Configuration.UI_MODE_NIGHT_NO);
            ctx = newBase.createConfigurationContext(c);
        }
        super.attachBaseContext(ctx);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        if (App.isDarkMode(this)) {
            setTheme(R.style.AppThemeDark);
        }
        super.onCreate(savedInstanceState);
    }
}
