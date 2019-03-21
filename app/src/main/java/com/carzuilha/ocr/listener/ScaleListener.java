package com.carzuilha.ocr.listener;

import android.view.ScaleGestureDetector;

import com.carzuilha.ocr.model.CameraSource;

public class ScaleListener implements ScaleGestureDetector.OnScaleGestureListener  {

    private CameraSource cameraSource;

    public ScaleListener(CameraSource _cameraSource) {
        cameraSource = _cameraSource;
    }

    /**
     *  Responds to scaling events for a gesture in progress.
     * Reported by pointer motion.
     *
     * @param   detector    The detector reporting the event - use this to
     *                      retrieve extended info about event state.
     * @return              Whether or not the detector should consider this event
     *                      as handled.
     */
    @Override
    public boolean onScale(ScaleGestureDetector detector) {
        return false;
    }

    /**
     *  Responds to the beginning of a scaling gesture. Reported by
     * new pointers going down.
     *
     * @param   detector    The detector reporting the event - use this to
     *                      retrieve extended info about event state.
     * @return              Whether or not the detector should continue recognizing
     *                      this gesture.
     */
    @Override
    public boolean onScaleBegin(ScaleGestureDetector detector) {
        return true;
    }

    /**
     *  Responds to the end of a scale gesture. Reported by existing
     * pointers going up.
     *
     * @param   detector    The detector reporting the event - use this to
     *                      retrieve extended info about event state.
     */
    @Override
    public void onScaleEnd(ScaleGestureDetector detector) {
        if (cameraSource != null) {
            cameraSource.doZoom(detector.getScaleFactor());
        }
    }

}
