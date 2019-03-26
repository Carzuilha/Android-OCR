package com.carzuilha.ocr.control;

import android.Manifest;
import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureFailure;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.MeteringRectangle;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.ImageReader;
import android.media.MediaRecorder;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.RequiresPermission;
import android.support.v4.content.ContextCompat;
import android.util.Log;
import android.util.SparseIntArray;
import android.view.MotionEvent;
import android.view.Surface;

import com.carzuilha.ocr.thread.Camera2Runnable;
import com.carzuilha.ocr.util.ScreenManager;
import com.carzuilha.ocr.view.DynamicTextureView;
import com.google.android.gms.common.images.Size;
import com.google.android.gms.vision.Detector;
import com.google.android.gms.vision.Frame;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/**
 *  Manages the application in conjunction with an underlying Google's detector. This code requires
 * Google Play Services 8.1 or higher, due to using indirect byte buffers for storing images.
 */
@TargetApi(Build.VERSION_CODES.LOLLIPOP)
public class Camera2Controller {

    //  Defines the tag of the class.
    private static final String TAG = "Camera2Controller";

    //  Defines the camera type.
    @SuppressLint("InlinedApi")
    public static final int CAMERA_FACING_BACK = 0;
    @SuppressLint("InlinedApi")
    public static final int CAMERA_FACING_FRONT = 1;

    //  If the absolute difference between a preview size aspect ratio and a picture size aspect
    // ratio is less than this tolerance, they are considered to be the same aspect ratio.
    private static final double ASPECT_RATIO_TOLERANCE = 0.01;

    //  Defines all the focus modes from a camera.
    public static final int CAMERA_AF_OFF = CaptureRequest.CONTROL_AF_MODE_OFF;
    public static final int CAMERA_AF_AUTO = CaptureRequest.CONTROL_AF_MODE_AUTO;
    public static final int CAMERA_AF_EDOF = CaptureRequest.CONTROL_AF_MODE_EDOF;
    public static final int CAMERA_AF_MACRO = CaptureRequest.CONTROL_AF_MODE_MACRO;
    public static final int CAMERA_AF_CONTINUOUS_VIDEO = CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_VIDEO;
    public static final int CAMERA_AF_CONTINUOUS_PICTURE = CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE;

    //  Defines all the flash modes from a camera.
    public static final int CAMERA_FLASH_ON = CaptureRequest.CONTROL_AE_MODE_ON;
    public static final int CAMERA_FLASH_OFF = CaptureRequest.CONTROL_AE_MODE_OFF;
    public static final int CAMERA_FLASH_AUTO = CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH;
    public static final int CAMERA_FLASH_ALWAYS = CaptureRequest.CONTROL_AE_MODE_ON_ALWAYS_FLASH;
    public static final int CAMERA_FLASH_REDEYE = CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH_REDEYE;

    //  Defines all the possible camera states: showing camera preview, waiting for the focus to be
    // locked, waiting for the exposure to be pre-capture state, waiting for the exposure state to be
    // something other than pre-capture, and after picture capture.
    private static final int STATE_PREVIEW = 0;
    private static final int STATE_WAITING_LOCK = 1;
    private static final int STATE_WAITING_PRE_CAPTURE = 2;
    private static final int STATE_WAITING_NON_PRE_CAPTURE = 3;
    private static final int STATE_PICTURE_TAKEN = 4;

    //  Contains all the single and inverted orientation types for the screen.
    private static final SparseIntArray ORIENTATIONS = new SparseIntArray();
    private static final SparseIntArray INVERSE_ORIENTATIONS = new SparseIntArray();

    static {
        ORIENTATIONS.append(Surface.ROTATION_0, 90);
        ORIENTATIONS.append(Surface.ROTATION_90, 0);
        ORIENTATIONS.append(Surface.ROTATION_180, 270);
        ORIENTATIONS.append(Surface.ROTATION_270, 180);
    }

    static {
        INVERSE_ORIENTATIONS.append(Surface.ROTATION_0, 270);
        INVERSE_ORIENTATIONS.append(Surface.ROTATION_90, 180);
        INVERSE_ORIENTATIONS.append(Surface.ROTATION_180, 90);
        INVERSE_ORIENTATIONS.append(Surface.ROTATION_270, 0);
    }

    //  Max preview width, height and ratio tolerance, which is guaranteed by the Camera2 API.
    private static final int MAX_PREVIEW_WIDTH = 1920;
    private static final int MAX_PREVIEW_HEIGHT = 1080;
    private static final double MAX_RATIO_TOLERANCE = 0.18;

    //  Defines the default camera.
    private int facing = CAMERA_FACING_BACK;

    //  Contains the camera state, the focus and the flash values.
    private int state = STATE_PREVIEW;
    private int focusMode = CAMERA_AF_AUTO;
    private int flashMode = CAMERA_FLASH_AUTO;

    //  Contains the sensor and display orientations.
    private int sensorOrientation;
    private int displayOrientation;

    //  A set of references for all the resources to camera manipulation.
    private String cameraId;
    private CameraDevice cameraDevice;
    private CameraManager cameraManager = null;
    private CameraCaptureSession captureSession;
    private Semaphore mCameraOpenCloseLock = new Semaphore(1);

    //  The preview size and the context of the camera source.
    private Size previewSize;
    private Context context;

    //  Dedicated thread and associated runnable for calling into the detector with frames, as the
    // frames become available from the camera.
    private Thread processingThread;
    private Camera2Runnable frameProcessor;

    //  An additional thread for running tasks that shouldn't block the UI.
    private HandlerThread backgroundThread;

    //  A handler for running tasks in the background.
    private Handler backgroundHandler;

    //  The builder for the camera preview and for the capture request.
    private CaptureRequest.Builder previewRequestBuilder;
    private CaptureRequest previewRequest;

    //  A set of flags utilized during the camera execution.
    private boolean cameraStarted = false;
    private boolean swappedDimensions = false;
    private boolean isMeteringAreaAFSupported = false;

    //  A set of camera callbacks.
    private boolean mFlashSupported;
    private ShutterCallback mShutterCallback;
    private AutoFocusCallback mAutoFocusCallback;

    //  Graphic elements from the calling activity.
    private Rect sensorArraySize;
    private DynamicTextureView mTextureView;
    private ImageReader mImageReaderStill;
    private ImageReader mImageReaderPreview;

    //  A capture callback that handles events related to JPEG capture.
    private CameraCaptureSession.CaptureCallback mCaptureCallback = new CameraCaptureSession.CaptureCallback() {

        private void process(CaptureResult result) {

            switch (state) {

                case STATE_PREVIEW: {
                    break;
                }
                case STATE_WAITING_LOCK: {

                    Integer afState = result.get(CaptureResult.CONTROL_AF_STATE);

                    if (afState == null) {

                        captureStillPicture();

                    } else if (CaptureResult.CONTROL_AF_STATE_FOCUSED_LOCKED == afState
                            || CaptureResult.CONTROL_AF_STATE_NOT_FOCUSED_LOCKED == afState
                            || CaptureResult.CONTROL_AF_STATE_PASSIVE_UNFOCUSED == afState
                            || CaptureRequest.CONTROL_AF_STATE_PASSIVE_FOCUSED == afState
                            || CaptureRequest.CONTROL_AF_STATE_INACTIVE == afState) {

                        Integer aeState = result.get(CaptureResult.CONTROL_AE_STATE);

                        if (aeState == null || aeState == CaptureResult.CONTROL_AE_STATE_CONVERGED) {
                            state = STATE_PICTURE_TAKEN;
                            captureStillPicture();
                        } else {
                            runPrecaptureSequence();
                        }
                    }

                    break;
                }
                case STATE_WAITING_PRE_CAPTURE: {

                    Integer aeState = result.get(CaptureResult.CONTROL_AE_STATE);

                    if (aeState == null ||
                            aeState == CaptureResult.CONTROL_AE_STATE_PRECAPTURE ||
                            aeState == CaptureRequest.CONTROL_AE_STATE_FLASH_REQUIRED) {
                        state = STATE_WAITING_NON_PRE_CAPTURE;
                    }

                    break;
                }
                case STATE_WAITING_NON_PRE_CAPTURE: {

                    Integer aeState = result.get(CaptureResult.CONTROL_AE_STATE);

                    if (aeState == null || aeState != CaptureResult.CONTROL_AE_STATE_PRECAPTURE) {
                        state = STATE_PICTURE_TAKEN;
                        captureStillPicture();
                    }

                    break;
                }
            }
        }

        @Override
        public void onCaptureProgressed(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull CaptureResult partialResult) {
            process(partialResult);
        }

        @Override
        public void onCaptureCompleted(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull TotalCaptureResult result) {

            if(request.getTag() == ("FOCUS_TAG")) {

                mAutoFocusCallback.onAutoFocus(true);
                previewRequestBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER, null);
                previewRequestBuilder.setTag("");
                previewRequest = previewRequestBuilder.build();

                try {
                    captureSession.setRepeatingRequest(previewRequest, mCaptureCallback, backgroundHandler);
                } catch(CameraAccessException ex) {
                    Log.d(TAG, "AUTO FOCUS FAILURE: "+ex);
                }

            } else {
                process(result);
            }
        }

        @Override
        public void onCaptureFailed(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull CaptureFailure failure) {

            if(request.getTag() == "FOCUS_TAG") {

                Log.d(TAG, "Manual AF failure: "+failure);
                mAutoFocusCallback.onAutoFocus(false);
            }
        }
    };

    //  A callback object for the ImageReader. "onImageAvailable" will be called when a preview frame
    // is ready to be processed.
    private final ImageReader.OnImageAvailableListener mOnPreviewAvailableListener = new ImageReader.OnImageAvailableListener() {

        @Override
        public void onImageAvailable(ImageReader reader) {

            Image mImage = reader.acquireNextImage();

            if(mImage == null) {
                return;
            }

            frameProcessor.setNextFrame(convertYUV420888ToNV21(mImage));

            mImage.close();
        }
    };

    //  A callback object for the ImageReader. "onImageAvailable" will be called when a still image
    // is ready to be saved.
    private PictureDoneCallback mOnImageAvailableListener = new PictureDoneCallback();

    //  The state callback is called when camera device changes its state.
    private CameraDevice.StateCallback mStateCallback = new CameraDevice.StateCallback() {

        @Override
        public void onOpened(@NonNull CameraDevice cameraDevice) {

            mCameraOpenCloseLock.release();

            Camera2Controller.this.cameraDevice = cameraDevice;
            createCameraPreviewSession();
        }

        @Override
        public void onDisconnected(@NonNull CameraDevice cameraDevice) {

            mCameraOpenCloseLock.release();

            cameraDevice.close();
            Camera2Controller.this.cameraDevice = null;
        }

        @Override
        public void onError(@NonNull CameraDevice cameraDevice, int error) {

            mCameraOpenCloseLock.release();

            cameraDevice.close();
            Camera2Controller.this.cameraDevice = null;
        }
    };

    //==============================================================================================
    //                                  Default methods
    //==============================================================================================

    /**
     *  Only allow creation via the builder class.
     */
    private Camera2Controller() { }

    /**
     *  Returns the current orientation of the detector.
     *
     * @return      The detector orientation.
     */
    public int getDetectorOrientation() { return getDetectorOrientation(sensorOrientation); }

    /**
     *  Returns the current orientation based on a sensor.
     *
     * @param   sensorOrientation   The reference sensor.
     * @return                      The detector orientation.
     */
    public int getDetectorOrientation(int sensorOrientation) {

        switch (sensorOrientation) {
            case 0:
                return Frame.ROTATION_0;
            case 90:
                return Frame.ROTATION_90;
            case 180:
                return Frame.ROTATION_180;
            case 270:
                return Frame.ROTATION_270;
            case 360:
                return Frame.ROTATION_0;
            default:
                return Frame.ROTATION_90;
        }
    }

    /**
     *  Returns the preview size that is currently in use by the underlying application.
     */
    public Size getPreviewSize() {
        return previewSize;
    }

    /**
     *  Returns the processing thread of the camera, which does the frame processing.
     *
     * @return      The processing thread.
     */
    public Thread getProcessingThread() {
        return processingThread;
    }

    //==============================================================================================
    //                                  Execution methods
    //==============================================================================================

    /**
     * Starts a background thread and its {@link Handler}.
     */
    private void startBackgroundThread() {
        backgroundThread = new HandlerThread("CameraBackground");
        backgroundThread.start();
        backgroundHandler = new Handler(backgroundThread.getLooper());
    }

    /**
     * Stops the background thread and its {@link Handler}.
     */
    private void stopBackgroundThread() {
        try {
            if(backgroundThread != null) {
                backgroundThread.quitSafely();
                backgroundThread.join();
                backgroundThread = null;
                backgroundHandler = null;
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    /**
     * Stops the camera and releases the resources of the camera and underlying detector.
     */
    public void release() {
        frameProcessor.release();
        stop();
    }

    /**
     * Closes the camera and stops sending frames to the underlying frame detector.
     * <p/>
     * Call {@link #release()} instead to completely shut down this camera source and release the
     * resources of the underlying detector.
     */
    public void stop() {
        try {
            frameProcessor.setActive(false);
            if (processingThread != null) {
                try {
                    // Wait for the thread to complete to ensure that we can't have multiple threads
                    // executing at the same time (i.e., which would happen if we called start too
                    // quickly after stop).
                    processingThread.join();
                } catch (InterruptedException e) {
                    Log.d(TAG, "Frame processing thread interrupted on release.");
                }
                processingThread = null;
            }
            mCameraOpenCloseLock.acquire();
            if (null != captureSession) {
                captureSession.close();
                captureSession = null;
            }
            if (null != cameraDevice) {
                cameraDevice.close();
                cameraDevice = null;
            }
            if (null != mImageReaderPreview) {
                mImageReaderPreview.close();
                mImageReaderPreview = null;
            }
            if (null != mImageReaderStill) {
                mImageReaderStill.close();
                mImageReaderStill = null;
            }
        } catch (InterruptedException e) {
            throw new RuntimeException("Interrupted while trying to lock camera closing.", e);
        } finally {
            mCameraOpenCloseLock.release();
            stopBackgroundThread();
        }
    }

    public boolean isCamera2Native() {
        try {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {return false;}
            cameraManager = (CameraManager) context.getSystemService(Context.CAMERA_SERVICE);
            cameraId = cameraManager.getCameraIdList()[facing];
            CameraCharacteristics characteristics = cameraManager.getCameraCharacteristics(cameraId);
            //CHECK CAMERA HARDWARE LEVEL. IF CAMERA2 IS NOT NATIVELY SUPPORTED, GO BACK TO CAMERA1
            Integer deviceLevel = characteristics.get(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL);
            return deviceLevel != null && (deviceLevel != CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LEGACY);
        }
        catch (CameraAccessException ex) {return false;}
        catch (NullPointerException e) {return false;}
        catch (ArrayIndexOutOfBoundsException ez) {return false;}
    }

    /**
     * Opens the camera and starts sending preview frames to the underlying detector.  The supplied
     * texture view is used for the preview so frames can be displayed to the user.
     *
     * @param textureView the surface holder to use for the preview frames
     * @param displayOrientation the display orientation for a non stretched preview
     * @throws IOException if the supplied texture view could not be used as the preview display
     */
    @RequiresPermission(Manifest.permission.CAMERA)
    public Camera2Controller start(@NonNull DynamicTextureView textureView, int displayOrientation) throws IOException {

        this.displayOrientation = displayOrientation;

        if(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            if (cameraStarted) {
                return this;
            }
            cameraStarted = true;
            startBackgroundThread();

            processingThread = new Thread(frameProcessor);
            frameProcessor.setActive(true);
            processingThread.start();

            mTextureView = textureView;
            if (mTextureView.isAvailable()) {
                setUpCameraOutputs(mTextureView.getWidth(), mTextureView.getHeight());
            }
        }

        return this;
    }

    /**
     * Returns the selected camera; one of {@link #CAMERA_FACING_BACK} or
     * {@link #CAMERA_FACING_FRONT}.
     */
    public int getCameraFacing() {
        return facing;
    }

    public void autoFocus(@Nullable AutoFocusCallback cb, MotionEvent pEvent, int screenW, int screenH) {
        if(cb != null) {
            mAutoFocusCallback = cb;
        }
        if(sensorArraySize != null) {
            final int y = (int)pEvent.getX() / screenW * sensorArraySize.height();
            final int x = (int)pEvent.getY() / screenH * sensorArraySize.width();
            final int halfTouchWidth = 150;
            final int halfTouchHeight = 150;
            MeteringRectangle focusAreaTouch = new MeteringRectangle(
                    Math.max(x-halfTouchWidth, 0),
                    Math.max(y-halfTouchHeight, 0),
                    halfTouchWidth*2,
                    halfTouchHeight*2,
                    MeteringRectangle.METERING_WEIGHT_MAX - 1);

            try {
                captureSession.stopRepeating();
                //Cancel any existing AF trigger (repeated touches, etc.)
                previewRequestBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER, CameraMetadata.CONTROL_AF_TRIGGER_CANCEL);
                previewRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_OFF);
                captureSession.capture(previewRequestBuilder.build(), mCaptureCallback, backgroundHandler);

                //Now add a new AF trigger with focus region
                if(isMeteringAreaAFSupported) {
                    previewRequestBuilder.set(CaptureRequest.CONTROL_AF_REGIONS, new MeteringRectangle[]{focusAreaTouch});
                }
                previewRequestBuilder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO);
                previewRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_AUTO);
                previewRequestBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER, CameraMetadata.CONTROL_AF_TRIGGER_START);
                previewRequestBuilder.setTag("FOCUS_TAG"); //we'll capture this later for resuming the preview!
                //Then we ask for a single request (not repeating!)
                captureSession.capture(previewRequestBuilder.build(), mCaptureCallback, backgroundHandler);
            } catch(CameraAccessException ex) {
                Log.d("ASD", "AUTO FOCUS EXCEPTION: "+ex);
            }
        }
    }

    /**
     * Initiate a still image capture. The camera preview is suspended
     * while the picture is being taken, but will resume once picture taking is done.
     */
    public void takePicture(ShutterCallback shutter, PictureCallback picCallback) {
        mShutterCallback = shutter;
        mOnImageAvailableListener.mDelegate = picCallback;
        lockFocus();
    }

    private Size getBestAspectPictureSize(android.util.Size[] supportedPictureSizes) {
        float targetRatio = ScreenManager.getScreenRatio(context);
        Size bestSize = null;
        TreeMap<Double, List<android.util.Size>> diffs = new TreeMap<>();

        //Select supported sizes which ratio is less than ASPECT_RATIO_TOLERANCE
        for (android.util.Size size : supportedPictureSizes) {
            float ratio = (float) size.getWidth() / size.getHeight();
            double diff = Math.abs(ratio - targetRatio);
            if (diff < ASPECT_RATIO_TOLERANCE){
                if (diffs.keySet().contains(diff)){
                    //add the value to the list
                    diffs.get(diff).add(size);
                } else {
                    List<android.util.Size> newList = new ArrayList<>();
                    newList.add(size);
                    diffs.put(diff, newList);
                }
            }
        }

        //If no sizes were supported, (strange situation) establish a higher ASPECT_RATIO_TOLERANCE
        if(diffs.isEmpty()) {
            for (android.util.Size size : supportedPictureSizes) {
                float ratio = (float)size.getWidth() / size.getHeight();
                double diff = Math.abs(ratio - targetRatio);
                if (diff < MAX_RATIO_TOLERANCE){
                    if (diffs.keySet().contains(diff)){
                        //add the value to the list
                        diffs.get(diff).add(size);
                    } else {
                        List<android.util.Size> newList = new ArrayList<>();
                        newList.add(size);
                        diffs.put(diff, newList);
                    }
                }
            }
        }

        //Select the highest resolution from the ratio filtered ones.
        for (Map.Entry entry: diffs.entrySet()){
            List<?> entries = (List) entry.getValue();
            for (int i=0; i<entries.size(); i++) {
                android.util.Size s = (android.util.Size) entries.get(i);
                if(bestSize == null) {
                    bestSize = new Size(s.getWidth(), s.getHeight());
                } else if(bestSize.getWidth() < s.getWidth() || bestSize.getHeight() < s.getHeight()) {
                    bestSize = new Size(s.getWidth(), s.getHeight());
                }
            }
        }
        return bestSize;
    }

    /**
     * Given {@code choices} of {@code Size}s supported by a camera, choose the smallest one that
     * is at least as large as the respective texture view size, and that is at most as large as the
     * respective max size, and whose aspect ratio matches with the specified value. If such size
     * doesn't exist, choose the largest one that is at most as large as the respective max size,
     * and whose aspect ratio matches with the specified value.
     *
     * @param choices           The list of sizes that the camera supports for the intended output
     *                          class
     * @param textureViewWidth  The width of the texture view relative to sensor coordinate
     * @param textureViewHeight The height of the texture view relative to sensor coordinate
     * @param maxWidth          The maximum width that can be chosen
     * @param maxHeight         The maximum height that can be chosen
     * @param aspectRatio       The aspect ratio
     * @return The optimal {@code Size}, or an arbitrary one if none were big enough
     */
    private static Size chooseOptimalSize(Size[] choices, int textureViewWidth, int textureViewHeight, int maxWidth, int maxHeight, Size aspectRatio) {

        // Collect the supported resolutions that are at least as big as the preview Surface
        List<Size> bigEnough = new ArrayList<>();
        // Collect the supported resolutions that are smaller than the preview Surface
        List<Size> notBigEnough = new ArrayList<>();
        int w = aspectRatio.getWidth();
        int h = aspectRatio.getHeight();
        for (Size option : choices) {
            if (option.getWidth() <= maxWidth && option.getHeight() <= maxHeight &&
                    option.getHeight() == option.getWidth() * h / w) {
                if (option.getWidth() >= textureViewWidth &&
                        option.getHeight() >= textureViewHeight) {
                    bigEnough.add(option);
                } else {
                    notBigEnough.add(option);
                }
            }
        }

        // Pick the smallest of those big enough. If there is no one big enough, pick the
        // largest of those not big enough.
        if (bigEnough.size() > 0) {
            return Collections.min(bigEnough, new CompareSizesByArea());
        } else if (notBigEnough.size() > 0) {
            return Collections.max(notBigEnough, new CompareSizesByArea());
        } else {
            Log.e(TAG, "Couldn't find any suitable preview size");
            return choices[0];
        }
    }

    /**
     * We choose a video size with 3x4 aspect ratio. Also, we don't use sizes
     * larger than 1080p, since MediaRecorder cannot handle such a high-resolution video.
     *
     * @param choices The list of available sizes
     * @return The video size
     */
    private static Size chooseVideoSize(Size[] choices) {
        for (Size size : choices) {
            if (size.getWidth() == size.getHeight() * 16 / 9)
            {
                return size;
            }
        }
        Log.e(TAG, "Couldn't find any suitable video size");
        return choices[0];
    }

    /**
     * Compares two {@code Size}s based on their areas.
     */
    private static class CompareSizesByArea implements Comparator<Size> {

        @Override
        public int compare(Size lhs, Size rhs) {
            // We cast here to ensure the multiplications won't overflow
            return Long.signum((long) lhs.getWidth() * lhs.getHeight() -
                    (long) rhs.getWidth() * rhs.getHeight());
        }

    }

    /**
     * Configures the necessary {@link android.graphics.Matrix} transformation to `mTextureView`.
     * This method should be called after the camera preview size is determined in
     * setUpCameraOutputs and also the size of `mTextureView` is fixed.
     *
     * @param viewWidth  The width of `mTextureView`
     * @param viewHeight The height of `mTextureView`
     */
    private void configureTransform(int viewWidth, int viewHeight) {
        if (null == mTextureView || null == previewSize) {
            return;
        }
        int rotation = displayOrientation;
        Matrix matrix = new Matrix();
        RectF viewRect = new RectF(0, 0, viewWidth, viewHeight);
        RectF bufferRect = new RectF(0, 0, previewSize.getHeight(), previewSize.getWidth());
        float centerX = viewRect.centerX();
        float centerY = viewRect.centerY();
        if (Surface.ROTATION_90 == rotation || Surface.ROTATION_270 == rotation) {
            bufferRect.offset(centerX - bufferRect.centerX(), centerY - bufferRect.centerY());
            matrix.setRectToRect(viewRect, bufferRect, Matrix.ScaleToFit.FILL);
            float scale = Math.max(
                    (float) viewHeight / previewSize.getHeight(),
                    (float) viewWidth / previewSize.getWidth());
            matrix.postScale(scale, scale, centerX, centerY);
            matrix.postRotate(90 * (rotation - 2), centerX, centerY);
        } else if (Surface.ROTATION_180 == rotation) {
            matrix.postRotate(180, centerX, centerY);
        }
        mTextureView.setTransform(matrix);
    }

    /**
     * Sets up member variables related to camera.
     *
     * @param width  The width of available size for camera preview
     * @param height The height of available size for camera preview
     */
    private void setUpCameraOutputs(int width, int height) {
        try {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {return;}
            if (!mCameraOpenCloseLock.tryAcquire(2500, TimeUnit.MILLISECONDS)) {
                throw new RuntimeException("Time out waiting to lock camera opening.");
            }
            if(cameraManager == null) cameraManager = (CameraManager) context.getSystemService(Context.CAMERA_SERVICE);
            cameraId = cameraManager.getCameraIdList()[facing];
            CameraCharacteristics characteristics = cameraManager.getCameraCharacteristics(cameraId);
            StreamConfigurationMap map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
            if (map == null) {return;}

            // For still image captures, we use the largest available size.
            Size largest = getBestAspectPictureSize(map.getOutputSizes(ImageFormat.JPEG));
            mImageReaderStill = ImageReader.newInstance(largest.getWidth(), largest.getHeight(), ImageFormat.JPEG, /*maxImages*/2);
            mImageReaderStill.setOnImageAvailableListener(mOnImageAvailableListener, backgroundHandler);

            sensorArraySize = characteristics.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE);
            Integer maxAFRegions = characteristics.get(CameraCharacteristics.CONTROL_MAX_REGIONS_AF);
            if(maxAFRegions != null) {
                isMeteringAreaAFSupported = maxAFRegions >= 1;
            }
            // Find out if we need to swap dimension to get the preview size relative to sensor
            // coordinate.
            Integer sensorOrientation = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION);
            if(sensorOrientation != null) {
                this.sensorOrientation = sensorOrientation;
                switch (displayOrientation) {
                    case Surface.ROTATION_0:
                    case Surface.ROTATION_180:
                        if (this.sensorOrientation == 90 || this.sensorOrientation == 270) {
                            swappedDimensions = true;
                        }
                        break;
                    case Surface.ROTATION_90:
                    case Surface.ROTATION_270:
                        if (this.sensorOrientation == 0 || this.sensorOrientation == 180) {
                            swappedDimensions = true;
                        }
                        break;
                    default:
                        Log.e(TAG, "Display rotation is invalid: " + displayOrientation);
                }
            }

            Point displaySize = new Point(ScreenManager.getScreenWidth(context), ScreenManager.getScreenHeight(context));
            int rotatedPreviewWidth = width;
            int rotatedPreviewHeight = height;
            int maxPreviewWidth = displaySize.x;
            int maxPreviewHeight = displaySize.y;

            if (swappedDimensions) {
                rotatedPreviewWidth = height;
                rotatedPreviewHeight = width;
                maxPreviewWidth = displaySize.y;
                maxPreviewHeight = displaySize.x;
            }

            if (maxPreviewWidth > MAX_PREVIEW_WIDTH) {
                maxPreviewWidth = MAX_PREVIEW_WIDTH;
            }

            if (maxPreviewHeight > MAX_PREVIEW_HEIGHT) {
                maxPreviewHeight = MAX_PREVIEW_HEIGHT;
            }

            // Danger, W.R.! Attempting to use too large a preview size could  exceed the camera
            // bus' bandwidth limitation, resulting in gorgeous previews but the storage of
            // garbage capture data.
            Size[] outputSizes = ScreenManager.sizeToSize(map.getOutputSizes(SurfaceTexture.class));
            Size[] outputSizesMediaRecorder = ScreenManager.sizeToSize(map.getOutputSizes(MediaRecorder.class));
            previewSize = chooseOptimalSize(outputSizes, rotatedPreviewWidth, rotatedPreviewHeight, maxPreviewWidth, maxPreviewHeight, largest);

            // We fit the aspect ratio of TextureView to the size of preview we picked.
            int orientation = displayOrientation;
            if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
                mTextureView.setAspectRatio(previewSize.getWidth(), previewSize.getHeight());
            } else {
                mTextureView.setAspectRatio(previewSize.getHeight(), previewSize.getWidth());
            }

            // Check if the flash is supported.
            Boolean available = characteristics.get(CameraCharacteristics.FLASH_INFO_AVAILABLE);
            mFlashSupported = available == null ? false : available;

            configureTransform(width, height);

            cameraManager.openCamera(cameraId, mStateCallback, backgroundHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        } catch (InterruptedException e) {
            throw new RuntimeException("Interrupted while trying to lock camera opening.", e);
        } catch (NullPointerException e) {
            // Currently an NPE is thrown when the Camera2API is used but not supported on the
            // device this code runs.
            Log.d(TAG, "Camera Error: "+e.getMessage());
        }
    }

    /**
     * Lock the focus as the first step for a still image capture.
     */
    private void lockFocus() {
        try {
            // This is how to tell the camera to lock focus.
            previewRequestBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER, CameraMetadata.CONTROL_AF_TRIGGER_START);
            // Tell #mCaptureCallback to wait for the lock.
            state = STATE_WAITING_LOCK;
            previewRequest = previewRequestBuilder.build();
            captureSession.capture(previewRequest, mCaptureCallback, backgroundHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    /**
     * Unlock the focus. This method should be called when still image capture sequence is
     * finished.
     */
    private void unlockFocus() {
        try {
            // Reset the auto-focus trigger
            previewRequestBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER, CameraMetadata.CONTROL_AF_TRIGGER_CANCEL);
            if(mFlashSupported) {
                previewRequestBuilder.set(CaptureRequest.CONTROL_AE_MODE, flashMode);
            }
            captureSession.capture(previewRequestBuilder.build(), mCaptureCallback, backgroundHandler);
            // After this, the camera will go back to the normal state of preview.
            state = STATE_PREVIEW;
            captureSession.setRepeatingRequest(previewRequest, mCaptureCallback, backgroundHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    /**
     * Run the precapture sequence for capturing a still image. This method should be called when
     * we get a response in {@link #mCaptureCallback} from {@link #lockFocus()}.
     */
    private void runPrecaptureSequence() {
        try {
            // This is how to tell the camera to trigger.
            previewRequestBuilder.set(CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER, CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER_START);
            // Tell #mCaptureCallback to wait for the precapture sequence to be set.
            state = STATE_WAITING_PRE_CAPTURE;
            captureSession.capture(previewRequestBuilder.build(), mCaptureCallback, backgroundHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    /**
     * Capture a still picture. This method should be called when we get a response in
     * {@link #mCaptureCallback} from both {@link #lockFocus()}.
     */
    private void captureStillPicture() {
        try {
            if (null == cameraDevice) {
                return;
            }
            if(mShutterCallback != null) {
                mShutterCallback.onShutter();
            }
            // This is the CaptureRequest.Builder that we use to take a picture.
            final CaptureRequest.Builder captureBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
            captureBuilder.addTarget(mImageReaderStill.getSurface());

            // Use the same AE and AF modes as the preview.
            captureBuilder.set(CaptureRequest.CONTROL_AF_MODE, focusMode);
            if(mFlashSupported) {
                captureBuilder.set(CaptureRequest.CONTROL_AE_MODE, flashMode);
            }

            // Orientation
            captureBuilder.set(CaptureRequest.JPEG_ORIENTATION, calculateOrientation(displayOrientation));

            CameraCaptureSession.CaptureCallback CaptureCallback = new CameraCaptureSession.CaptureCallback() {

                @Override
                public void onCaptureCompleted(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull TotalCaptureResult result) {
                    unlockFocus();
                }
            };

            captureSession.stopRepeating();
            captureSession.capture(captureBuilder.build(), CaptureCallback, null);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private void closePreviewSession() {
        if(captureSession != null) {
            captureSession.close();
            captureSession = null;
        }
    }

    /**
     * Creates a new {@link CameraCaptureSession} for camera preview.
     */
    private void createCameraPreviewSession() {
        try {
            SurfaceTexture texture = mTextureView.getSurfaceTexture();
            assert texture != null;

            // We configure the size of default buffer to be the size of camera preview we want.
            texture.setDefaultBufferSize(previewSize.getWidth(), previewSize.getHeight());

            mImageReaderPreview = ImageReader.newInstance(previewSize.getWidth(), previewSize.getHeight(), ImageFormat.YUV_420_888, 1);
            mImageReaderPreview.setOnImageAvailableListener(mOnPreviewAvailableListener, backgroundHandler);

            // This is the output Surface we need to start preview.
            Surface surface = new Surface(texture);

            // We set up a CaptureRequest.Builder with the output Surface.
            previewRequestBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            previewRequestBuilder.addTarget(surface);
            previewRequestBuilder.addTarget(mImageReaderPreview.getSurface());

            // Here, we create a CameraCaptureSession for camera preview.
            cameraDevice.createCaptureSession(Arrays.asList(surface, mImageReaderPreview.getSurface(), mImageReaderStill.getSurface()), new CameraCaptureSession.StateCallback() {
                @Override
                public void onConfigured(@NonNull CameraCaptureSession cameraCaptureSession) {
                    // The camera is already closed
                    if (null == cameraDevice) {
                        return;
                    }

                    // When the session is ready, we start displaying the preview.
                    captureSession = cameraCaptureSession;

                    try {
                        // Auto focus should be continuous for camera preview.
                        previewRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE, focusMode);
                        if(mFlashSupported) {
                            previewRequestBuilder.set(CaptureRequest.CONTROL_AE_MODE, flashMode);
                        }

                        // Finally, we start displaying the camera preview.
                        previewRequest = previewRequestBuilder.build();
                        captureSession.setRepeatingRequest(previewRequest, mCaptureCallback, backgroundHandler);
                    } catch (CameraAccessException e) {
                        e.printStackTrace();
                    }
                }

                @Override
                public void onConfigureFailed(@NonNull CameraCaptureSession cameraCaptureSession) {Log.d(TAG, "Camera Configuration failed!");}
            }, backgroundHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    /**
     * Retrieves the JPEG orientation from the specified screen rotation.
     *
     * @param rotation The screen rotation.
     * @return The JPEG orientation (one of 0, 90, 270, and 360)
     */
    private int calculateOrientation(int rotation) {
        // Sensor orientation is 90 for most devices, or 270 for some devices (eg. Nexus 5X)
        // We have to take that into account and rotate JPEG properly.
        // For devices with orientation of 90, we simply return our mapping from ORIENTATIONS.
        // For devices with orientation of 270, we need to rotate the JPEG 180 degrees.
        return (ORIENTATIONS.get(rotation) + sensorOrientation + 270) % 360;
    }

    private byte[] convertYUV420888ToNV21(Image imgYUV420) {
        // Converting YUV_420_888 data to NV21.
        byte[] data;
        ByteBuffer buffer0 = imgYUV420.getPlanes()[0].getBuffer();
        ByteBuffer buffer2 = imgYUV420.getPlanes()[2].getBuffer();
        int buffer0_size = buffer0.remaining();
        int buffer2_size = buffer2.remaining();
        data = new byte[buffer0_size + buffer2_size];
        buffer0.get(data, 0, buffer0_size);
        buffer2.get(data, buffer0_size, buffer2_size);
        return data;
    }

    public byte[] quarterNV21(byte[] data, int iWidth, int iHeight) {
        // Reduce to quarter size the NV21 frame
        byte[] yuv = new byte[iWidth/4 * iHeight/4 * 3 / 2];
        // halve yuma
        int i = 0;
        for (int y = 0; y < iHeight; y+=4) {
            for (int x = 0; x < iWidth; x+=4) {
                yuv[i] = data[y * iWidth + x];
                i++;
            }
        }
        return yuv;
    }

    //==============================================================================================
    //                                  Camera builder
    //==============================================================================================

    /**
     *  Builder for configuring and creating an associated project source.
     */
    public static class Builder {

        //  Defines an OCR detector.
        private final Detector<?> detector;

        //  Defines a new camera source.
        private Camera2Controller cameraController = new Camera2Controller();

        /**
         *  Creates an application source builder with the supplied context and detector. Camera
         * preview images will be streamed to the associated detector upon starting the application
         * source.
         */
        public Builder(Context context, Detector<?> detector) {

            if (context == null) {
                throw new IllegalArgumentException("No context supplied.");
            }

            if (detector == null) {
                throw new IllegalArgumentException("No detector supplied.");
            }

            this.detector = detector;
            cameraController.context = context;
        }

        /**
         *  Sets the focus mode of the camera.
         */
        public Builder setFocusMode(int mode) {
            cameraController.focusMode = mode;
            return this;
        }

        /**
         *  Sets the flash mode of the camera.
         */
        public Builder setFlashMode(int mode) {
            cameraController.flashMode = mode;
            return this;
        }

        /**
         *  Sets the camera facing to use -- either CAMERA_FACING_BACK or CAMERA_FACING_FRONT
         * (Default: back facing).
         */
        public Builder setFacing(int facing) {
            if ((facing != CAMERA_FACING_BACK) && (facing != CAMERA_FACING_FRONT)) {
                throw new IllegalArgumentException("Invalid camera: " + facing);
            }
            cameraController.facing = facing;
            return this;
        }

        /**
         *  Creates an instance of the camera source.
         */
        public Camera2Controller build() {
            cameraController.frameProcessor = new Camera2Runnable(detector, cameraController);
            return cameraController;
        }
    }

    //==============================================================================================
    //                                      Callbacks
    //==============================================================================================

    private class PictureDoneCallback implements ImageReader.OnImageAvailableListener {
        private PictureCallback mDelegate;

        @Override
        public void onImageAvailable(ImageReader reader) {
            if(mDelegate != null) {
                mDelegate.onPictureTaken(reader.acquireNextImage());
            }
        }

    };

    /**
     * Callback interface used to signal the moment of actual image capture.
     */
    public interface ShutterCallback {
        /**
         * Called as near as possible to the moment when a photo is captured from the sensor. This
         * is a good opportunity to play a shutter sound or give other feedback of camera operation.
         * This may be some time after the photo was triggered, but some time before the actual data
         * is available.
         */
        void onShutter();
    }

    /**
     * Callback interface used to supply image data from a photo capture.
     */
    public interface PictureCallback {
        /**
         * Called when image data is available after a picture is taken.  The format of the data
         * is a JPEG Image.
         */
        void onPictureTaken(Image image);
    }

    /**
     * Callback interface used to notify on completion of camera auto focus.
     */
    public interface AutoFocusCallback {
        /**
         * Called when the camera auto focus completes.  If the camera
         * does not support auto-focus and autoFocus is called,
         * onAutoFocus will be called immediately with a fake value of
         * <code>success</code> set to <code>true</code>.
         * <p/>
         * The auto-focus routine does not lock auto-exposure and auto-white
         * balance after it completes.
         *
         * @param success true if focus was successful, false if otherwise
         */
        void onAutoFocus(boolean success);
    }


}