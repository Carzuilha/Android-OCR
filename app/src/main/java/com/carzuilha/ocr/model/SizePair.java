package com.carzuilha.ocr.model;

import com.google.android.gms.common.images.Size;

/**
 *  Stores a preview size and a corresponding same-aspect-ratio picture size.  To avoid distorted
 * preview images on some devices, the picture size must be set to a size that is the same aspect
 * ratio as the preview size or the preview may end up being distorted.  If the picture size is
 * null, then there is no picture size with the same aspect ratio as the preview size.
 */
@SuppressWarnings("deprecation")
public class SizePair {

    //  The preview and picture size.
    private Size mPreview;
    private Size mPicture;

    /**
     *  Initializes the SizePair detector and sets its parameters.
     *
     * @param   _previewSize        The preview size.
     * @param   _pictureSize        The picture size.
     */
    public SizePair(android.hardware.Camera.Size _previewSize, android.hardware.Camera.Size _pictureSize) {

        mPreview = new Size(_previewSize.width, _previewSize.height);

        if (_pictureSize != null) {
            mPicture = new Size(_pictureSize.width, _pictureSize.height);
        }
    }

    /**
     *  Returns the preview size.
     *
     * @return      The preview size.
     */
    public Size previewSize() {
        return mPreview;
    }

    /**
     *  Returns the picture size.
     *
     * @return      The picture size.
     */
    @SuppressWarnings("unused")
    public Size pictureSize() {
        return mPicture;
    }

}
