package com.camera;

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
import java.util.Map;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class CameraNative implements ICamera {
    private static final String TAG = CameraNative.class.getSimpleName();
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
    private Map<String, Integer> sensorOrientations = new ArrayMap<>();
    private Map<String, Surface> previewSurfaces = new ArrayMap<>();
    private Map<String, String> captureDirs = new ArrayMap<>();
    private Map<String, String> recordDirs = new ArrayMap<>();
    private Map<String, String> thumbnailDirs = new ArrayMap<>();
    private Map<String, Size> recordSizes = new ArrayMap<>();
    private Map<String, Integer> videoBps = new ArrayMap<>();
    private Map<String, Boolean> isRecordings = new ArrayMap<>();
    private Map<String, ImageReader> imageReaders = new ArrayMap<>();
    private Map<String, MediaRecorder> mediaRecorders = new ArrayMap<>();
    private Map<String, CameraDevice> cameraDevices = new ArrayMap<>();
    private Map<String, CameraCaptureSession> cameraCaptureSessions = new ArrayMap<>();
    private ICameraCallback cameraCallback = null;
    private ICaptureCallback captureCallback = null;
    private IRecordCallback recordCallback = null;
    private SimpleDateFormat dateFormat = new SimpleDateFormat("yyyyMMddHHmmss", Locale.CHINESE);
    private HandlerThread handlerThread = null;
    private Handler handler = null;
    private HandlerThread sessionHandlerThread = null;
    private Handler sessionHandler = null;
    private Map<String, Boolean> cameraFlags = new ArrayMap<>();
    private Map<String, Lock> cameraLocks = new ArrayMap<>();
    private Map<String, Condition> cameraConditions = new ArrayMap<>();
    private Map<String, LinkedBlockingDeque<CaptureResult>> captureResults = new ArrayMap<>();

    private CameraCaptureSession.CaptureCallback sessionCaptureCallback = new CameraCaptureSession.CaptureCallback() {
        @Override
        public void onCaptureCompleted(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull TotalCaptureResult result) {
            super.onCaptureCompleted(session, request, result);

            String cameraId = null;

            for (Map.Entry<String, CameraCaptureSession> entry : cameraCaptureSessions.entrySet()) {
                if(session.equals(entry.getValue())){
                    cameraId = entry.getKey();
                    break;
                }
            }

            if(null == cameraId) {
                return;
            }

            Log.i(TAG, "onCaptureCompleted: cameraId = " + cameraId);

            try {
                LinkedBlockingDeque<CaptureResult> captureResultQueue = captureResults.get(cameraId);

                if (null != captureResultQueue) {
                    captureResultQueue.put(result);
                }
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    };

    private CameraCaptureSession.CaptureCallback sessionRecordCallback = new CameraCaptureSession.CaptureCallback() {
        @Override
        public void onCaptureSequenceCompleted(@NonNull CameraCaptureSession session, int sequenceId, long frameNumber) {
            super.onCaptureSequenceCompleted(session, sequenceId, frameNumber);

            String cameraId = null;

            for (Map.Entry<String, CameraCaptureSession> entry : cameraCaptureSessions.entrySet()) {
                if(session.equals(entry.getValue())){
                    cameraId = entry.getKey();
                    break;
                }
            }

            if(null == cameraId) {
                return;
            }

            String path = recordDirs.get(cameraId);
            Size size = recordSizes.get(cameraId);
            ContentValues values = new ContentValues();
            MediaMetadataRetriever mmr = new MediaMetadataRetriever();
            Bitmap thumbnail = null;
            Uri uri = null;

            Log.i(TAG, "onCaptureSequenceCompleted: cameraId = " + cameraId + ", sequenceId = " + sequenceId + ", frameNumber = " + frameNumber);
            releaseRecorder(cameraId);

            try {
                mmr.setDataSource(path);
                values.put(MediaStore.Video.Media.DURATION, mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION));
                thumbnail = mmr.getFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC);
                Log.i(TAG, "onCaptureSequenceCompleted: the video thumbnail(MediaMetadataRetriever) = " + thumbnail);
            } catch (RuntimeException e) {
                e.printStackTrace();
            }

            // Insert the video to MediaStore
            values.put(MediaStore.Video.Media.DATA, path);
            if (null != size) {
                values.put(MediaStore.Video.Media.WIDTH, size.getWidth());
                values.put(MediaStore.Video.Media.HEIGHT, size.getHeight());
            }
            uri = context.getContentResolver().insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values);
            Log.i(TAG, "onCaptureSequenceCompleted: insert the video to database, path = " + path + ", uri = " + uri);

            // Insert the thumbnail to MediaStore
            if (null != path) {
                if (null == thumbnail) {
                    thumbnail = ThumbnailUtils.createVideoThumbnail(path, MediaStore.Video.Thumbnails.FULL_SCREEN_KIND);
                    Log.i(TAG, "onCaptureSequenceCompleted: the video thumbnail(ThumbnailUtils) = " + thumbnail);
                }

                if (null != thumbnail && null != uri) {
                    try {
                        String thumbnailPath = thumbnailDirs.get(cameraId) + path.substring(path.lastIndexOf(File.separator), path.lastIndexOf('.')) + ".jpg";
                        FileOutputStream fos = new FileOutputStream(thumbnailPath);

                        thumbnail.compress(Bitmap.CompressFormat.JPEG, 100, fos);
                        fos.flush();
                        fos.close();

                        values.clear();
                        values.put(MediaStore.Video.Thumbnails.DATA, thumbnailPath);
                        values.put(MediaStore.Video.Thumbnails.VIDEO_ID, ContentUris.parseId(uri));
                        values.put(MediaStore.Video.Thumbnails.WIDTH, thumbnail.getWidth());
                        values.put(MediaStore.Video.Thumbnails.HEIGHT, thumbnail.getHeight());
                        Uri thumbnailUri = context.getContentResolver().insert(MediaStore.Video.Thumbnails.EXTERNAL_CONTENT_URI, values);
                        Log.i(TAG, "onCaptureSequenceCompleted: insert the video thumbnail to database, thumbnailPath = " + thumbnailPath + ", thumbnailUri = " + thumbnailUri);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }

            if (null != recordCallback) {
                recordCallback.onComplete(cameraId, path);
            }

        }
    };

    private ImageReader.OnImageAvailableListener imageAvailableListener = new ImageReader.OnImageAvailableListener() {
        @Override
        public void onImageAvailable(ImageReader reader) {
            String key = null;

            for (Map.Entry<String, ImageReader> entry : imageReaders.entrySet()) {
                if(reader.equals(entry.getValue())){
                    key = entry.getKey();
                    break;
                }
            }

            if(null == key) {
                return;
            }

            Image image = reader.acquireNextImage();
            ByteBuffer buffer = image.getPlanes()[0].getBuffer();
            final byte bytes[] = new byte[buffer.remaining()];
            final int width = reader.getWidth();
            final int height = reader.getHeight();
            final String cameraId = key;
            final String path = captureDirs.get(cameraId) + File.separator + dateFormat.format(new Date()) + ".jpg";

            buffer.get(bytes);
            Log.i(TAG, "onImageAvailable: cameraId = " + cameraId + " width = " + width + ", height = " + height);

            new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        // Write the image data to the file
                        FileOutputStream fos = new FileOutputStream(path);
                        fos.write(bytes);
                        fos.flush();
                        fos.close();

                        ContentValues values = new ContentValues();
                        LinkedBlockingDeque<CaptureResult> captureResultQueue = captureResults.get(cameraId);

                        if (null != captureResultQueue) {
                            CaptureResult captureResult = captureResultQueue.take();
                            Location location = captureResult.get(CaptureResult.JPEG_GPS_LOCATION);

                            // Write the location to the image
                            if (null != location) {
                                double latitude = location.getLatitude();
                                double longitude = location.getLongitude();
                                ExifInterface exif = new ExifInterface(path);
                                exif.setAttribute(ExifInterface.TAG_GPS_LATITUDE_REF, ConvertUtils.convertToDegree(latitude));
                                exif.setAttribute(ExifInterface.TAG_GPS_LONGITUDE_REF, ConvertUtils.convertToDegree(longitude));
                                exif.saveAttributes();
                                values.put(MediaStore.Images.Media.LATITUDE, latitude);
                                values.put(MediaStore.Images.Media.LONGITUDE, longitude);
                            }
                        }

                        // Insert the image to MediaStore
                        values.put(MediaStore.Images.Media.DATA, path);
                        values.put(MediaStore.Images.Media.WIDTH, width);
                        values.put(MediaStore.Images.Media.HEIGHT, height);
                        Uri uri = context.getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
                        Log.i(TAG, "run(onImageAvailable): insert the image file to database, path = " + path + ", uri = " + uri);
                    } catch (IOException | InterruptedException e) {
                        e.printStackTrace();
                    }

                    if (null != captureCallback) {
                        captureCallback.onComplete(cameraId, path);
                    }
                }
            }, "ImageReader.OnImageAvailableListener.onImageAvailable").start();
            image.close();
        }
    };

    private MediaRecorder.OnInfoListener recordInfoListener = new MediaRecorder.OnInfoListener() {
        @Override
        public void onInfo(MediaRecorder mr, int what, int extra) {
            String cameraId = null;

            for (Map.Entry<String, MediaRecorder> entry : mediaRecorders.entrySet()) {
                if(mr.equals(entry.getValue())){
                    cameraId = entry.getKey();
                    break;
                }
            }

            if(null == cameraId) {
                return;
            }

            Log.i(TAG, "onInfo: cameraId = " + cameraId + ", what = " + what + ", extra = " + extra);
            isRecordings.put(cameraId, false);
            releaseRecorder(cameraId);
        }
    };

    private MediaRecorder.OnErrorListener recordErrorListener = new MediaRecorder.OnErrorListener() {
        @Override
        public void onError(MediaRecorder mr, int what, int extra) {
            String cameraId = null;

            for (Map.Entry<String, MediaRecorder> entry : mediaRecorders.entrySet()) {
                if(mr.equals(entry.getValue())){
                    cameraId = entry.getKey();
                    break;
                }
            }

            if(null == cameraId) {
                return;
            }

            Log.e(TAG, "onError: cameraId = " + cameraId + ", what = " + what + ", extra = " + extra);
            stopRecord(cameraId);

            if (null != recordCallback) {
                recordCallback.onError(cameraId, what, extra);
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

        File captureDir = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES);
        if (null == captureDir) {
            captureDir = new File("/storage/emulated/0/Pictures");
            if (!captureDir.exists()) {
                if (captureDir.mkdirs()) {
                    Log.i(TAG, "CameraNative: make directory " + captureDir.getPath());
                } else {
                    Log.e(TAG, "CameraNative: make directory " + captureDir.getPath() + " failed!");
                }
            }
        }

        File recordDir = context.getExternalFilesDir(Environment.DIRECTORY_MOVIES);
        if (null == recordDir) {
            recordDir = new File("/storage/emulated/0/Movies");
            if (!recordDir.exists()) {
                if (recordDir.mkdirs()) {
                    Log.i(TAG, "CameraNative: make directory " + recordDir.getPath());
                } else {
                    Log.e(TAG, "CameraNative: make directory " + recordDir.getPath() + " failed!");
                }
            }
        }

        File thumbnailDir = new File(recordDir.getParentFile(), "Thumbnails");
        if (!thumbnailDir.exists()) {
            if (thumbnailDir.mkdirs()) {
                Log.i(TAG, "CameraNative: make directory " + thumbnailDir.getPath());
            } else {
                Log.e(TAG, "CameraNative: make directory " + thumbnailDir.getPath() + " failed!");
            }
        }

        String cameraIds[] = getCameraIdList();
        for (String cameraId : cameraIds) {
            try {
                CameraCharacteristics characteristics = cameraManager.getCameraCharacteristics(cameraId);
                Integer sensorOrientation = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION);
                sensorOrientations.put(cameraId, sensorOrientation);
                Log.i(TAG, "CameraNative: camera " + cameraId + " sensor orientation =  " + sensorOrientation);

                Integer facing = characteristics.get(CameraCharacteristics.LENS_FACING);
                Log.i(TAG, "CameraNative: camera " + cameraId + " facing =  " + facing);

                Integer level = characteristics.get(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL);
                Log.i(TAG, "CameraNative: camera " + cameraId + " supported level =  " + level);
            } catch (CameraAccessException e) {
                e.printStackTrace();
            }

            captureDirs.put(cameraId, captureDir.getPath());
            recordDirs.put(cameraId, recordDir.getPath() + File.separator + "dummy.mp4");
            thumbnailDirs.put(cameraId, thumbnailDir.getPath());
            videoBps.put(cameraId, 3000000);
            isRecordings.put(cameraId, false);
            cameraFlags.put(cameraId, false);
            cameraLocks.put(cameraId, new ReentrantLock());
            cameraConditions.put(cameraId, cameraLocks.get(cameraId).newCondition());
            captureResults.put(cameraId, new LinkedBlockingDeque<CaptureResult>());
        }
    }

    @Override
    public String[] getCameraIdList() {
        try {
            return cameraManager.getCameraIdList();
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }

        return new String[0];
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
    public Size[] getAvailableRecordSizes(String cameraId) {
        try {
            StreamConfigurationMap map = cameraManager.getCameraCharacteristics(cameraId).get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);

            if (null == map) {
                Log.e(TAG, "getAvailableRecordSizes: camera no available size!");
                return null;
            }

            return map.getOutputSizes(MediaRecorder.class);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }

        return null;
    }

    @Override
    public int setCameraCallback(ICameraCallback callback) {
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

        Boolean  isRecording = isRecordings.get(cameraId);
        if (null != isRecording && isRecording) {
            return ResultCode.FAILED_WHILE_RECORDING;
        }

        ImageReader imageReader = ImageReader.newInstance(width, height, ImageFormat.JPEG, 2);
        imageReader.setOnImageAvailableListener(imageAvailableListener, handler);
        imageReaders.put(cameraId, imageReader);
        deleteCameraCaptureSession(cameraId);

        return createCameraCaptureSession(cameraId, Collections.singletonList(imageReader.getSurface()));
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
    public int setRecordSize(String cameraId, int width, int height) {
        Log.i(TAG, "setRecordSize: cameraId = " + cameraId + ", width = " + width + ", height = " + height);
        recordSizes.put(cameraId, new Size(width, height));
        return ResultCode.SUCCESS;
    }

    @Override
    public int setVideoEncodingBps(String cameraId, int bps) {
        Log.i(TAG, "setVideoEncodingBps: cameraId = " + cameraId + ", bps = " + bps);
        videoBps.put(cameraId, bps);
        return ResultCode.SUCCESS;
    }

    @Override
    public int setRecordDir(String cameraId, String dir) {
        Log.i(TAG, "setRecordDir: cameraId = " + cameraId + ", dir = " + dir);

        File recordDir = new File(dir);
        if (!recordDir.exists()) {
            if (recordDir.mkdirs()) {
                Log.i(TAG, "setRecordDir: make directory " + dir);
            } else {
                return ResultCode.CREATE_DIRECTORY_FAILED;
            }
        }
        recordDirs.put(cameraId, dir + File.separator + "dummy.mp4");

        File thumbnailDir = new File(recordDir.getParentFile(), "thumbnails");
        if (!thumbnailDir.exists()) {
            if(thumbnailDir.mkdirs()) {
                Log.i(TAG, "setRecordDir: make directory " + thumbnailDir.getPath());
            } else {
                return ResultCode.CREATE_DIRECTORY_FAILED;
            }
        }
        thumbnailDirs.put(cameraId, thumbnailDir.getPath());

        return ResultCode.SUCCESS;
    }

    @Override
    public int setRecordCallback(IRecordCallback callback) {
        recordCallback = callback;
        return ResultCode.SUCCESS;
    }

    @Override
    public int open(final String cameraId) {
        Log.i(TAG, "open: cameraId = " + cameraId);

        if (PackageManager.PERMISSION_GRANTED != ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA)) {
            Log.w(TAG, "open: no camera permission!");
            return ResultCode.NO_CAMERA_PERMISSION;
        }

        if (PackageManager.PERMISSION_GRANTED != ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
            Log.w(TAG, "open: no write external storage permission!");
            return ResultCode.NO_WRITE_EXTERNAL_STORAGE_PERMISSION;
        }

        if (PackageManager.PERMISSION_GRANTED != ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)) {
            Log.w(TAG, "open: no record audio permission!");
            return ResultCode.NO_RECORD_AUDIO_PERMISSION;
        }

        close(cameraId);

        try {
            Size size = Collections.max(Arrays.asList(getAvailableCaptureSizes(cameraId)), new CompareSizesByArea());
            final ImageReader imageReader = ImageReader.newInstance(size.getWidth(), size.getHeight(), ImageFormat.JPEG, 2);

            imageReader.setOnImageAvailableListener(imageAvailableListener, handler);
            imageReaders.put(cameraId, imageReader);
            Log.i(TAG, "open: camera " + cameraId + " capture size = " + size);

            size = Collections.max(Arrays.asList(getAvailableRecordSizes(cameraId)), new CompareSizesByArea());
            recordSizes.put(cameraId, size);
            Log.i(TAG, "open: camera " + cameraId + " record size = " + size);

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
                    createCameraCaptureSession(cameraId, Collections.singletonList(imageReader.getSurface()));

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
                    stopRecord(cameraId);
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
        stopRecord(cameraId);
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

        ImageReader imageReader = imageReaders.get(cameraId);
        if (null != imageReader) {
            imageReader.close();
            imageReaders.remove(cameraId);
        }

        return ResultCode.SUCCESS;
    }

    @Override
    public int startPreview(String cameraId) {
        Log.i(TAG, "startPreview: cameraId = " + cameraId);

        Surface previewSurface = previewSurfaces.get(cameraId);
        if (null == previewSurface) {
            return ResultCode.NO_PREVIEW_SURFACE;
        }

        ImageReader imageReader = imageReaders.get(cameraId);
        if (null == imageReader) {
            return ResultCode.NO_IMAGE_READER;
        }

        Boolean  isRecording = isRecordings.get(cameraId);
        if (null != isRecording && isRecording) {
            return ResultCode.FAILED_WHILE_RECORDING;
        }

        deleteCameraCaptureSession(cameraId);
        int ret = createCameraCaptureSession(cameraId, Arrays.asList(imageReader.getSurface(), previewSurface));
        if (ResultCode.SUCCESS != ret) {
            return ret;
        }

        CameraDevice cameraDevice = cameraDevices.get(cameraId);
        if (null == cameraDevice) {
            return ResultCode.CAMERA_DEVICE_NULL;
        }

        CameraCaptureSession cameraCaptureSession = cameraCaptureSessions.get(cameraId);
        if (null == cameraCaptureSession) {
            return ResultCode.CAMERA_CAPTURE_SESSION_NULL;
        }

        try {
            CaptureRequest.Builder captureBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);

            captureBuilder.addTarget(previewSurface);
            captureBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
            captureBuilder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH);
            cameraCaptureSession.setRepeatingRequest(captureBuilder.build(), null, handler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }

        return ResultCode.SUCCESS;
    }

    @Override
    public int stopPreview(String cameraId) {
        Log.i(TAG, "stopPreview: cameraId = " + cameraId);

        Boolean  isRecording = isRecordings.get(cameraId);
        if (null != isRecording && isRecording) {
            return ResultCode.FAILED_WHILE_RECORDING;
        }

        CameraCaptureSession cameraCaptureSession = cameraCaptureSessions.get(cameraId);
        if (null != cameraCaptureSession) {
            try {
                cameraCaptureSession.stopRepeating();
            } catch (CameraAccessException e) {
                e.printStackTrace();
            }
        }

        return ResultCode.SUCCESS;
    }

    @Override
    public int capture(String cameraId, double latitude, double longitude) {
        Log.i(TAG, "capture: cameraId = " + cameraId + ", latitude = " + latitude + ", longitude = " + longitude);

        CameraDevice cameraDevice = cameraDevices.get(cameraId);
        if (null == cameraDevice) {
            return ResultCode.CAMERA_DEVICE_NULL;
        }

        CameraCaptureSession cameraCaptureSession = cameraCaptureSessions.get(cameraId);
        if (null == cameraCaptureSession) {
            return ResultCode.CAMERA_CAPTURE_SESSION_NULL;
        }

        ImageReader imageReader = imageReaders.get(cameraId);
        if (null == imageReader) {
            return ResultCode.NO_IMAGE_READER;
        }

        try {
            CaptureRequest.Builder captureBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
            Location location = new Location(LocationManager.PASSIVE_PROVIDER);
            WindowManager windowManager = ((WindowManager) context.getSystemService(Context.WINDOW_SERVICE));

            if (null != windowManager) {
                captureBuilder.set(CaptureRequest.JPEG_ORIENTATION, getOrientation(cameraId, windowManager.getDefaultDisplay().getRotation()));
            }

            location.setLatitude(latitude);
            location.setLongitude(longitude);
            captureBuilder.addTarget(imageReader.getSurface());
            captureBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
            captureBuilder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH);
            captureBuilder.set(CaptureRequest.JPEG_GPS_LOCATION, location);
            cameraCaptureSession.capture(captureBuilder.build(), sessionCaptureCallback, handler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }

        return ResultCode.SUCCESS;
    }

    @Override
    public int startRecord(String cameraId) {
        return startRecord(cameraId, 0);
    }

    @Override
    public int startRecord(String cameraId, int duration) {
        Log.i(TAG, "startRecord: cameraId = " + cameraId + ", duration = " + duration);

        Boolean  isRecording = isRecordings.get(cameraId);
        if (null != isRecording && isRecording) {
            return ResultCode.FAILED_WHILE_RECORDING;
        }

        CameraDevice cameraDevice = cameraDevices.get(cameraId);
        if (null == cameraDevice) {
            return ResultCode.CAMERA_DEVICE_NULL;
        }

        ImageReader imageReader = imageReaders.get(cameraId);
        if (null == imageReader) {
            return ResultCode.NO_IMAGE_READER;
        }

        prepareRecorder(cameraId, duration);

        MediaRecorder mediaRecorder = mediaRecorders.get(cameraId);
        if (null == mediaRecorder) {
            return ResultCode.NO_MEDIA_RECORDER;
        }

        try {
            List<Surface> surfaces = new ArrayList<>();
            CaptureRequest.Builder captureBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_RECORD);
            Surface previewSurface = previewSurfaces.get(cameraId);

            if (null != previewSurface) {
                surfaces.add(previewSurface);
                captureBuilder.addTarget(previewSurface);
            }

            surfaces.add(imageReader.getSurface());
            surfaces.add(mediaRecorder.getSurface());

            deleteCameraCaptureSession(cameraId);
            int ret = createCameraCaptureSession(cameraId, surfaces);
            if (ResultCode.SUCCESS != ret) {
                return ret;
            }

            CameraCaptureSession cameraCaptureSession = cameraCaptureSessions.get(cameraId);
            if (null == cameraCaptureSession) {
                return ResultCode.CAMERA_CAPTURE_SESSION_NULL;
            }

            captureBuilder.addTarget(mediaRecorder.getSurface());
            captureBuilder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO);
            cameraCaptureSession.setRepeatingRequest(captureBuilder.build(), sessionRecordCallback, handler);
            mediaRecorder.start();
            isRecordings.put(cameraId, true);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }

        return ResultCode.SUCCESS;
    }

    @Override
    public int stopRecord(String cameraId) {
        Log.i(TAG, "stopRecord: cameraId = " + cameraId);

        MediaRecorder mediaRecorder = mediaRecorders.get(cameraId);

        if (null != mediaRecorder) {
            mediaRecorder.stop();
        }

        isRecordings.put(cameraId, false);
        releaseRecorder(cameraId);

        return ResultCode.SUCCESS;
    }

    private void notifyCamera(String cameraId) {
        Lock lock = cameraLocks.get(cameraId);
        Condition condition = cameraConditions.get(cameraId);

        if (null == lock || null == condition) {
            return;
        }

        lock.lock();
        cameraFlags.put(cameraId, true);
        condition.signalAll();
        lock.unlock();
    }

    private void waitCamera(String cameraId) {
        Lock lock = cameraLocks.get(cameraId);
        Condition condition = cameraConditions.get(cameraId);
        Boolean cameraFlag = cameraFlags.get(cameraId);

        if (null == lock || null == condition || null == cameraFlag) {
            return;
        }

        try {
            lock.lock();
            if (!cameraFlag) {
                condition.await();
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
        } finally {
            lock.unlock();
        }
    }

    private void closeDevice(String cameraId) {
        CameraDevice cameraDevice = cameraDevices.get(cameraId);

        if (null != cameraDevice) {
            cameraDevice.close();
            cameraDevices.remove(cameraId);
        }
    }

    private int createCameraCaptureSession(final String cameraId, List<Surface> surfaces) {
        CameraDevice cameraDevice = cameraDevices.get(cameraId);

        if (null == cameraDevice) {
            return ResultCode.CAMERA_DEVICE_NULL;
        }

        final int[] ret = {ResultCode.SUCCESS};

        try {
            sessionHandlerThread = new HandlerThread("SessionHandlerThread");
            sessionHandlerThread.start();
            sessionHandler = new Handler(sessionHandlerThread.getLooper());
            cameraFlags.put(cameraId, false);
            cameraDevice.createCaptureSession(surfaces, new CameraCaptureSession.StateCallback() {
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
        CameraCaptureSession cameraCaptureSession = cameraCaptureSessions.get(cameraId);

        if (null != cameraCaptureSession) {
            try {
                cameraCaptureSession.abortCaptures();
                cameraCaptureSession.stopRepeating();
            } catch (CameraAccessException e) {
                e.printStackTrace();
            } finally {
                cameraCaptureSession.close();
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
        String path = recordDirs.get(cameraId).substring(0, recordDirs.get(cameraId).lastIndexOf(File.separator)) + File.separator + dateFormat.format(new Date()) + ".mp4";
        Integer sensorOrientation = sensorOrientations.get(cameraId);
        WindowManager windowManager = ((WindowManager) context.getSystemService(Context.WINDOW_SERVICE));

        mediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
        mediaRecorder.setVideoSource(MediaRecorder.VideoSource.SURFACE);
        mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
        mediaRecorder.setOutputFile(path);
        mediaRecorder.setVideoEncodingBitRate(videoBps.get(cameraId));
        mediaRecorder.setVideoFrameRate(30);
        mediaRecorder.setVideoSize(recordSizes.get(cameraId).getWidth(), recordSizes.get(cameraId).getHeight());
        mediaRecorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264);
        mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
        mediaRecorder.setMaxDuration(duration);
        mediaRecorder.setOnInfoListener(recordInfoListener);
        mediaRecorder.setOnErrorListener(recordErrorListener);

        if (null != sensorOrientation && null != windowManager) {
            int rotation = windowManager.getDefaultDisplay().getRotation();

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

        recordDirs.put(cameraId, path);
        mediaRecorders.put(cameraId, mediaRecorder);
    }

    private void releaseRecorder(String cameraId) {
        Log.i(TAG, "releaseRecorder: cameraId = " + cameraId);

        MediaRecorder mediaRecorder = mediaRecorders.get(cameraId);

        if (null != mediaRecorder) {
            synchronized (mediaRecorders) {
                if (null != (mediaRecorder = mediaRecorders.get(cameraId))) {
                    mediaRecorder.reset();
                    mediaRecorder.release();
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

    private int getJpegOrientation(CameraCharacteristics c, int deviceOrientation) {
        if (deviceOrientation == android.view.OrientationEventListener.ORIENTATION_UNKNOWN) return 0;
        int sensorOrientation = c.get(CameraCharacteristics.SENSOR_ORIENTATION);

        // Round device orientation to a multiple of 90
        deviceOrientation = (deviceOrientation + 45) / 90 * 90;

        // Reverse device orientation for front-facing cameras
        boolean facingFront = c.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_FRONT;
        if (facingFront) deviceOrientation = -deviceOrientation;

        // Calculate desired JPEG orientation relative to camera orientation to make
        // the image upright relative to the device orientation
        int jpegOrientation = (sensorOrientation + deviceOrientation + 360) % 360;

        return jpegOrientation;
    }
}
