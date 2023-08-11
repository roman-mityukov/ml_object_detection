package ru.pluscards.ml_object_detection.model

import android.content.Context
import java.util.Collections
import java.util.Vector

class Yolov8(
    context: Context,
    modelPath: String,
    isAssets: Boolean,
    numThreads: Int,
    useGpu: Boolean,
    labelPath: String
) : Yolo(context, modelPath, isAssets, numThreads, useGpu, labelPath) {
    override fun filterBox(
        modelOutputs: Array<Array<FloatArray>>,
        iouThreshold: Float,
        confThreshold: Float,
        classThreshold: Float,
        inputWidth: Float,
        inputHeight: Float
    ): List<FloatArray> {
        //reshape [1,box+class,detected_box] to reshape [1,detected_box,box+class]
        val reshapedModelOutputs = reshape(modelOutputs)
        val preBox: MutableList<FloatArray> = ArrayList()
        val classIndex = 4
        val dimension = reshapedModelOutputs[0][0].size
        val rows = reshapedModelOutputs[0].size
        var x1: Float
        var y1: Float
        var x2: Float
        var y2: Float
        for (i in 0 until rows) {
            //convert xywh to xyxy
            x1 = reshapedModelOutputs[0][i][0] - reshapedModelOutputs[0][i][2] / 2f
            y1 = reshapedModelOutputs[0][i][1] - reshapedModelOutputs[0][i][3] / 2f
            x2 = reshapedModelOutputs[0][i][0] + reshapedModelOutputs[0][i][2] / 2f
            y2 = reshapedModelOutputs[0][i][1] + reshapedModelOutputs[0][i][3] / 2f
            var max = 0f
            var y = 0
            for (j in classIndex until dimension) {
                if (reshapedModelOutputs[0][i][j] < classThreshold) continue
                if (max < reshapedModelOutputs[0][i][j]) {
                    max = reshapedModelOutputs[0][i][j]
                    y = j
                }
            }
            if (max > 0) {
                val tmp = FloatArray(6)
                tmp[0] = x1
                tmp[1] = y1
                tmp[2] = x2
                tmp[3] = y2
                tmp[4] = reshapedModelOutputs[0][i][y]
                tmp[5] = (y - classIndex) * 1f
                preBox.add(tmp)
            }
        }
        if (preBox.isEmpty()) return ArrayList()
        //for reverse orden, insteand of using .reversed method
        val compareValues = java.util.Comparator { v1: FloatArray, v2: FloatArray ->
            v1[1].compareTo(v2[1])
        }
        Collections.sort(preBox, compareValues)
        return nms(preBox, iouThreshold)
    }

    override fun out(yoloResult: List<FloatArray>, labels: Vector<String>): List<Map<String, Any>> {
        val result: MutableList<Map<String, Any>> = ArrayList()
        for (box in yoloResult) {
            val output: MutableMap<String, Any> = HashMap()
            output["box"] =
                floatArrayOf(box[0], box[1], box[2], box[3], box[4]) //x1,y1,x2,y2,conf_class
            output["tag"] = labels[box[5].toInt()]
            result.add(output)
        }
        return result
    }
}