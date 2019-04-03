package com.carzuilha.ocr.view;

import android.util.Log;
import android.util.SparseArray;

import com.google.android.gms.vision.Detector;
import com.google.android.gms.vision.text.TextBlock;

/**
 *  A very simple processor which gets detected TextBlocks and adds them to the GraphicView as
 * OcrGraphics.
 */
public class OcrTextBlock implements Detector.Processor<TextBlock> {

    //  Defines the tag of the class.
    private static final String TAG = "OcrTextBlock";

    //  The graphics utilized to draw the text.
    private GraphicView<OcrGraphic> graphicOverlay;

    //==============================================================================================
    //                                  Default methods
    //==============================================================================================

    /**
     *  Initializes the OCR detector and sets its parameters.
     *
     * @param   ocrGraphicOverlay   The graphics utilized to draw the detected text.
     */
    public OcrTextBlock(GraphicView<OcrGraphic> ocrGraphicOverlay) {
        graphicOverlay = ocrGraphicOverlay;
    }

    /**
     *  Called by the detector to deliver detection results.
     *
     * @param   _detections         The received _detections.
     */
    @Override
    public void receiveDetections(Detector.Detections<TextBlock> _detections) {

        graphicOverlay.clear();

        SparseArray<TextBlock> items = _detections.getDetectedItems();

        for (int i = 0; i < items.size(); ++i) {

            TextBlock item = items.valueAt(i);

            if (item != null && item.getValue() != null) {

                OcrGraphic graphic = new OcrGraphic(graphicOverlay, item);

                graphicOverlay.add(graphic);

                Log.d(TAG, "Text detected: [" + item.getValue() + "]");
            }
        }
    }

    /**
     *  Frees the resources associated with this detection processor.
     */
    @Override
    public void release() {
        graphicOverlay.clear();
    }

}
