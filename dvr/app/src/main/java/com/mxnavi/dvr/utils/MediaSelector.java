package com.mxnavi.dvr.utils;

import android.os.Parcel;
import android.os.Parcelable;

import com.storage.MediaBean;

import java.util.ArrayList;
import java.util.List;

public class MediaSelector implements Parcelable {
    private int number = 0;
    private boolean isShow = false;
    private List<MediaBean> beans = new ArrayList<>();

    public MediaSelector() {
    }

    protected MediaSelector(Parcel in) {
        number = in.readInt();
        isShow = (0 != in.readByte());
        beans = in.createTypedArrayList(MediaBean.CREATOR);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(number);
        dest.writeByte((byte) (isShow ? 1 : 0));
        dest.writeTypedList(beans);
    }

    public static final Creator<MediaSelector> CREATOR = new Creator<MediaSelector>() {
        @Override
        public MediaSelector createFromParcel(Parcel in) {
            return new MediaSelector(in);
        }

        @Override
        public MediaSelector[] newArray(int size) {
            return new MediaSelector[size];
        }
    };

    public int getNumber() {
        return number;
    }

    public void setNumber(int number) {
        this.number = number;
    }

    public boolean isShow() {
        return isShow;
    }

    public void setShow(boolean isShow) {
        this.isShow = isShow;
    }

    public List<MediaBean> getBeans() {
        return beans;
    }

    public void setBeans(List<MediaBean> beans) {
        this.beans = beans;
    }
}
