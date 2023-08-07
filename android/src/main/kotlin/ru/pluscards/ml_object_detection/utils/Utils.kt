package ru.pluscards.ml_object_detection.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import org.tensorflow.lite.support.image.TensorImage
import java.nio.ByteBuffer

object Utils {
    fun feedInputTensor(
        bitmap: Bitmap?,
        inputWidth: Int,
        inputHeight: Int,
        srcWidth: Int,
        srcHeight: Int,
        mean: Float,
        std: Float
    ): ByteBuffer {
        return try {
            val tensorImage: TensorImage = if (srcWidth > inputWidth || srcHeight > inputHeight) {
                FeedInputTensorHelper.getBytebufferFromBitmap(
                    bitmap,
                    inputWidth,
                    inputHeight,
                    mean,
                    std,
                    "downsize"
                )
            } else {
                FeedInputTensorHelper.getBytebufferFromBitmap(
                    bitmap,
                    inputWidth,
                    inputHeight,
                    mean,
                    std,
                    "upsize"
                )
            }
            tensorImage.buffer
        } finally {
            assert(bitmap != null)
            if (!bitmap!!.isRecycled) {
                bitmap.recycle()
            }
        }
    }

    fun feedInputToBitmap(
        context: Context,
        bytesList: List<ByteArray>,
        imageHeight: Int,
        imageWidth: Int,
        rotation: Int
    ): Bitmap {
        val yb = bytesList[0].size
        val ub = bytesList[1].size
        val vb = bytesList[2].size
        // Copy YUV data to plane byte
        val data = ByteArray(yb + ub + vb)
        System.arraycopy(bytesList[0], 0, data, 0, yb)
        System.arraycopy(bytesList[2], 0, data, yb, ub)
        System.arraycopy(bytesList[1], 0, data, yb + ub, vb)
        var bitmapRaw = RenderScriptHelper.getBitmapFromNV21(context, data, imageWidth, imageHeight)
        val matrix = Matrix()
        matrix.postRotate(rotation.toFloat())
        bitmapRaw =
            Bitmap.createBitmap(bitmapRaw, 0, 0, bitmapRaw.width, bitmapRaw.height, matrix, true)
        return bitmapRaw
    }
}