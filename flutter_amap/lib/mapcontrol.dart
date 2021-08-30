import 'dart:async';

import 'package:flutter/services.dart';

typedef OnLocationChangeCallback = void Function(List<double> latlng);
typedef OnMapClickCallback = void Function(List<double> latlng);
typedef OnCalculateRouteCallback = void Function(bool success);

class MapControl {
  int _id;
  late MethodChannel _mapMethodChannel;
  late EventChannel _eventChannel;
  late StreamSubscription _streamSubscription;
  late OnLocationChangeCallback _locationChangeCallback;
  late OnMapClickCallback _mapClickCallback;
  late OnCalculateRouteCallback _calculateRouteCallback;

  MapControl(this._id) {
    _mapMethodChannel = MethodChannel('plugins.mapview_$_id.method');
    _eventChannel = EventChannel('plugins.mapview_$_id.event');
    _streamSubscription = _eventChannel
        .receiveBroadcastStream(['map view event channel setup'])
        .listen(_onData, onError: _onError, onDone: _onDone);
  }

  void setLocationCallback(OnLocationChangeCallback callback) {
    _locationChangeCallback = callback;
  }

  void setMapClickCallback(OnMapClickCallback callback) {
    _mapClickCallback = callback;
  }

  void setCalculateRouteCallback(OnCalculateRouteCallback callback) {
    _calculateRouteCallback = callback;
  }

  Future<void> startLocation() async {
    try {
      return await _mapMethodChannel.invokeMethod('startLocation');
    } on PlatformException catch (e) {
      print(e);
    }
  }

  Future<void> calculateRoute(Map<String, dynamic> route) async {
    try {
      return await _mapMethodChannel.invokeMethod('calculateRoute', route);
    } on PlatformException catch (e) {
      print(e);
    }
  }

  void cancel() {
    _streamSubscription.cancel();
  }

  // native端发送正常数据
  void _onData(event) {
    Map data = event;

    if (data.containsKey('location')) {
      _locationChangeCallback(data['location']);
    } else if (data.containsKey('calculate_route_result')) {
      _calculateRouteCallback(data['calculate_route_result']);
    } else if (data.containsKey('map_click')) {
      _mapClickCallback(data['map_click']);
    }
  }

  // 当native出错时，发送的数据
  void _onError(error) {
  }

  // 当native发送数据完成时调用的方法，每一次发送完成就会调用
  void _onDone() {
  }
}