package ru.pluscards.ml_object_detection.model

import android.content.Context
import android.content.res.AssetManager
import android.util.Log
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.Tensor
import org.tensorflow.lite.gpu.CompatibilityList
import org.tensorflow.lite.gpu.GpuDelegate
import java.io.BufferedReader
import java.io.FileInputStream
import java.io.InputStreamReader
import java.nio.ByteBuffer
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import java.util.Collections
import java.util.Vector
import kotlin.math.max
import kotlin.math.min

open class Yolo(
    private val context: Context,
    private val modelPath: String,
    private val isAssets: Boolean,
    private val numThreads: Int,
    private val useGpu: Boolean,
    private val labelPath: String
) {
    private var output: Array<Array<FloatArray>>? = null
    private var interpreter: Interpreter? = null
    private var labels: Vector<String>? = null
    val inputTensor: Tensor
        //    public Vector<String> getLabels(){return this.labels;}
        get() = interpreter!!.getInputTensor(0)

    fun initModel() {
        var assetManager: AssetManager? = null
        var buffer: MappedByteBuffer? = null
        var fileChannel: FileChannel? = null
        var fileInputStream: FileInputStream? = null
        try {
            if (interpreter == null) {
                if (isAssets) {
                    assetManager = context.assets
                    val assetFileDescriptor = assetManager.openFd(
                        modelPath
                    )
                    fileInputStream = FileInputStream(assetFileDescriptor.fileDescriptor)
                    fileChannel = fileInputStream.channel
                    buffer = fileChannel.map(
                        FileChannel.MapMode.READ_ONLY, assetFileDescriptor.startOffset,
                        assetFileDescriptor.length
                    )
                    assetFileDescriptor.close()
                } else {
                    fileInputStream = FileInputStream(modelPath)
                    fileChannel = fileInputStream.channel
                    buffer = fileChannel.map(FileChannel.MapMode.READ_ONLY, 0, fileChannel.size())
                }
                val compatibilityList = CompatibilityList()
                val interpreterOptions = Interpreter.Options()
                interpreterOptions.numThreads = numThreads
                if (useGpu) {
                    if (compatibilityList.isDelegateSupportedOnThisDevice) {
                        val gpuOptions = compatibilityList.bestOptionsForThisDevice
                        interpreterOptions.addDelegate(
                            GpuDelegate(gpuOptions.setQuantizedModelsAllowed(true))
                        )
                    }
                }
                //batch, width, height, channels
                interpreter = Interpreter(buffer, interpreterOptions)
                interpreter!!.allocateTensors()
                labels = loadLabels(assetManager, labelPath)
                val shape = interpreter!!.getOutputTensor(0).shape()
                output = Array(shape[0]) {
                    Array(
                        shape[1]
                    ) { FloatArray(shape[2]) }
                }
            }
        } finally {
            buffer?.clear()
            if (fileChannel != null) if (fileChannel.isOpen) fileChannel.close()
            if (fileChannel != null) if (fileChannel.isOpen) fileInputStream!!.close()
        }
    }

    private fun loadLabels(assetManager: AssetManager?, labelPath: String?): Vector<String> {
        var bufferedReader: BufferedReader? = null
        return try {
            bufferedReader = if (assetManager != null) {
                BufferedReader(InputStreamReader(assetManager.open(labelPath!!)))
            } else {
                BufferedReader(InputStreamReader(FileInputStream(labelPath)))
            }
            val labels = Vector<String>()

            bufferedReader.forEachLine {
                labels.add(it)
            }

            labels
        } finally {
            bufferedReader?.close()
        }
    }

    fun detectTask(
        byteBuffer: ByteBuffer,
        sourceHeight: Int,
        sourceWidth: Int,
        iouThreshold: Float,
        confThreshold: Float,
        classThreshold: Float
    ): List<Map<String, Any>> {
        return try {
            val shape = interpreter!!.getInputTensor(0).shape()
            interpreter!!.run(byteBuffer, output)
            var boxes = filterBox(
                output!!,
                iouThreshold,
                confThreshold,
                classThreshold,
                shape[1].toFloat(),
                shape[2].toFloat()
            )
            boxes = restoreSize(boxes, shape[1], shape[2], sourceWidth, sourceHeight)
            out(boxes, labels!!)
        } finally {
            byteBuffer.clear()
        }
    }

    protected open fun filterBox(
        modelOutputs: Array<Array<FloatArray>>,
        iouThreshold: Float,
        confThreshold: Float,
        classThreshold: Float,
        inputWidth: Float,
        inputHeight: Float
    ): List<FloatArray> {
        val preBox: MutableList<FloatArray> = ArrayList()
        val confIndex = 4
        val classIndex = 5
        val dimension = modelOutputs[0][0].size
        val rows = modelOutputs[0].size
        var x1: Float
        var y1: Float
        var x2: Float
        var y2: Float
        var conf: Float
        for (i in 0 until rows) {
            //convert xywh to xyxy
            x1 = (modelOutputs[0][i][0] - modelOutputs[0][i][2] / 2f) * inputWidth
            y1 = (modelOutputs[0][i][1] - modelOutputs[0][i][3] / 2f) * inputHeight
            x2 = (modelOutputs[0][i][0] + modelOutputs[0][i][2] / 2f) * inputWidth
            y2 = (modelOutputs[0][i][1] + modelOutputs[0][i][3] / 2f) * inputHeight
            conf = modelOutputs[0][i][confIndex]
            if (conf < confThreshold) continue
            var max = 0f
            var y = 0
            for (j in classIndex until dimension) {
                if (modelOutputs[0][i][j] < classThreshold) continue
                if (max < modelOutputs[0][i][j]) {
                    max = modelOutputs[0][i][j]
                    y = j
                }
            }
            if (max > 0) {
                val tmp = FloatArray(6)
                tmp[0] = x1
                tmp[1] = y1
                tmp[2] = x2
                tmp[3] = y2
                tmp[4] = modelOutputs[0][i][y]
                tmp[5] = (y - classIndex) * 1f
                preBox.add(tmp)
            }
        }
        if (preBox.isEmpty()) return ArrayList()
        //for reverse orden, insteand of using .reversed method
        val compareValues = java.util.Comparator { v1: FloatArray, v2: FloatArray ->
            v1[1].compareTo(v2[1])
        }
        //Collections.sort(pre_box,compareValues.reversed());
        Collections.sort(preBox, compareValues)
        return nms(preBox, iouThreshold)
    }

    private fun restoreSize(
        nms: List<FloatArray>,
        inputWidth: Int,
        inputHeight: Int,
        srcWidth: Int,
        srcHeight: Int
    ): List<FloatArray> {
        return try {
            //restore size after scaling, larger images
            if (srcWidth > inputWidth || srcHeight > inputHeight) {
                val gainX = srcWidth / inputWidth.toFloat()
                val gainY = srcHeight / inputHeight.toFloat()
                for (i in nms.indices) {
                    nms[i][0] = min(srcWidth.toFloat(), max(nms[i][0] * gainX, 0f))
                    nms[i][1] = min(srcHeight.toFloat(), max(nms[i][1] * gainY, 0f))
                    nms[i][2] = min(srcWidth.toFloat(), max(nms[i][2] * gainX, 0f))
                    nms[i][3] = min(srcHeight.toFloat(), max(nms[i][3] * gainY, 0f))
                }
                //restore size after padding, smaller images
            } else {
                val padx = (srcWidth - inputWidth) / 2f
                val pady = (srcHeight - inputHeight) / 2f
                for (i in nms.indices) {
                    nms[i][0] = min(srcWidth.toFloat(), max(nms[i][0] + padx, 0f))
                    nms[i][1] = min(srcHeight.toFloat(), max(nms[i][1] + pady, 0f))
                    nms[i][2] = min(srcWidth.toFloat(), max(nms[i][2] + padx, 0f))
                    nms[i][3] = min(srcHeight.toFloat(), max(nms[i][3] + pady, 0f))
                }
            }
            nms
        } catch (e: Exception) {
            throw RuntimeException(e.message)
        }
    }

    protected open fun out(
        yoloResult: List<FloatArray>,
        labels: Vector<String>
    ): List<Map<String, Any>> {
        val result: MutableList<Map<String, Any>> = ArrayList()
        //utils.getScreenshotBmp(bitmap, "current");
        for (box in yoloResult) {
            val output: MutableMap<String, Any> = HashMap()
            output["box"] =
                floatArrayOf(box[0], box[1], box[2], box[3], box[4]) //x1,y1,x2,y2,conf_class
            output["tag"] = labels[box[5].toInt()]
            result.add(output)
        }
        return result
    }

    fun close() {
        if (interpreter != null) interpreter!!.close()
        interpreter = null
    }

    companion object {
        fun nms(boxes: MutableList<FloatArray>, iouThreshold: Float): List<FloatArray> {
            return try {
                for (i in boxes.indices) {
                    @Suppress("KotlinConstantConditions")
                    if (i < boxes.size) {
                        val box = boxes[i]
                        var j = i + 1
                        while (j < boxes.size) {
                            val nextBox = boxes[j]
                            val x1 = max(nextBox[0], box[0])
                            val y1 = max(nextBox[1], box[1])
                            val x2 = min(nextBox[2], box[2])
                            val y2 = min(nextBox[3], box[3])
                            val width = max(0f, x2 - x1)
                            val height = max(0f, y2 - y1)
                            val intersection = width * height
                            val union = (nextBox[2] - nextBox[0]) * (nextBox[3] - nextBox[1])
                            +(box[2] - box[0]) * (box[3] - box[1]) - intersection
                            val iou = intersection / union
                            if (iou > iouThreshold) {
                                boxes.removeAt(j)
                                j--
                            }
                            j++
                        }
                    }

                }
                boxes
            } catch (e: Exception) {
                Log.e("nms", e.message!!)
                throw e
            }
        }

        fun reshape(input: Array<Array<FloatArray>>): Array<Array<FloatArray>> {
            val x = input.size
            val y = input[0].size
            val z = input[0][0].size
            val output = Array(x) { Array(z) { FloatArray(y) } }
            for (i in 0 until x) {
                for (j in 0 until y) {
                    for (k in 0 until z) {
                        output[i][k][j] = input[i][j][k]
                    }
                }
            }
            return output
        }
    }
}