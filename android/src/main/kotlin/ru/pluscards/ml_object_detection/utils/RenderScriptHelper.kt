package ru.pluscards.ml_object_detection.utils

import android.content.Context
import android.graphics.Bitmap
import android.renderscript.Allocation
import android.renderscript.Element
import android.renderscript.RenderScript
import android.renderscript.ScriptIntrinsicYuvToRGB
import android.renderscript.Type

class RenderScriptHelper private constructor(context: Context) {
    private val rs: RenderScript
    private val yuvToRgbIntrinsic: ScriptIntrinsicYuvToRGB
    private var yuvType: Type.Builder? = null
    private var rgbaType: Type.Builder? = null
    private var inAllocation: Allocation? = null
    private var outAllocation: Allocation? = null

    init {
        rs = RenderScript.create(context)
        yuvToRgbIntrinsic = ScriptIntrinsicYuvToRGB.create(rs, Element.U8_4(rs))
    }

    fun renderScriptNV21ToRGBA888(width: Int, height: Int, nv21: ByteArray): Allocation? {
        if (yuvType == null) {
            yuvType = Type.Builder(rs, Element.U8(rs)).setX(nv21.size)
        }
        if (rgbaType == null) {
            rgbaType = Type.Builder(rs, Element.RGBA_8888(rs)).setX(width).setY(height)
        }
        // Create input allocation for YUV data
        if (inAllocation == null) {
            inAllocation = Allocation.createTyped(rs, yuvType!!.create(), Allocation.USAGE_SCRIPT)
        }
        // Create output allocation for RGBA data
        if (outAllocation == null) {
            outAllocation = Allocation.createTyped(rs, rgbaType!!.create(), Allocation.USAGE_SCRIPT)
        }

        // Convert YUV to RGBA using RenderScript intrinsic
        inAllocation!!.copyFrom(nv21)
        yuvToRgbIntrinsic.setInput(inAllocation)
        yuvToRgbIntrinsic.forEach(outAllocation)
        return outAllocation
    }

    companion object {
        private lateinit var instance: RenderScriptHelper
        @Synchronized
        fun getInstance(context: Context): RenderScriptHelper {
            if (::instance.isInitialized.not()) {
                instance = RenderScriptHelper(context)
            }
            return instance
        }

        fun getBitmapFromNV21(context: Context, nv21: ByteArray, width: Int, height: Int): Bitmap {
            val rsHelper = getInstance(context)
            //https://blog.minhazav.dev/how-to-convert-yuv-420-sp-android.media.Image-to-Bitmap-or-jpeg/
            val allocation = rsHelper.renderScriptNV21ToRGBA888(width, height, nv21)
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            allocation!!.copyTo(bitmap)
            return bitmap
        }
    }
}