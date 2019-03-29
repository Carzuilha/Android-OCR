package com.carzuilha.ocr.thread;

import android.annotation.SuppressLint;
import android.graphics.ImageFormat;
import android.hardware.Camera;
import android.os.SystemClock;
import android.util.Log;

import com.carzuilha.ocr.control.CameraControl_A;
import com.google.android.gms.vision.Detector;
import com.google.android.gms.vision.Frame;

import java.nio.ByteBuffer;

/**
 *  This runnable controls access to the underlying receiver, calling it to process frames when
 * available from the camera. This is designed to run detection on frames as fast as possible
 * (i.e., without unnecessary context switching or waiting on the next frame).
 */
@SuppressWarnings("deprecation")
public class CameraThread_A extends CameraThread implements Runnable {

    //  Defines the tag of the class.
    private static final String TAG = "CameraThread_A";

    //  The camera source, which the thread will run.
    private CameraControl_A cameraControllerA;

    //==============================================================================================
    //                                  Default methods
    //==============================================================================================

    /**
     *  Creates a new thread instance.
     *
     * @param   _detector               The OCR detector.
     * @param   _cameraController       Controller of the camera device.
     */
    public CameraThread_A(Detector<?> _detector, CameraControl_A _cameraController) {
        detector = _detector;
        cameraControllerA = _cameraController;
    }

    /**
     *  Marks the runnable as active/not active. Signals any blocked threads to continue.
     *
     * @param   _active         Indicates if the thread must be active or not.
     */
    public void setActive(boolean _active) {

        synchronized (lock) {
            active = _active;
            lock.notifyAll();
        }
    }

    /**
     *  Sets the frame data received from the camera. This adds the previous unused frame buffer
     * (if present) back to the application, and keeps a pending reference to the frame data for
     * future use.
     *
     * @param   _data           The buffer data.
     * @param   _camera         The camera being utilized.
     */
    public void setNextFrame(byte[] _data, Camera _camera) {

        synchronized (lock) {

            if (pendingFrameData != null) {
                _camera.addCallbackBuffer(pendingFrameData.array());
                pendingFrameData = null;
            }

            if (!cameraControllerA.getBytesToByteBuffer().containsKey(_data)) {
                Log.d(TAG,
                        "Skipping frame. Could not find ByteBuffer associated with the image " +
                                "data from the com.project.util.");
                return;
            }

            //  Timestamp and frame ID are maintained here, which will give downstream code some
            // idea of the timing of frames received and when frames were dropped along the way.
            pendingTimeMillis = SystemClock.elapsedRealtime() - startTimeMillis;
            pendingFrameId++;
            pendingFrameData = cameraControllerA.getBytesToByteBuffer().get(_data);

            //  Notify the processor thread if it is waiting on the next frame (see below).
            lock.notifyAll();
        }
    }

    /**
     *  Releases the underlying receiver. This is only safe to do after the associated thread
     * has completed, which is managed in frame source's release method above.
     */
    @SuppressLint("Assert")
    public void release() {

        assert (cameraControllerA.getProcessingThread().getState() == Thread.State.TERMINATED);

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
        ByteBuffer data;

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
                                pendingFrameData,
                                cameraControllerA.getPreviewSize().getWidth(),
                                cameraControllerA.getPreviewSize().getHeight(), ImageFormat.NV21)
                        .setId(pendingFrameId)
                        .setTimestampMillis(pendingTimeMillis)
                        .setRotation(cameraControllerA.getRotation())
                        .build();

                data = pendingFrameData;

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
            } finally {
                cameraControllerA.getCamera().addCallbackBuffer(data.array());
            }
        }
    }

}