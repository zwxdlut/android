package com.storage.util;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import com.storage.R;

public class ToastUtil {
    /**
     * Toast 长时显示
     */
    private static final int LENGTH_LONG = 3500;

    /**
     * Toast 短时显示
     */
    private static final int LENGTH_SHORT = 2000;

    public static void show(Context context, String content) {
        show(context, content, LENGTH_LONG);
    }

    public static void show(Context context, int resId) {
        show(context, context.getString(resId), LENGTH_LONG);
    }

    public static void show(Context context, String content, int duration) {
        final Toast toast = Toast.makeText(context, content, duration);
        View view = LayoutInflater.from(context).inflate(R.layout.toast_util, null);

        ((TextView) view.findViewById(R.id.tv_toast)).setText(content);
        toast.setView(view);
        toast.setMargin(0f, 0f);
        toast.setDuration(duration);
        new Handler(Looper.getMainLooper()).post(new Runnable() {
            @Override
            public void run() {
                toast.show();
            }
        });
    }
}
