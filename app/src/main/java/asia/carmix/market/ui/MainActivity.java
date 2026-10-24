package asia.carmix.market.ui;

import android.app.Activity;
import android.content.Intent;
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
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import asia.carmix.market.R;
import asia.carmix.market.Session;
import asia.carmix.market.api.Api;
import asia.carmix.market.model.SoftwareItem;
import asia.carmix.market.util.ImageLoader;
import asia.carmix.market.util.Task;

/**
 * 首页：分类筛选 + 热门推荐 + 软件列表（分页加载）+ 本地搜索过滤。
 * 后端列表接口暂无服务端关键字搜索，输入框对已加载数据做本地过滤。
 */
public class MainActivity extends Activity {

    private static final int PAGE_SIZE = 20;
    private static final int REQ_LOGIN = 1;

    private SoftwareAdapter mAdapter;
    private AbsListView mList;
    private ProgressBar mLoading;
    private TextView mEmpty;
    private TextView mFooter;
    private LinearLayout mCatRow;
    private LinearLayout mHotRow;
    private View mHotSection;
    private HorizontalScrollView mHotScroll;

    private final List<SoftwareItem> mAll = new ArrayList<SoftwareItem>();
    private final List<String> mCategories = new ArrayList<String>();
    private String mCurrentCategory = null;
    private int mPage = 1;
    private int mTotalPages = 1;
    private boolean mLoadingMore = false;
    private String mQuery = "";
    private boolean mFirstLoad = true;

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
                getResources().getConfiguration().orientation
                        == android.content.res.Configuration.ORIENTATION_LANDSCAPE);
        mList = (AbsListView) findViewById(R.id.list);
        mLoading = (ProgressBar) findViewById(R.id.loading);
        mEmpty = (TextView) findViewById(R.id.empty);
        mCatRow = (LinearLayout) findViewById(R.id.cat_row);
        mHotRow = (LinearLayout) findViewById(R.id.hot_row);
        mHotSection = findViewById(R.id.hot_section);
        mHotScroll = (HorizontalScrollView) findViewById(R.id.hot_scroll);

        // 「加载更多」footer 仅 ListView 支持；横屏 GridView 靠滚动预加载，无需 footer
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

        loadPage(1, true);
        loadHot();
        loadCategories();
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
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString("category", mCurrentCategory);
        outState.putString("query", mQuery);
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshLoginButton();
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
                return Api.listSoftware(MainActivity.this, page, PAGE_SIZE, category);
            }
        }, new Task.Ui<Api.ListResult>() {
            @Override
            public void onDone(Api.ListResult result, Exception error) {
                mLoadingMore = false;
                mFirstLoad = false;
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
                buildCategoryChips();
            }
        });
    }

    private void loadHot() {
        Task.run(new Task.Background<List<SoftwareItem>>() {
            @Override
            public List<SoftwareItem> run() throws Exception {
                return Api.hot(MainActivity.this);
            }
        }, new Task.Ui<List<SoftwareItem>>() {
            @Override
            public void onDone(List<SoftwareItem> result, Exception error) {
                if (error != null || result == null || result.isEmpty()) {
                    mHotSection.setVisibility(View.GONE);
                    return;
                }
                mHotSection.setVisibility(View.VISIBLE);
                mHotRow.removeAllViews();
                LayoutInflater inflater = LayoutInflater.from(MainActivity.this);
                for (final SoftwareItem s : result) {
                    View card = inflater.inflate(R.layout.item_hot, mHotRow, false);
                    ImageView icon = (ImageView) card.findViewById(R.id.hot_icon);
                    TextView name = (TextView) card.findViewById(R.id.hot_name);
                    name.setText(s.name);
                    if (s.thumbnailUrl == null || s.thumbnailUrl.length() == 0 || "null".equals(s.thumbnailUrl)) {
                        icon.setImageDrawable(new asia.carmix.market.util.LetterDrawable(s.name));
                    } else {
                        ImageLoader.get(MainActivity.this)
                                .display(icon, s.thumbnailUrl, R.drawable.placeholder_app);
                    }
                    card.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            Intent it = new Intent(MainActivity.this, DetailActivity.class);
                            it.putExtra(DetailActivity.EXTRA_ID, s.id);
                            startActivity(it);
                        }
                    });
                    mHotRow.addView(card);
                }
            }
        });
    }

    private void buildCategoryChips() {
        mCatRow.removeAllViews();
        addChip(getString(R.string.category_all), null);
        for (String c : mCategories) {
            addChip(c, c);
        }
    }

    private void addChip(String label, final String category) {
        android.widget.Button chip = (android.widget.Button)
                LayoutInflater.from(this).inflate(R.layout.item_chip, mCatRow, false);
        chip.setText(label);
        chip.setSelected(category == null ? mCurrentCategory == null
                : category.equals(mCurrentCategory));
        chip.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (category == null ? mCurrentCategory == null
                        : category.equals(mCurrentCategory)) {
                    return;
                }
                mCurrentCategory = category;
                for (int i = 0; i < mCatRow.getChildCount(); i++) {
                    View child = mCatRow.getChildAt(i);
                    child.setSelected(child == v);
                }
                loadPage(1, true);
            }
        });
        mCatRow.addView(chip);
    }

    /** 搜索过滤（仅作用于已加载数据） */
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
        mAdapter.setItems(shown);
        if (shown.isEmpty()) {
            mEmpty.setText(mQuery.length() > 0 ? R.string.no_search_result : R.string.empty_list);
            mEmpty.setVisibility(View.VISIBLE);
        } else {
            mEmpty.setVisibility(View.GONE);
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
            loadHot();
            return true;
        } else if (id == R.id.action_exit) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}
