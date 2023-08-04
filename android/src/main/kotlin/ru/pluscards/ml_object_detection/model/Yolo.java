package ru.pluscards.ml_object_detection.model;

import static java.lang.Math.min;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.content.res.AssetManager;
import android.util.Log;

import org.tensorflow.lite.Interpreter;
import org.tensorflow.lite.Tensor;
import org.tensorflow.lite.gpu.CompatibilityList;
import org.tensorflow.lite.gpu.GpuDelegate;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.nio.ByteBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Vector;

public class Yolo {
    protected float[][][] output;
    protected Interpreter interpreter;
    protected Vector<String> labels;
    protected final Context context;
    protected final String model_path;
    protected final boolean is_assets;
    protected final int num_threads;
    protected final boolean use_gpu;
    protected final String label_path;
    protected final int rotation;

    public Yolo(Context context,
                String model_path,
                boolean is_assets,
                int num_threads,
                boolean use_gpu,
                String label_path,
                int rotation) {
        this.context = context;
        this.model_path = model_path;
        this.is_assets = is_assets;
        this.num_threads = num_threads;
        this.use_gpu = use_gpu;
        this.label_path = label_path;
        this.rotation = rotation;
    }

    //    public Vector<String> getLabels(){return this.labels;}
    public Tensor getInputTensor() {
        return this.interpreter.getInputTensor(0);
    }

    public void initModel() throws Exception {
        AssetManager assetManager = null;
        MappedByteBuffer buffer = null;
        FileChannel fileChannel = null;
        FileInputStream fileInputStream = null;
        try {
            if (this.interpreter == null) {
                if (is_assets) {
                    assetManager = context.getAssets();
                    AssetFileDescriptor assetFileDescriptor = assetManager.openFd(
                            this.model_path);
                    fileInputStream = new FileInputStream(assetFileDescriptor.getFileDescriptor());

                    fileChannel = fileInputStream.getChannel();
                    buffer = fileChannel.map(
                            FileChannel.MapMode.READ_ONLY, assetFileDescriptor.getStartOffset(),
                            assetFileDescriptor.getLength()
                    );
                    assetFileDescriptor.close();

                } else {
                    fileInputStream = new FileInputStream(new File(this.model_path));
                    fileChannel = fileInputStream.getChannel();
                    buffer = fileChannel.map(FileChannel.MapMode.READ_ONLY, 0, fileChannel.size());

                }
                CompatibilityList compatibilityList = new CompatibilityList();
                Interpreter.Options interpreterOptions = new Interpreter.Options();
                interpreterOptions.setNumThreads(num_threads);
                if (use_gpu) {
                    if (compatibilityList.isDelegateSupportedOnThisDevice()) {
                        GpuDelegate.Options gpuOptions = compatibilityList.getBestOptionsForThisDevice();
                        interpreterOptions.addDelegate(
                                new GpuDelegate(gpuOptions.setQuantizedModelsAllowed(true)));
                    }
                }
                //batch, width, height, channels
                this.interpreter = new Interpreter(buffer, interpreterOptions);
                this.interpreter.allocateTensors();
                this.labels = loadLabels(assetManager, label_path);
                int[] shape = interpreter.getOutputTensor(0).shape();
                this.output = new float[shape[0]][shape[1]][shape[2]];
            }
        } finally {

            if (buffer != null)
                buffer.clear();
            if (fileChannel != null)
                if (fileChannel.isOpen())
                    fileChannel.close();
            if (fileChannel != null)
                if (fileChannel.isOpen())
                    fileInputStream.close();
        }
    }

    protected Vector<String> loadLabels(AssetManager assetManager, String labelPath) throws Exception {
        BufferedReader br = null;
        try {
            if (assetManager != null) {
                br = new BufferedReader(new InputStreamReader(assetManager.open(labelPath)));
            } else {
                br = new BufferedReader(new InputStreamReader(new FileInputStream(new File(labelPath))));
            }
            String line;
            Vector<String> labels = new Vector<>();
            while ((line = br.readLine()) != null) {
                labels.add(line);
            }
            return labels;
        } catch (Exception e) {
            throw new Exception(e.getMessage());
        } finally {
            if (br != null) {
                br.close();
            }
        }
    }

    public List<Map<String, Object>> detectTask(
            ByteBuffer byteBuffer,
            int sourceHeight,
            int sourceWidth,
            float iouThreshold,
            float confThreshold,
            float classThreshold
    ) {
        try {
            int[] shape = this.interpreter.getInputTensor(0).shape();
            this.interpreter.run(byteBuffer, this.output);
            List<float[]> boxes = filterBox(this.output, iouThreshold, confThreshold, classThreshold, shape[1], shape[2]);
            boxes = restoreSize(boxes, shape[1], shape[2], sourceWidth, sourceHeight);
            return out(boxes, this.labels);
        } finally {
            byteBuffer.clear();
        }
    }

    protected List<float[]> filterBox(
            float[][][] modelOutputs,
            float iouThreshold,
            float confThreshold,
            float classThreshold,
            float inputWidth,
            float inputHeight
    ) {
        List<float[]> pre_box = new ArrayList<>();
        int conf_index = 4;
        int class_index = 5;
        int dimension = modelOutputs[0][0].length;
        int rows = modelOutputs[0].length;
        float x1, y1, x2, y2, conf;
        for (int i = 0; i < rows; i++) {
            //convert xywh to xyxy
            x1 = (modelOutputs[0][i][0] - modelOutputs[0][i][2] / 2f) * inputWidth;
            y1 = (modelOutputs[0][i][1] - modelOutputs[0][i][3] / 2f) * inputHeight;
            x2 = (modelOutputs[0][i][0] + modelOutputs[0][i][2] / 2f) * inputWidth;
            y2 = (modelOutputs[0][i][1] + modelOutputs[0][i][3] / 2f) * inputHeight;
            conf = modelOutputs[0][i][conf_index];
            if (conf < confThreshold) continue;
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

    protected static List<float[]> nms(List<float[]> boxes, float iouThreshold) {
        try {
            for (int i = 0; i < boxes.size(); i++) {
                float[] box = boxes.get(i);
                for (int j = i + 1; j < boxes.size(); j++) {
                    float[] next_box = boxes.get(j);
                    float x1 = Math.max(next_box[0], box[0]);
                    float y1 = Math.max(next_box[1], box[1]);
                    float x2 = Math.min(next_box[2], box[2]);
                    float y2 = Math.min(next_box[3], box[3]);

                    float width = Math.max(0, x2 - x1);
                    float height = Math.max(0, y2 - y1);

                    float intersection = width * height;
                    float union = (next_box[2] - next_box[0]) * (next_box[3] - next_box[1])
                            + (box[2] - box[0]) * (box[3] - box[1]) - intersection;
                    float iou = intersection / union;
                    if (iou > iouThreshold) {
                        boxes.remove(j);
                        j--;
                    }
                }
            }
            return boxes;
        } catch (Exception e) {
            Log.e("nms", e.getMessage());
            throw e;
        }
    }

    protected List<float[]> restoreSize(
            List<float[]> nms,
            int inputWidth,
            int inputHeight,
            int srcWidth,
            int srcHeight
    ) {
        try {
            //restore size after scaling, larger images
            if (srcWidth > inputWidth || srcHeight > inputHeight) {
                float gainx = srcWidth / (float) inputWidth;
                float gainy = srcHeight / (float) inputHeight;
                for (int i = 0; i < nms.size(); i++) {
                    nms.get(i)[0] = min(srcWidth, Math.max(nms.get(i)[0] * gainx, 0));
                    nms.get(i)[1] = min(srcHeight, Math.max(nms.get(i)[1] * gainy, 0));
                    nms.get(i)[2] = min(srcWidth, Math.max(nms.get(i)[2] * gainx, 0));
                    nms.get(i)[3] = min(srcHeight, Math.max(nms.get(i)[3] * gainy, 0));
                }
                //restore size after padding, smaller images
            } else {
                float padx = (srcWidth - inputWidth) / 2f;
                float pady = (srcHeight - inputHeight) / 2f;
                for (int i = 0; i < nms.size(); i++) {
                    nms.get(i)[0] = min(srcWidth, Math.max(nms.get(i)[0] + padx, 0));
                    nms.get(i)[1] = min(srcHeight, Math.max(nms.get(i)[1] + pady, 0));
                    nms.get(i)[2] = min(srcWidth, Math.max(nms.get(i)[2] + padx, 0));
                    nms.get(i)[3] = min(srcHeight, Math.max(nms.get(i)[3] + pady, 0));
                }
            }
            return nms;
        } catch (Exception e) {
            throw new RuntimeException(e.getMessage());
        }
    }

    protected static float[][][] reshape(float[][][] input) {
        final int x = input.length;
        final int y = input[0].length;
        final int z = input[0][0].length;
        float[][][] output = new float[x][z][y];
        for (int i = 0; i < x; i++) {
            for (int j = 0; j < y; j++) {
                for (int k = 0; k < z; k++) {
                    output[i][k][j] = input[i][j][k];
                }
            }
        }
        return output;
    }

    protected List<Map<String, Object>> out(List<float[]> yoloResult, Vector<String> labels) {
        List<Map<String, Object>> result = new ArrayList<>();
        //utils.getScreenshotBmp(bitmap, "current");
        for (float[] box : yoloResult) {
            Map<String, Object> output = new HashMap<>();
            output.put("box", new float[]{box[0], box[1], box[2], box[3], box[4]}); //x1,y1,x2,y2,conf_class
            output.put("tag", labels.get((int) box[5]));
            result.add(output);
        }
        return result;
    }

    public void close() {
        if (interpreter != null)
            interpreter.close();
    }
}
