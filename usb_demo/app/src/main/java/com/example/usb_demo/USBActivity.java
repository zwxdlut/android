package com.example.usb_demo;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import android.annotation.SuppressLint;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.os.storage.StorageManager;
import android.provider.MediaStore;
import android.util.Log;
import android.widget.ArrayAdapter;
import android.widget.ListView;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;

public class USBActivity extends AppCompatActivity {
    private static final String TAG = "USBActivity";
    public static final int MSG_USB_DEVICE_PERMISSION_GRANTED = 100;
    public static final int MSG_USB_DEVICE_DETACCHED = 101;
    public static final int MSG_USB_DEVICE_AUDIO_UPDATE = 102;
    public static final int MSG_ = 101;
    private USBHandler handler = new USBHandler();
    private USBReceiver receiver = new USBReceiver(handler);
    private USBScanThread thread = null;
    private List<String> audioList = new ArrayList<String>();

    public class USBHandler extends Handler {
        @Override
        public void handleMessage(@NonNull Message msg) {
            Log.d(TAG, "handleMessage: msg = " + msg);
            switch (msg.what) {
                case MSG_USB_DEVICE_PERMISSION_GRANTED:
                    thread = new USBScanThread();
                    thread.start();
                    break;
                case MSG_USB_DEVICE_DETACCHED:
                    finish();
                    break;
                case MSG_USB_DEVICE_AUDIO_UPDATE:
                    ArrayAdapter<String> adapter = new ArrayAdapter<String>(USBActivity.this, android.R.layout.simple_list_item_1, audioList);
                    ListView listView = (ListView) findViewById(R.id.list_view);
                    listView.setAdapter(adapter);
                    break;
                default:
                    super.handleMessage(msg);
                    break;
            }
        }
    }

    public class USBScanThread extends Thread {
        public Boolean stop = false;

        @Override
        public void run() {
            super.run();
            Log.d(TAG, "USBScanThread run");

            /* Get storage directories */
            String dirs[] = getStorageDirs();
            if (null != dirs) {
                for (String dir : dirs) {
                    Log.d(TAG, "USBScanThread run: storage directory = " + dir);
                }
            } else {
                Log.w(TAG, "USBScanThread run: no storage directory!");
                stop = true;
            }

            /* Scan Audio */
            while (!stop) {
                final String[] projection = new String[]{
                        MediaStore.Audio.Media._ID, MediaStore.Audio.Media.DISPLAY_NAME,
                        MediaStore.Audio.Media.TITLE, MediaStore.Audio.Media.DURATION,
                        MediaStore.Audio.Media.ARTIST, MediaStore.Audio.Media.ALBUM,
                        MediaStore.Audio.Media.YEAR, MediaStore.Audio.Media.MIME_TYPE,
                        MediaStore.Audio.Media.SIZE, MediaStore.Audio.Media.DATA};
                Uri uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
                String selection = null;
                String selectionArgs[] = null;
                Cursor cursor = getContentResolver().query(uri, projection, selection, selectionArgs, MediaStore.Audio.Media.DEFAULT_SORT_ORDER);

                if (0 < cursor.getCount()) {
                    int i = 0;

                    audioList.clear();
                    while (cursor.moveToNext()) {
                        String title = cursor.getString(cursor.getColumnIndex(MediaStore.Audio.Media.TITLE));
                        String data = cursor.getString(cursor.getColumnIndex(MediaStore.Audio.Media.DATA));

                        Log.d(TAG, "Audio[" + i + "]: title = " + title + ", data = " + data);
                        audioList.add(title);
                        i++;
                    }
                    handler.sendEmptyMessage(MSG_USB_DEVICE_AUDIO_UPDATE);
                }

                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }

            Log.d(TAG, "USBScanThread exit");
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_u_s_b);
        Log.d(TAG, "onCreate: this = " + this);

        UsbManager usbManager = (UsbManager) getSystemService(Context.USB_SERVICE);
        HashMap<String, UsbDevice> deviceList = usbManager.getDeviceList();
        Iterator<UsbDevice> deviceIterator = deviceList.values().iterator();
        IntentFilter filter = new IntentFilter();

        /* Register broadcast receiver */
        filter.addAction(USBReceiver.ACTION_USB_PERMISSION);
        filter.addAction("android.hardware.usb.action.USB_DEVICE_DETACHED");
        filter.addAction("android.hardware.usb.action.USB_DEVICE_ATTACHED");
        registerReceiver(receiver, filter);

        /* Scan USB devices */
        while (deviceIterator.hasNext()) {
            UsbDevice device = deviceIterator.next();
            PendingIntent permissionIntent = PendingIntent.getBroadcast(this, 0, new Intent(USBReceiver.ACTION_USB_PERMISSION), 0);

            Log.d(TAG, "USB device: " + device);
            usbManager.requestPermission(device, permissionIntent);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "onDestroy: this = " + this);

        if (null != thread) {
            thread.stop = true;
            try {
                thread.join();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            thread = null;
        }

        unregisterReceiver(receiver);
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
}
