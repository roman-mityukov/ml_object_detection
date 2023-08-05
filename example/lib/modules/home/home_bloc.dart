import 'dart:async';

import 'package:equatable/equatable.dart';
import 'package:flutter_bloc/flutter_bloc.dart';

enum HomeRoute { image, video }

abstract interface class HomeEvent {}

class SelectRouteEvent implements HomeEvent {
  final HomeRoute route;

  SelectRouteEvent(this.route);
}

sealed class HomeState extends Equatable {
  @override
  List<Object?> get props => [];
}

class IdleHomeState extends HomeState {
  final List<HomeRoute> routes;

  IdleHomeState(this.routes);

  @override
  List<Object?> get props => [routes];
}

class NavigateToDestinationState extends HomeState {
  final HomeRoute route;

  NavigateToDestinationState(this.route);

  @override
  List<Object?> get props => [route];
}

class HomeBloc extends Bloc<HomeEvent, HomeState> {
  HomeBloc() : super(IdleHomeState(HomeRoute.values)) {
    on<SelectRouteEvent>(_onSelectRouteEvent);
  }

  Future<void> _onSelectRouteEvent(
    SelectRouteEvent event,
    Emitter<HomeState> emit,
  ) async {
    emit(NavigateToDestinationState(event.route));
    emit(IdleHomeState(HomeRoute.values));
  }
}
