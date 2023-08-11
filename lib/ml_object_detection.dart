import 'ml_object_detection_platform_interface.dart';

class MlObjectDetection {
  Stream<List<Map<String, Object>>> detections() {
    return MlObjectDetectionPlatform.instance.detections();
  }

  Future<int> init({
    required String modelPath,
    required String classesPath,
    required int previewWidth,
    required int previewHeight,
    int? numThreads,
    bool? useGpu,
  }) async {
    return MlObjectDetectionPlatform.instance.init(
      modelPath: modelPath,
      classesPath: classesPath,
      previewWidth: previewWidth,
      previewHeight: previewHeight,
      numThreads: numThreads,
      useGpu: useGpu,
    );
  }

  Future<void> deinit() async {
    return MlObjectDetectionPlatform.instance.deinit();
  }
}
