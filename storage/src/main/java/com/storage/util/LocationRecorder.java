/*
 * *
 * Copyright (c) 2017 Baidu, Inc. All Rights Reserved.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package com.storage.util;

import android.app.Application;
import android.content.Context;
import android.os.Parcel;
import android.os.Parcelable;
import android.util.Log;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONException;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.reflect.InvocationTargetException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.LinkedBlockingQueue;

public class LocationRecorder {
    private static final String TAG = "DVR-" + LocationRecorder.class.getSimpleName();
    private Context context = null;
    private File directory = null;
    private RecordThread thread = null;
    private SimpleDateFormat dateFormat = new SimpleDateFormat("yyyyMMddHHmmss", Locale.CHINESE);

    public static List<LocationBean> parseLocations(String path) {
        File file = new File(path);
        if (!file.exists() || !file.isFile()) {
            return null;
        }

        try {
            String line;
            StringBuilder content = new StringBuilder("");
            FileInputStream fis = new FileInputStream(file);
            InputStreamReader isReader = new InputStreamReader(fis);
            BufferedReader bufReader = new BufferedReader(isReader);

            while (( line = bufReader.readLine()) != null) {
                content.append(line).append("\n");
            }

            fis.close();

            return JSON.parseArray(content.toString(), LocationBean.class);
        } catch (IOException | JSONException e) {
            e.printStackTrace();
        }

        return null;
    }

    public static class LocationBean implements Parcelable {
        private long time;
        private double latitude;
        private double longitude;
        //private double altitude;
        private String formatLocation;

        public LocationBean() {
        }

        public LocationBean(long time, double latitude, double longitude, /*double altitude, */String formatLocation) {
            this.time = time;
            this.latitude = latitude;
            this.longitude = longitude;
            this.formatLocation = formatLocation;
            //this.altitude = altitude;
        }

        protected LocationBean(Parcel in) {
            time = in.readLong();
            latitude = in.readDouble();
            longitude = in.readDouble();
            formatLocation = in.readString();
            //altitude = in.readDouble();
        }

        @Override
        public int describeContents() {
            return 0;
        }

        @Override
        public void writeToParcel(Parcel dest, int flags) {
            dest.writeLong(time);
            dest.writeDouble(latitude);
            dest.writeDouble(longitude);
            //dest.writeDouble(altitude);
            dest.writeString(formatLocation);
        }

        public static final Creator<LocationBean> CREATOR = new Creator<LocationBean>() {
            @Override
            public LocationBean createFromParcel(Parcel in) {
                return new LocationBean(in);
            }

            @Override
            public LocationBean[] newArray(int size) {
                return new LocationBean[size];
            }
        };

        @Override
        public String toString() {
            return "TimeLocationBean{" +
                    "time=" + time +
                    ", latitude=" + latitude +
                    ", longitude=" + longitude +
                    //", altitude=" + altitude +
                    ", formatLocation=" + formatLocation +
                    '}';
        }

        public long getTime() {
            return time;
        }

        public void setTime(long time) {
            this.time = time;
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

//        public double getAltitude() {
//            return altitude;
//        }
//
//        public void setAltitude(double altitude) {
//            this.altitude = altitude;
//        }


        public String getFormatLocation() {
            return formatLocation;
        }

        public void setFormatLocation(String formatLocation) {
            this.formatLocation = formatLocation;
        }
    }

    private static class RecordThread extends Thread {
        private boolean done = false;
        private File file = null;
        private LinkedBlockingQueue<LocationBean> queue = new LinkedBlockingQueue<>();

        public RecordThread(File file) {
            super("RecordThread");
            Log.i(TAG, "RecordThread: file = " + file);
            this.file = file;
        }

        @Override
        public void run() {
            Log.i(TAG, "RecordThread.run: +");

            FileOutputStream fos = null;
            boolean isFirst = true;

            try {
                fos = new FileOutputStream(file);
                fos.write(new String("[").getBytes());
                fos.flush();
            } catch (IOException e) {
                e.printStackTrace();
                done = true;
                return;
            }

            while (!done) {
                LocationBean bean = queue.poll();

                while (null != bean) {
                    //Log.d(TAG, "RecordThread.run: poll bean = " + bean.toString());

                    // save time and location to file
                    String json = JSON.toJSONString(bean);
                    //Log.d(TAG, "RecordThread.run: json = " + json);

                    try {
                        if (isFirst) {
                            isFirst = false;
                        } else {
                            fos.write(new String(",").getBytes());
                        }

                        fos.write(json.getBytes());
                        fos.flush();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }

                    bean = queue.poll();
                }
            }

            try {
                fos.write(new String("]").getBytes());
                fos.flush();
                fos.close();
            } catch (IOException e) {
                e.printStackTrace();
            }

            Log.i(TAG, "RecordThread.run: -");
        }

        public void terminate() {
            Log.i(TAG, "RecordThread.terminate: done = " + done);
            done = true;
        }

        public void feed(LocationBean bean) {
            //Log.d(TAG, "RecordThread.feed: bean =  " + bean + ", done = " + done);

            if (done) {
                return;
            }

            if (!queue.offer(bean)) {
                Log.e(TAG, "RecordThread.feed: offer the location into the queue failed!");
            }
        }
    }

    private static class Builder {
        private static final LocationRecorder instance = new LocationRecorder();
    }

    public static LocationRecorder getInstance() {
        return LocationRecorder.Builder.instance;
    }

    public LocationRecorder() {
        try {
            //Application application = (Application) Class.forName("android.app.ActivityThread").getMethod("currentApplication").invoke(null, (Object[]) null);
            Application application = (Application) Class.forName("android.app.AppGlobals").getMethod("getInitialApplication").invoke(null, (Object[]) null);

            if (null != application) {
                context = application.getApplicationContext();
            } else {
                throw new NullPointerException();
            }
        } catch (IllegalAccessException | InvocationTargetException | NoSuchMethodException | ClassNotFoundException e) {
            e.printStackTrace();
        }

        directory = new File(context.getExternalFilesDir(null), "Locations");
        if (!directory.exists()) {
            if (directory.mkdirs()) {
                Log.e(TAG, "LocationRecorder: make directory " + directory.getPath());
            } else {
                Log.e(TAG, "LocationRecorder: make directory " + directory.getPath() + " failed!");
            }
        }
    }

    public boolean setDir(String dir) {
        Log.i(TAG, "setDir: dir = " + dir);

        if (null == dir) {
            return false;
        }

        File path = new File(dir);

        if (!path.exists()) {
            if (!path.mkdirs()) {
                Log.e(TAG, "setDirectory: make directory " + dir);
            } else {
                Log.e(TAG, "setDirectory: make directory " + dir + " failed!");
                return false;
            }
        }

        directory = path;

        return true;
    }

    public String getDir() {
        return directory.getAbsolutePath();
    }

    public void start() {
        start(dateFormat.format(new Date()));
    }

    public void start(String name) {
        Log.i(TAG, "start: name = " + name);
        thread = new RecordThread(new File(directory, name));
        thread.start();
    }

    public void stop() {
        Log.i(TAG, "stop: thread = " + thread);

        if (null != thread) {
            thread.terminate();
        }
    }

    public void feed(LocationBean bean) {
        if (null != thread) {
            thread.feed(bean);
        }
    }
}