package com.mxnavi.dvr.adapter;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.os.Parcelable;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CompoundButton;

import androidx.annotation.NonNull;
import androidx.databinding.DataBindingUtil;
import androidx.recyclerview.widget.RecyclerView;

import com.amap.api.services.core.LatLonPoint;
import com.amap.api.services.geocoder.GeocodeResult;
import com.amap.api.services.geocoder.GeocodeSearch;
import com.amap.api.services.geocoder.RegeocodeAddress;
import com.amap.api.services.geocoder.RegeocodeQuery;
import com.amap.api.services.geocoder.RegeocodeResult;
import com.amap.api.services.geocoder.StreetNumber;
import com.mxnavi.dvr.R;
import com.mxnavi.dvr.activity.VideoPlaybackActivity;
import com.mxnavi.dvr.databinding.VideoItemBinding;
import com.mxnavi.dvr.utils.MediaSelector;
import com.mxnavi.dvr.utils.TransportStatus;
import com.storage.MediaBean;
import com.storage.util.LocationRecorder;

import java.io.File;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public class VideoAdapter extends  RecyclerView.Adapter<VideoAdapter.ViewHolder> {
    private static final String TAG = "DVR-" + VideoAdapter.class.getSimpleName();
    private boolean isEnable = true;
    private Context context = null;
    private MediaSelector selector = new MediaSelector();
    private List<MediaBean> beans = new ArrayList<>();
    private List<TransportStatus> transportStatuses = new ArrayList<>();
    private SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd  HH:mm:ss", Locale.getDefault());
    private SimpleDateFormat nameFormat = new SimpleDateFormat("yyyyMMddHHmmss", Locale.getDefault());

    public VideoAdapter(Context context, MediaSelector selector) {
        this.context = context;

        if (null != selector) {
            this.selector = selector;
        }
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        VideoItemBinding binding;

        public ViewHolder(View itemView) {
            super(itemView);
            binding = DataBindingUtil.bind(itemView);
        }
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new ViewHolder(LayoutInflater.from(parent.getContext()).inflate(R.layout.video_item, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        final MediaBean bean = beans.get(position);
        TransportStatus transportStatus = transportStatuses.get(position);
        List<LocationRecorder.LocationBean> locations = LocationRecorder.parseLocations(
                LocationRecorder.getInstance().getDir() + File.separator + bean.getTitle() + ".json");

        // workaround
        try {
            holder.binding.tvDate.setText(dateFormat.format(nameFormat.parse(bean.getName())));
        } catch (ParseException e) {
            e.printStackTrace();
        }

        //holder.binding.tvDate.setText(dateFormat.format(new Date(bean.getTime())));
        holder.binding.cbSelect.setChecked(false);
        holder.binding.setSelectorShow(selector.isShow());
        holder.binding.setEnable(isEnable);

        if (TransportStatus.STARTED == transportStatus.status) {
            holder.binding.pbUploading.setVisibility(View.VISIBLE);
            holder.binding.tvProgress.setVisibility(View.VISIBLE);
        } else if ( TransportStatus.PROGRESS == transportStatus.status) {
            holder.binding.pbUploading.setVisibility(View.VISIBLE);
            holder.binding.tvProgress.setVisibility(View.VISIBLE);
            holder.binding.tvProgress.setText(String.format("%d%%", transportStatus.progress));
        } else if (TransportStatus.COMPLETED == transportStatus.status) {
            holder.binding.pbUploading.setVisibility(View.GONE);
            holder.binding.tvProgress.setVisibility(View.GONE);
        }

        if (null != locations && !locations.isEmpty()) {
            String formatLocation = null;

            for (LocationRecorder.LocationBean l : locations) {
                formatLocation = l.getFormatLocation();

                if (null != formatLocation && !formatLocation.isEmpty()) {
                    holder.binding.tvLocation.setText(formatLocation);
                    break;
                }
            }

            if (null == formatLocation || formatLocation.isEmpty()) {
                GeocodeSearch  geocodeSearch = new GeocodeSearch(context);
                geocodeSearch.setOnGeocodeSearchListener(new GeocodeSearch.OnGeocodeSearchListener() {
                    @SuppressLint("SetTextI18n")
                    @Override
                    public void onRegeocodeSearched(RegeocodeResult regeocodeResult, int i) {
                        if (1000 == i && null != regeocodeResult) {
                            RegeocodeAddress address = regeocodeResult.getRegeocodeAddress();
                            if (null != address) {
                                StreetNumber street = address.getStreetNumber();
                                holder.binding.tvLocation.setText("" + address.getCity() + address.getDistrict() + (null != street ? street.getStreet() : ""));
                                holder.binding.tvLocation.invalidate();
                            }
                        } else {
                            Log.e(TAG, "onRegeocodeSearched: i = " + i + ", regeocodeResult = " + regeocodeResult);
                        }
                    }

                    @Override
                    public void onGeocodeSearched(GeocodeResult geocodeResult, int i) {
                    }
                });

                LatLonPoint point= new LatLonPoint(locations.get(0).getLatitude(), locations.get(0).getLongitude());
                RegeocodeQuery query = new RegeocodeQuery(point, 200, GeocodeSearch.AMAP);
                geocodeSearch.getFromLocationAsyn(query);
            }
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

        holder.binding.rlPlayback.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(context, VideoPlaybackActivity.class);
                intent.putExtra("index", position);
                intent.putParcelableArrayListExtra("beans", (ArrayList<? extends Parcelable>) beans);
                context.startActivity(intent);
            }
        });

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

        holder.binding.rlItem.setOnClickListener(new View.OnClickListener() {
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
                    Intent intent = new Intent(context, VideoPlaybackActivity.class);
                    intent.putExtra("index", position);
                    intent.putParcelableArrayListExtra("beans", (ArrayList<? extends Parcelable>) beans);
                    context.startActivity(intent);
                }
            }
        });
    }

    @Override
    public int getItemCount() {
        return beans.size();
    }

    public void notifyDataSetChanged(List<MediaBean> beans) {
        if (null == beans) {
            return;
        }

        this.beans = beans;
        transportStatuses.clear();
        transportStatuses.addAll(Collections.nCopies(beans.size(), new TransportStatus()));
        notifyDataSetChanged();
    }

    public void notifySelectAll(boolean selectAll) {
        if (selectAll) {
            selector.getBeans().clear();
            selector.getBeans().addAll(beans);
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
        int index = beans.indexOf(bean);

        if (0 <= index) {
            transportStatuses.set(index, transportStatus);
            notifyItemChanged(index);
        }
    }
}
