package com.carzuilha.ocr.control;

import android.support.annotation.NonNull;

import com.carzuilha.ocr.view.DynamicTextureView;

/**
 *  Defines a generic class that manages the application in conjunction with an underlying Google's
 * detector.
 */
@SuppressWarnings("WeakerAccess")
public abstract class CameraControl {

    //  Defines a camera type.
    public static final int CAMERA_FACING_BACK = 0;
    public static final int CAMERA_FACING_FRONT = 1;

    //  Defines the camera FPS. It is possible to let the user changes the value, but (for now) I
    // found it to unstable.
    protected static final float REQUESTED_FPS = 40.0f;

    //  If the absolute difference between a preview size aspect ratio and a picture size aspect
    // ratio is less than this tolerance, they are considered to be the same aspect ratio.
    protected static final float ASPECT_RATIO_TOLERANCE = 0.01f;

    //  Defines the default camera.
    protected int selectedCamera = CAMERA_FACING_BACK;

    /**
     *  Opens the camera and starts sending preview frames to the underlying detector. The supplied
     * surface holder is used for the preview so frames can be displayed to the user.
     *
     * @param   _dynamicTextureView     The surface holder to use for the preview frames
     */
    public abstract void start(@NonNull DynamicTextureView _dynamicTextureView);

    /**
     *  Closes the camera and stops sending frames to the underlying frame detector. This source may
     * be restarted again by calling start() or start(SurfaceHolder). Call release() instead to
     * completely shut down this source and release the resources of the underlying detector.
     */
    public abstract void stop();

    /**
     *  Stops the application and releases its resources and underlying detector.
     */
    public abstract void release();

}
