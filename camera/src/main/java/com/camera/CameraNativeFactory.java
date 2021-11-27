package com.camera;

/**
 * This class creates native camera instance.
 */
public class CameraNativeFactory implements ICameraFactory {
    /**
     * Get a native camera instance.
     *
     * @return a native camera instance
     */
    @Override
    public ICamera getCamera() {
        return CameraNative.getInstance();
    }
}
