package com.camera;

import android.content.Context;

public class CameraNativeFactory implements ICameraFactory {
    @Override
    public ICamera getCamera(Context context) {
        return CameraNative.getInstance(context);
    }
}
