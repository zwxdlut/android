package com.example.my_native_service;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.os.RemoteException;
import android.util.Log;

import androidx.annotation.Nullable;

public class MyNativeService extends Service {
    static {
        System.loadLibrary("native-lib");
    }

    private static final String TAG = "MyNativeService";

    public native void init();
    public native void deinit();
    public native int call(int param);

    public class MyBinder extends IMyNativeService.Stub{
        @Override
        public int call(int param) throws RemoteException {
            return MyNativeService.this.call(param);
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "onCreate");
        init();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "onStartCommand: intent = " + intent + ", flags = " + flags + ", startId = " + startId);
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "onDestroy");
        deinit();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        Log.d(TAG, "onBind: intent = " + intent);
        return new MyBinder();
    }

    @Override
    public boolean onUnbind(Intent intent) {
        Log.d(TAG, "onUnbind: intent = " + intent);
        return super.onUnbind(intent);
    }
}
