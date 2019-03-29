package com.carzuilha.ocr.util;

import android.media.Image;

import java.nio.ByteBuffer;

/**
 *  This class defines a set of operation about Yuv420888 image manipulation.
 */
public class NV21Image {

    /**
     *  Converts an Yuv420888 image to NV21 image.
     *
     * @param   _imgYUV420      The Yuv420888 image.
     * @return                  Thw NV21 image.
     */
    public static byte[] FromYUV420888(Image _imgYUV420) {

        byte[] data;

        ByteBuffer buffer0 = _imgYUV420.getPlanes()[0].getBuffer();
        ByteBuffer buffer2 = _imgYUV420.getPlanes()[2].getBuffer();

        int buffer0_size = buffer0.remaining();
        int buffer2_size = buffer2.remaining();

        data = new byte[buffer0_size + buffer2_size];
        buffer0.get(data, 0, buffer0_size);
        buffer2.get(data, buffer0_size, buffer2_size);

        return data;
    }

    /**
     *  Reduce the size of a NV21 frame.
     *
     * @param   _data           The original image.
     * @param   _width          The input'image width.
     * @param   _height         The input's image height.
     * @return                  The reduced image.
     */
    public static byte[] quarter(byte[] _data, int _width, int _height) {

        int i = 0;
        byte[] yuv = new byte[_width / 4 * _height / 4 * 3 / 2];

        for (int y = 0; y < _height; y += 4) {
            for (int x = 0; x < _width; x += 4) {
                yuv[i] = _data[y * _width + x];
                i++;
            }
        }

        return yuv;
    }

}
