import 'dart:async';

import 'package:flutter_bloc/flutter_bloc.dart';
import 'package:logging/logging.dart';
import 'package:ml_object_detection/ml_object_detection.dart';
import 'package:ml_object_detection_example/app/app_assembly.dart';

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
  final int previewWidth;
  final int previewHeight;

  DetectCompleteState({
    required this.result,
    required this.previewWidth,
    required this.previewHeight,
  });
}

class VideoDetectionBloc
    extends Bloc<VideoDetectionEvent, VideoDetectionState> {
  final _logger = Logger('VideoDetectionBloc');
  final MlObjectDetection _mlObjectDetection;

  VideoDetectionBloc(this._mlObjectDetection) : super(IdleState()) {
    on<InitEvent>(_onInitEvent);
    on<DetectCompleteEvent>(_onDetectCompleteEvent);
  }

  @override
  Future<void> close() async {
    await super.close();
    await _mlObjectDetection.deinit();
  }

  int _counter = 0;

  Future<void> _onInitEvent(
    InitEvent event,
    Emitter<VideoDetectionState> emit,
  ) async {
    try {
      emit(InitPendingState());

      final environmentInfo = await _mlObjectDetection.init(
        classesPath: 'assets/labels.txt',
        modelPath: 'assets/yolov8n.tflite',
        previewWidth: AppAssembly.previewWidth,
        previewHeight: AppAssembly.previewHeight,
        numThreads: 2,
        useGpu: true,
      );

      _mlObjectDetection.detections().listen(
        (results) {
          if (results.isNotEmpty) {
            _logger.fine('event ${results.first}');
          }

          if (results.isEmpty) {
            _counter++;
          }

          if (!isClosed && (results.isNotEmpty || _counter > 3)) {
            _counter = 0;
            add(DetectCompleteEvent(results));
          }
        },
      );

      emit(InitCompleteState(environmentInfo.textureId));
    } catch (error, stackTrace) {
      _logger.warning('Init error', error, stackTrace);
      emit(InitErrorState());
    }
  }

  Future<void> _onDetectCompleteEvent(
    DetectCompleteEvent event,
    Emitter<VideoDetectionState> emit,
  ) async {
    // Здесь height и width передаются в таком порядке, т.к. см как камера
    // возвращает превью в андроид
    emit(DetectCompleteState(
      result: event.result,
      previewWidth: AppAssembly.previewWidth,
      previewHeight: AppAssembly.previewHeight,
    ));
  }
}
