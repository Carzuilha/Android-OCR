package com.carzuilha.ocr.view;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

import com.google.android.gms.vision.CameraSource;

import java.util.HashSet;
import java.util.Set;

/**
 *  A view which renders a series of custom graphics to be overlaid on top of an associated preview
 * (i.e., the com.project.util preview).
 */
public class GraphicView<T extends GraphicView.Graphic> extends View {

    private int facing = CameraSource.CAMERA_FACING_BACK;
    private int previewWidth;
    private int previewHeight;
    private float widthScaleFactor = 1.0f;
    private float heightScaleFactor = 1.0f;

    private Set<T> graphics = new HashSet<>();
    private final Object lock = new Object();

    /**
     *  Base class for a custom graphics object to be rendered within the graphic overlay.  Subclass
     * this and implement the draw(Canvas) method to define the graphics element.  Add instances to
     * the overlay using add(Graphic).
     */
    public static abstract class Graphic {

        private GraphicView mOverlay;

        public Graphic(GraphicView _overlay) {
            mOverlay = _overlay;
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
        public float scaleX(float _horizontal) {
            return _horizontal * mOverlay.widthScaleFactor;
        }

        /**
         *  Adjusts a vertical value of the supplied value from the preview scale to the view scale.
         */
        public float scaleY(float _vertical) {
            return _vertical * mOverlay.heightScaleFactor;
        }

        /**
         *  Adjusts the x coordinate from the preview's coordinate system to the view coordinate
         * system.
         */
        public float translateX(float _x) {
            if (mOverlay.facing == CameraSource.CAMERA_FACING_FRONT) {
                return mOverlay.getWidth() - scaleX(_x);
            } else {
                return scaleX(_x);
            }
        }

        /**
         *  Adjusts the y coordinate from the preview's coordinate system to the view coordinate
         * system.
         */
        public float translateY(float _y) {
            return scaleY(_y);
        }

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

        public void postInvalidate() {
            mOverlay.postInvalidate();
        }
    }

    public GraphicView(Context _context, AttributeSet _attrs) {
        super(_context, _attrs);
    }

    /**
     *  Removes all graphics from the overlay.
     */
    public void clear() {
        synchronized (lock) {
            graphics.clear();
        }
        postInvalidate();
    }

    /**
     *  Adds a graphic to the overlay.
     */
    public void add(T _graphic) {
        synchronized (lock) {
            graphics.add(_graphic);
        }
        postInvalidate();
    }

    /**
     *  Removes a graphic from the overlay.
     */
    public void remove(T _graphic) {
        synchronized (lock) {
            graphics.remove(_graphic);
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
     *  Sets the camera attributes for size and facing direction, which informs how to transform
     * image coordinates later.
     */
    public void setCameraInfo(int _previewWidth, int _previewHeight, int _facing) {

        synchronized (lock) {
            this.previewWidth = _previewWidth;
            this.previewHeight = _previewHeight;
            this.facing = _facing;
        }

        postInvalidate();
    }

    /**
     *  Draws the overlay with its associated graphic objects.
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
