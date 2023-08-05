import 'package:camera/camera.dart';
import 'package:flutter/material.dart';
import 'package:flutter_bloc/flutter_bloc.dart';
import 'package:ml_object_detection_example/modules/detection/video/video_detection_bloc.dart';

class VideoDetectionWidget extends StatefulWidget {
  const VideoDetectionWidget({super.key});

  @override
  State<VideoDetectionWidget> createState() => _VideoDetectionWidgetState();
}

class _VideoDetectionWidgetState extends State<VideoDetectionWidget> {
  @override
  void didChangeDependencies() {
    super.didChangeDependencies();

    context.read<VideoDetectionBloc>().add(InitEvent());
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('Video'),
      ),
      body: BlocBuilder<VideoDetectionBloc, VideoDetectionState>(
        buildWhen: (previous, current) {
          return current is IdleState ||
              current is InitPendingState ||
              current is InitCompleteState ||
              current is InitErrorState;
        },
        builder: (context, state) {
          final widget = switch (state) {
            IdleState() => const SizedBox.shrink(),
            InitPendingState() =>
              const Center(child: CircularProgressIndicator()),
            InitErrorState() =>
              const Center(child: Text('Initialization error')),
            InitCompleteState() => Stack(
                fit: StackFit.expand,
                children: [
                  AspectRatio(
                    aspectRatio: state.cameraController.value.aspectRatio,
                    child: CameraPreview(
                      state.cameraController,
                    ),
                  ),
                  BlocBuilder<VideoDetectionBloc, VideoDetectionState>(
                    buildWhen: (previous, current) {
                      return current is DetectCompleteState;
                    },
                    builder: (context, state) {
                      if (state is DetectCompleteState) {
                        return Stack(
                          fit: StackFit.expand,
                          children: displayBoxesAroundRecognizedObjects(
                            MediaQuery.of(context).size,
                            state.result,
                            state.cameraImage,
                          ),
                        );
                      } else {
                        return const SizedBox.shrink();
                      }
                    },
                  ),
                ],
              ),
            _ => const SizedBox.shrink(),
          };

          return widget;
        },
      ),
    );
  }

  List<Widget> displayBoxesAroundRecognizedObjects(
    Size screen,
    List<Map<String, dynamic>> result,
    CameraImage cameraImage,
  ) {
    if (result.isEmpty) return [];
    double factorX = screen.width / (cameraImage.height);
    double factorY = screen.height / (cameraImage.width);

    Color colorPick = const Color.fromARGB(255, 50, 233, 30);

    return result.map((result) {
      return Positioned(
        left: result["box"][0] * factorX,
        top: result["box"][1] * factorY,
        width: (result["box"][2] - result["box"][0]) * factorX,
        height: (result["box"][3] - result["box"][1]) * factorY,
        child: Container(
          decoration: BoxDecoration(
            borderRadius: const BorderRadius.all(Radius.circular(10.0)),
            border: Border.all(color: Colors.pink, width: 2.0),
          ),
          child: Text(
            "${result['tag']} ${(result['box'][4] * 100).toStringAsFixed(0)}%",
            style: TextStyle(
              background: Paint()..color = colorPick,
              color: Colors.white,
              fontSize: 18.0,
            ),
          ),
        ),
      );
    }).toList();
  }
}
