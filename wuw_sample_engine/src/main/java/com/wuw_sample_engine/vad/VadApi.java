package com.wuw_sample_engine.vad;

public class VadApi {
    private boolean started = false;

    private static class Builder {
        private static VadApi instance = new VadApi();
    }

    public static VadApi getInstance() {
        return Builder.instance;
    }

    public interface ResultCallback {
        int onResult(int status);
    }

    public int create(String resPath) {
        return native_create(resPath);
    }

    public int delete() {
        return native_delete();
    }

    public int start(ResultCallback callback) {
        if (started) {
            return 0;
        }

        started = true;

        return native_start(callback);
    }

    public int feed(byte[] buf, int size) {
        if (!started) {
            return -2;
        }

        return native_feed(buf, size);
    }

    public int stop() {
        started = false;
        return native_stop();
    }

    public int reset() {
        return native_reset();
    }

    public int setting(String config) {
        return native_setting(config);
    }

    public native int native_create(String resPath);

    public native int native_delete();

    public native int native_start(ResultCallback callback);

    public native int native_feed(byte[] buf, int size);

    public native int native_stop();

    public native int native_reset();

    public native int native_setting(String config);
}
