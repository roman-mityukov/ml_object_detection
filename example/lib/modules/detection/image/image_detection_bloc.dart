import 'dart:async';
import 'dart:io';

import 'package:equatable/equatable.dart';
import 'package:flutter/foundation.dart';
import 'package:flutter/painting.dart';
import 'package:flutter_bloc/flutter_bloc.dart';
import 'package:image_picker/image_picker.dart';
import 'package:logging/logging.dart';
import 'package:ml_object_detection/ml_object_detection.dart';

abstract interface class ImageDetectionEvent {}

class InitEvent implements ImageDetectionEvent {}

class LoadImageEvent implements ImageDetectionEvent {
  final ImageSource imageSource;

  LoadImageEvent(this.imageSource);
}

class DetectEvent implements ImageDetectionEvent {}

sealed class ImageDetectionState extends Equatable {
  @override
  List<Object?> get props => [];
}

class IdleState extends ImageDetectionState {}

class InitPendingState extends ImageDetectionState {}

class InitCompleteState extends ImageDetectionState {}

class InitErrorState extends ImageDetectionState {}

class DetectPendingState extends ImageDetectionState {}

class DetectCompleteState extends ImageDetectionState {
  final File image;
  final int imageWidth;
  final int imageHeight;
  final List<Map<String, Object?>> result;

  DetectCompleteState(
    this.image,
    this.imageWidth,
    this.imageHeight,
    this.result,
  );

  @override
  List<Object?> get props => [image.path, result];
}

class DetectErrorState extends ImageDetectionState {}

class LoadImageCompleteState extends ImageDetectionState {
  final File image;

  LoadImageCompleteState(this.image);
}

class ImageDetectionBloc
    extends Bloc<ImageDetectionEvent, ImageDetectionState> {
  final _logger = Logger('ImageDetectionBloc');
  final ImagePicker _imagePicker;
  final MlObjectDetection _mlObjectDetection;
  File? _imageFile;
  int? _imageWidth;
  int? _imageHeight;

  ImageDetectionBloc(
    this._mlObjectDetection,
    this._imagePicker,
  ) : super(IdleState()) {
    on<DetectEvent>(_onDetectEvent);
    on<InitEvent>(_onInitEvent);
    on<LoadImageEvent>(_onLoadImageEvent);
  }

  @override
  Future<void> close() async {
    await super.close();
    await _mlObjectDetection.closeModel();
  }

  Future<void> _onDetectEvent(
    DetectEvent event,
    Emitter<ImageDetectionState> emit,
  ) async {
    if (_imageFile != null) {
      try {
        emit(DetectPendingState());
        Uint8List byte = await _imageFile!.readAsBytes();
        final image = await decodeImageFromList(byte);
        _imageWidth = image.width;
        _imageHeight = image.height;
        final result = await _mlObjectDetection.onImage(
          bytesList: byte,
          imageHeight: image.height,
          imageWidth: image.width,
          iouThreshold: 0.8,
          confThreshold: 0.4,
          classThreshold: 0.5,
        );
        emit(
          DetectCompleteState(_imageFile!, _imageWidth!, _imageHeight!, result),
        );
      } catch (error, stackTrace) {
        _logger.warning('Detect error', error, stackTrace);
        emit(DetectErrorState());
      }
    }
  }

  Future<void> _onInitEvent(
    InitEvent event,
    Emitter<ImageDetectionState> emit,
  ) async {
    try {
      emit(InitPendingState());
      await _mlObjectDetection.loadModel(
        labels: 'assets/labels.txt',
        modelPath: 'assets/yolov8n.tflite',
        numThreads: 2,
        useGpu: true,
      );
      emit(InitCompleteState());
    } catch (error, stackTrace) {
      _logger.warning('Init error', error, stackTrace);
      emit(InitErrorState());
    }
  }

  Future<void> _onLoadImageEvent(
    LoadImageEvent event,
    Emitter<ImageDetectionState> emit,
  ) async {
    final XFile? photo =
        await _imagePicker.pickImage(source: event.imageSource);
    if (photo != null) {
      _imageFile = File(photo.path);
      emit(LoadImageCompleteState(_imageFile!));
    }
  }
}
