package com.carzuilha.ocr.thread;

import android.os.SystemClock;

import com.google.android.gms.vision.Detector;

import java.nio.ByteBuffer;

/**
 *  This generic runnable controls access to the underlying receiver, calling it to process frames when
 * available from the camera. You must implements the Runnable class when extending this class.
 */
@SuppressWarnings("WeakerAccess")
public abstract class CameraThread {

    //  This represents a detector and the frame time.
    protected long startTimeMillis = SystemClock.elapsedRealtime();
    protected Detector<?> detector;

    //  This lock guards all of the member variables below.
    protected boolean active = true;
    protected final Object lock = new Object();

    //  These pending variables hold the state associated with the new frame awaiting processing.
    protected int pendingFrameId = 0;
    protected long pendingTimeMillis;
    protected ByteBuffer pendingFrameData;

}
