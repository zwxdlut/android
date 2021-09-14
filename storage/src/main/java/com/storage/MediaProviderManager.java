package com.storage;

import android.app.Application;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.net.Uri;
import android.os.IBinder;
import android.os.RemoteException;
import android.util.Log;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class MediaProviderManager {
    private static final String TAG = "DVR-" + MediaProviderManager.class.getSimpleName();
    private Context context = null;
    private IMediaProviderManager manager = null;
    private List<MediaProviderCallback> mediaProviderCallbacks = new ArrayList<>();
    private List<UploadCallback> uploadCallbacks = new ArrayList<>();

    private ServiceConnection connection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            Log.i(TAG, "onServiceConnected: name = " + name);
            manager = IMediaProviderManager.Stub.asInterface(service);

            try {
                manager.asBinder().linkToDeath(deathRecipient, 0);
            } catch (RemoteException e) {
                e.printStackTrace();
            }

            for (MediaProviderCallback callback : mediaProviderCallbacks) {
                if (null != callback) {
                    callback.onConnected(name);
                }
            }

            try {
                manager.addMediaProviderCallback(mediaProviderCallbackStub);
            } catch (RemoteException e) {
                e.printStackTrace();
            }

            try {
                manager.addUploadCallback(uploadCallbackStub);
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            Log.w(TAG, "onServiceDisconnected: name = " + name);
            manager = null;

            for (MediaProviderCallback callback : mediaProviderCallbacks) {
                if (null != callback) {
                    callback.onDisconnected(name);
                }
            }
        }
    };

    private IBinder.DeathRecipient deathRecipient = new IBinder.DeathRecipient() {
        @Override
        public void binderDied() {
            Log.e(TAG, "binderDied");
            unbind();
            bind();
        }
    };

    private com.storage.IMediaProviderCallback.Stub mediaProviderCallbackStub = new com.storage.IMediaProviderCallback.Stub() {
        @Override
        public void onInserted(MediaBean bean) throws RemoteException {
            for (MediaProviderCallback callback : mediaProviderCallbacks) {
                if (null != callback) {
                    callback.onInserted(bean);
                }
            }
        }

        @Override
        public void onDeleted(int count) throws RemoteException {
            for (MediaProviderCallback callback : mediaProviderCallbacks) {
                if (null != callback) {
                    callback.onDeleted(count);
                }
            }
        }

        @Override
        public void onUpdated(int count) throws RemoteException {
            for (MediaProviderCallback callback : mediaProviderCallbacks) {
                if (null != callback) {
                    callback.onUpdated(count);
                }
            }
        }

        @Override
        public void onQueried(int type, List<MediaBean> beans) throws RemoteException {
            for (MediaProviderCallback callback : mediaProviderCallbacks) {
                if (null != callback) {
                    callback.onQueried(type, beans);
                }
            }
        }

        @Override
        public void onQueriedAll(List<MediaBean> beans) throws RemoteException {
            for (MediaProviderCallback callback : mediaProviderCallbacks) {
                if (null != callback) {
                    callback.onQueriedAll(beans);
                }
            }
        }

        @Override
        public void onChanged(int type, Uri uri) throws RemoteException {
            for (MediaProviderCallback callback : mediaProviderCallbacks) {
                if (null != callback) {
                    callback.onChanged(type, uri);
                }
            }
        }
    };

    private com.storage.IUploadCallback.Stub uploadCallbackStub = new com.storage.IUploadCallback.Stub() {
        @Override
        public void onStarted(MediaBean bean) throws RemoteException {
            for (UploadCallback callback : uploadCallbacks) {
                if (null != callback) {
                    callback.onStarted(bean);
                }
            }
        }

        @Override
        public void onProgress(MediaBean bean, int progress) throws RemoteException {
            for (UploadCallback callback : uploadCallbacks) {
                if (null != callback) {
                    callback.onProgress(bean, progress);
                }
            }
        }

        @Override
        public void onCompleted(MediaBean bean, boolean isSuccess) throws RemoteException {
            for (UploadCallback callback : uploadCallbacks) {
                if (null != callback) {
                    callback.onCompleted(bean, isSuccess);
                }
            }
        }
    };

    public static abstract class MediaProviderCallback {
        public void onConnected(ComponentName name) {}

        public void onDisconnected(ComponentName name) {}

        public void onInserted(MediaBean bean) {}

        public void onDeleted(int count) {}

        public void onUpdated(int count) {}

        public void onQueried(int type, List<MediaBean> beans) {}

        public void onQueriedAll(List<MediaBean> beans) {}

        public void onQueriedDateMap(int type, Map<String, List<MediaBean>> beans) {}

        public void onQueriedDateMap(Map<String, List<MediaBean>> beans) {}

        public void onChanged(int type, Uri uri) {}
    }

    public static abstract class UploadCallback {
        public void onStarted(MediaBean bean) {}

        public void onProgress(MediaBean bean, int progress) {}

        public void onCompleted(MediaBean bean, boolean isSuccess) {}
    }

    private static class Builder {
        private static MediaProviderManager instance = new MediaProviderManager();
    }

    public static MediaProviderManager getInstance() {
        return MediaProviderManager.Builder.instance;
    }

    public MediaProviderManager() {
        try {
            //Application application = (Application) Class.forName("android.app.ActivityThread").getMethod("currentApplication").invoke(null, (Object[]) null);
            Application application = (Application) Class.forName("android.app.AppGlobals").getMethod("getInitialApplication").invoke(null, (Object[]) null);

            if (null != application) {
                context = application.getApplicationContext();
            } else {
                throw new NullPointerException();
            }
        } catch (IllegalAccessException | InvocationTargetException | NoSuchMethodException | ClassNotFoundException e) {
            e.printStackTrace();
        }
    }

    public void setImageDir(String dir) {
        if (null == manager) {
            return;
        }

        try {
            manager.setImageDir(dir);
        } catch (RemoteException e) {
            e.printStackTrace();
        }
    }

    public void setVideoDir(String dir) {
        if (null == manager) {
            return;
        }

        try {
            manager.setVideoDir(dir);
        } catch (RemoteException e) {
            e.printStackTrace();
        }
    }

    public void addMediaProviderCallback(MediaProviderCallback callback) {
        mediaProviderCallbacks.add(callback);
    }

    public void removeMediaProviderCallback(MediaProviderCallback callback) {
        mediaProviderCallbacks.remove(callback);
    }

    public void bind() {
        context.bindService(new Intent(context, MediaProviderService.class), connection, Context.BIND_AUTO_CREATE);
    }

    public void unbind(){
        if (null != manager) {
            try {
                manager.removeMediaProviderCallback(mediaProviderCallbackStub);
            } catch (RemoteException e) {
                e.printStackTrace();
            }

            try {
                manager.removeUploadCallback(uploadCallbackStub);
            } catch (RemoteException e) {
                e.printStackTrace();
            }

            manager.asBinder().unlinkToDeath(deathRecipient, 0);
            manager = null;

            context.unbindService(connection);
        }
    }

    public void insert(int type, String path, String url) {
        if (null == manager) {
            return;
        }

        try {
            manager.insert(type, path, url);
        } catch (RemoteException e) {
            e.printStackTrace();
        }
    }

    public void delete(MediaBean bean) {
        if (null == manager) {
            return;
        }

        try {
            manager.delete(bean);
        } catch (RemoteException e) {
            e.printStackTrace();
        }
    }

    public void delete(List<MediaBean> beans) {
        if (null == manager) {
            return;
        }

        try {
            manager.deleteBatch(beans);
        } catch (RemoteException e) {
            e.printStackTrace();
        }
    }

    public void update(MediaBean bean) {
        if (null == manager) {
            return;
        }

        try {
            manager.update(bean);
        } catch (RemoteException e) {
            e.printStackTrace();
        }
    }

    public void update(List<MediaBean> beans) {
        if (null == manager) {
            return;
        }

        try {
            manager.updateBatch(beans);
        } catch (RemoteException e) {
            e.printStackTrace();
        }
    }

    public void query(int type, String pathFilter, int order) {
        if (null == manager) {
            return;
        }

        try {
            manager.query(type, pathFilter, order);
        } catch (RemoteException e) {
            e.printStackTrace();
        }
    }

    public void queryAll(String pathFilter, int order) {
        if (null == manager) {
            return;
        }

        try {
            manager.queryAll(pathFilter, order);
        } catch (RemoteException e) {
            e.printStackTrace();
        }
    }

    public void queryDateMap(int type, String pathFilter, int order) {
        if (null == manager) {
            return;
        }

        try {
            manager.queryDateMap(type, pathFilter, order);
        } catch (RemoteException e) {
            e.printStackTrace();
        }
    }

    public void queryDateMap(String pathFilter, int order) {
        if (null == manager) {
            return;
        }

        try {
            manager.queryDateMapAll(pathFilter, order);
        } catch (RemoteException e) {
            e.printStackTrace();
        }
    }

    public List<MediaBean> getUploads() {
        if (null == manager) {
            return null;
        }

        try {
            return manager.getUploads();
        } catch (RemoteException e) {
            e.printStackTrace();
        }

        return null;
    }

    public void clearUpload() {
        if (null == manager) {
            return;
        }

        try {
            manager.clearUpload();
        } catch (RemoteException e) {
            e.printStackTrace();
        }
    }

    public void addUploadCallback(UploadCallback callback) {
        uploadCallbacks.add(callback);
    }

    public void removeUploadCallback(UploadCallback callback) {
        uploadCallbacks.remove(callback);
    }

    public void upload(List<MediaBean> beans) {
        if (null == manager) {
            return;
        }

        try {
            manager.upload(beans);
        } catch (RemoteException e) {
            e.printStackTrace();
        }
    }
}
