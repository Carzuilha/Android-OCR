package com.carzuilha.ocr.api;

import android.util.Log;
import android.util.SparseArray;

import com.carzuilha.ocr.view.GraphicView;
import com.google.android.gms.vision.Detector;
import com.google.android.gms.vision.text.TextBlock;

/**
 *  A very simple Processor which gets detected TextBlocks and adds them to the overlay as
 * OcrGraphics.
 */
public class OcrDetector implements Detector.Processor<TextBlock> {

    //  The graphics utilized to draw the text.
    private GraphicView<OcrGraphicView> graphicOverlay;

    /**
     *  Initializes the OCR detector and sets its parameters.
     *
     * @param   ocrGraphicOverlay   The graphics utilized to draw the detected text.
     */
    public OcrDetector(GraphicView<OcrGraphicView> ocrGraphicOverlay) {
        graphicOverlay = ocrGraphicOverlay;
    }

    /**
     *  Called by the detector to deliver detection results.
     *
     * @param   detections          The received detections.
     */
    @Override
    public void receiveDetections(Detector.Detections<TextBlock> detections) {

        graphicOverlay.clear();

        SparseArray<TextBlock> items = detections.getDetectedItems();

        for (int i = 0; i < items.size(); ++i) {
            TextBlock item = items.valueAt(i);
            if (item != null && item.getValue() != null) {
                Log.d("OcrDetector", "Text detected! " + item.getValue());
                OcrGraphicView graphic = new OcrGraphicView(graphicOverlay, item);
                graphicOverlay.add(graphic);
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
