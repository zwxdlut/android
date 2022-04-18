package com.mxnavi.dvr.adapter;

import android.content.Context;
import android.content.Intent;
import android.os.Parcelable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CompoundButton;

import androidx.annotation.NonNull;
import androidx.databinding.DataBindingUtil;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.mxnavi.dvr.R;
import com.mxnavi.dvr.activity.ImagePlaybackActivity;
import com.mxnavi.dvr.databinding.ImageItemGridBinding;
import com.mxnavi.dvr.utils.MediaSelector;
import com.mxnavi.dvr.utils.TransportStatus;
import com.storage.MediaBean;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ImageGridAdapter extends RecyclerView.Adapter<ImageGridAdapter.ViewHolder> {
    private boolean isEnable = true;
    private Context context = null;
    private MediaSelector selector = new MediaSelector();
    private List<MediaBean> beans = new ArrayList<>();
    private List<MediaBean> allBeans = new ArrayList<>();
    private List<TransportStatus> transportStatuses = new ArrayList<>();

    static class ViewHolder extends RecyclerView.ViewHolder {
        ImageItemGridBinding binding;

        public ViewHolder(View itemView) {
            super(itemView);
            binding = DataBindingUtil.bind(itemView);
        }
    }

    public ImageGridAdapter(Context context, MediaSelector selector) {
        this.context = context;

        if (null != selector) {
            this.selector = selector;
        }
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        return new ViewHolder(LayoutInflater.from(parent.getContext()).inflate(R.layout.image_item_grid, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        final MediaBean bean = beans.get(position);
        TransportStatus transportStatus = transportStatuses.get(position);
        String loadPath = bean.getThumbnailPath();

        if (null == loadPath) {
            loadPath = bean.getPath();
        }

        holder.binding.tvName.setText(bean.getName().toUpperCase());
        holder.binding.cbSelect.setChecked(false);
        holder.binding.setSelectorShow(selector.isShow());
        holder.binding.setEnable(isEnable);
        //holder.binding.executePendingBindings();
        Glide.with(context).load(loadPath).into(holder.binding.ivPreview);

        if (TransportStatus.STARTED == transportStatus.status) {
            holder.binding.pbUploading.setVisibility(View.VISIBLE);
            holder.binding.tvProgress.setVisibility(View.VISIBLE);
        } else if (TransportStatus.PROGRESS == transportStatus.status) {
            holder.binding.pbUploading.setVisibility(View.VISIBLE);
            holder.binding.tvProgress.setVisibility(View.VISIBLE);
            holder.binding.tvProgress.setText(String.format("%d%%", transportStatus.progress));
        } else if (TransportStatus.COMPLETED == transportStatus.status) {
            holder.binding.pbUploading.setVisibility(View.GONE);
            holder.binding.tvProgress.setVisibility(View.GONE);
        }

        // update check box
        List<MediaBean> selectorBeans = selector.getBeans();
        if (null != selectorBeans) {
            for (MediaBean b : selectorBeans) {
                if (b.equals(bean)) {
                    holder.binding.cbSelect.setChecked(true);
                }
            }
        }

        holder.binding.cbSelect.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (!buttonView.isPressed()) {
                    return;
                }

                if (isChecked) {
                    selector.setNumber(selector.getNumber() + 1);
                    selector.getBeans().add(bean);
                } else {
                    selector.setNumber(selector.getNumber() - 1);
                    selector.getBeans().remove(bean);
                }
            }
        });

        holder.binding.ivPreview.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!isEnable) {
                    return;
                }

                if (selector.isShow()) {
                    if (holder.binding.cbSelect.isChecked()) {
                        selector.setNumber(selector.getNumber() - 1);
                        selector.getBeans().remove(bean);
                        holder.binding.cbSelect.setChecked(false);
                    } else {
                        selector.setNumber(selector.getNumber() + 1);
                        selector.getBeans().add(bean);
                        holder.binding.cbSelect.setChecked(true);
                    }
                } else {
                    Intent intent = new Intent(context, ImagePlaybackActivity.class);
                    intent.putExtra("index", allBeans.indexOf(bean));
                    intent.putParcelableArrayListExtra("beans", (ArrayList<? extends Parcelable>) allBeans);
                    context.startActivity(intent);
                }
            }
        });
    }

    @Override
    public int getItemCount() {
        return beans.size();
    }

    public void notifyDataChanged(List<MediaBean> beans, List<TransportStatus> transportStatuses, boolean isEnable) {
        if (null == beans) {
            return;
        }

        if (null == transportStatuses) {
            transportStatuses = new ArrayList<>(Collections.nCopies(beans.size(), new TransportStatus()));;
        }

        this.beans = beans;
        this.transportStatuses = transportStatuses;
        this.isEnable = isEnable;
        notifyDataSetChanged();
    }

    public void setAll(List<MediaBean> beans) {
        allBeans = beans;
    }
}
