package asia.carmix.market.ui;

import android.app.Activity;
import android.content.Intent;
import android.content.res.Configuration;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Locale;

import asia.carmix.market.R;
import asia.carmix.market.Session;
import asia.carmix.market.api.Api;
import asia.carmix.market.model.SoftwareItem;
import asia.carmix.market.util.ApkDownloader;
import asia.carmix.market.util.Formatters;
import asia.carmix.market.util.Task;

/**
 * 首页：左侧分类导航（热门/全部/各分类）+ 右侧卡片网格 + 排序栏 + 本地搜索。
 * 排序为客户端排序（后端列表接口暂无 sort 参数），作用于已加载的分页数据。
 */
public class MainActivity extends Activity {

    /** 热门伪分类标记（真实分类名不可能与之冲突） */
    private static final String CAT_HOT = "\u0001hot";
    private static final int PAGE_SIZE = 20;
    private static final int REQ_LOGIN = 1;

    // 排序三态：默认 → 高到低 → 低到高 → 默认
    private static final int SORT_NONE = 0;
    private static final int SORT_DESC = 1;
    private static final int SORT_ASC = 2;

    private SoftwareAdapter mAdapter;
    private AbsListView mList;
    private ProgressBar mLoading;
    private TextView mEmpty;
    private TextView mFooter;
    private LinearLayout mCatRow;
    private LinearLayout mSortRow;
    private View mDlCenter;      // 下载管理入口（含角标）
    private TextView mDlBadge;   // 任务数角标

    private final List<SoftwareItem> mAll = new ArrayList<SoftwareItem>();
    private final List<String> mCategories = new ArrayList<String>();
    private String mCurrentCategory = null;   // null=全部, CAT_HOT=热门, 其他=分类名
    private int mPage = 1;
    private int mTotalPages = 1;
    private boolean mLoadingMore = false;
    private String mQuery = "";

    // 每个排序键独立三态；激活一个时其余归零
    private int mSortUpdated = SORT_NONE;
    private int mSortDownloads = SORT_NONE;
    private int mSortRating = SORT_NONE;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        if (savedInstanceState != null) {
            String cat = savedInstanceState.getString("category");
            if (cat != null) mCurrentCategory = cat;
            mQuery = savedInstanceState.getString("query", "");
        }

        mAdapter = new SoftwareAdapter(LayoutInflater.from(this),
                getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE);
        mList = (AbsListView) findViewById(R.id.list);
        mLoading = (ProgressBar) findViewById(R.id.loading);
        mEmpty = (TextView) findViewById(R.id.empty);
        mCatRow = (LinearLayout) findViewById(R.id.cat_row);
        mSortRow = (LinearLayout) findViewById(R.id.sort_row);

        // 「加载更多」footer 仅 ListView 支持；横屏 GridView 靠滚动预加载
        mFooter = (TextView) LayoutInflater.from(this).inflate(R.layout.footer_loading, mList, false);
        if (mList instanceof ListView) {
            ((ListView) mList).addFooterView(mFooter, null, false);
        }
        mFooter.setVisibility(View.GONE);
        mList.setAdapter(mAdapter);
        mList.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                if (position >= mAdapter.getCount()) return;
                SoftwareItem s = mAdapter.getItem(position);
                Intent it = new Intent(MainActivity.this, DetailActivity.class);
                it.putExtra(DetailActivity.EXTRA_ID, s.id);
                startActivity(it);
            }
        });
        mList.setOnScrollListener(new AbsListView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(AbsListView view, int scrollState) {
            }

            @Override
            public void onScroll(AbsListView view, int firstVisible, int visibleCount, int totalCount) {
                if (!mLoadingMore && totalCount > 0
                        && firstVisible + visibleCount >= totalCount - 3
                        && mPage < mTotalPages && mQuery.length() == 0) {
                    loadPage(mPage + 1, false);
                }
            }
        });

        EditText search = (EditText) findViewById(R.id.search);
        search.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                mQuery = s.toString().trim().toLowerCase(Locale.CHINA);
                applyFilter();
            }
        });

        findViewById(R.id.btn_login).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (Session.isLoggedIn(MainActivity.this)) {
                    Session.clear(MainActivity.this);
                    Toast.makeText(MainActivity.this, R.string.logged_out, Toast.LENGTH_SHORT).show();
                    refreshLoginButton();
                } else {
                    startActivityForResult(
                            new Intent(MainActivity.this, LoginActivity.class), REQ_LOGIN);
                }
            }
        });

        buildSortButtons();
        buildDownloadCenter();
        mAdapter.setDownloadClickListener(new SoftwareAdapter.OnDownloadClick() {
            @Override
            public void onDownloadClick(SoftwareItem item, View origin) {
                boolean started = asia.carmix.market.util.DownloadStarter.start(
                        MainActivity.this, item, sToastListener);
                if (started) flyToDownloadCenter(origin);
            }
        });
        loadPage(1, true);
        loadCategories();
    }

    /** 网格发起的下载：完成/失败用轻提示，进度走通知与下载管理页 */
    private static final ApkDownloader.Listener sToastListener =
            new ApkDownloader.Listener() {
                @Override
                public void onProgress(String tag, int percent) {
                }

                @Override
                public void onComplete(String tag, java.io.File file) {
                }

                @Override
                public void onError(String tag, String message) {
                }
            };

    @Override
    protected void onDestroy() {
        super.onDestroy();
        ApkDownloader.removeCenterListener(mCenterListener);
    }

    private final ApkDownloader.CenterListener mCenterListener =
            new ApkDownloader.CenterListener() {
                @Override
                public void onTasksChanged() {
                    updateDlBadge();
                }
            };

    private void updateDlBadge() {
        if (mDlBadge == null) return;
        int n = ApkDownloader.activeCount();
        if (n > 0) {
            mDlBadge.setText(String.valueOf(n));
            mDlBadge.setVisibility(View.VISIBLE);
        } else {
            mDlBadge.setVisibility(View.GONE);
        }
    }

    /** 下载管理入口：蓝色圆底箭头图标 + 任务数角标，点击进入下载管理页 */
    private void buildDownloadCenter() {
        LinearLayout sortRow = mSortRow;
        android.widget.FrameLayout box = new android.widget.FrameLayout(this);
        box.setContentDescription(getString(R.string.desc_dl_center));
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        blp.leftMargin = 12;
        sortRow.addView(box, blp);

        android.widget.ImageView icon = new android.widget.ImageView(this);
        icon.setImageDrawable(new asia.carmix.market.util.DownloadIconDrawable(0xFF2563EB, 0xFFFFFFFF));
        android.widget.FrameLayout.LayoutParams ilp = new android.widget.FrameLayout.LayoutParams(
                (int) (30 * getResources().getDisplayMetrics().density),
                (int) (30 * getResources().getDisplayMetrics().density));
        ilp.gravity = android.view.Gravity.CENTER;
        icon.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(MainActivity.this, DownloadManagerActivity.class));
            }
        });
        box.addView(icon, ilp);
        box.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(MainActivity.this, DownloadManagerActivity.class));
            }
        });

        mDlBadge = new TextView(this);
        mDlBadge.setTextColor(0xFFFFFFFF);
        mDlBadge.setTextSize(10f);
        mDlBadge.setGravity(android.view.Gravity.CENTER);
        mDlBadge.setVisibility(View.GONE);
        android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
        bg.setShape(android.graphics.drawable.GradientDrawable.OVAL);
        bg.setColor(0xFFE53935);
        mDlBadge.setBackgroundDrawable(bg);
        int badge = (int) (17 * getResources().getDisplayMetrics().density);
        android.widget.FrameLayout.LayoutParams tlp = new android.widget.FrameLayout.LayoutParams(
                badge, badge, android.view.Gravity.TOP | android.view.Gravity.RIGHT);
        box.addView(mDlBadge, tlp);
        mDlCenter = box;
        updateDlBadge();
    }

    /** 卡片按钮 → 下载管理入口的飞行动画（抛物线 + 缩小） */
    private void flyToDownloadCenter(View origin) {
        if (mDlCenter == null) return;
        try {
            android.view.ViewGroup content = (android.view.ViewGroup)
                    findViewById(android.R.id.content);
            int[] from = new int[2];
            origin.getLocationOnScreen(from);
            int[] to = new int[2];
            mDlCenter.getLocationOnScreen(to);
            int[] root = new int[2];
            content.getLocationOnScreen(root);
            float fx = from[0] - root[0];
            float fy = from[1] - root[1];
            float tx = to[0] - root[0];
            float ty = to[1] - root[1];

            final android.widget.ImageView dot = new android.widget.ImageView(this);
            dot.setImageDrawable(new asia.carmix.market.util.DownloadIconDrawable(
                    0xFF2563EB, 0xFFFFFFFF));
            int d = (int) (34 * getResources().getDisplayMetrics().density);
            android.widget.FrameLayout.LayoutParams lp = new android.widget.FrameLayout.LayoutParams(d, d);
            lp.leftMargin = (int) fx;
            lp.topMargin = (int) fy;
            content.addView(dot, lp);

            android.animation.ValueAnimator anim =
                    android.animation.ValueAnimator.ofFloat(0f, 1f);
            anim.setDuration(520);
            anim.setInterpolator(new android.view.animation.DecelerateInterpolator());
            final float arc = 90 * getResources().getDisplayMetrics().density;
            anim.addUpdateListener(new android.animation.ValueAnimator.AnimatorUpdateListener() {
                @Override
                public void onAnimationUpdate(android.animation.ValueAnimator a) {
                    float t = (Float) a.getAnimatedValue();
                    float x = fx + (tx - fx) * t;
                    float y = fy + (ty - fy) * t - arc * (float) Math.sin(Math.PI * t);
                    dot.setTranslationX(x - fx);
                    dot.setTranslationY(y - fy);
                    float s = 1f - 0.35f * t;
                    dot.setScaleX(s);
                    dot.setScaleY(s);
                    dot.setAlpha(1f - 0.4f * t);
                }
            });
            anim.addListener(new android.animation.AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(android.animation.Animator animation) {
                    content.removeView(dot);
                }
            });
            anim.start();
        } catch (Throwable ignored) {
            // 动画失败不影响下载流程
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_LOGIN) refreshLoginButton();
    }

    private void refreshLoginButton() {
        TextView btn = (TextView) findViewById(R.id.btn_login);
        if (Session.isLoggedIn(this)) {
            String name = Session.getUser(this) == null ? "" : Session.getUser(this).username;
            btn.setText(getString(R.string.hello_user, name));
        } else {
            btn.setText(R.string.login);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshLoginButton();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString("category", mCurrentCategory);
        outState.putString("query", mQuery);
    }

    // ================================ 数据加载 ================================

    private void loadPage(final int page, final boolean reset) {
        if (mLoadingMore) return;
        mLoadingMore = true;
        if (reset) {
            mLoading.setVisibility(View.VISIBLE);
            mEmpty.setVisibility(View.GONE);
        } else {
            mFooter.setVisibility(View.VISIBLE);
        }
        final String category = mCurrentCategory;
        Task.run(new Task.Background<Api.ListResult>() {
            @Override
            public Api.ListResult run() throws Exception {
                if (CAT_HOT.equals(category)) {
                    // 热门伪分类：/software/hot 单页（后端固定最多 10 条）
                    Api.ListResult r = new Api.ListResult();
                    r.items = Api.hot(MainActivity.this);
                    r.total = r.items.size();
                    r.page = 1;
                    r.totalPages = 1;
                    return r;
                }
                return Api.listSoftware(MainActivity.this, page, PAGE_SIZE, category);
            }
        }, new Task.Ui<Api.ListResult>() {
            @Override
            public void onDone(Api.ListResult result, Exception error) {
                mLoadingMore = false;
                mLoading.setVisibility(View.GONE);
                mFooter.setVisibility(View.GONE);
                if (error != null) {
                    Toast.makeText(MainActivity.this, error.getMessage(), Toast.LENGTH_SHORT).show();
                    if (mAll.isEmpty() || reset) {
                        mEmpty.setText(R.string.load_failed);
                        mEmpty.setVisibility(View.VISIBLE);
                    }
                    return;
                }
                if (reset) mAll.clear();
                mAll.addAll(result.items);
                mPage = result.page;
                mTotalPages = Math.max(result.totalPages, 1);
                applyFilter();
            }
        });
    }

    private void loadCategories() {
        Task.run(new Task.Background<List<String>>() {
            @Override
            public List<String> run() throws Exception {
                return Api.categories(MainActivity.this);
            }
        }, new Task.Ui<List<String>>() {
            @Override
            public void onDone(List<String> result, Exception error) {
                if (error != null || result == null || result.isEmpty()) return;
                mCategories.clear();
                mCategories.addAll(result);
                buildNav();
            }
        });
    }

    // ================================ 分类导航 ================================

    private void buildNav() {
        boolean landscape =
                getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE;
        mCatRow.removeAllViews();
        addNav(getString(R.string.category_hot), CAT_HOT, landscape);
        addNav(getString(R.string.category_all), null, landscape);
        for (String c : mCategories) {
            addNav(c, c, landscape);
        }
    }

    private void addNav(String label, final String key, boolean landscape) {
        TextView item = landscape
                ? (TextView) LayoutInflater.from(this).inflate(R.layout.item_nav, mCatRow, false)
                : (TextView) LayoutInflater.from(this).inflate(R.layout.item_chip, mCatRow, false);
        item.setText(label);
        item.setSelected(sameCategory(key, mCurrentCategory));
        item.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (sameCategory(key, mCurrentCategory)) return;
                mCurrentCategory = key;
                for (int i = 0; i < mCatRow.getChildCount(); i++) {
                    View child = mCatRow.getChildAt(i);
                    child.setSelected(child == v);
                }
                loadPage(1, true);
            }
        });
        mCatRow.addView(item);
    }

    private static boolean sameCategory(String a, String b) {
        return a == null ? b == null : a.equals(b);
    }

    // ================================ 排序 ================================

    private void buildSortButtons() {
        mSortRow.removeAllViews();
        addSortButton(R.string.sort_updated);
        addSortButton(R.string.sort_downloads);
        addSortButton(R.string.sort_rating);
        refreshSortLabels();
    }

    private void addSortButton(int labelRes) {
        TextView btn = (TextView) LayoutInflater.from(this)
                .inflate(R.layout.item_sort_btn, mSortRow, false);
        btn.setTag(labelRes);
        btn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                int key = (Integer) v.getTag();
                if (key == R.string.sort_updated) {
                    mSortUpdated = next(mSortUpdated);
                    mSortDownloads = SORT_NONE;
                    mSortRating = SORT_NONE;
                } else if (key == R.string.sort_downloads) {
                    mSortDownloads = next(mSortDownloads);
                    mSortUpdated = SORT_NONE;
                    mSortRating = SORT_NONE;
                } else {
                    mSortRating = next(mSortRating);
                    mSortUpdated = SORT_NONE;
                    mSortDownloads = SORT_NONE;
                }
                refreshSortLabels();
                applyFilter();
            }
        });
        mSortRow.addView(btn);
    }

    private static int next(int state) {
        return state == SORT_NONE ? SORT_DESC : state == SORT_DESC ? SORT_ASC : SORT_NONE;
    }

    /** 按钮文案：无排序不带箭头，高到低 ▼，低到高 ▲ */
    private void refreshSortLabels() {
        for (int i = 0; i < mSortRow.getChildCount(); i++) {
            TextView btn = (TextView) mSortRow.getChildAt(i);
            int key = (Integer) btn.getTag();
            int state;
            if (key == R.string.sort_updated) state = mSortUpdated;
            else if (key == R.string.sort_downloads) state = mSortDownloads;
            else state = mSortRating;
            String label = getString(key);
            if (state == SORT_DESC) label += " ▼";
            else if (state == SORT_ASC) label += " ▲";
            btn.setText(label);
            btn.setSelected(state != SORT_NONE);
        }
    }

    // ================================ 过滤 + 排序 ================================

    private void applyFilter() {
        List<SoftwareItem> shown = new ArrayList<SoftwareItem>();
        if (mQuery.length() == 0) {
            shown.addAll(mAll);
        } else {
            for (SoftwareItem s : mAll) {
                if (s.name != null && s.name.toLowerCase(Locale.CHINA).contains(mQuery)) {
                    shown.add(s);
                }
            }
        }
        sortShown(shown);
        mAdapter.setItems(shown);
        if (shown.isEmpty()) {
            mEmpty.setText(mQuery.length() > 0 ? R.string.no_search_result : R.string.empty_list);
            mEmpty.setVisibility(View.VISIBLE);
        } else {
            mEmpty.setVisibility(View.GONE);
        }
    }

    private void sortShown(List<SoftwareItem> list) {
        Comparator<SoftwareItem> cmp = null;
        if (mSortUpdated != SORT_NONE) {
            cmp = new Comparator<SoftwareItem>() {
                @Override
                public int compare(SoftwareItem a, SoftwareItem b) {
                    long x = Formatters.parseIso(a.updatedAt) - Formatters.parseIso(b.updatedAt);
                    return Long.signum(mSortUpdated == SORT_ASC ? x : -x);
                }
            };
        } else if (mSortDownloads != SORT_NONE) {
            cmp = new Comparator<SoftwareItem>() {
                @Override
                public int compare(SoftwareItem a, SoftwareItem b) {
                    long x = a.downloadCount - b.downloadCount;
                    return Long.signum(mSortDownloads == SORT_ASC ? x : -x);
                }
            };
        } else if (mSortRating != SORT_NONE) {
            cmp = new Comparator<SoftwareItem>() {
                @Override
                public int compare(SoftwareItem a, SoftwareItem b) {
                    int x = Double.compare(a.averageRating, b.averageRating);
                    if (x == 0) x = Integer.signum(a.ratingCount - b.ratingCount);
                    return mSortRating == SORT_ASC ? x : -x;
                }
            };
        }
        if (cmp != null) {
            Collections.sort(list, cmp);
        }
    }

    // ================================ 菜单 ================================

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_refresh) {
            loadPage(1, true);
            return true;
        } else if (id == R.id.action_exit) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}
