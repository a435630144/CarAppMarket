package asia.carmix.market.ui;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;

import asia.carmix.market.R;
import asia.carmix.market.api.Api;
import asia.carmix.market.util.ApkDownloader;
import asia.carmix.market.util.Task;

/**
 * 关于页：版本、作者、GitHub 仓库、协议、官方站点 + 手动检查更新。
 */
public class AboutActivity extends BaseActivity {

    private TextView mCheckBtn;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_about);
        getActionBar().setTitle(R.string.about_title);
        getActionBar().setDisplayHomeAsUpEnabled(true);

        // 版本号
        String version;
        int code;
        try {
            android.content.pm.PackageInfo pi =
                    getPackageManager().getPackageInfo(getPackageName(), 0);
            version = pi.versionName;
            code = pi.versionCode;
        } catch (Exception e) {
            version = "?";
            code = 0;
        }
        ((TextView) findViewById(R.id.a_version)).setText(version + " (" + code + ")");

        // GitHub 仓库
        findViewById(R.id.a_repo).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                open("https://github.com/a435630144/CarAppMarket");
            }
        });
        // GPL-3.0 全文
        findViewById(R.id.a_license).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                open("https://www.gnu.org/licenses/gpl-3.0.html");
            }
        });
        // 官方站点
        findViewById(R.id.a_site).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                open("https://<后端域名>");
            }
        });
        // 支付宝打赏：弹收款码
        findViewById(R.id.a_donate).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                android.widget.ScrollView sc = new android.widget.ScrollView(AboutActivity.this);
                android.widget.ImageView iv = new android.widget.ImageView(AboutActivity.this);
                iv.setImageResource(R.drawable.alipay_qr);
                iv.setAdjustViewBounds(true);
                int pad = (int) (16 * getResources().getDisplayMetrics().density);
                sc.addView(iv);
                sc.setPadding(pad, pad, pad, pad);
                new AlertDialog.Builder(AboutActivity.this)
                        .setTitle(R.string.about_donate)
                        .setView(sc)
                        .setPositiveButton(R.string.cancel, null)
                        .show();
            }
        });
        // 手动检查更新（忽略 1 小时节流）
        mCheckBtn = (TextView) findViewById(R.id.a_check_update);
        mCheckBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                checkUpdate();
            }
        });
    }

    private void open(String url) {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (Exception e) {
            Toast.makeText(this, e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    /** 手动检查：走同一自更新链路，结果用对话框明示（无更新也提示） */
    private void checkUpdate() {
        mCheckBtn.setEnabled(false);
        mCheckBtn.setAlpha(0.6f);
        Task.run(new Task.Background<Api.AppVersion>() {
            @Override
            public Api.AppVersion run() throws Exception {
                return Api.latestApp(AboutActivity.this);
            }
        }, new Task.Ui<Api.AppVersion>() {
            @Override
            public void onDone(final Api.AppVersion v, Exception error) {
                mCheckBtn.setEnabled(true);
                mCheckBtn.setAlpha(1f);
                if (error != null) {
                    Toast.makeText(AboutActivity.this, error.getMessage(), Toast.LENGTH_SHORT).show();
                    return;
                }
                int self = 0;
                try {
                    self = getPackageManager().getPackageInfo(getPackageName(), 0).versionCode;
                } catch (Exception ignored) {
                }
                if (v == null || v.versionCode <= self) {
                    Toast.makeText(AboutActivity.this, R.string.update_latest, Toast.LENGTH_SHORT).show();
                    return;
                }
                new AlertDialog.Builder(AboutActivity.this)
                        .setTitle(getString(R.string.update_banner, v.versionName))
                        .setMessage(v.changelog == null || v.changelog.length() == 0
                                ? getString(R.string.update_force_msg)
                                : v.changelog)
                        .setPositiveButton(R.string.download, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                if (ApkDownloader.isActive("self-update")) {
                                    Toast.makeText(AboutActivity.this,
                                            R.string.update_downloading, Toast.LENGTH_SHORT).show();
                                    return;
                                }
                                ApkDownloader.start(AboutActivity.this, "self-update",
                                        "车软星球市场 " + v.versionName, v.downloadUrl,
                                        "carmix-market-" + v.versionName + ".apk",
                                        new ApkDownloader.Listener() {
                                            @Override
                                            public void onProgress(String tag, int percent) {
                                            }

                                            @Override
                                            public void onComplete(String tag, File file) {
                                                if (v.sha256 != null && v.sha256.length() > 0) {
                                                    String actual =
                                                            MainActivity.sha256OfFile(file);
                                                    if (actual == null || !actual
                                                            .equalsIgnoreCase(v.sha256)) {
                                                        file.delete();
                                                        Toast.makeText(AboutActivity.this,
                                                                R.string.update_verify_failed,
                                                                Toast.LENGTH_LONG).show();
                                                        return;
                                                    }
                                                }
                                                try {
                                                    startActivity(ApkDownloader.installIntent(
                                                            AboutActivity.this, file.getName()));
                                                } catch (Exception e) {
                                                    Toast.makeText(AboutActivity.this,
                                                            getString(R.string.install_failed,
                                                                    e.getMessage()),
                                                            Toast.LENGTH_SHORT).show();
                                                }
                                            }

                                            @Override
                                            public void onError(String tag, String message) {
                                                Toast.makeText(AboutActivity.this, message,
                                                        Toast.LENGTH_SHORT).show();
                                            }
                                        });
                            }
                        })
                        .setNegativeButton(R.string.cancel, null)
                        .show();
            }
        });
    }

    @Override
    public boolean onOptionsItemSelected(android.view.MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}
