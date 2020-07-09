package com.media.camera.demo;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.os.Bundle;
import android.util.Log;
import android.view.SurfaceView;
import android.view.View;
import android.widget.ImageView;
import android.widget.RelativeLayout;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.media.camera.CameraHelper;
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
    private ImageView captureView = null;

//    private CameraHelper.ICameraCallback cameraCallback = new CameraHelper.ICameraCallback() {
//        @Override
//        public void onError(String cameraId, int error) {
//            Log.d(TAG, "onError: cameraId = " + cameraId + ", error = " + error);
//        }
//    };
//
//    private CameraHelper.ICaptureCallback captureCallback = new CameraHelper.ICaptureCallback() {
//        @Override
//        public void onComplete(String cameraId, String filePath) {
//            Log.d(TAG, "onComplete: capture cameraId = " + cameraId + ", filePath = " + filePath);
//            final Bitmap bm = BitmapFactory.decodeFile(filePath);
//            Matrix matrix = new Matrix();
//            matrix.postScale(((float) 640) / bm.getWidth(), ((float) 480) / bm.getHeight());
//            final Bitmap bitmap = Bitmap.createBitmap(bm, 0, 0, bm.getWidth(), bm.getHeight(), matrix, true);
//
//            captureView.post(new Runnable() {
//                @Override
//                public void run() {
//                    captureView.setVisibility(View.VISIBLE);
//                    if (bitmap != null) {
//                        captureView.setImageBitmap(bitmap);
//                    }
//                }
//            });
//        }
//    };
//
//    private CameraHelper.IRecordingCallback recordingCallback = new CameraHelper.IRecordingCallback() {
//        @Override
//        public void onComplete(String cameraId, String filePath) {
//            Log.d(TAG, "onComplete: Recording cameraId = " + cameraId + ", filePath = " + filePath);
//        }
//
//        @Override
//        public void onError(String cameraId, int what, int extra) {
//            Log.d(TAG, "onError: Recording cameraId = " + cameraId + ", what = " + what + ", extra = " + extra);
//        }
//    };

    private ICamera.ICameraCallback cameraCallback = new ICamera.ICameraCallback() {
        @Override
        public void onState(String cameraId, int state) {
            Log.d(TAG, "onState: cameraId = " + cameraId + ", state = " + state);
        }

        @Override
        public void onError(String cameraId, int error) {
            Log.d(TAG, "onError: cameraId = " + cameraId + ", error = " + error);
        }
    };

    private ICamera.ICaptureCallback captureCallback = new ICamera.ICaptureCallback() {
        @Override
        public void onComplete(String cameraId, String filePath) {
            Log.d(TAG, "onComplete: capture cameraId = " + cameraId + ", filePath = " + filePath);
            final Bitmap bm = BitmapFactory.decodeFile(filePath);
            Matrix matrix = new Matrix();
            matrix.postScale(((float) 640) / bm.getWidth(), ((float) 480) / bm.getHeight());
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

    private ICamera.IRecordingCallback recordingCallback = new ICamera.IRecordingCallback() {
        @Override
        public void onComplete(String cameraId, String filePath) {
            Log.d(TAG, "onComplete: Recording cameraId = " + cameraId + ", filePath = " + filePath);
        }

        @Override
        public void onError(String cameraId, int what, int extra) {
            Log.d(TAG, "onError: Recording cameraId = " + cameraId + ", what = " + what + ", extra = " + extra);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        if (PackageManager.PERMISSION_GRANTED != ContextCompat.checkSelfPermission(MainActivity.this, Manifest.permission.CAMERA)
                ||PackageManager.PERMISSION_GRANTED != ContextCompat.checkSelfPermission(MainActivity.this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                || PackageManager.PERMISSION_GRANTED != ContextCompat.checkSelfPermission(MainActivity.this, Manifest.permission.RECORD_AUDIO)) {
            ActivityCompat.requestPermissions(MainActivity.this,
                    new String[]{Manifest.permission.CAMERA, Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.RECORD_AUDIO},
                    PERMISSION_REQUEST);
        }

        init();
    }

    @Override
    public void onClick(View v) {
        if (null == cameraIds || 0 >= cameraIds.length) {
            Log.w(TAG, "onClick: no camera");
            return;
        }

        switch (v.getId()) {
            case R.id.btn_open_camera:
                Log.d(TAG, "onClick: open = " + camera.open(cameraIds[0]));
                break;
            case R.id.btn_close_camera:
                Log.d(TAG, "onClick: close = " + camera.close(cameraIds[0]));
                break;
            case R.id.btn_start_preview:
                Log.d(TAG, "onClick: startPreview = " + camera.startPreview(cameraIds[0]));
                break;
            case R.id.btn_stop_preview:
                Log.d(TAG, "onClick: stopPreview = " + camera.stopPreview(cameraIds[0]));
                break;
            case R.id.btn_capture:
                Log.d(TAG, "onClick: capture = " + camera.capture(cameraIds[0], 116.2353515625, 39.5379397452));
                break;
            case R.id.btn_set_preview_surface:
                Log.d(TAG, "onClick: setPreviewSurface = " + camera.setPreviewSurface(cameraIds[0], preView.getHolder().getSurface()));
                break;
            case R.id.btn_start_recording:
                Log.d(TAG, "onClick: startRecording = " + camera.startRecording(cameraIds[0]));
                break;
            case R.id.btn_stop_recording:
                Log.d(TAG, "onClick: stopRecording = " + camera.stopRecording(cameraIds[0]));
                break;
            default:
                break;
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        Log.d(TAG, "onRequestPermissionsResult: requestCode = " + requestCode + ", permissions = " + Arrays.asList(permissions)
                + ", grantResults = " + Arrays.stream(grantResults).boxed().collect(Collectors.toList()));
        switch (requestCode) {
            case PERMISSION_REQUEST:
                if (PackageManager.PERMISSION_GRANTED == grantResults[0]
                        && PackageManager.PERMISSION_GRANTED == grantResults[1]
                        && PackageManager.PERMISSION_GRANTED == grantResults[2]) {
                    Log.d(TAG, "onRequestPermissionsResult: permission granted requestCode = " + requestCode);
                    init();
                } else {
                    Log.w(TAG, "onRequestPermissionsResult: permission denied requestCode = " + requestCode);
                }
                break;
            default:
                super.onRequestPermissionsResult(requestCode, permissions, grantResults);
                break;
        }
    }

    private void init() {
        //camera = CameraHelper.getInstance(this);
        camera = new CameraNativeFactory().getCamera(this);
        cameraIds = camera.getCameraIdList();
        camera.setStateCallback(cameraCallback);
        camera.setCaptureCallback(captureCallback);
        camera.setRecordingCallback(recordingCallback);
        Log.d(TAG, "onCreate: camera count = " + cameraIds.length);

        RelativeLayout.LayoutParams params = new RelativeLayout.LayoutParams(640, 480);
        params.leftMargin = 0;
        params.topMargin = 0;
        preView = findViewById(R.id.view_preview);
        preView.setLayoutParams(params);
        captureView = findViewById(R.id.view_capture);
        findViewById(R.id.btn_open_camera).setOnClickListener(this);
        findViewById(R.id.btn_close_camera).setOnClickListener(this);
        findViewById(R.id.btn_start_preview).setOnClickListener(this);
        findViewById(R.id.btn_stop_preview).setOnClickListener(this);
        findViewById(R.id.btn_capture).setOnClickListener(this);
        findViewById(R.id.btn_set_preview_surface).setOnClickListener(this);
        findViewById(R.id.btn_start_recording).setOnClickListener(this);
        findViewById(R.id.btn_stop_recording).setOnClickListener(this);
    }
}
