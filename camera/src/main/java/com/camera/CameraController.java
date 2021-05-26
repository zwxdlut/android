package com.camera;

import android.Manifest;
import android.app.Application;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.ImageFormat;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureFailure;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.location.Location;
import android.media.Image;
import android.media.ImageReader;
import android.media.MediaMetadataRetriever;
import android.media.MediaRecorder;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.ParcelFileDescriptor;
import android.provider.MediaStore;
import android.util.ArrayMap;
import android.util.Log;
import android.util.Size;
import android.util.SparseIntArray;
import android.view.Surface;
import android.view.WindowManager;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.exifinterface.media.ExifInterface;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.InvocationTargetException;
import java.nio.ByteBuffer;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The CameraController class provides control and operation of the cameras.
 */
public class CameraController {
    private static final String TAG = CameraController.class.getSimpleName();
    private static final int SURFACE_PREVIEW = 0;
    private static final int SURFACE_CAPTURE = 1;
    private static final int SURFACE_RECORD = 2;
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

    private String publicDir = null;
    private String privateDir = null;
    private Context context = null;
    private CameraManager cameraManager = null;
    private Map<String, Integer> sensorOrientations = new ArrayMap<>();
    private Map<String, Boolean> isCapturePublicDirs = new ArrayMap<>();
    private Map<String, Boolean> isRecordPublicDirs = new ArrayMap<>();
    private Map<String, String> captureRelativeDirs = new ArrayMap<>();
    private Map<String, String> recordRelativeDirs = new ArrayMap<>();
    private Map<String, String> capturePaths = new ArrayMap<>();
    private Map<String, String> recordPaths = new ArrayMap<>();
    private Map<String, Uri> recorddUri = new ArrayMap<>();
    private Map<String, Size> captureSizes = new ArrayMap<>();
    private Map<String, Size> recordSizes = new ArrayMap<>();
    private Map<String, Location> captureLocations = new ArrayMap<>();
    private Map<String, Long> recordTimes = new ArrayMap<>();
    private Map<String, Boolean> isRecordings = new ArrayMap<>();
    private Map<String, Boolean> audioMutes = new ArrayMap<>();
    private Map<String, Integer> videoEncodingRates = new ArrayMap<>();
    private Map<String, Surface[]> surfaces = new ArrayMap<>();
    private Map<String, Surface> previewSurfaces = new ArrayMap<>();
    private Map<String, ImageReader> imageReaders = new ArrayMap<>();
    private Map<String, MediaRecorder> mediaRecorders = new ArrayMap<>();
    private Map<String, CameraDevice> cameraDevices = new ArrayMap<>();
    private Map<String, CameraCaptureSession> cameraCaptureSessions = new ArrayMap<>();
    private Map<String, CaptureRequest.Builder> captureBuilders = new ArrayMap<>();
    private SimpleDateFormat nameDateFormat = new SimpleDateFormat("yyyyMMddHHmmss", Locale.getDefault());
    private SimpleDateFormat exifDateFormat = new SimpleDateFormat("yyyy:MM:dd HH:mm:ss", Locale.getDefault());
    private HandlerThread handlerThread = null;
    private Handler handler = null;
    private ICameraCallback cameraCallback = null;
    private ICaptureCallback captureCallback = null;
    private IRecordCallback recordCallback = null;

    private CameraCaptureSession.CaptureCallback captureSessionCallback = new CameraCaptureSession.CaptureCallback() {
        @Override
        public void onCaptureStarted(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, long timestamp, long frameNumber) {
            super.onCaptureStarted(session, request, timestamp, frameNumber);

            String cameraId = findCameraId(session);
            if(null == cameraId) {
                Log.e(TAG, "onCaptureStarted: don't find camera id!");
                return;
            }

            String path = capturePaths.get(cameraId);
            Log.i(TAG, "onCaptureStarted: cameraId = " + cameraId + ", path = " + path);

            if (null != captureCallback) {
                captureCallback.onStarted(cameraId, path);
            }
        }

        @Override
        public void onCaptureCompleted(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull TotalCaptureResult result) {
            super.onCaptureCompleted(session, request, result);

            String cameraId = findCameraId(session);
            if(null == cameraId) {
                Log.e(TAG, "onCaptureCompleted: don't find camera id!");
                return;
            }

            Log.i(TAG, "onCaptureCompleted: cameraId = " + cameraId + ", path = " + capturePaths.get(cameraId));
        }

        @Override
        public void onCaptureFailed(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull CaptureFailure failure) {
            super.onCaptureFailed(session, request, failure);

            String cameraId = findCameraId(session);
            if(null == cameraId) {
                Log.e(TAG, "onCaptureFailed: don't find camera id!");
                return;
            }

            String path = capturePaths.get(cameraId);
            Log.e(TAG, "onCaptureFailed: cameraId = " + cameraId + ", path = " + path);

            if (null != captureCallback) {
                captureCallback.onFailed(cameraId, path);
            }
        }
    };

    private CameraCaptureSession.CaptureCallback recordSessionCallback = new CameraCaptureSession.CaptureCallback() {
        @Override
        public void onCaptureSequenceCompleted(@NonNull CameraCaptureSession session, int sequenceId, long frameNumber) {
            super.onCaptureSequenceCompleted(session, sequenceId, frameNumber);

            final String cameraId = findCameraId(session);
            if(null == cameraId) {
                Log.e(TAG, "onCaptureSequenceCompleted: don't find camera id!");
                return;
            }

            final String path = recordPaths.get(cameraId);
            Log.i(TAG, "onCaptureSequenceCompleted: cameraId = " + cameraId + ", path = " + path +
                    ", sequenceId = " + sequenceId + ", frameNumber = " + frameNumber);
            if(null == path) {
                return;
            }

            new Thread(new Runnable() {
                @Override
                public void run() {
                    Log.i(TAG, "onCaptureSequenceCompleted.SaveRecordThread: +");

                    // for MediaMetadataRetriever
                    releaseRecorder(cameraId);

                    if ((Build.VERSION.SDK_INT > Build.VERSION_CODES.Q
                            || (Build.VERSION.SDK_INT == Build.VERSION_CODES.Q && !Environment.isExternalStorageLegacy()))
                            && isRecordPublicDirs.get(cameraId)) {
                        ContentResolver resolver = context.getContentResolver();
                        ContentValues values = new ContentValues();
                        Uri uri = recorddUri.get(cameraId);

                        if (null != uri) {
                            try {
                                ParcelFileDescriptor pfd = resolver.openFileDescriptor(uri, "r");

                                if (null != pfd) {
                                    MediaMetadataRetriever mmr = new MediaMetadataRetriever();

                                    // update the size and duration to database
                                    mmr.setDataSource(pfd.getFileDescriptor());
                                    values.clear();
                                    values.put(MediaStore.Video.Media.SIZE, pfd.getStatSize());
                                    values.put(MediaStore.Video.Media.DURATION, mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION));
                                    resolver.update(uri, values, null, null);
                                    Log.i(TAG, "onCaptureSequenceCompleted.SaveRecordThread: update the size and duration to database");
                                    pfd.close();
                                }
                            } catch (RuntimeException | IOException e) {
                                e.printStackTrace();
                            }
                        }
                    } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && !isRecordPublicDirs.get(cameraId)){
                        MediaMetadataRetriever mmr = new MediaMetadataRetriever();
                        Bitmap thumbnail = null;

                        mmr.setDataSource(path);
                        thumbnail = mmr.getFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC);
                        Log.i(TAG, "onCaptureSequenceCompleted.SaveRecordThread: the video thumbnail(MediaMetadataRetriever) = " + thumbnail);

                        if (null != thumbnail) {
                            String thumbnailPath = path.substring(0, path.lastIndexOf('.')) + ".jpg";

                            // write the video thumbnail to file
                            try {
                                FileOutputStream fos = new FileOutputStream(thumbnailPath);

                                thumbnail.compress(Bitmap.CompressFormat.JPEG, 100, fos);
                                fos.flush();
                                fos.close();
                                Log.i(TAG, "onCaptureSequenceCompleted.SaveRecordThread: write the video thumbnail to file, thumbnailPath = " + thumbnailPath);
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                        }
                    } else {
                        String name = path.substring(path.lastIndexOf(File.separator) + 1);
                        File file = new File(path);
                        MediaMetadataRetriever mmr = new MediaMetadataRetriever();
                        ContentResolver resolver = context.getContentResolver();
                        ContentValues values = new ContentValues();
                        Uri uri = null;

                        mmr.setDataSource(path);

                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            values.put(MediaStore.Video.Media.RELATIVE_PATH, recordRelativeDirs.get(cameraId));
                        }

                        values.put(MediaStore.Video.Media.DATA, path);
                        values.put(MediaStore.Video.Media.DISPLAY_NAME, name);
                        values.put(MediaStore.Video.Media.TITLE,  name.substring(0, name.lastIndexOf(".")));
                        values.put(MediaStore.Video.Media.WIDTH, recordSizes.get(cameraId).getWidth());
                        values.put(MediaStore.Video.Media.HEIGHT, recordSizes.get(cameraId).getHeight());
                        values.put(MediaStore.Video.Media.SIZE, file.length());
                        values.put(MediaStore.Video.Media.DATE_TAKEN, recordTimes.get(cameraId));
                        values.put(MediaStore.Video.Media.DURATION, mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION));
                        uri = resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values);
                        Log.i(TAG, "onCaptureSequenceCompleted.SaveRecordThread: insert the video to database, uri = " + uri);

                        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                            Bitmap thumbnail = mmr.getFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC);

                            Log.i(TAG, "onCaptureSequenceCompleted.SaveRecordThread: the video thumbnail(MediaMetadataRetriever) = " + thumbnail);

                            if (null != thumbnail) {
                                try {
                                    String thumbnailPath = path.substring(0, path.lastIndexOf('.')) + ".jpg";
                                    FileOutputStream fos = new FileOutputStream(thumbnailPath);

                                    // write the video thumbnail to file
                                    thumbnail.compress(Bitmap.CompressFormat.JPEG, 100, fos);
                                    fos.flush();
                                    fos.close();
                                    Log.i(TAG, "onCaptureSequenceCompleted.SaveRecordThread: write the video thumbnail to file, thumbnailPath = " + thumbnailPath);

                                    if (null != uri) {
                                        Uri thumbnailUri = null;

                                        // insert the video thumbnail to database
                                        values.clear();
                                        values.put(MediaStore.Video.Thumbnails.DATA, thumbnailPath);
                                        values.put(MediaStore.Video.Thumbnails.VIDEO_ID, ContentUris.parseId(uri));
                                        values.put(MediaStore.Video.Thumbnails.WIDTH, thumbnail.getWidth());
                                        values.put(MediaStore.Video.Thumbnails.HEIGHT, thumbnail.getHeight());
                                        thumbnailUri = resolver.insert(MediaStore.Video.Thumbnails.EXTERNAL_CONTENT_URI, values);
                                        Log.i(TAG, "onCaptureSequenceCompleted.SaveRecordThread: insert the video thumbnail to database, thumbnailUri = " + thumbnailUri);
                                    }
                                } catch (IOException e) {
                                    e.printStackTrace();
                                }
                            }
                        }
                    }

                    if (null != recordCallback) {
                        recordCallback.onCompleted(cameraId, path);
                    }

                    Log.i(TAG, "onCaptureSequenceCompleted.SaveRecordThread: -");
                }
            }, "SaveRecordThread").start();
        }
    };

    private ImageReader.OnImageAvailableListener imageAvailableListener = new ImageReader.OnImageAvailableListener() {
        @Override
        public void onImageAvailable(final ImageReader reader) {
            Log.i(TAG, "onImageAvailable: width = " + reader.getWidth() + ", height = " + reader.getHeight());

            // Must call acquireNextImage() and close(),
            // or it will block the thread and the preview surface.
            Image image = reader.acquireNextImage();
            ByteBuffer buffer = image.getPlanes()[0].getBuffer();
            final byte[] bytes = new byte[buffer.remaining()];
            buffer.get(bytes);
            image.close();

            final String cameraId = findCameraId(reader);
            if(null == cameraId) {
                Log.e(TAG, "onImageAvailable: don't find camera id!");
                return;
            }

            final String path = capturePaths.get(cameraId);
            Log.i(TAG, "onImageAvailable: cameraId = " + cameraId + ", path = " + path);
            if(null == path) {
                return;
            }

            new Thread(new Runnable() {
                @Override
                public void run() {
                    Log.i(TAG, "onImageAvailable.SaveImageThread: +");

                    Location location = captureLocations.get(cameraId);
                    
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && !isCapturePublicDirs.get(cameraId)) {
                        try {
                            FileOutputStream fos = new FileOutputStream(path);

                            // write the image to file
                            fos.write(bytes);
                            fos.flush();
                            fos.close();
                            Log.i(TAG, "onImageAvailable.SaveImageThread: write the image to file, path = " + path);

                            // write the location to image file
                            writeImageLocation(new ExifInterface(path), location);
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    } else {
                        String name = path.substring(path.lastIndexOf(File.separator) + 1);
                        String title = name.substring(0, name.lastIndexOf("."));
                        ContentResolver resolver = context.getContentResolver();
                        ContentValues values = new ContentValues();
                        Uri uri = null;

                        // insert the image to database
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            values.put(MediaStore.Images.Media.RELATIVE_PATH, captureRelativeDirs.get(cameraId));
                        }
                        
                        if (null != location) {
                            values.put(MediaStore.Images.Media.LATITUDE, location.getLatitude());
                            values.put(MediaStore.Images.Media.LONGITUDE, location.getLongitude());
                        }
                        
                        values.put(MediaStore.Images.Media.DATA, path);
                        values.put(MediaStore.Images.Media.DISPLAY_NAME, name);
                        values.put(MediaStore.Images.Media.TITLE, title);
                        values.put(MediaStore.Images.Media.SIZE, bytes.length);
                        values.put(MediaStore.Images.Media.WIDTH, reader.getWidth());
                        values.put(MediaStore.Images.Media.HEIGHT, reader.getHeight());
                        uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
                        Log.i(TAG, "onImageAvailable.SaveImageThread: insert the image to database, uri = " + uri);

                        if (null != uri) {
                            try {
                                OutputStream os = resolver.openOutputStream(uri);

                                if (null != os) {
                                    // write the image to file
                                    os.write(bytes);
                                    os.flush();
                                    os.close();
                                    Log.i(TAG, "onImageAvailable.SaveImageThread: write the image to file, path = " + path);

                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                        uri = MediaStore.setRequireOriginal(uri);
                                    }
                                    
                                    ParcelFileDescriptor pfd = resolver.openFileDescriptor(uri, "rw");

                                    if (null != pfd) {
                                        ExifInterface exif = new ExifInterface(pfd.getFileDescriptor());
                                        String dateTime = exif.getAttribute(ExifInterface.TAG_DATETIME);

                                        // update the date time to database
                                        if (null != dateTime) {
                                            try {
                                                Date date = exifDateFormat.parse(dateTime);

                                                if (null != date) {
                                                    values.clear();
                                                    values.put(MediaStore.Images.Media.DATE_TAKEN, date.getTime());
                                                    resolver.update(uri, values, null, null);
                                                    Log.i(TAG, "onImageAvailable.SaveImageThread: update the date time to database");
                                                }
                                            } catch (ParseException e) {
                                                e.printStackTrace();
                                            }
                                        }

                                        // write the location to image file
                                        writeImageLocation(exif, location);

                                        pfd.close();
                                    }
                                }
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                        }
                    }

                    if (null != captureCallback) {
                        captureCallback.onCompleted(cameraId, path);
                    }

                    Log.i(TAG, "onImageAvailable.SaveImageThread: -");
                }
            }, "SaveImageThread").start();
        }
    };
    
    private MediaRecorder.OnInfoListener recordInfoListener = new MediaRecorder.OnInfoListener() {
        @Override
        public void onInfo(MediaRecorder mr, int what, int extra) {
            String cameraId = findCameraId(mr);
            if(null == cameraId) {
                return;
            }

            Log.i(TAG, "onInfo: cameraId = " + cameraId + ", path = " + recordPaths.get(cameraId) + ", what = " + what + ", extra = " + extra);
            releaseRecorder(cameraId);
            isRecordings.put(cameraId, false);
        }
    };

    private MediaRecorder.OnErrorListener recordErrorListener = new MediaRecorder.OnErrorListener() {
        @Override
        public void onError(MediaRecorder mr, int what, int extra) {
            String cameraId = findCameraId(mr);
            if(null == cameraId) {
                return;
            }

            String path = recordPaths.get(cameraId);
            Log.e(TAG, "onError: cameraId = " + cameraId + ", path = " + path + ", what = " + what + ", extra = " + extra);
            releaseRecorder(cameraId);
            isRecordings.put(cameraId, false);

            if (null != recordCallback) {
                recordCallback.onError(cameraId, path, what, extra);
            }

        }
    };

    /**
     * The result code returned by API.
     */
    public static class ResultCode {
        /**
         * The constant SUCCESS.
         */
        public static final int SUCCESS = 0;

        /**
         * The constant NO_PERMISSION.
         */
        public static final int NO_PERMISSION = -1;

        /**
         * The constant CAMERA_EXCEPTION.
         */
        public static final int CAMERA_EXCEPTION = -2;

        /**
         * The constant NO_CAMERA_DEVICE.
         */
        public static final int NO_CAMERA_DEVICE = -3;

        /**
         * The constant NO_PREVIEW_SURFACE.
         */
        public static final int NO_PREVIEW_SURFACE = -4;

        /**
         * The constant FAILED_WHILE_RECORDING.
         */
        public static final int FAILED_WHILE_RECORDING = -5;

        /**
         * The constant RECORDER_ERROR.
         */
        public static final int RECORDER_ERROR = -6;
    }

    /**
     * The interface camera callback.
     */
    public interface ICameraCallback {
        /**
         * The camera state.
         */
        class State {
            /**
             * The constant CAMERA_CLOSED.
             */
            public static final int CAMERA_CLOSED = 0;

            /**
             * The constant CAMERA_OPENED.
             */
            public static final int CAMERA_OPENED = 1;

            /**
             * The constant CAMERA_DISCONNECTED.
             */
            public static final int CAMERA_DISCONNECTED = 2;
        }

        /**
         * The camera error code.
         */
        class ErrorCode {
            /**
             * The constant CAMERA_IN_USE.
             */
            public static final int CAMERA_IN_USE = CameraDevice.StateCallback.ERROR_CAMERA_IN_USE;

            /**
             * The constant MAX_CAMERAS_IN_USE.
             */
            public static final int MAX_CAMERAS_IN_USE = CameraDevice.StateCallback.ERROR_MAX_CAMERAS_IN_USE;

            /**
             * The constant CAMERA_DISABLED.
             */
            public static final int CAMERA_DISABLED = CameraDevice.StateCallback.ERROR_CAMERA_DISABLED;

            /**
             * The constant CAMERA_DEVICE.
             */
            public static final int CAMERA_DEVICE = CameraDevice.StateCallback.ERROR_CAMERA_DEVICE;

            /**
             * The constant CAMERA_SERVICE.
             */
            public static final int CAMERA_SERVICE = CameraDevice.StateCallback.ERROR_CAMERA_SERVICE;
        }

        /**
         * Called when the camera state changed.
         *
         * @param cameraId the camera id
         * @param state the camera state:
         * <ul>
         * <li>{@link State#CAMERA_CLOSED}
         * <li>{@link State#CAMERA_OPENED}
         * <li>{@link State#CAMERA_DISCONNECTED}
         * <ul/>
         */
        void onState(String cameraId, int state);

        /**
         * Called when the camera error occurred.
         *
         * @param cameraId the camera id
         * @param error the error code:
         * <ul>
         * <li>{@link ErrorCode#CAMERA_IN_USE}
         * <li>{@link ErrorCode#MAX_CAMERAS_IN_USE}
         * <li>{@link ErrorCode#CAMERA_DISABLED}
         * <li>{@link ErrorCode#CAMERA_DEVICE}
         * <li>{@link ErrorCode#CAMERA_SERVICE}
         * <ul/>
         */
        void onError(String cameraId, int error);
    }

    /**
     * The interface capture callback.
     */
    public interface ICaptureCallback {
        /**
         * Called when the capture started.
         *
         * @param cameraId the camera id
         * @param path the captured file full path
         */
        void onStarted(String cameraId, String path);

        /**
         * Called when the capture completed.
         *
         * @param cameraId the camera id
         * @param path the captured file full path
         */
        void onCompleted(String cameraId, String path);

        /**
         * Called when the capture failed.
         *
         * @param cameraId the camera id
         * @param path the captured file full path
         */
        void onFailed(String cameraId, String path);
    }

    /**
     * The interface record callback.
     */
    public interface IRecordCallback {
        /**
         * The error code while recording.
         */
        class ErrorCode {
            /**
             * The constant ERROR_UNKNOWN.
             */
            public static final int UNKNOWN = MediaRecorder.MEDIA_RECORDER_ERROR_UNKNOWN;

            /**
             * The constant ERROR_SERVER_DIED.
             */
            public static final int SERVER_DIED = MediaRecorder.MEDIA_ERROR_SERVER_DIED;
        }

        /**
         * Called when the record completed.
         *
         * @param cameraId the camera id
         * @param path the record file full path
         */
        void onCompleted(String cameraId, String path);

        /**
         * Called when error occurred while recording.
         *
         * @param cameraId the camera id
         * @param path the record file full path
         * @param what the type of error that has occurred:
         * <ul>
         * <li>{@link ErrorCode#UNKNOWN}
         * <li>{@link ErrorCode#SERVER_DIED}
         * <ul/>
         * @param extra an extra code, specific to the error type
         */
        void onError(String cameraId, String path, int what, int extra);
    }

    /**
     * The comparator for sizes by area.
     */
    public static class CompareSizesByArea implements Comparator<Size> {
        @Override
        public int compare(Size lhs, Size rhs) {
            // We cast here to ensure the multiplications won't overflow
            return Long.signum((long) lhs.getWidth() * lhs.getHeight() -
                    (long) rhs.getWidth() * rhs.getHeight());
        }
    }

    /**
     * The tool for coordinate conversation.
     */
    public static class ConvertUtil {
        public static String coordinateToDMS(double coordinate) {
            return Location.convert(coordinate, Location.FORMAT_SECONDS);
        }

        public static double DMSToCoordinate(String stringDMS) {
            if (stringDMS == null) return 0;
            String[] split = stringDMS.split(":", 3);
            return Double.parseDouble(split[0]) + Double.parseDouble(split[1]) / 60 + Double.parseDouble(split[2]) / 3600;
        }

        /**
         * 浮点型经纬度值转成度分秒格式
         *
         * @param coord
         * @return
         */
        public static String decimalToDMS(double coord) {
            String output,degrees,minutes,seconds;

            // gets the modulus the coordinate divided by one (MOD1).
            // in other words gets all the numbers after the decimal point.
            // e.g. mod := -79.982195 % 1 == 0.982195
            //
            // next get the integer part of the coord. On other words the whole
            // number part.
            // e.g. intPart := -79

            double mod = coord % 1;
            int intPart = (int) coord;

            // set degrees to the value of intPart
            // e.g. degrees := "-79"

            degrees = String.valueOf(intPart);

            // next times the MOD1 of degrees by 60 so we can find the integer part
            // for minutes.
            // get the MOD1 of the new coord to find the numbers after the decimal
            // point.
            // e.g. coord := 0.982195 * 60 == 58.9317
            // mod := 58.9317 % 1 == 0.9317
            //
            // next get the value of the integer part of the coord.
            // e.g. intPart := 58

            coord = mod * 60;
            mod = coord % 1;
            intPart = (int) coord;
            if (intPart < 0) {
                // Convert number to positive if it's negative.
                intPart *= -1;
            }

            // set minutes to the value of intPart.
            // e.g. minutes = "58"
            minutes = String.valueOf(intPart);

            // do the same again for minutes
            // e.g. coord := 0.9317 * 60 == 55.902
            // e.g. intPart := 55
            coord = mod * 60;
            intPart = (int) coord;
            if (intPart < 0) {
                // Convert number to positive if it's negative.
                intPart *= -1;
            }

            // set seconds to the value of intPart.
            // e.g. seconds = "55"
            seconds = String.valueOf(intPart);

            // I used this format for android but you can change it
            // to return in whatever format you like
            // e.g. output = "-79/1,58/1,56/1"
            output = degrees + "/1," + minutes + "/1," + seconds + "/1";

            // Standard output of D°M′S″
            // output = degrees + "°" + minutes + "'" + seconds + "\"";
            return output;
        }

        public static double convertRationalLatLonToDouble(String rationalString, String ref) {
            try {
                String [] parts = rationalString.split(",", -1);

                String [] pair;
                pair = parts[0].split("/", -1);
                double degrees = Double.parseDouble(pair[0].trim())
                        / Double.parseDouble(pair[1].trim());

                pair = parts[1].split("/", -1);
                double minutes = Double.parseDouble(pair[0].trim())
                        / Double.parseDouble(pair[1].trim());

                pair = parts[2].split("/", -1);
                double seconds = Double.parseDouble(pair[0].trim())
                        / Double.parseDouble(pair[1].trim());

                double result = degrees + (minutes / 60.0) + (seconds / 3600.0);
                if ((ref.equals("S") || ref.equals("W"))) {
                    return -result;
                } else if (ref.equals("N") || ref.equals("E")) {
                    return result;
                } else {
                    // Not valid
                    throw new IllegalArgumentException();
                }
            } catch (NumberFormatException | ArrayIndexOutOfBoundsException e) {
                // Not valid
                throw new IllegalArgumentException();
            }
        }
    }

    private static class Builder {
        private static final CameraController instance = new CameraController();
    }

    /**
     * Get the singleton of class CameraController.
     */
    public static CameraController getInstance() {
        return Builder.instance;
    }

    private CameraController() {
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

        cameraManager = (CameraManager)context.getSystemService(Context.CAMERA_SERVICE);
        publicDir = Environment.getExternalStorageDirectory().getAbsolutePath();
        privateDir = context.getExternalFilesDir(null).getAbsolutePath();

        File dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES);
        if (!dir.exists()) {
            if (dir.mkdir()) {
                Log.e(TAG, "CameraController: make external storage public pictures directory!");
            } else {
                Log.e(TAG, "CameraController: make external storage public pictures directory failed!");
            }
        }

        dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES);
        if (!dir.exists()) {
            if (dir.mkdir()) {
                Log.e(TAG, "CameraController: make external storage public movies directory!");
            } else {
                Log.e(TAG, "CameraController: make external storage public movies directory failed!");
            }
        }

        dir = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES);
        if (null == dir) {
            Log.e(TAG, "CameraController: no external storage private pictures directory!");
        } else if (!dir.exists()) {
            if (dir.mkdir()) {
                Log.e(TAG, "CameraController: make external storage private pictures directory!");
            } else {
                Log.e(TAG, "CameraController: make external storage private pictures directory failed!");
            }
        }

        dir = context.getExternalFilesDir(Environment.DIRECTORY_MOVIES);
        if (null == dir) {
            Log.e(TAG, "CameraController: no external storage private movies directory!");
        } else if (!dir.exists()) {
            if (dir.mkdir()) {
                Log.e(TAG, "CameraController: make external storage private movies directory!");
            } else {
                Log.e(TAG, "CameraController: make external storage private movies directory failed!");
            }
        }

        String[] cameraIds = getCameraIdList();
        for (String cameraId : cameraIds) {
            try {
                CameraCharacteristics characteristics = cameraManager.getCameraCharacteristics(cameraId);

                Integer sensorOrientation = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION);
                sensorOrientations.put(cameraId, sensorOrientation);
                Log.i(TAG, "CameraController: cameraId = " + cameraId + ", sensor orientation =  " + sensorOrientation);

                StreamConfigurationMap map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
                if (null != map) {
                    Size size = Collections.max(Arrays.asList(map.getOutputSizes(ImageFormat.JPEG)), new CompareSizesByArea());
                    captureSizes.put(cameraId, size);
                    Log.i(TAG, "CameraController: cameraId = " + cameraId + ", capture size = " + size);
                    
                    size = Collections.max(Arrays.asList(map.getOutputSizes(MediaRecorder.class)), new CompareSizesByArea());
                    recordSizes.put(cameraId, size);
                    Log.i(TAG, "CameraController: cameraId = " + cameraId + ", record size = " + size);
                } else {
                    captureSizes.put(cameraId, new Size(1280, 720));
                    recordSizes.put(cameraId, new Size(1280, 720));
                    Log.e(TAG, "CameraController: cameraId = " + cameraId + ", the StreamConfigurationMap is null, use 1280 * 720 as default!");
                }
                
                Integer facing = characteristics.get(CameraCharacteristics.LENS_FACING);
                Log.i(TAG, "CameraController: cameraId = " + cameraId + ", facing =  " + facing);

                Integer level = characteristics.get(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL);
                Log.i(TAG, "CameraController: cameraId = " + cameraId + ", supported level =  " + level);
            } catch (CameraAccessException e) {
                e.printStackTrace();
            }
            
            isCapturePublicDirs.put(cameraId, true);
            isRecordPublicDirs.put(cameraId, true);
            captureRelativeDirs.put(cameraId, Environment.DIRECTORY_PICTURES);
            recordRelativeDirs.put(cameraId, Environment.DIRECTORY_MOVIES);
            audioMutes.put(cameraId, false);
            videoEncodingRates.put(cameraId, 3000000);
            isRecordings.put(cameraId, false);
            surfaces.put(cameraId, new Surface[]{null, null, null});
        }
    }

    /**
     * Get the camera id list.
     *
     * @return the camera id list
     */
    public String[] getCameraIdList() {
        try {
            return cameraManager.getCameraIdList();
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }

        return new String[0];
    }

    /**
     * Get the available preview sizes.
     *
     * @param cameraId the camera id
     * @return the available preview sizes
     */
    public Size[] getAvailablePreviewSizes(String cameraId) {
        try {
            StreamConfigurationMap map = cameraManager.getCameraCharacteristics(cameraId).get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);

            if (null == map) {
                Log.e(TAG, "getAvailablePreviewSizes: no camera available size!");
                return null;
            }

            return map.getOutputSizes(SurfaceTexture.class);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }

        return null;
    }

    /**
     * Get the available capture sizes.
     *
     * @param cameraId the camera id
     * @return the available capture sizes
     */
    public Size[] getAvailableCaptureSizes(String cameraId) {
        try {
            StreamConfigurationMap map = cameraManager.getCameraCharacteristics(cameraId).get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);

            if (null == map) {
                Log.e(TAG, "getAvailableCaptureSizes: no camera available size!");
                return null;
            }

            return map.getOutputSizes(ImageFormat.JPEG);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }

        return null;
    }

    /**
     * Get the available record sizes.
     *
     * @param cameraId the camera id
     * @return the available record sizes
     */
    public Size[] getAvailableRecordSizes(String cameraId) {
        try {
            StreamConfigurationMap map = cameraManager.getCameraCharacteristics(cameraId).get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);

            if (null == map) {
                Log.e(TAG, "getAvailableRecordSizes: no camera available size!");
                return null;
            }

            return map.getOutputSizes(MediaRecorder.class);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }

        return null;
    }

    /**
     * Check if the camera is recording.
     *
     * @param cameraId the camera id
     * @return true while recording or false
     */
    public boolean isRecording(String cameraId) {
        Boolean  isRecording = isRecordings.get(cameraId);
        return null != isRecording && isRecording;
    }

    /**
     * Set the camera callback.
     *
     * @param callback the camera callback
     */
    public void setCameraCallback(ICameraCallback callback) {
        cameraCallback = callback;
    }

    /**
     * Set the preview surface.
     *
     * @param cameraId the camera id
     * @param surface the preview surface
     */
    public void setPreviewSurface(String cameraId, Surface surface) {
        Log.i(TAG, "setPreviewSurface: cameraId = " + cameraId);
        previewSurfaces.put(cameraId, surface);
    }

    /**
     * Set the capture relative storage directory.
     *
     * Relative path of this capture item within the storage device where it
     * is persisted. For example, an item stored at
     * {@code /storage/emulated/0/Pictures/IMG1024.JPG} would have a
     * path of {@code Pictures}.
     *
     * @param cameraId the camera id
     * @param dir the capture relative storage directory
     * @param isPublic Indicate if the directory is under public directory.
     *                 For public, the root directory is
     *                 {@link Environment#getExternalStorageDirectory()},
     *                 and for private, it is {@link Context#getExternalFilesDir(String)}.
     * @return true if successful or false
     */
    public boolean setCaptureRelativeDir(String cameraId, String dir, boolean isPublic) {
        Log.i(TAG, "setCaptureRelativeDir: cameraId = " + cameraId + ", dir = " + dir + ", isPublic = " + isPublic);

        if (null == dir) {
            return false;
        }

        File path = new File(isPublic ? publicDir : privateDir, dir);

        if (!path.exists()) {
            if (path.mkdirs()) {
                Log.e(TAG, "setCaptureRelativeDir: make directory " + path.getAbsolutePath());
            } else {
                Log.e(TAG, "setCaptureRelativeDir: make directory " + path.getAbsolutePath() + " failed!");

                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q
                        || (Build.VERSION.SDK_INT == Build.VERSION_CODES.Q && Environment.isExternalStorageLegacy())
                        || !isPublic) {
                    return false;
                }
            }
        }

        isCapturePublicDirs.put(cameraId, isPublic);
        captureRelativeDirs.put(cameraId, dir);

        return true;
    }

    /**
     * Set the capture size.
     *
     * @param cameraId the camera id
     * @param width the capture width
     * @param height the capture height
     */
    public void setCaptureSize(String cameraId, int width, int height) {
        Log.i(TAG, "setCaptureSize: cameraId = " + cameraId + ", width = " + width + ", height = " + height);

        if (isRecording(cameraId)) {
            Log.e(TAG, "setCaptureSize: failed while recording!");
        }

        ImageReader imageReader = ImageReader.newInstance(width, height, ImageFormat.JPEG, 1);
        imageReader.setOnImageAvailableListener(imageAvailableListener, handler);
        imageReaders.put(cameraId, imageReader);
        captureSizes.put(cameraId, new Size(width, height));
    }

    /**
     * Set the capture callback.
     *
     * @param callback the capture callback
     */
    public void setCaptureCallback(ICaptureCallback callback) {
        captureCallback = callback;
    }

    /**
     * Set the record relative storage directory.
     *
     * Relative path of this record item within the storage device where it
     * is persisted. For example, an item stored at
     * {@code /storage/emulated/0/Movies/VID1024.MP4} would have a
     * path of {@code Movies}.
     *
     * @param cameraId the camera id
     * @param dir the record relative storage directory
     * @param isPublic Indicate if the directory is under public directory.
     *                 For public, the root directory is
     *                 {@link Environment#getExternalStorageDirectory()},
     *                 and for private, it is {@link Context#getExternalFilesDir(String)}.
     * @return true if successful or false
     */
    public boolean setRecordRelativeDir(String cameraId, String dir, boolean isPublic) {
        Log.i(TAG, "setRecordRelativeDir: cameraId = " + cameraId + ", dir = " + dir + ", isPublic = " + isPublic);

        if (null == dir) {
            return false;
        }

        File path = new File(isPublic ? publicDir : privateDir, dir);

        if (!path.exists()) {
            if (path.mkdirs()) {
                Log.e(TAG, "setRecordRelativeDir: make directory " + path.getAbsolutePath());
            } else {
                Log.e(TAG, "setRecordRelativeDir: make directory " + path.getAbsolutePath() + " failed!");

                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q
                        || (Build.VERSION.SDK_INT == Build.VERSION_CODES.Q && Environment.isExternalStorageLegacy())
                        || !isPublic) {
                    return false;
                }
            }
        }

        isRecordPublicDirs.put(cameraId, isPublic);
        recordRelativeDirs.put(cameraId, dir);

        return true;
    }

    /**
     * Set the record video size.
     *
     * @param cameraId the camera id
     * @param width the record video width
     * @param height the record video height
     */
    public void setRecordSize(String cameraId, int width, int height) {
        Log.i(TAG, "setRecordSize: cameraId = " + cameraId + ", width = " + width + ", height = " + height);
        recordSizes.put(cameraId, new Size(width, height));
    }

    /**
     * Set the audio mute.
     *
     * @param cameraId the camera id
     * @param isMute if the audio is mute
     */
    public void setAudioMute(String cameraId, boolean isMute) {
        Log.i(TAG, "setAudioMute: cameraId = " + cameraId + ", isMute = " + isMute);
        audioMutes.put(cameraId, isMute);
    }

    /**
     * Set the video encoding bit rate.
     *
     * @param cameraId the camera id
     * @param bps the video encoding bit rate in bps
     */
    public void setVideoEncodingRate(String cameraId, int bps) {
        Log.i(TAG, "setVideoEncodingRate: cameraId = " + cameraId + ", bps = " + bps);
        videoEncodingRates.put(cameraId, bps);
    }

    /**
     * Set the record callback.
     *
     * @param callback the record callback
     */
    public void setRecordCallback(IRecordCallback callback) {
        recordCallback = callback;
    }

    /**
     * Open the camera by id.
     *
     * @param cameraId the camera id
     * @return {@link ICamera.ResultCode}
     */
    public int open(final String cameraId) {
        Log.i(TAG, "open: cameraId = " + cameraId);

        if (PackageManager.PERMISSION_GRANTED != ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA)) {
            Log.e(TAG, "open: no camera permission!");
            return ResultCode.NO_PERMISSION;
        }

        if (PackageManager.PERMISSION_GRANTED != ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)) {
            Log.e(TAG, "open: no record audio permission!");
            return ResultCode.NO_PERMISSION;
        }

        if (PackageManager.PERMISSION_GRANTED != ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
            Log.e(TAG, "open: no write external storage permission!");
            return ResultCode.NO_PERMISSION;
        }

        if (PackageManager.PERMISSION_GRANTED != ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE)) {
            Log.e(TAG, "open: no read external storage permission!");
            return ResultCode.NO_PERMISSION;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (PackageManager.PERMISSION_GRANTED != ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_MEDIA_LOCATION)) {
                Log.e(TAG, "open: no access media location permission!");
                return ResultCode.NO_PERMISSION;
            }
        }
        
        try {
            handlerThread = new HandlerThread("CameraHandlerThread");
            handlerThread.start();
            handler = new Handler(handlerThread.getLooper());

            Size size = captureSizes.get(cameraId);
            ImageReader imageReader = ImageReader.newInstance(size.getWidth(), size.getHeight(), ImageFormat.JPEG, 1);
            imageReader.setOnImageAvailableListener(imageAvailableListener, handler);
            imageReaders.put(cameraId, imageReader);

            cameraManager.openCamera(cameraId, new CameraDevice.StateCallback() {
                @Override
                public void onOpened(@NonNull CameraDevice camera) {
                    String cameraId = camera.getId();

                    Log.i(TAG, "onOpened: cameraId = " + cameraId);
                    cameraDevices.put(cameraId, camera);

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
                    //close(cameraId);

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

    /**
     * Close the camera by id.
     *
     * @param cameraId the camera id
     * @return {@link ICamera.ResultCode}
     */
    public int close(String cameraId) {
        Log.i(TAG, "close: cameraId = " + cameraId);
        
        // stop record
        stopRecord(cameraId);

        // close session
        CameraCaptureSession cameraCaptureSession = cameraCaptureSessions.get(cameraId);
        if (null != cameraCaptureSession) {
            try {
                cameraCaptureSession.stopRepeating();
                cameraCaptureSession.abortCaptures();
            } catch (CameraAccessException | IllegalStateException e) {
                e.printStackTrace();
            }

            try {
                cameraCaptureSession.close();
            } catch (Exception e) {
                e.printStackTrace();
            }

            cameraCaptureSessions.remove(cameraId);
        }

        // remove surfaces
        CaptureRequest.Builder captureBuilder =  captureBuilders.get(cameraId);
        if (null != captureBuilder) {
            Surface[] ss = surfaces.get(cameraId);

            if (null != ss) {
                for (Surface s : ss) {
                    captureBuilder.removeTarget(ss[SURFACE_CAPTURE]);
                    captureBuilder.removeTarget(ss[SURFACE_PREVIEW]);
                    captureBuilder.removeTarget(ss[SURFACE_RECORD]);
                }
            }

            captureBuilders.remove(cameraId);
        }

        // close device
        CameraDevice cameraDevice = cameraDevices.get(cameraId);
        if (null != cameraDevice) {
            cameraDevice.close();
            cameraDevices.remove(cameraId);
        }

        // close image reader
        ImageReader imageReader = imageReaders.get(cameraId);
        if (null != imageReader) {
            imageReader.close();
            imageReaders.remove(cameraId);
        }

        // remove callbacks and messages
        if (null != handler) {
            handler.removeCallbacksAndMessages(null);
            handler = null;
        }

        // terminate thread
        if (null != handlerThread) {
            handlerThread.quitSafely();

            try {
                handlerThread.join();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

            handlerThread = null;
            handler = null;
        }

        return ResultCode.SUCCESS;
    }

    /**
     * Start preview.
     *
     * @param cameraId the camera id
     * @return {@link ICamera.ResultCode}
     */
    public int startPreview(String cameraId) {
        Log.i(TAG, "startPreview: cameraId = " + cameraId);

        CameraDevice cameraDevice = cameraDevices.get(cameraId);
        if (null == cameraDevice) {
            return ResultCode.NO_CAMERA_DEVICE;
        }

        Surface previewSurface = previewSurfaces.get(cameraId);
        if (null == previewSurface) {
            return ResultCode.NO_PREVIEW_SURFACE;
        }

        if (isRecording(cameraId)) {
            return ResultCode.FAILED_WHILE_RECORDING;
        }

        boolean isNeedCreateSession = false;
        Surface captureSurface = imageReaders.get(cameraId).getSurface();
        List<Surface> surfaceList= new ArrayList<>();

        if (null == cameraCaptureSessions.get(cameraId)
                || surfaces.get(cameraId)[SURFACE_PREVIEW] != previewSurface
                || surfaces.get(cameraId)[SURFACE_CAPTURE] != captureSurface) {
            isNeedCreateSession = true;
        }

        surfaces.get(cameraId)[SURFACE_PREVIEW] = previewSurface;
        surfaces.get(cameraId)[SURFACE_CAPTURE] = captureSurface;
        surfaceList.add(previewSurface);
        surfaceList.add(captureSurface);

        if (isNeedCreateSession) {
            try {
                cameraDevice.createCaptureSession(surfaceList, new CameraCaptureSession.StateCallback() {
                    @Override
                    public void onConfigured(@NonNull CameraCaptureSession session) {
                        String cameraId = session.getDevice().getId();

                        Log.i(TAG, "onConfigured: cameraId = " + cameraId);
                        cameraCaptureSessions.put(cameraId, session);
                        doPreview(cameraId);
                    }

                    @Override
                    public void onConfigureFailed(@NonNull CameraCaptureSession session) {
                        Log.e(TAG, "onConfigureFailed: cameraId = " + session.getDevice().getId());
                    }
                }, handler);
            } catch (CameraAccessException e) {
                e.printStackTrace();
                return ResultCode.CAMERA_EXCEPTION;
            }
        } else {
            return doPreview(cameraId);
        }

        return ResultCode.SUCCESS;
    }

    /**
     * Stop preview.
     *
     * @param cameraId the camera id
     * @return {@link ICamera.ResultCode}
     */
    public int stopPreview(String cameraId) {
        Log.i(TAG, "stopPreview: cameraId = " + cameraId);

        if (isRecording(cameraId)) {
            return ResultCode.FAILED_WHILE_RECORDING;
        }

        CameraCaptureSession cameraCaptureSession = cameraCaptureSessions.get(cameraId);

        if (null != cameraCaptureSession) {
            try {
                cameraCaptureSession.stopRepeating();
            } catch (CameraAccessException | IllegalStateException e) {
                e.printStackTrace();
            }
        }

        return ResultCode.SUCCESS;
    }

    /**
     * Capture a picture.
     *
     * @param cameraId the camera id
     * @return {@link ICamera.ResultCode}
     */
    public int capture(String cameraId) {
        return capture(cameraId, null, null);
    }

    /**
     * Capture a picture with file.
     *
     * @param cameraId the camera id
     * @param name the capture file name
     * @return {@link ICamera.ResultCode}
     */
    public int capture(String cameraId, String name) {
        return capture(cameraId, name, null);
    }

    /**
     * Capture a picture with location.
     *
     * @param cameraId the camera id
     * @param location the location
     * @return {@link ICamera.ResultCode}
     */
    public int capture(String cameraId, Location location) {
        return capture(cameraId, null, location);
    }

    /**
     * Capture a picture with file name and location.
     *
     * @param cameraId the camera id
     * @param name the capture file name
     * @param location the location
     * @return {@link ICamera.ResultCode}
     */
    public int capture(String cameraId, final String name, final Location location) {
        Log.i(TAG, "capture: cameraId = " + cameraId + ", name = " + name + ", location = " + location);

        CameraDevice cameraDevice = cameraDevices.get(cameraId);
        if (null == cameraDevice) {
            return ResultCode.NO_CAMERA_DEVICE;
        }

        boolean isNeedCreateSession = false;
        Surface previewSurface = previewSurfaces.get(cameraId);
        Surface captureSurface = imageReaders.get(cameraId).getSurface();
        List<Surface> surfaceList= new ArrayList<>();

        if (null == cameraCaptureSessions.get(cameraId)
                || surfaces.get(cameraId)[SURFACE_PREVIEW] != previewSurface
                || surfaces.get(cameraId)[SURFACE_CAPTURE] != captureSurface) {
            isNeedCreateSession = true;
        }

        surfaces.get(cameraId)[SURFACE_PREVIEW] = previewSurface;
        surfaces.get(cameraId)[SURFACE_CAPTURE] = captureSurface;
        surfaceList.add(captureSurface);

        if (null != previewSurface) {
            surfaceList.add(previewSurface);
        }

        if (isNeedCreateSession) {
            try {
                cameraDevice.createCaptureSession(surfaceList, new CameraCaptureSession.StateCallback() {
                    @Override
                    public void onConfigured(@NonNull CameraCaptureSession session) {
                        String cameraId = session.getDevice().getId();

                        Log.i(TAG, "onConfigured: cameraId = " + cameraId);
                        cameraCaptureSessions.put(cameraId, session);
                        doCapture(cameraId, name, location);
                    }

                    @Override
                    public void onConfigureFailed(@NonNull CameraCaptureSession session) {
                        Log.e(TAG, "onConfigureFailed: cameraId = " + session.getDevice().getId());
                    }
                }, handler);
            } catch (CameraAccessException e) {
                e.printStackTrace();
                return ResultCode.CAMERA_EXCEPTION;
            }
        } else {
            return doCapture(cameraId, name, location);
        }

        return ResultCode.SUCCESS;
    }

    /**
     * Start record.
     *
     * @param cameraId the camera id
     * @return {@link ICamera.ResultCode}
     */
    public int startRecord(String cameraId) {
        return startRecord(cameraId, null, 0);
    }

    /**
     * Start record with file name.
     *
     * @param cameraId the camera id
     * @param name the record file name
     * @return {@link ICamera.ResultCode}
     */
    public int startRecord(String cameraId, String name) {
        return startRecord(cameraId, name, 0);
    }

    /**
     * Start record with max duration.
     *
     * @param cameraId the camera id
     * @param duration the record max duration in ms (if zero or negative, disables the duration limit)
     * @return {@link ICamera.ResultCode}
     */
    public int startRecord(String cameraId, int duration) {
        return startRecord(cameraId, null, duration);
    }

    /**
     * Start record with file name and max duration.
     *
     * @param cameraId the camera id
     * @param name the record file name
     * @param duration the record max duration in ms (if zero or negative, disables the duration limit)
     * @return {@link ICamera.ResultCode}
     */
    public int startRecord(String cameraId, String name, int duration) {
        Log.i(TAG, "startRecord: cameraId = " + cameraId + ", name = " + name + ", duration = " + duration);

        final CameraDevice cameraDevice = cameraDevices.get(cameraId);
        if (null == cameraDevice) {
            return ResultCode.NO_CAMERA_DEVICE;
        }

        if (isRecording(cameraId)) {
            return ResultCode.FAILED_WHILE_RECORDING;
        }

        stopPreview(cameraId);
        isRecordings.put(cameraId, true);

        if (!prepareRecorder(cameraId, name, duration)) {
            isRecordings.put(cameraId, false);
            return ResultCode.RECORDER_ERROR;
        }

        final MediaRecorder mediaRecorder = mediaRecorders.get(cameraId);
        final Surface previewSurface = previewSurfaces.get(cameraId);
        Surface captureSurface = imageReaders.get(cameraId).getSurface();
        final Surface recordSurface = mediaRecorder.getSurface();
        List<Surface> surfaceList= new ArrayList<>();

        surfaces.get(cameraId)[SURFACE_PREVIEW] = previewSurface;
        surfaces.get(cameraId)[SURFACE_CAPTURE] = captureSurface;
        surfaces.get(cameraId)[SURFACE_RECORD] = recordSurface;
        surfaceList.add(captureSurface);
        surfaceList.add(recordSurface);

        if (null != previewSurface) {
            surfaceList.add(previewSurface);
        }

        try {
            cameraDevice.createCaptureSession(surfaceList, new CameraCaptureSession.StateCallback() {
                @Override
                public void onConfigured(@NonNull CameraCaptureSession session) {
                    String cameraId = session.getDevice().getId();

                    Log.i(TAG, "onConfigured: cameraId = " + cameraId);
                    cameraCaptureSessions.put(cameraId, session);

                    try {
                        CaptureRequest.Builder captureBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_RECORD);

                        if (null != previewSurface) {
                            captureBuilder.addTarget(previewSurface);
                        }

                        captureBuilder.addTarget(recordSurface);
                        captureBuilder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO);
                        captureBuilders.put(cameraId, captureBuilder);
                        session.setRepeatingRequest(captureBuilder.build(), recordSessionCallback, handler);
                        mediaRecorder.start();
                    } catch (CameraAccessException e) {
                        e.printStackTrace();
                    }
                }

                @Override
                public void onConfigureFailed(@NonNull CameraCaptureSession session) {
                    Log.e(TAG, "onConfigureFailed: cameraId = " + session.getDevice().getId());
                }
            }, handler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
            return ResultCode.CAMERA_EXCEPTION;
        }

        return ResultCode.SUCCESS;
    }

    /**
     * Stop record.
     *
     * @param cameraId the camera id
     * @return {@link ICamera.ResultCode}
     */
    public int stopRecord(String cameraId) {
        Log.i(TAG, "stopRecord: cameraId = " + cameraId);

        MediaRecorder mediaRecorder = mediaRecorders.get(cameraId);

        if (null != mediaRecorder) {
            mediaRecorder.stop();
        }

        releaseRecorder(cameraId);
        isRecordings.put(cameraId, false);

        return ResultCode.SUCCESS;
    }

    private int doPreview(String cameraId) {
        try {
            CaptureRequest.Builder captureBuilder =  cameraDevices.get(cameraId).createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);

            captureBuilder.addTarget(surfaces.get(cameraId)[SURFACE_PREVIEW]);
            captureBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
            captureBuilder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH);
            captureBuilders.put(cameraId, captureBuilder);
            cameraCaptureSessions.get(cameraId).setRepeatingRequest(captureBuilder.build(), null, handler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
            return ResultCode.CAMERA_EXCEPTION;
        }

        return ResultCode.SUCCESS;
    }

    private int doCapture(String cameraId, String name, Location location) {
        try {
            CaptureRequest.Builder captureBuilder = cameraDevices.get(cameraId).createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
            WindowManager windowManager = ((WindowManager) context.getSystemService(Context.WINDOW_SERVICE));

            if (null != windowManager) {
                captureBuilder.set(CaptureRequest.JPEG_ORIENTATION, getOrientation(sensorOrientations.get(cameraId), windowManager.getDefaultDisplay().getRotation()));
            }

            capturePaths.put(cameraId, (isCapturePublicDirs.get(cameraId) ? publicDir : privateDir)
                    + File.separator + captureRelativeDirs.get(cameraId) + File.separator
                    + (null != name && !name.isEmpty() ? name : nameDateFormat.format(new Date()) + ".jpg"));
            captureLocations.put(cameraId, location);
            captureBuilder.addTarget(surfaces.get(cameraId)[SURFACE_CAPTURE]);
            captureBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
            captureBuilder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH);
            captureBuilder.set(CaptureRequest.JPEG_GPS_LOCATION, location);
            captureBuilders.put(cameraId, captureBuilder);
            cameraCaptureSessions.get(cameraId).capture(captureBuilder.build(), captureSessionCallback, handler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
            return ResultCode.CAMERA_EXCEPTION;
        }

        return ResultCode.SUCCESS;
    }

    private boolean prepareRecorder(String cameraId, String name, int duration) {
        Log.i(TAG, "prepareRecorder: cameraId = " + cameraId + ", name = " + name + ", duration = " + duration);

        long time = System.currentTimeMillis();
        String displayName = (null != name && !name.isEmpty() ? name : nameDateFormat.format(new Date(time)) + ".mp4");
        String path = (isRecordPublicDirs.get(cameraId) ? publicDir : privateDir)
                + File.separator + recordRelativeDirs.get(cameraId)
                + File.separator + displayName;
        Size size = recordSizes.get(cameraId);
        Integer sensorOrientation = sensorOrientations.get(cameraId);
        WindowManager windowManager = ((WindowManager) context.getSystemService(Context.WINDOW_SERVICE));
        MediaRecorder mediaRecorder = new MediaRecorder();

        Log.i(TAG, "prepareRecorder: path = " + path);

        if ((Build.VERSION.SDK_INT > Build.VERSION_CODES.Q
                || (Build.VERSION.SDK_INT == Build.VERSION_CODES.Q && !Environment.isExternalStorageLegacy()))
                && isRecordPublicDirs.get(cameraId)) {
            ContentResolver resolver = context.getContentResolver();
            ContentValues values = new ContentValues();
            Uri uri = null;

            values.put(MediaStore.Video.Media.DATA, path);
            values.put(MediaStore.Video.Media.DISPLAY_NAME, displayName);
            values.put(MediaStore.Video.Media.TITLE,  displayName.substring(0, displayName.lastIndexOf(".")));
            values.put(MediaStore.Video.Media.WIDTH, size.getWidth());
            values.put(MediaStore.Video.Media.HEIGHT, size.getHeight());
            values.put(MediaStore.Video.Media.DATE_TAKEN, time);
            values.put(MediaStore.Video.Media.RELATIVE_PATH, recordRelativeDirs.get(cameraId));
            uri = resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values);
            Log.i(TAG, "prepareRecorder: insert the video to database, uri = " + uri);

            if (null == uri) {
                return false;
            }

            try {
                ParcelFileDescriptor pfd = resolver.openFileDescriptor(uri, "w");

                if (null == pfd) {
                    return false;
                }

                if (!audioMutes.get(cameraId)) {
                    mediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
                }

                mediaRecorder.setVideoSource(MediaRecorder.VideoSource.SURFACE);
                mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
                mediaRecorder.setOutputFile(pfd.getFileDescriptor());
                mediaRecorder.setVideoEncodingBitRate(videoEncodingRates.get(cameraId));
                mediaRecorder.setVideoFrameRate(30);
                mediaRecorder.setVideoSize(size.getWidth(), size.getHeight());
                mediaRecorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264);

                if (!audioMutes.get(cameraId)) {
                    mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
                }

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

                mediaRecorder.prepare();
                pfd.close();
                recorddUri.put(cameraId, uri);
            } catch (IOException e) {
                e.printStackTrace();
                return false;
            }
        } else {
            if (!audioMutes.get(cameraId)) {
                mediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
            }

            mediaRecorder.setVideoSource(MediaRecorder.VideoSource.SURFACE);
            mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
            mediaRecorder.setOutputFile(path);
            mediaRecorder.setVideoEncodingBitRate(videoEncodingRates.get(cameraId));
            mediaRecorder.setVideoFrameRate(30);
            mediaRecorder.setVideoSize(size.getWidth(), size.getHeight());
            mediaRecorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264);

            if (!audioMutes.get(cameraId)) {
                mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
            }

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
                return false;
            }

            recorddUri.put(cameraId, null);
        }

        recordTimes.put(cameraId, time);
        recordPaths.put(cameraId, path);
        mediaRecorders.put(cameraId, mediaRecorder);

        return true;
    }

    private void releaseRecorder(String cameraId) {
        Log.i(TAG, "releaseRecorder: cameraId = " + cameraId);

        MediaRecorder mediaRecorder = mediaRecorders.get(cameraId);

        if (null != mediaRecorder) {
            synchronized (this) {
                if (null != (mediaRecorder = mediaRecorders.get(cameraId))) {
                    mediaRecorder.reset();
                    mediaRecorder.release();
                    mediaRecorders.remove(cameraId);
                }
            }
        }
    }

    private String findCameraId(CameraCaptureSession session) {
        for (Map.Entry<String, CameraCaptureSession> entry : cameraCaptureSessions.entrySet()) {
            if(session.equals(entry.getValue())){
                return entry.getKey();
            }
        }
        
        return null;
    }

    private String findCameraId(ImageReader reader) {
        for (Map.Entry<String, ImageReader> entry : imageReaders.entrySet()) {
            if(reader.equals(entry.getValue())){
                return entry.getKey();
            }
        }

        return null;
    }

    private String findCameraId(MediaRecorder mediaRecorder) {
        for (Map.Entry<String, MediaRecorder> entry : mediaRecorders.entrySet()) {
            if(mediaRecorder.equals(entry.getValue())){
                return entry.getKey();
            }
        }

        return null;
    }

    private int getOrientation(Integer sensorOrientation, int rotation) {
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

    private void writeImageLocation(ExifInterface exif, Location location) {
        if (null == exif || null == location) {
            return;
        }

        double[] latLong = exif.getLatLong();

        if (null == latLong) {
            double latitude = location.getLatitude();
            double longitude = location.getLongitude();

            Log.e(TAG, "writeImageLocation: save the coordinate to image!");
            exif.setAttribute(ExifInterface.TAG_GPS_LATITUDE, ConvertUtil.decimalToDMS(latitude));
            exif.setAttribute(ExifInterface.TAG_GPS_LATITUDE_REF, latitude > 0 ? "N" : "S");
            exif.setAttribute(ExifInterface.TAG_GPS_LONGITUDE, ConvertUtil.decimalToDMS(longitude));
            exif.setAttribute(ExifInterface.TAG_GPS_LONGITUDE_REF, longitude > 0 ? "E" : "W");

            try {
                exif.saveAttributes();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        if (location.hasAltitude() && -1 == exif.getAltitude(-1)) {
            double altitude = location.getAltitude();

            Log.e(TAG, "writeImageLocation: save the altitude to image!");
            exif.setAttribute(ExifInterface.TAG_GPS_ALTITUDE, Double.toString(Math.abs(altitude)) + "/1");
            exif.setAttribute(ExifInterface.TAG_GPS_ALTITUDE_REF, altitude >= 0 ? "0" : "1");

            try {
                exif.saveAttributes();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}
