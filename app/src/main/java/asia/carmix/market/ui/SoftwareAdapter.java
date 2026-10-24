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

import asia.carmix.market.R;
import asia.carmix.market.model.SoftwareItem;
import asia.carmix.market.util.Formatters;
import asia.carmix.market.util.ImageLoader;

public class SoftwareAdapter extends BaseAdapter {

    private final LayoutInflater mInflater;
    private final List<SoftwareItem> mItems = new ArrayList<SoftwareItem>();

    public SoftwareAdapter(LayoutInflater inflater) {
        this.mInflater = inflater;
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
        ViewHolder h;
        if (convertView == null) {
            convertView = mInflater.inflate(R.layout.item_software, parent, false);
            h = new ViewHolder();
            h.icon = (ImageView) convertView.findViewById(R.id.icon);
            h.name = (TextView) convertView.findViewById(R.id.name);
            h.meta = (TextView) convertView.findViewById(R.id.meta);
            h.rating = (RatingBar) convertView.findViewById(R.id.rating);
            h.stats = (TextView) convertView.findViewById(R.id.stats);
            h.badge = (TextView) convertView.findViewById(R.id.badge);
            convertView.setTag(h);
        } else {
            h = (ViewHolder) convertView.getTag();
        }
        SoftwareItem s = getItem(position);
        h.name.setText(s.name);
        h.meta.setText(s.metaLine());
        h.rating.setRating((float) s.averageRating);
        String stats = Formatters.count(s.downloadCount) + "次下载";
        if (s.ratingCount > 0) {
            stats += " · " + String.format(java.util.Locale.CHINA, "%.1f分", s.averageRating);
        }
        h.stats.setText(stats);
        int badgeVis = View.GONE;
        if (s.isOfficial) {
            h.badge.setText(R.string.badge_official);
            badgeVis = View.VISIBLE;
        } else if (s.isHot) {
            h.badge.setText(R.string.badge_hot);
            badgeVis = View.VISIBLE;
        }
        h.badge.setVisibility(badgeVis);
        if (s.thumbnailUrl == null || s.thumbnailUrl.length() == 0 || "null".equals(s.thumbnailUrl)) {
            h.icon.setImageDrawable(new asia.carmix.market.util.LetterDrawable(s.name));
        } else {
            ImageLoader.get(convertView.getContext())
                    .display(h.icon, s.thumbnailUrl, R.drawable.placeholder_app);
        }
        return convertView;
    }

    private static class ViewHolder {
        ImageView icon;
        TextView name;
        TextView meta;
        RatingBar rating;
        TextView stats;
        TextView badge;
    }
}
