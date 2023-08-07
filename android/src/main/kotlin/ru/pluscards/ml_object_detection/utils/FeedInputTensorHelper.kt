package ru.pluscards.ml_object_detection.utils

import android.graphics.Bitmap
import org.tensorflow.lite.DataType
import org.tensorflow.lite.support.common.ops.NormalizeOp
import org.tensorflow.lite.support.image.ImageProcessor
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.support.image.ops.ResizeOp
import org.tensorflow.lite.support.image.ops.ResizeWithCropOrPadOp

class FeedInputTensorHelper private constructor(width: Int, height: Int, mean: Float, std: Float) {
    private val tensorImage: TensorImage = TensorImage(DataType.FLOAT32)
    private val downSizeImageProcessor: ImageProcessor
    private val upSizeImageProcessor: ImageProcessor

    init {
        downSizeImageProcessor =
            ImageProcessor.Builder()
                .add(
                    ResizeOp(
                        height,
                        width,
                        ResizeOp.ResizeMethod.BILINEAR
                    )
                )
                .add(NormalizeOp(mean, std))
                .build()
        upSizeImageProcessor =
            ImageProcessor.Builder() // Center crop the image to the largest square possible
                .add(ResizeWithCropOrPadOp(height, width))
                .add(NormalizeOp(mean, std))
                .build()
    }

    companion object {
        private var instance: FeedInputTensorHelper? = null

        @Synchronized
        fun getInstance(width: Int, height: Int, mean: Float, std: Float): FeedInputTensorHelper {
            if (instance == null) {
                instance = FeedInputTensorHelper(width, height, mean, std)
            }
            return instance!!
        }

        fun getBytebufferFromBitmap(
            bitmap: Bitmap?,
            inputWidth: Int,
            inputHeight: Int,
            mean: Float,
            std: Float,
            sizeOption: String
        ): TensorImage {
            //https://www.tensorflow.org/lite/inference_with_metadata/lite_support
            val feedInputTensorHelper = getInstance(inputWidth, inputHeight, mean, std)
            feedInputTensorHelper.tensorImage.load(bitmap)
            if (sizeOption === "downsize") {
                return feedInputTensorHelper.downSizeImageProcessor.process(
                    feedInputTensorHelper.tensorImage
                )
            }
            if (sizeOption === "upsize") {
                return feedInputTensorHelper.upSizeImageProcessor.process(
                    feedInputTensorHelper.tensorImage
                )
            }
            throw Exception("internal error, size_option no supported")
        }
    }
}