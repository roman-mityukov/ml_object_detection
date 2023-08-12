import 'package:flutter/material.dart';
import 'package:flutter_bloc/flutter_bloc.dart';
import 'package:logging/logging.dart';
import 'package:ml_object_detection/ml_preview_widget.dart';
import 'package:ml_object_detection_example/modules/detection/video/video_detection_bloc.dart';

class VideoDetectionWidget extends StatefulWidget {
  const VideoDetectionWidget({super.key});

  @override
  State<VideoDetectionWidget> createState() => _VideoDetectionWidgetState();
}

class _VideoDetectionWidgetState extends State<VideoDetectionWidget> {
  final _logger = Logger('_VideoDetectionWidgetState');

  @override
  void didChangeDependencies() {
    super.didChangeDependencies();

    context.read<VideoDetectionBloc>().add(InitEvent());
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
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
                  MlPreviewWidget(state.textureId),
                  BlocBuilder<VideoDetectionBloc, VideoDetectionState>(
                    buildWhen: (previous, current) {
                      return current is DetectCompleteState;
                    },
                    builder: (context, state) {
                      if (state is DetectCompleteState) {
                        return LayoutBuilder(builder: (context, constraints) {
                          return Stack(
                            children: _displayBoxesAroundRecognizedObjects(
                              Size(constraints.maxWidth, constraints.maxHeight),
                              state.previewWidth,
                              state.previewHeight,
                              state.result,
                            ),
                          );
                        });
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

  List<Widget> _displayBoxesAroundRecognizedObjects(
    Size size,
    int previewWidth,
    int previewHeight,
    List<Map<String, dynamic>> result,
  ) {
    if (result.isEmpty) return [];
    double factorX = size.width / previewWidth;
    double factorY = size.height / previewHeight;

    Color colorPick = const Color.fromARGB(255, 50, 233, 30);

    return result.map((result) {
      double left = result["box"][0] * factorX;
      double top = result["box"][1] * factorY;
      double width = (result["box"][2] - result["box"][0]) * factorX;
      double height = (result["box"][3] - result["box"][1]) * factorY;
      String label = result['tag'];

      return Positioned(
        left: left,
        top: top,
        width: width,
        height: height,
        child: Container(
          decoration: BoxDecoration(
            borderRadius: const BorderRadius.all(Radius.circular(10.0)),
            border: Border.all(color: Colors.pink, width: 2.0),
          ),
          child: Text(
            "$label ${(result['box'][4] * 100).toStringAsFixed(0)}%",
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
