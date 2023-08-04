package ru.pluscards.ml_object_detection.model;

import android.content.Context;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Vector;

public class Yolov8 extends Yolo {
    public Yolov8(
            Context context,
            String modelPath,
            boolean isAssets,
            int num_threads,
            boolean useGpu,
            String labelPath,
            int rotation
    ) {
        super(context, modelPath, isAssets, num_threads, useGpu, labelPath, rotation);
    }

    @Override
    protected List<float[]> filterBox(
            float[][][] modelOutputs,
            float iouThreshold,
            float confThreshold,
            float classThreshold,
            float inputWidth,
            float inputHeight
    ) {
        //reshape [1,box+class,detected_box] to reshape [1,detected_box,box+class]
        modelOutputs = reshape(modelOutputs);
        List<float[]> pre_box = new ArrayList<>();
        int class_index = 4;
        int dimension = modelOutputs[0][0].length;
        int rows = modelOutputs[0].length;
        float x1, y1, x2, y2;
        for (int i = 0; i < rows; i++) {
            //convert xywh to xyxy
            x1 = (modelOutputs[0][i][0] - modelOutputs[0][i][2] / 2f);
            y1 = (modelOutputs[0][i][1] - modelOutputs[0][i][3] / 2f);
            x2 = (modelOutputs[0][i][0] + modelOutputs[0][i][2] / 2f);
            y2 = (modelOutputs[0][i][1] + modelOutputs[0][i][3] / 2f);
            float max = 0;
            int y = 0;
            for (int j = class_index; j < dimension; j++) {
                if (modelOutputs[0][i][j] < classThreshold) continue;
                if (max < modelOutputs[0][i][j]) {
                    max = modelOutputs[0][i][j];
                    y = j;
                }
            }
            if (max > 0) {
                float[] tmp = new float[6];
                tmp[0] = x1;
                tmp[1] = y1;
                tmp[2] = x2;
                tmp[3] = y2;
                tmp[4] = modelOutputs[0][i][y];
                tmp[5] = (y - class_index) * 1f;
                pre_box.add(tmp);
            }
        }
        if (pre_box.isEmpty()) return new ArrayList<>();
        //for reverse orden, insteand of using .reversed method
        Comparator<float[]> compareValues = (v1, v2) -> Float.compare(v1[1], v2[1]);
        //Collections.sort(pre_box,compareValues.reversed());
        Collections.sort(pre_box, compareValues);
        return nms(pre_box, iouThreshold);
    }

    @Override
    protected List<Map<String, Object>> out(List<float[]> yoloResult, Vector<String> labels) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (float[] box : yoloResult) {
            Map<String, Object> output = new HashMap<>();
            output.put("box", new float[]{box[0], box[1], box[2], box[3], box[4]}); //x1,y1,x2,y2,conf_class
            output.put("tag", labels.get((int) box[5]));
            result.add(output);
        }
        return result;
    }
}
