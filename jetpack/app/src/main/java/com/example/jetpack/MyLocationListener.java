package com.example.jetpack;

import android.util.Log;

import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.LifecycleObserver;
import androidx.lifecycle.Observer;
import androidx.lifecycle.OnLifecycleEvent;

import java.util.Timer;
import java.util.TimerTask;

public class MyLocationListener implements LifecycleObserver {
    private static final String TAG = "MyLocationListener";
    private Observer<Integer> observer = null;
    private Timer timer = null;

    public MyLocationListener(Lifecycle lifecycle, Observer<Integer> observer) {
        lifecycle.addObserver(this);
        this.observer = observer;
    }

    @OnLifecycleEvent(Lifecycle.Event.ON_CREATE)
    void create() {
        Log.d(TAG, "create");
    }

    @OnLifecycleEvent(Lifecycle.Event.ON_START)
    void start() {
        Log.d(TAG, "start");
        timer = new Timer();
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                observer.onChanged((int)(1 + Math.random() * (1000 -1 + 1)));
            }
        }, 0, 1000);
    }

    @OnLifecycleEvent(Lifecycle.Event.ON_RESUME)
    void resume() {
        Log.d(TAG, "resume");
    }

    @OnLifecycleEvent(Lifecycle.Event.ON_PAUSE)
    void pause() {
        Log.d(TAG, "pause");
    }

    @OnLifecycleEvent(Lifecycle.Event.ON_STOP)
    void stop() {
        Log.d(TAG, "stop");
        timer.cancel();
    }

    @OnLifecycleEvent(Lifecycle.Event.ON_DESTROY)
    void destroy() {
        Log.d(TAG, "destroy");
    }
}
