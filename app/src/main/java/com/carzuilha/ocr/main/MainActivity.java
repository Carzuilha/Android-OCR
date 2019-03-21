package com.carzuilha.ocr.main;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.hardware.Camera;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.design.widget.Snackbar;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.widget.Toast;

import com.carzuilha.ocr.api.OcrDetector;
import com.carzuilha.ocr.api.OcrGraphicView;
import com.carzuilha.ocr.listener.ScaleListener;
import com.carzuilha.ocr.R;
import com.carzuilha.ocr.model.CameraSource;
import com.carzuilha.ocr.view.CameraGroup;
import com.carzuilha.ocr.view.GraphicView;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.android.gms.vision.text.TextRecognizer;

import java.io.IOException;

/**
 *  Main activity of the application. This app detects text and displays the value. During detection
 *  overlay, graphics are drawn to indicate the position, size, and contents of each TextBlock.
 */
public final class MainActivity extends AppCompatActivity {

    // Intent request code to handle updating play services if needed.
    private static final int RC_HANDLE_GMS = 9001;

    // Permission request codes need to be < 256.
    private static final int RC_HANDLE_CAMERA_PERM = 2;

    // Component elements and events.
    private CameraSource cameraSource;
    private CameraGroup cameraSourcePreview;
    private GraphicView<OcrGraphicView> graphicOverlay;
    private ScaleGestureDetector scaleGestureDetector;

    //region Initializing activity

    /**
     *  Initializes the UI and creates the detector pipeline.
     */
    @Override
    public void onCreate(Bundle bundle) {

        super.onCreate(bundle);

        setContentView(R.layout.main_activity);

        initializeViews();
        initializeCamera();

        scaleGestureDetector = new ScaleGestureDetector(this, new ScaleListener(cameraSource));

        Snackbar.make(graphicOverlay, "Pinch/Stretch to zoom.", Snackbar.LENGTH_LONG).show();
    }

    /**
     *  Initializes the application views.
     *
     */
    private void initializeViews() {

        graphicOverlay = findViewById(R.id.graphicOverlay);
        cameraSourcePreview = findViewById(R.id.cameraSourcePreview);
    }

    /**
     *  Initializes the camera.
     *
     */
    private void initializeCamera() {

        int result = ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA);

        if (result == PackageManager.PERMISSION_GRANTED) {
            createCameraSource();
        } else {
            requestCameraPermission();
        }
    }

    //endregion

    //region Overriding methods

    /**
     *  Restarts the application.
     *
     */
    @Override
    protected void onResume() {

        super.onResume();

        startCameraSource();
    }

    /**
     *  Stops the application.
     *
     */
    @Override
    protected void onPause() {

        super.onPause();

        if (cameraSourcePreview != null) {
            cameraSourcePreview.stop();
        }
    }

    /**
     *  Releases the resources associated with the application, the associated detectors, and the
     *  rest of the processing pipeline.
     *
     */
    @Override
    protected void onDestroy() {

        super.onDestroy();

        if (cameraSourcePreview != null) {
            cameraSourcePreview.release();
        }
    }

    /**
     *  Defines an event that happens when the device screen is touched.
     *
     * @param   e       The motion event?
     * @return          'true' if an event were interpreted, 'false' otherwise.
     */
    @Override
    public boolean onTouchEvent(MotionEvent e) {
        return scaleGestureDetector.onTouchEvent(e) || super.onTouchEvent(e);
    }

    /**
     *  Callback for the result from requesting permissions. This method
     * is invoked for every call on requestPermissions().
     *
     * @param   requestCode     The request code passed in requestPermissions().
     * @param   permissions     The requested permissions. Never null.
     * @param   grantResults    The grant results for the corresponding permissions
     *                          which is either PERMISSION_GRANTED or PERMISSION_DENIED. Never null.
     */
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {

        if (requestCode != RC_HANDLE_CAMERA_PERM) {
            super.onRequestPermissionsResult(requestCode, permissions, grantResults);
            return;
        }

        if (grantResults.length != 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            createCameraSource();
            return;
        }

        DialogInterface.OnClickListener listener =
                new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        finish();
                    }
                };

        AlertDialog.Builder builder = new AlertDialog.Builder(this);

        builder.setTitle("Optical Character Recognition");
        builder.setMessage(R.string.no_camera_permission);
        builder.setPositiveButton(R.string.ok, listener);

        builder.show();
    }

    //endregion

    //region Internal methods

    /**
     *  Requests the camera permission.
     *
     */
    private void requestCameraPermission() {

        final Activity thisActivity = this;
        final String[] permissions = new String[]{Manifest.permission.CAMERA};

        if (!ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.CAMERA)) {

            ActivityCompat.requestPermissions(this, permissions, RC_HANDLE_CAMERA_PERM);

            return;
        }

        View.OnClickListener listener =
                new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        ActivityCompat.requestPermissions(thisActivity, permissions, RC_HANDLE_CAMERA_PERM);
                    }
                };

        Snackbar.make(graphicOverlay, R.string.permission_camera_rationale, Snackbar.LENGTH_INDEFINITE)
                .setAction(R.string.ok, listener)
                .show();
    }

    /**
     *  Creates and starts the com.project.util. Note that this uses a higher resolution in comparison
     * to other detection examples to enable the OCR detector to detect small text samples at long
     * distances.
     *
     */
    @SuppressLint("InlinedApi")
    private void createCameraSource() {

        Context context = getApplicationContext();
        TextRecognizer textRecognizer = new TextRecognizer.Builder(context).build();

        textRecognizer.setProcessor(new OcrDetector(graphicOverlay));

        if (!textRecognizer.isOperational()) {

            IntentFilter lowStorageFilter = new IntentFilter(Intent.ACTION_DEVICE_STORAGE_LOW);
            boolean hasLowStorage = registerReceiver(null, lowStorageFilter) != null;

            if (hasLowStorage) {
                Toast.makeText(this, R.string.low_storage_error, Toast.LENGTH_LONG).show();
            }
        }

        cameraSource =
                new CameraSource.Builder(getApplicationContext(), textRecognizer)
                        .setFacing(CameraSource.CAMERA_FACING_BACK)
                        .setRequestedPreviewSize(1280, 1024)
                        .setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE)
                        .setRequestedFps(2.0f)
                        .setFlashMode(null)
                        .build();
    }

    /**
     *  Starts or restarts the application source, if it exists. If the application source doesn't
     * exist yet (e.g., because onResume was called before the application source was created), this
     * will be called again when the application source is created.
     *
     */
    private void startCameraSource() throws SecurityException {

        int code = GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(getApplicationContext());

        if (code != ConnectionResult.SUCCESS) {
            Dialog dlg = GoogleApiAvailability.getInstance().getErrorDialog(this, code, RC_HANDLE_GMS);
            dlg.show();
        }

        if (cameraSource != null) {

            try {

                cameraSourcePreview.start(cameraSource, graphicOverlay);

            } catch (IOException e) {

                cameraSource.release();
                cameraSource = null;
            }
        }
    }

    //endregion

}
