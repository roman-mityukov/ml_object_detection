import 'dart:async';

import 'package:camera/camera.dart';
import 'package:flutter_bloc/flutter_bloc.dart';
import 'package:logging/logging.dart';
import 'package:ml_object_detection/ml_object_detection.dart';

abstract interface class VideoDetectionEvent {}

class InitEvent implements VideoDetectionEvent {}

class DetectCompleteEvent implements VideoDetectionEvent {
  final List<Map<String, dynamic>> result;

  DetectCompleteEvent(this.result);
}

sealed class VideoDetectionState {}

class IdleState extends VideoDetectionState {}

class InitPendingState extends VideoDetectionState {}

class InitCompleteState extends VideoDetectionState {
  final int textureId;

  InitCompleteState(this.textureId);
}

class InitErrorState extends VideoDetectionState {}

class DetectCompleteState extends VideoDetectionState {
  final List<Map<String, dynamic>> result;

  DetectCompleteState(this.result);
}

class VideoDetectionBloc
    extends Bloc<VideoDetectionEvent, VideoDetectionState> {
  late final CameraController _cameraController;
  final _logger = Logger('VideoDetectionBloc');
  final MlObjectDetection _mlObjectDetection;

  VideoDetectionBloc(this._mlObjectDetection) : super(IdleState()) {
    on<InitEvent>(_onInitEvent);
    on<DetectCompleteEvent>(_onDetectCompleteEvent);
  }

  @override
  Future<void> close() async {
    await super.close();
    await _mlObjectDetection.closeModel();
    await _cameraController.stopImageStream();
    await _cameraController.dispose();
  }

  int _counter = 0;

  Future<void> _onInitEvent(
    InitEvent event,
    Emitter<VideoDetectionState> emit,
  ) async {
    try {
      emit(InitPendingState());

      final textureId = await _mlObjectDetection.getTextureId();

      await _mlObjectDetection.loadModel(
        labels: 'assets/labels.txt',
        modelPath: 'assets/yolov8n.tflite',
        numThreads: 2,
        useGpu: true,
      );

      // final cameras = await availableCameras();
      // _cameraController = CameraController(
      //   cameras[0],
      //   ResolutionPreset.high,
      //   enableAudio: false,
      // );
      // await _cameraController.initialize();
      //
      // _logger.fine(
      //     'Camera is initialized. CameraController.value ${_cameraController.value}');
      //
      // _cameraController.startImageStream(
      //   (cameraImage) async {
      //     final stopwatch = Stopwatch()..start();
      //     final results = await _mlObjectDetection.onFrame(
      //       bytesList: cameraImage.planes.map((plane) => plane.bytes).toList(),
      //       imageHeight: cameraImage.height,
      //       imageWidth: cameraImage.width,
      //       iouThreshold: 0.4,
      //       confThreshold: 0.4,
      //       classThreshold: 0.5,
      //     );
      //
      //     stopwatch.stop();
      //     if (results.isNotEmpty) {
      //       _counter = 0;
      //       String logMessage = 'elapsed ${stopwatch.elapsed.inMilliseconds} ';
      //       for(final concreteResult in results) {
      //         logMessage = '${logMessage}result: tag ${concreteResult['tag']}, confidence ${(concreteResult['box'][4] * 100).toStringAsFixed(0)} ';
      //       }
      //       _logger.fine(logMessage);
      //     }
      //
      //     if (results.isEmpty) {
      //       _counter++;
      //     }
      //
      //     if (!isClosed && (results.isNotEmpty || _counter > 20)) {
      //       _counter = 0;
      //       add(DetectCompleteEvent(results, cameraImage));
      //     }
      //   },
      // );

      _mlObjectDetection.objectDetectionResult().listen((event) {
        add(DetectCompleteEvent(event));
      });

      emit(InitCompleteState(textureId));
    } catch (error, stackTrace) {
      _logger.warning('Init error', error, stackTrace);
      emit(InitErrorState());
    }
  }

  Future<void> _onDetectCompleteEvent(
    DetectCompleteEvent event,
    Emitter<VideoDetectionState> emit,
  ) async {
    emit(DetectCompleteState(event.result));
  }
}
