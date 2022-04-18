package com.mxnavi.dvr.utils;

import android.os.Parcel;
import android.os.Parcelable;

public class TransportStatus implements Parcelable{
    public static final int STARTED = 0;
    public static final int PROGRESS = 1;
    public static final int COMPLETED = 2;

    public int status = COMPLETED;
    public int progress;
    public boolean isSuccess;

    public TransportStatus() {
    }
    
    public TransportStatus(int status, int progress, boolean isSuccess) {
        this.status = status;
        this.progress = progress;
        this.isSuccess = isSuccess;
    }

    protected TransportStatus(Parcel in) {
        status = in.readInt();
        progress = in.readInt();
        isSuccess = (0 != in.readByte());
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(status);
        dest.writeInt(progress);
        dest.writeByte((byte) (isSuccess ? 1 : 0));
    }

    public static final Parcelable.Creator<TransportStatus> CREATOR = new Parcelable.Creator<TransportStatus>() {
        @Override
        public TransportStatus createFromParcel(Parcel in) {
            return new TransportStatus(in);
        }

        @Override
        public TransportStatus[] newArray(int size) {
            return new TransportStatus[size];
        }
    };

    @Override
    public String toString() {
        return "TransportStatus{" +
                "status=" + status +
                ", progress=" + progress +
                ", isSuccess=" + isSuccess +
                '}';
    }
}