package com.camera;

/**
 * This interface creates native camera instance.
 */
public interface ICameraFactory {
    /**
     * Get a camera instance.
     *
     * @return a camera instance
     */
    ICamera getCamera();
}
