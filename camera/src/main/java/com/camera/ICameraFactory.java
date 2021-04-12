package com.camera;

import android.content.Context;

/**
 * The interface camera factory.
 */
public interface ICameraFactory {
    /**
     * Get the camera instance.
     *
     * @return the camera instance
     */
    public ICamera getCamera(Context context);
}
