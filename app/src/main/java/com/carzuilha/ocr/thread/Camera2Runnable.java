package com.carzuilha.ocr.thread;

import android.annotation.SuppressLint;
import android.graphics.ImageFormat;
import android.hardware.Camera;
import android.os.SystemClock;
import android.util.Log;

import com.carzuilha.ocr.control.Camera2Controller;
import com.google.android.gms.vision.Detector;
import com.google.android.gms.vision.Frame;

import java.nio.ByteBuffer;

/**
 *  This runnable controls access to the underlying receiver, calling it to process frames when
 * available from the camera. This is designed to run detection on frames as fast as possible
 * (i.e., without unnecessary context switching or waiting on the next frame).
 */
public class Camera2Runnable implements Runnable {

    //  Defines the tag of the class.
    private static final String TAG = "Camera2Runnable";

    private long mStartTimeMillis = SystemClock.elapsedRealtime();
    private Detector<?> mDetector;

    //  This lock guards all of the member variables below.
    private boolean mActive = true;
    private final Object mLock = new Object();

    //  These pending variables hold the state associated with the new frame awaiting processing.
    private int mPendingFrameId = 0;
    private long mPendingTimeMillis;
    private ByteBuffer mPendingFrameData;

    //  The camera source, which the thread will run.
    private Camera2Controller cameraController;

    public Camera2Runnable(Detector<?> detector, Camera2Controller _cameraController) {
        mDetector = detector;
        cameraController = _cameraController;
    }

    /**
     * Releases the underlying receiver.  This is only safe to do after the associated thread
     * has completed, which is managed in camera source's release method above.
     */
    @SuppressLint("Assert")
    public void release() {
        assert (cameraController.getProcessingThread().getState() == Thread.State.TERMINATED);
        mDetector.release();
        mDetector = null;
    }

    /**
     * Marks the runnable as active/not active.  Signals any blocked threads to continue.
     */
    public void setActive(boolean active) {
        synchronized (mLock) {
            mActive = active;
            mLock.notifyAll();
        }
    }

    /**
     * Sets the frame data received from the camera.
     */
    public void setNextFrame(byte[] data) {
        synchronized (mLock) {
            if (mPendingFrameData != null) {
                mPendingFrameData = null;
            }

            // Timestamp and frame ID are maintained here, which will give downstream code some
            // idea of the timing of frames received and when frames were dropped along the way.
            mPendingTimeMillis = SystemClock.elapsedRealtime() - mStartTimeMillis;
            mPendingFrameId++;
            mPendingFrameData = ByteBuffer.wrap(data);

            // Notify the processor thread if it is waiting on the next frame (see below).
            mLock.notifyAll();
        }
    }

    /**
     * As long as the processing thread is active, this executes detection on frames
     * continuously.  The next pending frame is either immediately available or hasn't been
     * received yet.  Once it is available, we transfer the frame info to local variables and
     * run detection on that frame.  It immediately loops back for the next frame without
     * pausing.
     * <p/>
     * If detection takes longer than the time in between new frames from the camera, this will
     * mean that this loop will run without ever waiting on a frame, avoiding any context
     * switching or frame acquisition time latency.
     * <p/>
     * If you find that this is using more CPU than you'd like, you should probably decrease the
     * FPS setting above to allow for some idle time in between frames.
     */
    @Override
    public void run() {
        Frame outputFrame;

        while (true) {
            synchronized (mLock) {
                while (mActive && (mPendingFrameData == null)) {
                    try {
                        // Wait for the next frame to be received from the camera, since we
                        // don't have it yet.
                        mLock.wait();
                    } catch (InterruptedException e) {
                        Log.d(TAG, "Frame processing loop terminated.", e);
                        return;
                    }
                }

                if (!mActive) {
                    // Exit the loop once this camera source is stopped or released.  We check
                    // this here, immediately after the wait() above, to handle the case where
                    // setActive(false) had been called, triggering the termination of this
                    // loop.
                    return;
                }

                outputFrame = new Frame.Builder()
                        .setImageData(
                                ByteBuffer.wrap(cameraController.quarterNV21(
                                        mPendingFrameData.array(),
                                        cameraController.getPreviewSize().getWidth(),
                                        cameraController.getPreviewSize().getHeight())
                                ),
                                cameraController.getPreviewSize().getWidth()/4,
                                cameraController.getPreviewSize().getHeight()/4,
                                ImageFormat.NV21)
                        .setId(mPendingFrameId)
                        .setTimestampMillis(mPendingTimeMillis)
                        .setRotation(cameraController.getDetectorOrientation())
                        .build();

                // We need to clear mPendingFrameData to ensure that this buffer isn't
                // recycled back to the camera before we are done using that data.
                mPendingFrameData = null;
            }

            // The code below needs to run outside of synchronization, because this will allow
            // the camera to add pending frame(s) while we are running detection on the current
            // frame.

            try {
                mDetector.receiveFrame(outputFrame);
            } catch (Throwable t) {
                Log.e(TAG, "Exception thrown from receiver.", t);
            }
        }
    }
}