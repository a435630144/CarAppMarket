package asia.carmix.market.ui;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.RatingBar;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import asia.carmix.market.R;
import asia.carmix.market.model.SoftwareItem;
import asia.carmix.market.util.Formatters;
import asia.carmix.market.util.ImageLoader;
import asia.carmix.market.util.LetterDrawable;

/**
 * 双模式条目适配器：
 * - gridMode=true  → 横屏车机网格小卡片（item_software_card）
 * - gridMode=false → 竖屏手机单列横条（item_software）
 */
public class SoftwareAdapter extends BaseAdapter {

    /** 卡片下载按钮回调（origin 供飞行动画取起点坐标） */
    public interface OnDownloadClick {
        void onDownloadClick(SoftwareItem item, View origin);
    }

    private final LayoutInflater mInflater;
    private final boolean mGridMode;
    private final List<SoftwareItem> mItems = new ArrayList<SoftwareItem>();
    private OnDownloadClick mDownloadClick;

    public SoftwareAdapter(LayoutInflater inflater, boolean gridMode) {
        this.mInflater = inflater;
        this.mGridMode = gridMode;
    }

    public void setDownloadClickListener(OnDownloadClick l) {
        mDownloadClick = l;
    }

    public void setItems(List<SoftwareItem> items) {
        mItems.clear();
        if (items != null) mItems.addAll(items);
        notifyDataSetChanged();
    }

    public void addItems(List<SoftwareItem> items) {
        if (items != null) mItems.addAll(items);
        notifyDataSetChanged();
    }

    @Override
    public int getCount() {
        return mItems.size();
    }

    @Override
    public SoftwareItem getItem(int position) {
        return mItems.get(position);
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        return mGridMode ? getCardView(position, convertView, parent)
                : getRowView(position, convertView, parent);
    }

    // ================================ 网格卡片（横屏车机） ================================

    private View getCardView(int position, View convertView, ViewGroup parent) {
        CardHolder h;
        if (convertView == null) {
            convertView = mInflater.inflate(R.layout.item_software_card, parent, false);
            h = new CardHolder();
            h.icon = (ImageView) convertView.findViewById(R.id.icon);
            h.name = (TextView) convertView.findViewById(R.id.name);
            h.meta = (TextView) convertView.findViewById(R.id.meta);
            h.rating = (RatingBar) convertView.findViewById(R.id.rating);
            h.stats = (TextView) convertView.findViewById(R.id.stats);
            h.badge = (TextView) convertView.findViewById(R.id.badge);
            h.typeTag = (TextView) convertView.findViewById(R.id.type_tag);
            h.dlBtn = (TextView) convertView.findViewById(R.id.btn_download);
            convertView.setTag(h);
        } else {
            h = (CardHolder) convertView.getTag();
        }
        SoftwareItem s = getItem(position);
        h.name.setText(s.name);
        h.meta.setText(s.metaLine());
        h.rating.setRating((float) s.averageRating);
        String stats = Formatters.count(s.downloadCount) + "次";
        String d = Formatters.shortDate(s.createdAt);
        if (d.length() > 0) stats += " · " + d;
        h.stats.setText(stats);
        bindBadge(h.badge, s);
        bindType(h.typeTag, s);
        bindIcon(h.icon, s);
        bindDownload(h.dlBtn, s);
        return convertView;
    }

    private static class CardHolder {
        ImageView icon;
        TextView name;
        TextView meta;
        RatingBar rating;
        TextView stats;
        TextView badge;
        TextView typeTag;
        TextView dlBtn;
    }

    // ================================ 单列横条（竖屏手机） ================================

    private View getRowView(int position, View convertView, ViewGroup parent) {
        RowHolder h;
        if (convertView == null) {
            convertView = mInflater.inflate(R.layout.item_software, parent, false);
            h = new RowHolder();
            h.icon = (ImageView) convertView.findViewById(R.id.icon);
            h.name = (TextView) convertView.findViewById(R.id.name);
            h.meta = (TextView) convertView.findViewById(R.id.meta);
            h.rating = (RatingBar) convertView.findViewById(R.id.rating);
            h.stats = (TextView) convertView.findViewById(R.id.stats);
            h.badge = (TextView) convertView.findViewById(R.id.badge);
            h.typeTag = (TextView) convertView.findViewById(R.id.type_tag);
            h.dlBtn = (TextView) convertView.findViewById(R.id.btn_download);
            convertView.setTag(h);
        } else {
            h = (RowHolder) convertView.getTag();
        }
        SoftwareItem s = getItem(position);
        h.name.setText(s.name);
        h.meta.setText(s.metaLine());
        h.rating.setRating((float) s.averageRating);
        String stats = Formatters.count(s.downloadCount) + "次下载";
        if (s.ratingCount > 0) {
            stats += " · " + String.format(Locale.CHINA, "%.1f分", s.averageRating);
        }
        String d = Formatters.shortDate(s.createdAt);
        if (d.length() > 0) stats += " · " + d + "收录";
        h.stats.setText(stats);
        bindBadge(h.badge, s);
        bindType(h.typeTag, s);
        bindIcon(h.icon, s);
        bindDownload(h.dlBtn, s);
        return convertView;
    }

    private static class RowHolder {
        ImageView icon;
        TextView name;
        TextView meta;
        RatingBar rating;
        TextView stats;
        TextView badge;
        TextView typeTag;
        TextView dlBtn;
    }

    // ================================ 公共绑定 ================================

    private void bindDownload(TextView btn, final SoftwareItem s) {
        if (s.downloadable()) {
            btn.setEnabled(true);
            btn.setAlpha(1f);
            if (mDownloadClick != null) {
                btn.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        mDownloadClick.onDownloadClick(s, v);
                    }
                });
            } else {
                btn.setOnClickListener(null);
            }
        } else {
            btn.setEnabled(false);
            btn.setAlpha(0.4f);
            btn.setOnClickListener(null);
        }
    }

    private void bindBadge(TextView badge, SoftwareItem s) {
        int vis = View.GONE;
        if (s.isOfficial) {
            badge.setText(R.string.badge_official);
            badge.setBackgroundResource(R.drawable.badge_official_solid);
            badge.setTextColor(0xFFFFFFFF);
            vis = View.VISIBLE;
        } else if (s.collectionId != null) {
            // 合集卡片：点进详情可切换版本
            badge.setText(R.string.badge_collection);
            badge.setBackgroundResource(R.drawable.badge_bg);
            badge.setTextColor(0xFF2563EB);
            vis = View.VISIBLE;
        } else if (s.isHot) {
            badge.setText(R.string.badge_hot);
            badge.setBackgroundResource(R.drawable.badge_bg);
            badge.setTextColor(0xFF2563EB);
            vis = View.VISIBLE;
        }
        badge.setVisibility(vis);
    }

    /** 类型标识：车机版 / 手机版（后端 type 字段），空则隐藏 */
    private void bindType(TextView tag, SoftwareItem s) {
        if (s.type != null && s.type.length() > 0 && !"null".equalsIgnoreCase(s.type)) {
            tag.setText(s.type);
            tag.setVisibility(View.VISIBLE);
        } else {
            tag.setVisibility(View.GONE);
        }
    }

    private void bindIcon(ImageView icon, SoftwareItem s) {
        if (s.thumbnailUrl == null || s.thumbnailUrl.length() == 0 || "null".equals(s.thumbnailUrl)) {
            icon.setImageDrawable(new LetterDrawable(s.name));
        } else {
            ImageLoader.get(icon.getContext()).display(icon, s.thumbnailUrl, R.drawable.placeholder_app);
        }
    }
}
