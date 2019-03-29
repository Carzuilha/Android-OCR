package com.carzuilha.ocr.view;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.RectF;

import com.google.android.gms.vision.CameraSource;
import com.google.android.gms.vision.text.Text;
import com.google.android.gms.vision.text.TextBlock;

import java.util.List;

/**
 *  Graphic instance for rendering TextBlock position, size, and ID within an associated graphic
 * graphicView view.
 */
public class OcrGraphic extends Graphic {

    //  Defines the color of the text drawn.
    private static final int TEXT_COLOR = Color.WHITE;

    //  Components used to draw the text.
    private static Paint rectPaint;
    private static Paint textPaint;
    private final TextBlock textBlock;

    //==============================================================================================
    //                                  Default methods
    //==============================================================================================

    /**
     *  Initializes the OCR graphic and sets its parameters.
     *
     * @param   _overlay    The graphic graphicView.
     * @param   _text       The TextBlock used to draw the detected text.
     */
    public OcrGraphic(GraphicView _overlay, TextBlock _text) {

        super(_overlay);

        textBlock = _text;

        if (rectPaint == null) {

            rectPaint = new Paint();

            rectPaint.setColor(TEXT_COLOR);
            rectPaint.setStyle(Paint.Style.STROKE);
            rectPaint.setStrokeWidth(4.0f);
            rectPaint.setPathEffect(new DashPathEffect(new float[] {5, 5}, 0));
        }

        if (textPaint == null) {

            textPaint = new Paint();

            textPaint.setColor(TEXT_COLOR);
            textPaint.setTextSize(54.0f);
        }

        postInvalidate();
    }

    /**
     *  Checks whether a point is within the bounding box of this graphic. The provided point should
     * be relative to this graphic's containing graphic.
     *
     * @param   _x          An x parameter in the relative context of the canvas.
     * @param   _y          A y parameter in the relative context of the canvas.
     * @return              'true' if the provided point is contained within this graphic's
     *                      bounding box; 'false' otherwise.
     */
    public boolean contains(float _x, float _y) {

        if (textBlock == null) return false;

        RectF rect = new RectF(textBlock.getBoundingBox());
        rect = translateRect(rect);

        return rect.contains(_x, _y);
    }

    /**
     *  Adjusts a horizontal value of the supplied value from the preview scale to the view
     * scale.
     *
     * @param   _horizontal     The reference value.
     * @return                  The adjusted value from the reference.
     */
    public float scaleX(float _horizontal) {
        return _horizontal * graphicView.getWidthScaleFactor();
    }

    /**
     *  Adjusts a vertical value of the supplied value from the preview scale to the view
     * scale.
     *
     * @param   _vertical       The reference value.
     * @return                  The adjusted value from the reference.
     */
    public float scaleY(float _vertical) {
        return _vertical * graphicView.getHeightScaleFactor();
    }

    /**
     *  Adjusts the x coordinate from the preview's coordinate system to the view coordinate
     * system.
     *
     * @param   _x          The translation value.
     * @return              The new x coordinate of the graphic.
     */
    public float translateX(float _x) {
        if (graphicView.getCameraType() == CameraSource.CAMERA_FACING_FRONT) {
            return graphicView.getWidth() - scaleX(_x);
        } else {
            return scaleX(_x);
        }
    }

    /**
     *  Adjusts the y coordinate from the preview's coordinate system to the view coordinate
     * system.
     *
     * @param   _y          The translation value.
     * @return              The new y coordinate of the graphic.
     */
    public float translateY(float _y) {
        return scaleY(_y);
    }

    /**
     *  Draws the text block annotations for position, size, and raw value on the supplied canvas.
     *
     * @param   _canvas      The drawing canvas.
     */
    @Override
    public void draw(Canvas _canvas) {

        if (textBlock == null) return;

        RectF rect = new RectF(textBlock.getBoundingBox());

        rect = translateRect(rect);
        _canvas.drawRect(rect, rectPaint);

        List<? extends Text> textComponents = textBlock.getComponents();

        for(Text currentText : textComponents) {

            float left = translateX(currentText.getBoundingBox().left);
            float bottom = translateY(currentText.getBoundingBox().bottom);

            _canvas.drawText(currentText.getValue(), left, bottom, textPaint);
        }
    }

}
