package com.carzuilha.ocr.view;

import android.content.Context;
import android.graphics.Canvas;
import android.util.AttributeSet;
import android.view.View;

import java.util.HashSet;
import java.util.Set;

/**
 *  A view which renders a series of custom graphics to be overlaid on top of an associated preview
 * (i.e., the com.project.util preview).
 */
public class GraphicView<T extends Graphic> extends View {

    private int cameraType = 0;
    private int previewWidth;
    private int previewHeight;
    private float widthScaleFactor = 1.0f;
    private float heightScaleFactor = 1.0f;

    private Set<T> graphics = new HashSet<>();
    private final Object lock = new Object();

    //==============================================================================================
    //                                  Default methods
    //==============================================================================================

    /**
     *  Initializes the GraphicView and sets its parameters.
     *
     * @param   _context        The context to be utilized.
     * @param   _attrs          A set of attributes to be used with the context.
     */
    public GraphicView(Context _context, AttributeSet _attrs) {
        super(_context, _attrs);
    }

    /**
     *  Returns the selected camera; one of CAMERA_FACING_BACK or CAMERA_FACING_FRONT.
     *
     * @return      The type of the camera (CAMERA_FACING_BACK or CAMERA_FACING_FRONT).
     */
    public int getCameraType() {
        return cameraType;
    }

    /**
     *  Returns the width scale factor of the GraphicView.
     *
     * @return      The width scale factor.
     */
    public float getWidthScaleFactor() {
        return widthScaleFactor;
    }

    /**
     *  Returns the height scale factor of the GraphicView.
     *
     * @return      The height scale factor.
     */
    public float getHeightScaleFactor() {
        return heightScaleFactor;
    }

    /**
     *  Adds a graphic to the GraphicView.
     *
     * @param   _graphic        The graphic to be added.
     */
    public void add(T _graphic) {

        synchronized (lock) {
            graphics.add(_graphic);
        }

        postInvalidate();
    }

    /**
     *  Removes a graphic to the GraphicView.
     *
     * @param   _graphic        The graphic to be removed.
     */
    public void remove(T _graphic) {

        synchronized (lock) {
            graphics.remove(_graphic);
        }

        postInvalidate();
    }

    /**
     *   Removes all graphics from the GraphicView.
     */
    public void clear() {

        synchronized (lock) {
            graphics.clear();
        }

        postInvalidate();
    }

    /**
     *  Sets the camera attributes for size and camera type direction, which informs how to transform
     * image coordinates later.
     *
     * @param   _previewWidth       The preview's width for the camera.
     * @param   _previewHeight      The preview's height for the camera.
     * @param   _camera             The selected camera.
     */
    public void setCameraInfo(int _previewWidth, int _previewHeight, int _camera) {

        synchronized (lock) {
            this.previewWidth = _previewWidth;
            this.previewHeight = _previewHeight;
            this.cameraType = _camera;
        }

        postInvalidate();
    }

    /**
     *  Draws the GraphicView with its associated graphic objects.
     *
     * @param   _canvas             The canvas to be drawn.
     */
    @Override
    protected void onDraw(Canvas _canvas) {

        super.onDraw(_canvas);

        synchronized (lock) {

            if ((previewWidth != 0) && (previewHeight != 0)) {
                widthScaleFactor = (float) _canvas.getWidth() / (float) previewWidth;
                heightScaleFactor = (float) _canvas.getHeight() / (float) previewHeight;
            }

            for (Graphic graphic : graphics) {
                graphic.draw(_canvas);
            }
        }
    }

}
