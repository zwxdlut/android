package com.mxnavi.dvr;

import android.app.Application;
import android.content.Intent;
import android.os.Build;
import android.os.storage.StorageManager;
import android.os.storage.StorageVolume;
import android.util.Log;

import com.camera.util.StorageUtil;
import com.mxnavi.dvr.utils.MediaDirUtil;
import com.mxnavi.dvr.utils.PhoneNumberManager;
import com.mxnavi.dvr.web.WebManager;
import com.storage.MediaProviderService;
import com.storage.dao.CloudDatabase;
import com.storage.util.LocationRecorder;

import java.io.File;
import java.util.Arrays;

public class DVRApplication extends Application {
    private static final String TAG = "DVR-" + DVRApplication.class.getSimpleName();

    @Override
    public void onCreate() {
        super.onCreate();
        Log.i(TAG, "onCreate");

        String[] dirs = StorageUtil.getRemovableStorageDirs(this);
        if (null != dirs && 0 < dirs.length) {
            StorageManager sm = getSystemService(StorageManager.class);
            StorageVolume volume = sm.getStorageVolume(new File(dirs[0]));

            Log.i(TAG, "onCreate: removable dirs = " + Arrays.toString(dirs));
            Log.i(TAG, "onCreate: removable volume = " + volume);

            if (null != volume) {
                MediaDirUtil.isRemovable = true;
                MediaDirUtil.ROOT_DIR = dirs[0];

                File[] files = getExternalFilesDirs(null);

                for (File file : files) {
                    volume = sm.getStorageVolume(file);

                    if (null != volume && volume.isRemovable()) {
                        LocationRecorder.getInstance().setDir(new File(file, "Locations").getAbsolutePath());

                        File dir = new File(file, "database");
                        if (!dir.exists()) {
                            if (dir.mkdirs()) {
                                Log.e(TAG, "CloudDatabase: make directory " + dir);
                            } else {
                                Log.e(TAG, "CloudDatabase: make directory " + dir + " failed!");
                            }
                        }
                        CloudDatabase.sDirectory = dir.getAbsolutePath();

                        break;
                    }
                }
            }
        }

        WebManager.getInstance().init();
        PhoneNumberManager.getInstance().init(DVRApplication.this);
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(new Intent(this, MediaProviderService.class));
        } else {
            startService(new Intent(this, MediaProviderService.class));
        }
    }

    @Override
    public void onTerminate() {
        Log.i(TAG, "onTerminate");
        stopService(new Intent(this, MediaProviderService.class));
        super.onTerminate();
    }
}
