import 'dart:typed_data';

import 'package:flutter_test/flutter_test.dart';
import 'package:ml_object_detection/ml_object_detection.dart';
import 'package:ml_object_detection/ml_object_detection_platform_interface.dart';
import 'package:ml_object_detection/ml_object_detection_method_channel.dart';
import 'package:plugin_platform_interface/plugin_platform_interface.dart';

class MockMlObjectDetectionPlatform
    with MockPlatformInterfaceMixin
    implements MlObjectDetectionPlatform {

  @override
  Future<void> deinit() {
    // TODO: implement closeModel
    throw UnimplementedError();
  }

  @override
  Future<void> init({required String modelPath, required String classesPath, int? numThreads, bool? useGpu}) {
    // TODO: implement loadModel
    throw UnimplementedError();
  }

  @override
  Future<List<Map<String, dynamic>>> onFrame({required List<Uint8List> bytesList, required int imageHeight, required int imageWidth, double? iouThreshold, double? confThreshold, double? classThreshold}) {
    // TODO: implement onFrame
    throw UnimplementedError();
  }

  @override
  Future<List<Map<String, dynamic>>> onImage({required Uint8List bytesList, required int imageHeight, required int imageWidth, double? iouThreshold, double? confThreshold, double? classThreshold}) {
    // TODO: implement onImage
    throw UnimplementedError();
  }
}

void main() {
  final MlObjectDetectionPlatform initialPlatform = MlObjectDetectionPlatform.instance;

  test('$MethodChannelMlObjectDetection is the default instance', () {
    expect(initialPlatform, isInstanceOf<MethodChannelMlObjectDetection>());
  });

  test('getPlatformVersion', () async {
    MlObjectDetection mlObjectDetectionPlugin = MlObjectDetection();
    MockMlObjectDetectionPlatform fakePlatform = MockMlObjectDetectionPlatform();
    MlObjectDetectionPlatform.instance = fakePlatform;

    //expect(await mlObjectDetectionPlugin.getPlatformVersion(), '42');
  });
}
