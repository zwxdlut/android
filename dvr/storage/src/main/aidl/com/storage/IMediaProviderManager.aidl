// IMediaProviderManager.aidl
package com.storage;

// Declare any non-default types here with import statements
import com.storage.IMediaBean;
import com.storage.IMediaProviderCallback;
import com.storage.IUploadCallback;

interface IMediaProviderManager {
    /**
     * Media crud.
     */
    void setImageDir(String dir);
    void setVideoDir(String dir);
    void addMediaProviderCallback(IMediaProviderCallback callback);
    void removeMediaProviderCallback(IMediaProviderCallback callback);
    void insert(int type, String path, String url);
    void delete(in MediaBean bean);
    void deleteBatch(in List<MediaBean> beans);
    void update(in MediaBean bean);
    void updateBatch(in List<MediaBean> beans);
    void query(int type, String pathFilter, int order);
    void queryAll(String pathFilter, int order);
    void queryDateMap(int type, String pathFilter, int order);
    void queryDateMapAll(String pathFilter, int order);

    /**
     * Cloud media operation.
     */
    List<MediaBean> getUploads();
    void clearUpload();
    void addUploadCallback(IUploadCallback callback);
    void removeUploadCallback(IUploadCallback callback);
    void upload(in List<MediaBean> beans);
}


