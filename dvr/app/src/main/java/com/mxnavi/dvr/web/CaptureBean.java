package com.mxnavi.dvr.web;

import java.util.List;

public class CaptureBean {
    public static class PhotoBean {
        public long time;
        public double latitude;
        public double longitude;
        public String url;

        public PhotoBean() {
        }

        public PhotoBean(long time, double latitude, double longitude, String url) {
            this.time = time;
            this.latitude = latitude;
            this.longitude = longitude;
            this.url = url;
        }
    }

    public String phone;
    public List<PhotoBean> photos;

    public CaptureBean() {
    }

    public CaptureBean(String phone, List<PhotoBean> photos) {
        this.phone = phone;
        this.photos = photos;
    }
}
