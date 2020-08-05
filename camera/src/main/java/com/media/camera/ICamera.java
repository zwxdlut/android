package com.media.camera;

import android.hardware.camera2.CameraDevice;
import android.media.MediaRecorder;
import android.util.Size;
import android.view.Surface;

/**
 * The interface camera.
 */
public interface ICamera {
    /**
     * The result code returned by API.
     */
    public interface ResultCode {
        /**
         * The constant SUCCESS.
         */
        public static final int SUCCESS = 0;

        /**
         * The constant NO_CAMERA_PERMISSION.
         */
        public static final int NO_CAMERA_PERMISSION = -1;

        /**
         * The constant NO_WRITE_EXTERNAL_STORAGE_PERMISSION.
         */
        public static final int NO_WRITE_EXTERNAL_STORAGE_PERMISSION = -2;

        /**
         * The constant NO_RECORD_AUDIO_PERMISSION.
         */
        public static final int NO_RECORD_AUDIO_PERMISSION = -3;

        /**
         * The constant CAMERA_DISCONNECTED.
         */
        public static final int CAMERA_DISCONNECTED = -4;

        /**
         * The constant CAMERA_ERROR_OCCURRED.
         */
        public static final int CAMERA_ERROR_OCCURRED = -5;

        /**
         * The constant CAMERA_EXCEPTION.
         */
        public static final int CAMERA_EXCEPTION = -6;

        /**
         * The constant CAMERA_CAPTURE_SESSION_CONFIG_FAILED.
         */
        public static final int CAMERA_CAPTURE_SESSION_CONFIG_FAILED = -7;

        /**
         * The constant CAMERA_DEVICE_NULL.
         */
        public static final int CAMERA_DEVICE_NULL = -8;

        /**
         * The constant CAMERA_CAPTURE_SESSION_NULL.
         */
        public static final int CAMERA_CAPTURE_SESSION_NULL = -9;

        /**
         * The constant NO_PREVIEW_SURFACE.
         */
        public static final int NO_PREVIEW_SURFACE = -10;

        /**
         * The constant NO_IMAGE_READER.
         */
        public static final int NO_IMAGE_READER = -11;

        /**
         * The constant NO_MEDIA_RECORDER.
         */
        public static final int NO_MEDIA_RECORDER = -12;

        /**
         * The constant FAILED_WHILE_RECORDING.
         */
        public static final int FAILED_WHILE_RECORDING = -13;

        /**
         * The constant CREATE_DIRECTORY_FAILED.
         */
        public static final int CREATE_DIRECTORY_FAILED = -14;
    }

    /**
     * The interface camera callback.
     */
    public interface ICameraCallback {
        /**
         * The camera state.
         */
        public interface State {
            /**
             * The constant CAMERA_CLOSED.
             */
            public static final int CAMERA_CLOSED = 0;

            /**
             * The constant CAMERA_OPENED.
             */
            public static final int CAMERA_OPENED = 1;

            /**
             * The constant CAMERA_DISCONNECTED.
             */
            public static final int CAMERA_DISCONNECTED = 2;
        }

        /**
         * The camera error code.
         */
        public interface ErrorCode {
            /**
             * The constant CAMERA_IN_USE.
             */
            public static final int CAMERA_IN_USE = CameraDevice.StateCallback.ERROR_CAMERA_IN_USE;

            /**
             * The constant MAX_CAMERAS_IN_USE.
             */
            public static final int MAX_CAMERAS_IN_USE = CameraDevice.StateCallback.ERROR_MAX_CAMERAS_IN_USE;

            /**
             * The constant CAMERA_DISABLED.
             */
            public static final int CAMERA_DISABLED = CameraDevice.StateCallback.ERROR_CAMERA_DISABLED;

            /**
             * The constant CAMERA_DEVICE.
             */
            public static final int CAMERA_DEVICE = CameraDevice.StateCallback.ERROR_CAMERA_DEVICE;

            /**
             * The constant CAMERA_SERVICE.
             */
            public static final int CAMERA_SERVICE = CameraDevice.StateCallback.ERROR_CAMERA_SERVICE;
        }

        /**
         * Called when the camera state changed.
         *
         * @param cameraId The camera id.
         * @param state    The state:
         * <ul>
         * <li>{@link State#CAMERA_CLOSED}
         * <li>{@link State#CAMERA_OPENED}
         * <li>{@link State#CAMERA_DISCONNECTED}
         * <ul/>
         */
        public void onState(String cameraId, int state);

        /**
         * Called when the camera error occurred.
         *
         * @param cameraId The camera id.
         * @param error    The error code:
         * <ul>
         * <li>{@link ErrorCode#CAMERA_IN_USE}
         * <li>{@link ErrorCode#MAX_CAMERAS_IN_USE}
         * <li>{@link ErrorCode#CAMERA_DISABLED}
         * <li>{@link ErrorCode#CAMERA_DEVICE}
         * <li>{@link ErrorCode#CAMERA_SERVICE}
         * <ul/>
         */
        public void onError(String cameraId, int error);
    }

    /**
     * The interface capture callback.
     */
    public interface ICaptureCallback {
        /**
         * Called when the capture complete.
         *
         * @param cameraId The camera id.
         * @param filePath The captured file full path.
         */
        public void onComplete(String cameraId, String filePath);
    }

    /**
     * The interface record callback.
     */
    public interface IRecordCallback {
        /**
         * The error code while recording.
         */
        public interface ErrorCode {
            /**
             * The constant ERROR_UNKNOWN.
             */
            public static final int UNKNOWN = MediaRecorder.MEDIA_RECORDER_ERROR_UNKNOWN;

            /**
             * The constant ERROR_SERVER_DIED.
             */
            public static final int SERVER_DIED = MediaRecorder.MEDIA_ERROR_SERVER_DIED;
        }

        /**
         * Called when the record complete.
         *
         * @param cameraId The camera id.
         * @param filePath The record file full path.
         */
        public void onComplete(String cameraId, String filePath);

        /**
         * Called when error occurred while recording.
         *
         * @param cameraId The camera id.
         * @param what     The type of error that has occurred:
         * <ul>
         * <li>{@link ErrorCode#UNKNOWN}
         * <li>{@link ErrorCode#SERVER_DIED}
         * <ul/>
         * @param extra    An extra code, specific to the error type.
         */
        public void onError(String cameraId, int what, int extra);
    }

    /**
     * Get the camera id list.
     *
     * @return The camera id list.
     */
    public String[] getCameraIdList();

    /**
     * Get the available preview sizes.
     *
     * @param cameraId The camera id.
     * @return The available preview sizes.
     */
    public Size[] getAvailablePreviewSizes(String cameraId);

    /**
     * Get the available capture sizes.
     *
     * @param cameraId The camera id.
     * @return The available capture sizes.
     */
    public Size[] getAvailableCaptureSizes(String cameraId);

    /**
     * Get the available record sizes.
     *
     * @param cameraId The camera id.
     * @return The available record sizes.
     */
    public Size[] getAvailableRecordSizes(String cameraId);

    /**
     * Set the camera callback.
     *
     * @param callback The camera callback.
     * @return {@link ResultCode}.
     */
    public int setCameraCallback(ICameraCallback callback);

    /**
     * Set the preview surface.
     *
     * @param cameraId       The camera id.
     * @param previewSurface The preview surface.
     * @return {@link ResultCode}.
     */
    public int setPreviewSurface(String cameraId, Surface previewSurface);

    /**
     * Set the capture size.
     *
     * @param cameraId The camera id.
     * @param width    The capture width.
     * @param height   The capture height.
     * @return {@link ResultCode}.
     */
    public int setCaptureSize(String cameraId, int width, int height);

    /**
     * Set the capture storage directory.
     *
     * @param cameraId The camera id.
     * @param dir      The capture storage directory.
     * @return {@link ResultCode}.
     */
    public int setCaptureDir(String cameraId, String dir);

    /**
     * Set the capture callback.
     *
     * @param callback The capture callback.
     * @return {@link ResultCode}.
     */
    public int setCaptureCallback(ICaptureCallback callback);

    /**
     * Set the record video size.
     *
     * @param cameraId The camera id.
     * @param width    The record video width.
     * @param height   The record video height.
     * @return {@link ResultCode}.
     */
    public int setRecordSize(String cameraId, int width, int height);

    /**
     * Set the video encoding bit rate.
     *
     * @param cameraId The camera id.
     * @param bps      The video encoding bit rate in bps.
     * @return {@link ResultCode}.
     */
    public int setVideoEncodingBps(String cameraId, int bps);

    /**
     * Set the record storage directory.
     *
     * @param cameraId The camera id.
     * @param dir      The record storage directory.
     * @return {@link ResultCode}.
     */
    public int setRecordDir(String cameraId, String dir);

    /**
     * Set the record callback.
     *
     * @param callback The record callback.
     * @return {@link ResultCode}.
     */
    public int setRecordCallback(IRecordCallback callback);

    /**
     * Open the camera by id.
     *
     * @param cameraId The camera id.
     * @return {@link ResultCode}.
     */
    public int open(String cameraId);

    /**
     * Close the camera by id.
     *
     * @param cameraId The camera id.
     * @return {@link ResultCode}.
     */
    public int close(String cameraId);

    /**
     * Start the preview.
     *
     * @param cameraId The camera id.
     * @return {@link ResultCode}.
     */
    public int startPreview(String cameraId);

    /**
     * Stop the preview.
     *
     * @param cameraId the camera id
     * @return {@link ResultCode}.
     */
    public int stopPreview(String cameraId);

    /**
     * Capture a picture.
     *
     * @param cameraId  The camera id.
     * @param latitude  The latitude.
     * @param longitude The longitude.
     * @return {@link ResultCode}.
     */
    public int capture(String cameraId, double latitude, double longitude);

    /**
     * Start record.
     *
     * @param cameraId The camera id.
     * @return {@link ResultCode}.
     */
    public int startRecord(String cameraId);

    /**
     * Start record with max duration.
     *
     * @param cameraId The camera id.
     * @param duration The record max duration in ms.
     * @return {@link ResultCode}.
     */
    public int startRecord(String cameraId, int duration);

    /**
     * Stop record.
     *
     * @param cameraId The camera id.
     * @return {@link ResultCode}.
     */
    public int stopRecord(String cameraId);
}
