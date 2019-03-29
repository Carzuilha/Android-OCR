package com.carzuilha.ocr.view;

import android.graphics.Canvas;
import android.graphics.RectF;

/**
 *  Base class for a custom graphics object to be rendered within the graphic graphicView.  Subclass
 * this and implement the draw(Canvas) method to define the graphics element.  Add instances to
 * the graphicView using add(Graphic).
 */
public abstract class Graphic {

    //  The default GraphicView to be rendered.
    GraphicView graphicView;

    //==============================================================================================
    //                                  Default methods
    //==============================================================================================

    /**
     *  Initializes the DynamicTextureView and sets its parameters.
     *
     * @param   _graphicView    The context to be utilized.
     */
    Graphic(GraphicView _graphicView) {
        graphicView = _graphicView;
    }

    /**
     *  Returns a RectF in which the left and right parameters of the provided Rect are adjusted
     * by translateX, and the top and bottom are adjusted by translateY.
     *
     * @param   _inputRect      The given RectF.
     * @return                  The same RectF, but translated.
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

    //==============================================================================================
    //                                  Abstract methods
    //==============================================================================================

    /**
     *  Checks whether a point is within the bounding box of this graphic. The provided point should
     * be relative to this graphic's containing graphic.
     *
     * @param   _x          An x parameter in the relative context of the canvas.
     * @param   _y          A y parameter in the relative context of the canvas.
     * @return              'true' if the provided point is contained within this graphic's
     *                      bounding box; 'false' otherwise.
     */
    public abstract boolean contains(float _x, float _y);

    /**
     *  Adjusts a horizontal value of the supplied value from the preview scale to the view
     * scale.
     *
     * @param   _horizontal     The reference value.
     * @return                  The adjusted value from the reference.
     */
    public abstract float scaleX(float _horizontal);

    /**
     *  Adjusts a vertical value of the supplied value from the preview scale to the view
     * scale.
     *
     * @param   _vertical       The reference value.
     * @return                  The adjusted value from the reference.
     */
    public abstract float scaleY(float _vertical);

    /**
     *  Adjusts the x coordinate from the preview's coordinate system to the view coordinate
     * system.
     *
     * @param   _x          The translation value.
     * @return              The new x coordinate of the graphic.
     */
    public abstract float translateX(float _x);

    /**
     *  Adjusts the y coordinate from the preview's coordinate system to the view coordinate
     * system.
     *
     * @param   _y          The translation value.
     * @return              The new y coordinate of the graphic.
     */
    public abstract float translateY(float _y);

    /**
     *  Draws the text block annotations for position, size, and raw value on the supplied canvas.
     *
     * @param   _canvas      The drawing canvas.
     */
    public abstract void draw(Canvas _canvas);

}
