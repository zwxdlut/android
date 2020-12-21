package com.example.my_activity;

import androidx.appcompat.app.AppCompatActivity;

import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.database.ContentObserver;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.util.Log;
import android.view.View;

import com.example.my_native_service.IMyNativeService;
import com.example.my_service.IMyService;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "MainActivity";

    /* Content resolver */
    private ContentResolver contentResolver = null;
    private Uri uri = Uri.parse("content://my_provider");

    /* Local service */
    private MyLocalService.MyBinder localService = null;
    private ServiceConnection localConn = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            Log.d(TAG, "onServiceConnected(local): name = " + name);
            localService = (MyLocalService.MyBinder)service;
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            Log.d(TAG, "onServiceDisconnected(local): name = " + name);
            localService = null;
        }
    };

    /* Messenger service */
    private Messenger msgService = null;

    private ServiceConnection msgConn = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            Log.d(TAG, "onServiceConnected(messenger): name = " + name);
            msgService = new Messenger(service);
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            Log.d(TAG, "onServiceDisconnected(messenger): name = " + name);
            msgService = null;
        }
    };

    private Messenger replyMsg = new Messenger(new ReplyHandler());

    private static class ReplyHandler extends Handler {
        @Override
        public void handleMessage(Message msg) {
            Log.d(TAG, "handleMessage: msg = " + msg + ", data = " + msg.getData());
            switch (msg.what) {
                case MyMessengerService.MSG_CALL:
                    Log.d(TAG, "handleMessage: Messenger service call return " + msg.getData().getInt("call"));
                    break;
                default:
                    super.handleMessage(msg);
            }
        }
    }

    /* Aidl service */
    private IMyAidlService aidlService = null;
    private ServiceConnection aidlConn = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            Log.d(TAG, "onServiceConnected(aidl): name = " + name);
            aidlService = IMyAidlService.Stub.asInterface(service);
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            Log.d(TAG, "onServiceDisconnected(aidl): name = " + name);
            aidlService = null;
        }
    };

    /* Remote service */
    private IMyService remoteService = null;
    private ServiceConnection remoteConn = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            Log.d(TAG, "onServiceConnected(remote): name = " + name);
            remoteService = IMyService.Stub.asInterface(service);
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            Log.d(TAG, "onServiceDisconnected(remote): name = " + name);
            remoteService = null;
        }
    };

    /* Remote native service */
    private IMyNativeService remoteNativeService = null;
    private ServiceConnection remoteNativeConn = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            Log.d(TAG, "onServiceConnected(remote native): name = " + name);
            remoteNativeService = IMyNativeService.Stub.asInterface(service);
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            Log.d(TAG, "onServiceDisconnected(remote native): name = " + name);
            remoteNativeService = null;
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Log.d(TAG, "onCreate");
        bindContentResolver();
        bindLocalService();
        bindMessengerService();
        bindAidlService();
        bindRemoteService();
        bindRemoteNativeService();
        bindBroadcast();
    }

    private void bindContentResolver() {
        contentResolver = getContentResolver();
        contentResolver.registerContentObserver(uri, true, new ContentObserver(new Handler()) {
            @Override
            public void onChange(boolean selfChange) {
                super.onChange(selfChange);
                Log.d(TAG, "onChange: selfChange = " + selfChange);
            }
        });

        findViewById(R.id.btn_insert).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                ContentValues values = new ContentValues();

                values.put("name", "A");
                Log.d(TAG, "onClick: Insert: uri = " + uri + ", values = " + values);
                Log.d(TAG, "onClick: Insert return " + contentResolver.insert(uri, values));
            }
        });

        findViewById(R.id.btn_delete).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Log.d(TAG, "onClick: Delete: uri = " + uri);
                Log.d(TAG, "onClick: Delete return " + contentResolver.delete(uri, "delete_where", null));
            }
        });

        findViewById(R.id.btn_update).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                ContentValues values = new ContentValues();

                values.put("name", "B");
                Log.d(TAG, "onClick: Update: uri = " + uri + ", values = " + values);
                Log.d(TAG, "onClick: Update return " + contentResolver.update(uri, values, "update_where", null));
                contentResolver.notifyChange(uri, null);
            }
        });

        findViewById(R.id.btn_query).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Log.d(TAG, "onClick: Query: uri = " + uri);
                Log.d(TAG, "onClick: Query return " + contentResolver.query(uri, null, "query_where", null, null));
            }
        });
    }

    private void bindLocalService() {
        findViewById(R.id.btn_bind_local).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Log.d(TAG, "onClick: Bind local service");
                bindService(new Intent(MainActivity.this, MyLocalService.class), localConn, Context.BIND_AUTO_CREATE);
            }
        });

        findViewById(R.id.btn_unbind_local).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Log.d(TAG, "onClick: Unbind local service");
                if (null == localService)
                    return;
                unbindService(localConn);
                localService = null;
            }
        });

        findViewById(R.id.btn_call_local).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Log.d(TAG, "onClick: Local service call");
                if (null == localService) {
                    Log.d(TAG, "onClick: Local service is null");
                    return;
                }
                Log.d(TAG, "onClick: Local service call return " + localService.call(100));
            }
        });
    }

    private void bindMessengerService() {
        findViewById(R.id.btn_bind_msg).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Log.d(TAG, "onClick: Bind messenger service");
                bindService(new Intent(MainActivity.this, MyMessengerService.class), msgConn, Context.BIND_AUTO_CREATE);
            }
        });

        findViewById(R.id.btn_unbind_msg).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Log.d(TAG, "onClick: Unbind messenger service");
                if (null == msgService)
                    return;
                unbindService(msgConn);
                msgService = null;
            }
        });

        findViewById(R.id.btn_call_msg).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Log.d(TAG, "onClick: Messenger service call");
                if (null == msgService) {
                    Log.d(TAG, "onClick: Messenger service is null");
                    return;
                }

                Message msg = Message.obtain(null, MyMessengerService.MSG_CALL, 0, 0);
                Bundle bundle = new Bundle();

                /* TODO: Parameters parcelable */
                bundle.putInt("call", 100);
                msg.setData(bundle);
                msg.replyTo = replyMsg;
                Log.d(TAG, "onClick: Send to messenger service: msg = " + msg + ", data = " + msg.getData());
                try {
                    msgService.send(msg);
                } catch (RemoteException e) {
                    e.printStackTrace();
                }
            }
        });
    }

    private void bindAidlService() {
        findViewById(R.id.btn_bind_aidl).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Log.d(TAG, "onClick: Bind aidl service");
                bindService(new Intent(MainActivity.this, MyAidlService.class), aidlConn, Context.BIND_AUTO_CREATE);
            }
        });

        findViewById(R.id.btn_unbind_aidl).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Log.d(TAG, "onClick: Unbind aidl service");
                if (null == aidlService)
                    return;
                unbindService(aidlConn);
                aidlService = null;
            }
        });

        findViewById(R.id.btn_call_aidl).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Log.d(TAG, "onClick: Aidl service call");
                if (null == aidlService) {
                    Log.d(TAG, "onClick: Aidl service is null");
                    return;
                }

                try {
                    Log.d(TAG, "onClick: Aidl service call return " + aidlService.call(100));
                } catch (RemoteException e) {
                    e.printStackTrace();
                }
            }
        });
    }

    private void bindRemoteService() {
        findViewById(R.id.btn_bind_remote).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Log.d(TAG, "onClick: Bind remote service");

                Intent intent = new Intent("com.example.my_service.MyService");

                intent.setPackage("com.example.my_service");
                bindService(intent, remoteConn, Context.BIND_AUTO_CREATE);
            }
        });

        findViewById(R.id.btn_unbind_remote).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Log.d(TAG, "onClick: Unbind remote service");
                if (null == remoteService)
                    return;
                unbindService(remoteConn);
                remoteService = null;
            }
        });

        findViewById(R.id.btn_call_remote).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Log.d(TAG, "onClick: Remote service call");
                if (null == remoteService) {
                    Log.d(TAG, "onClick: Remote service is null");
                    return;
                }

                try {
                    Log.d(TAG, "onClick: Remote service call return " + remoteService.call(100));
                } catch (RemoteException e) {
                    e.printStackTrace();
                }
            }
        });
    }

    private void bindRemoteNativeService() {
        findViewById(R.id.btn_bind_remote_native).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Log.d(TAG, "onClick: Bind remote native service");

                Intent intent = new Intent("com.example.my_service.MyNativeService");

                intent.setPackage("com.example.my_native_service");
                bindService(intent, remoteNativeConn, Context.BIND_AUTO_CREATE);
            }
        });

        findViewById(R.id.btn_unbind_remote_native).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Log.d(TAG, "onClick: Unbind remote native service");
                if (null == remoteNativeService)
                    return;
                unbindService(remoteNativeConn);
                remoteNativeService = null;
            }
        });

        findViewById(R.id.btn_call_remote_native).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Log.d(TAG, "onClick: Remote native service call");
                if (null == remoteNativeService) {
                    Log.d(TAG, "onClick: Remote native service is null");
                    return;
                }

                try {
                    Log.d(TAG, "onClick: Remote native service call return " + remoteNativeService.call(100));
                } catch (RemoteException e) {
                    e.printStackTrace();
                }
            }
        });
    }

    private void bindBroadcast() {
        findViewById(R.id.btn_send_broadcast).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Log.d(TAG, "onClick: Send broadcast");

                Intent intent = new Intent("android.intent.action.MY_BROADCAST");

                intent.setComponent(new ComponentName("com.example.my_receiver", "com.example.my_receiver.MyReceiver"));
                sendBroadcast(intent);
            }
        });
    }
}
