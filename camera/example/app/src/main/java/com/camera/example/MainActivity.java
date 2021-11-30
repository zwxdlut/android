package com.camera.example;

import android.Manifest;
import android.content.ContentUris;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.SurfaceTexture;
import android.location.Location;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Log;
import android.util.Size;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.widget.RelativeLayout;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.databinding.DataBindingUtil;

import com.camera.CameraController;
import com.camera.CameraNativeFactory;
import com.camera.ICamera;
import com.camera.example.databinding.ActivityMainBinding;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class MainActivity extends AppCompatActivity implements View.OnClickListener {
    private static final String TAG = MainActivity.class.getSimpleName();
    private static final int PERMISSION_REQUEST = 1;
    private String[] cameraIds = null;
    private CameraController camera = null;
    //private ICamera camera = null;
    private Surface previewSurface = null;
    private ActivityMainBinding binding = null;

    private CameraController.ICameraCallback cameraCallback = new CameraController.ICameraCallback() {
    //private ICamera.ICameraCallback cameraCallback = new ICamera.ICameraCallback() {
        @Override
        public void onState(String cameraId, int state) {
            Log.i(TAG, "onState: cameraId = " + cameraId + ", state = " + state);
        }

        @Override
        public void onError(String cameraId, int error) {
            Log.e(TAG, "onError: cameraId = " + cameraId + ", error = " + error);
        }
    };

    private CameraController.ICaptureCallback captureCallback = new CameraController.ICaptureCallback() {
    //private ICamera.ICaptureCallback captureCallback = new ICamera.ICaptureCallback() {
        @Override
        public void onStarted(String cameraId, String path) {
            Log.i(TAG, "onStarted: cameraId = " + cameraId + ", path = " + path);
        }

        @Override
        public void onCompleted(String cameraId, final String path) {
            Log.i(TAG, "onCompleted: cameraId = " + cameraId + ", path = " + path);
            binding.ivCapture.post(new Runnable() {
                @Override
                public void run() {
                    Rect rect = new Rect();
                    getWindowManager().getDefaultDisplay().getRectSize(rect);
                    RelativeLayout.LayoutParams params = new RelativeLayout.LayoutParams(
                            rect.width() - binding.tvPreview.getWidth(), binding.tvPreview.getHeight());

                    params.leftMargin = binding.tvPreview.getWidth();
                    params.topMargin = 0;
                    binding.ivCapture.setLayoutParams(params);

                    Cursor cursor = getContentResolver().query(
                            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                            new String[] {MediaStore.Images.Media._ID},
                            MediaStore.Images.Media.DATA + " LIKE '%" + path + "%'",
                            null,
                            null);

                    if (null != cursor) {
                        if (cursor.moveToNext()) {
                            Uri uri = ContentUris.withAppendedId(
                                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                                    cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)));

                            Log.i(TAG, "onCompleted: the image uri = " + uri);
                            binding.ivCapture.setImageURI(uri);
                        } else {
                            Log.e(TAG, "onCompleted: no items!");
                            binding.ivCapture.setImageBitmap(BitmapFactory.decodeFile(path));
                        }

                        cursor.close();
                    } else {
                        Log.e(TAG, "onCompleted: Cursor is null!");
                        binding.ivCapture.setImageBitmap(BitmapFactory.decodeFile(path));
                    }
                }
            });
        }

        @Override
        public void onFailed(String cameraId, String path) {
            Log.e(TAG, "onFailed: cameraId = " + cameraId + ", path = " + path);
        }
    };

    private CameraController.IRecordCallback recordCallback = new CameraController.IRecordCallback() {
    //private ICamera.IRecordCallback recordCallback = new ICamera.IRecordCallback() {
        @Override
        public void onCompleted(String cameraId, String path) {
            Log.i(TAG, "onCompleted: cameraId = " + cameraId + ", path = " + path);

            Cursor cursor = getContentResolver().query(
                    MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                    new String[] {MediaStore.Video.Media._ID, MediaStore.Video.Media.DATE_TAKEN},
                    MediaStore.Video.Media.DATA + " LIKE '%" + path + "%'",
                    null,
                    null);

            if (null != cursor) {
                if (cursor.moveToNext()) {
                    Uri uri = ContentUris.withAppendedId(
                            MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                            cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)));

                    Log.i(TAG, "onCompleted: the video uri = " + uri);
                } else {
                    Log.e(TAG, "onCompleted: no items!");
                }

                cursor.close();
            } else {
                Log.e(TAG, "onCompleted: Cursor is null!");
                binding.ivCapture.setImageBitmap(BitmapFactory.decodeFile(path));
            }
        }

        @Override
        public void onError(String cameraId, String path, int what, int extra) {
            Log.e(TAG, "onError: cameraId = " + cameraId + ", path = " + path + ", what = " + what + ", extra = " + extra);
        }
    };

    private TextureView.SurfaceTextureListener surfaceTextureListener = new TextureView.SurfaceTextureListener() {
        @Override
        public void onSurfaceTextureAvailable(SurfaceTexture surfaceTexture, int width, int height) {
            Log.i(TAG, "onSurfaceTextureAvailable: surfaceTexture = " + surfaceTexture + ", width = " + width + ", height = " + height);
            configurePreviewTransform(width, height, width, height);
            previewSurface = new Surface(surfaceTexture);
        }

        @Override
        public void onSurfaceTextureSizeChanged(SurfaceTexture surfaceTexture, int width, int height) {
            Log.i(TAG, "onSurfaceTextureSizeChanged: surfaceTexture = " + surfaceTexture + ", width = " + width + ", height = " + height);
            configurePreviewTransform(width, height, width, height);
            previewSurface = new Surface(surfaceTexture);
        }

        @Override
        public boolean onSurfaceTextureDestroyed(SurfaceTexture surfaceTexture) {
            Log.i(TAG, "onSurfaceTextureDestroyed: surfaceTexture = " + surfaceTexture);
            previewSurface = null;
            return true;
        }

        @Override
        public void onSurfaceTextureUpdated(SurfaceTexture surfaceTexture) {
            //Log.i(TAG, "onSurfaceTextureUpdated: surfaceTexture = " + surfaceTexture);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.i(TAG, "onCreate");

        List<String> permissions = new ArrayList<>();

        permissions.add(Manifest.permission.CAMERA);
        permissions.add(Manifest.permission.RECORD_AUDIO);
        permissions.add(Manifest.permission.WRITE_EXTERNAL_STORAGE);
        permissions.add(Manifest.permission.READ_EXTERNAL_STORAGE);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            permissions.add(Manifest.permission.ACCESS_MEDIA_LOCATION);
        }

        ActivityCompat.requestPermissions(this, permissions.toArray(new String[0]), PERMISSION_REQUEST);
    }

    @Override
    protected void onDestroy() {
        Log.i(TAG, "onDestroy");

        // de-initialize the camera
        camera.stopRecord(cameraIds[0]);
        camera.close(cameraIds[0]);
        camera.setCameraCallback(null);
        camera.setCaptureCallback(null);
        camera.setRecordCallback(null);
        super.onDestroy();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        Log.i(TAG, "onRequestPermissionsResult: requestCode = " + requestCode + ", permissions = " + Arrays.asList(permissions)
                + ", grantResults = " + Arrays.stream(grantResults).boxed().collect(Collectors.toList()));

        if (requestCode == PERMISSION_REQUEST) {
            boolean isGranted = true;

            for (int result : grantResults) {
                if (PackageManager.PERMISSION_GRANTED != result) {
                    isGranted = false;
                    break;
                }
            }

            if (isGranted) {
                Log.i(TAG, "onRequestPermissionsResult: permission granted requestCode = " + requestCode);
                camera = CameraController.getInstance();
                //camera = new CameraNativeFactory().getCamera();
                cameraIds = camera.getCameraIdList();

                if (0 < cameraIds.length) {
                    Log.i(TAG, "onRequestPermissionsResult: camera count = " + cameraIds.length);
                    init();
                } else {
                    Log.e(TAG, "onRequestPermissionsResult: no camera!");
                    finish();
                }
            } else {
                Log.e(TAG, "onRequestPermissionsResult: permission denied requestCode = " + requestCode);
                finish();
            }
        }
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.btn_set_preview_surface:
                Log.i(TAG, "onClick: setPreviewSurface()");
                camera.setPreviewSurface(cameraIds[0], previewSurface);
                break;

            case R.id.btn_open_camera:
                Log.i(TAG, "onClick: open() = " + camera.open(cameraIds[0]));
                break;

            case R.id.btn_close_camera:
                Log.i(TAG, "onClick: close() = " + camera.close(cameraIds[0]));
                break;

            case R.id.btn_start_preview:
                Log.i(TAG, "onClick: startPreview() = " + camera.startPreview(cameraIds[0]));
                break;

            case R.id.btn_stop_preview:
                Log.i(TAG, "onClick: stopPreview() = " + camera.stopPreview(cameraIds[0]));
                break;

            case R.id.btn_capture:
                Location location = new Location(LocationManager.GPS_PROVIDER);
                location.setLatitude(116.2353515625);
                location.setLongitude(39.5379397452);
                location.setAltitude(100);
                Log.i(TAG, "onClick: capture() = " + camera.capture(cameraIds[0], location));
                break;

            case R.id.btn_start_record:
                Log.i(TAG, "onClick: startRecord() = " + camera.startRecord(cameraIds[0]));
                break;

            case R.id.btn_stop_record:
                Log.i(TAG, "onClick: stopRecord() = " + camera.stopRecord(cameraIds[0]));
                break;

            default:
                break;
        }
    }

    private void init() {
        // initialize the camera
        camera.setCameraCallback(cameraCallback);
        camera.setCaptureCallback(captureCallback);
        camera.setRecordCallback(recordCallback);
//        camera.setCaptureRelativeDir(cameraIds[0], "Pictures", false, false);
//        camera.setRecordRelativeDir(cameraIds[0], "Movies", false, false);
        // This is important because not all available sizes are supported by the camera.
        // We set 1280×720 just for test.
        camera.setCaptureSize(cameraIds[0], 1280, 720);
        camera.setRecordSize(cameraIds[0], 1280, 720);

        // initialize the UI
        Size size = getMatchedPreviewSize();
        RelativeLayout.LayoutParams params = new RelativeLayout.LayoutParams(size.getWidth(), size.getHeight());
        params.leftMargin = 0;
        params.topMargin = 0;
        binding = DataBindingUtil.setContentView(this, R.layout.activity_main);
        binding.tvPreview.setLayoutParams(params);
        binding.tvPreview.setSurfaceTextureListener(surfaceTextureListener);
        binding.btnOpenCamera.setOnClickListener(this);
        binding.btnCloseCamera.setOnClickListener(this);
        binding.btnSetPreviewSurface.setOnClickListener(this);
        binding.btnStartPreview.setOnClickListener(this);
        binding.btnStopPreview.setOnClickListener(this);
        binding.btnCapture.setOnClickListener(this);
        binding.btnStartRecord.setOnClickListener(this);
        binding.btnStopRecord.setOnClickListener(this);
    }

    private Size getMatchedPreviewSize() {
        Rect rect = new Rect();
        getWindowManager().getDefaultDisplay().getRectSize(rect);
        Size size = new Size(rect.width() / 2, rect.height() / 2);
        Size[] sizes = camera.getAvailablePreviewSizes(cameraIds[0]);
        boolean isFlip = false;
        int line = size.getWidth();
        int diff = Integer.MAX_VALUE;

        Log.i(TAG, "getMatchedPreviewSize: display rect = " + rect);
        Log.i(TAG, "getMatchedPreviewSize: available preview sizes = " + Arrays.toString(sizes));

        if (size.getWidth() < size.getHeight()) {
            isFlip = true;
        }

        for (Size s : sizes) {
            int temp = Math.abs(line - (isFlip ? s.getHeight() : s.getWidth()));
            if (diff > temp) {
                diff = temp;
                size = s;
            }
        }

        Size matchedSize = (isFlip ? new Size(size.getHeight(), size.getWidth()) : size);
        Log.i(TAG, "getMatchedPreviewSize: matchedSize = " + matchedSize);

        return matchedSize;
    }

    private void configurePreviewTransform(int srcWidth, int srcHeight, int dstWidth, int dstHeight) {
        Matrix matrix = new Matrix();
        RectF viewRect = new RectF(0, 0, srcWidth, srcHeight);
        float centerX = viewRect.centerX();
        float centerY = viewRect.centerY();
        int rotation = getWindowManager().getDefaultDisplay().getRotation();

        if (Surface.ROTATION_90 == rotation || Surface.ROTATION_270 == rotation) {
            RectF bufferRect = new RectF(0, 0, dstHeight, dstWidth);
            float scale = Math.max((float) srcHeight / dstHeight, (float) srcWidth / dstWidth);

            bufferRect.offset(centerX - bufferRect.centerX(), centerY - bufferRect.centerY());
            matrix.setRectToRect(viewRect, bufferRect, Matrix.ScaleToFit.FILL);
            matrix.postScale(scale, scale, centerX, centerY);
            matrix.postRotate(90 * (rotation - 2), centerX, centerY);
        } else if (Surface.ROTATION_180 == rotation) {
            matrix.postRotate(180, centerX, centerY);
        }

        binding.tvPreview.setTransform(matrix);
    }
}