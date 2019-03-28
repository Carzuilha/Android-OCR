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
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.ImageReader;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.support.annotation.NonNull;
import android.support.annotation.RequiresPermission;
import android.support.v4.content.ContextCompat;
import android.util.Log;
import android.util.Range;
import android.util.SparseIntArray;
import android.view.Surface;

import com.carzuilha.ocr.thread.Camera2Runnable;
import com.carzuilha.ocr.util.ScreenManager;
import com.carzuilha.ocr.view.DynamicTextureView;
import com.google.android.gms.common.images.Size;
import com.google.android.gms.vision.Detector;
import com.google.android.gms.vision.Frame;

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
    public static final String TAG = "Camera2Controller";

    //  Defines the camera type.
    @SuppressLint("InlinedApi")
    public static final int CAMERA_FACING_BACK = 0;
    @SuppressLint("InlinedApi")
    public static final int CAMERA_FACING_FRONT = 1;

    //  If the absolute difference between a preview size aspect ratio and a picture size aspect
    // ratio is less than this tolerance, they are considered to be the same aspect ratio.
    private static final double ASPECT_RATIO_TOLERANCE = 0.01;

    //  Defines the camera FPS. It is possible to let the user changes the value, but (for now) I
    // found it to unstable.
    private static final float REQUESTED_FPS = 30.0f;

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
        ORIENTATIONS.append(Surface.ROTATION_0, 0);
        ORIENTATIONS.append(Surface.ROTATION_90, 90);
        ORIENTATIONS.append(Surface.ROTATION_180, 180);
        ORIENTATIONS.append(Surface.ROTATION_270, 270);
    }

    static {
        INVERSE_ORIENTATIONS.append(Surface.ROTATION_0, 270);
        INVERSE_ORIENTATIONS.append(Surface.ROTATION_90, 180);
        INVERSE_ORIENTATIONS.append(Surface.ROTATION_180, 90);
        INVERSE_ORIENTATIONS.append(Surface.ROTATION_270, 0);
    }

    //  Max preview width, height, and ratio tolerance.
    private int maxPreviewWidth = 1920;
    private int maxPreviewHeight = 1080;
    private double maxRatioTolerance = 0.18;

    //  Defines the default camera.
    private int selectedCamera = CAMERA_FACING_BACK;

    //  Contains the camera state, the focus and the flash values.
    private int state = STATE_PREVIEW;
    private int focusMode = CAMERA_AF_AUTO;
    private int flashMode = CAMERA_FLASH_OFF;

    //  Contains the device rotation and sensor orientation of the camera.
    private int rotation;
    private int orientation;

    //  A set of references for all the resources to camera manipulation.
    private CameraDevice cameraDevice;
    private CameraManager cameraManager = null;
    private CameraCaptureSession captureSession;
    private Semaphore cameraSemaphore = new Semaphore(1);

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
    private boolean flashSupported;
    private boolean cameraStarted = false;
    private boolean swappedDimensions = false;
    private boolean isMeteringAreaAFSupported = false;

    //  A set of camera callbacks.
    private ShutterCallback shutterCallback;
    private AutoFocusCallback autoFocusCallback;

    //  Graphic elements from the calling activity.
    private Rect sensorArraySize;
    private DynamicTextureView dynamicTextureView;
    private ImageReader imageReaderStill;
    private ImageReader imageReaderPreview;

    //  A callback object for the ImageReader. "onImageAvailable" will be called when a preview frame
    // is ready to be processed.
    private final ImageReader.OnImageAvailableListener onPreviewAvailableListener = new ImageReader.OnImageAvailableListener() {

        @Override
        public void onImageAvailable(ImageReader _reader) {

            Image mImage = _reader.acquireNextImage();

            if(mImage == null) {
                return;
            }

            frameProcessor.setNextFrame(convertYUV420888ToNV21(mImage));

            mImage.close();
        }
    };

    //  A capture callback that handles events related to JPEG capture.
    private CameraCaptureSession.CaptureCallback captureCallback = new CameraCaptureSession.CaptureCallback() {

        private void process(CaptureResult _result) {

            switch (state) {

                case STATE_PREVIEW: {
                    break;
                }
                case STATE_WAITING_LOCK: {

                    Integer afState = _result.get(CaptureResult.CONTROL_AF_STATE);

                    if (afState == null) {

                        captureStillPicture();

                    } else if (CaptureResult.CONTROL_AF_STATE_FOCUSED_LOCKED == afState
                            || CaptureResult.CONTROL_AF_STATE_NOT_FOCUSED_LOCKED == afState
                            || CaptureResult.CONTROL_AF_STATE_PASSIVE_UNFOCUSED == afState
                            || CaptureRequest.CONTROL_AF_STATE_PASSIVE_FOCUSED == afState
                            || CaptureRequest.CONTROL_AF_STATE_INACTIVE == afState) {

                        Integer aeState = _result.get(CaptureResult.CONTROL_AE_STATE);

                        if (aeState == null || aeState == CaptureResult.CONTROL_AE_STATE_CONVERGED) {
                            state = STATE_PICTURE_TAKEN;
                            captureStillPicture();
                        } else {
                            runPreCaptureSequence();
                        }
                    }

                    break;
                }
                case STATE_WAITING_PRE_CAPTURE: {

                    Integer aeState = _result.get(CaptureResult.CONTROL_AE_STATE);

                    if (aeState == null ||
                            aeState == CaptureResult.CONTROL_AE_STATE_PRECAPTURE ||
                            aeState == CaptureRequest.CONTROL_AE_STATE_FLASH_REQUIRED) {
                        state = STATE_WAITING_NON_PRE_CAPTURE;
                    }

                    break;
                }
                case STATE_WAITING_NON_PRE_CAPTURE: {

                    Integer aeState = _result.get(CaptureResult.CONTROL_AE_STATE);

                    if (aeState == null || aeState != CaptureResult.CONTROL_AE_STATE_PRECAPTURE) {
                        state = STATE_PICTURE_TAKEN;
                        captureStillPicture();
                    }

                    break;
                }
            }
        }

        @Override
        public void onCaptureProgressed(@NonNull CameraCaptureSession _session, @NonNull CaptureRequest _request, @NonNull CaptureResult _partialResult) {
            process(_partialResult);
        }

        @Override
        public void onCaptureCompleted(@NonNull CameraCaptureSession _session, @NonNull CaptureRequest _request, @NonNull TotalCaptureResult _result) {

            if(_request.getTag() == ("FOCUS_TAG")) {

                autoFocusCallback.onAutoFocus(true);
                previewRequestBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER, null);
                previewRequestBuilder.setTag("");
                previewRequest = previewRequestBuilder.build();

                try {
                    captureSession.setRepeatingRequest(previewRequest, captureCallback, backgroundHandler);
                } catch(CameraAccessException ex) {
                    Log.d(TAG, "Auto focus exception:  "+ ex);
                }

            } else {
                process(_result);
            }
        }

        @Override
        public void onCaptureFailed(@NonNull CameraCaptureSession _session, @NonNull CaptureRequest _request, @NonNull CaptureFailure _failure) {

            if(_request.getTag() == "FOCUS_TAG") {

                Log.d(TAG, "Manual focus exception: "+ _failure);
                autoFocusCallback.onAutoFocus(false);
            }
        }
    };

    //  The state callback is called when camera device changes its state.
    private CameraDevice.StateCallback stateCallback = new CameraDevice.StateCallback() {

        @Override
        public void onOpened(@NonNull CameraDevice _cameraDevice) {

            cameraSemaphore.release();

            Camera2Controller.this.cameraDevice = _cameraDevice;
            createCaptureSession();
        }

        @Override
        public void onDisconnected(@NonNull CameraDevice _cameraDevice) {

            cameraSemaphore.release();

            _cameraDevice.close();
            Camera2Controller.this.cameraDevice = null;
        }

        @Override
        public void onError(@NonNull CameraDevice _cameraDevice, int _error) {

            cameraSemaphore.release();

            _cameraDevice.close();
            Camera2Controller.this.cameraDevice = null;
        }
    };

    //  A callback object for the ImageReader. "onImageAvailable" will be called when a still image
    // is ready to be saved.
    private PictureDoneCallback onImageAvailableListener = new PictureDoneCallback();

    //==============================================================================================
    //                                  Default methods
    //==============================================================================================

    /**
     *  Only allow creation via the builder class.
     */
    private Camera2Controller() { }

    /**
     *  Returns the selected camera.
     *
     * @return      The id of the selected camera (CAMERA_FACING_BACK or CAMERA_FACING_FRONT).
     */
    public int getSelectedCamera() {
        return selectedCamera;
    }

    /**
     *  Returns the current orientation of the detector.
     *
     * @return      The detector orientation.
     */
    public int getDetectorOrientation() { return getDetectorOrientation(orientation); }

    /**
     *  Returns the current orientation based on a sensor.
     *
     * @param   _sensorOrientation  The reference sensor.
     * @return                      The detector orientation.
     */
    public int getDetectorOrientation(int _sensorOrientation) {

        switch (_sensorOrientation) {
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
    //                              Create/Start/Stop/Release
    //==============================================================================================

    /**
     * Creates a new capture session for camera preview.
     */
    private void createCaptureSession() {

        try {

            SurfaceTexture texture = dynamicTextureView.getSurfaceTexture();
            assert texture != null;

            // We configure the size of default buffer to be the size of camera preview we want.
            texture.setDefaultBufferSize(previewSize.getWidth(), previewSize.getHeight());

            imageReaderPreview = ImageReader.newInstance(previewSize.getWidth(), previewSize.getHeight(), ImageFormat.YUV_420_888, 1);
            imageReaderPreview.setOnImageAvailableListener(onPreviewAvailableListener, backgroundHandler);

            // This is the output Surface we need to start preview.
            Surface surface = new Surface(texture);

            // We set up a CaptureRequest.Builder with the output Surface.
            previewRequestBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            previewRequestBuilder.addTarget(surface);
            previewRequestBuilder.addTarget(imageReaderPreview.getSurface());

            // Here, we create a CameraCaptureSession for camera preview.
            cameraDevice.createCaptureSession(
                    Arrays.asList(surface, imageReaderPreview.getSurface(), imageReaderStill.getSurface()),
                    new CameraCaptureSession.StateCallback() {

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
                                if(flashSupported) {
                                    previewRequestBuilder.set(CaptureRequest.CONTROL_AE_MODE, flashMode);
                                }

                                // Finally, we start displaying the camera preview.
                                previewRequest = previewRequestBuilder.build();
                                captureSession.setRepeatingRequest(previewRequest, captureCallback, backgroundHandler);
                            } catch (CameraAccessException e) {
                                e.printStackTrace();
                            }
                        }

                        @Override
                        public void onConfigureFailed(@NonNull CameraCaptureSession cameraCaptureSession) {
                            Log.d(TAG, "Camera Configuration failed!");
                        }
                    }, backgroundHandler);

        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    /**
     * Opens the camera and starts sending preview frames to the underlying detector.  The supplied
     * texture view is used for the preview so frames can be displayed to the user.
     *
     * @param   _textureView            The surface holder to use for the preview frames.
     */
    @RequiresPermission(Manifest.permission.CAMERA)
    public Camera2Controller start(@NonNull DynamicTextureView _textureView) {

        if(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {

            if (cameraStarted) {
                return this;
            }

            cameraStarted = true;
            startBackgroundThread();

            processingThread = new Thread(frameProcessor);
            frameProcessor.setActive(true);
            processingThread.start();

            dynamicTextureView = _textureView;

            if (dynamicTextureView.isAvailable()) {
                setUpCameraOutputs(dynamicTextureView.getWidth(), dynamicTextureView.getHeight());
            }
        }

        return this;
    }

    /**
     *  Closes the camera and stops sending frames to the underlying frame detector. This source may
     * be restarted again by calling start() or start(SurfaceHolder). Call release() instead to
     * completely shut down this source and release the resources of the underlying detector.
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

            cameraSemaphore.acquire();

            if (null != captureSession) {
                captureSession.close();
                captureSession = null;
            }

            if (null != cameraDevice) {
                cameraDevice.close();
                cameraDevice = null;
            }

            if (null != imageReaderPreview) {
                imageReaderPreview.close();
                imageReaderPreview = null;
            }

            if (null != imageReaderStill) {
                imageReaderStill.close();
                imageReaderStill = null;
            }

        } catch (InterruptedException e) {
            throw new RuntimeException("Interrupted while trying to lock camera closing.", e);
        } finally {

            cameraSemaphore.release();
            stopBackgroundThread();
        }
    }

    /**
     *  Stops the application and releases its resources and underlying detector.
     */
    public void release() {
        stop();
        frameProcessor.release();
    }

    //==============================================================================================
    //                                  Execution methods
    //==============================================================================================

    /**
     *  Given _choices of sizes supported by a camera, choose the smallest one that is at least as
     * large as the respective texture view size, and that is at most as large as the respective max
     * size, and whose aspect ratio matches with the specified value. If such size doesn't exist,
     * choose the largest one that is at most as large as the respective max size, and whose aspect
     * ratio matches with the specified value.
     *
     * @param   _choices            The list of sizes that the camera supports for the intended output
     *                              class.
     * @param   _textureWidth       The width of the texture view relative to sensor coordinate.
     * @param   _textureHeight      The height of the texture view relative to sensor coordinate.
     * @param   _maxWidth           The maximum width that can be chosen.
     * @param   _maxHeight          The maximum height that can be chosen.
     * @param   _aspectRatio        The aspect ratio.
     * @return                      The optimal size, or an arbitrary one if none were big enough.
     */
    private static Size chooseOptimalSize(Size[] _choices, int _textureWidth, int _textureHeight, int _maxWidth, int _maxHeight, Size _aspectRatio) {

        //  Collect the supported resolutions that are at least as big, and smaller, as the preview
        // surface.
        List<Size> bigEnough = new ArrayList<>();
        List<Size> notBigEnough = new ArrayList<>();

        int w = _aspectRatio.getWidth();
        int h = _aspectRatio.getHeight();

        for (Size option : _choices) {

            if (option.getWidth() <= _maxWidth &&
                option.getHeight() <= _maxHeight &&
                option.getHeight() == option.getWidth() * h / w) {

                if (option.getWidth() >= _textureWidth && option.getHeight() >= _textureHeight) {
                    bigEnough.add(option);
                } else {
                    notBigEnough.add(option);
                }
            }
        }

        //  Pick the smallest of those big enough. If there is no one big enough, pick the
        // largest of those not big enough.
        if (bigEnough.size() > 0) {
            return Collections.min(bigEnough, new CompareSizesByArea());
        } else if (notBigEnough.size() > 0) {
            return Collections.max(notBigEnough, new CompareSizesByArea());
        } else {

            Log.e(TAG, "Couldn't find any suitable preview size.");
            return _choices[0];
        }
    }

    /**
     *  Selects the most suitable preview frames per second range, given the desired frames per
     * second.
     *
     * @param   _cameraCharacteristics      The camera characteristics.
     * @return                              The selected preview frames per second range.
     */
    private Range<Integer> selectPreviewFpsRange(CameraCharacteristics _cameraCharacteristics) {

        //  The application API uses integers scaled by a factor of 1000 instead of floating-point frame
        // rates.
        int desiredPreviewFpsScaled = (int) (REQUESTED_FPS * 1000.0f);

        //  The method for selecting the best range is to minimize the sum of the differences between
        // the desired value and the upper and lower bounds of the range. This may camera a range
        // that the desired value is outside of, but this is often preferred. For example, if the
        // desired frame rate is 29.97, the range (30, 30) is probably more desirable than the
        // range (15, 30).
        Range selectedFpsRange = null;
        int minDiff = Integer.MAX_VALUE;

        Range<Integer>[] previewFpsRangeList = _cameraCharacteristics.get(CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES);

        for (Range<Integer> range : previewFpsRangeList) {

            int deltaMin = desiredPreviewFpsScaled - range.getLower();
            int deltaMax = desiredPreviewFpsScaled - range.getUpper();
            int diff = Math.abs(deltaMin) + Math.abs(deltaMax);

            if (diff < minDiff) {
                selectedFpsRange = range;
                minDiff = diff;
            }
        }

        return selectedFpsRange;
    }

    /**
     *  Starts a background thread and its handler.
     */
    private void startBackgroundThread() {

        backgroundThread = new HandlerThread("CameraBackground");
        backgroundThread.start();

        backgroundHandler = new Handler(backgroundThread.getLooper());
    }

    /**
     *  Stops the background thread and its handler.
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
     *  Returns the best aspect picture size for the screen.
     *
     * @param   _supportedPictureSizes  A list with all the supported picture sizes.
     * @return                          The best aspect picture size.
     */
    private Size getBestAspectPictureSize(android.util.Size[] _supportedPictureSizes) {

        float targetRatio = ScreenManager.getScreenRatio(context);

        Size bestSize = null;
        TreeMap<Double, List<android.util.Size>> diffs = new TreeMap<>();

        //  Select supported sizes which ratio is less than ASPECT_RATIO_TOLERANCE.
        for (android.util.Size size : _supportedPictureSizes) {

            float ratio = (float) size.getWidth() / size.getHeight();
            double diff = Math.abs(ratio - targetRatio);

            if (diff < ASPECT_RATIO_TOLERANCE){

                if (diffs.keySet().contains(diff)){
                    diffs.get(diff).add(size);
                } else {
                    List<android.util.Size> newList = new ArrayList<>();

                    newList.add(size);
                    diffs.put(diff, newList);
                }
            }
        }

        //  If no sizes were supported (strange situation), establish a higher ASPECT_RATIO_TOLERANCE.
        if(diffs.isEmpty()) {

            for (android.util.Size size : _supportedPictureSizes) {

                float ratio = (float)size.getWidth() / size.getHeight();
                double diff = Math.abs(ratio - targetRatio);

                if (diff < maxRatioTolerance){

                    if (diffs.keySet().contains(diff)){
                        diffs.get(diff).add(size);
                    } else {
                        List<android.util.Size> newList = new ArrayList<>();

                        newList.add(size);
                        diffs.put(diff, newList);
                    }
                }
            }
        }

        //  Select the highest resolution from the ratio filtered ones.
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
     *  Configures the necessary matrix transformation to `dynamicTextureView`. This method should
     * be called after the camera preview size is determined in setUpCameraOutputs and also the size
     * of `dynamicTextureView` is fixed.
     *
     * @param   _viewWidth  The width of `dynamicTextureView`
     * @param   _viewHeight The height of `dynamicTextureView`
     */
    private void configureTransform(int _viewWidth, int _viewHeight) {

        if (null == dynamicTextureView || null == previewSize) {
            return;
        }

        int rotation = this.rotation;

        Matrix matrix = new Matrix();
        RectF viewRect = new RectF(0, 0, _viewWidth, _viewHeight);
        RectF bufferRect = new RectF(0, 0, previewSize.getHeight(), previewSize.getWidth());

        float centerX = viewRect.centerX();
        float centerY = viewRect.centerY();

        if (Surface.ROTATION_90 == rotation || Surface.ROTATION_270 == rotation) {

            bufferRect.offset(centerX - bufferRect.centerX(), centerY - bufferRect.centerY());
            matrix.setRectToRect(viewRect, bufferRect, Matrix.ScaleToFit.FILL);

            float scale = Math.max((float) _viewHeight / previewSize.getHeight(),
                                   (float) _viewWidth / previewSize.getWidth());

            matrix.postScale(scale, scale, centerX, centerY);
            matrix.postRotate(90 * (rotation - 2), centerX, centerY);

        } else if (Surface.ROTATION_180 == rotation) {
            matrix.postRotate(180, centerX, centerY);
        }

        dynamicTextureView.setTransform(matrix);
    }

    /**
     *  Sets up member variables related to camera.
     *
     * @param   _width  The _width of available size for camera preview
     * @param   _height The _height of available size for camera preview
     */
    private void setUpCameraOutputs(int _width, int _height) {

        try {

            if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                return;
            }

            if (!cameraSemaphore.tryAcquire(2500, TimeUnit.MILLISECONDS)) {
                throw new RuntimeException("Time out waiting to lock camera opening.");
            }

            if(cameraManager == null) {
                cameraManager = (CameraManager) context.getSystemService(Context.CAMERA_SERVICE);
            }

            String cameraId = cameraManager.getCameraIdList()[selectedCamera];

            CameraCharacteristics characteristics = cameraManager.getCameraCharacteristics(cameraId);
            StreamConfigurationMap map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);

            if (map == null) {
                return;
            }

            //  For still image captures, we use the largest available size.
            Size largest = getBestAspectPictureSize(map.getOutputSizes(ImageFormat.JPEG));

            imageReaderStill = ImageReader.newInstance(largest.getWidth(), largest.getHeight(), ImageFormat.JPEG, 2);
            imageReaderStill.setOnImageAvailableListener(onImageAvailableListener, backgroundHandler);
            sensorArraySize = characteristics.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE);

            Integer maxAFRegions = characteristics.get(CameraCharacteristics.CONTROL_MAX_REGIONS_AF);

            if(maxAFRegions != null) {
                isMeteringAreaAFSupported = maxAFRegions >= 1;
            }

            //  Finds the screen rotation.
            rotation = ScreenManager.getScreenRotation(context);

            //  Find out if we need to swap dimension to get the preview size relative to sensor
            // coordinate.
            Integer sOrientation = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION);

            if(sOrientation != null) {

                orientation = sOrientation;

                switch (rotation) {

                    case Surface.ROTATION_0:
                    case Surface.ROTATION_180:

                        if (orientation == 90 || orientation == 270) {
                            swappedDimensions = true;
                        }
                        break;

                    case Surface.ROTATION_90:
                    case Surface.ROTATION_270:

                        if (orientation == 0 || orientation == 180) {
                            swappedDimensions = true;
                        }
                        break;

                    default:
                        Log.e(TAG, "Display rotation is invalid: " + rotation);
                }
            }

            Point displaySize = new Point(ScreenManager.getScreenWidth(context), ScreenManager.getScreenHeight(context));

            int rPreviewWidth = _width;
            int rPreviewHeight = _height;
            int mPreviewWidth = displaySize.x;
            int mPreviewHeight = displaySize.y;

            if (swappedDimensions) {
                rPreviewWidth = _height;
                rPreviewHeight = _width;
                mPreviewWidth = displaySize.y;
                mPreviewHeight = displaySize.x;
            }

            if (mPreviewWidth > maxPreviewWidth) {
                mPreviewWidth = maxPreviewWidth;
            }

            if (mPreviewHeight > maxPreviewHeight) {
                mPreviewHeight = maxPreviewHeight;
            }

            //  Danger, W.R.! Attempting to use too large a preview size could  exceed the camera
            // bus' bandwidth limitation, resulting in gorgeous previews but the storage of garbage
            // capture data.
            Size[] outputSizes = ScreenManager.sizeToSize(map.getOutputSizes(SurfaceTexture.class));

            previewSize = chooseOptimalSize(outputSizes, rPreviewWidth, rPreviewHeight, mPreviewWidth, mPreviewHeight, largest);

            //  Fits the aspect ratio of TextureView to the size of preview we picked.
            int orientation = rotation;

            if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
                dynamicTextureView.setAspectRatio(previewSize.getWidth(), previewSize.getHeight());
            } else {
                dynamicTextureView.setAspectRatio(previewSize.getHeight(), previewSize.getWidth());
            }

            //  Check if the flash is supported.
            Boolean available = characteristics.get(CameraCharacteristics.FLASH_INFO_AVAILABLE);
            flashSupported = available == null ? false : available;

            configureTransform(_width, _height);

            cameraManager.openCamera(cameraId, stateCallback, backgroundHandler);

        } catch (CameraAccessException e) {
            e.printStackTrace();
        } catch (InterruptedException e) {
            throw new RuntimeException("Interrupted while trying to lock camera opening.", e);
        } catch (NullPointerException e) {
            Log.d(TAG, "Camera Error: "+e.getMessage());
        }
    }

    /**
     *  Unlock the focus. This method should be called when still image capture sequence is
     *  finished.
     */
    private void unlockFocus() {

        try {

            // Reset the auto-focus trigger.
            previewRequestBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER, CameraMetadata.CONTROL_AF_TRIGGER_CANCEL);

            if(flashSupported) {
                previewRequestBuilder.set(CaptureRequest.CONTROL_AE_MODE, flashMode);
            }

            captureSession.capture(previewRequestBuilder.build(), captureCallback, backgroundHandler);

            // After this, the camera will go back to the normal state of preview.
            state = STATE_PREVIEW;
            captureSession.setRepeatingRequest(previewRequest, captureCallback, backgroundHandler);

        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    /**
     * Run the pre-capture sequence for capturing a still image. This method should be called when
     * we get a response in captureCallback from lockFocus().
     */
    private void runPreCaptureSequence() {

        try {

            // This is how to tell the camera to trigger.
            previewRequestBuilder.set(CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER, CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER_START);

            // Tell #captureCallback to wait for the pre-capture sequence to be set.
            state = STATE_WAITING_PRE_CAPTURE;
            captureSession.capture(previewRequestBuilder.build(), captureCallback, backgroundHandler);

        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    /**
     *  Capture a still picture. This method should be called when we get a response in captureCallback
     * from both lockFocus().
     */
    private void captureStillPicture() {

        try {

            if (null == cameraDevice) {
                return;
            }

            if(shutterCallback != null) {
                shutterCallback.onShutter();
            }

            // This is the CaptureRequest.Builder that we use to take a picture.
            final CaptureRequest.Builder captureBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
            captureBuilder.addTarget(imageReaderStill.getSurface());

            // Use the same AE and AF modes as the preview.
            captureBuilder.set(CaptureRequest.CONTROL_AF_MODE, focusMode);

            if(flashSupported) {
                captureBuilder.set(CaptureRequest.CONTROL_AE_MODE, flashMode);
            }

            // Orientation
            captureBuilder.set(CaptureRequest.JPEG_ORIENTATION, calculateOrientation(rotation));

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

    /**
     *  Retrieves the JPEG orientation from the specified screen _rotation.
     *
     * @param   _rotation   The screen _rotation.
     * @return              The JPEG orientation (one of 0, 90, 270, and 360)
     */
    private int calculateOrientation(int _rotation) {
        // Sensor orientation is 90 for most devices, or 270 for some devices (eg. Nexus 5X)
        // We have to take that into account and rotate JPEG properly.
        // For devices with orientation of 90, we simply return our mapping from ORIENTATIONS.
        // For devices with orientation of 270, we need to rotate the JPEG 180 degrees.
        return (ORIENTATIONS.get(_rotation) + orientation + 270) % 360;
    }

    /**
     *  Converts an Yuv420888 image to NV21 image.
     *
     * @param   _imgYUV420      The Yuv420888 image.
     * @return                  Thw NV21 image.
     */
    private byte[] convertYUV420888ToNV21(Image _imgYUV420) {

        // Converting YUV_420_888 data to NV21.
        byte[] data;

        ByteBuffer buffer0 = _imgYUV420.getPlanes()[0].getBuffer();
        ByteBuffer buffer2 = _imgYUV420.getPlanes()[2].getBuffer();

        int buffer0_size = buffer0.remaining();
        int buffer2_size = buffer2.remaining();

        data = new byte[buffer0_size + buffer2_size];
        buffer0.get(data, 0, buffer0_size);
        buffer2.get(data, buffer0_size, buffer2_size);

        return data;
    }

    /**
     *  Reduce the size of a NV21 frame.
     *
     * @param   data        The original image.
     * @param   iWidth      The input'image width.
     * @param   iHeight     The input's image height.
     * @return              The reduced image.
     */
    public byte[] quarterNV21(byte[] data, int iWidth, int iHeight) {

        // Reduce to quarter size the NV21 frame.
        byte[] yuv = new byte[iWidth/4 * iHeight/4 * 3 / 2];

        // halve YUMA
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
    //                              Callbacks/Static classes
    //==============================================================================================

    /**
     * Compares two sizes based on their areas.
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
     *  Wraps the final callback in the camera sequence, so that we can automatically turn the camera
     * preview back on after the picture has been taken.
     */
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
         *  Called when _image data is available after a picture is taken.
         *
         * @param   _image      The picture taken.
         */
        void onPictureTaken(Image _image);
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
         *  Creates an application source builder with the supplied _context and _detector. Camera
         * preview images will be streamed to the associated _detector upon starting the application
         * source.
         */
        public Builder(Context _context, Detector<?> _detector) {

            if (_context == null) {
                throw new IllegalArgumentException("No _context supplied.");
            }

            if (_detector == null) {
                throw new IllegalArgumentException("No _detector supplied.");
            }

            this.detector = _detector;
            cameraController.context = _context;
        }

        /**
         *  Sets the camera to use -- either CAMERA_FACING_BACK or CAMERA_FACING_FRONT (Default:
         *  back camera).
         */
        public Builder camera(int facing) {

            if ((facing != CAMERA_FACING_BACK) && (facing != CAMERA_FACING_FRONT)) {
                throw new IllegalArgumentException("Invalid camera: " + facing);
            }

            cameraController.selectedCamera = facing;
            return this;
        }

        /**
         *  Sets the focus mode of the camera.
         */
        public Builder focus(int mode) {
            cameraController.focusMode = mode;
            return this;
        }

        /**
         *  Sets the flash mode of the camera.
         */
        public Builder flash(int mode) {
            cameraController.flashMode = mode;
            return this;
        }

        /**
         *  Sets the desired width and height of the application frames in pixels. If the exact desired
         * values are not available options, the best matching available options are selected.*
         *
         * @param   _width      The desired width.
         * @param   _height     The desired height.
         * @return              A new builder object.
         */
        public Builder previewSize(int _width, int _height) {

            // Restrict the requested range to something within the realm of possibility.  The
            // choice of 1000000 is a bit arbitrary -- intended to be well beyond resolutions that
            // devices can support.  We bound this to avoid int overflow in the code later.
            final int MAX = 1000000;

            if ((_width <= 0) || (_width > MAX) || (_height <= 0) || (_height > MAX)) {
                throw new IllegalArgumentException("Invalid preview size: " + _width + "x" + _height);
            }

            cameraController.maxPreviewWidth = _width;
            cameraController.maxPreviewHeight = _height;

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
}