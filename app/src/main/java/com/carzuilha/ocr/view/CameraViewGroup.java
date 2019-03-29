package com.carzuilha.ocr.view;

import android.Manifest;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.SurfaceTexture;
import android.support.annotation.RequiresPermission;
import android.util.AttributeSet;
import android.util.Log;
import android.view.TextureView;
import android.view.ViewGroup;

import com.carzuilha.ocr.control.CameraControl_A;
import com.carzuilha.ocr.control.CameraControl_B;
import com.carzuilha.ocr.main.MainActivity;
import com.google.android.gms.common.images.Size;

import java.io.IOException;

/**
 *  Defines a ViewGroup that contains the GraphicView object.
 */
public class CameraViewGroup extends ViewGroup {

    //  Defines the tag of the class.
    private static final String TAG = "CameraViewGroup";

    private boolean startRequested;
    private boolean surfaceAvailable;

    private Context context;
    private DynamicTextureView dynamicTextureView;
    private GraphicView graphicView;

    private CameraControl_A cameraControllerA = null;
    private CameraControl_B camera2Controller = null;

    //==============================================================================================
    //                                  Default methods
    //==============================================================================================

    /**
     *  Initializes the CameraViewGroup and sets its parameters.
     *
     * @param   _context        The context to be utilized.
     * @param   _attrs          A set of attributes to be used with the context.
     */
    public CameraViewGroup(Context _context, AttributeSet _attrs) {

        super(_context, _attrs);

        context = _context;
        startRequested = false;
        surfaceAvailable = false;
        dynamicTextureView = new DynamicTextureView(_context);
        dynamicTextureView.setSurfaceTextureListener(new TextureListener());

        addView(dynamicTextureView);
    }

    /**
     *  Called when the application starts.
     *
     * @param   _cameraControllerA      The camera source to be utilized as OCR reference.
     * @throws  IOException             If there is any problem with the camera execution.
     * @throws  SecurityException       If the access for the camera is blocked.
     */
    @RequiresPermission(Manifest.permission.CAMERA)
    public void start(CameraControl_A _cameraControllerA) throws IOException, SecurityException {

        if (_cameraControllerA == null) {
            stop();
        }

        cameraControllerA = _cameraControllerA;

        if (cameraControllerA != null) {
            startRequested = true;
            startIfReady();
        }
    }

    /**
     *  Called when the application starts.
     *
     * @param   _camera2Controller  The camera source to be utilized as OCR reference.
     * @throws  IOException         If there is any problem with the camera execution.
     * @throws  SecurityException   If the access for the camera is blocked.
     */
    @RequiresPermission(Manifest.permission.CAMERA)
    public void start(CameraControl_B _camera2Controller) throws IOException, SecurityException {

        if (_camera2Controller == null) {
            stop();
        }

        camera2Controller = _camera2Controller;

        if (camera2Controller != null) {
            startRequested = true;
            startIfReady();
        }
    }

    /**
     *  Called when the application starts.
     *
     * @param   _cameraControllerA   The camera source to be utilized as OCR reference.
     * @param   _overlay            The graphicView to be utilized for the camera.
     * @throws  IOException         If there is any problem with the camera execution.
     * @throws  SecurityException   If the access for the camera is blocked.
     */
    @RequiresPermission(Manifest.permission.CAMERA)
    public void start(CameraControl_A _cameraControllerA, GraphicView _overlay) throws IOException, SecurityException {

        graphicView = _overlay;
        start(_cameraControllerA);
    }


    /**
     *  Called when the application starts.
     *
     * @param   _camera2Controller  The camera source to be utilized as OCR reference.
     * @param   _overlay            The graphicView to be utilized for the camera.
     * @throws  IOException         If there is any problem with the camera execution.
     * @throws  SecurityException   If the access for the camera is blocked.
     */
    @RequiresPermission(Manifest.permission.CAMERA)
    public void start(CameraControl_B _camera2Controller, GraphicView _overlay) throws IOException, SecurityException {

        graphicView = _overlay;
        start(_camera2Controller);
    }

    /**
     *  Called when the application is backgrounded, interrupting the camera.
     */
    public void stop() {

        if (cameraControllerA != null) {
            cameraControllerA.stop();
        }
    }

    /**
     *  Called when the application stops, disposing resources.
     */
    public void release() {

        if (cameraControllerA != null) {
            cameraControllerA.release();
            cameraControllerA = null;
        }
    }

    //==============================================================================================
    //                                  Internal methods
    //==============================================================================================

    /**
     *  Starts the component if all are ready.
     *
     * @throws  IOException         If there is any problem with the camera execution.
     * @throws  SecurityException   If the access for the camera is blocked.
     */
    @RequiresPermission(Manifest.permission.CAMERA)
    private void startIfReady() throws IOException, SecurityException {

        if (startRequested && surfaceAvailable) {

            if (MainActivity.USE_LEGACY_CAMERA) {

                cameraControllerA.start(dynamicTextureView);

                if (graphicView != null) {

                    Size size = cameraControllerA.getPreviewSize();
                    int min = Math.min(size.getWidth(), size.getHeight());
                    int max = Math.max(size.getWidth(), size.getHeight());

                    if (isPortraitMode()) {
                        graphicView.setCameraInfo(min, max, cameraControllerA.getSelectedCamera());
                    } else {
                        graphicView.setCameraInfo(max, min, cameraControllerA.getSelectedCamera());
                    }

                    graphicView.clear();
                }

            }
            else {

                camera2Controller.start(dynamicTextureView);

                if (graphicView != null) {

                    Size size = camera2Controller.getPreviewSize();
                    int min = Math.min(size.getWidth(), size.getHeight());
                    int max = Math.max(size.getWidth(), size.getHeight());

                    if (isPortraitMode()) {
                        graphicView.setCameraInfo(min, max, camera2Controller.getSelectedCamera());
                    } else {
                        graphicView.setCameraInfo(max, min, camera2Controller.getSelectedCamera());
                    }

                    graphicView.clear();
                }

            }

            startRequested = false;
        }
    }

    /**
     *  Returns if the device is in portrait mode.
     *
     * @return      'true' if in portrait mode, 'false' otherwise.
     */
    private boolean isPortraitMode() {

        int orientation = context.getResources().getConfiguration().orientation;

        if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
            return false;
        }
        if (orientation == Configuration.ORIENTATION_PORTRAIT) {
            return true;
        }

        return false;
    }

    /**
     *  Called when the component layout were build or changed.
     *
     * @param   _changed    If the layout has changed or not.
     * @param   _left       The new left coordinate of the component.
     * @param   _top        The new top coordinate of the component.
     * @param   _right      The new right coordinate of the component.
     * @param   _bottom     The new bottom coordinate of the component.
     */
    @Override
    protected void onLayout(boolean _changed, int _left, int _top, int _right, int _bottom) {

        int previewWidth = 320;
        int previewHeight = 240;

        if (cameraControllerA != null) {

            Size size = cameraControllerA.getPreviewSize();

            if (size != null) {
                previewWidth = size.getWidth();
                previewHeight = size.getHeight();
            }
        }

        if (isPortraitMode()) {

            int tmp = previewWidth;

            previewWidth = previewHeight;
            previewHeight = tmp;
        }

        final int viewWidth = _right - _left;
        final int viewHeight = _bottom - _top;

        int childWidth;
        int childHeight;
        int childXOffset = 0;
        int childYOffset = 0;
        float widthRatio = (float) viewWidth / (float) previewWidth;
        float heightRatio = (float) viewHeight / (float) previewHeight;

        if (widthRatio > heightRatio) {

            childWidth = viewWidth;
            childHeight = (int) ((float) previewHeight * widthRatio);
            childYOffset = (childHeight - viewHeight) / 2;

        } else {

            childWidth = (int) ((float) previewWidth * heightRatio);
            childHeight = viewHeight;
            childXOffset = (childWidth - viewWidth) / 2;

        }

        for (int i = 0; i < getChildCount(); ++i) {
            getChildAt(i).layout(
                    -1 * childXOffset, -1 * childYOffset,
                    childWidth - childXOffset, childHeight - childYOffset);
        }

        try {
            startIfReady();
        } catch (SecurityException se) {
            Log.e(TAG,"Do not have permission to start the application.", se);
        } catch (IOException e) {
            Log.e(TAG, "Could not start application source.", e);
        }
    }

    //==============================================================================================
    //                                      Inner classes
    //==============================================================================================

    /**
     *  Defines a custom callback for the component.
     */
    private class TextureListener implements TextureView.SurfaceTextureListener {

        @Override
        public void onSurfaceTextureAvailable(SurfaceTexture surfaceTexture, int i, int i1) {

            surfaceAvailable = true;

            try {
                startIfReady();
            } catch (SecurityException se) {
                Log.e(TAG,"Do not have permission to start application.", se);
            } catch (IOException e) {
                Log.e(TAG,"Could not start application source.", e);
            }
        }

        @Override
        public void onSurfaceTextureSizeChanged(SurfaceTexture surfaceTexture, int i, int i1) {

        }

        @Override
        public boolean onSurfaceTextureDestroyed(SurfaceTexture surfaceTexture) {
            surfaceAvailable = false;
            return false;
        }

        @Override
        public void onSurfaceTextureUpdated(SurfaceTexture surfaceTexture) {

        }
    }

}
