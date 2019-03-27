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

    /**
     *
     * @param   _context
     * @param   _attrs
     */
    public GraphicView(Context _context, AttributeSet _attrs) {
        super(_context, _attrs);
    }

    public int getCameraType() {
        return cameraType;
    }

    public float getWidthScaleFactor() {
        return widthScaleFactor;
    }

    public float getHeightScaleFactor() {
        return heightScaleFactor;
    }

    /**
     *  Adds a graphic to the graphicView.
     */
    public void add(T _graphic) {
        synchronized (lock) {
            graphics.add(_graphic);
        }
        postInvalidate();
    }

    /**
     *  Removes a graphic from the graphicView.
     */
    public void remove(T _graphic) {
        synchronized (lock) {
            graphics.remove(_graphic);
        }
        postInvalidate();
    }

    /**
     *  Removes all graphics from the graphicView.
     */
    public void clear() {
        synchronized (lock) {
            graphics.clear();
        }
        postInvalidate();
    }

    /**
     *  Returns the first graphic, if any, that exists at the provided absolute screen coordinates.
     * These coordinates will be offset by the relative screen position of this view.
     *
     * @return              First graphic containing the point, or null if no text is detected.
     */
    public T getGraphicAtLocation(float _rawX, float _rawY) {

        synchronized (lock) {

            // Get the position of this View so the raw location can be offset relative to the view.
            int[] location = new int[2];
            this.getLocationOnScreen(location);

            for (T graphic : graphics) {
                if (graphic.contains(_rawX - location[0], _rawY - location[1])) {
                    return graphic;
                }
            }

            return null;
        }
    }

    /**
     *  Sets the camera attributes for size and cameraType direction, which informs how to transform
     * image coordinates later.
     */
    public void setCameraInfo(int _previewWidth, int _previewHeight, int _facing) {

        synchronized (lock) {
            this.previewWidth = _previewWidth;
            this.previewHeight = _previewHeight;
            this.cameraType = _facing;
        }

        postInvalidate();
    }

    /**
     *  Draws the graphicView with its associated graphic objects.
     */
    @Override
    protected void onDraw(Canvas canvas) {

        super.onDraw(canvas);

        synchronized (lock) {

            if ((previewWidth != 0) && (previewHeight != 0)) {
                widthScaleFactor = (float) canvas.getWidth() / (float) previewWidth;
                heightScaleFactor = (float) canvas.getHeight() / (float) previewHeight;
            }

            for (Graphic graphic : graphics) {
                graphic.draw(canvas);
            }
        }
    }
}
