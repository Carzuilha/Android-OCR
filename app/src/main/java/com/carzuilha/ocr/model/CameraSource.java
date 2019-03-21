package com.carzuilha.ocr.model;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.ImageFormat;
import android.graphics.SurfaceTexture;
import android.hardware.Camera;
import android.hardware.Camera.CameraInfo;
import android.os.Build;
import android.os.SystemClock;
import android.support.annotation.Nullable;
import android.support.annotation.RequiresPermission;
import android.support.annotation.StringDef;
import android.util.Log;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.WindowManager;

import com.google.android.gms.common.images.Size;
import com.google.android.gms.vision.Detector;
import com.google.android.gms.vision.Frame;

import java.io.IOException;
import java.lang.Thread.State;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 *  Manages the application in conjunction with an underlying Google's detector.  This receives
 * preview frames from the camera at a specified rate, sending those frames to the detector as fast
 * as it is able to process those frames.
 *
 * NOTE: this code requires Google Play Services 8.1 or higher, due to using indirect byte buffers
 * for storing images.
 */
@SuppressWarnings("deprecation")
public class CameraSource {

    //  Defines the tag of the class.
    private static final String TAG = "CameraSource";

    //  Defines the camera type.
    @SuppressLint("InlinedApi")
    public static final int CAMERA_FACING_BACK = CameraInfo.CAMERA_FACING_BACK;
    @SuppressLint("InlinedApi")
    public static final int CAMERA_FACING_FRONT = CameraInfo.CAMERA_FACING_FRONT;

    //  The dummy surface texture must be assigned a chosen name. Since we never use an OpenGL
    // context, we can choose any ID we want here.
    private static final int DUMMY_TEXTURE_NAME = 100;

    //  If the absolute difference between a preview size aspect ratio and a picture size aspect
    // ratio is less than this tolerance, they are considered to be the same aspect ratio.
    private static final float ASPECT_RATIO_TOLERANCE = 0.01f;

    //  Defines all the focus modes from a camera.
    @StringDef({
            Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE,
            Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO,
            Camera.Parameters.FOCUS_MODE_AUTO,
            Camera.Parameters.FOCUS_MODE_EDOF,
            Camera.Parameters.FOCUS_MODE_FIXED,
            Camera.Parameters.FOCUS_MODE_INFINITY,
            Camera.Parameters.FOCUS_MODE_MACRO
    })
    @Retention(RetentionPolicy.SOURCE)
    private @interface FocusMode {}

    //  Defines all the flash modes from a camera.
    @StringDef({
            Camera.Parameters.FLASH_MODE_ON,
            Camera.Parameters.FLASH_MODE_OFF,
            Camera.Parameters.FLASH_MODE_AUTO,
            Camera.Parameters.FLASH_MODE_RED_EYE,
            Camera.Parameters.FLASH_MODE_TORCH
    })
    @Retention(RetentionPolicy.SOURCE)
    private @interface FlashMode {}

    //  Defines the default camera.
    private int mFacing = CAMERA_FACING_BACK;

    //  Rotation of the device, and thus the associated preview images captured from the device.
    private int rotation;

    //  These values may be requested by the caller.  Due to hardware limitations, we may need to
    // select close, but not exactly the same values for these.
    private int requestedPreviewWidth = 1024;
    private int requestedPreviewHeight = 768;
    private float requestedFps = 30.0f;

    //  Contains the focus and the flash values.
    private String focusMode = null;
    private String flashMode = null;

    //  The camera and cameraLock.
    private Camera camera;
    private final Object cameraLock = new Object();

    //  The preview size and the context of the camera source.
    private Size previewSize;
    private Context context;

    //  These instances need to be held onto to avoid GC of their underlying resources.  Even though
    // these aren't used outside of the method that creates them, they still must have hard
    // references maintained to them.
    private SurfaceView dummySurfaceView;
    private SurfaceTexture dummySurfaceTexture;

    //  Dedicated thread and associated runnable for calling into the detector with frames, as the
    // frames become available from the com.project.util.
    private Thread processingThread;
    private FrameProcessingRunnable frameProcessor;

    //  Map to convert between a byte array, received from the com.project.util, and its associated
    // byte buffer.  We use byte buffers internally because this is a more efficient way to call into
    // native code later (avoids a potential copy).
    private Map<byte[], ByteBuffer> bytesToByteBuffer = new HashMap<>();

    /**
     *  Only allow creation via the builder class.
     */
    private CameraSource() { }

    /**
     *  Stops the application and releases its resources and underlying detector.
     */
    public void release() {
        synchronized (cameraLock) {
            stop();
            frameProcessor.release();
        }
    }

    /**
     *  Opens the camera and starts sending preview frames to the underlying detector. The preview
     * frames are not displayed.
     *
     * @throws  IOException     If the camera's preview texture or display could not be initialized.
     */
    @RequiresPermission(Manifest.permission.CAMERA)
    public CameraSource start() throws IOException {

        synchronized (cameraLock) {

            if (camera != null) return this;

            camera = createCamera();

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
                dummySurfaceTexture = new SurfaceTexture(DUMMY_TEXTURE_NAME);
                camera.setPreviewTexture(dummySurfaceTexture);
            } else {
                dummySurfaceView = new SurfaceView(context);
                camera.setPreviewDisplay(dummySurfaceView.getHolder());
            }

            camera.startPreview();

            processingThread = new Thread(frameProcessor);
            frameProcessor.setActive(true);
            processingThread.start();
        }

        return this;
    }

    /**
     *  Opens the camera and starts sending preview frames to the underlying detector. The supplied
     * surface holder is used for the preview so frames can be displayed to the user.
     *
     * @param   _surfaceHolder      The surface holder to use for the preview frames
     * @throws  IOException         If the supplied surface holder could not be used as the preview
     *                              display.
     */
    @RequiresPermission(Manifest.permission.CAMERA)
    public CameraSource start(SurfaceHolder _surfaceHolder) throws IOException {

        synchronized (cameraLock) {

            if (camera != null) return this;

            camera = createCamera();
            camera.setPreviewDisplay(_surfaceHolder);
            camera.startPreview();

            processingThread = new Thread(frameProcessor);
            frameProcessor.setActive(true);
            processingThread.start();
        }

        return this;
    }

    /**
     *  Closes the camera and stops sending frames to the underlying frame detector. This source may
     * be restarted again by calling start() or start(SurfaceHolder). Call release() instead to
     * completely shut down this source and release the resources of the underlying detector.
     */
    public void stop() {

        synchronized (cameraLock) {

            frameProcessor.setActive(false);

            if (processingThread != null) {

                try {
                    processingThread.join();
                } catch (InterruptedException e) {
                    Log.d(TAG, "Frame processing thread interrupted on release.");
                }

                processingThread = null;
            }

            bytesToByteBuffer.clear();

            if (camera != null) {

                camera.stopPreview();
                camera.setPreviewCallbackWithBuffer(null);

                try {

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
                        camera.setPreviewTexture(null);
                    } else {
                        camera.setPreviewDisplay(null);
                    }

                } catch (Exception e) {
                    Log.e(TAG, "Failed to clear com.project.util preview: " + e);
                }

                camera.release();
                camera = null;
            }
        }
    }

    /**
     *  Returns the preview size that is currently in use by the underlying application.
     */
    public Size getPreviewSize() {
        return previewSize;
    }

    /**
     *  Returns the selected camera; one of CAMERA_FACING_BACK or CAMERA_FACING_FRONT.
     */
    public int getCameraFacing() {
        return mFacing;
    }

    /**
     *  Performs the zoom operation on the camera.
     *
     * @param   _scale      The zoom scale.
     * @return              The current zoom of the camera.
     */
    public int doZoom(float _scale) {

        synchronized (cameraLock) {

            if (camera == null) return 0;

            int currentZoom = 0;
            int maxZoom;
            float newZoom;

            Camera.Parameters parameters = camera.getParameters();

            if (!parameters.isZoomSupported()) {
                return currentZoom;
            }

            maxZoom = parameters.getMaxZoom();
            currentZoom = parameters.getZoom() + 1;

            if (_scale > 1) {
                newZoom = currentZoom + _scale * (maxZoom / 10);
            } else {
                newZoom = currentZoom * _scale;
            }
            currentZoom = Math.round(newZoom) - 1;

            if (currentZoom < 0) {
                currentZoom = 0;
            } else if (currentZoom > maxZoom) {
                currentZoom = maxZoom;
            }
            parameters.setZoom(currentZoom);
            camera.setParameters(parameters);

            return currentZoom;
        }
    }

    /**
     *  Initiates taking a picture, which happens asynchronously. The camera source should have been
     * activated previously with start() or start(SurfaceHolder). The camera preview is suspended
     * while the picture is being taken, but will resume once picture taking is done.
     *
     * @param   _shutter    The callback for image capture moment, or null.
     * @param   _jpeg       The callback for JPEG image data, or null.
     */
    public void takePicture(ShutterCallback _shutter, PictureCallback _jpeg) {

        synchronized (cameraLock) {

            if (camera != null) {

                PictureStartCallback startCallback = new PictureStartCallback();
                startCallback.mDelegate = _shutter;
                PictureDoneCallback doneCallback = new PictureDoneCallback();
                doneCallback.mDelegate = _jpeg;

                camera.takePicture(startCallback, null, null, doneCallback);
            }
        }
    }

    /**
     *  Gets the current focus mode setting.
     *
     * @return              Current focus mode. This value is null if the camera is not yet created.
     *                      Applications should call autoFocus(AutoFocusCallback) to start the focus
     *                      if focus mode is FOCUS_MODE_AUTO or FOCUS_MODE_MACRO.
     */
    @Nullable
    @FocusMode
    public String getFocusMode() {
        return focusMode;
    }

    /**
     *  Sets the focus mode.
     *
     * @param   _mode       The focus mode.
     * @return              'true' if the focus mode is set, 'false' otherwise.
     */
    public boolean setFocusMode(@FocusMode String _mode) {

        synchronized (cameraLock) {

            if (camera != null && _mode != null) {

                Camera.Parameters parameters = camera.getParameters();

                if (parameters.getSupportedFocusModes().contains(_mode)) {

                    parameters.setFocusMode(_mode);
                    camera.setParameters(parameters);
                    focusMode = _mode;

                    return true;
                }
            }

            return false;
        }
    }

    /**
     * Gets the current flash mode setting.
     *
     * @return              Current flash mode. Null if flash mode setting is not
     *                      supported or the camera is not yet created.
     */
    @Nullable
    @FlashMode
    public String getFlashMode() {
        return flashMode;
    }

    /**
     *  Sets the flash mode.
     *
     * @param   _mode       Flash mode.
     * @return              'true' if the flash mode is set, 'false' otherwise.
     */
    public boolean setFlashMode(@FlashMode String _mode) {

        synchronized (cameraLock) {

            if (camera != null && _mode != null) {

                Camera.Parameters parameters = camera.getParameters();

                if (parameters.getSupportedFlashModes().contains(_mode)) {

                    parameters.setFlashMode(_mode);
                    camera.setParameters(parameters);
                    flashMode = _mode;

                    return true;
                }
            }

            return false;
        }
    }

    /**
     *  Starts camera's auto-focus and registers a callback function to run when the camera is
     * focused.
     *
     * @param   _cb         The callback to run.
     */
    public void autoFocus(@Nullable AutoFocusCallback _cb) {

        synchronized (cameraLock) {

            if (camera != null) {

                CameraAutoFocusCallback autoFocusCallback = null;

                if (_cb != null) {
                    autoFocusCallback = new CameraAutoFocusCallback();
                    autoFocusCallback.mDelegate = _cb;
                }

                camera.autoFocus(autoFocusCallback);
            }
        }
    }

    /**
     *  Cancels any auto-focus function in progress. Whether or not auto-focus is currently in progress,
     * this function will return the focus position to the default. If the camera does not support
     * auto-focus, this is a no-op.
     */
    public void cancelAutoFocus() {

        synchronized (cameraLock) {

            if (camera != null) {
                camera.cancelAutoFocus();
            }
        }
    }


    /**
     *  Opens the com.project.util and applies the user settings.
     *
     * @throws  RuntimeException        If the method fails.
     */
    @SuppressLint("InlinedApi")
    private Camera createCamera() {

        int requestedCameraId = getIdForRequestedCamera(mFacing);

        if (requestedCameraId == -1) {
            throw new RuntimeException("Could not find requested com.project.util.");
        }

        Camera camera = Camera.open(requestedCameraId);
        SizePair sizePair = selectSizePair(camera, requestedPreviewWidth, requestedPreviewHeight);

        if (sizePair == null) {
            throw new RuntimeException("Could not find suitable preview size.");
        }

        Size pictureSize = sizePair.pictureSize();
        previewSize = sizePair.previewSize();

        int[] previewFpsRange = selectPreviewFpsRange(camera, requestedFps);

        if (previewFpsRange == null) {
            throw new RuntimeException("Could not find suitable preview frames per second range.");
        }

        Camera.Parameters parameters = camera.getParameters();

        if (pictureSize != null) {
            parameters.setPictureSize(pictureSize.getWidth(), pictureSize.getHeight());
        }

        parameters.setPreviewSize(previewSize.getWidth(), previewSize.getHeight());
        parameters.setPreviewFpsRange(
                previewFpsRange[Camera.Parameters.PREVIEW_FPS_MIN_INDEX],
                previewFpsRange[Camera.Parameters.PREVIEW_FPS_MAX_INDEX]);
        parameters.setPreviewFormat(ImageFormat.NV21);

        setRotation(camera, parameters, requestedCameraId);

        if (focusMode != null) {

            if (parameters.getSupportedFocusModes().contains(focusMode)) {
                parameters.setFocusMode(focusMode);
            } else {
                Log.i(TAG, "Camera focus mode: " + focusMode + " is not supported on this device.");
            }
        }

        focusMode = parameters.getFocusMode();

        if (flashMode != null) {

            if (parameters.getSupportedFlashModes().contains(flashMode)) {
                parameters.setFlashMode(flashMode);
            } else {
                Log.i(TAG, "Camera flash mode: " + flashMode + " is not supported on this device.");
            }
        }

        flashMode = parameters.getFlashMode();

        camera.setParameters(parameters);

        // Four frame buffers are needed for working with the application:
        //
        // - one for the frame that is currently being executed upon in doing detection.
        // - one for the next pending frame to process immediately upon completing detection.
        // - two for the frames that the application uses to populate future preview images.
        camera.setPreviewCallbackWithBuffer(new CameraPreviewCallback());
        camera.addCallbackBuffer(createPreviewBuffer(previewSize));
        camera.addCallbackBuffer(createPreviewBuffer(previewSize));
        camera.addCallbackBuffer(createPreviewBuffer(previewSize));
        camera.addCallbackBuffer(createPreviewBuffer(previewSize));

        return camera;
    }


    /**
     *  Gets the id for the camera specified by the direction it is facing. Returns -1 if no such
     * camera was found.
     *
     * @param   _facing     The desired camera (front-facing or rear-facing).
     */
    private static int getIdForRequestedCamera(int _facing) {

        CameraInfo cameraInfo = new CameraInfo();

        for (int i = 0; i < Camera.getNumberOfCameras(); ++i) {

            Camera.getCameraInfo(i, cameraInfo);

            if (cameraInfo.facing == _facing) {
                return i;
            }
        }
        return -1;
    }

    /**
     *  Selects the most suitable preview and picture size, given the desired width and height.
     *
     * @param   _camera         The camera to select a preview size from.
     * @param   _desiredWidth   The desired width of the camera preview frames.
     * @param   _desiredHeight  The desired height of the camera preview frames.
     * @return                  The selected preview and picture size pair.
     */
    private static SizePair selectSizePair(Camera _camera, int _desiredWidth, int _desiredHeight) {

        List<SizePair> validPreviewSizes = generateValidPreviewSizeList(_camera);

        SizePair selectedPair = null;

        int minDiff = Integer.MAX_VALUE;

        for (SizePair sizePair : validPreviewSizes) {

            Size size = sizePair.previewSize();

            int diff = Math.abs(size.getWidth() - _desiredWidth) +
                    Math.abs(size.getHeight() - _desiredHeight);
            if (diff < minDiff) {
                selectedPair = sizePair;
                minDiff = diff;
            }
        }

        return selectedPair;
    }

    /**
     *  Generates a list of acceptable preview sizes. Preview sizes are not acceptable if there is
     * not a corresponding picture size of the same aspect ratio.
     */
    private static List<SizePair> generateValidPreviewSizeList(Camera _camera) {

        Camera.Parameters parameters = _camera.getParameters();
        List<android.hardware.Camera.Size> supportedPreviewSizes = parameters.getSupportedPreviewSizes();
        List<android.hardware.Camera.Size> supportedPictureSizes = parameters.getSupportedPictureSizes();
        List<SizePair> validPreviewSizes = new ArrayList<>();

        for (android.hardware.Camera.Size previewSize : supportedPreviewSizes) {

            float previewAspectRatio = (float) previewSize.width / (float) previewSize.height;

            //  By looping through the picture sizes in order, we favor the higher resolutions.
            // We choose the highest resolution in order to support taking the full resolution
            // picture later.
            for (android.hardware.Camera.Size pictureSize : supportedPictureSizes) {

                float pictureAspectRatio = (float) pictureSize.width / (float) pictureSize.height;

                if (Math.abs(previewAspectRatio - pictureAspectRatio) < ASPECT_RATIO_TOLERANCE) {
                    validPreviewSizes.add(new SizePair(previewSize, pictureSize));
                    break;
                }
            }
        }

        //  If there are no picture sizes with the same aspect ratio as any preview sizes, allow all
        // of the preview sizes and hope that the com.project.util can handle it.  Probably unlikely, but we
        // still account for it.
        if (validPreviewSizes.size() == 0) {

            Log.w(TAG, "No preview sizes have a corresponding same-aspect-ratio picture size.");

            for (android.hardware.Camera.Size previewSize : supportedPreviewSizes) {
                // The null picture size will let us know that we shouldn't set a picture size.
                validPreviewSizes.add(new SizePair(previewSize, null));
            }
        }

        return validPreviewSizes;
    }

    /**
     *  Selects the most suitable preview frames per second range, given the desired frames per
     * second.
     *
     * @param   _camera             The camera to select a frames per second range from.
     * @param   _desiredPreviewFps  The desired frames per second for the camera preview frames.
     * @return                      The selected preview frames per second range.
     */
    private int[] selectPreviewFpsRange(Camera _camera, float _desiredPreviewFps) {

        //  The application API uses integers scaled by a factor of 1000 instead of floating-point frame
        // rates.
        int desiredPreviewFpsScaled = (int) (_desiredPreviewFps * 1000.0f);

        //  The method for selecting the best range is to minimize the sum of the differences between
        // the desired value and the upper and lower bounds of the range. This may select a range
        // that the desired value is outside of, but this is often preferred. For example, if the
        // desired frame rate is 29.97, the range (30, 30) is probably more desirable than the
        // range (15, 30).
        int[] selectedFpsRange = null;
        int minDiff = Integer.MAX_VALUE;

        List<int[]> previewFpsRangeList = _camera.getParameters().getSupportedPreviewFpsRange();

        for (int[] range : previewFpsRangeList) {

            int deltaMin = desiredPreviewFpsScaled - range[Camera.Parameters.PREVIEW_FPS_MIN_INDEX];
            int deltaMax = desiredPreviewFpsScaled - range[Camera.Parameters.PREVIEW_FPS_MAX_INDEX];
            int diff = Math.abs(deltaMin) + Math.abs(deltaMax);

            if (diff < minDiff) {
                selectedFpsRange = range;
                minDiff = diff;
            }
        }

        return selectedFpsRange;
    }

    /**
     *  Calculates the correct rotation for the given camera id and sets the rotation in the
     * parameters.  It also sets the com.project.util's display orientation and rotation.
     *
     * @param   _parameters     The camera parameters for which to set the rotation.
     * @param   _cameraId       The camera id to set rotation based on.
     */
    private void setRotation(Camera _camera, Camera.Parameters _parameters, int _cameraId) {

        WindowManager windowManager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);

        int degrees = 0;
        int rotation = windowManager.getDefaultDisplay().getRotation();

        switch (rotation) {
            case Surface.ROTATION_0:
                degrees = 0;
                break;
            case Surface.ROTATION_90:
                degrees = 90;
                break;
            case Surface.ROTATION_180:
                degrees = 180;
                break;
            case Surface.ROTATION_270:
                degrees = 270;
                break;
            default:
                Log.e(TAG, "Bad rotation value: " + rotation);
        }

        CameraInfo cameraInfo = new CameraInfo();
        Camera.getCameraInfo(_cameraId, cameraInfo);

        int angle;
        int displayAngle;

        if (cameraInfo.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
            angle = (cameraInfo.orientation + degrees) % 360;
            displayAngle = (360 - angle); // compensate for it being mirrored
        } else {  // back-facing
            angle = (cameraInfo.orientation - degrees + 360) % 360;
            displayAngle = angle;
        }

        //  This corresponds to the rotation constants in Frame.
        this.rotation = angle / 90;

        _camera.setDisplayOrientation(displayAngle);
        _parameters.setRotation(angle);
    }

    /**
     *  Creates one buffer for the com.project.util preview callback. The size of the buffer is based
     * off of the camera preview size and the format of the camera image.
     *
     * @return          A new preview buffer of the appropriate size for the current camera settings.
     */
    private byte[] createPreviewBuffer(Size _previewSize) {

        int bitsPerPixel = ImageFormat.getBitsPerPixel(ImageFormat.NV21);
        long sizeInBits = _previewSize.getHeight() * _previewSize.getWidth() * bitsPerPixel;
        int bufferSize = (int) Math.ceil(sizeInBits / 8.0d) + 1;

        //
        // NOTICE: This code only works when using play services v. 8.1 or higher.
        //

        //  Creating the byte array this way and wrapping it, as opposed to using .allocate(),
        // should guarantee that there will be an array to work with.
        byte[] byteArray = new byte[bufferSize];
        ByteBuffer buffer = ByteBuffer.wrap(byteArray);

        if (!buffer.hasArray() || (buffer.array() != byteArray)) {
            //  I don't think that this will ever happen.  But if it does, then we wouldn't be
            // passing the preview content to the underlying detector later.
            throw new IllegalStateException("Failed to create valid buffer for camera source.");
        }

        bytesToByteBuffer.put(byteArray, buffer);

        return byteArray;
    }

    /**
     *  Builder for configuring and creating an associated project source.
     */
    public static class Builder {

        //  Defines the
        private final Detector<?> detector;

        //  Defines a new camera source.
        private CameraSource cameraSource = new CameraSource();

        /**
         *  Creates an application source builder with the supplied context and detector. Camera
         * preview images will be streamed to the associated detector upon starting the application
         * source.
         */
        public Builder(Context _context, Detector<?> _detector) {
            if (_context == null) {
                throw new IllegalArgumentException("No context supplied.");
            }
            if (_detector == null) {
                throw new IllegalArgumentException("No detector supplied.");
            }

            this.detector = _detector;
            cameraSource.context = _context;
        }

        /**
         *  Sets the requested frame rate in frames per second.  If the exact requested value is not
         * not available, the best matching available value is selected (Default: 30).
         */
        public Builder setRequestedFps(float _fps) {
            if (_fps <= 0) {
                throw new IllegalArgumentException("Invalid fps: " + _fps);
            }
            cameraSource.requestedFps = _fps;
            return this;
        }

        /**
         *  Sets the focus mode of the camera.
         */
        public Builder setFocusMode(@FocusMode String _mode) {
            cameraSource.focusMode = _mode;
            return this;
        }

        /**
         *  Sets the flash mode of the camera.
         */
        public Builder setFlashMode(@FlashMode String _mode) {
            cameraSource.flashMode = _mode;
            return this;
        }

        /**
         *  Sets the desired width and height of the application frames in pixels. If the exact desired
         * values are not available options, the best matching available options are selected.
         */
        public Builder setRequestedPreviewSize(int _width, int _height) {
            // Restrict the requested range to something within the realm of possibility.  The
            // choice of 1000000 is a bit arbitrary -- intended to be well beyond resolutions that
            // devices can support.  We bound this to avoid int overflow in the code later.
            final int MAX = 1000000;
            if ((_width <= 0) || (_width > MAX) || (_height <= 0) || (_height > MAX)) {
                throw new IllegalArgumentException("Invalid preview size: " + _width + "x" + _height);
            }
            cameraSource.requestedPreviewWidth = _width;
            cameraSource.requestedPreviewHeight = _height;
            return this;
        }

        /**
         *  Sets the camera facing to use -- either CAMERA_FACING_BACK or CAMERA_FACING_FRONT
         * (Default: back facing).
         */
        public Builder setFacing(int _facing) {
            if ((_facing != CAMERA_FACING_BACK) && (_facing != CAMERA_FACING_FRONT)) {
                throw new IllegalArgumentException("Invalid com.project.util: " + _facing);
            }
            cameraSource.mFacing = _facing;
            return this;
        }

        /**
         *  Creates an instance of the camera source.
         */
        public CameraSource build() {
            cameraSource.frameProcessor = cameraSource.new FrameProcessingRunnable(detector);
            return cameraSource;
        }

    }

    /**
     *  Stores a preview size and a corresponding same-aspect-ratio picture size.  To avoid distorted
     * preview images on some devices, the picture size must be set to a size that is the same aspect
     * ratio as the preview size or the preview may end up being distorted.  If the picture size is
     * null, then there is no picture size with the same aspect ratio as the preview size.
     */
    private static class SizePair {

        private Size mPreview;
        private Size mPicture;

        public SizePair(android.hardware.Camera.Size _previewSize, android.hardware.Camera.Size _pictureSize) {
            mPreview = new Size(_previewSize.width, _previewSize.height);

            if (_pictureSize != null) {
                mPicture = new Size(_pictureSize.width, _pictureSize.height);
            }
        }

        public Size previewSize() {
            return mPreview;
        }

        @SuppressWarnings("unused")
        public Size pictureSize() {
            return mPicture;
        }
    }

    /**
     *  Called when the camera has a new preview frame.
     */
    private class CameraPreviewCallback implements Camera.PreviewCallback {

        @Override
        public void onPreviewFrame(byte[] data, Camera camera) {
            frameProcessor.setNextFrame(data, camera);
        }
    }

    /**
     *  This runnable controls access to the underlying receiver, calling it to process frames when
     * available from the camera. This is designed to run detection on frames as fast as possible
     * (i.e., without unnecessary context switching or waiting on the next frame).
     */
    private class FrameProcessingRunnable implements Runnable {

        private long mStartTimeMillis = SystemClock.elapsedRealtime();
        private Detector<?> mDetector;

        //  This lock guards all of the member variables below.
        private boolean mActive = true;
        private final Object mLock = new Object();

        //  These pending variables hold the state associated with the new frame awaiting processing.
        private int mPendingFrameId = 0;
        private long mPendingTimeMillis;
        private ByteBuffer mPendingFrameData;

        FrameProcessingRunnable(Detector<?> detector) {
            mDetector = detector;
        }

        /**
         *  Releases the underlying receiver. This is only safe to do after the associated thread
         * has completed, which is managed in frame source's release method above.
         */
        @SuppressLint("Assert")
        void release() {
            assert (processingThread.getState() == State.TERMINATED);
            mDetector.release();
            mDetector = null;
        }

        /**
         *  Marks the runnable as active/not active. Signals any blocked threads to continue.
         */
        void setActive(boolean _active) {

            synchronized (mLock) {
                mActive = _active;
                mLock.notifyAll();
            }
        }

        /**
         *  Sets the frame data received from the camera. This adds the previous unused frame buffer
         * (if present) back to the application, and keeps a pending reference to the frame data for
         * future use.
         */
        void setNextFrame(byte[] _data, Camera _camera) {

            synchronized (mLock) {

                if (mPendingFrameData != null) {
                    _camera.addCallbackBuffer(mPendingFrameData.array());
                    mPendingFrameData = null;
                }

                if (!bytesToByteBuffer.containsKey(_data)) {
                    Log.d(TAG,
                            "Skipping frame.  Could not find ByteBuffer associated with the image " +
                                    "data from the com.project.util.");
                    return;
                }

                //  Timestamp and frame ID are maintained here, which will give downstream code some
                // idea of the timing of frames received and when frames were dropped along the way.
                mPendingTimeMillis = SystemClock.elapsedRealtime() - mStartTimeMillis;
                mPendingFrameId++;
                mPendingFrameData = bytesToByteBuffer.get(_data);

                //  Notify the processor thread if it is waiting on the next frame (see below).
                mLock.notifyAll();
            }
        }

        /**
         *  As long as the processing thread is active, this executes detection on frames
         * continuously. The next pending frame is either immediately available or hasn't been
         * received yet. Once it is available, we transfer the frame info to local variables and
         * run detection on that frame. It immediately loops back for the next frame without
         * pausing.
         */
        @Override
        public void run() {

            Frame outputFrame;
            ByteBuffer data;

            while (true) {

                synchronized (mLock) {

                    while (mActive && (mPendingFrameData == null)) {

                        try {
                            mLock.wait();
                        } catch (InterruptedException e) {
                            Log.d(TAG, "Frame processing loop terminated.", e);
                            return;
                        }
                    }

                    if (!mActive) return;

                    outputFrame = new Frame.Builder()
                            .setImageData(mPendingFrameData, previewSize.getWidth(),
                                    previewSize.getHeight(), ImageFormat.NV21)
                            .setId(mPendingFrameId)
                            .setTimestampMillis(mPendingTimeMillis)
                            .setRotation(rotation)
                            .build();

                    data = mPendingFrameData;
                    mPendingFrameData = null;
                }

                try {
                    mDetector.receiveFrame(outputFrame);
                } catch (Throwable t) {
                    Log.e(TAG, "Exception thrown from receiver.", t);
                } finally {
                    camera.addCallbackBuffer(data.array());
                }
            }
        }
    }

    /**
     *  Wraps the camera1 shutter callback so that the deprecated API isn't exposed.
     */
    private class PictureStartCallback implements Camera.ShutterCallback {

        private ShutterCallback mDelegate;

        @Override
        public void onShutter() {

            if (mDelegate != null) {
                mDelegate.onShutter();
            }
        }
    }

    /**
     *  Wraps the final callback in the camera sequence, so that we can automatically turn the camera
     * preview back on after the picture has been taken.
     */
    private class PictureDoneCallback implements Camera.PictureCallback {

        private PictureCallback mDelegate;

        @Override
        public void onPictureTaken(byte[] data, Camera camera) {

            if (mDelegate != null) {
                mDelegate.onPictureTaken(data);
            }

            synchronized (cameraLock) {

                if (CameraSource.this.camera != null) {
                    CameraSource.this.camera.startPreview();
                }
            }
        }
    }

    /**
     *  Wraps the camera1 auto focus callback so that the deprecated API isn't exposed.
     */
    private class CameraAutoFocusCallback implements Camera.AutoFocusCallback {

        private AutoFocusCallback mDelegate;

        @Override
        public void onAutoFocus(boolean success, Camera camera) {

            if (mDelegate != null) {
                mDelegate.onAutoFocus(success);
            }
        }
    }

    /**
     *  Callback interface used to signal the moment of actual image capture.
     */
    public interface ShutterCallback {
        /**
         *  Called as near as possible to the moment when a photo is captured from the sensor.
         */
        void onShutter();
    }

    /**
     *  Callback interface used to supply image data from a photo capture.
     */
    public interface PictureCallback {
        /**
         *  Called when image data is available after a picture is taken.  The format of the data
         * is a jpeg binary.
         */
        void onPictureTaken(byte[] _data);
    }

    /**
     *  Callback interface used to notify on completion of application auto focus.
     */
    public interface AutoFocusCallback {
        /**
         *  Called when the application auto focus completes. If the application does not support
         * auto-focus and autoFocus is called, onAutoFocus will be called immediately with a fake
         * value of success set to true.
         *
         * @param   _success    'true' if focus was successful, 'false' otherwise.
         */
        void onAutoFocus(boolean _success);
    }

    /**
     *  Callback interface used to notify on auto focus start and stop.
     */
    public interface AutoFocusMoveCallback {
        /**
         *  Called when the com.project.util auto focus starts or stops.
         *
         * @param   _start      'true' if focus starts to move, 'false' if focus stops to move
         */
        void onAutoFocusMoving(boolean _start);
    }

}
