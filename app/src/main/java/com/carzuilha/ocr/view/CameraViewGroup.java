package com.carzuilha.ocr.view;

import android.Manifest;
import android.content.Context;
import android.content.res.Configuration;
import android.support.annotation.RequiresPermission;
import android.util.AttributeSet;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.ViewGroup;

import com.carzuilha.ocr.control.OcrController;
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
    private SurfaceView surfaceView;
    private OcrController cameraSource;
    private GraphicView overlay;

    /**
     *  Initializes the CameraViewGroup detector and sets its parameters.
     *
     * @param   _context    The context to be utilized.
     * @param   _attrs      A set of attributes to be used with the context.
     */
    public CameraViewGroup(Context _context, AttributeSet _attrs) {

        super(_context, _attrs);

        context = _context;
        startRequested = false;
        surfaceAvailable = false;
        surfaceView = new SurfaceView(_context);
        surfaceView.getHolder().addCallback(new SurfaceCallback());

        addView(surfaceView);
    }

    /**
     *  Called when the application starts.
     *
     * @param   _cameraSource       The camera source to be utilized as OCR reference.
     * @throws  IOException         If there is any problem with the camera execution.
     * @throws  SecurityException   If the access for the camera is blocked.
     */
    @RequiresPermission(Manifest.permission.CAMERA)
    public void start(OcrController _cameraSource) throws IOException, SecurityException {

        if (_cameraSource == null) {
            stop();
        }

        cameraSource = _cameraSource;

        if (cameraSource != null) {
            startRequested = true;
            startIfReady();
        }
    }

    /**
     *  Called when the application starts.
     *
     * @param   _cameraSource       The camera source to be utilized as OCR reference.
     * @param   _overlay            The overlay to be utilized for the camera.
     * @throws  IOException         If there is any problem with the camera execution.
     * @throws  SecurityException   If the access for the camera is blocked.
     */
    @RequiresPermission(Manifest.permission.CAMERA)
    public void start(OcrController _cameraSource, GraphicView _overlay) throws IOException, SecurityException {

        overlay = _overlay;
        start(_cameraSource);
    }

    /**
     *  Called when the application is backgrounded, interrupting the camera.
     */
    public void stop() {

        if (cameraSource != null) {
            cameraSource.stop();
        }
    }

    /**
     *  Called when the application stops, disposing resources.
     */
    public void release() {

        if (cameraSource != null) {
            cameraSource.release();
            cameraSource = null;
        }
    }

    /**
     *  Starts the component if all are ready.
     *
     * @throws  IOException         If there is any problem with the camera execution.
     * @throws  SecurityException   If the access for the camera is blocked.
     */
    @RequiresPermission(Manifest.permission.CAMERA)
    private void startIfReady() throws IOException, SecurityException {

        if (startRequested && surfaceAvailable) {

            cameraSource.start(surfaceView.getHolder());

            if (overlay != null) {

                Size size = cameraSource.getPreviewSize();
                int min = Math.min(size.getWidth(), size.getHeight());
                int max = Math.max(size.getWidth(), size.getHeight());

                if (isPortraitMode()) {
                    overlay.setCameraInfo(min, max, cameraSource.getCameraFacing());
                } else {
                    overlay.setCameraInfo(max, min, cameraSource.getCameraFacing());
                }

                overlay.clear();
            }

            startRequested = false;
        }
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

        if (cameraSource != null) {

            Size size = cameraSource.getPreviewSize();

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
     *  Defines a custom callback for the component.
     */
    private class SurfaceCallback implements SurfaceHolder.Callback {

        /**
         *  Called when the surface is created.
         *
         * @param   _surface        The surface holder.
         */
        @Override
        public void surfaceCreated(SurfaceHolder _surface) {

            surfaceAvailable = true;

            try {
                startIfReady();
            } catch (SecurityException se) {
                Log.e(TAG,"Do not have permission to start application.", se);
            } catch (IOException e) {
                Log.e(TAG, "Could not start application source.", e);
            }
        }

        /**
         *  Called when the surface is destroyed.
         *
         * @param   _surface        The surface holder.
         */
        @Override
        public void surfaceDestroyed(SurfaceHolder _surface) {
            surfaceAvailable = false;
        }

        /**
         *  Called when the surface is changed.
         *
         * @param   _holder         The surface holder.
         * @param   _format         The new surface format.
         * @param   _width          The new surface width.
         * @param   _height         The new surface height.
         */
        @Override
        public void surfaceChanged(SurfaceHolder _holder, int _format, int _width, int _height) {
        }
    }
}
