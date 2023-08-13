import 'package:plugin_platform_interface/plugin_platform_interface.dart';

import 'ml_object_detection_method_channel.dart';

enum CameraProvider {
  glasses, phone
}

class EnvironmentInfo {
  final int textureId;
  final CameraProvider cameraProvider;

  EnvironmentInfo(this.textureId, this.cameraProvider);
}

abstract class MlObjectDetectionPlatform extends PlatformInterface {
  /// Constructs a MlObjectDetectionPlatform.
  MlObjectDetectionPlatform() : super(token: _token);

  static final Object _token = Object();

  static MlObjectDetectionPlatform _instance = MethodChannelMlObjectDetection();

  /// The default instance of [MlObjectDetectionPlatform] to use.
  ///
  /// Defaults to [MethodChannelMlObjectDetection].
  static MlObjectDetectionPlatform get instance => _instance;

  /// Platform-specific implementations should set this with their own
  /// platform-specific class that extends [MlObjectDetectionPlatform] when
  /// they register themselves.
  static set instance(MlObjectDetectionPlatform instance) {
    PlatformInterface.verifyToken(instance, _token);
    _instance = instance;
  }

  Stream<List<Map<String, Object>>> detections();

  /// load YOLO model from the assets folder
  ///
  /// args: [modelPath] - path to the model file
  /// ,[labelsPath] - path to the labels file
  /// ,[numThreads] - number of threads to use for inference
  /// ,[useGPU] - use GPU for inference
  Future<EnvironmentInfo> init({
    required String modelPath,
    required String classesPath,
    required int previewWidth,
    required int previewHeight,
    int? numThreads,
    bool? useGpu,
  });

  /// dispose YOLO model, clean and save resources
  Future<void> deinit();
}
