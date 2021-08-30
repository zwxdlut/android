package com.example.flutter_amap;

import android.util.Log;

import io.flutter.plugin.common.BinaryMessenger;
import io.flutter.plugin.common.EventChannel;

public class EventChannelPlugin implements EventChannel.StreamHandler {
    private static final String TAG = EventChannelPlugin.class.getSimpleName();
    private EventChannel.EventSink eventSink = null;

    static EventChannelPlugin registerWith(BinaryMessenger messenger, String name) {
        EventChannelPlugin plugin = new EventChannelPlugin(messenger);
        new EventChannel(messenger, name).setStreamHandler(plugin);
        return plugin;
    }

    private EventChannelPlugin(BinaryMessenger messenger) {}


    @Override
    public void onListen(Object arguments, EventChannel.EventSink events) {
        Log.e(TAG, "arguments：" + arguments.toString());
        Log.e(TAG, "events：" + events);
        eventSink = events;
    }

    @Override
    public void onCancel(Object arguments) {
        Log.e(TAG, "onCancel：" + arguments.toString());
        eventSink = null;
    }

    void send(Object params) {
        if (null != eventSink) {
            eventSink.success(params);
        }
    }

    void sendError(String str1, String str2, Object params) {
        if (null != eventSink) {
            eventSink.error(str1, str2, params);
        }
    }

    void cancel() {
        if (null != eventSink) {
            eventSink.endOfStream();
        }
    }
}
