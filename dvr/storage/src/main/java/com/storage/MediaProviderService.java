package com.storage;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Message;
import android.os.RemoteException;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.storage.dao.CloudDatabase;
import com.storage.dao.CloudInfo;
import com.storage.dao.CloudInfoDao;
import com.storage.util.Constant;
import com.storage.util.NetworkUtil;
import com.storage.util.ToastUtil;
import com.upyun.library.common.SerialUploader;
import com.upyun.library.listener.UpCompleteListener;
import com.upyun.library.listener.UpProgressListener;
import com.upyun.library.utils.UpYunUtils;

import java.io.File;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.LinkedBlockingQueue;

import okhttp3.Response;

public class MediaProviderService extends Service {
    private static final String TAG = "DVR-" + MediaProviderService.class.getSimpleName();
    private static final String SPACE = "cloud-storage-test";
    private static final String OPERATOR = "lijh";
    private static final String PASSWORD = "obLfmonZpvcwc5VVcO5NKvgButqljDPJ";
    private static final String DOWN_LOAD_DOMAIN = "https://mx-cloud-storage-test-upyun.mxnavi.com";
    private static final int MSG_INSERT = 0;
    private static final int MSG_DELETE = 1;
    private static final int MSG_DELETE_BATCH = 2;
    private static final int MSG_UPDATE = 3;
    private static final int MSG_UPDATE_BATCH = 4;
    private static final int MSG_QUERY = 5;
    private static final int MSG_QUERY_ALL = 6;
    private static final int MSG_QUERY_DATE_MAP = 7;
    private static final int MSG_QUERY_DATE_MAP_ALL = 8;
    private static final int MSG_UPLOAD = 9;

    private MediaProviderHelper mediaProviderHelper = null;
    //private SharedPreferences sharedPreferences = null;
    private CloudInfoDao cloudInfoDao = null;
    private HandlerThread handlerThread = null;
    private Handler handler = null;
    private SerialUploader serialUploader = null;
    private LinkedBlockingQueue<MediaBean> uploadQueue = new LinkedBlockingQueue<>();
    private List<IUploadCallback> uploadCallbacks = new ArrayList<>();
    private List<IMediaProviderCallback> mediaProviderCallbacks = new ArrayList<>();

    private IMediaProviderManager.Stub stub = new IMediaProviderManager.Stub() {
        @Override
        public void setImageDir(String dir) throws RemoteException {
            mediaProviderHelper.setImageDir(dir);
        }

        @Override
        public void setVideoDir(String dir) throws RemoteException {
            mediaProviderHelper.setVideoDir(dir);
        }

        @Override
        public void addMediaProviderCallback(IMediaProviderCallback callback) throws RemoteException {
            mediaProviderCallbacks.add(callback);
        }

        @Override
        public void removeMediaProviderCallback(IMediaProviderCallback callback) throws RemoteException {
            mediaProviderCallbacks.remove(callback);
        }

        @Override
        public void insert(int type, String path, String url) throws RemoteException {
            Bundle bundle = new Bundle();
            bundle.putInt("type", type);
            bundle.putString("path", path);
            bundle.putString("url", url);
            handler.sendMessage(handler.obtainMessage(MSG_INSERT, bundle));
        }

        @Override
        public void delete(MediaBean bean) throws RemoteException {
            Bundle bundle = new Bundle();
            bundle.putParcelable("bean", bean);
            handler.sendMessage(handler.obtainMessage(MSG_DELETE, bundle));
        }

        @Override
        public void deleteBatch(List<MediaBean> beans) throws RemoteException {
            Bundle bundle = new Bundle();
            bundle.putParcelableArrayList("beans", (ArrayList<MediaBean>) beans);
            handler.sendMessage(handler.obtainMessage(MSG_DELETE_BATCH, bundle));
        }

        @Override
        public void update(MediaBean bean) throws RemoteException {
            Bundle bundle = new Bundle();
            bundle.putParcelable("bean", bean);
            handler.sendMessage(handler.obtainMessage(MSG_UPDATE, bundle));
        }

        @Override
        public void updateBatch(List<MediaBean> beans) throws RemoteException {
            Bundle bundle = new Bundle();
            bundle.putParcelableArrayList("beans", (ArrayList<MediaBean>) beans);
            handler.sendMessage(handler.obtainMessage(MSG_UPDATE_BATCH, bundle));
        }

        @Override
        public void query(int type, String pathFilter, int order) throws RemoteException {
            Bundle bundle = new Bundle();
            bundle.putInt("type", type);
            bundle.putString("pathFilter", pathFilter);
            bundle.putInt("order", order);
            handler.sendMessage(handler.obtainMessage(MSG_QUERY, bundle));
        }

        @Override
        public void queryAll(String pathFilter, int order) throws RemoteException {
            Bundle bundle = new Bundle();
            bundle.putString("pathFilter", pathFilter);
            bundle.putInt("order", order);
            handler.sendMessage(handler.obtainMessage(MSG_QUERY_ALL, bundle));
        }

        @Override
        public void queryDateMap(int type, String pathFilter, int order) throws RemoteException {
            Bundle bundle = new Bundle();
            bundle.putInt("type", type);
            bundle.putString("pathFilter", pathFilter);
            bundle.putInt("order", order);
            handler.sendMessage(handler.obtainMessage(MSG_QUERY_DATE_MAP, bundle));
        }

        @Override
        public void queryDateMapAll(String pathFilter, int order) throws RemoteException {
            Bundle bundle = new Bundle();
            bundle.putString("pathFilter", pathFilter);
            bundle.putInt("order", order);
            handler.sendMessage(handler.obtainMessage(MSG_QUERY_DATE_MAP_ALL, bundle));
        }

        @Override
        public List<MediaBean> getUploads() throws RemoteException {
            List<MediaBean> uploads = new ArrayList<>();

            for (int i = 0; i < uploadQueue.size(); i++) {
                uploads.add(uploadQueue.peek());
            }

            return uploads;
        }

        @Override
        public void clearUpload() throws RemoteException {
            uploadQueue.clear();
        }

        @Override
        public void addUploadCallback(IUploadCallback callback) throws RemoteException {
            uploadCallbacks.add(callback);
        }

        @Override
        public void removeUploadCallback(IUploadCallback callback) throws RemoteException {
            uploadCallbacks.remove(callback);
        }

        @Override
        public void upload(List<MediaBean> beans) throws RemoteException {
            if (null == beans || beans.isEmpty()) {
                return;
            }

            uploadQueue.addAll(beans);
            handler.sendEmptyMessage(MSG_UPLOAD);
        }
    };

    private ConnectivityManager.NetworkCallback networkCallback = new ConnectivityManager.NetworkCallback() {
        @Override
        public void onAvailable(@NonNull Network network) {
            super.onAvailable(network);
            Log.i(TAG, "onAvailable");
        }

        @Override
        public void onLost(@NonNull Network network) {
            super.onLost(network);
            Log.w(TAG, "onLost");

            if (0 != uploadQueue.size()) {
                uploadQueue.clear();
                ToastUtil.show(MediaProviderService.this, R.string.network_error);
            }
        }

        @Override
        public void onCapabilitiesChanged(@NonNull Network network, @NonNull NetworkCapabilities networkCapabilities) {
            super.onCapabilitiesChanged(network, networkCapabilities);
//            Log.i(TAG, "onCapabilitiesChanged: network = " + network + ", networkCapabilities =" + networkCapabilities);
//
//            if (networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)) {
//                if (networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
//                        networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI_AWARE)) {
//                    Log.w(TAG, "onCapabilitiesChanged: WIFI!");
//                } else if (networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ||
//                        networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) ) {
//                    Log.w(TAG, "onCapabilitiesChanged: CELLULAR!");
//                } else{
//                    Log.w(TAG, "onCapabilitiesChanged: UNKNOWN!");
//                }
//            }
        }
    };

    private MediaProviderHelper.IMediaCallback mediaCallback = new MediaProviderHelper.IMediaCallback() {
        @Override
        public void onChanged(int type, Uri uri) {
            for (IMediaProviderCallback callback : mediaProviderCallbacks) {
                try {
                    callback.onChanged(type, uri);
                } catch (RemoteException e) {
                    e.printStackTrace();
                }
            }
        }
    };

    private static class MediaProviderHandler extends Handler {
        private WeakReference<MediaProviderService> ref = null;

        MediaProviderHandler(MediaProviderService service) {
            super(service.handlerThread.getLooper());
            ref = new WeakReference<>(service);
        }

        @Override
        public void handleMessage(@NonNull Message msg) {
            Log.i(TAG, "handleMessage: what = " + msg.what);

            MediaProviderService service = ref.get();

            if (null == service) {
                Log.e(TAG, "handleMessage: MediaProviderService is null!");
                return;
            }

            switch (msg.what) {
                case MSG_INSERT:
                    MediaBean insertBean = service.mediaProviderHelper.insert(
                            ((Bundle) msg.obj).getInt("type"),
                            ((Bundle) msg.obj).getString("path"),
                            ((Bundle) msg.obj).getString("url"));

                    for (IMediaProviderCallback callback : service.mediaProviderCallbacks) {
                        try {
                            callback.onInserted(insertBean);
                        } catch (RemoteException e) {
                            e.printStackTrace();
                        }
                    }

                    break;

                case MSG_DELETE:
                    MediaBean deleteBean = (MediaBean)((Bundle) msg.obj).getParcelable("bean");
                    int deleteCount = service.mediaProviderHelper.delete(deleteBean);

                    if (null != deleteBean) {
//                        SharedPreferences.Editor editor = service.sharedPreferences.edit();
//                        editor.remove(deleteBean.getName());
//                        editor.apply();
                        service.cloudInfoDao.delete(deleteBean.getName());
                    }

                    for (IMediaProviderCallback callback : service.mediaProviderCallbacks) {
                        try {
                            callback.onDeleted(deleteCount);
                        } catch (RemoteException e) {
                            e.printStackTrace();
                        }
                    }

                    break;

                case MSG_DELETE_BATCH:
                    List<MediaBean> deleteBeans = ((Bundle) msg.obj).<MediaBean>getParcelableArrayList("beans");
                    int deleteBatchCount = service.mediaProviderHelper.delete(deleteBeans);

                    if (null != deleteBeans) {
                        for (MediaBean bean : deleteBeans) {
//                            SharedPreferences.Editor editor = service.sharedPreferences.edit();
//                            editor.remove(bean.getName());
//                            editor.apply();
                            service.cloudInfoDao.delete(bean.getName());
                        }
                    }

                    for (IMediaProviderCallback callback : service.mediaProviderCallbacks) {
                        try {
                            callback.onDeleted(deleteBatchCount);
                        } catch (RemoteException e) {
                            e.printStackTrace();
                        }
                    }

                    break;

                case MSG_UPDATE:
                    int updateCount = service.mediaProviderHelper.update((MediaBean)((Bundle) msg.obj).getParcelable("bean"));

                    for (IMediaProviderCallback callback : service.mediaProviderCallbacks) {
                        try {
                            callback.onUpdated(updateCount);
                        } catch (RemoteException e) {
                            e.printStackTrace();
                        }
                    }

                    break;

                case MSG_UPDATE_BATCH:
                    int updateBatchCount = service.mediaProviderHelper.update(((Bundle) msg.obj).<MediaBean>getParcelableArrayList("beans"));

                    for (IMediaProviderCallback callback : service.mediaProviderCallbacks) {
                        try {
                            callback.onUpdated(updateBatchCount);
                        } catch (RemoteException e) {
                            e.printStackTrace();
                        }
                    }

                    break;

                case MSG_QUERY:
                    int queryType = ((Bundle) msg.obj).getInt("type");
                    List<MediaBean> beans = service.mediaProviderHelper.query(queryType,
                            ((Bundle) msg.obj).getString("pathFilter"),
                            ((Bundle) msg.obj).getInt("order", Constant.OrderType.DESCENDING));

                    if (null != beans) {
                        for (MediaBean bean : beans) {
                            //bean.setUrl(service.sharedPreferences.getString(bean.getName(),""));
                            bean.setUrl(service.cloudInfoDao.query(bean.getName()));
                        }
                    }

                    for (IMediaProviderCallback callback : service.mediaProviderCallbacks) {
                        try {
                            callback.onQueried(queryType, beans);
                        } catch (RemoteException e) {
                            e.printStackTrace();
                        }
                    }

                    break;

                case MSG_QUERY_ALL:
                    int order = ((Bundle) msg.obj).getInt("order", Constant.OrderType.DESCENDING);
                    String queryAllPathFilter = ((Bundle) msg.obj).getString("pathFilter");
                    List<MediaBean> tempBeans = null;
                    List<MediaBean> allBeans = new ArrayList<>();

                    tempBeans = service.mediaProviderHelper.query(MediaBean.Type.IMAGE, queryAllPathFilter, order);
                    if (null != tempBeans && !tempBeans.isEmpty()) {
                        allBeans.addAll(tempBeans);
                    }

                    tempBeans = service.mediaProviderHelper.query(MediaBean.Type.VIDEO, queryAllPathFilter, order);
                    if (null != tempBeans && !tempBeans.isEmpty()) {
                        allBeans.addAll(tempBeans);
                    }

                    for (MediaBean bean : allBeans) {
                        //bean.setUrl(service.sharedPreferences.getString(bean.getName(),""));
                        bean.setUrl(service.cloudInfoDao.query(bean.getName()));
                    }

                    for (IMediaProviderCallback callback : service.mediaProviderCallbacks) {
                        try {
                            callback.onQueriedAll(allBeans);
                        } catch (RemoteException e) {
                            e.printStackTrace();
                        }
                    }

                    break;

                case MSG_QUERY_DATE_MAP:
                    Map<String, List<MediaBean>> dateMapBeans = service.mediaProviderHelper.queryDateMap(
                            ((Bundle) msg.obj).getInt("type"),
                            ((Bundle) msg.obj).getString("pathFilter"),
                            ((Bundle) msg.obj).getInt("order", Constant.OrderType.DESCENDING));
                    // TODO: map parameter for aidl
                    break;

                case MSG_QUERY_DATE_MAP_ALL:
                    int queryDateMapAllOrder = ((Bundle) msg.obj).getInt("order", Constant.OrderType.DESCENDING);
                    String queryDateMapAllPathFilter = ((Bundle) msg.obj).getString("pathFilter");
                    Map<String, List<MediaBean>> images = service.mediaProviderHelper.queryDateMap(
                            MediaBean.Type.IMAGE, queryDateMapAllPathFilter, queryDateMapAllOrder);
                    Map<String, List<MediaBean>> videos = service.mediaProviderHelper.queryDateMap(
                            MediaBean.Type.VIDEO, queryDateMapAllPathFilter, queryDateMapAllOrder);
                    // TODO: combine the two maps
                    break;

                case MSG_UPLOAD:
                    service.upload();
                    break;

                default:
                    break;
            }
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Log.i(TAG, "onCreate");

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForeground(1, createNotification());
        }

        getSystemService(ConnectivityManager.class).registerNetworkCallback(new NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build(), 
                networkCallback);
        mediaProviderHelper = new MediaProviderHelper(this);
        mediaProviderHelper.setMediaCallback(mediaCallback);
        //sharedPreferences = getSharedPreferences("clouds", MODE_PRIVATE);
        cloudInfoDao = CloudDatabase.getInstance(this).cloudInfoDao();
        handlerThread = new HandlerThread("MediaProviderHandlerThread");
        handlerThread.start();
        handler = new MediaProviderHandler(this);
        serialUploader = new SerialUploader(SPACE, OPERATOR, UpYunUtils.md5(PASSWORD));
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.i(TAG, "onStartCommand: intent = " + intent + ", flags = " + flags + ", startId = " + startId);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForeground(1, createNotification());
        }

        return START_STICKY;
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        Log.i(TAG, "onBind: intent = " + intent);
        return stub;
    }

    @Override
    public boolean onUnbind(Intent intent) {
        Log.i(TAG, "onUnbind: intent = " + intent);
        //return super.onUnbind(intent);
        return true;
    }

    @Override
    public void onRebind(Intent intent) {
        super.onRebind(intent);
        Log.i(TAG, "onRebind: intent = " + intent);
    }

    @Override
    public void onDestroy() {
        Log.i(TAG, "onDestroy");

        handlerThread.quitSafely();

        try {
            handlerThread.join();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        mediaProviderHelper.setMediaCallback(null);
        mediaProviderHelper.destroy();
        getSystemService(ConnectivityManager.class).unregisterNetworkCallback(networkCallback);
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            stopForeground(true);
        }

        super.onDestroy();
    }
    
    private void upload() {
        if (!NetworkUtil.isNetworkAvailable(this)) {
            ToastUtil.show(this, R.string.network_error);
            return;
        }

        Log.i(TAG, "upload: queue size = " + uploadQueue.size());

        final MediaBean bean = uploadQueue.poll();
        if (null == bean) {
            return;
        }

        Log.i(TAG, "upload: path = " + bean.getPath());

        for (IUploadCallback callback : uploadCallbacks) {
            try {
                callback.onStarted(bean);
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        }

        String url = bean.getUrl();

        if (null != url && !url.isEmpty()) {
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

            for (IUploadCallback callback : uploadCallbacks) {
                try {
                    callback.onProgress(bean, 100);
                } catch (RemoteException e) {
                    e.printStackTrace();
                }
            }

            Log.i(TAG, "upload: url = " + url);

            for (IUploadCallback callback : uploadCallbacks) {
                try {
                    callback.onCompleted(bean, true);
                } catch (RemoteException e) {
                    e.printStackTrace();
                }
            }

            handler.sendEmptyMessage(MSG_UPLOAD);
            return;
        }

        File file = new File(bean.getPath());

        serialUploader.setCheckMD5(true);
        serialUploader.setOnProgressListener(new UpProgressListener() {
            @Override
            public void onRequestProgress(long bytesWrite, long contentLength) {
                Log.i(TAG, "upload: " + bytesWrite + "/" + contentLength);

                for (IUploadCallback callback : uploadCallbacks) {
                    try {
                        callback.onProgress(bean, (int) ((100 * bytesWrite) / contentLength));
                    } catch (RemoteException e) {
                        e.printStackTrace();
                    }
                }
            }
        });

        serialUploader.upload(file, bean.getPath(), null, new UpCompleteListener() {
            @Override
            public void onComplete(final boolean isSuccess, Response response, Exception error) {
                Log.i(TAG, "upload: isSuccess = " + isSuccess + ", response = " + response + ", error = " + error);

                if (isSuccess) {
                    final String url = DOWN_LOAD_DOMAIN + bean.getPath();
                    bean.setUrl(url);
                    Log.i(TAG, "upload: url = " + url);

//                    SharedPreferences.Editor editor = sharedPreferences.edit();
//                    editor.putString(bean.getName(), url);
//                    editor.apply();

                    new Thread(new Runnable() {
                        @Override
                        public void run() {
                            cloudInfoDao.insert(new CloudInfo(bean.getName(), url));
                        }
                    }).start();
                }

                for (IUploadCallback callback : uploadCallbacks) {
                    try {
                        callback.onCompleted(bean, isSuccess);
                    } catch (RemoteException e) {
                        e.printStackTrace();
                    }
                }

                handler.sendEmptyMessage(MSG_UPLOAD);
            }
        });
    }

    private Notification createNotification() {
        NotificationChannel notificationChannel = new NotificationChannel(getPackageName(), getPackageName(), NotificationManager.IMPORTANCE_DEFAULT);
        NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

        if (null == manager.getNotificationChannel(getPackageName())) {
            notificationChannel.enableLights(false);
            notificationChannel.setShowBadge(false);
            notificationChannel.setLockscreenVisibility(Notification.VISIBILITY_PRIVATE);
            manager.createNotificationChannel(notificationChannel);
        }

        return new Notification.Builder(this, getPackageName())
                .setContentTitle(getText(R.string.notification_title))
                .setContentText(getText(R.string.notification_content))
                .setContentIntent(PendingIntent.getActivity(this, 0,
                        getPackageManager().getLaunchIntentForPackage(getPackageName()), 0))
                .build();
    }
}
