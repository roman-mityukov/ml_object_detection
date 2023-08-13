import 'package:flutter/material.dart';
import 'package:ml_object_detection/ml_preview_widget.dart';

import 'ml_object_detection_platform_interface.dart';

class MlObjectDetection {
  EnvironmentInfo? _environmentInfo;

  Stream<List<Map<String, Object>>> detections() {
    return MlObjectDetectionPlatform.instance.detections();
  }

  Future<EnvironmentInfo> init({
    required String modelPath,
    required String classesPath,
    required int previewWidth,
    required int previewHeight,
    int? numThreads,
    bool? useGpu,
  }) async {
    _environmentInfo = await MlObjectDetectionPlatform.instance.init(
      modelPath: modelPath,
      classesPath: classesPath,
      previewWidth: previewWidth,
      previewHeight: previewHeight,
      numThreads: numThreads,
      useGpu: useGpu,
    );
    return _environmentInfo!;
  }

  Future<void> deinit() async {
    return MlObjectDetectionPlatform.instance.deinit();
  }

  Widget buildPreview() {
    final previewWidget = MlPreviewWidget(_environmentInfo!.textureId);
    Widget result;
    if (_environmentInfo!.cameraProvider == CameraProvider.phone) {
      result = RotatedBox(quarterTurns: 3, child: previewWidget);
    } else {
      result = previewWidget;
    }

    return result;
  }
}
