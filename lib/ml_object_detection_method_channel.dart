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
  Stream<List<Map<String, Object>>> detections() async* {
    await for (List<Object?> result in eventChannel.receiveBroadcastStream()) {
      final detectionResult = <Map<String, Object>>[];
      for (final dynamic concreteResult in result) {
        detectionResult.add(
          {
            'tag': concreteResult['tag'] as String,
            'box': (concreteResult['box'] as List<dynamic>)
                .map((e) => e as double)
                .toList()
          },
        );
      }
      yield detectionResult;
    }
  }

  @override
  Future<int> init({
    required String modelPath,
    required String classesPath,
    required int previewWidth,
    required int previewHeight,
    int? numThreads,
    bool? useGpu,
  }) async {
    return (await methodChannel.invokeMethod<int>(
      'init',
      {
        'model_path': modelPath,
        'classes_path': classesPath,
        'preview_width': previewWidth,
        'preview_height': previewHeight,
        'is_asset': true,
        'num_threads': numThreads ?? 1,
        'use_gpu': useGpu ?? false,
      },
    ))!;
  }

  @override
  Future<void> deinit() async {
    await methodChannel.invokeMethod('deinit');
  }
}
