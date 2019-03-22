package com.carzuilha.ocr.thread;

import android.annotation.SuppressLint;
import android.graphics.ImageFormat;
import android.hardware.Camera;
import android.os.SystemClock;
import android.util.Log;

import com.carzuilha.ocr.control.OcrController;
import com.google.android.gms.vision.Detector;
import com.google.android.gms.vision.Frame;

import java.nio.ByteBuffer;

/**
 *  This runnable controls access to the underlying receiver, calling it to process frames when
 * available from the camera. This is designed to run detection on frames as fast as possible
 * (i.e., without unnecessary context switching or waiting on the next frame).
 */
public class FrameRunnable implements Runnable {

    //  Defines the tag of the class.
    private static final String TAG = "FrameRunnable";

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
    private OcrController cameraSource;

    public FrameRunnable(Detector<?> detector, OcrController _cameraSource) {
        mDetector = detector;
        cameraSource = _cameraSource;
    }

    /**
     *  Releases the underlying receiver. This is only safe to do after the associated thread
     * has completed, which is managed in frame source's release method above.
     */
    @SuppressLint("Assert")
    public void release() {
        assert (cameraSource.getProcessingThread().getState() == Thread.State.TERMINATED);
        mDetector.release();
        mDetector = null;
    }

    /**
     *  Marks the runnable as active/not active. Signals any blocked threads to continue.
     */
    public void setActive(boolean _active) {

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
    public void setNextFrame(byte[] _data, Camera _camera) {

        synchronized (mLock) {

            if (mPendingFrameData != null) {
                _camera.addCallbackBuffer(mPendingFrameData.array());
                mPendingFrameData = null;
            }

            if (!cameraSource.getBytesToByteBuffer().containsKey(_data)) {
                Log.d(TAG,
                        "Skipping frame. Could not find ByteBuffer associated with the image " +
                                "data from the com.project.util.");
                return;
            }

            //  Timestamp and frame ID are maintained here, which will give downstream code some
            // idea of the timing of frames received and when frames were dropped along the way.
            mPendingTimeMillis = SystemClock.elapsedRealtime() - mStartTimeMillis;
            mPendingFrameId++;
            mPendingFrameData = cameraSource.getBytesToByteBuffer().get(_data);

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
                        .setImageData(
                            mPendingFrameData,
                                cameraSource.getPreviewSize().getWidth(),
                                cameraSource.getPreviewSize().getHeight(), ImageFormat.NV21)
                        .setId(mPendingFrameId)
                        .setTimestampMillis(mPendingTimeMillis)
                        .setRotation(cameraSource.getRotation())
                        .build();

                data = mPendingFrameData;
                mPendingFrameData = null;
            }

            try {
                mDetector.receiveFrame(outputFrame);
            } catch (Throwable t) {
                Log.e(TAG, "Exception thrown from receiver.", t);
            } finally {
                cameraSource.getCamera().addCallbackBuffer(data.array());
            }
        }
    }
}