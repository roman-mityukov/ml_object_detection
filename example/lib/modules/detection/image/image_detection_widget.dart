import 'package:flutter/material.dart';
import 'package:flutter_bloc/flutter_bloc.dart';
import 'package:image_picker/image_picker.dart';
import 'package:ml_object_detection_example/modules/detection/image/image_detection_bloc.dart';

class ImageDetectionWidget extends StatefulWidget {
  const ImageDetectionWidget({super.key});

  @override
  State<ImageDetectionWidget> createState() => _ImageDetectionWidgetState();
}

class _ImageDetectionWidgetState extends State<ImageDetectionWidget> {
  @override
  void didChangeDependencies() {
    super.didChangeDependencies();

    context.read<ImageDetectionBloc>().add(InitEvent());
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('Image'),
      ),
      body: BlocBuilder<ImageDetectionBloc, ImageDetectionState>(
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
            InitCompleteState() => Column(
                children: [
                  Row(
                    mainAxisAlignment: MainAxisAlignment.spaceEvenly,
                    children: [
                      ElevatedButton(
                        onPressed: () {
                          context
                              .read<ImageDetectionBloc>()
                              .add(LoadImageEvent(ImageSource.gallery));
                        },
                        child: const Text('Pick image'),
                      ),
                      ElevatedButton(
                        onPressed: () {
                          context
                              .read<ImageDetectionBloc>()
                              .add(LoadImageEvent(ImageSource.camera));
                        },
                        child: const Text('Take photo'),
                      ),
                      ElevatedButton(
                        onPressed: () {
                          context.read<ImageDetectionBloc>().add(DetectEvent());
                        },
                        child: const Text('Detect'),
                      ),
                    ],
                  ),
                  Expanded(
                    child:
                        BlocConsumer<ImageDetectionBloc, ImageDetectionState>(
                      buildWhen: (previous, current) {
                        return current is DetectCompleteState ||
                            current is LoadImageCompleteState;
                      },
                      builder: (context, state) {
                        return switch (state) {
                          LoadImageCompleteState() => Image.file(state.image),
                          DetectCompleteState() => LayoutBuilder(
                              builder: (context, constraints) {
                                return Stack(
                                  fit: StackFit.expand,
                                  children: [
                                    Image.file(state.image),
                                    ...displayBoxesAroundRecognizedObjects(
                                      Size(constraints.maxWidth,
                                          constraints.maxHeight),
                                      state.result,
                                      state.imageWidth,
                                      state.imageHeight,
                                    ),
                                  ],
                                );
                              },
                            ),
                          _ => const SizedBox.shrink(),
                        };
                      },
                      listenWhen: (previous, current) {
                        return current is DetectPendingState ||
                            current is DetectErrorState;
                      },
                      listener: (context, state) {},
                    ),
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
    Size size,
    List<Map<String, dynamic>> results,
    int imageWidth,
    int imageHeight,
  ) {
    if (results.isEmpty) return [];

    double factorX = size.width / (imageWidth);
    double imgRatio = imageWidth / imageHeight;
    double newWidth = imageWidth * factorX;
    double newHeight = newWidth / imgRatio;
    double factorY = newHeight / (imageHeight);
    double padding = (size.height - newHeight) / 2;

    Color colorPick = const Color.fromARGB(255, 50, 233, 30);
    return results.map(
      (result) {
        return Positioned(
          left: result["box"][0] * factorX,
          top: result["box"][1] * factorY + padding,
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
      },
    ).toList();
  }
}
