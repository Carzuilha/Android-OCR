package com.carzuilha.ocr.util;

import android.content.Context;
import android.graphics.Point;
import android.util.DisplayMetrics;
import android.view.Display;
import android.view.WindowManager;

import com.google.android.gms.common.images.Size;

/**
 *  This class defines a set of operation about the current device screen.
 */
public class ScreenManager {

    /**
     *  Returns the height of a screen device.
     *
     * @param       _context        An application context.
     * @return                      The height of the screen, in pixels.
     */
    public static int getScreenHeight(Context _context) {

        WindowManager wm = (WindowManager) _context.getSystemService(Context.WINDOW_SERVICE);
        Display display = wm.getDefaultDisplay();
        Point size = new Point();

        display.getSize(size);

        return size.y;
    }

    /**
     *  Returns the width of a screen device.
     *
     * @param       _context        An application context.
     * @return                      The width of the screen, in pixels.
     */
    public static int getScreenWidth(Context _context) {

        WindowManager wm = (WindowManager) _context.getSystemService(Context.WINDOW_SERVICE);
        Display display = wm.getDefaultDisplay();
        Point size = new Point();

        display.getSize(size);

        return size.x;
    }

    /**
     *  Returns the ratio of a screen device.
     *
     * @param       _context        An application context.
     * @return                      The ratio of the screen.
     */
    public static float getScreenRatio(Context _context) {

        DisplayMetrics metrics = _context.getResources().getDisplayMetrics();

        return ((float)metrics.heightPixels / (float)metrics.widthPixels);
    }

    /**
     *  Returns the rotation of a screen device.
     *
     * @param       _context        An application context.
     * @return                      The rotation of the screen, in degrees (between 0 and 360).
     */
    public static int getScreenRotation(Context _context) {

        WindowManager wm = (WindowManager) _context.getSystemService(Context.WINDOW_SERVICE);

        return wm.getDefaultDisplay().getRotation();
    }

    /**
     *  Converts an android.util.Size to a default android Size.
     *
     * @param       _sizes          The android.util.Size.
     * @return                      The default size.
     */
    public static Size[] sizeToSize(android.util.Size[] _sizes) {

        Size[] size = new Size[_sizes.length];

        for(int i = 0; i < _sizes.length; i++) {
            size[i] = new Size(_sizes[i].getWidth(), _sizes[i].getHeight());
        }

        return size;
    }

}