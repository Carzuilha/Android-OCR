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

import com.carzuilha.ocr.view.OcrTextBlock;
import com.carzuilha.ocr.control.OcrController;
import com.carzuilha.ocr.view.OcrGraphic;
import com.carzuilha.ocr.listener.ScaleListener;
import com.carzuilha.ocr.R;
import com.carzuilha.ocr.view.CameraViewGroup;
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

    //  Intent request code to handle updating play services if needed.
    private static final int RC_HANDLE_GMS = 9001;

    //  Permission request codes need to be < 256.
    private static final int RC_HANDLE_CAMERA_PERM = 2;

    //  Component elements.
    private OcrController cameraSource;
    private CameraViewGroup cameraViewGroup;
    private GraphicView<OcrGraphic> graphicOverlay;

    //  Component events.
    private ScaleGestureDetector scaleGestureDetector;

    /**
     *  Initializes the UI and creates the detector pipeline.
     *
     * @param   bundle      The application bundle.
     */
    @Override
    public void onCreate(Bundle bundle) {

        super.onCreate(bundle);

        setContentView(R.layout.activity_main);

        initializeViews();
        initializeCamera();
        initializeListeners();
    }

    /**
     *  Initializes the application views.
     *
     */
    private void initializeViews() {

        graphicOverlay = findViewById(R.id.grv_overlay);
        cameraViewGroup = findViewById(R.id.cvg_camera);
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

    /**
     *  Initializes events listeners and detectors.
     *
     */
    private void initializeListeners() {

        scaleGestureDetector = new ScaleGestureDetector(this, new ScaleListener(cameraSource));
        Snackbar.make(graphicOverlay, R.string.pinch_stretch_zoom, Snackbar.LENGTH_LONG).show();
    }

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

        if (cameraViewGroup != null) {
            cameraViewGroup.stop();
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

        if (cameraViewGroup != null) {
            cameraViewGroup.release();
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

        builder.setTitle(R.string.app_name);
        builder.setMessage(R.string.no_camera_permission);
        builder.setPositiveButton(R.string.ok, listener);

        builder.show();
    }

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

        textRecognizer.setProcessor(new OcrTextBlock(graphicOverlay));

        if (!textRecognizer.isOperational()) {

            IntentFilter lowStorageFilter = new IntentFilter(Intent.ACTION_DEVICE_STORAGE_LOW);
            boolean hasLowStorage = registerReceiver(null, lowStorageFilter) != null;

            if (hasLowStorage) {
                Toast.makeText(this, R.string.low_storage_error, Toast.LENGTH_LONG).show();
            }
        }

        cameraSource =
                new OcrController.Builder(getApplicationContext(), textRecognizer)
                        .setFacing(OcrController.CAMERA_FACING_BACK)
                        .setFocusMode(Camera.Parameters.FOCUS_MODE_AUTO)
                        .setRequestedPreviewSize(1280, 1024)
                        .setRequestedFps(2.0f)
                        .setFlashMode(null)
                        .build();
    }

    /**
     *  Starts or restarts the camera source, if it exists. If the application source doesn't
     * exist yet (e.g., because onResume was called before the application source was created), this
     * will be called again when the application source is created.
     *
     * @throws  SecurityException       if the camera access is denied.
     */
    private void startCameraSource() throws SecurityException {

        int code = GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(getApplicationContext());

        if (code != ConnectionResult.SUCCESS) {

            Dialog dlg = GoogleApiAvailability.getInstance().getErrorDialog(this, code, RC_HANDLE_GMS);
            dlg.show();
        }

        if (cameraSource != null) {

            try {

                cameraViewGroup.start(cameraSource, graphicOverlay);

            } catch (IOException e) {

                cameraSource.release();
                cameraSource = null;
            }
        }
    }

}
