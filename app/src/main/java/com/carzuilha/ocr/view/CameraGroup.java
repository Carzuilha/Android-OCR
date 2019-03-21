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

import com.carzuilha.ocr.model.CameraSource;
import com.google.android.gms.common.images.Size;

import java.io.IOException;

public class CameraGroup extends ViewGroup {

    private static final String TAG = "CameraViewGroup";

    private boolean startRequested;
    private boolean surfaceAvailable;

    private Context context;
    private SurfaceView surfaceView;
    private CameraSource cameraSource;
    private GraphicView overlay;

    public CameraGroup(Context _context, AttributeSet _attrs) {

        super(_context, _attrs);

        context = _context;
        startRequested = false;
        surfaceAvailable = false;
        surfaceView = new SurfaceView(_context);
        surfaceView.getHolder().addCallback(new SurfaceCallback());

        addView(surfaceView);
    }

    @RequiresPermission(Manifest.permission.CAMERA)
    public void start(CameraSource _cameraSource) throws IOException, SecurityException {

        if (_cameraSource == null) {
            stop();
        }

        cameraSource = _cameraSource;

        if (cameraSource != null) {
            startRequested = true;
            startIfReady();
        }
    }

    @RequiresPermission(Manifest.permission.CAMERA)
    public void start(CameraSource _cameraSource, GraphicView _overlay) throws IOException, SecurityException {

        overlay = _overlay;
        start(_cameraSource);
    }

    public void stop() {

        if (cameraSource != null) {
            cameraSource.stop();
        }
    }

    public void release() {

        if (cameraSource != null) {
            cameraSource.release();
            cameraSource = null;
        }
    }

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

    private class SurfaceCallback implements SurfaceHolder.Callback {

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

        @Override
        public void surfaceDestroyed(SurfaceHolder _surface) {
            surfaceAvailable = false;
        }

        @Override
        public void surfaceChanged(SurfaceHolder _holder, int _format, int _width, int _height) {
        }
    }

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

        //  To fill the view with the camera preview, while also preserving the correct aspect ratio,
        // it is usually necessary to slightly oversize the child and to crop off portions along one
        // of the dimensions. We scale up based on the dimension requiring the most correction, and
        // compute a crop offset for the other dimension.
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
            //  One dimension will be cropped. We shift child over or up by this offset and adjust
            // the size to maintain the proper aspect ratio.
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
}
