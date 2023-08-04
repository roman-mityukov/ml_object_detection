import 'package:flutter/foundation.dart';

import 'ml_object_detection_platform_interface.dart';

class MlObjectDetection {
  Future<void> loadModel({
    required String modelPath,
    required String labels,
    int? numThreads,
    bool? useGpu,
  }) async {
    return MlObjectDetectionPlatform.instance.loadModel(
      modelPath: modelPath,
      labels: labels,
      numThreads: numThreads,
      useGpu: useGpu,
    );
  }

  Future<List<Map<String, dynamic>>> onFrame({
    required List<Uint8List> bytesList,
    required int imageHeight,
    required int imageWidth,
    double? iouThreshold,
    double? confThreshold,
    double? classThreshold,
  }) async {
    return MlObjectDetectionPlatform.instance.onFrame(
      bytesList: bytesList,
      imageHeight: imageHeight,
      imageWidth: imageWidth,
      iouThreshold: iouThreshold,
      confThreshold: confThreshold,
      classThreshold: classThreshold,
    );
  }

  Future<List<Map<String, dynamic>>> onImage({
    required Uint8List bytesList,
    required int imageHeight,
    required int imageWidth,
    double? iouThreshold,
    double? confThreshold,
    double? classThreshold,
  }) async {
    return MlObjectDetectionPlatform.instance.onImage(
      bytesList: bytesList,
      imageHeight: imageHeight,
      imageWidth: imageWidth,
      iouThreshold: iouThreshold,
      confThreshold: confThreshold,
      classThreshold: classThreshold,
    );
  }

  Future<void> closeModel() async {
    return MlObjectDetectionPlatform.instance.closeModel();
  }
}
