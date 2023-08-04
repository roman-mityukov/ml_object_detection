package ru.pluscards.ml_object_detection

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.embedding.engine.plugins.FlutterPlugin.FlutterAssets
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.common.MethodChannel.MethodCallHandler
import io.flutter.plugin.common.MethodChannel.Result
import ru.pluscards.ml_object_detection.model.Yolov8
import ru.pluscards.ml_object_detection.utils.Utils
import java.nio.ByteBuffer
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

private val empty = ArrayList<Map<String, Any>>()

/** MlObjectDetectionPlugin */
class MlObjectDetectionPlugin : FlutterPlugin, MethodCallHandler {
    /// The MethodChannel that will the communication between Flutter and native Android
    ///
    /// This local reference serves to register the plugin with the Flutter Engine and unregister it
    /// when the Flutter Engine is detached from the Activity
    private lateinit var channel: MethodChannel
    private var context: Context? = null
    private var assets: FlutterAssets? = null
    private var yolo: Yolo? = null

    private var executor: ExecutorService? = null

    private var isDetecting = false


    override fun onAttachedToEngine(flutterPluginBinding: FlutterPlugin.FlutterPluginBinding) {
        channel = MethodChannel(flutterPluginBinding.binaryMessenger, "ml_object_detection")
        channel.setMethodCallHandler(this)

        assets = flutterPluginBinding.flutterAssets
        context = flutterPluginBinding.applicationContext
        executor = Executors.newSingleThreadExecutor()
    }

    override fun onMethodCall(call: MethodCall, result: Result) {
        when (call.method) {
            "loadModel" -> {
                try {
                    yolo = loadModel(call.arguments as Map<String, Any>)
                    result.success("ok")
                } catch (e: Exception) {
                    result.error("100", "Error on load Yolov5 model", e)
                }
            }

            "onFrame" -> {
                onFrame(call.arguments as Map<String?, Any?>, result)
            }

            "onImage" -> {
                onImage(call.arguments as Map<String?, Any?>, result)
            }

            "closeModel" -> {
                closeModel(result)
            }

            else -> result.notImplemented()
        }
    }

    private fun loadModel(args: Map<String, Any>): Yolo {
        val labelPath = assets!!.getAssetFilePathByName(args["label_path"].toString())
        val modelPath = assets!!.getAssetFilePathByName(args["model_path"].toString())
        val isAsset = if (args["is_asset"] == null) false else args["is_asset"] as Boolean
        val numThreads = args["num_threads"] as Int
        val useGpu = args["use_gpu"] as Boolean
        val rotation = args["rotation"] as Int
        val yolo: Yolo = Yolov8(
            context,
            modelPath,
            isAsset,
            numThreads,
            useGpu,
            labelPath,
            rotation
        )
        yolo.initModel()
        return yolo
    }

    //https://www.baeldung.com/java-single-thread-executor-service
    internal class DetectionTask(
        private val yolo: Yolo,
        args: Map<String?, Any?>,
        private var typing: String,
        result: Result,
        private val context: Context,
        private val callback: () -> Unit,
    ) :
        Runnable {
        private var image: ByteArray? = null
        private var frame: List<ByteArray>? = null
        private var imageHeight: Int
        private var imageWidth: Int
        private var iouThreshold: Float
        private var confThreshold: Float
        private var classThreshold: Float
        private val result: Result

        init {
            if (typing === "img") {
                image = args["bytesList"] as ByteArray?
            } else {
                frame = args["bytesList"] as List<ByteArray>
            }
            imageHeight = args["image_height"] as Int
            imageWidth = args["image_width"] as Int
            iouThreshold = (args["iou_threshold"] as Double).toFloat()
            confThreshold = (args["conf_threshold"] as Double).toFloat()
            classThreshold = (args["class_threshold"] as Double).toFloat()
            this.result = result
        }

        override fun run() {
            try {
                val bitmap: Bitmap = if (typing === "img") {
                    BitmapFactory.decodeByteArray(image, 0, image!!.size)
                } else {
                    //rotate image, because android take a photo rotating 90 degrees
                    Utils.feedInputToBitmap(context, frame, imageHeight, imageWidth, 90)
                }
                val shape = yolo.inputTensor.shape()
                val sourceWidth = bitmap.width
                val sourceHeight = bitmap.height
                val byteBuffer: ByteBuffer = Utils.feedInputTensor(
                    bitmap,
                    shape[1], shape[2], sourceWidth, sourceHeight, 0f, 255f
                )
                val detections: List<Map<String, Any>> = yolo.detectTask(
                    byteBuffer,
                    sourceHeight,
                    sourceWidth,
                    iouThreshold,
                    confThreshold,
                    classThreshold
                )
                callback.invoke()
                result.success(detections)
            } catch (e: java.lang.Exception) {
                result.error("100", "Detection Error", e)
            }
        }
    }

    private fun onFrame(args: Map<String?, Any?>, result: Result) {
        try {
            if (isDetecting) {
                result.success(empty)
            } else {
                isDetecting = true
                val detectionTask = DetectionTask(yolo!!, args, "frame", result, context!!) {
                    isDetecting = false
                }
                executor!!.submit(detectionTask)
            }
        } catch (e: java.lang.Exception) {
            result.error("100", "Detection Error", e)
        }
    }

    private fun onImage(args: Map<String?, Any?>, result: Result) {
        try {
            if (isDetecting) {
                result.success(empty)
            } else {
                isDetecting = true
                val detectionTask = DetectionTask(yolo!!, args, "img", result, context!!) {
                    isDetecting = false
                }
                executor!!.submit(detectionTask)
            }
        } catch (e: java.lang.Exception) {
            result.error("100", "Detection Error", e)
        }
    }

    private fun closeModel(result: Result) {
        try {
            yolo!!.close()
            result.success("Yolo model closed succesfully")
        } catch (e: java.lang.Exception) {
            result.error("100", "Close_yolo_model error", e)
        }
    }

    override fun onDetachedFromEngine(binding: FlutterPlugin.FlutterPluginBinding) {
        channel.setMethodCallHandler(null)
        context = null
        assets = null
        executor?.shutdown()
        yolo?.close()
    }
}
