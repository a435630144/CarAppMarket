package asia.carmix.market.ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.text.format.Formatter;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.util.List;

import asia.carmix.market.R;
import asia.carmix.market.util.ApkDownloader;
import asia.carmix.market.util.DownloadIconDrawable;
import asia.carmix.market.util.Formatters;

/**
 * 下载管理页：上半区正在下载的任务（进度/速度/取消），下半区已落盘安装包（安装/删除）。
 */
public class DownloadManagerActivity extends BaseActivity {

    private LinearLayout mList;
    private TextView mEmpty;
    private LayoutInflater mInflater;

    private final ApkDownloader.CenterListener mListener = new ApkDownloader.CenterListener() {
        @Override
        public void onTasksChanged() {
            render();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_download_manager);
        getActionBar().setTitle(R.string.dl_center_title);
        getActionBar().setDisplayHomeAsUpEnabled(true);

        mList = (LinearLayout) findViewById(R.id.dl_list);
        mEmpty = (TextView) findViewById(R.id.dl_empty);
        mInflater = LayoutInflater.from(this);
    }

    @Override
    protected void onResume() {
        super.onResume();
        ApkDownloader.addCenterListener(mListener);
        render();
    }

    @Override
    protected void onPause() {
        super.onPause();
        ApkDownloader.removeCenterListener(mListener);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void render() {
        if (isFinishing()) return;
        mList.removeAllViews();

        List<ApkDownloader.Task> tasks = ApkDownloader.openTasks();
        List<File> files = ApkDownloader.downloadedApks(this);

        if (tasks.isEmpty() && files.isEmpty()) {
            mEmpty.setVisibility(View.VISIBLE);
            return;
        }
        mEmpty.setVisibility(View.GONE);

        if (!tasks.isEmpty()) {
            mList.addView(sectionHeader(getString(R.string.dl_section_downloading)));
            for (ApkDownloader.Task t : tasks) {
                mList.addView(taskRow(t));
            }
        }
        if (!files.isEmpty()) {
            mList.addView(sectionHeader(getString(R.string.dl_section_files)));
            for (final File f : files) {
                mList.addView(fileRow(f));
            }
        }
    }

    private TextView sectionHeader(String text) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextColor(0xFF6B7280);
        tv.setTextSize(13f);
        tv.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        tv.setPadding(4, 8, 4, 8);
        return tv;
    }

    private View taskRow(final ApkDownloader.Task t) {
        View v = mInflater.inflate(R.layout.item_dl_task, mList, false);
        TextView name = (TextView) v.findViewById(R.id.t_name);
        TextView speed = (TextView) v.findViewById(R.id.t_speed);
        ProgressBar progress = (ProgressBar) v.findViewById(R.id.t_progress);
        TextView state = (TextView) v.findViewById(R.id.t_state);
        TextView cancel = (TextView) v.findViewById(R.id.t_cancel);

        name.setText(t.displayName != null ? t.displayName : t.fileName);
        if (t.state == ApkDownloader.STATE_RUNNING) {
            speed.setText(Formatters.speed(t.speedBps));
            if (t.total > 0) {
                progress.setIndeterminate(false);
                progress.setProgress((int) (t.done * 100 / t.total));
                state.setText(Formatters.bytes(t.done) + " / " + Formatters.bytes(t.total)
                        + " · " + (t.done * 100 / t.total) + "%");
            } else {
                progress.setIndeterminate(true);
                state.setText(Formatters.bytes(t.done));
            }
            cancel.setVisibility(View.VISIBLE);
            cancel.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    ApkDownloader.cancel(t.tag);
                }
            });
        } else {
            speed.setText("");
            progress.setIndeterminate(false);
            progress.setProgress(0);
            state.setText(getString(R.string.dl_state_failed));
            state.setTextColor(0xFFE53935);
            cancel.setVisibility(View.GONE);
        }
        return v;
    }

    private View fileRow(final File f) {
        View v = mInflater.inflate(R.layout.item_dl_file, mList, false);
        ImageView icon = (ImageView) v.findViewById(R.id.f_icon);
        TextView name = (TextView) v.findViewById(R.id.f_name);
        TextView meta = (TextView) v.findViewById(R.id.f_meta);
        TextView install = (TextView) v.findViewById(R.id.f_install);
        TextView delete = (TextView) v.findViewById(R.id.f_delete);

        icon.setImageDrawable(new DownloadIconDrawable(0xFFE8EEFB, 0xFF2563EB));
        name.setText(f.getName());
        meta.setText(Formatters.bytes(f.length()) + " · " + Formatters.dateMillis(f.lastModified()));
        install.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                try {
                    startActivity(ApkDownloader.installIntent(DownloadManagerActivity.this, f.getName()));
                } catch (Exception e) {
                    Toast.makeText(DownloadManagerActivity.this,
                            getString(R.string.install_failed, e.getMessage()), Toast.LENGTH_SHORT).show();
                }
            }
        });
        delete.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                new AlertDialog.Builder(DownloadManagerActivity.this)
                        .setMessage(R.string.dl_delete_confirm)
                        .setPositiveButton(R.string.dl_delete, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                f.delete();
                                Toast.makeText(DownloadManagerActivity.this,
                                        R.string.dl_deleted, Toast.LENGTH_SHORT).show();
                                render();
                            }
                        })
                        .setNegativeButton(R.string.cancel, null)
                        .show();
            }
        });
        return v;
    }
}
