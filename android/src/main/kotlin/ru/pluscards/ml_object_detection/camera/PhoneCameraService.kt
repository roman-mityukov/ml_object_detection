package ru.pluscards.ml_object_detection.camera

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.ImageFormat
import android.graphics.SurfaceTexture
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.media.ImageReader
import android.util.Log
import android.view.Surface
import androidx.core.app.ActivityCompat
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding

class PhoneCameraService (
    private val context: Context,
    private val previewWidth: Int,
    private val previewHeight: Int,
    private val surfaceTexture: SurfaceTexture,
    private val activityPluginBinding: ActivityPluginBinding,
    private val callback: (ByteArray) -> Unit
) : CameraService {
    private var cameraDevice: CameraDevice? = null
    private lateinit var previewRequestBuilder: CaptureRequest.Builder
    private var imageReader: ImageReader? = null
    private lateinit var previewRequest: CaptureRequest

    companion object {
        const val TAG = "CameraService"
        const val permissionRequestCode = 4321
    }

    override fun init() {
        val cameraManager =
            context.getSystemService(Context.CAMERA_SERVICE) as CameraManager

        if (ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            activityPluginBinding.addRequestPermissionsResultListener { code, permissions, grantResults ->
                if (code == permissionRequestCode && permissions.contains(Manifest.permission.CAMERA)) {
                    if ((grantResults.isNotEmpty() &&
                                grantResults[0] == PackageManager.PERMISSION_GRANTED)
                    ) {
                        init()
                        return@addRequestPermissionsResultListener true
                    }
                }

                return@addRequestPermissionsResultListener false
            }

            ActivityCompat.requestPermissions(
                activityPluginBinding.activity,
                arrayOf(Manifest.permission.CAMERA),
                permissionRequestCode
            )

            return
        }

        cameraManager.openCamera(cameraManager.cameraIdList.first(), this.cameraStateCallback, null)

        imageReader = ImageReader.newInstance(
            previewWidth,
            previewHeight,
            ImageFormat.JPEG,
            2
        ).apply {
            setOnImageAvailableListener(onImageAvailableListener, null)
        }
    }

    private val cameraStateCallback = object : CameraDevice.StateCallback() {
        override fun onOpened(camera: CameraDevice) {
            Log.d(TAG, "onOpened")
            try {
                cameraDevice = camera
                previewRequestBuilder =
                    camera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
                surfaceTexture.setDefaultBufferSize(previewWidth, previewHeight)

                val surface = Surface(surfaceTexture)

                previewRequestBuilder.addTarget(surface)
                previewRequestBuilder.addTarget(imageReader!!.surface)

                camera.createCaptureSession(
                    mutableListOf(
                        surface,
                        imageReader!!.surface
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
            Log.d(TAG, "onDisconnected")
            closeCamera()
        }

        override fun onError(camera: CameraDevice, error: Int) {
            Log.d(TAG, "onError error $error")
            onDisconnected(camera)
        }
    }

    private var captureSession: CameraCaptureSession? = null

    private val sessionCallback = object : CameraCaptureSession.StateCallback() {
        override fun onConfigureFailed(session: CameraCaptureSession) {
            Log.d(TAG, "onConfigureFailed")
        }

        override fun onConfigured(session: CameraCaptureSession) {
            Log.d(TAG, "onConfigured")
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
        val image = reader.acquireLatestImage()
        image?.let {
            // так как превью в JPG, то преобразовывать цветовые схемы не нужно, просто
            // передаем байты в колбэк
            val buffer = it.planes[0].buffer
            val bytes = ByteArray(buffer.capacity())
            buffer.get(bytes)

            callback.invoke(bytes)
            it.close()
        }
    }

    private fun closeCamera() {
        captureSession?.close()
        captureSession = null
        cameraDevice?.close()
        cameraDevice = null
        imageReader?.close()
        imageReader = null
    }
}