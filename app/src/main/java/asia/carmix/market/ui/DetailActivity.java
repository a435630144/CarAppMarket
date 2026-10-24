package asia.carmix.market.ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RatingBar;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.util.List;

import asia.carmix.market.R;
import asia.carmix.market.Session;
import asia.carmix.market.api.Api;
import asia.carmix.market.model.Review;
import asia.carmix.market.model.SoftwareItem;
import asia.carmix.market.util.ApkDownloader;
import asia.carmix.market.util.Formatters;
import asia.carmix.market.util.ImageLoader;
import asia.carmix.market.util.Task;

/**
 * 详情页：信息展示 + 截图 + 评价 + 下载/安装。
 */
public class DetailActivity extends Activity {

    public static final String EXTRA_ID = "id";

    private String mId;
    private SoftwareItem mItem;
    private String mDownloadedFile; // 已下载完成的文件名

    private ImageView mIcon;
    private TextView mName, mMeta, mAuthor, mRatingText, mStats;
    private RatingBar mRating;
    private TextView mDesc, mSysReq;
    private TextView mDownloadBtn;
    private LinearLayout mShotRow, mReviewRow;
    private View mReviewsHeader, mReviewEmpty, mWriteReview, mVersionSection, mChangelogSection;
    private LinearLayout mVersionRow, mChangelogRow;
    private TextView mReviewSummary;

    private ApkDownloader.Listener mDownloadListener = new ApkDownloader.Listener() {
        @Override
        public void onProgress(String tag, int percent) {
            mDownloadBtn.setText(percent < 0
                    ? getString(R.string.downloading)
                    : getString(R.string.downloading_percent, percent));
        }

        @Override
        public void onComplete(String tag, File file) {
            mDownloadedFile = file.getName();
            mDownloadBtn.setText(R.string.install);
            toast(getString(R.string.download_complete));
            promptInstall();
        }

        @Override
        public void onError(String tag, String message) {
            mDownloadBtn.setText(R.string.download);
            toast(message);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_detail);
        getActionBar().setDisplayHomeAsUpEnabled(true);

        mId = getIntent().getStringExtra(EXTRA_ID);
        mIcon = (ImageView) findViewById(R.id.d_icon);
        mName = (TextView) findViewById(R.id.d_name);
        mMeta = (TextView) findViewById(R.id.d_meta);
        mAuthor = (TextView) findViewById(R.id.d_author);
        mRating = (RatingBar) findViewById(R.id.d_rating);
        mRatingText = (TextView) findViewById(R.id.d_rating_text);
        mStats = (TextView) findViewById(R.id.d_stats);
        mDesc = (TextView) findViewById(R.id.d_desc);
        mSysReq = (TextView) findViewById(R.id.d_sysreq);
        mDownloadBtn = (TextView) findViewById(R.id.d_btn_download);
        mShotRow = (LinearLayout) findViewById(R.id.d_shot_row);
        mReviewRow = (LinearLayout) findViewById(R.id.d_review_row);
        mReviewsHeader = findViewById(R.id.d_reviews_header);
        mReviewEmpty = findViewById(R.id.d_review_empty);
        mWriteReview = findViewById(R.id.d_btn_review);
        mReviewSummary = (TextView) findViewById(R.id.d_review_summary);
        mVersionSection = findViewById(R.id.d_version_section);
        mVersionRow = (LinearLayout) findViewById(R.id.d_version_row);
        mChangelogSection = findViewById(R.id.d_changelog_section);
        mChangelogRow = (LinearLayout) findViewById(R.id.d_changelog_row);

        findViewById(R.id.d_progress).setVisibility(View.VISIBLE);

        mDownloadBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onDownloadClicked();
            }
        });
        mWriteReview.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!Session.isLoggedIn(DetailActivity.this)) {
                    toast(getString(R.string.login_required));
                    startActivity(new Intent(DetailActivity.this, LoginActivity.class));
                    return;
                }
                showReviewDialog();
            }
        });

        loadDetail();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (mItem != null) loadReviews(false);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void toast(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }

    // ================================ 详情 ================================

    private void loadDetail() {
        Task.run(new Task.Background<SoftwareItem>() {
            @Override
            public SoftwareItem run() throws Exception {
                SoftwareItem s = Api.detail(DetailActivity.this, mId);
                Api.recordView(DetailActivity.this, mId);
                return s;
            }
        }, new Task.Ui<SoftwareItem>() {
            @Override
            public void onDone(SoftwareItem result, Exception error) {
                findViewById(R.id.d_progress).setVisibility(View.GONE);
                if (error != null) {
                    toast(error.getMessage());
                    finish();
                    return;
                }
                mItem = result;
                bindDetail();
                loadReviews(true);
                // 若已下载过，按钮直接显示“安装”
                String fn = defaultFileName(mItem);
                if (fn != null && new File(ApkDownloader.downloadsDir(DetailActivity.this), fn).exists()) {
                    mDownloadedFile = fn;
                    mDownloadBtn.setText(R.string.install);
                }
            }
        });
    }

    private void bindDetail() {
        getActionBar().setTitle(mItem.name);
        if (mItem.thumbnailUrl == null || mItem.thumbnailUrl.length() == 0 || "null".equals(mItem.thumbnailUrl)) {
            mIcon.setImageDrawable(new asia.carmix.market.util.LetterDrawable(mItem.name));
        } else {
            ImageLoader.get(this).display(mIcon, mItem.thumbnailUrl, R.drawable.placeholder_app);
        }
        mName.setText(mItem.name);
        mMeta.setText(mItem.metaLine());
        if (!TextUtils.isEmpty(mItem.author)) {
            mAuthor.setText(getString(R.string.author_label, mItem.author));
            mAuthor.setVisibility(View.VISIBLE);
        } else {
            mAuthor.setVisibility(View.GONE);
        }
        mRating.setRating((float) mItem.averageRating);
        mRatingText.setText(String.format(java.util.Locale.CHINA, "%.1f分", mItem.averageRating));
        String stats = Formatters.count(mItem.downloadCount) + getString(R.string.downloads_suffix)
                + " · " + Formatters.count(mItem.views) + getString(R.string.views_suffix);
        String created = Formatters.date(mItem.createdAt);
        if (created.length() > 0) {
            stats += " · " + getString(R.string.created_at, created);
        }
        mStats.setText(stats);
        mDesc.setText(TextUtils.isEmpty(mItem.description) ? "暂无介绍" : mItem.description);
        if (!TextUtils.isEmpty(mItem.systemRequirement)) {
            mSysReq.setText(getString(R.string.sysreq_label, mItem.systemRequirement));
            mSysReq.setVisibility(View.VISIBLE);
        } else {
            mSysReq.setVisibility(View.GONE);
        }
        if (!mItem.downloadable()) {
            mDownloadBtn.setEnabled(false);
            mDownloadBtn.setText(R.string.no_download_url);
        }
        buildScreenshots();
        buildVersions();
        buildChangelogs();
    }

    // ================================ 合集版本 ================================

    /** 版本 chips：点击即切换到对应成员重新加载详情 */
    private void buildVersions() {
        mVersionRow.removeAllViews();
        SoftwareItem.CollectionInfo col = mItem.collection;
        if (col == null || col.versions.size() < 2) {
            mVersionSection.setVisibility(View.GONE);
            return;
        }
        mVersionSection.setVisibility(View.VISIBLE);
        LayoutInflater inflater = LayoutInflater.from(this);
        for (final SoftwareItem.Version v : col.versions) {
            TextView chip = (TextView) inflater.inflate(R.layout.item_chip, mVersionRow, false);
            String label = TextUtils.isEmpty(v.version) ? "最新" : "v" + v.version;
            if (!TextUtils.isEmpty(v.fileSize)) label += " · " + v.fileSize;
            chip.setText(label);
            chip.setSelected(v.id != null && v.id.equals(mItem.id));
            chip.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    if (v.id != null && !v.id.equals(mItem.id)) {
                        mId = v.id;
                        findViewById(R.id.d_progress).setVisibility(View.VISIBLE);
                        loadDetail();
                    }
                }
            });
            mVersionRow.addView(chip);
        }
    }

    /** 更新日志条目：版本号·日期 + 内容 */
    private void buildChangelogs() {
        mChangelogRow.removeAllViews();
        SoftwareItem.CollectionInfo col = mItem.collection;
        if (col == null || col.changelogs.isEmpty()) {
            mChangelogSection.setVisibility(View.GONE);
            return;
        }
        mChangelogSection.setVisibility(View.VISIBLE);
        for (SoftwareItem.Changelog c : col.changelogs) {
            TextView head = new TextView(this);
            String h = TextUtils.isEmpty(c.versionLabel) ? "" : c.versionLabel + " · ";
            head.setText(h + Formatters.date(c.createdAt));
            head.setTextColor(0xFF2563EB);
            head.setTextSize(13f);
            head.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
            mChangelogRow.addView(head);
            if (!TextUtils.isEmpty(c.content)) {
                TextView body = new TextView(this);
                body.setText(c.content);
                body.setTextColor(0xFF1F2937);
                body.setTextSize(13f);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
                lp.topMargin = 2;
                lp.bottomMargin = 8;
                mChangelogRow.addView(body, lp);
            }
        }
    }

    private void buildScreenshots() {
        mShotRow.removeAllViews();
        if (mItem.screenshots.isEmpty()) {
            findViewById(R.id.d_shot_section).setVisibility(View.GONE);
            return;
        }
        findViewById(R.id.d_shot_section).setVisibility(View.VISIBLE);
        LayoutInflater inflater = LayoutInflater.from(this);
        for (final String url : mItem.screenshots) {
            ImageView iv = (ImageView) inflater.inflate(R.layout.item_screenshot, mShotRow, false);
            ImageLoader.get(this).display(iv, url, R.drawable.placeholder_shot, 1280);
            iv.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    showFullShot(url);
                }
            });
            mShotRow.addView(iv);
        }
    }

    /** 全屏查看截图 */
    private void showFullShot(String url) {
        final AlertDialog dialog = new AlertDialog.Builder(this).create();
        ImageView iv = new ImageView(this);
        iv.setBackgroundColor(Color.BLACK);
        iv.setAdjustViewBounds(true);
        iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
        ImageLoader.get(this).display(iv, url, R.drawable.placeholder_shot, 1920);
        iv.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dialog.dismiss();
            }
        });
        dialog.setView(iv);
        dialog.show();
    }

    // ================================ 评价 ================================

    private void loadReviews(final boolean showProgress) {
        if (mId == null) return;
        Task.run(new Task.Background<Api.ReviewsResult>() {
            @Override
            public Api.ReviewsResult run() throws Exception {
                return Api.reviews(DetailActivity.this, mId);
            }
        }, new Task.Ui<Api.ReviewsResult>() {
            @Override
            public void onDone(Api.ReviewsResult result, Exception error) {
                if (error != null) {
                    // 评价加载失败不阻塞页面
                    mReviewSummary.setVisibility(View.GONE);
                    mReviewEmpty.setVisibility(View.VISIBLE);
                    return;
                }
                bindReviews(result);
            }
        });
    }

    private void bindReviews(Api.ReviewsResult r) {
        if (r.ratingCount > 0) {
            mReviewSummary.setText(String.format(java.util.Locale.CHINA,
                    getString(R.string.review_summary_fmt), r.averageRating, r.ratingCount));
            mReviewSummary.setVisibility(View.VISIBLE);
        } else {
            mReviewSummary.setVisibility(View.GONE);
        }
        mReviewRow.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(this);
        boolean any = false;
        for (final Review rv : r.reviews) {
            if (rv.isHidden && !rv.isMine) continue; // 他人隐藏的评价占位不展示
            any = true;
            View v = inflater.inflate(R.layout.item_review, mReviewRow, false);
            TextView name = (TextView) v.findViewById(R.id.r_user);
            TextView vehicle = (TextView) v.findViewById(R.id.r_vehicle);
            TextView time = (TextView) v.findViewById(R.id.r_time);
            TextView content = (TextView) v.findViewById(R.id.r_content);
            TextView original = (TextView) v.findViewById(R.id.r_original);
            TextView like = (TextView) v.findViewById(R.id.r_like);
            RatingBar rating = (RatingBar) v.findViewById(R.id.r_rating);

            name.setText(rv.username + (rv.isMine ? "（我）" : ""));
            rating.setRating((float) rv.rating);
            time.setText(Formatters.date(rv.createdAt)
                    + (TextUtils.isEmpty(rv.softwareVersion) ? "" : " · v" + rv.softwareVersion));
            if (rv.vehicle != null) {
                vehicle.setText(rv.vehicle);
                vehicle.setVisibility(View.VISIBLE);
            } else {
                vehicle.setVisibility(View.GONE);
            }
            if (rv.isHidden) {
                content.setText("该评价已隐藏");
                content.setTextColor(0xFF9AA4B2);
            } else {
                content.setText(TextUtils.isEmpty(rv.content) ? "仅评分" : rv.content);
            }
            if (rv.originalContent != null && !rv.isHidden) {
                original.setText(getString(R.string.original_review, rv.originalContent));
                original.setVisibility(View.VISIBLE);
            } else {
                original.setVisibility(View.GONE);
            }
            like.setText(getString(R.string.like_count, rv.likeCount));
            like.setEnabled(!rv.isHidden);
            if (rv.isLiked) {
                like.setTextColor(0xFF2563EB);
            } else {
                like.setTextColor(0xFF6B7280);
            }
            like.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    toggleLike(rv, (TextView) v);
                }
            });
            mReviewRow.addView(v);
        }
        mReviewEmpty.setVisibility(any ? View.GONE : View.VISIBLE);
        mReviewsHeader.setVisibility(View.VISIBLE);
    }

    private void toggleLike(final Review rv, final TextView view) {
        if (!Session.isLoggedIn(this)) {
            toast(getString(R.string.login_required));
            startActivity(new Intent(this, LoginActivity.class));
            return;
        }
        Task.run(new Task.Background<Boolean>() {
            @Override
            public Boolean run() throws Exception {
                return Api.toggleLike(DetailActivity.this, rv.id);
            }
        }, new Task.Ui<Boolean>() {
            @Override
            public void onDone(Boolean liked, Exception error) {
                if (error != null) {
                    toast(error.getMessage());
                    return;
                }
                rv.isLiked = Boolean.TRUE.equals(liked);
                rv.likeCount += rv.isLiked ? 1 : -1;
                view.setText(getString(R.string.like_count, Math.max(rv.likeCount, 0)));
                view.setTextColor(rv.isLiked ? 0xFF2563EB : 0xFF6B7280);
            }
        });
    }

    private void showReviewDialog() {
        View v = LayoutInflater.from(this).inflate(R.layout.dialog_review, null);
        final RatingBar rb = (RatingBar) v.findViewById(R.id.rv_rating);
        final EditText et = (EditText) v.findViewById(R.id.rv_content);
        new AlertDialog.Builder(this)
                .setTitle(R.string.write_review)
                .setView(v)
                .setPositiveButton(R.string.submit, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        final double val = Math.max(rb.getRating(), 0.5f);
                        final String text = et.getText().toString().trim();
                        Task.run(new Task.Background<Object>() {
                            @Override
                            public Object run() throws Exception {
                                Api.submitReview(DetailActivity.this, mId, val, text);
                                return null;
                            }
                        }, new Task.Ui<Object>() {
                            @Override
                            public void onDone(Object result, Exception error) {
                                if (error != null) {
                                    toast(error.getMessage());
                                    return;
                                }
                                toast(getString(R.string.review_submitted));
                                loadReviews(false);
                            }
                        });
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    // ================================ 下载 / 安装 ================================

    private void onDownloadClicked() {
        if (mItem == null) return;
        if (ApkDownloader.isActive(mItem.id)) {
            toast(getString(R.string.downloading_already));
            return;
        }
        if (mDownloadedFile != null
                && new File(ApkDownloader.downloadsDir(this), mDownloadedFile).exists()) {
            promptInstall();
            return;
        }
        startDownload();
    }

    private void startDownload() {
        mDownloadBtn.setText(R.string.downloading);
        boolean started = asia.carmix.market.util.DownloadStarter.start(
                this, mItem, mDownloadListener);
        if (!started) {
            mDownloadBtn.setText(R.string.download);
        }
    }

    private void promptInstall() {
        if (mDownloadedFile == null) return;
        try {
            startActivity(ApkDownloader.installIntent(this, mDownloadedFile));
        } catch (Exception e) {
            toast(getString(R.string.install_failed, e.getMessage()));
        }
    }

    /** 文件名清洗：去掉路径分隔符，保证 .apk 结尾 */
    public static String sanitizeFileName(String name) {
        String n = name.replaceAll("[\\\\/:*?\"<>|\\s]+", "_");
        if (!n.toLowerCase(java.util.Locale.US).endsWith(".apk")) {
            n = n + ".apk";
        }
        if (n.length() > 100) {
            n = n.substring(0, 96) + ".apk";
        }
        return n;
    }

    private String defaultFileName(SoftwareItem item) {
        String n = !TextUtils.isEmpty(item.downloadFileName) ? item.downloadFileName : null;
        if (TextUtils.isEmpty(n)) n = "carmix-" + item.id + ".apk";
        return sanitizeFileName(n);
    }
}
