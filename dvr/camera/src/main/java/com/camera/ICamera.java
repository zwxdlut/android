package com.camera;

import android.content.Context;
import android.hardware.camera2.CameraDevice;
import android.location.Location;
import android.media.MediaRecorder;
import android.os.Environment;
import android.util.Size;
import android.view.Surface;

/**
 * This interface provides control and operation for the camera.
 */
public interface ICamera {
    /**
     * The result code returned by API.
     */
    class ResultCode {
        /**
         * Success.
         */
        public static final int SUCCESS = 0;

        /**
         * No permission.
         */
        public static final int NO_PERMISSION = -1;

        /**
         * Camera exception.
         */
        public static final int CAMERA_EXCEPTION = -2;

        /**
         * No camera device.
         */
        public static final int NO_CAMERA_DEVICE = -3;

        /**
         * No preview surface.
         */
        public static final int NO_PREVIEW_SURFACE = -4;

        /**
         * Failed because of recording.
         */
        public static final int FAILED_WHILE_RECORDING = -5;

        /**
         * Recorder error.
         */
        public static final int RECORDER_ERROR = -6;
    }

    /**
     * Camera callback.
     */
    interface ICameraCallback {
        /**
         * Camera state.
         */
        class State {
            /**
             * Camera closed.
             */
            public static final int CAMERA_CLOSED = 0;

            /**
             * Camera opened.
             */
            public static final int CAMERA_OPENED = 1;

            /**
             * Camera disconnected.
             */
            public static final int CAMERA_DISCONNECTED = 2;
        }

        /**
         * Camera error code.
         */
        class ErrorCode {
            /**
             * Camera in use.
             */
            public static final int CAMERA_IN_USE = CameraDevice.StateCallback.ERROR_CAMERA_IN_USE;

            /**
             * Max camera in use.
             */
            public static final int MAX_CAMERAS_IN_USE = CameraDevice.StateCallback.ERROR_MAX_CAMERAS_IN_USE;

            /**
             * Camera disabled.
             */
            public static final int CAMERA_DISABLED = CameraDevice.StateCallback.ERROR_CAMERA_DISABLED;

            /**
             * Camera device error.
             */
            public static final int CAMERA_DEVICE = CameraDevice.StateCallback.ERROR_CAMERA_DEVICE;

            /**
             * Camera service error.
             */
            public static final int CAMERA_SERVICE = CameraDevice.StateCallback.ERROR_CAMERA_SERVICE;
        }

        /**
         * Called when the camera state changed.
         *
         * @param cameraId the camera id
         * @param state the camera state:
         * <ul>
         * <li>{@link State#CAMERA_CLOSED}
         * <li>{@link State#CAMERA_OPENED}
         * <li>{@link State#CAMERA_DISCONNECTED}
         * <ul/>
         */
        void onState(String cameraId, int state);

        /**
         * Called when the camera error occurred.
         *
         * @param cameraId the camera id
         * @param error the error code:
         * <ul>
         * <li>{@link ErrorCode#CAMERA_IN_USE}
         * <li>{@link ErrorCode#MAX_CAMERAS_IN_USE}
         * <li>{@link ErrorCode#CAMERA_DISABLED}
         * <li>{@link ErrorCode#CAMERA_DEVICE}
         * <li>{@link ErrorCode#CAMERA_SERVICE}
         * <ul/>
         */
        void onError(String cameraId, int error);
    }

    /**
     * Camera capture callback.
     */
    interface ICaptureCallback {
        /**
         * Called when the capture started.
         *
         * @param cameraId the camera id
         * @param path the captured file full path
         */
        void onStarted(String cameraId, String path);

        /**
         * Called when the capture completed.
         *
         * @param cameraId the camera id
         * @param path the captured file full path
         */
        void onCompleted(String cameraId, String path);

        /**
         * Called when the capture failed.
         *
         * @param cameraId the camera id
         * @param path the captured file full path
         */
        void onFailed(String cameraId, String path);
    }

    /**
     * Camera record callback.
     */
    interface IRecordCallback {
        /**
         * Recorder error code.
         */
        class ErrorCode {
            /**
             * Unknown recorder error.
             */
            public static final int UNKNOWN = MediaRecorder.MEDIA_RECORDER_ERROR_UNKNOWN;

            /**
             * Recorder server died.
             */
            public static final int SERVER_DIED = MediaRecorder.MEDIA_ERROR_SERVER_DIED;
        }

        /**
         * Called when the record completed.
         *
         * @param cameraId the camera id
         * @param path the record file full path
         */
        void onCompleted(String cameraId, String path);

        /**
         * Called when error occurred while recording.
         *
         * @param cameraId the camera id
         * @param path the record file full path
         * @param what the type of error that has occurred:
         * <ul>
         * <li>{@link ErrorCode#UNKNOWN}
         * <li>{@link ErrorCode#SERVER_DIED}
         * <ul/>
         * @param extra an extra code, specific to the error type
         */
        void onError(String cameraId, String path, int what, int extra);
    }

    /**
     * Get the camera id list.
     *
     * @return the camera id list
     */
    String[] getCameraIdList();

    /**
     * Get the available preview sizes.
     *
     * @param cameraId the camera id
     * @return the available preview sizes
     */
    Size[] getAvailablePreviewSizes(String cameraId);

    /**
     * Get the available capture sizes.
     *
     * @param cameraId the camera id
     * @return the available capture sizes
     */
    Size[] getAvailableCaptureSizes(String cameraId);

    /**
     * Get the available record sizes.
     *
     * @param cameraId the camera id
     * @return the available record sizes
     */
    Size[] getAvailableRecordSizes(String cameraId);

    /**
     * Check if the camera is recording.
     *
     * @param cameraId the camera id
     * @return true while recording or false
     */
    boolean isRecording(String cameraId);

    /**
     * Set the camera callback.
     *
     * @param callback the camera callback
     */
    void setCameraCallback(ICameraCallback callback);

    /**
     * Set the preview surface.
     *
     * @param cameraId the camera id
     * @param surface the preview surface
     */
    void setPreviewSurface(String cameraId, Surface surface);

    /**
     * Set the capture relative storage directory.
     *
     * Relative path of this capture item within the storage device where it
     * is persisted. For example, an item stored at
     * {@code /storage/emulated/0/Pictures/IMG1024.JPG} would have a
     * path of {@code Pictures}.
     *
     * @param cameraId the camera id
     * @param dir the capture relative storage directory
     * @param isPublic Indicate if the directory is under public directory.
     *                 For public, the root directory is
     *                 {@link Environment#getExternalStorageDirectory()},
     *                 and for private, it is {@link Context#getExternalFilesDir(String)}.
     * @param isRemovable Indicate if the directory is under removable storage volume.
     * @return true if successful or false
     */
    boolean setCaptureRelativeDir(String cameraId, String dir, boolean isPublic, boolean isRemovable);

    /**
     * Set the capture size.
     *
     * @param cameraId the camera id
     * @param width the capture width
     * @param height the capture height
     */
    void setCaptureSize(String cameraId, int width, int height);

    /**
     * Set the capture callback.
     *
     * @param callback the capture callback
     */
    void setCaptureCallback(ICaptureCallback callback);

    /**
     * Set the record relative storage directory.
     *
     * Relative path of this record item within the storage device where it
     * is persisted. For example, an item stored at
     * {@code /storage/emulated/0/Movies/VID1024.MP4} would have a
     * path of {@code Movies}.
     *
     * @param cameraId the camera id
     * @param dir the record relative storage directory
     * @param isPublic Indicate if the directory is under public directory.
     *                 For public, the root directory is
     *                 {@link Environment#getExternalStorageDirectory()},
     *                 and for private, it is {@link Context#getExternalFilesDir(String)}.
     * @param isRemovable Indicate if the directory is under removable storage volume.
     * @return true if successful or false
     */
    boolean setRecordRelativeDir(String cameraId, String dir, boolean isPublic, boolean isRemovable);

    /**
     * Set the record video size.
     *
     * @param cameraId the camera id
     * @param width the record video width
     * @param height the record video height
     */
    void setRecordSize(String cameraId, int width, int height);

    /**
     * Set the audio mute.
     *
     * @param cameraId the camera id
     * @param isMute if the audio is mute
     */
    void setAudioMute(String cameraId, boolean isMute);

    /**
     * Set the video encoding bit rate.
     *
     * @param cameraId the camera id
     * @param bps the video encoding bit rate in bps
     */
    void setVideoEncodingRate(String cameraId, int bps);

    /**
     * Set the record callback.
     *
     * @param callback the record callback
     */
    void setRecordCallback(IRecordCallback callback);

    /**
     * Open the camera.
     *
     * @param cameraId the camera id
     * @return {@link ResultCode}
     */
    int open(String cameraId);

    /**
     * Close the camera.
     *
     * @param cameraId the camera id
     * @return {@link ResultCode}
     */
    int close(String cameraId);

    /**
     * Start preview.
     *
     * @param cameraId the camera id
     * @return {@link ResultCode}
     */
    int startPreview(String cameraId);

    /**
     * Stop preview.
     *
     * @param cameraId the camera id
     * @return {@link ResultCode}
     */
    int stopPreview(String cameraId);

    /**
     * Capture a picture.
     *
     * @param cameraId the camera id
     * @return {@link ResultCode}
     */
    int capture(String cameraId);

    /**
     * Capture a picture with file.
     *
     * @param cameraId the camera id
     * @param name the capture file name
     * @return {@link ResultCode}
     */
    int capture(String cameraId, String name);

    /**
     * Capture a picture with location.
     *
     * @param cameraId the camera id
     * @param location the location
     * @return {@link ResultCode}
     */
    int capture(String cameraId, Location location);

    /**
     * Capture a picture with file name and location.
     *
     * @param cameraId the camera id
     * @param name the capture file name
     * @param location the location
     * @return {@link ResultCode}
     */
    int capture(String cameraId, String name, Location location);

    /**
     * Start record.
     *
     * @param cameraId the camera id
     * @return {@link ResultCode}
     */
    int startRecord(String cameraId);

    /**
     * Start record with file name.
     *
     * @param cameraId the camera id
     * @param name the record file name
     * @return {@link ResultCode}
     */
    int startRecord(String cameraId, String name);

    /**
     * Start record with max duration.
     *
     * @param cameraId the camera id
     * @param duration the record max duration in ms (if zero or negative, disables the duration limit)
     * @return {@link ResultCode}
     */
    int startRecord(String cameraId, int duration);

    /**
     * Start record with file name and max duration.
     *
     * @param cameraId the camera id
     * @param name the record file name
     * @param duration the record max duration in ms (if zero or negative, disables the duration limit)
     * @return {@link ResultCode}
     */
    int startRecord(String cameraId, String name, int duration);

    /**
     * Stop record.
     *
     * @param cameraId the camera id
     * @return {@link ResultCode}
     */
    int stopRecord(String cameraId);
}
