package com.mxnavi.dvr.fragment;

import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.databinding.DataBindingUtil;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.mxnavi.dvr.R;
import com.mxnavi.dvr.activity.MediaActivity;
import com.mxnavi.dvr.adapter.VideoAdapter;
import com.mxnavi.dvr.databinding.FragmentMediaBinding;
import com.mxnavi.dvr.utils.MediaSelector;
import com.mxnavi.dvr.utils.MediaDirUtil;
import com.mxnavi.dvr.utils.PhoneNumberManager;
import com.mxnavi.dvr.utils.TransportStatus;
import com.mxnavi.dvr.viewmodel.MainViewModel;
import com.mxnavi.dvr.viewmodel.MediaViewModel;
import com.mxnavi.dvr.web.BaseApiResult;
import com.mxnavi.dvr.web.RecordBean;
import com.mxnavi.dvr.web.WebManager;
import com.storage.MediaBean;
import com.storage.util.Constant;
import com.storage.util.LocationRecorder;
import com.storage.util.NetworkUtil;
import com.storage.util.ToastUtil;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.annotations.NonNull;
import io.reactivex.observers.DefaultObserver;
import io.reactivex.schedulers.Schedulers;

public class VideoFragment extends Fragment {
    private static final String TAG = "DVR-" + VideoFragment.class.getSimpleName();
    private static final String PARAM = "type";
    private int order = Constant.OrderType.DESCENDING;
    private int type = MediaActivity.TYPE_PLAYBACK;
    private long totalUploadSize = 0;
    private long uploadingSize = 0;
    private boolean isEnable = true;
    private boolean isDeleting = false;
    private boolean isNeedRemove = false;
    private FragmentMediaBinding  binding = null;
    private VideoAdapter adapter = null;
    private MediaViewModel viewModel = null;
    private MediaSelector selector = new MediaSelector();
    private MutableLiveData<Boolean> operationCompleted = new MutableLiveData<>();

    public static VideoFragment newInstance(int param) {
        VideoFragment fragment = new VideoFragment();
        Bundle args = new Bundle();
        args.putInt(PARAM, param);
        fragment.setArguments(args);
        return fragment;
    }

    public VideoFragment() {
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.i(TAG, "onCreate");

        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.Q
                || (Build.VERSION.SDK_INT == Build.VERSION_CODES.Q
                && Environment.isExternalStorageLegacy() && MediaDirUtil.isRemovable)) {
            isNeedRemove = true;
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        Log.i(TAG, "onCreateView");

        Bundle args = getArguments();

        if (null != args) {
            type = getArguments().getInt(PARAM, MediaActivity.TYPE_PLAYBACK);
            selector.setShow(MediaActivity.TYPE_PLAYBACK != type);
        }

        adapter = new VideoAdapter(getContext(), selector);
        binding = DataBindingUtil.inflate(inflater, R.layout.fragment_media, container, false);
        binding.recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        binding.recyclerView.setAdapter(adapter);

        viewModel = new ViewModelProvider(this, new MediaViewModel.Factory(getContext())).get(MediaViewModel.class);
        viewModel.getQueriedVideos().observe(getViewLifecycleOwner(), new Observer<List<MediaBean>>() {
            @Override
            public void onChanged(List<MediaBean> beans) {
                // remove the current recording item
                if (isNeedRemove && MainViewModel.sRecording  && !beans.isEmpty()) {
                    if (Constant.OrderType.DESCENDING == order) {
                        beans.remove(0);
                    } else if (Constant.OrderType.ASCENDING == order) {
                        beans.remove(beans.size() - 1);
                    }
                }

                List<MediaBean> bs = beans;

                if (MediaActivity.TYPE_UPLOAD == type) {
                    bs = new ArrayList<>();

                    for (MediaBean b : beans) {
                        if (null == b.getUrl() || b.getUrl().isEmpty()) {
                            bs.add(b);
                        }
                    }
                }

                adapter.notifyDataSetChanged(bs);
                binding.pbLoading.setVisibility(View.GONE);
            }
        });

        viewModel.getVideoChange().observe(getViewLifecycleOwner(), new Observer<Uri>() {
            @Override
            public void onChanged(Uri uri) {
                if (isEnable) {
                    viewModel.query(MediaBean.Type.VIDEO, MediaDirUtil.getDir(getContext(), MediaDirUtil.Type.VIDEO), order);
                }
            }
        });

        viewModel.getUploadBean().observe(this, new Observer<MediaBean>() {
            @Override
            public void onChanged(MediaBean bean) {
                if (MediaBean.Type.VIDEO != bean.getType()) {
                    return;
                }

                Log.i(TAG, "upload: started bean = " + bean.getName());
                adapter.notifyUpload(bean, new TransportStatus(TransportStatus.STARTED, 0, false));
            }
        });

        viewModel.getUploadProgress().observe(this, new Observer<MediaViewModel.UploadProgress>() {
            @Override
            public void onChanged(MediaViewModel.UploadProgress uploadProgress) {
                if (MediaBean.Type.VIDEO != uploadProgress.bean.getType()) {
                    return;
                }

                Log.i(TAG, "upload: " + uploadProgress.toString());
                adapter.notifyUpload(uploadProgress.bean, new TransportStatus(TransportStatus.PROGRESS, uploadProgress.progress, false));
            }
        });

        viewModel.getUploadResult().observe(this, new Observer<MediaViewModel.UploadResult>() {
            @Override
            public void onChanged(MediaViewModel.UploadResult uploadResult) {
                if (MediaBean.Type.VIDEO != uploadResult.bean.getType()) {
                    return;
                }

                Log.i(TAG, "upload: " + uploadResult.toString());
                adapter.notifyUpload(uploadResult.bean, new TransportStatus(TransportStatus.COMPLETED, 100, uploadResult.isSuccess));
                uploadingSize += uploadResult.bean.getSize();

                if (uploadResult.isSuccess) {
                    List<LocationRecorder.LocationBean> locations = LocationRecorder.parseLocations(
                            LocationRecorder.getInstance().getDir() + File.separator + uploadResult.bean.getTitle() + ".json");

                    if (null != locations) {
                        List<RecordBean.Location> list = new ArrayList<>();

                        for (LocationRecorder.LocationBean l : locations) {
                            list.add(new RecordBean.Location(l.getTime(), l.getLatitude(), l.getLongitude()));
                        }

                        WebManager.getInstance().getWebService()
                                .uploadRecord(new RecordBean(
                                        uploadResult.bean.getTime(),
                                        uploadResult.bean.getUrl(),
                                        PhoneNumberManager.getInstance().getPhoneNumber(),
                                        list))
                                .subscribeOn(Schedulers.io())
                                .observeOn(AndroidSchedulers.mainThread())
                                .subscribe(new DefaultObserver<BaseApiResult>() {
                                    @Override
                                    public void onNext(@NonNull BaseApiResult baseApiResult) {
                                        Log.i(TAG, baseApiResult.toString());
                                        if (baseApiResult.getOk() == 1) {
                                            ToastUtil.show(getContext(), R.string.upload_success);
                                        } else {
                                            ToastUtil.show(getContext(), R.string.upload_failed);
                                        }
                                    }

                                    @Override
                                    public void onError(@NonNull Throwable e) {
                                        Log.e(TAG, "onError: " + e.getMessage());
                                        ToastUtil.show(getContext(), R.string.upload_failed);
                                    }

                                    @Override
                                    public void onComplete() {
                                        Log.i(TAG, "onComplete");
                                    }
                                });
                    } else {
                        ToastUtil.show(getContext(), R.string.upload_failed);
                    }
                } else {
                    ToastUtil.show(getContext(), R.string.upload_failed);
                }

                if (uploadingSize >= totalUploadSize) {
                    selector.getBeans().clear();
                    adapter.notifyEnable(true);
                    isEnable = true;
                    viewModel.query(MediaBean.Type.VIDEO, MediaDirUtil.getDir(getContext(), MediaDirUtil.Type.VIDEO), order);
                    operationCompleted.postValue(true);
                }
            }
        });

        viewModel.getDeleteCount().observe(this, new Observer<Integer>() {
            @Override
            public void onChanged(Integer integer) {
                if (!isDeleting) {
                    return;
                }

                Log.i(TAG, "delete: count = " + integer);
                selector.getBeans().clear();
                adapter.notifyEnable(true);
                binding.pbLoading.setVisibility(View.GONE);
                isDeleting = false;
                isEnable = true;
                ToastUtil.show(getContext(), R.string.delete_success);
                viewModel.query(MediaBean.Type.VIDEO, MediaDirUtil.getDir(getContext(), MediaDirUtil.Type.VIDEO), order);
                operationCompleted.postValue(true);
            }
        });

        viewModel.query(MediaBean.Type.VIDEO, MediaDirUtil.getDir(getContext(), MediaDirUtil.Type.VIDEO), order);

        return binding.getRoot();
        // Inflate the layout for this fragment
        //return inflater.inflate(R.layout.fragment_media, container, false);
    }

    @Override
    public void onDestroyView() {
        Log.i(TAG, "onDestroyView");
        viewModel.clearUpload();
        super.onDestroyView();
    }

    @Override
    public void onDestroy() {
        Log.i(TAG, "onDestroy");
        super.onDestroy();
    }

    public MutableLiveData<Boolean> getOperationCompleted() {
        return operationCompleted;
    }

    public void selectAll(boolean isSelectAll) {
        adapter.notifySelectAll(isSelectAll);
    }

    public int upload() {
        if (!NetworkUtil.isNetworkAvailable(Objects.requireNonNull(getContext()))) {
            ToastUtil.show(getContext(), R.string.network_error);
            return 0;
        }

        if (null == selector.getBeans() || selector.getBeans().isEmpty()) {
            return 0;
        }

        isEnable = false;
        adapter.notifyEnable(false);
        uploadingSize = 0;
        totalUploadSize = 0;

        for (MediaBean bean : selector.getBeans()) {
            Log.i(TAG, "upload:" + bean.getName());
        }

        for (MediaBean bean : selector.getBeans()) {
            totalUploadSize += bean.getSize();
        }

        viewModel.upload(selector.getBeans());

        return selector.getBeans().size();
    }

    public int delete() {
        List<MediaBean> beans = new ArrayList<>(selector.getBeans());

        if (beans.isEmpty()) {
            return 0;
        }

        isEnable = false;
        isDeleting = true;
        binding.pbLoading.setVisibility(View.VISIBLE);
        adapter.notifyEnable(false);
        viewModel.delete(beans);

        return beans.size();
    }

    public void sort(int order) {
        this.order = order;
        viewModel.query(MediaBean.Type.VIDEO, MediaDirUtil.getDir(getContext(), MediaDirUtil.Type.VIDEO), order);
    }
}