package com.mxnavi.dvr.utils;

import android.content.Context;
import android.os.Environment;
import android.util.Log;

import java.io.File;

public class MediaDirUtil {
    public static boolean isRemovable = false;
    public static String ROOT_DIR = Environment.getExternalStorageDirectory().getAbsolutePath();
    public static String RELATIVE_DIR = "";
    public static String RELATIVE_IMAGE_DIR = /*RELATIVE_DIR + File.separator +*/ Environment.DIRECTORY_PICTURES;
    public static String RELATIVE_VIDEO_DIR = /*RELATIVE_DIR + File.separator +*/ Environment.DIRECTORY_MOVIES;
    private static final String TAG = "DVR-" + MediaDirUtil.class.getSimpleName();

    public enum Type {
        ROOT,
        IMAGE,
        VIDEO,
    }

    public static String getDir(Context context, Type type) {
        File dir = new File(ROOT_DIR, RELATIVE_DIR);

        if (!dir.exists()) {
            if (dir.mkdirs()) {
                Log.e(TAG, "getDir: make directory " + dir.getAbsolutePath());
            } else {
                Log.e(TAG, "getDir: make directory " + dir.getAbsolutePath() + " failed!");
                return null;
            }
        }

        String child = null;

        switch (type) {
            case ROOT:
                child = RELATIVE_DIR;
                break;
            case IMAGE:
                child = RELATIVE_IMAGE_DIR;
                break;
            case VIDEO:
                child = RELATIVE_VIDEO_DIR;
                break;
            default:
                return null;
        }

        dir = new File(ROOT_DIR, child);

        if (!dir.exists()) {
            if (dir.mkdirs()) {
                Log.e(TAG, "getDir: make directory " + dir.getAbsolutePath());
            } else {
                Log.e(TAG, "getDir: make directory " + dir.getAbsolutePath() + " failed!");
                return null;
            }
        }

        return dir.getAbsolutePath();
    }
}
