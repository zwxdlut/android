package com.mxnavi.dvr.viewmodel;

import android.content.Context;
import android.net.Uri;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import com.storage.MediaBean;
import com.storage.MediaProviderManager;
import com.storage.util.LocationRecorder;

import java.io.File;
import java.util.List;

public class MediaViewModel extends ViewModel {
    private static final String TAG = "DVR-" + MediaViewModel.class.getSimpleName();
    private Context context = null;
    private MediaProviderManager mediaProviderManager = null;
    private MutableLiveData<Integer> deleteCount = new MutableLiveData<>();
    private MutableLiveData<List<MediaBean>> queriedImages = new MutableLiveData<>();
    private MutableLiveData<List<MediaBean>> queriedVideos = new MutableLiveData<>();
    private MutableLiveData<List<MediaBean>> queriedAll = new MutableLiveData<>();
    private MutableLiveData<Uri> imageChange = new MutableLiveData<>();
    private MutableLiveData<Uri> videoChange = new MutableLiveData<>();
    private MutableLiveData<MediaBean> uploadBean = new MutableLiveData<>();
    private MutableLiveData<UploadProgress> uploadProgress = new MutableLiveData<>();
    private MutableLiveData<UploadResult> uploadResult = new MutableLiveData<>();

    private MediaProviderManager.MediaProviderCallback mediaProviderCallback = new MediaProviderManager.MediaProviderCallback() {
        @Override
        public void onDeleted(int count) {
            deleteCount.postValue(count);
        }

        @Override
        public void onQueried(int type, List<MediaBean> beans) {
            if (MediaBean.Type.IMAGE == type) {
                queriedImages.postValue(beans);
            } else if (MediaBean.Type.VIDEO == type) {
                queriedVideos.postValue(beans);
            }
        }

        @Override
        public void onQueriedAll(List<MediaBean> beans) {
            queriedAll.postValue(beans);
        }

        @Override
        public void onChanged(int type, Uri uri) {
            if (MediaBean.Type.IMAGE == type) {
                imageChange.postValue(uri);
            } else if (MediaBean.Type.VIDEO == type) {
                videoChange.postValue(uri);
            }
        }
    };

    private MediaProviderManager.UploadCallback uploadCallback = new MediaProviderManager.UploadCallback() {
        @Override
        public void onStarted(MediaBean bean) {
            uploadBean.postValue(bean);
        }

        @Override
        public void onProgress(MediaBean bean, int progress) {
            uploadProgress.postValue(new UploadProgress(bean, progress));
        }

        @Override
        public void onCompleted(MediaBean bean, boolean isSuccess) {
            uploadResult.postValue(new UploadResult(bean, isSuccess));
        }
    };

    public static class UploadProgress {
        public MediaBean bean;
        public int progress;

        UploadProgress(MediaBean bean, int progress) {
            this.bean = bean;
            this.progress = progress;
        }

        @Override
        public String toString() {
            return "UploadProgress{" +
                    "bean=" + bean.getName() +
                    ", progress=" + progress +
                    '}';
        }
    }

    public static class UploadResult {
        public MediaBean bean;
        public boolean isSuccess;

        UploadResult(MediaBean bean, boolean isSuccess) {
            this.bean = bean;
            this.isSuccess = isSuccess;
        }

        @Override
        public String toString() {
            return "UploadResult{" +
                    "bean=" + bean.getName() +
                    ", isSuccess=" + isSuccess +
                    '}';
        }
    }

    public static class Factory implements ViewModelProvider.Factory {
        private Context context;

        public Factory(Context context) {
            this.context = context;
        }

        @NonNull
        @Override
        public <T extends ViewModel> T create(@NonNull Class<T> modelClass) {
            return (T) new MediaViewModel(context);
        }
    }

    public MediaViewModel(Context context) {
        this.context = context;
        mediaProviderManager = MediaProviderManager.getInstance();
        mediaProviderManager.addMediaProviderCallback(mediaProviderCallback);
        mediaProviderManager.addUploadCallback(uploadCallback);
    }

    @Override
    protected void onCleared() {
        mediaProviderManager.removeMediaProviderCallback(mediaProviderCallback);
        mediaProviderManager.removeUploadCallback(uploadCallback);
        super.onCleared();
    }

    public MutableLiveData<Integer> getDeleteCount() {
        return deleteCount;
    }

    public MutableLiveData<List<MediaBean>> getQueriedImages() {
        return queriedImages;
    }

    public MutableLiveData<List<MediaBean>> getQueriedVideos() {
        return queriedVideos;
    }

    public MutableLiveData<List<MediaBean>> getQueriedAll() {
        return queriedAll;
    }

    public MutableLiveData<Uri> getImageChange() {
        return imageChange;
    }

    public MutableLiveData<Uri> getVideoChange() {
        return videoChange;
    }

    public MutableLiveData<MediaBean> getUploadBean() {
        return uploadBean;
    }

    public MutableLiveData<UploadProgress> getUploadProgress() {
        return uploadProgress;
    }

    public MutableLiveData<UploadResult> getUploadResult() {
        return uploadResult;
    }

    public void delete(List<MediaBean> beans) {
        if (null != beans) {
            for (MediaBean bean : beans) {
                if (MediaBean.Type.VIDEO == bean.getType()) {
                    File file = new File(
                            LocationRecorder.getInstance().getDir(),
                            bean.getTitle() + ".json");

                    if (file.exists() && file.isFile() && !file.delete()) {
                        Log.e(TAG, "delete: failed, file = " + file.getAbsolutePath());
                    }
                }
            }
        }

        mediaProviderManager.delete(beans);
    }

    public void query(int type, String pathFilter, int order) {
        mediaProviderManager.query(type, pathFilter, order);
    }

    public void queryAll(String pathFilter, int order) {
        mediaProviderManager.queryAll(pathFilter, order);
    }

    public void upload(List<MediaBean> beans) {
        mediaProviderManager.upload(beans);
    }

    public void clearUpload() {
        mediaProviderManager.clearUpload();
    }

    public List<MediaBean> getUploads() {
        return mediaProviderManager.getUploads();
    }
}
