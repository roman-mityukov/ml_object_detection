import 'package:flutter/foundation.dart';
import 'package:flutter/services.dart';

import 'ml_object_detection_platform_interface.dart';

/// An implementation of [MlObjectDetectionPlatform] that uses method channels.
class MethodChannelMlObjectDetection extends MlObjectDetectionPlatform {
  /// The method channel used to interact with the native platform.
  @visibleForTesting
  final methodChannel = const MethodChannel('ml_object_detection_method');

  final eventChannel = const EventChannel('ml_object_detection_stream');

  @override
  Stream<List<Map<String, Object>>> objectDetectionResult() async* {
    await for (List<Object?> result in eventChannel.receiveBroadcastStream()) {
      final detectionResult = <Map<String, Object>>[];
      for (final dynamic concreteResult in result) {
        detectionResult.add(
          {
            'tag': concreteResult['tag'] as String,
            'box': (concreteResult['box'] as List<dynamic>).map((e) => e as double).toList()
          },
        );
      }
      yield detectionResult;
    }
  }

  @override
  Future<int> getTextureId() async {
    final result = await methodChannel.invokeMethod<int>('getTextureId');
    return result!;
  }

  @override
  Future<void> loadModel({
    required String modelPath,
    required String labels,
    int? numThreads,
    bool? useGpu,
  }) async {
    await methodChannel.invokeMethod<String?>('loadModel', {
      'model_path': modelPath,
      'is_asset': true,
      'num_threads': numThreads ?? 1,
      'use_gpu': useGpu ?? false,
      'label_path': labels,
      'rotation': 90
    });
  }

  @override
  Future<List<Map<String, dynamic>>> onFrame({
    required List<Uint8List> bytesList,
    required int imageHeight,
    required int imageWidth,
    double? iouThreshold,
    double? confThreshold,
    double? classThreshold,
  }) async {
    final x = await methodChannel.invokeMethod<List<dynamic>>(
      'onFrame',
      {
        "bytesList": bytesList,
        "image_height": imageHeight,
        "image_width": imageWidth,
        "iou_threshold": iouThreshold ?? 0.4,
        "conf_threshold": confThreshold ?? 0.5,
        "class_threshold": classThreshold ?? 0.5
      },
    );
    return x?.isNotEmpty ?? false
        ? x!.map((e) => Map<String, dynamic>.from(e)).toList()
        : [];
  }

  @override
  Future<List<Map<String, dynamic>>> onImage({
    required Uint8List bytesList,
    required int imageHeight,
    required int imageWidth,
    double? iouThreshold,
    double? confThreshold,
    double? classThreshold,
  }) async {
    final x = await methodChannel.invokeMethod<List<dynamic>>(
      'onImage',
      {
        "bytesList": bytesList,
        "image_height": imageHeight,
        "image_width": imageWidth,
        "iou_threshold": iouThreshold ?? 0.4,
        "conf_threshold": confThreshold ?? 0.5,
        "class_threshold": classThreshold ?? 0.5
      },
    );
    return x?.isNotEmpty ?? false
        ? x!.map((e) => Map<String, dynamic>.from(e)).toList()
        : [];
  }

  @override
  Future<void> closeModel() async {
    await methodChannel.invokeMethod('closeModel');
  }
}
