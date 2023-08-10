@file:Suppress("UNCHECKED_CAST")

package ru.pluscards.ml_object_detection

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.SurfaceTexture
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.media.ImageReader
import android.os.CountDownTimer
import android.util.Log
import android.view.Surface
import androidx.core.app.ActivityCompat
import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.embedding.engine.plugins.FlutterPlugin.FlutterAssets
import io.flutter.plugin.common.EventChannel
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.common.MethodChannel.MethodCallHandler
import io.flutter.plugin.common.MethodChannel.Result
import ru.pluscards.ml_object_detection.model.Yolo
import ru.pluscards.ml_object_detection.model.Yolov8
import ru.pluscards.ml_object_detection.utils.Utils
import java.nio.ByteBuffer
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

private val empty = ArrayList<Map<String, Any>>()

/** MlObjectDetectionPlugin */
class MlObjectDetectionPlugin : FlutterPlugin, MethodCallHandler, EventChannel.StreamHandler {
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

//        flutterPluginBinding.platformViewRegistry.registerViewFactory(
//            "MlPreviewWidget",
//            MlPreviewFactory(surfaceTexture)
//        )

        methodChannel =
            MethodChannel(flutterPluginBinding.binaryMessenger, "ml_object_detection_method")
        methodChannel.setMethodCallHandler(this)

        eventChannel =
            EventChannel(flutterPluginBinding.binaryMessenger, "ml_object_detection_stream")
        eventChannel?.setStreamHandler(this)

        assets = flutterPluginBinding.flutterAssets
        context = flutterPluginBinding.applicationContext
        executor = Executors.newSingleThreadExecutor()

        val timer = object : CountDownTimer(200000, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                eventSink?.success(
                    listOf<Map<String, Any>>(
                        hashMapOf(
                            Pair(
                                first = "tag",
                                second = "mouse"
                            ),
                            Pair(first = "box", second = listOf(100f, 100f, 300f, 300f, 0.75f))
                        )
                    )
                )
            }

            override fun onFinish() {}
        }
        timer.start()
    }

    private var cameraDevice: CameraDevice? = null
    private lateinit var previewRequestBuilder: CaptureRequest.Builder
    private var imageReader: ImageReader? = null
    private lateinit var previewRequest: CaptureRequest

    private val cameraStateCallback = object : CameraDevice.StateCallback() {
        override fun onOpened(camera: CameraDevice) {
            try {
                cameraDevice = camera
                previewRequestBuilder =
                    camera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)

                val texture = this@MlObjectDetectionPlugin.surfaceTexture
                texture.setDefaultBufferSize(1280, 720)
                val surface = Surface(texture)

                previewRequestBuilder.addTarget(surface)
                previewRequestBuilder.set(
                    CaptureRequest.FLASH_MODE,
                    CaptureRequest.FLASH_MODE_TORCH
                )

                camera.createCaptureSession(
                    mutableListOf(
                        surface,
                        this@MlObjectDetectionPlugin.imageReader?.surface
                    ),
                    sessionCallback,
                    null
                )
            } catch (e: Exception) {
                e.printStackTrace()
                closeCamera()
            }
        }

        override fun onDisconnected(camera: CameraDevice) {
            closeCamera()
        }

        override fun onError(camera: CameraDevice, error: Int) {
            onDisconnected(camera)
        }
    }

    private var captureSession: CameraCaptureSession? = null

    private val sessionCallback = object : CameraCaptureSession.StateCallback() {
        override fun onConfigureFailed(session: CameraCaptureSession) {
            Log.d("Hello", "error")
        }

        override fun onConfigured(session: CameraCaptureSession) {

            if (cameraDevice == null) return

            captureSession = session

            try {
                // Auto focus should be continuous for camera preview.
                previewRequestBuilder.set(
                    CaptureRequest.CONTROL_AF_MODE,
                    CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE
                )

                previewRequest = previewRequestBuilder.build()
                session.setRepeatingRequest(previewRequest, null, null)
            } catch (e: CameraAccessException) {
                e.printStackTrace()
            }
        }
    }

    private val onImageAvailableListener = ImageReader.OnImageAvailableListener { reader ->
        Log.d("Hello", "asdf")
    }

    private fun closeCamera() {

    }

    override fun onMethodCall(call: MethodCall, result: Result) {
        when (call.method) {
            "getTextureId" -> {
                result.success(textureId)

                val cameraManager =
                    context!!.getSystemService(Context.CAMERA_SERVICE) as CameraManager
                cameraManager.cameraIdList.forEach {
                    val characteristic = cameraManager.getCameraCharacteristics(it)

                    if (characteristic[CameraCharacteristics.FLASH_INFO_AVAILABLE] == true) {
                        if (ActivityCompat.checkSelfPermission(
                                context!!,
                                Manifest.permission.CAMERA
                            ) != PackageManager.PERMISSION_GRANTED
                        ) {
                            // TODO: Consider calling
                            //    ActivityCompat#requestPermissions
                            // here to request the missing permissions, and then overriding
                            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
                            //                                          int[] grantResults)
                            // to handle the case where the user grants the permission. See the documentation
                            // for ActivityCompat#requestPermissions for more details.
                            return
                        }
                        cameraManager.openCamera(it, this.cameraStateCallback, null)
                        val displayMetrics = this.context!!.resources.displayMetrics

                        imageReader = ImageReader.newInstance(
                            displayMetrics.widthPixels, displayMetrics.heightPixels,
                            ImageFormat.YUV_420_888, /*maxImages*/ 2
                        ).apply {
                            setOnImageAvailableListener(onImageAvailableListener, null)
                        }
                        return@forEach
                    }
                }


            }

            "loadModel" -> {
                try {
                    yolo = loadModel(call.arguments as Map<String, Any>)
                    result.success("ok")
                } catch (e: Exception) {
                    result.error("100", "Error on load Yolo model", e)
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
            context!!,
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
                    Utils.feedInputToBitmap(context, frame!!, imageHeight, imageWidth, 90)
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
        methodChannel.setMethodCallHandler(null)
        context = null
        assets = null
        executor?.shutdown()
        yolo?.close()
    }
}
