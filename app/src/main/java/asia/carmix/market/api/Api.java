package asia.carmix.market.api;

import android.content.Context;

import asia.carmix.market.BuildConfig;
import android.text.TextUtils;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.SSLContext;

import asia.carmix.market.App;
import asia.carmix.market.Session;
import asia.carmix.market.model.Review;
import asia.carmix.market.model.SoftwareItem;
import asia.carmix.market.model.User;
import asia.carmix.market.util.Tls12SocketFactory;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * 后端 API 客户端（全部方法为同步阻塞调用，必须在后台线程执行）。
 */
public class Api {

    /** 站点根地址（图片等静态资源可能直接挂在站点域名下） */
    public static final String SITE = BuildConfig.BACKEND_URL; // 构建期注入，仓库不含真实地址
    /** 后端 globalPrefix 为 api */
    public static final String BASE = SITE + "/api";

    private static final MediaType JSON_TYPE = MediaType.parse("application/json; charset=utf-8");

    private static OkHttpClient sClient;

    public static synchronized OkHttpClient client() {
        if (sClient == null) {
            OkHttpClient.Builder b = new OkHttpClient.Builder()
                    .connectTimeout(15, TimeUnit.SECONDS)
                    .readTimeout(30, TimeUnit.SECONDS)
                    .writeTimeout(30, TimeUnit.SECONDS)
                    // 车机系统 DNS 抖动兜底：失败时回落公共 DNS
                    .dns(new asia.carmix.market.util.FallbackDns());
            // Android 4.1~4.4：Conscrypt 未就绪时，强制 TLS1.2（Cloudflare 最低要求）
            if (!App.isConscryptReady()
                    && android.os.Build.VERSION.SDK_INT >= 16
                    && android.os.Build.VERSION.SDK_INT < 21) {
                try {
                    SSLContext sc = Tls12SocketFactory.enableTls12PreLollipop();
                    b.sslSocketFactory(new Tls12SocketFactory(sc.getSocketFactory()),
                            Tls12SocketFactory.defaultTrustManager());
                } catch (Throwable ignored) {
                }
            }
            sClient = b.build();
        }
        return sClient;
    }

    // ================================ 基础请求 ================================

    private static String request(Context ctx, String method, String path, JSONObject body) throws ApiException {
        Request.Builder rb = new Request.Builder().url(BASE + path);
        String token = Session.getToken(ctx);
        if (token != null) rb.header("Authorization", "Bearer " + token);
        rb.header("Accept", "application/json");
        if (body != null || "POST".equals(method)) {
            RequestBody rbBody = RequestBody.create(JSON_TYPE, body == null ? new JSONObject().toString() : body.toString());
            rb.method(method, rbBody);
        } else {
            rb.method(method, null);
        }
        Response resp = null;
        try {
            resp = client().newCall(rb.build()).execute();
            String text = resp.body() == null ? "" : resp.body().string();
            if (!resp.isSuccessful()) {
                // 登录态过期：清掉本地 token，提示重新登录（后续请求按游客身份进行）
                if (resp.code() == 401) {
                    Session.clear(ctx);
                    throw new ApiException(401, "登录已过期，请重新登录");
                }
                throw new ApiException(resp.code(), errorMessage(text, "请求失败 (" + resp.code() + ")"));
            }
            return text;
        } catch (IOException e) {
            throw new ApiException(0, "网络连接失败，请检查网络后重试");
        } finally {
            if (resp != null) {
                try {
                    resp.close();
                } catch (Throwable ignored) {
                }
            }
        }
    }

    private static String errorMessage(String body, String fallback) {
        try {
            JSONObject o = new JSONObject(body);
            Object msg = o.opt("message");
            if (msg instanceof JSONArray && ((JSONArray) msg).length() > 0) {
                return ((JSONArray) msg).optString(0, fallback);
            }
            if (msg != null && msg.toString().length() > 0) return msg.toString();
        } catch (Exception ignored) {
        }
        return fallback;
    }

    private static JSONObject getJson(Context ctx, String path) throws ApiException {
        try {
            return new JSONObject(request(ctx, "GET", path, null));
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            throw new ApiException(0, "数据解析失败");
        }
    }

    private static JSONObject postJson(Context ctx, String path, JSONObject body) throws ApiException {
        try {
            return new JSONObject(request(ctx, "POST", path, body));
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            throw new ApiException(0, "数据解析失败");
        }
    }

    // ================================ 软件模块 ================================

    public static class ListResult {
        public List<SoftwareItem> items = new ArrayList<SoftwareItem>();
        public int total;
        public int page;
        public int totalPages;
        public String raw;   // 服务器原始 JSON（供离线缓存落盘）
    }

    public static ListResult listSoftware(Context ctx, int page, int limit, String category) throws ApiException {
        return listSoftware(ctx, page, limit, category, null);
    }

    public static ListResult listSoftware(Context ctx, int page, int limit,
                                          String category, String keyword) throws ApiException {
        StringBuilder sb = new StringBuilder("/software?page=").append(page)
                .append("&limit=").append(limit);
        if (!TextUtils.isEmpty(category)) sb.append("&category=").append(urlEncode(category));
        if (!TextUtils.isEmpty(keyword)) sb.append("&keyword=").append(urlEncode(keyword.trim()));
        JSONObject o = getJson(ctx, sb.toString());
        return parseList(o);
    }

    /** 解析列表响应；raw 保留原始 JSON 供磁盘缓存 */
    public static ListResult parseList(JSONObject o) {
        ListResult r = new ListResult();
        r.total = o.optInt("total", 0);
        r.page = o.optInt("page", 1);
        r.totalPages = o.optInt("totalPages", 1);
        JSONArray data = o.optJSONArray("data");
        if (data != null) {
            for (int i = 0; i < data.length(); i++) {
                SoftwareItem item = SoftwareItem.fromJson(data.optJSONObject(i));
                if (item != null) r.items.add(item);
            }
        }
        r.raw = o.toString();
        return r;
    }

    public static List<String> categories(Context ctx) throws ApiException {
        List<String> out = new ArrayList<String>();
        // 后端直接返回 [{id,name,order,...}] 数组
        String text;
        try {
            text = request(ctx, "GET", "/software/categories", null);
        } catch (ApiException e) {
            throw e;
        }
        try {
            JSONArray arr = new JSONArray(text);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject c = arr.optJSONObject(i);
                String name = c == null ? arr.optString(i) : c.optString("name");
                if (name != null && name.length() > 0) out.add(name);
            }
        } catch (Exception e) {
            throw new ApiException(0, "分类数据解析失败");
        }
        return out;
    }

    public static List<SoftwareItem> hot(Context ctx) throws ApiException {
        List<SoftwareItem> out = new ArrayList<SoftwareItem>();
        try {
            String text = request(ctx, "GET", "/software/hot", null);
            JSONArray arr = new JSONArray(text);
            for (int i = 0; i < arr.length(); i++) {
                SoftwareItem s = SoftwareItem.fromJson(arr.optJSONObject(i));
                if (s != null) out.add(s);
            }
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            throw new ApiException(0, "数据解析失败");
        }
        return out;
    }

    public static SoftwareItem detail(Context ctx, String id) throws ApiException {
        return SoftwareItem.fromJson(getJson(ctx, "/software/" + id));
    }

    public static class DownloadInfo {
        public String url;
        public String fileName;
    }

    /** 计数 + 拿下载地址：POST /software/:id/download */
    public static DownloadInfo requestDownload(Context ctx, String id) throws ApiException {
        JSONObject o = postJson(ctx, "/software/" + id + "/download", null);
        DownloadInfo d = new DownloadInfo();
        d.url = absolutize(o.optString("downloadUrl", null));
        d.fileName = o.optString("downloadFileName", null);
        return d;
    }

    public static class ReviewsResult {
        public double averageRating;
        public int ratingCount;
        public Review myReview;
        public List<Review> reviews = new ArrayList<Review>();
    }

    public static ReviewsResult reviews(Context ctx, String softwareId) throws ApiException {
        JSONObject o = getJson(ctx, "/software/" + softwareId + "/reviews");
        ReviewsResult r = new ReviewsResult();
        r.averageRating = o.optDouble("averageRating", 0);
        r.ratingCount = o.optInt("ratingCount", 0);
        JSONObject mine = o.optJSONObject("myReview");
        if (mine != null) r.myReview = Review.fromJson(mine);
        JSONArray arr = o.optJSONArray("reviews");
        if (arr != null) {
            for (int i = 0; i < arr.length(); i++) {
                Review rv = Review.fromJson(arr.optJSONObject(i));
                if (rv != null) r.reviews.add(rv);
            }
        }
        return r;
    }

    /** 提交/修改评价（重复提交即修改）：{value: 0.5~5, content, keepOriginal} */
    public static void submitReview(Context ctx, String softwareId, double value, String content) throws ApiException {
        JSONObject body = new JSONObject();
        try {
            body.put("value", value);
            if (content != null && content.length() > 0) body.put("content", content);
            body.put("keepOriginal", true);
        } catch (Exception ignored) {
        }
        postJson(ctx, "/software/" + softwareId + "/reviews", body);
    }

    /** 评价点赞/取消，返回点赞后的状态 */
    public static boolean toggleLike(Context ctx, String reviewId) throws ApiException {
        JSONObject o = postJson(ctx, "/software/reviews/" + reviewId + "/like", null);
        return o.optBoolean("liked", false);
    }

    /** 浏览计数（fire-and-forget，失败忽略） */
    public static void recordView(Context ctx, String id) {
        try {
            postJson(ctx, "/software/" + id + "/view", null);
        } catch (Throwable ignored) {
        }
    }

    // ============================ 更新请求（需要升级反馈） ============================

    public static class UpdateRequestStatus {
        public int total;
        public boolean requested;
    }

    /** 更新请求状态：total 人数 + 当前访客/用户是否已请求 */
    public static UpdateRequestStatus updateRequestStatus(Context ctx, String id) throws ApiException {
        JSONObject o = getJson(ctx, "/software/" + id + "/update-request?visitorId="
                + urlEncode(asia.carmix.market.util.Visitor.id(ctx)));
        UpdateRequestStatus s = new UpdateRequestStatus();
        s.total = o.optInt("total", 0);
        s.requested = o.optBoolean("requested", false);
        return s;
    }

    /** 提交「需要升级」反馈（幂等，游客可提交），返回 {requested, total} */
    public static UpdateRequestStatus requestUpdate(Context ctx, String id) throws ApiException {
        JSONObject body = new JSONObject();
        try {
            body.put("visitorId", asia.carmix.market.util.Visitor.id(ctx));
        } catch (Exception ignored) {
        }
        JSONObject o = postJson(ctx, "/software/" + id + "/update-request", body);
        UpdateRequestStatus s = new UpdateRequestStatus();
        s.total = o.optInt("total", 0);
        s.requested = o.optBoolean("requested", true);
        return s;
    }

    // ================================ 认证模块 ================================

    public static class AuthResult {
        public String token;
        public User user;
    }

    private static AuthResult authResult(JSONObject o) {
        AuthResult r = new AuthResult();
        r.token = o.optString("token", null);
        r.user = User.fromJson(o.optJSONObject("user"));
        return r;
    }

    public static AuthResult login(Context ctx, String email, String password) throws ApiException {
        JSONObject body = new JSONObject();
        try {
            body.put("email", email);
            body.put("password", password);
        } catch (Exception ignored) {
        }
        return authResult(postJson(ctx, "/auth/user/login", body));
    }

    public static void sendVerificationCode(Context ctx, String email) throws ApiException {
        JSONObject body = new JSONObject();
        try {
            body.put("email", email);
        } catch (Exception ignored) {
        }
        request(ctx, "POST", "/auth/user/send-verification-code", body);
    }

    public static AuthResult register(Context ctx, String email, String username,
                                      String password, String code) throws ApiException {
        JSONObject body = new JSONObject();
        try {
            body.put("email", email);
            body.put("username", username);
            body.put("password", password);
            body.put("verificationCode", code);
        } catch (Exception ignored) {
        }
        return authResult(postJson(ctx, "/auth/user/register", body));
    }

    // ============================ 商店自更新 ============================

    public static class AppVersion {
        public int versionCode;
        public String versionName;
        public String downloadUrl;
        public String fileSize;
        public String sha256;
        public String changelog;
        public boolean forceUpdate;
        public int minSupportedCode;
    }

    /** 最新版本元数据；未配置/空响应返回 null（视为无更新） */
    public static AppVersion latestApp(Context ctx) throws ApiException {
        String text = request(ctx, "GET", "/app/latest", null);
        if (text == null) return null;
        String t = text.trim();
        if (t.isEmpty() || "null".equals(t) || !t.startsWith("{")) return null;
        try {
            JSONObject o = new JSONObject(t);
            if (!o.has("versionCode")) return null;
            AppVersion v = new AppVersion();
            v.versionCode = o.optInt("versionCode", 0);
            v.versionName = o.optString("versionName", "");
            v.downloadUrl = absolutize(o.optString("downloadUrl", null));
            v.fileSize = o.optString("fileSize", null);
            v.sha256 = o.optString("sha256", null);
            v.changelog = o.optString("changelog", "");
            v.forceUpdate = o.optBoolean("forceUpdate", false);
            v.minSupportedCode = o.optInt("minSupportedCode", 0);
            if (v.versionCode <= 0 || v.downloadUrl == null) return null;
            return v;
        } catch (Exception e) {
            return null;
        }
    }

    public static String urlEncode(String s) {
        try {
            return java.net.URLEncoder.encode(s, "UTF-8");
        } catch (Exception e) {
            return s;
        }
    }

    /** 相对 URL 补全（图片地址可能以 / 开头或 http 开头） */
    public static String absolutize(String url) {
        if (url == null || url.length() == 0) return null;
        if (url.startsWith("http://") || url.startsWith("https://")) return url;
        if (url.startsWith("/")) return SITE + url;
        return SITE + "/" + url;
    }
}
