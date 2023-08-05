import 'package:flutter/cupertino.dart';
import 'package:flutter/material.dart';
import 'package:flutter_bloc/flutter_bloc.dart';
import 'package:ml_object_detection_example/modules/detection/image/image_detection_bloc.dart';
import 'package:ml_object_detection_example/modules/detection/image/image_detection_widget.dart';
import 'package:ml_object_detection_example/modules/detection/video/video_detection_bloc.dart';
import 'package:ml_object_detection_example/modules/detection/video/video_detection_widget.dart';
import 'package:ml_object_detection_example/modules/home/home_bloc.dart';

class HomeWidget extends StatelessWidget {
  const HomeWidget({super.key});

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      body: BlocConsumer<HomeBloc, HomeState>(
        buildWhen: (previous, current) {
          return current is IdleHomeState;
        },
        builder: (context, state) {
          if (state is IdleHomeState) {
            return Padding(
              padding: const EdgeInsets.symmetric(horizontal: 16),
              child: ListView.builder(
                itemBuilder: (context, index) {
                  return _HomeRouteWidget(state.routes[index]);
                },
                itemCount: state.routes.length,
              ),
            );
          } else {
            throw UnimplementedError();
          }
        },
        listenWhen: (previous, current) {
          return current is NavigateToDestinationState;
        },
        listener: (context, state) {
          if (state is NavigateToDestinationState) {
            final route = switch (state.route) {
              HomeRoute.image => CupertinoPageRoute(
                  builder: (context) {
                    return BlocProvider(
                      create: (context) =>
                          ImageDetectionBloc(context.read(), context.read()),
                      child: const ImageDetectionWidget(),
                    );
                  },
                ),
              HomeRoute.video => CupertinoPageRoute(
                  builder: (context) {
                    return BlocProvider(
                      create: (context) => VideoDetectionBloc(context.read()),
                      child: const VideoDetectionWidget(),
                    );
                  },
                ),
            };
            Navigator.of(context).push(route);
          } else {
            throw UnimplementedError();
          }
        },
      ),
    );
  }
}

class _HomeRouteWidget extends StatelessWidget {
  final HomeRoute _route;

  const _HomeRouteWidget(this._route);

  @override
  Widget build(BuildContext context) {
    return ElevatedButton(
      onPressed: () {
        context.read<HomeBloc>().add(SelectRouteEvent(_route));
      },
      child: Text(_route.name),
    );
  }
}
