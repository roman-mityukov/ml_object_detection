package ru.pluscards.ml_object_detection.camera

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.ImageFormat
import android.graphics.Rect
import android.graphics.SurfaceTexture
import android.graphics.YuvImage
import android.hardware.usb.UsbDevice
import android.util.Log
import android.view.Surface
import androidx.core.app.ActivityCompat
import com.rokid.axr.phone.glasscamera.RKGlassCamera
import com.rokid.axr.phone.glasscamera.RKGlassCamera.RokidCameraCallback
import com.rokid.axr.phone.glasscamera.callback.OnGlassCameraConnectListener
import com.rokid.axr.phone.glassdevice.RKGlassDevice
import com.rokid.axr.phone.glassdevice.callback.OnGlassDeviceConnectListener
import com.rokid.axr.phone.glassdevice.hw.listener.RKKeyListener
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding
import java.io.ByteArrayOutputStream

class GlassesCameraService(
    private val context: Context,
    private val previewWidth: Int,
    private val previewHeight: Int,
    private val surfaceTexture: SurfaceTexture,
    private val activityPluginBinding: ActivityPluginBinding,
    private val callback: (ByteArray) -> Unit
) : CameraService {
    companion object {
        const val TAG = "GlassesCameraService"
    }

    override fun init() {
        try {
            RKGlassDevice.getInstance().init(deviceConnectionListener)
        } catch (e: Exception) {
            Log.d(TAG, "init error")
        }
    }

    override fun close() {
        stopPreview()
        RKGlassCamera.getInstance().closeCamera()
        RKGlassCamera.getInstance().deInit()
        RKGlassDevice.getInstance().deInit()
    }

    private fun initCamera() {
        if (ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            activityPluginBinding.addRequestPermissionsResultListener { code, permissions, grantResults ->
                if (code == PhoneCameraService.permissionRequestCode && permissions.contains(
                        Manifest.permission.CAMERA
                    )
                ) {
                    if ((grantResults.isNotEmpty() &&
                                grantResults[0] == PackageManager.PERMISSION_GRANTED)
                    ) {
                        initCamera()
                        return@addRequestPermissionsResultListener true
                    }
                }

                return@addRequestPermissionsResultListener false
            }

            ActivityCompat.requestPermissions(
                activityPluginBinding.activity,
                arrayOf(
                    Manifest.permission.CAMERA,
                    Manifest.permission.RECORD_AUDIO,
                    Manifest.permission.READ_EXTERNAL_STORAGE,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
                ),
                PhoneCameraService.permissionRequestCode
            )

            return
        }
        RKGlassCamera.getInstance().init(cameraConnectionListener)
        RKGlassCamera.getInstance().setCameraCallback(cameraCallback)
    }

    private val deviceConnectionListener = object : OnGlassDeviceConnectListener {
        override fun onGlassDeviceConnected(p0: UsbDevice?) {
            Log.d(TAG, "onGlassDeviceConnected")
            initCamera()

            RKGlassDevice.getInstance().setRkKeyListener(keyListener)
        }

        override fun onGlassDeviceDisconnected() {
            Log.d(TAG, "onGlassDeviceConnected")
        }
    }

    private val keyListener = object : RKKeyListener {
        override fun onPowerKeyEvent(p0: Int) {
            Log.d(TAG, "onPowerKeyEvent, parameter $p0")
        }

        override fun onBackKeyEvent(p0: Int) {
            Log.d(TAG, "onBackKeyEvent")
        }

        override fun onTouchKeyEvent(p0: Int) {
            Log.d(TAG, "onTouchKeyEvent")
        }

        override fun onTouchSlideBack() {
            Log.d(TAG, "onTouchSlideBack")
        }

        override fun onTouchSlideForward() {
            Log.d(TAG, "onTouchSlideForward")
        }

    }

    //Camera Connection listener, to open camera immediately when camera is connected.
    private val cameraConnectionListener: OnGlassCameraConnectListener by lazy {
        object : OnGlassCameraConnectListener {
            override fun onGlassCameraConnected(p0: UsbDevice?) {
                Log.d(TAG, "onGlassCameraConnected")
                RKGlassCamera.getInstance().openCamera()
            }

            override fun onGlassCameraDisconnected() {
                Log.d(TAG, "onGlassCameraDisconnected")
                stopPreview()
            }
        }
    }

    private val cameraCallback: RokidCameraCallback = object : RokidCameraCallback {
        override fun onOpen() {
            Log.d(TAG, "onOpen")
            RKGlassCamera.getInstance().addOnPreviewFrameListener { yuvByteArray, timestamp ->
                //преобразуем YUV в RGB битмапу
                val out = ByteArrayOutputStream()
                val yuv = YuvImage(yuvByteArray, ImageFormat.NV21, previewWidth, previewHeight, null)
                val quality = 100
                yuv.compressToJpeg(Rect(0, 0, previewWidth, previewHeight), quality, out)
                val bitmapByteArray = out.toByteArray()

                callback.invoke(bitmapByteArray)
            }
            RKGlassCamera.getInstance().startPreview(Surface(surfaceTexture), previewWidth, previewHeight)
        }

        override fun onClose() {
            Log.d(TAG, "onClose")
        }

        override fun onStartPreview() {
            Log.d(TAG, "onStartPreview")
            RKGlassCamera.getInstance().isAutoFocus = true
            RKGlassCamera.getInstance().supportedPreviewSizes?.forEach {
                Log.d(TAG,"it = ${it.width}  *  ${it.height}")
            }
        }

        override fun onStopPreview() {
            Log.d(TAG, "onStopPreview")
        }

        override fun onError(error: java.lang.Exception?) {
            Log.d(TAG, "onError $error")
        }

        override fun onStartRecording() {
            Log.d(TAG, "onStartRecording")
        }

        override fun onStopRecording() {
            Log.d(TAG, "onStopRecording")
        }
    }

    private fun stopPreview() {
        try {
            RKGlassCamera.getInstance().stopPreview()
        } catch (e: Exception) {
        }
        // surface = null
    }
}