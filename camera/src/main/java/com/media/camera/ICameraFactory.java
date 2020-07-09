package com.media.camera;

import android.content.Context;

/**
 * The interface camera factory.
 */
public interface ICameraFactory {
    /**
     * Get camera instance.
     *
     * @return The camera instance.
     */
    public ICamera getCamera(Context context);
}
