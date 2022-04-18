package com.storage.dao;

import android.content.Context;
import android.util.Log;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;

import com.storage.util.LocationRecorder;

import java.io.File;

@Database(entities = {CloudInfo.class}, version = 1, exportSchema =false)
public abstract class CloudDatabase extends RoomDatabase {
    public static String sDirectory = null;
    private static final String TAG = "DVR-" + LocationRecorder.class.getSimpleName();
    private static CloudDatabase instance = null;

    public static CloudDatabase getInstance(Context context) {
        if (null == instance) {
            synchronized (CloudDatabase.class) {
                if (null == instance) {
                    if (null == sDirectory) {
                        File dir = new File(context.getExternalFilesDir(null), "database");

                        if (!dir.exists()) {
                            if (dir.mkdirs()) {
                                Log.e(TAG, "CloudDatabase: make directory " + dir);
                            } else {
                                Log.e(TAG, "CloudDatabase: make directory " + dir + " failed!");
                            }
                        }

                        sDirectory = dir.getAbsolutePath();
                    }

                    instance = Room.databaseBuilder(
                            context.getApplicationContext(), CloudDatabase.class,
                            sDirectory + File.separator + "cloud.db").build();
                }
            }
        }

        return  instance;
    }

    public abstract CloudInfoDao cloudInfoDao();
}
