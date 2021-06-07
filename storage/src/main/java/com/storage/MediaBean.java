package com.storage;

import android.net.Uri;
import android.os.Parcel;
import android.os.Parcelable;

public class MediaBean implements Parcelable {
    public static class Type {
        public static final int UNKNOWN = -1;
        public static final int IMAGE = 0;
        public static final int VIDEO = 1;
    }

    private Uri uri;
    private String path;
    private String name;
    private String title;
    private String thumbnailPath;
    private String url;
    private int type;
    private int width;
    private int height;
    private int size;
    private long time;
    private long duration;
    private double latitude;
    private double longitude;

    public MediaBean() {
    }

    protected MediaBean(Parcel in) {
        uri = in.readParcelable(Uri.class.getClassLoader());
        path = in.readString();
        name = in.readString();
        title = in.readString();
        thumbnailPath = in.readString();
        url = in.readString();
        type = in.readInt();
        width = in.readInt();
        height = in.readInt();
        size = in.readInt();
        time = in.readLong();
        duration = in.readLong();
        latitude = in.readDouble();
        longitude = in.readDouble();
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeParcelable(uri, flags);
        dest.writeString(path);
        dest.writeString(name);
        dest.writeString(title);
        dest.writeString(thumbnailPath);
        dest.writeString(url);
        dest.writeInt(type);
        dest.writeInt(width);
        dest.writeInt(height);
        dest.writeInt(size);
        dest.writeLong(time);
        dest.writeLong(duration);
        dest.writeDouble(latitude);
        dest.writeDouble(longitude);
    }

    public static final Creator<MediaBean> CREATOR = new Creator<MediaBean>() {
        @Override
        public MediaBean createFromParcel(Parcel in) {
            return new MediaBean(in);
        }

        @Override
        public MediaBean[] newArray(int size) {
            return new MediaBean[size];
        }
    };

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }

        if (o instanceof MediaBean) {
            MediaBean bean = (MediaBean)o;

            if (path.equals(bean.getPath())) {
                return true;
            } else {
                return name.equals(bean.getName());
            }
        }

        return false;
    }

    @Override
    public int hashCode() {
        return super.hashCode();
    }

    @Override
    public String toString() {
        return "MediaBean{" +
                "uri=" + uri +
                ", path='" + path + '\'' +
                ", name='" + name + '\'' +
                ", title='" + title + '\'' +
                ", thumbnailPath='" + thumbnailPath + '\'' +
                ", url='" + url + '\'' +
                ", type=" + type +
                ", width=" + width +
                ", height=" + height +
                ", size=" + size +
                ", time=" + time +
                ", duration=" + duration +
                ", latitude=" + latitude +
                ", longitude=" + longitude +
                '}';
    }

    public Uri getUri() {
        return uri;
    }

    public void setUri(Uri uri) {
        this.uri = uri;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getThumbnailPath() {
        return thumbnailPath;
    }

    public void setThumbnailPath(String thumbnailPath) {
        this.thumbnailPath = thumbnailPath;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public int getType() {
        return type;
    }

    public void setType(int type) {
        this.type = type;
    }

    public int getWidth() {
        return width;
    }

    public void setWidth(int width) {
        this.width = width;
    }

    public int getHeight() {
        return height;
    }

    public void setHeight(int height) {
        this.height = height;
    }

    public int getSize() {
        return size;
    }

    public void setSize(int size) {
        this.size = size;
    }

    public long getTime() {
        return time;
    }

    public void setTime(long time) {
        this.time = time;
    }

    public long getDuration() {
        return duration;
    }

    public void setDuration(long duration) {
        this.duration = duration;
    }

    public double getLatitude() {
        return latitude;
    }

    public void setLatitude(double latitude) {
        this.latitude = latitude;
    }

    public double getLongitude() {
        return longitude;
    }

    public void setLongitude(double longitude) {
        this.longitude = longitude;
    }
}
