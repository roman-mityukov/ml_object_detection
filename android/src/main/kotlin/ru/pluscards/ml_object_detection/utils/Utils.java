package ru.pluscards.ml_object_detection.utils;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Matrix;

import org.tensorflow.lite.support.image.TensorImage;

import java.nio.ByteBuffer;
import java.util.List;

public class Utils {
    public static ByteBuffer feedInputTensor(
            Bitmap bitmap,
            int inputWidth,
            int inputHeight,
            int srcWidth,
            int srcHeight,
            float mean,
            float std
    ) throws Exception {
        try {
            TensorImage tensorImage;
            if (srcWidth > inputWidth || srcHeight > inputHeight) {
                tensorImage = FeedInputTensorHelper.getBytebufferFromBitmap(bitmap, inputWidth, inputHeight, mean, std, "downsize");
            } else {
                tensorImage = FeedInputTensorHelper.getBytebufferFromBitmap(bitmap, inputWidth, inputHeight, mean, std, "upsize");
            }
            return tensorImage.getBuffer();
        } finally {
            assert bitmap != null;
            if (!bitmap.isRecycled()) {
                bitmap.recycle();
            }
        }
    }

    public static Bitmap feedInputToBitmap(
            Context context,
            List<byte[]> bytesList,
            int imageHeight,
            int imageWidth,
            int rotation
    ) {

        int Yb = bytesList.get(0).length;
        int Ub = bytesList.get(1).length;
        int Vb = bytesList.get(2).length;
        // Copy YUV data to plane byte
        byte[] data = new byte[Yb + Ub + Vb];
        System.arraycopy(bytesList.get(0), 0, data, 0, Yb);
        System.arraycopy(bytesList.get(2), 0, data, Yb, Ub);
        System.arraycopy(bytesList.get(1), 0, data, Yb + Ub, Vb);

        Bitmap bitmapRaw = RenderScriptHelper.getBitmapFromNV21(context, data, imageWidth, imageHeight);
        Matrix matrix = new Matrix();
        matrix.postRotate(rotation);
        bitmapRaw = Bitmap.createBitmap(bitmapRaw, 0, 0, bitmapRaw.getWidth(), bitmapRaw.getHeight(), matrix, true);
        return bitmapRaw;
    }
}
