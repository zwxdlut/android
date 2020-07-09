package com.example.my_activity;

import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.Nullable;

public class MyLocalService extends Service {
    private static final String TAG = "MyLocalService";
    private MyBinder binder = new MyBinder();

    public class MyBinder extends Binder {
        public int call(int param) {
            return MyLocalService.this.call(param);
        }
    }

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
        return binder;
    }

    @Override
    public boolean onUnbind(Intent intent) {
        Log.d(TAG, "onUnbind intent = " + intent);
        return super.onUnbind(intent);
    }

    public int call(int param) {
        Log.d(TAG, "call: param = " + param);
        return 0;
    }
}
