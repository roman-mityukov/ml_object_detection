import 'dart:async';

import 'package:camera/camera.dart';
import 'package:flutter_bloc/flutter_bloc.dart';
import 'package:logging/logging.dart';
import 'package:ml_object_detection/ml_object_detection.dart';

abstract interface class VideoDetectionEvent {}

class InitEvent implements VideoDetectionEvent {}

class DetectCompleteEvent implements VideoDetectionEvent {
  final List<Map<String, dynamic>> result;
  final CameraImage cameraImage;

  DetectCompleteEvent(this.result, this.cameraImage);
}

sealed class VideoDetectionState {}

class IdleState extends VideoDetectionState {}

class InitPendingState extends VideoDetectionState {}

class InitCompleteState extends VideoDetectionState {
  final CameraController cameraController;

  InitCompleteState(this.cameraController);
}

class InitErrorState extends VideoDetectionState {}

class DetectCompleteState extends VideoDetectionState {
  final List<Map<String, dynamic>> result;
  final CameraImage cameraImage;

  DetectCompleteState(this.result, this.cameraImage);
}

class VideoDetectionBloc
    extends Bloc<VideoDetectionEvent, VideoDetectionState> {
  final _logger = Logger('VideoDetectionBloc');
  final MlObjectDetection _mlObjectDetection;
  late final CameraController _cameraController;

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

  Future<void> _onInitEvent(
    InitEvent event,
    Emitter<VideoDetectionState> emit,
  ) async {
    try {
      emit(InitPendingState());
      await _mlObjectDetection.loadModel(
        labels: 'assets/labels.txt',
        modelPath: 'assets/yolov8n.tflite',
        numThreads: 2,
        useGpu: true,
      );

      final cameras = await availableCameras();
      _cameraController = CameraController(cameras[0], ResolutionPreset.medium);
      await _cameraController.initialize();

      _cameraController.startImageStream(
        (cameraImage) async {
          final result = await _mlObjectDetection.onFrame(
            bytesList: cameraImage.planes.map((plane) => plane.bytes).toList(),
            imageHeight: cameraImage.height,
            imageWidth: cameraImage.width,
            iouThreshold: 0.4,
            confThreshold: 0.4,
            classThreshold: 0.5,
          );

          add(DetectCompleteEvent(result, cameraImage));
        },
      );

      emit(InitCompleteState(_cameraController));
    } catch (error, stackTrace) {
      _logger.warning('Init error', error, stackTrace);
      emit(InitErrorState());
    }
  }

  Future<void> _onDetectCompleteEvent(
    DetectCompleteEvent event,
    Emitter<VideoDetectionState> emit,
  ) async {
    emit(DetectCompleteState(event.result, event.cameraImage));
  }
}
