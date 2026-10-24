package asia.carmix.market.util;

import android.content.Context;
import android.text.TextUtils;
import android.widget.Toast;

import asia.carmix.market.R;
import asia.carmix.market.api.Api;
import asia.carmix.market.model.SoftwareItem;
import asia.carmix.market.ui.DetailActivity;

/**
 * 统一下载入口：计数+取直链（后台）→ ApkDownloader 启动。
 * 首页卡片按钮与详情页共用；返回 false 表示未发起（已在下载中）。
 */
public final class DownloadStarter {

    private DownloadStarter() {
    }

    /** 发起下载；返回 true 表示流程已开始（调用方可播飞行动画） */
    public static boolean start(final Context ctx, final SoftwareItem item,
                                final ApkDownloader.Listener listener) {
        if (item == null || TextUtils.isEmpty(item.id)) return false;
        if (ApkDownloader.isActive(item.id)) {
            Toast.makeText(ctx, R.string.downloading_already, Toast.LENGTH_SHORT).show();
            return false;
        }
        Task.run(new Task.Background<String[]>() {
            @Override
            public String[] run() throws Exception {
                String url = null;
                String name = null;
                try {
                    Api.DownloadInfo info = Api.requestDownload(ctx, item.id);
                    if (info != null) {
                        url = info.url;
                        name = info.fileName;
                    }
                } catch (Exception ignored) {
                    // 计数失败不阻断下载，退回条目自带字段
                }
                if (TextUtils.isEmpty(url)) {
                    url = !TextUtils.isEmpty(item.downloadUrl) ? item.downloadUrl : item.downloadLink;
                }
                if (TextUtils.isEmpty(name)) name = item.downloadFileName;
                if (TextUtils.isEmpty(name)) name = "carmix-" + item.id + ".apk";
                return new String[]{url, DetailActivity.sanitizeFileName(name)};
            }
        }, new Task.Ui<String[]>() {
            @Override
            public void onDone(String[] result, Exception error) {
                if (result == null || TextUtils.isEmpty(result[0])) {
                    if (listener != null) {
                        listener.onError(item.id, ctx.getString(R.string.no_download_url));
                    }
                    return;
                }
                ApkDownloader.start(ctx, item.id, item.name, result[0], result[1], listener);
            }
        });
        return true;
    }
}
