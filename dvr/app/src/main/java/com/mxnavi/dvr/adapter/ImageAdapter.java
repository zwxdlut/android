package com.mxnavi.dvr.adapter;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.databinding.DataBindingUtil;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.mxnavi.dvr.R;
import com.mxnavi.dvr.databinding.ImageItemBinding;
import com.mxnavi.dvr.utils.MediaSelector;
import com.mxnavi.dvr.utils.TransportStatus;
import com.storage.MediaBean;
import com.storage.util.Constant;
import com.storage.util.DateComparator;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

public class ImageAdapter extends RecyclerView.Adapter<ImageAdapter.ViewHolder> {
    private static final int GRID_COLUMNS = 4;
    private boolean isEnable = true;
    private Context context = null;
    private MediaSelector selector = null;
    private List<String> dates = new ArrayList<>();
    private List<Boolean> expands = new ArrayList<>();
    private List<MediaBean> beans = new ArrayList<>();
    private SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
    private Map<String, List<MediaBean>> beansMap = new TreeMap<>(new DateComparator(dateFormat, Constant.OrderType.DESCENDING));
    private Map<String, List<TransportStatus>> transportStatusMap = new TreeMap<>(new DateComparator(dateFormat, Constant.OrderType.DESCENDING));

    public ImageAdapter(Context context, MediaSelector selector) {
        this.context = context;
        this.selector = selector;
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        ImageItemBinding binding;

        public ViewHolder(View itemView) {
            super(itemView);
            binding = DataBindingUtil.bind(itemView);
        }
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new ViewHolder(LayoutInflater.from(parent.getContext()).inflate(R.layout.image_item, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull final ViewHolder holder, final int position) {
        String date = dates.get(position);
        final List<MediaBean> beans = beansMap.get(date);
        List<TransportStatus> transportStatuses = transportStatusMap.get(date);
        final ImageGridAdapter adapter = new ImageGridAdapter(context, selector);

        holder.binding.tvDate.setText(date);
        holder.binding.recyclerView.setLayoutManager(new GridLayoutManager(context, GRID_COLUMNS));
        holder.binding.recyclerView.setAdapter(adapter);
        adapter.setAll(this.beans);

        if (null != beans) {
            if (null == transportStatuses) {
                transportStatuses = new ArrayList<>(Collections.nCopies(beans.size(), new TransportStatus()));
                transportStatusMap.put(date, transportStatuses);
            }

            if (GRID_COLUMNS < beans.size()) {
                holder.binding.setAll(true);

                if (expands.get(position)) {
                    holder.binding.btnExpand.setActivated(true);
                    adapter.notifyDataChanged(beans, transportStatuses, isEnable);
                } else {
                    holder.binding.btnExpand.setActivated(false);
                    adapter.notifyDataChanged(beans.subList(0, GRID_COLUMNS), transportStatuses.subList(0, GRID_COLUMNS), isEnable);
                }
            } else {
                holder.binding.setAll(false);
                adapter.notifyDataChanged(beans, transportStatuses, isEnable);
            }
        }

        List<TransportStatus> finalTransportStatuses = transportStatuses;
        holder.binding.btnExpand.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (expands.get(position)) {
                    holder.binding.btnExpand.setActivated(false);

                    if (null != beans) {
                        adapter.notifyDataChanged(beans.subList(0, GRID_COLUMNS), finalTransportStatuses.subList(0, GRID_COLUMNS), isEnable);
                    }

                    expands.set(position, false);
                } else {
                    holder.binding.btnExpand.setActivated(true);
                    adapter.notifyDataChanged(beans, finalTransportStatuses, isEnable);
                    expands.set(position, true);
                }
            }
        });
    }

    @Override
    public int getItemCount() {
        return beansMap.size();
    }

    public void notifyDataSetChanged(List<MediaBean> beans, int order) {
        if (null == beans) {
            return;
        }

        this.beans = beans;
        beansMap = new TreeMap<>(new DateComparator(dateFormat, order));
        transportStatusMap = new TreeMap<>(new DateComparator(dateFormat, order));
        dates.clear();
        expands.clear();

        for (MediaBean bean : beans) {
            String date = dateFormat.format(new Date(bean.getTime()));

            if (beansMap.containsKey(date)) {
                beansMap.get(date).add(bean);
                transportStatusMap.get(date).add(new TransportStatus());
            } else {
                List<MediaBean> bs = new ArrayList<>();
                List<TransportStatus> ts = new ArrayList<>();

                bs.add(bean);
                beansMap.put(date, bs);
                ts.add(new TransportStatus());
                transportStatusMap.put(date, ts);
                dates.add(date);
                expands.add(false);
            }
        }

//        dates.addAll(beansMap.keySet());
//        expands.addAll(Collections.nCopies(dates.size(), false));
        notifyDataSetChanged();
    }

    public void notifySelectAll(boolean selectAll) {
        if (selectAll) {
            selector.getBeans().clear();

            for (Map.Entry<String, List<MediaBean>> entry : beansMap.entrySet()) {
                selector.getBeans().addAll(entry.getValue());
            }
        } else {
            selector.getBeans().clear();
        }

        notifyDataSetChanged();
    }

    public void notifyEnable(boolean isEnable) {
        this.isEnable = isEnable;
        notifyDataSetChanged();
    }

    public void notifyUpload(MediaBean bean, TransportStatus transportStatus) {
        for (Map.Entry<String, List<MediaBean>> entry : beansMap.entrySet()) {
            int index = entry.getValue().indexOf(bean);

            if (0 <= index) {
                String key = entry.getKey();
                List<TransportStatus> ts = transportStatusMap.get(key);
                int position = dates.indexOf(key);

                if (null == ts) {
                    ts = new ArrayList<>(Collections.nCopies(entry.getValue().size(), new TransportStatus()));
                    transportStatusMap.put(key, ts);
                }

                if (0 > position) {
                    dates.add(key);
                }

                ts.set(index, transportStatus);
                notifyItemChanged(position);
                break;
            }
        }
    }
}
