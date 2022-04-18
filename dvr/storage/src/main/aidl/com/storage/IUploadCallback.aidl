// IUploadCallback.aidl
package com.storage;

// Declare any non-default types here with import statements
import com.storage.IMediaBean;

interface IUploadCallback {
    void onStarted(in MediaBean bean);
    void onProgress(in MediaBean bean, int progress);
    void onCompleted(in MediaBean bean, boolean isSuccess);
}