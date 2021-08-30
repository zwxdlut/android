import 'dart:async';

import 'package:flutter/cupertino.dart';
import 'package:flutter/services.dart';

import 'mapcontrol.dart';

typedef OnCreateCallback = void Function(MapControl mapControl);

class MapView extends StatefulWidget {
  final int type;
  final OnCreateCallback? onCreate;

  MapView({
    Key? key,
    required this.type,
    this.onCreate,
  }): super(key: key);

  @override
  State<MapView> createState() => _MapViewState();
}

class _MapViewState extends State<MapView> {
  late MapControl _mapControl;

  @override
  Widget build(BuildContext context) {
    return AndroidView(
      viewType: 'plugins.mapview',
      onPlatformViewCreated: _onPlatformViewCreated,
      creationParams: {
        "type": widget.type,
      },
      creationParamsCodec: const StandardMessageCodec(),
    );
  }

  @override
  void dispose() {
    _mapControl.cancel();
    super.dispose();
  }

  void _onPlatformViewCreated(int id) {
    _mapControl = MapControl(id);
    widget.onCreate!(_mapControl);
  }
}