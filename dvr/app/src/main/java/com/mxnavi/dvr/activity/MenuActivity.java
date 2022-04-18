package com.mxnavi.dvr.activity;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;

import androidx.appcompat.app.AppCompatActivity;
import androidx.databinding.DataBindingUtil;

import com.mxnavi.dvr.R;
import com.mxnavi.dvr.databinding.ActivityMenuBinding;
import com.mxnavi.dvr.utils.DialogUtil;
import com.mxnavi.dvr.utils.PhoneNumberManager;
import com.mxnavi.dvr.viewmodel.MediaViewModel;
import com.storage.MediaProviderManager;

public class MenuActivity extends AppCompatActivity implements View.OnClickListener {
    private static final String TAG = "DVR-" + MenuActivity.class.getSimpleName();
    private double totalUploadSize = 0;
    private double uploadingSize = 0;
    private ActivityMenuBinding binding = null;
    private MediaViewModel viewModel = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.i(TAG, "onCreate");
        
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        binding = DataBindingUtil.setContentView(this, R.layout.activity_menu);
        binding.setEnable(true);
        binding.setUploading(false);
        binding.setDeleting(false);
        binding.btnBack.setOnClickListener(this);
        binding.btnPlayback.setOnClickListener(this);
        binding.btnUpload.setOnClickListener(this);
        binding.btnDelete.setOnClickListener(this);
        binding.btnLogout.setOnClickListener(this);

//        viewModel = new ViewModelProvider(this, new MediaViewModel.Factory(this)).get(MediaViewModel.class);
//        viewModel.getQueriedAll().observe(this, new Observer<List<MediaBean>>() {
//            @Override
//            public void onChanged(List<MediaBean> beans) {
//                if (null == beans || beans.isEmpty()) {
//                    binding.setUploading(false);
//                    binding.setDeleting(false);
//                    return;
//                }
//
//                binding.setEnable(false);
//
//                if (binding.getUploading()) {
//                    binding.pbUploading.setProgress(0);
//                    binding.tvProgress.setText("0%");
//                    uploadingSize = 0;
//                    totalUploadSize = 0;
//
//                    for (MediaBean bean : beans) {
//                        totalUploadSize += bean.getSize();
//                    }
//
//                    Log.i(TAG, "upload: totalUploadSize = " + totalUploadSize);
//                    viewModel.upload(beans);
//                } else if (binding.getDeleting()) {
//                    viewModel.delete(beans);
//                }
//            }
//        });
//
//        viewModel.getUploadBean().observe(this, new Observer<MediaBean>() {
//            @Override
//            public void onChanged(MediaBean bean) {
//                Log.i(TAG, "upload: started bean = " + bean.getName());
//            }
//        });
//
//        viewModel.getUploadProgress().observe(this, new Observer<MediaViewModel.UploadProgress>() {
//            @Override
//            public void onChanged(MediaViewModel.UploadProgress uploadProgress) {
//                Log.i(TAG, "upload: " + uploadProgress.toString());
//                double temp = ((double)uploadProgress.progress / 100) * uploadProgress.bean.getSize() + uploadingSize;
//                double progress = (temp / totalUploadSize) * 100;
//                binding.pbUploading.setProgress((int)progress);
//                binding.tvProgress.setText(String.format(Locale.getDefault(), "%d%%", (int)progress));
//                Log.i(TAG, "upload: " + temp + "/" + totalUploadSize + "(" + progress +")");
//            }
//        });
//
//        viewModel.getUploadResult().observe(this, new Observer<MediaViewModel.UploadResult>() {
//            @Override
//            public void onChanged(MediaViewModel.UploadResult uploadResult) {
//                Log.i(TAG, "upload: " + uploadResult.toString());
//                uploadingSize += uploadResult.bean.getSize();
//                double progress = ((double)uploadingSize / totalUploadSize) * 100;
//                binding.pbUploading.setProgress((int)progress);
//                binding.tvProgress.setText(String.format(Locale.getDefault(), "%d%%", (int)progress));
//                Log.i(TAG, "upload: " + uploadingSize + "/" + totalUploadSize + "(" + progress +")");
//
//                if (uploadResult.isSuccess) {
//                    if (MediaBean.Type.IMAGE ==  uploadResult.bean.getType()) {
//                        WebManager.getInstance().getWebService()
//                                .uploadCapture(new CaptureBean(
//                                        PhoneNumberManager.getInstance().getPhoneNumber(),
//                                        Collections.singletonList(new CaptureBean.PhotoBean(
//                                                uploadResult.bean.getTime(),
//                                                uploadResult.bean.getLatitude(),
//                                                uploadResult.bean.getLongitude(),
//                                                uploadResult.bean.getUrl()))))
//                                .subscribeOn(Schedulers.io())
//                                .observeOn(AndroidSchedulers.mainThread())
//                                .subscribe(new DefaultObserver<BaseApiResult>() {
//                                    @Override
//                                    public void onNext(@NonNull BaseApiResult baseApiResult) {
//                                        Log.i(TAG, baseApiResult.toString());
//                                    }
//
//                                    @Override
//                                    public void onError(@NonNull Throwable e) {
//                                        Log.e(TAG, "onError: " + e.getMessage());
//                                    }
//
//                                    @Override
//                                    public void onComplete() {
//                                        Log.i(TAG, "onComplete");
//                                    }
//                                });
//                    } else if (MediaBean.Type.VIDEO ==  uploadResult.bean.getType()) {
//                        List<LocationRecorder.LocationBean> locations = LocationRecorder.parseLocations(
//                                LocationRecorder.getInstance().getDir() + File.separator + uploadResult.bean.getTitle() + ".json");
//
//                        if (null != locations) {
//                            List<RecordBean.Location> list = new ArrayList<>();
//
//                            for (LocationRecorder.LocationBean l : locations) {
//                                list.add(new RecordBean.Location(l.getTime(), l.getLatitude(), l.getLongitude()));
//                            }
//
//                            WebManager.getInstance().getWebService()
//                                    .uploadRecord(new RecordBean(
//                                            uploadResult.bean.getTime(),
//                                            uploadResult.bean.getUrl(),
//                                            PhoneNumberManager.getInstance().getPhoneNumber(),
//                                            list))
//                                    .subscribeOn(Schedulers.io())
//                                    .observeOn(AndroidSchedulers.mainThread())
//                                    .subscribe(new DefaultObserver<BaseApiResult>() {
//                                        @Override
//                                        public void onNext(@NonNull BaseApiResult baseApiResult) {
//                                            Log.i(TAG, baseApiResult.toString());
//                                        }
//
//                                        @Override
//                                        public void onError(@NonNull Throwable e) {
//                                            Log.e(TAG, "onError: " + e.getMessage());
//                                        }
//
//                                        @Override
//                                        public void onComplete() {
//                                            Log.i(TAG, "onComplete");
//                                        }
//                                    });
//                        } else {
//                            Log.e(TAG, "upload: parse locations failed!");
//                        }
//                    }
//                }
//
//                // TODO: workaround for checking the end condition
//                List<MediaBean> uploads = MediaProviderManager.getInstance().getUploads();
//                if (uploadingSize >= totalUploadSize || null == uploads || uploads.isEmpty()) {
//                    if (!binding.getEnable()) {
//                        binding.pbUploading.setProgress(100);
//                        binding.tvProgress.setText(new String("100%"));
//                        binding.setEnable(true);
//                        binding.setUploading(false);
//                        ToastUtil.show(MenuActivity.this, R.string.upload_completed);
//                    }
//                }
//            }
//        });
//
//        viewModel.getDeleteCount().observe(this, new Observer<Integer>() {
//            @Override
//            public void onChanged(Integer integer) {
//                Log.i(TAG, "delete: count = " + integer);
//                if (binding.getDeleting()) {
//                    ToastUtil.show(MenuActivity.this, R.string.delete_completed);
//                }
//
//                binding.setEnable(true);
//                binding.setDeleting(false);
//            }
//        });
    }

    @Override
    protected void onDestroy() {
        Log.i(TAG, "onDestroy");
        MediaProviderManager.getInstance().clearUpload();
        super.onDestroy();
    }

    @Override
    public void onClick(View v) {
        if (binding.btnBack.equals(v)) {
            finish();
        } else if (binding.btnPlayback.equals(v)) {
            Intent intent = new Intent(this, MediaActivity.class);
            intent.putExtra("type", MediaActivity.TYPE_PLAYBACK);
            startActivity(intent);
        } else if (binding.btnUpload.equals(v)) {
            Intent intent = new Intent(this, MediaActivity.class);
            intent.putExtra("type", MediaActivity.TYPE_UPLOAD);
            startActivity(intent);
//            if (!NetworkUtil.isNetworkAvailable(this)) {
//                ToastUtil.show(this, R.string.network_error);
//                return;
//            }
//
//            DialogUtil.showNormalDialog(this, "是否上传全部的行程和抓拍?", null,
//                    "确定", new View.OnClickListener() {
//                        @Override
//                        public void onClick(View v) {
//                            binding.pbUploading.setProgress(0);
//                            binding.setUploading(true);
//                            viewModel.queryAll(MediaStorageUtil.getDir(MenuActivity.this, MediaStorageUtil.Type.ROOT));
//                        }
//                    }, "取消", new View.OnClickListener() {
//                        @Override
//                        public void onClick(View v) {
//                        }
//                    });
        } else if (binding.btnDelete.equals(v)) {
            Intent intent = new Intent(this, MediaActivity.class);
            intent.putExtra("type", MediaActivity.TYPE_DELETE);
            startActivity(intent);
//            DialogUtil.showNormalDialog(this, "是否删除全部的行程和抓拍?", null,
//                    "确定", new View.OnClickListener() {
//                        @Override
//                        public void onClick(View v) {
//                            binding.setDeleting(true);
//                            viewModel.queryAll(MediaStorageUtil.getDir(MenuActivity.this, MediaStorageUtil.Type.ROOT));
//                        }
//                    }, "取消", new View.OnClickListener() {
//                        @Override
//                        public void onClick(View v) {
//                        }
//                    });
        } else if (binding.btnLogout.equals(v)) {
            DialogUtil.showNormalDialog(this, "是否退出登录?", null,
                    "确定", new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            PhoneNumberManager.getInstance().deletePhoneNumber();
                            MainActivity.isLogout = true;
                            MainActivity.instance.finish();
                            finish();
                            Intent intent = new Intent(MenuActivity.this, LoginActivity.class);
                            startActivity(intent);
                        }
                    }, "取消", new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                        }
                    });
        }
    }
}