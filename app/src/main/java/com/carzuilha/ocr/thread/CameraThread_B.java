package com.carzuilha.ocr.thread;

import android.annotation.SuppressLint;
import android.graphics.ImageFormat;
import android.os.SystemClock;
import android.util.Log;

import com.carzuilha.ocr.control.CameraControl_B;
import com.carzuilha.ocr.util.NV21Image;
import com.google.android.gms.vision.Detector;
import com.google.android.gms.vision.Frame;

import java.nio.ByteBuffer;

/**
 *  This runnable controls access to the underlying receiver, calling it to process frames when
 * available from the camera. This is designed to run detection on frames as fast as possible
 * (i.e., without unnecessary context switching or waiting on the next frame).
 */
public class CameraThread_B extends CameraThread implements Runnable {

    //  Defines the tag of the class.
    private static final String TAG = "CameraThread_B";

    //  The camera source, which the thread will run.
    private CameraControl_B camera2Controller;

    //==============================================================================================
    //                                  Default methods
    //==============================================================================================

    /**
     *  Creates a new thread instance.
     *
     * @param   _detector               The OCR detector.
     * @param   _cameraController       Controller of the camera device.
     */
    public CameraThread_B(Detector<?> _detector, CameraControl_B _cameraController) {
        detector = _detector;
        camera2Controller = _cameraController;
    }

    /**
     *  Marks the runnable as active/not active. Signals any blocked threads to continue.
     *
     * @param   _active         Indicates if the thread must be active or not.
     */
    public void setActive(boolean _active) {

        synchronized (lock) {
            this.active = _active;
            lock.notifyAll();
        }
    }

    /**
     *  Sets the frame data received from the camera. This adds the previous unused frame buffer
     * (if present) back to the application, and keeps a pending reference to the frame data for
     * future use.
     *
     * @param   _data           The buffer data.
     */
    public void setNextFrame(byte[] _data) {

        synchronized (lock) {

            if (pendingFrameData != null) {
                pendingFrameData = null;
            }

            // Timestamp and frame ID are maintained here, which will give downstream code some
            // idea of the timing of frames received and when frames were dropped along the way.
            pendingTimeMillis = SystemClock.elapsedRealtime() - startTimeMillis;
            pendingFrameId++;
            pendingFrameData = ByteBuffer.wrap(_data);

            // Notify the processor thread if it is waiting on the next frame (see below).
            lock.notifyAll();
        }
    }

    /**
     * Releases the underlying receiver.  This is only safe to do after the associated thread
     * has completed, which is managed in camera source's release method above.
     */
    @SuppressLint("Assert")
    public void release() {

        assert (camera2Controller.getProcessingThread().getState() == Thread.State.TERMINATED);

        detector.release();
        detector = null;
    }

    //==============================================================================================
    //                                  Running the thread
    //==============================================================================================

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

        while (true) {

            synchronized (lock) {

                while (active && (pendingFrameData == null)) {

                    try {
                        // Wait for the next frame to be received from the camera, since we
                        // don't have it yet.
                        lock.wait();
                    } catch (InterruptedException e) {
                        Log.d(TAG, "Frame processing loop terminated.", e);
                        return;
                    }
                }

                if (!active) return;

                outputFrame = new Frame.Builder()
                        .setImageData(
                                ByteBuffer.wrap(
                                    NV21Image.quarter(
                                        pendingFrameData.array(),
                                        camera2Controller.getPreviewSize().getWidth(),
                                        camera2Controller.getPreviewSize().getHeight())
                                    ),
                                camera2Controller.getPreviewSize().getWidth()/4,
                                camera2Controller.getPreviewSize().getHeight()/4,
                                ImageFormat.NV21)
                        .setId(pendingFrameId)
                        .setTimestampMillis(pendingTimeMillis)
                        .setRotation(camera2Controller.getDetectorOrientation())
                        .build();

                //  We need to clear pendingFrameData to ensure that this buffer isn't
                // recycled back to the camera before we are done using that data.
                pendingFrameData = null;
            }

            // The code below needs to run outside of synchronization, because this will allow
            // the camera to add pending frame(s) while we are running detection on the current
            // frame.
            try {
                detector.receiveFrame(outputFrame);
            } catch (Throwable t) {
                Log.e(TAG, "Exception thrown from receiver.", t);
            }
        }
    }

}