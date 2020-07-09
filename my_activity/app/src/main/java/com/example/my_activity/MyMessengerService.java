package com.example.my_activity;

import android.app.Service;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.util.Log;

import androidx.annotation.Nullable;

public class MyMessengerService extends Service {
    private static final String TAG = "MyMessengerService";
    static final int MSG_CALL = 1;

    private class ReceiveHandler extends Handler {
        @Override
        public void handleMessage(Message msg) {
            Message replyMsg;

            Log.d(TAG, "handleMessage: receive msg = " + msg + ", data = " + msg.getData());
            switch (msg.what) {
                case MSG_CALL:
                    Bundle bundle = new Bundle();

                    bundle.putInt("call", MyMessengerService.this.call(msg.getData().getInt("call")));
                    replyMsg = Message.obtain(null, MyMessengerService.MSG_CALL, 0, 0);
                    replyMsg.setData(bundle);
                    break;
                default:
                    replyMsg = Message.obtain(null, -1, 0, 0);
                    super.handleMessage(msg);
            }

            Log.d(TAG, "handleMessage: reply msg = " + msg + ", data = " + replyMsg.getData());
            try {
                msg.replyTo.send(replyMsg);
            } catch (RemoteException e) {
                e.printStackTrace();
            }
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
        return new Messenger(new ReceiveHandler()).getBinder();
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
