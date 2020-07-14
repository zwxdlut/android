package com.example.my_activity;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.os.RemoteException;
import android.util.Log;

import androidx.annotation.Nullable;

public class MyAidlService extends Service {
    private static final String TAG = "MyAidlService";

    private IMyAidlService.Stub stub = new IMyAidlService.Stub() {
        @Override
        public int call(int param) {
            return MyAidlService.this.call(param);
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "onCreate");
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
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        Log.d(TAG, "onBind: intent = " + intent);
        return stub;
    }

    @Override
    public boolean onUnbind(Intent intent) {
        Log.d(TAG, "onUnbind: intent = " + intent);
        return super.onUnbind(intent);
    }

    public int call(int param) {
        Log.d(TAG, "call: param = " + param);
        return 0;
    }
}