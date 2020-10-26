package com.voice.recognizer.vad;

public class VadApi {
    private static class Builder {
        private static VadApi instance = new VadApi();
    }

    public static VadApi getInstance() {
        return Builder.instance;
    }

    public interface ResultCallback {
        int onResult(int status);
    }

    public native int create(String resPath);

    public native int delete();

    public native int start(ResultCallback callback);

    public native int stop();

    public native int reset();

    public native int feed(byte buf[], int size);

    public native int setting(String config);
}
