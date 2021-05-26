package com.camera;

/**
 * The CameraNativeFactory class creates the native camera instance.
 */
public class CameraNativeFactory implements ICameraFactory {
    @Override
    public ICamera getCamera() {
        return CameraNative.getInstance();
    }
}
