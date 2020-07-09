package com.example.usb_demo;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.os.Handler;
import android.util.Log;

public class USBReceiver extends BroadcastReceiver {
    private static final String TAG = "USBReceiver";
    public static final String ACTION_USB_PERMISSION = "com.android.example.USB_PERMISSION";
    private Handler handler = null;

    public USBReceiver(Handler handler) {
        Log.d(TAG, "constructor: handler = " + handler);
        this.handler = handler;
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        Log.d(TAG, "Receive broadcast: intent = " + intent);

        String action = intent.getAction();
        UsbDevice device = (UsbDevice) intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);

        if (ACTION_USB_PERMISSION.equals(action)) {
            synchronized (this) {
                if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                    if (device != null) {
                        Log.d(TAG, "Permission granted: device =  " + device);
                        handler.sendEmptyMessage(USBActivity.MSG_USB_DEVICE_PERMISSION_GRANTED);
                    }
                } else {
                    Log.d(TAG, "Permission denied: device =  " + device);

                }
            }
        } else if (UsbManager.ACTION_USB_DEVICE_ATTACHED.equals(action)) {
            Log.d(TAG, "USB device attached: device = " + device);
        } else if (UsbManager.ACTION_USB_DEVICE_DETACHED.equals(action)) {
            Log.d(TAG, "USB device detached: device = " + device);
            handler.sendEmptyMessage(USBActivity.MSG_USB_DEVICE_DETACCHED);
        }
    }
}
