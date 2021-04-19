package com.camera.example;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.SurfaceTexture;
import android.location.Location;
import android.location.LocationManager;
import android.os.Bundle;
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

import java.util.Arrays;
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
        public void onCompleted(String cameraId, String path) {
            Log.i(TAG, "onCompleted: cameraId = " + cameraId + ", path = " + path);

            Bitmap srcBitmap = BitmapFactory.decodeFile(path);
            Matrix matrix = new Matrix();
            Rect rect = new Rect();

            getWindowManager().getDefaultDisplay().getRectSize(rect);
            matrix.postScale((float) (rect.width() - binding.tvPreview.getWidth()) / srcBitmap.getWidth(),
                    (float) binding.tvPreview.getHeight() / srcBitmap.getHeight());
            Log.i(TAG, "onCompleted: bitmap width = " + srcBitmap.getWidth() + ", bitmap height = " + srcBitmap.getHeight());
            Log.i(TAG, "onCompleted: matrix = " + matrix);
            final Bitmap bitmap = Bitmap.createBitmap(srcBitmap, 0, 0, srcBitmap.getWidth(), srcBitmap.getHeight(), matrix, true);

            binding.ivCapture.post(new Runnable() {
                @Override
                public void run() {
                    binding.ivCapture.setImageBitmap(bitmap);
                }
            });
        }

        @Override
        public void onFailed(String cameraId, String path) {
            Log.w(TAG, "onFailed: cameraId = " + cameraId + ", path = " + path);
        }
    };

    private CameraController.IRecordCallback recordCallback = new CameraController.IRecordCallback() {
    //private ICamera.IRecordCallback recordCallback = new ICamera.IRecordCallback() {
        @Override
        public void onCompleted(String cameraId, String path) {
            Log.i(TAG, "onCompleted: cameraId = " + cameraId + ", path = " + path);
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
            Log.i(TAG, "onSurfaceTextureUpdated: surfaceTexture = " + surfaceTexture);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.i(TAG, "onCreate");

        ActivityCompat.requestPermissions(this,
                new String[]{Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO, Manifest.permission.WRITE_EXTERNAL_STORAGE},
                PERMISSION_REQUEST);
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
            if (PackageManager.PERMISSION_GRANTED == grantResults[0]
                    && PackageManager.PERMISSION_GRANTED == grantResults[1]
                    && PackageManager.PERMISSION_GRANTED == grantResults[2]) {
                Log.i(TAG, "onRequestPermissionsResult: permission granted requestCode = " + requestCode);
                camera = CameraController.getInstance();
                //camera = new CameraNativeFactory().getCamera();
                cameraIds = camera.getCameraIdList();

                if (0 < cameraIds.length) {
                    Log.i(TAG, "onRequestPermissionsResult: camera count = " + cameraIds.length);
                    init();
                    return;
                } else {
                    Log.w(TAG, "onRequestPermissionsResult: no camera!");
                }
            } else {
                Log.w(TAG, "onRequestPermissionsResult: permission denied requestCode = " + requestCode);
            }

            finish();
        }
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.btn_open_camera:
                Log.i(TAG, "onClick: open = " + camera.open(cameraIds[0]));
                // This is important because not all available record sizes are supported by the camera.
                // We set 1280×720 just for test.
                camera.setRecordSize(cameraIds[0], 1280, 720);
                break;

            case R.id.btn_close_camera:
                Log.i(TAG, "onClick: close = " + camera.close(cameraIds[0]));
                break;

            case R.id.btn_start_preview:
                Log.i(TAG, "onClick: startPreview = " + camera.startPreview(cameraIds[0]));
                break;

            case R.id.btn_stop_preview:
                Log.i(TAG, "onClick: stopPreview = " + camera.stopPreview(cameraIds[0]));
                break;

            case R.id.btn_capture:
                Location location = new Location(LocationManager.PASSIVE_PROVIDER);
                location.setLatitude(116.2353515625);
                location.setLongitude(39.5379397452);
                Log.i(TAG, "onClick: capture = " + camera.capture(cameraIds[0], location));
                break;

            case R.id.btn_set_preview_surface:
                Log.i(TAG, "onClick: setPreviewSurface = " + camera.setPreviewSurface(cameraIds[0], previewSurface));
                break;

            case R.id.btn_start_record:
                Log.i(TAG, "onClick: startRecord = " + camera.startRecord(cameraIds[0]));
                break;

            case R.id.btn_stop_record:
                Log.i(TAG, "onClick: stopRecord = " + camera.stopRecord(cameraIds[0]));
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