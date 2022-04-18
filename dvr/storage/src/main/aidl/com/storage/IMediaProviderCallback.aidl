// IMediaProviderCallback.aidl
package com.storage;

// Declare any non-default types here with import statements
import com.storage.IMediaBean;

interface IMediaProviderCallback {
    void onInserted(in MediaBean bean);
    void onDeleted(int count);
    void onUpdated(int count);
    void onQueried(int type, in List<MediaBean> beans);
    void onQueriedAll(in List<MediaBean> beans);
    void onChanged(int type, in Uri uri);
}
