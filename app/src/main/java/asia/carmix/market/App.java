package asia.carmix.market;

import android.app.Application;
import android.os.Build;

import java.security.Security;

import org.conscrypt.Conscrypt;

/**
 * 全局 Application：在老系统上抢先安装 Conscrypt 安全提供者。
 *
 * 背景：<后端域名> 位于 Cloudflare 之后，最低要求 TLS 1.2。
 * - Android 4.1~4.4：系统 OpenSSL 支持 TLS 1.2 但默认不启用；
 * - Android 4.0.x：系统 OpenSSL 完全没有 TLS 1.2。
 * 把 Conscrypt 插到提供者列表首位后，OkHttp 的 SSLContext 即具备 TLS 1.2/1.3 能力。
 */
public class App extends Application {

    private static App sInstance;
    private static boolean sConscryptReady = false;

    @Override
    public void onCreate() {
        super.onCreate();
        sInstance = this;
        if (Build.VERSION.SDK_INT < 22) {
            try {
                Security.insertProviderAt(Conscrypt.newProvider(), 1);
                sConscryptReady = true;
            } catch (Throwable ignored) {
                // 极端情况下加载失败：退回 Tls12SocketFactory 方案（见 Api 初始化）
            }
        } else {
            sConscryptReady = true; // 5.0+ 系统 TLS 1.2 默认可用
        }
    }

    public static App get() {
        return sInstance;
    }

    public static boolean isConscryptReady() {
        return sConscryptReady;
    }
}
