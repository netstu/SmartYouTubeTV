package edu.mit.mobile.android.appupdater.addons;

import android.app.Activity;
import android.content.*;
import android.net.*;
import android.os.*;
import android.os.Build.VERSION;
import android.support.v4.content.FileProvider;
import android.util.Log;
import android.webkit.*;
import android.widget.Toast;
import com.liskovsoft.smartyoutubetv.common.helpers.PermissionManager;
import edu.mit.mobile.android.appupdater.R;
import edu.mit.mobile.android.appupdater.downloadmanager.MyDownloadManager;
import edu.mit.mobile.android.appupdater.downloadmanager.MyDownloadManager.MyRequest;

import java.io.*;

/**
 * Usage:
 * <pre>
 *   atualizaApp = new UpdateApp(ctx);
 *   atualizaApp.execute("http://serverurl/appfile.apk");
 * </pre>
 */
public class UpdateApp extends AsyncTask<Uri[],Void,Void> {
    private static final String TAG = UpdateApp.class.getSimpleName();
    private final Context mContext;

    public UpdateApp(Context context) {
        mContext = context;
    }

    @Override
    protected Void doInBackground(Uri[]... args) {
        Uri[] uris = args[0];

        String path = null;
        for (Uri uri : uris) {
            if (URLUtil.isValidUrl(uri.toString())) {
                path = downloadPackage(uri.toString());
                if (path != null)
                    break;
            }
        }

        if (path != null) {
            installPackage(path);
        } else {
            showMessage(mContext.getResources().getString(R.string.cant_download_msg));
        }

        return null;
    }

    private void showMessage(final String msg) {
        new Handler(Looper.getMainLooper()).post(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(mContext, msg, Toast.LENGTH_LONG).show();
            }
        });
    }

    private File getCacheDir() {
        // NOTE: Android 6.0 fix
        File cacheDir = mContext.getExternalCacheDir();
        if (!PermissionManager.hasStoragePermissions((Activity) mContext)) {
            showMessage("Storage permission not granted!");
            return null;
        }

        if (cacheDir == null) { // no storage, try to use SDCard
            cacheDir = Environment.getExternalStorageDirectory();
            showMessage("Please, make sure that SDCard is mounted");
        }

        return cacheDir;
    }

    private String downloadPackage(String uri) {
        File cacheDir = getCacheDir();
        if (cacheDir == null) {
            return null;
        }
        File outputFile = new File(cacheDir, "update.apk");
        String path = null;
        try {
            MyDownloadManager manager = new MyDownloadManager(mContext);
            MyRequest request = new MyRequest(Uri.parse(uri));
            request.setDestinationUri(Uri.fromFile(outputFile));
            try {
                long id = manager.enqueue(request);
                int size = manager.getSizeForDownloadedFile(id);
                path = size > 1_000_000 ? manager.getUriForDownloadedFile(id).getPath() : null; // it could be a web page instead of apk
            } catch (IllegalStateException ex) { // 403 or something else
                Log.d(TAG, ex.toString());
            }
        } catch (IllegalStateException ex) { // CANNOT OBTAIN WRITE PERMISSIONS
            Log.e(TAG, ex.getMessage(), ex);
            // MessageHelpers.showMessage(mContext, TAG, ex);
        }
        return path;
    }

    // NOTE: as of Oreo you must also add the REQUEST_INSTALL_PACKAGES permission to your manifest. Otherwise it just silently fails
    private void installPackage(String packagePath) {
        if (packagePath == null) {
            return;
        }
        Intent intent = new Intent(Intent.ACTION_VIEW);
        Uri file = getFileUri(packagePath);
        intent.setDataAndType(file, "application/vnd.android.package-archive");
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_GRANT_READ_URI_PERMISSION); // without this flag android returned a intent error!
        mContext.startActivity(intent);
    }

    private Uri getFileUri(String packagePath) {
        // if your targetSdkVersion is 24 or higher, we have to use FileProvider class
        // https://stackoverflow.com/questions/38200282/android-os-fileuriexposedexception-file-storage-emulated-0-test-txt-exposed
        if (VERSION.SDK_INT >= 24) {
            return FileProvider.getUriForFile(mContext, mContext.getApplicationContext().getPackageName() + ".update_provider", new File(packagePath));
        } else {
            return Uri.fromFile(new File(packagePath));
        }
    }
}
