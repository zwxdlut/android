package com.example.usb;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.storage.StorageManager;
import android.os.storage.StorageVolume;
import android.provider.DocumentsContract;
import android.provider.MediaStore;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.documentfile.provider.DocumentFile;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "MainActivity";
    private static final int PERMISSION_REQUEST = 1;
    private static final int OPEN_DOCUMENT_TREE_CODE = 2;
    private String removableDir = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        List<String> permissions = new ArrayList<>();
        permissions.add(Manifest.permission.WRITE_EXTERNAL_STORAGE);
        permissions.add(Manifest.permission.READ_EXTERNAL_STORAGE);
        ActivityCompat.requestPermissions(this, permissions.toArray(new String[0]), PERMISSION_REQUEST);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        Log.i(TAG, "onRequestPermissionsResult: requestCode = " + requestCode + ", permissions = " + Arrays.asList(permissions)
                + ", grantResults = " + Arrays.stream(grantResults).boxed().collect(Collectors.toList()));

        if (requestCode == PERMISSION_REQUEST) {
            boolean isGranted = true;

            for (int result : grantResults) {
                if (PackageManager.PERMISSION_GRANTED != result) {
                    isGranted = false;
                    break;
                }
            }

            if (isGranted) {
                Log.i(TAG, "onRequestPermissionsResult: permission granted requestCode = " + requestCode);
                init();
            } else {
                Log.e(TAG, "onRequestPermissionsResult: permission denied requestCode = " + requestCode);
                finish();
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable @org.jetbrains.annotations.Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        Log.e(TAG, "onActivityResult: requestCode = " + requestCode + ", resultCode = " + resultCode);

        if (OPEN_DOCUMENT_TREE_CODE == requestCode && Activity.RESULT_OK == resultCode) {
            // The result data contains a URI for the document or directory that
            // the user selected.
            if (null != data) {
                Uri uri = data.getData();
                Log.e(TAG, "onActivityResult: data = " + data);
                Log.e(TAG, "onActivityResult: uri = " + uri.toString());

                Set<String> volumeNames = MediaStore.getExternalVolumeNames(this);
                for (String volumeName : volumeNames) {
                    Uri contentUri = MediaStore.Video.Media.getContentUri(volumeName);
                    Log.e(TAG, "onActivityResult: content uri = " + contentUri.toString());
                }

                Log.e(TAG, "onActivityResult: content uri = " + MediaStore.Video.Media.EXTERNAL_CONTENT_URI);

                DocumentFile documentFile = DocumentFile.fromTreeUri(this, uri);

                if (null != documentFile) {
                    DocumentFile df = documentFile.createDirectory("abc");
                    Log.e(TAG, "onActivityResult: df = " + df.getUri());
                }


//                File dir = new File(uri.getPath(), "abc");
//
//                if (!dir.exists()) {
//                    if (dir.mkdirs()) {
//                        Log.e(TAG, "init: make directory " + dir.getAbsolutePath());
//                    } else {
//                        Log.e(TAG, "init: make directory " + dir.getAbsolutePath() + " failed!");
//                    }
//                }
            }
        }
    }

    private void init() {
        String[] dirs = getRemovableStorageDirs();

        Log.e(TAG, "init: storage dirs:" + Arrays.toString(getStorageDirs()));
        Log.e(TAG, "init: removable storage dirs:" + Arrays.toString(dirs));

        if (null != dirs && 0 < dirs.length) {
            removableDir = dirs[0];
            showOpenDocumentTree(removableDir);
        }
    }

    private String[] getStorageDirs() {
        String[] dirs = null;

        try {
            StorageManager mStorageManager = (StorageManager) getSystemService(Context.STORAGE_SERVICE);
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
    private String[] getRemovableStorageDirs() {
        List<String> dirs = new ArrayList<>();

        /* Scan removable storage */
        try {
            StorageManager sm = (StorageManager) getSystemService(Context.STORAGE_SERVICE);
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

    private boolean checkStorageMounted(String mountPoint) {
        if (mountPoint == null) {
            return false;
        }

        try {
            StorageManager storageManager = (StorageManager) getSystemService(Context.STORAGE_SERVICE);
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

    private void showOpenDocumentTree(String path) {
        StorageManager sm = getSystemService(StorageManager.class);
        StorageVolume volume = getSystemService(StorageManager.class).getStorageVolume(new File(path));

        if (null != volume) {
            Log.e(TAG, "showOpenDocumentTree: volume = " + volume.toString());
            Intent intent = volume.createOpenDocumentTreeIntent();
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            intent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            startActivityForResult(intent, OPEN_DOCUMENT_TREE_CODE);
        }


        Set<String> volumeNames = MediaStore.getExternalVolumeNames(this);
        Uri contentUri = null;
        for (String volumeName : volumeNames) {
            contentUri = MediaStore.Video.Media.getContentUri(volumeName);
            Log.e(TAG, "showOpenDocumentTree: content uri = " + contentUri.toString());
        }

        // Choose a directory using the system's file picker.
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);

        // Provide read access to files and sub-directories in the user-selected
        // directory.
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        intent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION);

        // Optionally, specify a URI for the directory that should be opened in
        // the system file picker when it loads.
        intent.putExtra(DocumentsContract.EXTRA_INITIAL_URI, contentUri);

        startActivityForResult(intent, OPEN_DOCUMENT_TREE_CODE);
    }
}
