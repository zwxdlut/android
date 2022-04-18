package com.mxnavi.dvr.activity;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Matrix;
import android.graphics.RectF;
import android.graphics.SurfaceTexture;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.WindowManager;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.databinding.DataBindingUtil;
import androidx.documentfile.provider.DocumentFile;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProvider;

import com.mxnavi.dvr.R;
import com.mxnavi.dvr.databinding.ActivityMainBinding;
import com.mxnavi.dvr.utils.DialogUtil;
import com.mxnavi.dvr.utils.MediaDirUtil;
import com.mxnavi.dvr.viewmodel.MainViewModel;
import com.storage.MediaProviderManager;
import com.storage.util.ToastUtil;

import java.io.File;

public class MainActivity extends AppCompatActivity implements View.OnClickListener {
    public static MainActivity instance;
    public static boolean isLogout = false;
    private static final String TAG = "DVR-" + MainActivity.class.getSimpleName();
    private static final int OPEN_DOCUMENT_TREE_CODE = 1;
    private ActivityMainBinding binding = null;
    private MainViewModel viewModel = null;
    private MediaPlayer shootMP;

    private final TextureView.SurfaceTextureListener surfaceTextureListener = new TextureView.SurfaceTextureListener() {
        @Override
        public void onSurfaceTextureAvailable(SurfaceTexture surfaceTexture, int width, int height) {
            Log.i(TAG, "onSurfaceTextureAvailable: surfaceTexture = " + surfaceTexture + ", width = " + width + ", height = " + height);
            configurePreviewTransform(width, height, width, height);
            viewModel.setPreviewSurface(new Surface(surfaceTexture));
            viewModel.startRecord();
        }

        @Override
        public void onSurfaceTextureSizeChanged(SurfaceTexture surfaceTexture, int width, int height) {
            Log.i(TAG, "onSurfaceTextureSizeChanged: surfaceTexture = " + surfaceTexture + ", width = " + width + ", height = " + height);
            configurePreviewTransform(width, height, width, height);
            viewModel.stopRecord();
            viewModel.setPreviewSurface(new Surface(surfaceTexture));
            viewModel.startRecord();
        }

        @Override
        public boolean onSurfaceTextureDestroyed(SurfaceTexture surfaceTexture) {
            Log.i(TAG, "onSurfaceTextureDestroyed: surfaceTexture = " + surfaceTexture);
            viewModel.stopRecord();
            viewModel.setPreviewSurface(null);
            return true;
        }

        @Override
        public void onSurfaceTextureUpdated(SurfaceTexture surfaceTexture) {
            //Log.i(TAG, "onSurfaceTextureUpdated: surfaceTexture = " + surfaceTexture);
        }
    };

    private final BroadcastReceiver keyReceiver = new BroadcastReceiver() {
        final String SYSTEM_REASON = "reason";
        final String SYSTEM_HOME_KEY = "homekey";
        final String SYSTEM_RECENT_APPS = "recentapps";

        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();

            Log.i(TAG, "onReceive: intent = " + intent);

            if (Intent.ACTION_CLOSE_SYSTEM_DIALOGS.equals(action)) {
                String reason = intent.getStringExtra(SYSTEM_REASON);

                Log.i(TAG, "onReceive: reason = " + reason);

                if (SYSTEM_HOME_KEY.equals(reason)) {
                    ToastUtil.show(context, R.string.app_in_background);
                } else if (SYSTEM_RECENT_APPS.equals(reason)) {
                    // TODO:
                }
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        instance = this;
        isLogout = false;
        Log.i(TAG, "onCreate");

//        if (MediaDirUtil.isRemovable) {
//            StorageManager sm = getSystemService(StorageManager.class);
//            StorageVolume volume = sm.getStorageVolume(new File(MediaDirUtil.ROOT_DIR));
//
//            if (null != volume) {
//                Intent intent;
//
//                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
//                    intent = volume.createOpenDocumentTreeIntent();
//                } else {
//                    intent = volume.createAccessIntent(null);
//                }
//
//                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
//                intent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
//                intent.addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
//                startActivityForResult(intent, OPEN_DOCUMENT_TREE_CODE);
//                return;
//            }
//        }

        init();
    }

    @Override
    protected void onDestroy() {
        Log.i(TAG, "onDestroy");
        unregisterReceiver(keyReceiver);
        MediaProviderManager.getInstance().clearUpload();
        super.onDestroy();

        if (isLogout) {
            return;
        }

        // exit application
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    Thread.sleep(1000);
                    Log.w(TAG, "Exit application!");
                    System.exit(0);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }).start();
    }

    @Override
    public void onClick(View v) {
        if (binding.rvRecord.equals(v)) {
            if (binding.getRecording()) {
                viewModel.stopRecord();
            } else {
                viewModel.startRecord();
            }
        } else if (binding.rvCapture.equals(v)) {
            binding.rvCapture.setEnabled(false);
            binding.rvCapture.setAlpha(0.5f);
            viewModel.capture();
        } else if (binding.rvMute.equals(v)) {
            boolean mute = !binding.rvMute.isSelected();

            binding.rvMute.setSelected(mute);
            viewModel.setMute(!mute);

            if (mute) {
                ToastUtil.show(MainActivity.this, R.string.not_mute);
            } else {
                ToastUtil.show(MainActivity.this, R.string.mute);
            }
        } else if (binding.rvMenu.equals(v)) {
            startActivity(new Intent(this, MenuActivity.class));
        }
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        Log.i(TAG, "onKeyUp: keyCode = " + keyCode + ", event = " + event);

        if (KeyEvent.KEYCODE_BACK == keyCode && KeyEvent.ACTION_UP == event.getAction()) {
            DialogUtil.showNormalDialog(MainActivity.this, MainActivity.this.getString(R.string.exit_app), null,
                    "确定", new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            MainActivity.this.finish();
                        }
                    }, "取消", new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                        }
                    });

            return true;
        } else if (KeyEvent.KEYCODE_HOME == keyCode) {
            return true;
        }

        return super.onKeyUp(keyCode, event);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable @org.jetbrains.annotations.Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        Log.i(TAG, "onActivityResult: requestCode = " + requestCode + ", resultCode = " + resultCode + ", data = " + data);

        if (OPEN_DOCUMENT_TREE_CODE == requestCode && Activity.RESULT_OK == resultCode) {
            // The result data contains a URI for the document or directory that
            // the user selected.
            if (null != data) {
                Uri uri = data.getData();

                if (null != uri) {
                    File dir = new File(MediaDirUtil.ROOT_DIR, MediaDirUtil.RELATIVE_DIR);
                    DocumentFile documentFile = null;

                    if (dir.exists()) {
                        documentFile = DocumentFile.fromFile(dir);
                    } else {
                        documentFile = DocumentFile.fromTreeUri(this, uri);

                        if (null != documentFile) {
                            documentFile = documentFile.createDirectory(MediaDirUtil.RELATIVE_DIR);
                        }
                    }

                    dir = new File(MediaDirUtil.ROOT_DIR, MediaDirUtil.RELATIVE_IMAGE_DIR);
                    if (!dir.exists() && null != documentFile) {
                        DocumentFile df = documentFile.createDirectory(Environment.DIRECTORY_PICTURES);

                        if (null != df) {
                            Log.e(TAG, "onActivityResult: make directory " + dir.getAbsolutePath());
                        } else {
                            Log.e(TAG, "onActivityResult: make directory " + dir.getAbsolutePath() + ", failed!");
                        }
                    }

                    dir = new File(MediaDirUtil.ROOT_DIR, MediaDirUtil.RELATIVE_VIDEO_DIR);
                    if (!dir.exists() && null != documentFile) {
                        DocumentFile df = documentFile.createDirectory(Environment.DIRECTORY_MOVIES);

                        if (null != df) {
                            Log.e(TAG, "onActivityResult: make directory " + dir.getAbsolutePath());
                        } else {
                            Log.e(TAG, "onActivityResult: make directory " + dir.getAbsolutePath() + ", failed!");
                        }
                    }

                    final int takeFlags = data.getFlags()
                            & (Intent.FLAG_GRANT_READ_URI_PERMISSION
                            | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                    // Check for the freshest data.
                    getContentResolver().takePersistableUriPermission(uri, takeFlags);
                }
            }

            init();
        }
    }

    private void init() {
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        binding = DataBindingUtil.setContentView(this, R.layout.activity_main);
        binding.setRecording(false);
        binding.rvRecord.setSelected(false);
        binding.tvPreview.setSurfaceTextureListener(surfaceTextureListener);
        binding.rvRecord.setOnClickListener(this);
        binding.rvCapture.setOnClickListener(this);
        binding.rvMute.setOnClickListener(this);
        binding.rvMenu.setOnClickListener(this);

        viewModel = new ViewModelProvider(this, new MainViewModel.Factory(this)).get(MainViewModel.class);
        viewModel.getDate().observe(this, new Observer<String>() {
            @Override
            public void onChanged(String s) {
                binding.tvDate.setText(s);

                if (View.VISIBLE == binding.ivShape.getVisibility()) {
                    binding.ivShape.setVisibility(View.INVISIBLE);
                } else {
                    binding.ivShape.setVisibility(View.VISIBLE);
                }
            }
        });

        viewModel.getRecording().observe(this, new Observer<Boolean>() {
            @Override
            public void onChanged(Boolean aBoolean) {
                if (aBoolean != binding.getRecording()) {
                    if (aBoolean) {
                        ToastUtil.show(MainActivity.this, R.string.start_record);
                    } else {
                        ToastUtil.show(MainActivity.this, R.string.stop_record);
                    }
                }

                binding.setRecording(aBoolean);
                binding.rvRecord.setSelected(aBoolean);
            }
        });

        viewModel.getCaptureResult().observe(this, new Observer<Boolean>() {
            @Override
            public void onChanged(Boolean aBoolean) {
                binding.rvCapture.setEnabled(true);
                binding.rvCapture.setAlpha(1f);

                if (aBoolean) {
                    ToastUtil.show(MainActivity.this, R.string.capture_success);
                } else {
                    ToastUtil.show(MainActivity.this, R.string.capture_failed);
                }
            }
        });

        viewModel.getAvailableStorageSpace().observe(this, new Observer<Long>() {
            @Override
            public void onChanged(Long integer) {
                String indication = null;

                if (MainViewModel.STORAGE_SPACE_LIMIT2 >= integer) {
                    indication = MainActivity.this.getString(R.string.indicate_storage_limit2);
                } else if (MainViewModel.STORAGE_SPACE_LIMIT1 >= integer) {
                    indication = MainActivity.this.getString(R.string.indicate_storage_limit1);
                }

                if (null == indication) {
                    return;
                }

                DialogUtil.showNormalDialog(MainActivity.this, indication, null,
                        "确定", new View.OnClickListener() {
                            @Override
                            public void onClick(View v) {
                            }
                        }, null, null);
            }
        });

        viewModel.getAltitude().observe(this, new Observer<Double>() {
            @Override
            public void onChanged(Double aDouble) {
                binding.tvAltitude.setText(String.format("%dM", aDouble.intValue()));
            }
        });

        registerReceiver(keyReceiver, new IntentFilter(Intent.ACTION_CLOSE_SYSTEM_DIALOGS));
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