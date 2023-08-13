@file:Suppress("UNCHECKED_CAST")

package ru.pluscards.ml_object_detection

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.SurfaceTexture
import android.hardware.usb.UsbManager
import android.os.Handler
import android.os.Looper
import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.embedding.engine.plugins.FlutterPlugin.FlutterAssets
import io.flutter.embedding.engine.plugins.activity.ActivityAware
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding
import io.flutter.plugin.common.EventChannel
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.common.MethodChannel.MethodCallHandler
import io.flutter.plugin.common.MethodChannel.Result
import ru.pluscards.ml_object_detection.camera.CameraService
import ru.pluscards.ml_object_detection.camera.CameraProvider
import ru.pluscards.ml_object_detection.camera.GlassesCameraService
import ru.pluscards.ml_object_detection.camera.PhoneCameraService
import ru.pluscards.ml_object_detection.model.Yolo
import ru.pluscards.ml_object_detection.model.Yolov8
import ru.pluscards.ml_object_detection.utils.Utils
import java.nio.ByteBuffer
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/** MlObjectDetectionPlugin */
class MlObjectDetectionPlugin : FlutterPlugin, MethodCallHandler, EventChannel.StreamHandler,
    ActivityAware {
    companion object {
        val TAG = "MlObjectDetectionPlugin"
    }

    /// The MethodChannel that will the communication between Flutter and native Android
    ///
    /// This local reference serves to register the plugin with the Flutter Engine and unregister it
    /// when the Flutter Engine is detached from the Activity
    private lateinit var methodChannel: MethodChannel
    private lateinit var surfaceTexture: SurfaceTexture
    private var textureId: Long = -1
    private var context: Context? = null
    private var assets: FlutterAssets? = null
    private var yolo: Yolo? = null

    private var executor: ExecutorService? = null

    private var isDetecting = false

    private var eventChannel: EventChannel? = null
    private var eventSink: EventChannel.EventSink? = null

    private var cameraService: CameraService? = null

    override fun onListen(arguments: Any?, events: EventChannel.EventSink?) {
        this.eventSink = events
    }

    override fun onCancel(arguments: Any?) {
        this.eventSink = null
    }

    override fun onAttachedToEngine(flutterPluginBinding: FlutterPlugin.FlutterPluginBinding) {
        val entry = flutterPluginBinding.textureRegistry.createSurfaceTexture()
        textureId = entry.id()
        surfaceTexture = entry.surfaceTexture()

        methodChannel =
            MethodChannel(flutterPluginBinding.binaryMessenger, "ml_object_detection_method")
        methodChannel.setMethodCallHandler(this)

        eventChannel =
            EventChannel(flutterPluginBinding.binaryMessenger, "ml_object_detection_stream")
        eventChannel?.setStreamHandler(this)

        assets = flutterPluginBinding.flutterAssets
        context = flutterPluginBinding.applicationContext
        executor = Executors.newSingleThreadExecutor()
    }

    var isDeinitCalled: Boolean = false

    override fun onMethodCall(call: MethodCall, result: Result) {
        when (call.method) {
            "init" -> {
                try {
                    val args = call.arguments as Map<String, Any>

                    val labelPath = assets!!.getAssetFilePathByName(args["classes_path"].toString())
                    val modelPath = assets!!.getAssetFilePathByName(args["model_path"].toString())
                    val isAsset =
                        if (args["is_asset"] == null) false else args["is_asset"] as Boolean
                    val previewWidth = args["preview_width"] as Int
                    val previewHeight = args["preview_height"] as Int
                    val numThreads = args["num_threads"] as Int
                    val useGpu = args["use_gpu"] as Boolean

                    yolo = Yolov8(
                        context!!,
                        modelPath,
                        isAsset,
                        numThreads,
                        useGpu,
                        labelPath
                    )
                    yolo!!.initModel()

                    val callback: (bytes: ByteArray) -> Unit = {
                        if (!isDetecting) {

                            val bitmap = BitmapFactory.decodeByteArray(it, 0, it.size, null)

                            isDetecting = true
                            val detectionTask = DetectionTask(yolo!!, bitmap) {
                                val handler = Handler(Looper.getMainLooper())
                                handler.post {
                                    isDetecting = false
                                    eventSink?.success(it)
                                    handlePendingDeinit()
                                }
                            }
                            executor!!.submit(detectionTask)
                        }
                    }

                    val usbManager = context!!.getSystemService(Context.USB_SERVICE) as UsbManager
                    val usbDeviceHashMap = usbManager.deviceList
                    val cameraProvider = if (usbDeviceHashMap.values.any {
                            it.manufacturerName?.lowercase()?.contains("rokid") == true
                        }) {
                        CameraProvider.glasses
                    } else {
                        CameraProvider.phone
                    }

                    if (cameraProvider == CameraProvider.glasses) {
                        cameraService = GlassesCameraService(
                            context!!,
                            previewWidth,
                            previewHeight,
                            surfaceTexture,
                            activityPluginBinding!!,
                            callback
                        )
                    } else {
                        cameraService = PhoneCameraService(
                            context!!,
                            previewWidth,
                            previewHeight,
                            surfaceTexture,
                            activityPluginBinding!!,
                            callback
                        )
                    }

                    cameraService!!.init()

                    result.success(
                        mapOf(
                            Pair(first = "textureId", second = textureId),
                            Pair(first = "cameraProvider", second = cameraProvider.name)
                        )
                    )
                } catch (e: Exception) {
                    result.error("100", "Error on plugin initialization", e)
                }
            }

            "deinit" -> {
                close(result)
            }

            else -> result.notImplemented()
        }
    }

    private var pendingDeinitResult: Result? = null

    private fun handlePendingDeinit() {
        if (isDeinitCalled) {
            close(pendingDeinitResult)
            isDeinitCalled = false
            pendingDeinitResult = null
        }
    }

    private fun close(result: Result?) {
        try {
            if (!isDetecting) {
                yolo!!.close()
                yolo = null
                cameraService?.close()
                result?.success("Yolo model closed succesfully")
            } else {
                isDeinitCalled = true
                pendingDeinitResult = result
            }
        } catch (e: java.lang.Exception) {
            result?.error("100", "Close_yolo_model error", e)
        }
    }

    internal class DetectionTask(
        private val yolo: Yolo,
        private val bitmap: Bitmap,
        private val callback: (List<Map<String, Any>>) -> Unit,
    ) :
        Runnable {
        private var iouThreshold: Float = 0.4f
        private var confThreshold: Float = 0.4f
        private var classThreshold: Float = 0.5f

        override fun run() {
            try {
                val shape = yolo.inputTensor.shape()
                val sourceWidth = bitmap.width
                val sourceHeight = bitmap.height
                val byteBuffer: ByteBuffer = Utils.feedInputTensor(
                    bitmap,
                    shape[1],
                    shape[2],
                    sourceWidth,
                    sourceHeight,
                    0f,
                    255f
                )
                val detections: List<Map<String, Any>> = yolo.detectTask(
                    byteBuffer,
                    sourceHeight,
                    sourceWidth,
                    iouThreshold,
                    confThreshold,
                    classThreshold
                )

                callback.invoke(detections)
            } catch (e: java.lang.Exception) {
                // no op
            }
        }
    }

    override fun onDetachedFromEngine(binding: FlutterPlugin.FlutterPluginBinding) {
        methodChannel.setMethodCallHandler(null)
        context = null
        assets = null
        executor?.shutdown()
        yolo?.close()
    }

    private var activityPluginBinding: ActivityPluginBinding? = null

    override fun onAttachedToActivity(binding: ActivityPluginBinding) {
        activityPluginBinding = binding
    }

    override fun onDetachedFromActivityForConfigChanges() {
        activityPluginBinding = null
    }

    override fun onReattachedToActivityForConfigChanges(binding: ActivityPluginBinding) {
        activityPluginBinding = binding
    }

    override fun onDetachedFromActivity() {
        activityPluginBinding = null
    }
}
