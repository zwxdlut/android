package com.media.camera;

import android.Manifest;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.ImageFormat;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.location.Location;
import android.location.LocationManager;
import android.media.ExifInterface;
import android.media.Image;
import android.media.ImageReader;
import android.media.MediaMetadataRetriever;
import android.media.MediaRecorder;
import android.media.ThumbnailUtils;
import android.net.Uri;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.provider.MediaStore;
import android.util.ArrayMap;
import android.util.Log;
import android.util.Size;
import android.util.SparseIntArray;
import android.view.OrientationEventListener;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.WindowManager;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class CameraNative implements ICamera {
    private static final String TAG = "CameraNative";
    private static final int SENSOR_ORIENTATION_DEGREES = 90;
    private static final int SENSOR_ORIENTATION_INVERSE_DEGREES = 270;
    private static final SparseIntArray ORIENTATIONS = new SparseIntArray();
    private static final SparseIntArray INVERSE_ORIENTATIONS = new SparseIntArray();

    static {
        ORIENTATIONS.append(Surface.ROTATION_0, 90);
        ORIENTATIONS.append(Surface.ROTATION_90, 0);
        ORIENTATIONS.append(Surface.ROTATION_180, 270);
        ORIENTATIONS.append(Surface.ROTATION_270, 180);
    }

    static {
        INVERSE_ORIENTATIONS.append(Surface.ROTATION_0, 270);
        INVERSE_ORIENTATIONS.append(Surface.ROTATION_90, 180);
        INVERSE_ORIENTATIONS.append(Surface.ROTATION_180, 90);
        INVERSE_ORIENTATIONS.append(Surface.ROTATION_270, 0);
    }

    private static CameraNative instance = null;
    private Context context = null;
    private CameraManager cameraManager = null;
    private ArrayMap<String, Integer> sensorOrientations = new ArrayMap<>();
    private ArrayMap<String, Integer> facings = new ArrayMap<>();
    private ArrayMap<String, Surface> previewSurfaces = new ArrayMap<>();
    private ArrayMap<String, String> captureDirs = new ArrayMap<>();
    private ArrayMap<String, String> recordingDirs = new ArrayMap<>();
    private ArrayMap<String, String> thumbnailDirs = new ArrayMap<>();
    private ArrayMap<String, Size> videoSizes = new ArrayMap<>();
    private ArrayMap<String, Integer> videoBps = new ArrayMap<>();
    private ArrayMap<String, Boolean> isRecording = new ArrayMap<>();
    private ArrayMap<String, ImageReader> imageReaders = new ArrayMap<>();
    private ArrayMap<String, MediaRecorder> mediaRecorders = new ArrayMap<>();
    private ArrayMap<String, CameraDevice> cameraDevices = new ArrayMap<>();
    private ArrayMap<String, CameraCaptureSession> cameraCaptureSessions = new ArrayMap<>();
    private ICameraCallback cameraCallback = null;
    private ICaptureCallback captureCallback = null;
    private IRecordingCallback recordingCallback = null;
    private SimpleDateFormat dateFormat = new SimpleDateFormat("yyyyMMddHHmmss", Locale.CHINESE);
    private HandlerThread handlerThread = null;
    private Handler handler = null;
    private HandlerThread sessionHandlerThread = null;
    private Handler sessionHandler = null;
    private ArrayMap<String, Boolean> cameraFlags = new ArrayMap<>();
    private ArrayMap<String, Lock> cameraLocks = new ArrayMap<>();
    private ArrayMap<String, Condition> cameraConditions = new ArrayMap<>();
    private ArrayMap<String, LinkedBlockingDeque<CaptureResult>> captureResults = new ArrayMap<>();

    private CameraCaptureSession.CaptureCallback sessionCaptureCallback = new CameraCaptureSession.CaptureCallback() {
        @Override
        public void onCaptureCompleted(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull TotalCaptureResult result) {
            super.onCaptureCompleted(session, request, result);
            Log.i(TAG, "onCaptureCompleted");
            if (!cameraCaptureSessions.containsValue(session)) {
                return;
            }

            for (String cameraId : cameraCaptureSessions.keySet()) {
                if (session == cameraCaptureSessions.get(cameraId)) {
                    try {
                        captureResults.get(cameraId).put(result);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            }
        }
    };

    private CameraCaptureSession.CaptureCallback sessionRecordingCallback = new CameraCaptureSession.CaptureCallback() {
        @Override
        public void onCaptureSequenceCompleted(@NonNull CameraCaptureSession session, int sequenceId, long frameNumber) {
            super.onCaptureSequenceCompleted(session, sequenceId, frameNumber);
            if (!cameraCaptureSessions.containsValue(session)) {
                return;
            }

            for (String cameraId : cameraCaptureSessions.keySet()) {
                if (session == cameraCaptureSessions.get(cameraId)) {
                    Log.i(TAG, "onCaptureSequenceCompleted: cameraId = " + cameraId + ", sequenceId = " + sequenceId + ", frameNumber = " + frameNumber);
                    String filePath = recordingDirs.get(cameraId);
                    Size size = videoSizes.get(cameraId);
                    ContentValues values = new ContentValues();
                    MediaMetadataRetriever mmr = new MediaMetadataRetriever();
                    Bitmap thumbnail = null;
                    Uri uri = null;

                    releaseRecorder(cameraId);
                    try {
                        mmr.setDataSource(filePath);
                        values.put(MediaStore.Video.Media.DURATION, mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION));
                        thumbnail = mmr.getFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC);
                    } catch (RuntimeException e) {
                        e.printStackTrace();
                    }

                    /* Insert the video to MediaStore */
                    values.put(MediaStore.Video.Media.DATA, filePath);
                    if (null != size) {
                        values.put(MediaStore.Video.Media.WIDTH, size.getWidth());
                        values.put(MediaStore.Video.Media.HEIGHT, size.getHeight());
                    }
                    uri = context.getContentResolver().insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values);

                    /* Insert the thumbnail to MediaStore */
                    if (null != filePath) {
                        if (null == thumbnail) {
                            thumbnail = ThumbnailUtils.createVideoThumbnail(filePath, MediaStore.Video.Thumbnails.FULL_SCREEN_KIND);
                        }

                        if (null != thumbnail && null != uri) {
                            try {
                                String thumbnailPath = thumbnailDirs.get(cameraId) + filePath.substring(filePath.lastIndexOf(File.separator), filePath.lastIndexOf(".")) + ".jpg";
                                FileOutputStream fos = new FileOutputStream(thumbnailPath);

                                thumbnail.compress(Bitmap.CompressFormat.JPEG, 100, fos);
                                fos.flush();
                                fos.close();

                                values.clear();
                                values.put(MediaStore.Video.Thumbnails.DATA, thumbnailPath);
                                values.put(MediaStore.Video.Thumbnails.VIDEO_ID, ContentUris.parseId(uri));
                                values.put(MediaStore.Video.Thumbnails.WIDTH, thumbnail.getWidth());
                                values.put(MediaStore.Video.Thumbnails.HEIGHT, thumbnail.getHeight());
                                context.getContentResolver().insert(MediaStore.Video.Thumbnails.EXTERNAL_CONTENT_URI, values);
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                        }
                    }

                    if (null != recordingCallback) {
                        recordingCallback.onComplete(cameraId, filePath);
                    }
                }
            }
        }
    };

    private ImageReader.OnImageAvailableListener imageAvailableListener = new ImageReader.OnImageAvailableListener() {
        @Override
        public void onImageAvailable(ImageReader reader) {
            if (!imageReaders.containsValue(reader)) {
                return;
            }

            /* Lookup the camera id */
            for (String cameraId : imageReaders.keySet()) {
                if (reader == imageReaders.get(cameraId)) {
                    Log.i(TAG, "onImageAvailable: cameraId = " + cameraId + " width = " + reader.getWidth() + ", height = " + reader.getHeight());
                    Image image = reader.acquireNextImage();
                    ByteBuffer buffer = image.getPlanes()[0].getBuffer();
                    byte bytes[] = new byte[buffer.remaining()];
                    buffer.get(bytes);
                    String filePath = captureDirs.get(cameraId) + File.separator + dateFormat.format(new Date()) + ".jpg";

                    try {
                        /* Write the image data to the file */
                        FileOutputStream fos = new FileOutputStream(filePath);
                        fos.write(bytes);
                        fos.close();

                        ContentValues values = new ContentValues();
                        CaptureResult captureResult = captureResults.get(cameraId).take();
                        Location location = captureResult.get(CaptureResult.JPEG_GPS_LOCATION);

                        /* Write the location to the image */
                        if (null != location) {
                            double latitude = location.getLatitude();
                            double longitude = location.getLongitude();
                            ExifInterface exif = new ExifInterface(filePath);
                            exif.setAttribute(ExifInterface.TAG_GPS_LATITUDE_REF, ConvertUtils.convertToDegree(latitude));
                            exif.setAttribute(ExifInterface.TAG_GPS_LONGITUDE_REF, ConvertUtils.convertToDegree(longitude));
                            exif.saveAttributes();
                            values.put(MediaStore.Images.Media.LATITUDE, latitude);
                            values.put(MediaStore.Images.Media.LONGITUDE, longitude);
                        }

                        /* Insert the image to MediaStore */
                        values.put(MediaStore.Images.Media.DATA, filePath);
                        values.put(MediaStore.Images.Media.WIDTH, reader.getWidth());
                        values.put(MediaStore.Images.Media.HEIGHT, reader.getHeight());
                        context.getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
                    } catch (IOException | InterruptedException e) {
                        e.printStackTrace();
                    } finally {
                        if (null != captureCallback) {
                            captureCallback.onComplete(cameraId, filePath);
                        }

                        image.close();
                    }
                    break;
                }
            }
        }
    };

    private MediaRecorder.OnInfoListener recordingInfoListener = new MediaRecorder.OnInfoListener() {
        @Override
        public void onInfo(MediaRecorder mr, int what, int extra) {
            if (!mediaRecorders.containsValue(mr)) {
                return;
            }

            for (String cameraId : mediaRecorders.keySet()) {
                if (mr == mediaRecorders.get(cameraId)) {
                    Log.i(TAG, "onInfo: cameraId = " + cameraId + ", what = " + what + ", extra = " + extra);
                    isRecording.put(cameraId, false);
                    releaseRecorder(cameraId);
                    break;
                }
            }
        }
    };

    private MediaRecorder.OnErrorListener recordingErrorListener = new MediaRecorder.OnErrorListener() {
        @Override
        public void onError(MediaRecorder mr, int what, int extra) {
            if (!mediaRecorders.containsValue(mr)) {
                return;
            }

            for (String cameraId : mediaRecorders.keySet()) {
                if (mr == mediaRecorders.get(cameraId)) {
                    Log.e(TAG, "onError: cameraId = " + cameraId + ", what = " + what + ", extra = " + extra);
                    stopRecording(cameraId);

                    if (null != recordingCallback) {
                        recordingCallback.onError(cameraId, what, extra);
                    }

                    break;
                }
            }
        }
    };

    public static class CompareSizesByArea implements Comparator<Size> {
        @Override
        public int compare(Size lhs, Size rhs) {
            // We cast here to ensure the multiplications won't overflow
            return Long.signum((long) lhs.getWidth() * lhs.getHeight() -
                    (long) rhs.getWidth() * rhs.getHeight());
        }
    }

    public static class ConvertUtils {
        public static String convertToDegree(double gpsInfo) {
            return Location.convert(gpsInfo, Location.FORMAT_SECONDS);
        }

        public static Double convertToCoordinate(String stringDMS) {
            if (stringDMS == null) return null;
            String[] split = stringDMS.split(":", 3);
            return Double.parseDouble(split[0]) + Double.parseDouble(split[1]) / 60 + Double.parseDouble(split[2]) / 3600;
        }
    }

    public static ICamera getInstance(Context context) {
        if (null == instance) {
            synchronized (CameraNative.class) {
                if (null == instance) {
                    instance = new CameraNative(context);
                }
            }
        }

        return instance;
    }

    private CameraNative(Context context) {
        this.context = context.getApplicationContext();
        cameraManager = (CameraManager) this.context.getSystemService(Context.CAMERA_SERVICE);
        assert null != cameraManager;

        File captureDir = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES);
        if (null == captureDir) {
            captureDir = new File("/storage/emulated/0/Pictures");
            if (!captureDir.exists()) {
                if (!captureDir.exists()) {
                    if (captureDir.mkdirs()) {
                        Log.i(TAG, "CameraNative: make directory " + captureDir.getPath());
                    } else {
                        Log.e(TAG, "CameraNative: can't make directory " + captureDir.getPath());
                    }
                }
            }
        }

        File recordingDir = context.getExternalFilesDir(Environment.DIRECTORY_MOVIES);
        if (null == recordingDir) {
            recordingDir = new File("/storage/emulated/0/Movies");
            if (!recordingDir.exists()) {
                if (recordingDir.mkdirs()) {
                    Log.i(TAG, "CameraNative: make directory " + recordingDir.getPath());
                } else {
                    Log.e(TAG, "CameraNative: can't make directory " + recordingDir.getPath());
                }
            }
        }

        File thumbnailDir = new File(recordingDir.getParentFile(), "Thumbnails");
        if (!thumbnailDir.exists()) {
            if (thumbnailDir.mkdirs()) {
                Log.i(TAG, "CameraNative: make directory " + thumbnailDir.getPath());
            } else {
                Log.e(TAG, "CameraNative: can't make directory " + thumbnailDir.getPath());
            }
        }

        String cameraIds[] = getCameraIdList();
        if (null != cameraIds) {
            for (String cameraId : cameraIds) {
                captureDirs.put(cameraId, captureDir.getPath());
                recordingDirs.put(cameraId, recordingDir.getPath() + File.separator + "dummy.mp4");
                thumbnailDirs.put(cameraId, thumbnailDir.getPath());
                videoBps.put(cameraId, 3000000);
                isRecording.put(cameraId, false);
                cameraFlags.put(cameraId, false);
                cameraLocks.put(cameraId, new ReentrantLock());
                cameraConditions.put(cameraId, cameraLocks.get(cameraId).newCondition());
                captureResults.put(cameraId, new LinkedBlockingDeque<CaptureResult>());
            }
        }
    }

    @Override
    public String[] getCameraIdList() {
        try {
            return cameraManager.getCameraIdList();
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }

        return null;
    }

    @Override
    public Size[] getAvailablePreviewSizes(String cameraId) {
        try {
            StreamConfigurationMap map = cameraManager.getCameraCharacteristics(cameraId).get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
            if (null == map) {
                Log.e(TAG, "getAvailablePreviewSizes: camera no available size!");
                return null;
            }

            return map.getOutputSizes(SurfaceHolder.class);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }

        return null;
    }

    @Override
    public Size[] getAvailableCaptureSizes(String cameraId) {
        try {
            StreamConfigurationMap map = cameraManager.getCameraCharacteristics(cameraId).get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
            if (null == map) {
                Log.e(TAG, "getAvailableCaptureSizes: camera no available size!");
                return null;
            }

            return map.getOutputSizes(ImageFormat.JPEG);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }

        return null;
    }

    @Override
    public Size[] getAvailableRecordingSizes(String cameraId) {
        try {
            StreamConfigurationMap map = cameraManager.getCameraCharacteristics(cameraId).get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
            if (null == map) {
                Log.e(TAG, "getAvailableRecordingSizes: camera no available size!");
                return null;
            }

            return map.getOutputSizes(MediaRecorder.class);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }

        return null;
    }

    @Override
    public int setStateCallback(ICameraCallback callback) {
        cameraCallback = callback;
        return ResultCode.SUCCESS;
    }

    @Override
    public int setPreviewSurface(String cameraId, Surface surface) {
        Log.i(TAG, "setPreviewSurface: cameraId = " + cameraId);
        previewSurfaces.put(cameraId, surface);
        return ResultCode.SUCCESS;
    }

    @Override
    public int setCaptureSize(String cameraId, int width, int height) {
        Log.i(TAG, "setCaptureSize: cameraId = " + cameraId + ", width = " + width + ", height = " + height);
        if (isRecording.get(cameraId)) {
            return ResultCode.FAILED_WHILE_RECORDING;
        }

        ImageReader imageReader = ImageReader.newInstance(width, height, ImageFormat.JPEG, 2);

        imageReader.setOnImageAvailableListener(imageAvailableListener, handler);
        imageReaders.put(cameraId, imageReader);
        deleteCameraCaptureSession(cameraId);

        return createCameraCaptureSession(cameraId, Collections.singletonList(imageReaders.get(cameraId).getSurface()));
    }

    @Override
    public int setCaptureDir(String cameraId, String dir) {
        Log.i(TAG, "setCaptureDir: cameraId = " + cameraId + ", dir = " + dir);

        File file = new File(dir);
        if (!file.exists()) {
            if (file.mkdirs()) {
                Log.i(TAG, "setCaptureDir: make directory " + dir);
            } else {
                return ResultCode.CREATE_DIRECTORY_FAILED;
            }
        }

        captureDirs.put(cameraId, dir);

        return ResultCode.SUCCESS;
    }

    @Override
    public int setCaptureCallback(ICaptureCallback callback) {
        captureCallback = callback;
        return ResultCode.SUCCESS;
    }

    @Override
    public int setVideoSize(String cameraId, int width, int height) {
        Log.i(TAG, "setVideoSize: cameraId = " + cameraId + ", width = " + width + ", height = " + height);
        videoSizes.put(cameraId, new Size(width, height));
        return ResultCode.SUCCESS;
    }

    @Override
    public int setVideoEncodingBps(String cameraId, int bps) {
        Log.i(TAG, "setVideoEncodingBps: cameraId = " + cameraId + ", bps = " + bps);
        videoBps.put(cameraId, bps);
        return ResultCode.SUCCESS;
    }

    @Override
    public int setRecordingDir(String cameraId, String dir) {
        Log.i(TAG, "setRecordingDir: cameraId = " + cameraId + ", dir = " + dir);

        File recordingDir = new File(dir);
        if (!recordingDir.exists()) {
            if (recordingDir.mkdirs()) {
                Log.i(TAG, "setRecordingDir: make directory " + dir);
            } else {
                return ResultCode.CREATE_DIRECTORY_FAILED;
            }
        }

        recordingDirs.put(cameraId, dir + File.separator + "dummy.mp4");

        File thumbnailDir = new File(recordingDir.getParentFile(), "thumbnails");
        if (!thumbnailDir.exists()) {
            if(thumbnailDir.mkdirs()) {
                Log.i(TAG, "setRecordingDir: make directory " + thumbnailDir.getPath());
            } else {
                return ResultCode.CREATE_DIRECTORY_FAILED;
            }
        }

        thumbnailDirs.put(cameraId, thumbnailDir.getPath());

        return ResultCode.SUCCESS;
    }

    @Override
    public int setRecordingCallback(IRecordingCallback callback) {
        recordingCallback = callback;
        return ResultCode.SUCCESS;
    }

    @Override
    public int open(final String cameraId) {
        Log.i(TAG, "open: cameraId = " + cameraId);
        if (PackageManager.PERMISSION_GRANTED != ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA)) {
            Log.w(TAG, "open: camera permission denied!");
            return ResultCode.PERMISSION_CAMERA_DENIED;
        }
        if (PackageManager.PERMISSION_GRANTED != ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
            Log.w(TAG, "open: write external permission denied!");
            return ResultCode.PERMISSION_WRITE_EXTERNAL_STORAGE_DENIED;
        }
        if (PackageManager.PERMISSION_GRANTED != ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)) {
            Log.w(TAG, "open: record audio permission denied!");
            return ResultCode.PERMISSION_RECORD_AUDIO_DENIED;
        }

        close(cameraId);

        try {
            CameraCharacteristics characteristics = cameraManager.getCameraCharacteristics(cameraId);
            Integer sensorOrientation = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION);
            sensorOrientations.put(cameraId, sensorOrientation);
            Log.i(TAG, "open: camera " + cameraId + " sensor orientation =  " + sensorOrientation);

            Integer facing = characteristics.get(CameraCharacteristics.LENS_FACING);
            facings.put(cameraId, facing);
            Log.i(TAG, "open: camera " + cameraId + " facing =  " + facing);

            Integer level = characteristics.get(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL);
            Log.i(TAG, "open: camera " + cameraId + " supported level =  " + level);

            Size size = Collections.max(Arrays.asList(getAvailableCaptureSizes(cameraId)), new CompareSizesByArea());
            ImageReader imageReader = ImageReader.newInstance(size.getWidth(), size.getHeight(), ImageFormat.JPEG, 2);
            imageReader.setOnImageAvailableListener(imageAvailableListener, handler);
            imageReaders.put(cameraId, imageReader);
            Log.i(TAG, "open: camera " + cameraId + " capture largest size = " + size);

            size = Collections.max(Arrays.asList(getAvailableRecordingSizes(cameraId)), new CompareSizesByArea());
            videoSizes.put(cameraId, size);
            Log.i(TAG, "open: camera " + cameraId + " recording largest size = " + size);

            cameraFlags.put(cameraId, false);
            handlerThread = new HandlerThread("CameraHandlerThread");
            handlerThread.start();
            handler = new Handler(handlerThread.getLooper());
            cameraManager.openCamera(cameraId, new CameraDevice.StateCallback() {
                @Override
                public void onOpened(@NonNull CameraDevice camera) {
                    String cameraId = camera.getId();
                    Log.i(TAG, "onOpened: cameraId = " + cameraId);
                    cameraDevices.put(cameraId, camera);
                    createCameraCaptureSession(cameraId, Collections.singletonList(imageReaders.get(cameraId).getSurface()));
                    if (null != cameraCallback) {
                        cameraCallback.onState(cameraId, ICameraCallback.State.CAMERA_OPENED);
                    }
                }

                @Override
                public void onClosed(@NonNull CameraDevice camera) {
                    super.onClosed(camera);
                    Log.i(TAG, "onClosed: cameraId = " + camera.getId());
                    if (null != cameraCallback) {
                        cameraCallback.onState(cameraId, ICameraCallback.State.CAMERA_CLOSED);
                    }
                }

                @Override
                public void onDisconnected(@NonNull CameraDevice camera) {
                    String cameraId = camera.getId();
                    Log.i(TAG, "onDisconnected: cameraId = " + cameraId);
                    if (null != cameraCallback) {
                        cameraCallback.onState(cameraId, ICameraCallback.State.CAMERA_DISCONNECTED);
                    }
                }

                @Override
                public void onError(@NonNull CameraDevice camera, int error) {
                    String cameraId = camera.getId();
                    Log.i(TAG, "onError: cameraId = " + cameraId + ", error = " + error);
                    stopRecording(cameraId);
                    deleteCameraCaptureSession(cameraId);
                    closeDevice(cameraId);
                    if (null != cameraCallback) {
                        cameraCallback.onError(cameraId, error);
                    }
                }
            }, handler);
        } catch (CameraAccessException | IllegalArgumentException e) {
            e.printStackTrace();
            return ResultCode.CAMERA_EXCEPTION;
        }

        return ResultCode.SUCCESS;
    }

    @Override
    public int close(String cameraId) {
        Log.i(TAG, "close: cameraId = " + cameraId);
        stopRecording(cameraId);
        deleteCameraCaptureSession(cameraId);
        closeDevice(cameraId);

        if (null != handlerThread) {
            handlerThread.quitSafely();
            try {
                handlerThread.join();
            } catch (InterruptedException e) {
                e.printStackTrace();
            } finally {
                handlerThread = null;
                handler = null;
            }
        }

        if (null != imageReaders.get(cameraId)) {
            imageReaders.get(cameraId).close();
            imageReaders.remove(cameraId);
        }

        return ResultCode.SUCCESS;
    }

    @Override
    public int startPreview(String cameraId) {
        Log.i(TAG, "startPreview: cameraId = " + cameraId);
        if (null == previewSurfaces.get(cameraId) || null == imageReaders.get(cameraId)) {
            return ResultCode.NO_PREVIEW_SURFACE;
        }

        if (isRecording.get(cameraId)) {
            return ResultCode.FAILED_WHILE_RECORDING;
        }

        deleteCameraCaptureSession(cameraId);
        int ret = createCameraCaptureSession(cameraId, Arrays.asList(imageReaders.get(cameraId).getSurface(), previewSurfaces.get(cameraId)));
        if (ResultCode.SUCCESS != ret) {
            return ret;
        }

        try {
            final CaptureRequest.Builder captureBuilder = cameraDevices.get(cameraId).createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);

            captureBuilder.addTarget(previewSurfaces.get(cameraId));
            captureBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
            captureBuilder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH);
            cameraCaptureSessions.get(cameraId).setRepeatingRequest(captureBuilder.build(), null, handler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }

        return ResultCode.SUCCESS;
    }

    @Override
    public int stopPreview(String cameraId) {
        Log.i(TAG, "stopPreview: cameraId = " + cameraId);
        if (isRecording.get(cameraId)) {
            return ResultCode.FAILED_WHILE_RECORDING;
        }

        if (null != cameraCaptureSessions.get(cameraId)) {
            try {
                cameraCaptureSessions.get(cameraId).stopRepeating();
            } catch (CameraAccessException e) {
                e.printStackTrace();
            }
        }

        return ResultCode.SUCCESS;
    }

    @Override
    public int capture(String cameraId, double latitude, double longitude) {
        Log.i(TAG, "capture: cameraId = " + cameraId + ", latitude = " + latitude + ", longitude = " + longitude);
        if (null == cameraDevices.get(cameraId)) {
            return ResultCode.CAMERA_DEVICE_NULL;
        }

        if (null == cameraCaptureSessions.get(cameraId)) {
            return ResultCode.CAMERA_CAPTURE_SESSION_NULL;
        }

        try {
            final CaptureRequest.Builder captureBuilder = cameraDevices.get(cameraId).createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
            Location location = new Location(LocationManager.PASSIVE_PROVIDER);
            int rotation = ((WindowManager) context.getSystemService(Context.WINDOW_SERVICE)).getDefaultDisplay().getRotation();

            location.setLatitude(latitude);
            location.setLongitude(longitude);
            captureBuilder.addTarget(imageReaders.get(cameraId).getSurface());
            captureBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
            captureBuilder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH);
            captureBuilder.set(CaptureRequest.JPEG_GPS_LOCATION, location);
            captureBuilder.set(CaptureRequest.JPEG_ORIENTATION, getOrientation(cameraId, rotation));
            cameraCaptureSessions.get(cameraId).capture(captureBuilder.build(), sessionCaptureCallback, handler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }

        return ResultCode.SUCCESS;
    }

    @Override
    public int startRecording(String cameraId) {
        return startRecording(cameraId, 0);
    }

    @Override
    public int startRecording(String cameraId, int duration) {
        Log.i(TAG, "startRecording: cameraId = " + cameraId + ", duration = " + duration);
        if (null == cameraDevices.get(cameraId)) {
            return ResultCode.CAMERA_DEVICE_NULL;
        }

        if (isRecording.get(cameraId)) {
            return ResultCode.FAILED_WHILE_RECORDING;
        }

        prepareRecorder(cameraId, duration);

        try {
            List<Surface> surfaces = new ArrayList<>();
            CaptureRequest.Builder captureBuilder = cameraDevices.get(cameraId).createCaptureRequest(CameraDevice.TEMPLATE_RECORD);

            surfaces.add(imageReaders.get(cameraId).getSurface());
            surfaces.add(mediaRecorders.get(cameraId).getSurface());
            if (null != previewSurfaces.get(cameraId)) {
                surfaces.add(previewSurfaces.get(cameraId));
                captureBuilder.addTarget(previewSurfaces.get(cameraId));
            }

            deleteCameraCaptureSession(cameraId);
            int ret = createCameraCaptureSession(cameraId, surfaces);
            if (ResultCode.SUCCESS != ret) {
                return ret;
            }

            captureBuilder.addTarget(mediaRecorders.get(cameraId).getSurface());
            captureBuilder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO);
            cameraCaptureSessions.get(cameraId).setRepeatingRequest(captureBuilder.build(), sessionRecordingCallback, handler);
            mediaRecorders.get(cameraId).start();
            isRecording.put(cameraId, true);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }

        return ResultCode.SUCCESS;
    }

    @Override
    public int stopRecording(String cameraId) {
        Log.i(TAG, "stopRecording: cameraId = " + cameraId);
        if (null != mediaRecorders.get(cameraId)) {
            mediaRecorders.get(cameraId).stop();
        }

        isRecording.put(cameraId, false);
        releaseRecorder(cameraId);

        return ResultCode.SUCCESS;
    }

    private void notifyCamera(String cameraId) {
        cameraLocks.get(cameraId).lock();
        cameraFlags.put(cameraId, true);
        cameraConditions.get(cameraId).signalAll();
        cameraLocks.get(cameraId).unlock();
    }

    private void waitCamera(String cameraId) {
        try {
            cameraLocks.get(cameraId).lock();
            if (!cameraFlags.get(cameraId)) {
                cameraConditions.get(cameraId).await();
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
        } finally {
            cameraLocks.get(cameraId).unlock();
        }
    }

    private void closeDevice(String cameraId) {
        if (null != cameraDevices.get(cameraId)) {
            cameraDevices.get(cameraId).close();
            cameraDevices.remove(cameraId);
        }
    }

    private int createCameraCaptureSession(final String cameraId, List<Surface> surfaces) {
        if (null == cameraDevices.get(cameraId)) {
            return ResultCode.CAMERA_DEVICE_NULL;
        }

        final int[] ret = {ResultCode.SUCCESS};

        try {
            sessionHandlerThread = new HandlerThread("SessionHandlerThread");
            sessionHandlerThread.start();
            sessionHandler = new Handler(sessionHandlerThread.getLooper());
            cameraFlags.put(cameraId, false);
            cameraDevices.get(cameraId).createCaptureSession(surfaces, new CameraCaptureSession.StateCallback() {
                @Override
                public void onConfigured(@NonNull CameraCaptureSession session) {
                    String cameraId = session.getDevice().getId();
                    Log.i(TAG, "onConfigured: cameraId = " + cameraId);
                    cameraCaptureSessions.put(cameraId, session);
                    notifyCamera(cameraId);
                }

                @Override
                public void onConfigureFailed(@NonNull CameraCaptureSession session) {
                    String cameraId = session.getDevice().getId();
                    Log.i(TAG, "onConfigureFailed: cameraId = " + cameraId);
                    ret[0] = ResultCode.CAMERA_CAPTURE_SESSION_CONFIG_FAILED;
                    notifyCamera(cameraId);
                }
            }, sessionHandler);
            waitCamera(cameraId);
        } catch (CameraAccessException e) {
            e.printStackTrace();
            return ResultCode.CAMERA_EXCEPTION;
        }

        return ret[0];
    }

    private void deleteCameraCaptureSession(String cameraId) {
        if (null != cameraCaptureSessions.get(cameraId)) {
            try {
                cameraCaptureSessions.get(cameraId).abortCaptures();
                cameraCaptureSessions.get(cameraId).stopRepeating();
            } catch (CameraAccessException e) {
                e.printStackTrace();
            } finally {
                cameraCaptureSessions.get(cameraId).close();
                cameraCaptureSessions.remove(cameraId);
            }
        }

        if (null != sessionHandlerThread) {
            sessionHandlerThread.quitSafely();
            try {
                sessionHandlerThread.join();
            } catch (InterruptedException e) {
                e.printStackTrace();
            } finally {
                sessionHandlerThread = null;
                sessionHandler = null;
            }
        }
    }

    private void prepareRecorder(String cameraId, int duration) {
        Log.i(TAG, "prepareRecorder: cameraId = " + cameraId + ", duration = " + duration);
        MediaRecorder mediaRecorder = new MediaRecorder();
        String filePath = recordingDirs.get(cameraId).substring(0, recordingDirs.get(cameraId).lastIndexOf(File.separator)) + File.separator + dateFormat.format(new Date()) + ".mp4";

        mediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
        mediaRecorder.setVideoSource(MediaRecorder.VideoSource.SURFACE);
        mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
        mediaRecorder.setOutputFile(filePath);
        mediaRecorder.setVideoEncodingBitRate(videoBps.get(cameraId));
        mediaRecorder.setVideoFrameRate(30);
        mediaRecorder.setVideoSize(videoSizes.get(cameraId).getWidth(), videoSizes.get(cameraId).getHeight());
        mediaRecorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264);
        mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
        mediaRecorder.setMaxDuration(duration);
        mediaRecorder.setOnInfoListener(recordingInfoListener);
        mediaRecorder.setOnErrorListener(recordingErrorListener);

        Integer sensorOrientation = sensorOrientations.get(cameraId);
        if (null != sensorOrientation) {
            int rotation = ((WindowManager) context.getSystemService(Context.WINDOW_SERVICE)).getDefaultDisplay().getRotation();

            switch (sensorOrientation) {
                case SENSOR_ORIENTATION_DEGREES:
                    mediaRecorder.setOrientationHint(ORIENTATIONS.get(rotation));
                    break;
                case SENSOR_ORIENTATION_INVERSE_DEGREES:
                    mediaRecorder.setOrientationHint(INVERSE_ORIENTATIONS.get(rotation));
                    break;
                default:
                    break;
            }
        }

        try {
            mediaRecorder.prepare();
        } catch (IOException e) {
            e.printStackTrace();
        }

        recordingDirs.put(cameraId, filePath);
        mediaRecorders.put(cameraId, mediaRecorder);
    }

    private void releaseRecorder(String cameraId) {
        Log.i(TAG, "releaseRecorder: cameraId = " + cameraId);
        if (null != mediaRecorders.get(cameraId)) {
            synchronized (mediaRecorders.get(cameraId)) {
                if (null != mediaRecorders.get(cameraId)) {
                    mediaRecorders.get(cameraId).reset();
                    mediaRecorders.get(cameraId).release();
                    mediaRecorders.remove(cameraId);
                }
            }
        }
    }

    private int getOrientation(String cameraId, int rotation) {
        Integer sensorOrientation = sensorOrientations.get(cameraId);

        if (null == sensorOrientation) {
            sensorOrientation = 0;
        }

        // Sensor orientation is 90 for most devices, or 270 for some devices (eg. Nexus 5X)
        // We have to take that into account and rotate JPEG properly.
        // For devices with orientation of 90, we simply return our mapping from ORIENTATIONS.
        // For devices with orientation of 270, we need to rotate the JPEG 180 degrees.
        return (ORIENTATIONS.get(rotation) + sensorOrientation + 270) % 360;
    }

    private int getOrientation1(String cameraId, CameraCharacteristics c, int rotation) {
        if (OrientationEventListener.ORIENTATION_UNKNOWN == rotation){
            return 0;
        }

        // Round device orientation to a multiple of 90
        rotation = (rotation + 45) / 90 * 90;

        // Reverse device orientation for front-facing cameras
        Integer facing = facings.get(cameraId);
        if (null != facing && CameraCharacteristics.LENS_FACING_FRONT == facing) {
            rotation = -rotation;
        }

        Integer sensorOrientation = sensorOrientations.get(cameraId);
        if (null == sensorOrientation) {
            sensorOrientation = 0;
        }

        // Calculate desired JPEG orientation relative to camera orientation to make
        // the image upright relative to the device orientation
        return (sensorOrientation + rotation + 360) % 360;
    }
}
