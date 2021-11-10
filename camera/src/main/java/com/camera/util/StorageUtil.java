package com.camera.util;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.Environment;
import android.os.storage.StorageManager;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

public class StorageUtil {
    public static String[] getStorageDirs(Context context) {
        String[] dirs = null;

        try {
            StorageManager mStorageManager = (StorageManager) context.getSystemService(Context.STORAGE_SERVICE);
            if (null == mStorageManager) {
                return null;
            }

            Method mMethodGetPaths = mStorageManager.getClass().getMethod("getVolumePaths");
            dirs = (String[]) mMethodGetPaths.invoke(mStorageManager);
        } catch (Exception e) {
            e.printStackTrace();
        }

        return dirs;
    }

    @SuppressLint("DiscouragedPrivateApi")
    public static String[] getRemovableStorageDirs(Context context) {
        List<String> dirs = new ArrayList<>();

        /* scan removable storage */
        try {
            StorageManager sm = (StorageManager) context.getSystemService(Context.STORAGE_SERVICE);
            if (null == sm) {
                return null;
            }

            Method getVolumeList = sm.getClass().getDeclaredMethod("getVolumeList");
            Object[] volumeList = (Object[]) getVolumeList.invoke(sm);
            if (null == volumeList) {
                return null;
            }

            for (Object volume : volumeList) {
                Method getPath = volume.getClass().getDeclaredMethod("getPath");
                Method isRemovable = volume.getClass().getDeclaredMethod("isRemovable");
                String dir = (String) getPath.invoke(volume);
                boolean removable = (Boolean) isRemovable.invoke(volume);

                if (removable) {
                    dirs.add(dir);
                }
            }
        } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
            e.printStackTrace();
        }

        return dirs.toArray(new String[0]);
    }

    public static boolean checkStorageMounted(Context context, String mountPoint) {
        if (mountPoint == null) {
            return false;
        }

        try {
            StorageManager storageManager = (StorageManager) context.getSystemService(Context.STORAGE_SERVICE);
            if (null == storageManager) {
                return false;
            }

            Method getVolumeState = storageManager.getClass().getMethod("getVolumeState", String.class);
            String state = (String) getVolumeState.invoke(storageManager, mountPoint);

            return Environment.MEDIA_MOUNTED.equals(state);
        } catch (Exception e) {
            e.printStackTrace();
        }

        return false;
    }
}
