package com.carzuilha.ocr.view;

import android.content.Context;
import android.util.AttributeSet;
import android.view.TextureView;

/**
 *  Defines a dynamic TextureView that contains all the camera frames.
 */
public class DynamicTextureView extends TextureView {

    //  The width and height ratio of the TextureView.
    private int mRatioWidth = 0;
    private int mRatioHeight = 0;

    //==============================================================================================
    //                                  Default methods
    //==============================================================================================

    /**
     *  Initializes the DynamicTextureView and sets its parameters.
     *
     * @param   _context        The context to be utilized.
     */
    public DynamicTextureView(Context _context) {
        this(_context, null);
    }

    /**
     *  Initializes the DynamicTextureView and sets its parameters.
     *
     * @param   _context        The context to be utilized.
     * @param   _attrs          A set of attributes to be used with the context.
     */
    public DynamicTextureView(Context _context, AttributeSet _attrs) {
        this(_context, _attrs, 0);
    }

    /**
     *  Initializes the DynamicTextureView and sets its parameters.
     *
     * @param   _context        The context to be utilized.
     * @param   _attrs          A set of attributes to be used with the context.
     * @param   _style          The TextureView style.
     */
    public DynamicTextureView(Context _context, AttributeSet _attrs, int _style) {
        super(_context, _attrs, _style);
    }

    /**
     * Sets the aspect ratio for this view. The size of the view will be measured based on the ratio
     * calculated from the parameters. Note that the actual sizes of parameters don't matter, that
     * is, calling setAspectRatio(2, 3) and setAspectRatio(4, 6) make the same result.
     *
     * @param width  Relative horizontal size
     * @param height Relative vertical size
     */
    public void setAspectRatio(int width, int height) {

        if (width < 0 || height < 0) {
            throw new IllegalArgumentException("Size cannot be negative.");
        }

        mRatioWidth = width;
        mRatioHeight = height;

        requestLayout();
    }

    //==============================================================================================
    //                                  Internal methods
    //==============================================================================================

    /**
     *  Changes the measured dimension of the TextureView when the event is measured.
     *
     * @param   _widthMeasureSpec       The measured width.
     * @param   _heightMeasureSpec      The measured height.
     */
    @Override
    protected void onMeasure(int _widthMeasureSpec, int _heightMeasureSpec) {

        super.onMeasure(_widthMeasureSpec, _heightMeasureSpec);

        int width = MeasureSpec.getSize(_widthMeasureSpec);
        int height = MeasureSpec.getSize(_heightMeasureSpec);

        if (0 == mRatioWidth || 0 == mRatioHeight) {
            setMeasuredDimension(width, height);
        } else {
            if (width < height * mRatioWidth / mRatioHeight) {
                setMeasuredDimension(width, width * mRatioHeight / mRatioWidth);
            } else {
                setMeasuredDimension(height * mRatioWidth / mRatioHeight, height);
            }
        }
    }

}
