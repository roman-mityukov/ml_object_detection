import 'package:flutter/foundation.dart';
import 'package:plugin_platform_interface/plugin_platform_interface.dart';

import 'ml_object_detection_method_channel.dart';

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

  /// load YOLO model from the assets folder
  ///
  /// args: [modelPath] - path to the model file
  /// ,[labelsPath] - path to the labels file
  /// ,[numThreads] - number of threads to use for inference
  /// ,[useGPU] - use GPU for inference
  Future<void> loadModel(
      {required String modelPath,
        required String labels,
        int? numThreads,
        bool? useGpu});

  ///onFrame accept a byte List as input and
  ///return a List<Map<String, dynamic>>.
  ///
  ///where map is mapped as follow:
  ///
  ///```Map<String, dynamic>:{
  ///    "box": [x1:top, y1:left, x2:bottom, y2:right, class_confidence]
  ///    "tag": String: detected class
  /// }```
  ///
  ///args: [bytesList] - image as byte list
  ///, [imageHeight] - image height
  ///, [imageWidth] - image width
  ///, [iouThreshold] - intersection over union threshold, default 0.4
  ///, [confThreshold] - model confidence threshold, default 0.5, only for [yolov5]
  ///, [classThreshold] - class confidence threshold, default 0.5
  Future<List<Map<String, dynamic>>> onFrame({
    required List<Uint8List> bytesList,
    required int imageHeight,
    required int imageWidth,
    double? iouThreshold,
    double? confThreshold,
    double? classThreshold,
  });

  ///onImage accept a Uint8List as input and
  ///return a List<Map<String, dynamic>>.
  ///
  ///where map is mapped as follows:
  ///
  ///```Map<String, dynamic>:{
  ///    "box": [x1:top, y1:left, x2:bottom, y2:right, class_confidence]
  ///    "tag": String: detected class
  /// }```
  ///
  ///args: [bytesList] - image bytes
  ///, [imageHeight] - image height
  ///, [imageWidth] - image width
  ///, [iouThreshold] - intersection over union threshold, default 0.4
  ///, [confThreshold] - model confidence threshold, default 0.5, only for [yolov5]
  ///, [classThreshold] - class confidence threshold, default 0.5
  Future<List<Map<String, dynamic>>> onImage({
    required Uint8List bytesList,
    required int imageHeight,
    required int imageWidth,
    double? iouThreshold,
    double? confThreshold,
    double? classThreshold,
  });

  /// dispose YOLO model, clean and save resources
  Future<void> closeModel();
}
