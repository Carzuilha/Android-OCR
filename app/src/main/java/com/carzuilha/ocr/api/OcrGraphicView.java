package com.carzuilha.ocr.api;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;

import com.carzuilha.ocr.view.GraphicView;
import com.google.android.gms.vision.text.Text;
import com.google.android.gms.vision.text.TextBlock;

import java.util.List;

/**
 *  Graphic instance for rendering TextBlock position, size, and ID within an associated graphic
 * overlay view.
 */
public class OcrGraphicView extends GraphicView.Graphic {

    //  Defines the color of the text drawn.
    private static final int TEXT_COLOR = Color.WHITE;

    //  Components used to draw the text.
    private static Paint rectPaint;
    private static Paint textPaint;
    private final TextBlock textBlock;

    /**
     *  Initializes the OCR graphic and sets its parameters.
     *
     * @param   _overlay    The graphic overlay.
     * @param   _text       The TextBlock used to draw the detected text.
     */
    OcrGraphicView(GraphicView _overlay, TextBlock _text) {

        super(_overlay);

        textBlock = _text;

        if (rectPaint == null) {
            rectPaint = new Paint();
            rectPaint.setColor(TEXT_COLOR);
            rectPaint.setStyle(Paint.Style.STROKE);
            rectPaint.setStrokeWidth(4.0f);
        }

        if (textPaint == null) {
            textPaint = new Paint();
            textPaint.setColor(TEXT_COLOR);
            textPaint.setTextSize(54.0f);
        }

        postInvalidate();
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

    /**
     *  Checks whether a point is within the bounding box of this graphic. The provided point should
     * be relative to this graphic's containing overlay.
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

}
