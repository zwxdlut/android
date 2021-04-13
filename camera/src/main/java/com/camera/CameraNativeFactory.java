package com.camera;

public class CameraNativeFactory implements ICameraFactory {
    @Override
    public ICamera getCamera() {
        return CameraNative.getInstance();
    }
}
