package com.mxnavi.dvr.web;

import java.util.List;

public class RecordBean {
    public long time;
    public String video_url;
    public String phone;
    public List<Location> locations;

    public static class Location {
        public long time;
        public double latitude;
        public double longitude;

        public Location(long time, double latitude, double longitude) {
            this.time = time;
            this.latitude = latitude;
            this.longitude = longitude;
        }
    }

    public RecordBean(long time, String url, String phone, List<Location> locations) {
        this.time = time;
        this.video_url = url;
        this.phone = phone;
        this.locations = locations;
    }
}
