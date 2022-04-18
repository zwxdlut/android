package com.mxnavi.dvr.viewmodel;

import android.content.ComponentName;
import android.content.Context;
import android.location.Location;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.os.StatFs;
import android.util.Log;
import android.util.Size;
import android.view.Surface;

import androidx.annotation.NonNull;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import com.amap.api.location.AMapLocation;
import com.amap.api.location.AMapLocationClient;
import com.amap.api.location.AMapLocationClientOption;
import com.amap.api.location.AMapLocationListener;
import com.camera.CameraController;
import com.mxnavi.dvr.utils.MediaDirUtil;
import com.storage.MediaProviderManager;
import com.storage.util.LocationRecorder;

import java.lang.ref.WeakReference;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.Locale;
import java.util.Timer;
import java.util.TimerTask;

public class MainViewModel extends ViewModel {
    public static final int STORAGE_SPACE_LIMIT1 = 500 * 1024 *1024;
    public static final int STORAGE_SPACE_LIMIT2 = 300 * 1024 *1024;
    public static boolean sRecording = false;
    private static final String TAG = "DVR-" + MainViewModel.class.getSimpleName();
    private static final int RECORD_DURATION = 5 * 60 * 1000;
    private static final int MAG_START_CAPTURE = 100;
    private static final int MSG_START_RECORD = 101;
    private static final int MSG_STOP_RECORD = 102;
    private static final int MSG_REOPEN_CAMERA = 103;
    private static final long LOCATION_INTERVAL = 2000;
    private static final Size captureSize = new Size(1920, 1080);
    private static final Size recordSize = new Size(1920, 1080);
    private int captureCount = 0;
    private boolean isCameraOpened = false;
    private String[] cameraIds = null;
    private Context context = null;
    private Timer timer = null;
    private SimpleDateFormat recordDateFormat = new SimpleDateFormat("yyyyMMddHHmmss", Locale.getDefault());
    private SimpleDateFormat titleDateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
    private CameraController camera = null;
    private LocationRecorder locationRecorder = null;
    private AMapLocationClient locationClient = null;
    private HandlerThread handlerThread = null;
    private Handler handler = null;
    private MediaPlayer player = null;
    private MutableLiveData<String> date = new MutableLiveData<>();
    private MutableLiveData<Boolean> recording = new MutableLiveData<>();
    private MutableLiveData<Boolean> captureResult = new MutableLiveData<>();
    private MutableLiveData<Long> availableStorageSpace = new MutableLiveData<>();
    private MutableLiveData<Double> altitude = new MutableLiveData<>();

    private TimerTask timerTask = new TimerTask() {
        @Override
        public void run() {
            date.postValue(titleDateFormat.format(new Date()));
        }
    };

    private CameraController.ICameraCallback cameraCallback = new CameraController.ICameraCallback() {
        @Override
        public void onState(String cameraId, int state) {
            Log.i(TAG, "onState: cameraId = " + cameraId + ", state = " + state);

            if (CameraController.ICameraCallback.State.CAMERA_OPENED == state) {
                isCameraOpened = true;
                handler.sendEmptyMessage(MSG_START_RECORD);
            }
        }

        @Override
        public void onError(String cameraId, int error) {
            Log.i(TAG, "onError: cameraId = " + cameraId + ", error = " + error);
            handler.sendEmptyMessage(MSG_REOPEN_CAMERA);
        }
    };

    private CameraController.ICaptureCallback captureCallback = new CameraController.ICaptureCallback() {
        @Override
        public void onStarted(String cameraId, String path) {
            Log.i(TAG, "onStarted: capture cameraId = " + cameraId + ", path = " + path + ", captureCount = " + captureCount);
        }

        @Override
        public void onCompleted(String cameraId, String path) {
            Log.i(TAG, "onCompleted: capture cameraId = " + cameraId + ", path = " + path + ", captureCount = " + captureCount);
            captureCount--;

            if (captureCount > 0) {
                handler.sendEmptyMessageDelayed(MAG_START_CAPTURE, 1000);
            } else {
                captureResult.postValue(true);
            }
        }

        @Override
        public void onFailed(String cameraId, String path) {
            Log.w(TAG, "onFailed: capture cameraId = " + cameraId + ", path = " + path + ", captureCount = " + captureCount);
            captureResult.postValue(false);
        }
    };

    private CameraController.IRecordCallback recordCallback = new CameraController.IRecordCallback() {
        @Override
        public void onCompleted(String cameraId, String path) {
            Log.i(TAG, "onCompleted: record cameraId = " + cameraId + ", path = " + path);

            // stop record location
            locationRecorder.stop();
            locationClient.stopLocation();

            if (sRecording) {
                handler.sendEmptyMessage(MSG_START_RECORD);
            } else {
                Log.w(TAG, "onCompleted: Recording has been stopped!");
            }
        }

        @Override
        public void onError(String cameraId, String path, int what, int extra) {
            Log.i(TAG, "onError: record cameraId = " + cameraId + ", path = " + path + ", what = " + what + ", extra = " + extra);
            handler.sendEmptyMessage(MSG_REOPEN_CAMERA);
        }
    };

    private AMapLocationListener locationListener = new AMapLocationListener() {
        @Override
        public void onLocationChanged(AMapLocation amapLocation) {
            if (null != amapLocation) {
                if (amapLocation.getErrorCode() == 0) {
                    //Log.d(TAG, "onLocationChanged: amapLocation = " + amapLocation.toString());
                    locationRecorder.feed(new LocationRecorder.LocationBean(
                            amapLocation.getTime(), amapLocation.getLatitude(), amapLocation.getLongitude(), /*amapLocation.getAltitude(),*/
                            amapLocation.getCity() + amapLocation.getDistrict() + amapLocation.getStreet()));
                    altitude.postValue(amapLocation.getAltitude());
                } else {
                    Log.e(TAG,"onLocationChanged: location Error, ErrCode:"
                            + amapLocation.getErrorCode() + ", errInfo:"
                            + amapLocation.getErrorInfo());
                }
            }
        }
    };

    private MediaProviderManager.MediaProviderCallback mediaProviderCallback = new MediaProviderManager.MediaProviderCallback() {
        @Override
        public void onConnected(ComponentName name) {
            MediaProviderManager.getInstance().setImageDir(MediaDirUtil.getDir(context, MediaDirUtil.Type.IMAGE));
            MediaProviderManager.getInstance().setVideoDir(MediaDirUtil.getDir(context, MediaDirUtil.Type.VIDEO));
        }
    };

    private static class MainHandler extends Handler {
        private WeakReference<MainViewModel> ref = null;

        public MainHandler(MainViewModel viewModel) {
            super(viewModel.handlerThread.getLooper());
            ref = new WeakReference<>(viewModel);
        }

        @Override
        public void handleMessage(Message msg) {
            Log.i(TAG, "handleMessage: what = " + msg.what);

            MainViewModel viewModel = ref.get();

            if (null == viewModel) {
                Log.e(TAG, "handleMessage: MainViewModel is null!");
                return;
            }

            switch (msg.what) {
                case MAG_START_CAPTURE:
                    AMapLocation captureLocation = viewModel.locationClient.getLastKnownLocation();
                    Location location = null;

                    if (null != captureLocation) {
                        location = new Location(captureLocation.getProvider());
                        location.setLatitude(captureLocation.getLatitude());
                        location.setLongitude(captureLocation.getLongitude());
                        //location.setAltitude(captureLocation.getAltitude());
                    }

                    viewModel.player.start();
                    viewModel.camera.capture(viewModel.cameraIds[0], viewModel.recordDateFormat.format(new Date()) + ".jpg", location);
                    break;

                case MSG_START_RECORD:
                    // check storage available size
                    StatFs statFs = new StatFs(MediaDirUtil.ROOT_DIR);
                    long availableSize = statFs.getBlockSizeLong() * statFs.getAvailableBlocksLong();
                    Log.i(TAG, "handleMessage: storage available size " +  availableSize);
                    viewModel.availableStorageSpace.postValue(availableSize);

                    if (STORAGE_SPACE_LIMIT2 >= availableSize) {
                        viewModel.sRecording = false;
                        viewModel.recording.postValue(false);
                        viewModel.camera.startPreview(viewModel.cameraIds[0]);
                        return;
                    } else if (STORAGE_SPACE_LIMIT1 >= availableSize) {
                        if (!viewModel.sRecording) {
                            return;
                        }
                    }

                    // start record
                    String time = viewModel.recordDateFormat.format(new Date());
                    viewModel.camera.stopPreview(viewModel.cameraIds[0]);
                    viewModel.camera.startRecord(viewModel.cameraIds[0], time + ".mp4", RECORD_DURATION);
                    viewModel.locationClient.startLocation();
                    viewModel.locationRecorder.start(time + ".json");
                    viewModel.sRecording = true;
                    viewModel.recording.postValue(true);
                    break;

                case MSG_STOP_RECORD:
                    viewModel.sRecording = false;
                    viewModel.camera.stopRecord(viewModel.cameraIds[0]);
                    viewModel.recording.postValue(false);
                    viewModel.camera.startPreview(viewModel.cameraIds[0]);
                    break;

                case MSG_REOPEN_CAMERA:
                    // stop record video
                    viewModel.sRecording = false;
                    viewModel.camera.stopRecord(viewModel.cameraIds[0]);
                    viewModel.recording.postValue(false);

                    // stop record location
                    viewModel.locationRecorder.stop();
                    viewModel.locationClient.stopLocation();

                    // reopen camera
                    viewModel.camera.close(viewModel.cameraIds[0]);
                    viewModel.camera.open(viewModel.cameraIds[0]);
                    break;

                default:
                    break;
            }
        }
    }

    public static class Factory implements ViewModelProvider.Factory {
        private Context context = null;

        public Factory(Context ctx) {
            context = ctx;
        }

        @NonNull
        @Override
        public <T extends ViewModel> T create(@NonNull Class<T> modelClass) {
            return (T) new MainViewModel(context);
        }
    }

    public MainViewModel(Context context) {
        Log.i(TAG, "MainViewModel: constructor");

        // start timer
        timer = new Timer();
        timer.schedule(timerTask, 0, 1000);

        // initialize the camera
        camera = CameraController.getInstance();
        cameraIds = camera.getCameraIdList();
        camera.setCameraCallback(cameraCallback);
        camera.setCaptureCallback(captureCallback);
        camera.setRecordCallback(recordCallback);
        camera.setCaptureRelativeDir(cameraIds[0], MediaDirUtil.RELATIVE_IMAGE_DIR, true, MediaDirUtil.isRemovable);
        camera.setRecordRelativeDir(cameraIds[0], MediaDirUtil.RELATIVE_VIDEO_DIR, true, MediaDirUtil.isRemovable);
        Log.i(TAG, "MainViewModel: available capture sizes = " + Arrays.toString(camera.getAvailableCaptureSizes(cameraIds[0])));
        Log.i(TAG, "MainViewModel: available record sizes = " + Arrays.toString(camera.getAvailablePreviewSizes(cameraIds[0])));
        camera.setCaptureSize(cameraIds[0], captureSize.getWidth(), captureSize.getHeight());
        camera.setRecordSize(cameraIds[0], recordSize.getWidth(), recordSize.getHeight());
        camera.setAudioMute(cameraIds[0], true);
        //camera.setVideoEncodingRate(cameraIds[0], 500000);

        // initialize the location recorder
        AMapLocationClientOption locationOption = new AMapLocationClientOption();
        locationOption.setLocationMode(AMapLocationClientOption.AMapLocationMode.Hight_Accuracy);
        locationOption.setInterval(LOCATION_INTERVAL);
        locationOption.setNeedAddress(true);
        locationClient = new AMapLocationClient(context);
        locationClient.setLocationOption(locationOption);
        locationClient.setLocationListener(locationListener);
        locationRecorder = LocationRecorder.getInstance();

        // start the handler thread
        handlerThread = new HandlerThread("MainHandlerThread");
        handlerThread.start();
        handler = new MainHandler(this);

        // crete media player
        player = MediaPlayer.create(context, Uri.parse("file:///system/media/audio/ui/camera_click.ogg"));

        // bind MediaProviderService
        MediaProviderManager.getInstance().addMediaProviderCallback(mediaProviderCallback);
        MediaProviderManager.getInstance().bind();
    }

    @Override
    protected void onCleared() {
        Log.i(TAG, "onCleared");

        // unbind MediaProviderService
        MediaProviderManager.getInstance().unbind();
        MediaProviderManager.getInstance().removeMediaProviderCallback(mediaProviderCallback);
        
        // terminate the handler thread
        handler.removeCallbacksAndMessages(null);
        handlerThread.quitSafely();

        try {
            handlerThread.join();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        // de-initialize the location recorder
        locationRecorder.stop();
        locationClient.stopLocation();
        locationClient.onDestroy();

        // de-initialize the camera
        sRecording = false;
        camera.stopRecord(cameraIds[0]);
        // For the last record file, don't close.
        //camera.close(cameraIds[0]);
        camera.setCameraCallback(null);
        camera.setCaptureCallback(null);
        camera.setRecordCallback(null);

        // stop timer
        timer.cancel();

        super.onCleared();
    }

    public MutableLiveData<String> getDate() {
        return date;
    }

    public MutableLiveData<Boolean> getRecording() {
        return recording;
    }

    public MutableLiveData<Boolean> getCaptureResult() {
        return captureResult;
    }

    public MutableLiveData<Long> getAvailableStorageSpace() {
        return availableStorageSpace;
    }

    public MutableLiveData<Double> getAltitude() {
        return altitude;
    }

    public void setPreviewSurface(Surface surface) {
        Log.i(TAG, "setPreviewSurface: surface = " + surface);
        camera.setPreviewSurface(cameraIds[0], surface);
    }
    
    public void startRecord() {
        Log.i(TAG, "startRecord: isCameraOpened = " + isCameraOpened);
        
        if (isCameraOpened) {
            handler.sendEmptyMessage(MSG_START_RECORD);
        } else {
            camera.open(cameraIds[0]);
        }
    }

    public void stopRecord() {
        Log.i(TAG, "stopRecord");
        handler.sendEmptyMessage(MSG_STOP_RECORD);
    }

    public void capture() {
        Log.i(TAG, "capture");
        captureCount = 3;
        handler.sendEmptyMessage(MAG_START_CAPTURE);
    }

    public void setMute(boolean isMute) {
        Log.i(TAG, "setMute: isMute = " + isMute);
        camera.setAudioMute(cameraIds[0], isMute);
    }
}
