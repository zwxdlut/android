package com.camera;

/**
 * The ICameraFactory interface creates camera instance.
 */
public interface ICameraFactory {
    /**
     * Get the camera instance.
     *
     * @return the camera instance
     */
    ICamera getCamera();
}
