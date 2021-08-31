/*
 *           Copyright © 2015-2016, 2019, 2021 Stanislav Petriakov
 *  Distributed under the Boost Software License, Version 1.0.
 *     (See accompanying file LICENSE_1_0.txt or copy at
 *           http://www.boost.org/LICENSE_1_0.txt)
 */

package com.keenfin.easypicker;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.media.ExifInterface;

import com.hypertrack.hyperlog.HyperLog;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

public class BitmapUtil {
    // thanks to http://stackoverflow.com/questions/477572/strange-out-of-memory-issue-while-loading-an-image-to-a-bitmap-object
    public static Bitmap getBitmap(String path, int requiredSize) {
        HyperLog.v("PhotoPicker", "get image: " + path);
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(path, options);
        int scale = 1;

        while (options.outWidth / scale > requiredSize && options.outHeight / scale > requiredSize)
            scale *= 2;

        options.inSampleSize = scale;
        options.inJustDecodeBounds = false;
        options.inDither = false;
        options.inPurgeable = true;
        options.inInputShareable = true;
        options.inTempStorage = new byte[32 * 1024];

        Bitmap result = null;
        File file = new File(path);
        FileInputStream fs = null;
        try {
            HyperLog.v("PhotoPicker", "try file input stream");
            fs = new FileInputStream(file);

            try {
                HyperLog.v("PhotoPicker", "get file descriptor");
                result = BitmapFactory.decodeFileDescriptor(fs.getFD(), null, options);
            } catch (OutOfMemoryError oom) {
                HyperLog.v("PhotoPicker", "oom: " + oom.getLocalizedMessage());
                oom.printStackTrace();

                try {
                    HyperLog.v("PhotoPicker", "in sample size x4");
                    options.inSampleSize *= 4;
                    result = BitmapFactory.decodeFileDescriptor(fs.getFD(), null, options);
                } catch (OutOfMemoryError oom1) {
                    HyperLog.v("PhotoPicker", "oom2: " + oom.getLocalizedMessage());
                    oom.printStackTrace();
                }
            }
        } catch (IOException e) {
            HyperLog.v("PhotoPicker", "ioex: " + e.getLocalizedMessage());
            e.printStackTrace();
        } finally {
            if (fs != null)
                try {
                    fs.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
        }

        ExifInterface exif;
        int orientation = ExifInterface.ORIENTATION_NORMAL;
        HyperLog.v("PhotoPicker", "orientation: " + orientation);
        try {
            exif = new ExifInterface(path);
            orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
            HyperLog.v("PhotoPicker", "exif: " + orientation);
        } catch (IOException e) {
            HyperLog.v("PhotoPicker", "ioex2: " + e.getLocalizedMessage());
            e.printStackTrace();
        }

        HyperLog.v("PhotoPicker", "result: " + result);
        result = rotateBitmap(result, orientation);
        HyperLog.v("PhotoPicker", "rotated result: " + result);
        return result;
    }

    // http://stackoverflow.com/a/20480741/2088273
    public static Bitmap rotateBitmap(Bitmap bitmap, int orientation) {
        Matrix matrix = new Matrix();
        switch (orientation) {
            case ExifInterface.ORIENTATION_NORMAL:
                return bitmap;
            case ExifInterface.ORIENTATION_FLIP_HORIZONTAL:
                matrix.setScale(-1, 1);
                break;
            case ExifInterface.ORIENTATION_ROTATE_180:
                matrix.setRotate(180);
                break;
            case ExifInterface.ORIENTATION_FLIP_VERTICAL:
                matrix.setRotate(180);
                matrix.postScale(-1, 1);
                break;
            case ExifInterface.ORIENTATION_TRANSPOSE:
                matrix.setRotate(90);
                matrix.postScale(-1, 1);
                break;
            case ExifInterface.ORIENTATION_ROTATE_90:
                matrix.setRotate(90);
                break;
            case ExifInterface.ORIENTATION_TRANSVERSE:
                matrix.setRotate(-90);
                matrix.postScale(-1, 1);
                break;
            case ExifInterface.ORIENTATION_ROTATE_270:
                matrix.setRotate(-90);
                break;
            default:
                return bitmap;
        }

        try {
            Bitmap bmRotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
            bitmap.recycle();
            return bmRotated;
        } catch (OutOfMemoryError e) {
            e.printStackTrace();
            return bitmap;
        }
    }

    // http://stackoverflow.com/a/19739471/2088273
    public static String getMimeTypeOfFile(String pathName) {
        BitmapFactory.Options opt = new BitmapFactory.Options();
        opt.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(pathName, opt);
        return opt.outMimeType;
    }
}
