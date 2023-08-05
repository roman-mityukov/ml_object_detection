import 'package:flutter/material.dart';
import 'package:flutter_bloc/flutter_bloc.dart';
import 'package:image_picker/image_picker.dart';
import 'package:ml_object_detection/ml_object_detection.dart';
import 'package:ml_object_detection_example/modules/home/home_bloc.dart';
import 'package:ml_object_detection_example/modules/home/home_widget.dart';
import 'package:provider/provider.dart';

void main() {
  final mlObjectDetectionPlugin = MlObjectDetection();

  runApp(
    MultiProvider(
      providers: [
        Provider<MlObjectDetection>.value(value: mlObjectDetectionPlugin),
        Provider<ImagePicker>.value(value: ImagePicker()),
      ],
      child: const MyApp(),
    ),
  );
}

class MyApp extends StatefulWidget {
  const MyApp({super.key});

  @override
  State<MyApp> createState() => _MyAppState();
}

class _MyAppState extends State<MyApp> {
  @override
  void initState() {
    super.initState();
  }

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      debugShowCheckedModeBanner: false,
      home: Scaffold(
        body: BlocProvider<HomeBloc>(
          create: (context) => HomeBloc(),
          child: const HomeWidget(),
        ),
      ),
    );
  }
}