package com.media.camera_demo;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.os.Bundle;
import android.util.Log;
import android.util.Size;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.widget.ImageView;
import android.widget.RelativeLayout;

import com.media.camera.CameraNativeFactory;
import com.media.camera.ICamera;

import java.util.Arrays;
import java.util.stream.Collectors;

public class MainActivity extends AppCompatActivity implements View.OnClickListener {
    private static final String TAG = "MainActivity";
    private static final int PERMISSION_REQUEST = 1;
    //private CameraHelper camera = null;
    private ICamera camera = null;
    private String cameraIds[] = new String[0];
    private SurfaceView preView = null;
    private SurfaceHolder holder = null;
    private ImageView captureView = null;
    Size size = null;

    private SurfaceHolder.Callback callback = new SurfaceHolder.Callback() {
        @Override
        public void surfaceCreated(SurfaceHolder holder) {
            int rotation = getWindowManager().getDefaultDisplay().getRotation();

            Log.i(TAG, "surfaceCreated: holder = " + holder + ", rotation = " + rotation);
            MainActivity.this.holder = holder;
        }

        @Override
        public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
            Log.i(TAG, "surfaceChanged: holder = " + holder + ", format = " + format + ", width = " + width + ", height = " + height);
        }

        @Override
        public void surfaceDestroyed(SurfaceHolder holder) {
            Log.i(TAG, "surfaceDestroyed: holder = " + holder);
        }
    };

    //private CameraHelper.ICameraCallback cameraCallback = new CameraHelper.ICameraCallback() {
    private ICamera.ICameraCallback cameraCallback = new ICamera.ICameraCallback() {
        @Override
        public void onState(String cameraId, int state) {
            Log.i(TAG, "onState: cameraId = " + cameraId + ", state = " + state);
        }

        @Override
        public void onError(String cameraId, int error) {
            Log.i(TAG, "onError: cameraId = " + cameraId + ", error = " + error);
        }
    };

    //private CameraHelper.ICaptureCallback captureCallback = new CameraHelper.ICaptureCallback() {
    private ICamera.ICaptureCallback captureCallback = new ICamera.ICaptureCallback() {
        @Override
        public void onComplete(String cameraId, String path) {
            Log.i(TAG, "onComplete: capture cameraId = " + cameraId + ", path = " + path);

            final Bitmap bm = BitmapFactory.decodeFile(path);
            Rect rect = new Rect();
            getWindowManager().getDefaultDisplay().getRectSize(rect);
            Matrix matrix = new Matrix();
            float scale = ((float) (rect.width() - size.getWidth())) / bm.getWidth();
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

    //private CameraHelper.IRecordCallback recordCallback = new CameraHelper.IRecordCallback() {
    private ICamera.IRecordCallback recordCallback = new ICamera.IRecordCallback() {
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
        setContentView(R.layout.activity_main);

        ActivityCompat.requestPermissions(this,
                new String[]{Manifest.permission.CAMERA, Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.RECORD_AUDIO},
                PERMISSION_REQUEST);

        init();
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
        if (null == cameraIds || 0 >= cameraIds.length) {
            Log.w(TAG, "onClick: no camera");
            return;
        }

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
                if (null != holder) {
                    Log.i(TAG, "onClick: setPreviewSurface = " + camera.setPreviewSurface(cameraIds[0], holder.getSurface()));
                } else {
                    Log.w(TAG, "onClick: setPreviewSurface surface holder is null");
                }
                break;
            case R.id.btn_start_record:
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
        // Initialize camera
        //camera = CameraHelper.getInstance(this);
        camera = new CameraNativeFactory().getCamera(this);
        cameraIds = camera.getCameraIdList();
        camera.setCameraCallback(cameraCallback);
        camera.setCaptureCallback(captureCallback);
        camera.setRecordCallback(recordCallback);
        Log.i(TAG, "init: camera count = " + cameraIds.length);

        // Initialize UI
        size = getMatchSize();
        RelativeLayout.LayoutParams params = new RelativeLayout.LayoutParams(size.getWidth(), size.getHeight());
        params.leftMargin = 0;
        params.topMargin = 0;
        preView = findViewById(R.id.view_preview);
        preView.setLayoutParams(params);
        preView.getHolder().addCallback(callback);
        captureView = findViewById(R.id.view_capture);
        findViewById(R.id.btn_open_camera).setOnClickListener(this);
        findViewById(R.id.btn_close_camera).setOnClickListener(this);
        findViewById(R.id.btn_start_preview).setOnClickListener(this);
        findViewById(R.id.btn_stop_preview).setOnClickListener(this);
        findViewById(R.id.btn_capture).setOnClickListener(this);
        findViewById(R.id.btn_set_preview_surface).setOnClickListener(this);
        findViewById(R.id.btn_start_record).setOnClickListener(this);
        findViewById(R.id.btn_stop_record).setOnClickListener(this);
    }

    private Size getMatchSize() {
        Rect rect = new Rect();

        getWindowManager().getDefaultDisplay().getRectSize(rect);
        Log.i(TAG, "getMatchSize: display rect = " + rect);

        Size size = new Size(rect.width() / 2, rect.height() / 2);
        Size sizes[] = camera.getAvailablePreviewSizes(cameraIds[0]);
        boolean isFlip = false;

        if (null != sizes) {
            Log.i(TAG, "getMatchSize: available preview sizes = " + Arrays.toString(sizes));
            int line = 0;
            int diff = Integer.MAX_VALUE;

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