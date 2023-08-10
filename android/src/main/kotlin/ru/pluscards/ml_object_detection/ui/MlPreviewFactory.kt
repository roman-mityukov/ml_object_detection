package ru.pluscards.ml_object_detection.ui

import android.content.Context
import android.graphics.SurfaceTexture
import io.flutter.plugin.common.StandardMessageCodec
import io.flutter.plugin.platform.PlatformView
import io.flutter.plugin.platform.PlatformViewFactory

class MlPreviewFactory(private val surfaceTexture: SurfaceTexture) :
    PlatformViewFactory(StandardMessageCodec.INSTANCE) {


    override fun create(context: Context, viewId: Int, args: Any?): PlatformView {
        val creationParams = args as Map<String?, Any?>?
        return MlPreview(context, viewId, creationParams)
    }
}