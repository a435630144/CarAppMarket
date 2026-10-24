package asia.carmix.market.util;

import android.os.Build;
import android.util.Base64;

import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;

/**
 * 在 Android 4.1~4.4 上强制启用 TLS 1.2（系统支持但默认不启用）。
 * Conscrypt 加载失败时的兜底方案。
 */
public class Tls12SocketFactory extends SSLSocketFactory {

    private static final String[] TLS_V12_ONLY = {"TLSv1.2"};

    private final SSLSocketFactory mDelegate;

    public Tls12SocketFactory(SSLSocketFactory delegate) {
        this.mDelegate = delegate;
    }

    @Override
    public String[] getDefaultCipherSuites() {
        return mDelegate.getDefaultCipherSuites();
    }

    @Override
    public String[] getSupportedCipherSuites() {
        return mDelegate.getSupportedCipherSuites();
    }

    @Override
    public Socket createSocket(Socket s, String host, int port, boolean autoClose) throws IOException {
        return patch(mDelegate.createSocket(s, host, port, autoClose));
    }

    @Override
    public Socket createSocket(String host, int port) throws IOException {
        return patch(mDelegate.createSocket(host, port));
    }

    @Override
    public Socket createSocket(String host, int port, InetAddress localHost, int localPort) throws IOException {
        return patch(mDelegate.createSocket(host, port, localHost, localPort));
    }

    @Override
    public Socket createSocket(InetAddress host, int port) throws IOException {
        return patch(mDelegate.createSocket(host, port));
    }

    @Override
    public Socket createSocket(InetAddress address, int port, InetAddress localAddress, int localPort) throws IOException {
        return patch(mDelegate.createSocket(address, port, localAddress, localPort));
    }

    private Socket patch(Socket s) {
        if (s instanceof SSLSocket) {
            ((SSLSocket) s).setEnabledProtocols(TLS_V12_ONLY);
        }
        return s;
    }

    /**
     * 构造启用 TLS1.2 的 SSLSocketFactory + 匹配的 TrustManager。
     * 仅应在 Build.VERSION.SDK_INT 为 16~20 且 Conscrypt 未安装时调用。
     */
    public static SSLContext enableTls12PreLollipop()
            throws NoSuchAlgorithmException, KeyManagementException, KeyStoreException {
        SSLContext sc = SSLContext.getInstance("TLSv1.2");
        sc.init(null, null, new SecureRandom());
        return sc;
    }

    public static X509TrustManager defaultTrustManager()
            throws NoSuchAlgorithmException, KeyStoreException {
        TrustManagerFactory tmf = TrustManagerFactory
                .getInstance(TrustManagerFactory.getDefaultAlgorithm());
        tmf.init((java.security.KeyStore) null);
        for (TrustManager tm : tmf.getTrustManagers()) {
            if (tm instanceof X509TrustManager) {
                return (X509TrustManager) tm;
            }
        }
        return null;
    }

    @SuppressWarnings("unused")
    private static void unusedBase64Keep() {
        // 防止 R8 移除 android.util.Base64（部分老设备 ROM 缺类时提前暴露）
        byte[] b = Base64.decode("QQ==", Base64.DEFAULT);
    }
}
