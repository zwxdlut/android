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
import android.os.storage.StorageManager;
import android.os.storage.StorageVolume;
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

import com.camera.util.CompareSizesByArea;
import com.camera.util.CoordinateConvert;
import com.camera.util.StorageUtil;

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
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * The CameraNative class provides control and operation of the native cameras.
 */
public class CameraNative implements ICamera {
    private static final String TAG = CameraNative.class.getSimpleName();
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

    private Uri REMOVABLE_IMAGE_CONTENT_URI = MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
    private Uri REMOVABLE_VIDEO_CONTENT_URI = MediaStore.Video.Media.EXTERNAL_CONTENT_URI;
    private String publicDir = null;
    private String privateDir = null;
    private String removablePublicDir = null;
    private String removablePrivateDir = null;
    private Context context = null;
    private CameraManager cameraManager = null;
    private Map<String, Integer> sensorOrientations = new ArrayMap<>();
    private Map<String, Boolean> isCapturePublicDirs = new ArrayMap<>();
    private Map<String, Boolean> isRecordPublicDirs = new ArrayMap<>();
    private Map<String, Boolean> isCaptureRemovableDirs = new ArrayMap<>();
    private Map<String, Boolean> isRecordRemovableDirs = new ArrayMap<>();
    private Map<String, String> captureRootDirs = new ArrayMap<>();
    private Map<String, String> recordRootDirs = new ArrayMap<>();
    private Map<String, String> captureRelativeDirs = new ArrayMap<>();
    private Map<String, String> recordRelativeDirs = new ArrayMap<>();
    private Map<String, String> capturePaths = new ArrayMap<>();
    private Map<String, String> recordPaths = new ArrayMap<>();
    private Map<String, Uri> imageContentUris = new ArrayMap<>();
    private Map<String, Uri> videoContentUris = new ArrayMap<>();
    private Map<String, Uri> recordUris = new ArrayMap<>();
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
                            || (Build.VERSION.SDK_INT == Build.VERSION_CODES.Q && (!Environment.isExternalStorageLegacy() || isRecordRemovableDirs.get(cameraId))))
                            && isRecordPublicDirs.get(cameraId)) {
                        ContentResolver resolver = context.getContentResolver();
                        ContentValues values = new ContentValues();
                        Uri uri = recordUris.get(cameraId);

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
                        uri = resolver.insert(videoContentUris.get(cameraId), values);
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
                        uri = resolver.insert(imageContentUris.get(cameraId), values);
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

    private static class Builder {
        private static final CameraNative instance = new CameraNative();
    }

    /**
     * Get the singleton of class CameraNative.
     */
    public static CameraNative getInstance() {
        return CameraNative.Builder.instance;
    }

    private CameraNative() {
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

        // initialize the storage directories
        cameraManager = (CameraManager)context.getSystemService(Context.CAMERA_SERVICE);
        publicDir = Environment.getExternalStorageDirectory().getAbsolutePath();
        privateDir = context.getExternalFilesDir(null).getAbsolutePath();

        File dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES);
        if (null == dir) {
            Log.e(TAG, "CameraNative: no external storage public pictures directory!");
        } else if(!dir.exists()) {
            if (dir.mkdirs()) {
                Log.e(TAG, "CameraNative: make external storage public pictures directory " + dir.getAbsolutePath());
            } else {
                Log.e(TAG, "CameraNative: make external storage public pictures directory " + dir.getAbsolutePath() + " failed!");
            }
        }

        dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES);
        if (null == dir) {
            Log.e(TAG, "CameraNative: no external storage public movies directory!");
        } else if (!dir.exists()) {
            if (dir.mkdirs()) {
                Log.e(TAG, "CameraNative: make external storage public movies directory " + dir.getAbsolutePath());
            } else {
                Log.e(TAG, "CameraNative: make external storage public movies directory " + dir.getAbsolutePath() + " failed!");
            }
        }
        
        dir = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES);
        if (null == dir) {
            Log.e(TAG, "CameraNative: no external storage private pictures directory!");
        } else if (!dir.exists()) {
            if (dir.mkdirs()) {
                Log.e(TAG, "CameraNative: make external storage private pictures directory " + dir.getAbsolutePath());
            } else {
                Log.e(TAG, "CameraNative: make external storage private pictures directory " + dir.getAbsolutePath() + " failed!");
            }
        }

        dir = context.getExternalFilesDir(Environment.DIRECTORY_MOVIES);
        if (null == dir) {
            Log.e(TAG, "CameraNative: no external storage private movies directory!");
        } else if (!dir.exists()) {
            if (dir.mkdirs()) {
                Log.e(TAG, "CameraNative: make external storage private movies directory " + dir.getAbsolutePath());
            } else {
                Log.e(TAG, "CameraNative: make external storage private movies directory " + dir.getAbsolutePath() + " failed!");
            }
        }

        String[] dirs = StorageUtil.getRemovableStorageDirs(context);
        if (null != dirs && 0 < dirs.length) {
            Log.i(TAG, "CameraNative: external removable storage directory " + Arrays.toString(dirs));
            removablePublicDir = dirs[0];

            dir = new File(removablePublicDir, Environment.DIRECTORY_PICTURES);
            if (!dir.exists()) {
                if (dir.mkdirs()) {
                    Log.e(TAG, "CameraNative: make external removable storage public picture directory " + dir.getAbsolutePath());
                } else {
                    Log.e(TAG, "CameraNative: make external removable storage public picture directory " + dir.getAbsolutePath() + " failed!");
                }
            }

            dir = new File(removablePublicDir, Environment.DIRECTORY_MOVIES);
            if (!dir.exists()) {
                if (dir.mkdirs()) {
                    Log.e(TAG, "CameraNative: make external removable storage public movies directory " + dir.getAbsolutePath());
                } else {
                    Log.e(TAG, "CameraNative: make external removable storage public movies directory " + dir.getAbsolutePath() + " failed!");
                }
            }
        } else {
            Log.e(TAG, "CameraNative: no external removable storage directory!");
            removablePublicDir = publicDir;
        }

        File[] files = context.getExternalFilesDirs(null);
        if (null != files && 0 < files.length) {
            Log.i(TAG, "CameraNative: external storage private directory " + Arrays.toString(files));

            StorageManager sm = context.getSystemService(StorageManager.class);
            StorageVolume volume = null;

            for (File file : files) {
                volume = sm.getStorageVolume(file);
                if (null != volume && volume.isRemovable()) {
                    removablePrivateDir = file.getAbsolutePath();
                    break;
                }
            }

            if (null != removablePrivateDir) {
                Log.i(TAG, "CameraNative: external removable storage private directory " + removablePrivateDir);

                dir = new File(removablePrivateDir, Environment.DIRECTORY_PICTURES);
                if (!dir.exists()) {
                    if (dir.mkdirs()) {
                        Log.e(TAG, "CameraNative: make external removable storage private picture directory " + dir.getAbsolutePath());
                    } else {
                        Log.e(TAG, "CameraNative: make external removable storage private picture directory " + dir.getAbsolutePath() + " failed!");
                    }
                }

                dir = new File(removablePrivateDir, Environment.DIRECTORY_MOVIES);
                if (!dir.exists()) {
                    if (dir.mkdirs()) {
                        Log.e(TAG, "CameraNative: make external removable storage private movies directory " + dir.getAbsolutePath());
                    } else {
                        Log.e(TAG, "CameraNative: make external removable storage private movies directory " + dir.getAbsolutePath() + " failed!");
                    }
                }
            } else {
                Log.e(TAG, "CameraNative: no external removable storage private directory!");
                removablePrivateDir = privateDir;
            }
        } else {
            Log.e(TAG, "CameraNative: no external storage private directory!");
        }

        // initialize the removable media content uris
        StorageManager sm = context.getSystemService(StorageManager.class);
        Set<String> volumeNames = MediaStore.getExternalVolumeNames(context);
        for (String volumeName : volumeNames) {
            Uri uri = MediaStore.Images.Media.getContentUri(volumeName);
            StorageVolume volume = sm.getStorageVolume(uri);
            if (volume.isRemovable()) {
                REMOVABLE_IMAGE_CONTENT_URI = uri;
            }

            uri = MediaStore.Video.Media.getContentUri(volumeName);
            volume = sm.getStorageVolume(uri);
            if (volume.isRemovable()) {
                REMOVABLE_VIDEO_CONTENT_URI = uri;
            }
        }

        String[] cameraIds = getCameraIdList();
        for (String cameraId : cameraIds) {
            try {
                CameraCharacteristics characteristics = cameraManager.getCameraCharacteristics(cameraId);

                Integer sensorOrientation = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION);
                sensorOrientations.put(cameraId, sensorOrientation);
                Log.i(TAG, "CameraNative: cameraId = " + cameraId + ", sensor orientation =  " + sensorOrientation);

                StreamConfigurationMap map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
                if (null != map) {
                    Size size = Collections.max(Arrays.asList(map.getOutputSizes(ImageFormat.JPEG)), new CompareSizesByArea());
                    captureSizes.put(cameraId, size);
                    Log.i(TAG, "CameraNative: cameraId = " + cameraId + ", capture size = " + size);
                    
                    size = Collections.max(Arrays.asList(map.getOutputSizes(MediaRecorder.class)), new CompareSizesByArea());
                    recordSizes.put(cameraId, size);
                    Log.i(TAG, "CameraNative: cameraId = " + cameraId + ", record size = " + size);
                } else {
                    captureSizes.put(cameraId, new Size(1280, 720));
                    recordSizes.put(cameraId, new Size(1280, 720));
                    Log.e(TAG, "CameraNative: cameraId = " + cameraId + ", the StreamConfigurationMap is null, use 1280 * 720 as default!");
                }
                
                Integer facing = characteristics.get(CameraCharacteristics.LENS_FACING);
                Log.i(TAG, "CameraNative: cameraId = " + cameraId + ", facing =  " + facing);

                Integer level = characteristics.get(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL);
                Log.i(TAG, "CameraNative: cameraId = " + cameraId + ", supported level =  " + level);
            } catch (CameraAccessException e) {
                e.printStackTrace();
            }
            
            isCapturePublicDirs.put(cameraId, true);
            isRecordPublicDirs.put(cameraId, true);
            isCaptureRemovableDirs.put(cameraId, false);
            isRecordRemovableDirs.put(cameraId, false);
            captureRootDirs.put(cameraId, publicDir);
            recordRootDirs.put(cameraId, publicDir);
            captureRelativeDirs.put(cameraId, Environment.DIRECTORY_PICTURES);
            recordRelativeDirs.put(cameraId, Environment.DIRECTORY_MOVIES);
            imageContentUris.put(cameraId, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
            videoContentUris.put(cameraId, MediaStore.Video.Media.EXTERNAL_CONTENT_URI);
            audioMutes.put(cameraId, false);
            videoEncodingRates.put(cameraId, 3000000);
            isRecordings.put(cameraId, false);
            surfaces.put(cameraId, new Surface[]{null, null, null});
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
                Log.e(TAG, "getAvailablePreviewSizes: no camera available size!");
                return null;
            }

            return map.getOutputSizes(SurfaceTexture.class);
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
                Log.e(TAG, "getAvailableCaptureSizes: no camera available size!");
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
                Log.e(TAG, "getAvailableRecordSizes: no camera available size!");
                return null;
            }

            return map.getOutputSizes(MediaRecorder.class);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }

        return null;
    }

    @Override
    public boolean isRecording(String cameraId) {
        Boolean  isRecording = isRecordings.get(cameraId);
        return null != isRecording && isRecording;
    }

    @Override
    public void setCameraCallback(ICameraCallback callback) {
        cameraCallback = callback;
    }

    @Override
    public void setPreviewSurface(String cameraId, Surface surface) {
        Log.i(TAG, "setPreviewSurface: cameraId = " + cameraId);
        previewSurfaces.put(cameraId, surface);
    }

    @Override
    public boolean setCaptureRelativeDir(String cameraId, String dir, boolean isPublic, boolean isRemovable) {
        Log.i(TAG, "setCaptureRelativeDir: cameraId = " + cameraId + ", dir = " + dir
                + ", isPublic = " + isPublic + ", isRemovable = " + isRemovable);

        if (null == dir) {
            return false;
        }

        String rootDir = null;
        Uri uri = null;

        if (isPublic) {
            if (isRemovable) {
                rootDir = removablePublicDir;
                uri = REMOVABLE_IMAGE_CONTENT_URI;
            } else {
                rootDir = publicDir;
                uri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
            }
        } else {
            if (isRemovable) {
                rootDir = removablePrivateDir;
                uri = REMOVABLE_IMAGE_CONTENT_URI;
            } else {
                rootDir = privateDir;
                uri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
            }
        }

        File path = new File(rootDir, dir);

        if (!path.exists()) {
            if (path.mkdirs()) {
                Log.e(TAG, "setCaptureRelativeDir: make directory " + path.getAbsolutePath());
            } else {
                Log.e(TAG, "setCaptureRelativeDir: make directory " + path.getAbsolutePath() + " failed!");

                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q
                        || (Build.VERSION.SDK_INT == Build.VERSION_CODES.Q && Environment.isExternalStorageLegacy() && !isRemovable)
                        || !isPublic) {
                    Log.e(TAG, "setVideoDir: Build.VERSION.SDK_INT " + Build.VERSION.SDK_INT);
                    return false;
                }
            }
        }

        isCapturePublicDirs.put(cameraId, isPublic);
        isCaptureRemovableDirs.put(cameraId, isRemovable);
        captureRootDirs.put(cameraId, rootDir);
        captureRelativeDirs.put(cameraId, dir);
        imageContentUris.put(cameraId, uri);

        return true;
    }

    @Override
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

    @Override
    public void setCaptureCallback(ICaptureCallback callback) {
        captureCallback = callback;
    }

    @Override
    public boolean setRecordRelativeDir(String cameraId, String dir, boolean isPublic, boolean isRemovable) {
        Log.i(TAG, "setRecordRelativeDir: cameraId = " + cameraId + ", dir = " + dir
                + ", isPublic = " + isPublic + ", isRemovable = " + isRemovable);

        if (null == dir) {
            return false;
        }

        String rootDir = null;
        Uri uri = null;

        if (isPublic) {
            if (isRemovable) {
                rootDir = removablePublicDir;
                uri = REMOVABLE_VIDEO_CONTENT_URI;
            } else {
                rootDir = publicDir;
                uri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI;
            }
        } else {
            if (isRemovable) {
                rootDir = removablePrivateDir;
                uri = REMOVABLE_VIDEO_CONTENT_URI;
            } else {
                rootDir = privateDir;
                uri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI;
            }
        }

        File path = new File(rootDir, dir);

        if (!path.exists()) {
            if (path.mkdirs()) {
                Log.e(TAG, "setRecordRelativeDir: make directory " + path.getAbsolutePath());
            } else {
                Log.e(TAG, "setRecordRelativeDir: make directory " + path.getAbsolutePath() + " failed!");

                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q
                        || (Build.VERSION.SDK_INT == Build.VERSION_CODES.Q && Environment.isExternalStorageLegacy() && !isRemovable)
                        || !isPublic) {
                    Log.e(TAG, "setVideoDir: Build.VERSION.SDK_INT " + Build.VERSION.SDK_INT);
                    return false;
                }
            }
        }

        isRecordPublicDirs.put(cameraId, isPublic);
        isRecordRemovableDirs.put(cameraId, isRemovable);
        recordRootDirs.put(cameraId, rootDir);
        recordRelativeDirs.put(cameraId, dir);
        videoContentUris.put(cameraId, uri);

        return true;
    }

    @Override
    public void setRecordSize(String cameraId, int width, int height) {
        Log.i(TAG, "setRecordSize: cameraId = " + cameraId + ", width = " + width + ", height = " + height);
        recordSizes.put(cameraId, new Size(width, height));
    }

    @Override
    public void setAudioMute(String cameraId, boolean isMute) {
        Log.i(TAG, "setAudioMute: cameraId = " + cameraId + ", isMute = " + isMute);
        audioMutes.put(cameraId, isMute);
    }

    @Override
    public void setVideoEncodingRate(String cameraId, int bps) {
        Log.i(TAG, "setVideoEncodingRate: cameraId = " + cameraId + ", bps = " + bps);
        videoEncodingRates.put(cameraId, bps);
    }

    @Override
    public void setRecordCallback(IRecordCallback callback) {
        recordCallback = callback;
    }

    @Override
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

    @Override
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

    @Override
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

    @Override
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

	@Override
    public int capture(String cameraId) {
        return capture(cameraId, null, null);
    }

    @Override
    public int capture(String cameraId, String name) {
        return capture(cameraId, name, null);
    }

    @Override
    public int capture(String cameraId, Location location) {
        return capture(cameraId, null, location);
    }

    @Override
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

    @Override
    public int startRecord(String cameraId) {
        return startRecord(cameraId, null, 0);
    }

    @Override
    public int startRecord(String cameraId, String name) {
        return startRecord(cameraId, name, 0);
    }

    @Override
    public int startRecord(String cameraId, int duration) {
        return startRecord(cameraId, null, duration);
    }

    @Override
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

    @Override
    public int stopRecord(String cameraId) {
        Log.i(TAG, "stopRecord: cameraId = " + cameraId);

        MediaRecorder mediaRecorder = mediaRecorders.get(cameraId);

        if (null != mediaRecorder) {
            try {
                mediaRecorder.stop();
            } catch (Exception e) {
                e.printStackTrace();
            }
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

            capturePaths.put(cameraId, captureRootDirs.get(cameraId)
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
        String path = recordRootDirs.get(cameraId) + File.separator + recordRelativeDirs.get(cameraId) + File.separator + displayName;
        Size size = recordSizes.get(cameraId);
        Integer sensorOrientation = sensorOrientations.get(cameraId);
        WindowManager windowManager = ((WindowManager) context.getSystemService(Context.WINDOW_SERVICE));
        MediaRecorder mediaRecorder = new MediaRecorder();

        Log.i(TAG, "prepareRecorder: path = " + path);

        if ((Build.VERSION.SDK_INT > Build.VERSION_CODES.Q
                || (Build.VERSION.SDK_INT == Build.VERSION_CODES.Q && (!Environment.isExternalStorageLegacy() || isCaptureRemovableDirs.get(cameraId))))
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
            uri = resolver.insert(videoContentUris.get(cameraId), values);
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
                recordUris.put(cameraId, uri);
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

            recordUris.put(cameraId, null);
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
            exif.setAttribute(ExifInterface.TAG_GPS_LATITUDE, CoordinateConvert.decimalToDMS(latitude));
            exif.setAttribute(ExifInterface.TAG_GPS_LATITUDE_REF, latitude > 0 ? "N" : "S");
            exif.setAttribute(ExifInterface.TAG_GPS_LONGITUDE, CoordinateConvert.decimalToDMS(longitude));
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
