package asia.carmix.market.provider;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;

import java.io.File;
import java.io.FileNotFoundException;

/**
 * 极简 APK 文件提供者（替代 androidx FileProvider，避免引入 AndroidX）。
 * 暴露本应用外部私有下载目录中的 APK，供系统 PackageInstaller 读取。
 *
 * URI 形态：content://asia.carmix.market.apkfiles/<文件名>
 */
public class ApkFileProvider extends ContentProvider {

    public static final String AUTHORITY = "asia.carmix.market.apkfiles";

    @Override
    public boolean onCreate() {
        return true;
    }

    private File fileFor(Uri uri) {
        String name = uri.getLastPathSegment();
        // 防目录穿越
        if (name == null || name.contains("/") || name.contains("\\") || name.contains("..")) {
            return null;
        }
        File dir = getContext().getExternalFilesDir(android.os.Environment.DIRECTORY_DOWNLOADS);
        return dir == null ? null : new File(dir, name);
    }

    /** 供下载完成后的安装 Intent 使用 */
    public static Uri uriFor(String fileName) {
        return Uri.parse("content://" + AUTHORITY + "/" + Uri.encode(fileName));
    }

    @Override
    public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        File f = fileFor(uri);
        if (f == null || !f.exists()) throw new FileNotFoundException(String.valueOf(uri));
        return ParcelFileDescriptor.open(f, ParcelFileDescriptor.MODE_READ_ONLY);
    }

    @Override
    public String getType(Uri uri) {
        return "application/vnd.android.package-archive";
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection,
                        String[] selectionArgs, String sortOrder) {
        File f = fileFor(uri);
        if (projection == null) {
            projection = new String[]{OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE};
        }
        MatrixCursor cursor = new MatrixCursor(projection);
        if (f != null && f.exists()) {
            Object[] row = new Object[projection.length];
            for (int i = 0; i < projection.length; i++) {
                if (OpenableColumns.DISPLAY_NAME.equals(projection[i])) row[i] = f.getName();
                else if (OpenableColumns.SIZE.equals(projection[i])) row[i] = f.length();
                else row[i] = null;
            }
            cursor.addRow(row);
        }
        return cursor;
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        return 0;
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        return null;
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        return 0;
    }
}
