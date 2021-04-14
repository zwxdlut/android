package com.camera.example;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.graphics.SurfaceTexture;
import android.os.Bundle;
import android.util.Log;
import android.util.Size;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.widget.ImageView;
import android.widget.RelativeLayout;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import com.camera.CameraController;
import com.camera.CameraNativeFactory;
import com.camera.ICamera;

import java.util.Arrays;
import java.util.stream.Collectors;

public class MainActivity extends AppCompatActivity implements View.OnClickListener {
    private static final String TAG = MainActivity.class.getSimpleName();
    private static final int PERMISSION_REQUEST = 1;
    private CameraController camera = null;
    //private ICamera camera = null;
    private String[] cameraIds = null;
    private Surface previewSurface = null;
    private ImageView captureView = null;
    private Size previewSize = null;

    private TextureView.SurfaceTextureListener surfaceTextureListener = new TextureView.SurfaceTextureListener() {
        @Override
        public void onSurfaceTextureAvailable(SurfaceTexture surfaceTexture, int width, int height) {
            Log.i(TAG, "onSurfaceTextureAvailable: surfaceTexture = " + surfaceTexture + ", width = " + width + ", height = " + height);
            previewSurface = new Surface(surfaceTexture);
        }

        @Override
        public void onSurfaceTextureSizeChanged(SurfaceTexture surfaceTexture, int width, int height) {
            Log.i(TAG, "onSurfaceTextureSizeChanged: surfaceTexture = " + surfaceTexture + ", width = " + width + ", height = " + height);
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

    private CameraController.ICameraCallback cameraCallback = new CameraController.ICameraCallback() {
    //private ICamera.ICameraCallback cameraCallback = new ICamera.ICameraCallback() {
        @Override
        public void onState(String cameraId, int state) {
            Log.i(TAG, "onState: cameraId = " + cameraId + ", state = " + state);
        }

        @Override
        public void onError(String cameraId, int error) {
            Log.i(TAG, "onError: cameraId = " + cameraId + ", error = " + error);
        }
    };

    private CameraController.ICaptureCallback captureCallback = new CameraController.ICaptureCallback() {
    //private ICamera.ICaptureCallback captureCallback = new ICamera.ICaptureCallback() {
        @Override
        public void onComplete(String cameraId, String path) {
            Log.i(TAG, "onComplete: capture cameraId = " + cameraId + ", path = " + path);

            Rect rect = new Rect();
            getWindowManager().getDefaultDisplay().getRectSize(rect);
            Bitmap bm = BitmapFactory.decodeFile(path);
            float scale = ((float) (rect.width() - previewSize.getWidth())) / bm.getWidth();
            Log.i(TAG, "onComplete: scale = " + scale);

            if (0 >= scale) {
                return;
            }

            Matrix matrix = new Matrix();
            matrix.postScale(scale, scale);
            final Bitmap bitmap = Bitmap.createBitmap(bm, 0, 0, bm.getWidth(), bm.getHeight(), matrix, true);

            captureView.post(new Runnable() {
                @Override
                public void run() {
                    captureView.setVisibility(View.VISIBLE);
                    if (bitmap != null) {
                        captureView.setImageBitmap(bitmap);
                    }
                }
            });
        }
    };

    private CameraController.IRecordCallback recordCallback = new CameraController.IRecordCallback() {
    //private ICamera.IRecordCallback recordCallback = new ICamera.IRecordCallback() {
        @Override
        public void onComplete(String cameraId, String path) {
            Log.i(TAG, "onComplete: record cameraId = " + cameraId + ", path = " + path);
        }

        @Override
        public void onError(String cameraId, int what, int extra) {
            Log.i(TAG, "onError: record cameraId = " + cameraId + ", what = " + what + ", extra = " + extra);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.i(TAG, "onCreate");

        setContentView(R.layout.activity_main);
        ActivityCompat.requestPermissions(this,
                new String[]{Manifest.permission.CAMERA, Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.RECORD_AUDIO},
                PERMISSION_REQUEST);
    }

    @Override
    protected void onDestroy() {
        Log.i(TAG, "onDestroy");
        camera.close(cameraIds[0]);
        super.onDestroy();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        Log.i(TAG, "onRequestPermissionsResult: requestCode = " + requestCode + ", permissions = " + Arrays.asList(permissions)
                + ", grantResults = " + Arrays.stream(grantResults).boxed().collect(Collectors.toList()));

        if (requestCode == PERMISSION_REQUEST) {
            if (PackageManager.PERMISSION_GRANTED == grantResults[0]
                    && PackageManager.PERMISSION_GRANTED == grantResults[1]
                    && PackageManager.PERMISSION_GRANTED == grantResults[2]) {
                Log.i(TAG, "onRequestPermissionsResult: permission granted requestCode = " + requestCode);
                init();
            } else {
                Log.w(TAG, "onRequestPermissionsResult: permission denied requestCode = " + requestCode);
                finish();
            }
        } else {
            super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        }
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.btn_open_camera:
                Log.i(TAG, "onClick: open = " + camera.open(cameraIds[0]));
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
                Log.i(TAG, "onClick: capture = " + camera.capture(cameraIds[0], 116.2353515625, 39.5379397452));
                break;

            case R.id.btn_set_preview_surface:
                Log.i(TAG, "onClick: setPreviewSurface = " + camera.setPreviewSurface(cameraIds[0], previewSurface));
                break;

            case R.id.btn_start_record:
                // This is important because not all available record sizes are supported by the camera.
                // We set 1280×720 just for test.
                camera.setRecordSize(cameraIds[0], 1280, 720);
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
        // initialize camera
        camera = CameraController.getInstance();
        //camera = new CameraNativeFactory().getCamera();
        cameraIds = camera.getCameraIdList();
        camera.setCameraCallback(cameraCallback);
        camera.setCaptureCallback(captureCallback);
        camera.setRecordCallback(recordCallback);

        if (0 == cameraIds.length) {
            Log.w(TAG, "init: no camera!");
            finish();
        } else {
            Log.i(TAG, "init: camera count = " + cameraIds.length);
        }

        // initialize UI
        previewSize = getMatchPreviewSize();
        RelativeLayout.LayoutParams params = new RelativeLayout.LayoutParams(previewSize.getWidth(), previewSize.getHeight());
        params.leftMargin = 0;
        params.topMargin = 0;
        TextureView preView = findViewById(R.id.tv_preview);
        preView.setLayoutParams(params);
        preView.setSurfaceTextureListener(surfaceTextureListener);
        captureView = findViewById(R.id.iv_capture);
        findViewById(R.id.btn_open_camera).setOnClickListener(this);
        findViewById(R.id.btn_close_camera).setOnClickListener(this);
        findViewById(R.id.btn_start_preview).setOnClickListener(this);
        findViewById(R.id.btn_stop_preview).setOnClickListener(this);
        findViewById(R.id.btn_capture).setOnClickListener(this);
        findViewById(R.id.btn_set_preview_surface).setOnClickListener(this);
        findViewById(R.id.btn_start_record).setOnClickListener(this);
        findViewById(R.id.btn_stop_record).setOnClickListener(this);
    }

    private Size getMatchPreviewSize() {
        Rect rect = new Rect();
        getWindowManager().getDefaultDisplay().getRectSize(rect);
        Log.i(TAG, "getMatchPreviewSize: display rect = " + rect);
        Size size = new Size(rect.width() / 2, rect.height() / 2);
        Size[] sizes = camera.getAvailablePreviewSizes(cameraIds[0]);
        boolean isFlip = false;

        if (null != sizes) {
            int line = 0;
            int diff = Integer.MAX_VALUE;

            Log.i(TAG, "getMatchPreviewSize: available preview sizes = " + Arrays.toString(sizes));

            if (size.getWidth() < size.getHeight()) {
                line = size.getHeight();
                isFlip = true;
            } else {
                line = size.getWidth();
            }

            for (Size s : sizes) {
                int temp = Math.abs(line - s.getWidth());
                if (diff > temp) {
                    diff = temp;
                    size = s;
                }
            }
        }

        return (isFlip ? new Size(size.getHeight(), size.getWidth()) : size);
    }
}