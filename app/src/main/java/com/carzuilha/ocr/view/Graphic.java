package com.carzuilha.ocr.view;

import android.graphics.Canvas;
import android.graphics.RectF;

/**
 *  Base class for a custom graphics object to be rendered within the graphic graphicView.  Subclass
 * this and implement the draw(Canvas) method to define the graphics element.  Add instances to
 * the graphicView using add(Graphic).
 */
public abstract class Graphic {

    GraphicView graphicView;

    Graphic(GraphicView _graphicView) {
        graphicView = _graphicView;
    }

    /**
     * Draw the graphic on the supplied canvas.
     *
     * @param   _canvas         The drawing canvas.
     */
    public abstract void draw(Canvas _canvas);

    /**
     *  Returns 'true' if the supplied coordinates are within this graphic.
     */
    public abstract boolean contains(float _x, float _y);

    /**
     *  Adjusts a horizontal value of the supplied value from the preview scale to the view
     * scale.
     */
    public abstract float scaleX(float _horizontal);

    /**
     *  Adjusts a vertical value of the supplied value from the preview scale to the view scale.
     */
    public abstract float scaleY(float _vertical);

    /**
     *  Adjusts the x coordinate from the preview's coordinate system to the view coordinate
     * system.
     */
    public abstract float translateX(float _x);

    /**
     *  Adjusts the y coordinate from the preview's coordinate system to the view coordinate
     * system.
     */
    public abstract float translateY(float _y);

    /**
     *  Returns a RectF in which the left and right parameters of the provided Rect are adjusted
     * by translateX, and the top and bottom are adjusted by translateY.
     */
    public RectF translateRect(RectF _inputRect) {

        RectF returnRect = new RectF();

        returnRect.left = translateX(_inputRect.left);
        returnRect.top = translateY(_inputRect.top);
        returnRect.right = translateX(_inputRect.right);
        returnRect.bottom = translateY(_inputRect.bottom);

        return returnRect;
    }

    /**
     *  Just calls postInvalidate() from the GraphicView.
     *
     */
    public void postInvalidate() {
        graphicView.postInvalidate();
    }

}
