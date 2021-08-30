import 'dart:async';

import 'package:flutter/cupertino.dart';
import 'package:flutter/services.dart';

typedef OnCreateCallback = void Function();

class NaviView extends StatefulWidget {
  final int type;
  final OnCreateCallback? onCreate;

  NaviView({
    Key? key,
    required this.type,
    this.onCreate,
  }): super(key: key);

  @override
  State<NaviView> createState() => _NaviViewState();
}

class _NaviViewState extends State<NaviView> {
  @override
  Widget build(BuildContext context) {
    return AndroidView(
      viewType: 'plugins.naviview',
      onPlatformViewCreated: _onPlatformViewCreated,
      creationParams: {
        "type": widget.type,
      },
      creationParamsCodec: const StandardMessageCodec(),
    );
  }

  @override
  void dispose() {
    super.dispose();
  }

  void _onPlatformViewCreated(int id) {
    widget.onCreate!();
  }
}