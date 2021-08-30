import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_amap/mapcontrol.dart';
import 'package:flutter_amap/mapview.dart';
import 'package:flutter_amap/naviview.dart';
import 'package:flutter_smart_dialog/flutter_smart_dialog.dart';
import 'package:sprintf/sprintf.dart';

Future<void> main() async {
  runApp(MyApp());
}

class MyApp extends StatelessWidget {
  // This widget is the root of your application.
  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'Flutter Map Demo',
      theme: ThemeData(
        // This is the theme of your application.
        //
        // Try running your application with "flutter run". You'll see the
        // application has a blue toolbar. Then, without quitting the app, try
        // changing the primarySwatch below to Colors.green and then invoke
        // "hot reload" (press "r" in the console where you ran "flutter run",
        // or simply save your changes to "hot reload" in a Flutter IDE).
        // Notice that the counter didn't reset back to zero; the application
        // is not restarted.
        primarySwatch: Colors.blue,
      ),
      home: MyHomePage(title: '地图'),
      builder: (BuildContext context, Widget? child) {
        return FlutterSmartDialog(child: child);
      },
    );
  }
}

class MyHomePage extends StatefulWidget {
  MyHomePage({Key? key, required this.title}) : super(key: key);

  // This widget is the home page of your application. It is stateful, meaning
  // that it has a State object (defined below) that contains fields that affect
  // how it looks.

  // This class is the configuration for the state. It holds the values (in this
  // case the title) provided by the parent (in this case the App widget) and
  // used by the build method of the State. Fields in a Widget subclass are
  // always marked "final".

  final String title;

  @override
  _MyHomePageState createState() => _MyHomePageState();
}

class _MyHomePageState extends State<MyHomePage> {
  late MapControl _mapControl;

  @override
  Widget build(BuildContext context) {
    print("_MyHomePageState::build");

    // This method is rerun every time setState is called, for instance as done
    // by the _incrementCounter method above.
    //
    // The Flutter framework has been optimized to make rerunning build methods
    // fast, so that you can just rebuild anything that needs updating rather
    // than having to individually change instances of widgets.
    return Scaffold(
      appBar: AppBar(
        // Here we take the value from the MyHomePage object that was created by
        // the App.build method, and use it to set our appbar title.
        title: Text(widget.title),
      ),
      body: Center(
        child: MapView(
          type: 1,
          onCreate: _onMapViewCreate,
        ),
      ),
      floatingActionButton: FloatingActionButton(
        onPressed: _enterNavigation,
        child: Icon(Icons.navigation),
      ), // This trailing comma makes auto-formatting nicer for build methods.
      // This trailing comma makes auto-formatting nicer for build methods.
      floatingActionButtonLocation: FloatingActionButtonLocation.centerFloat,
    );
  }

  @override
  void dispose() {
    print("_MyHomePageState::dispose");
    _mapControl.cancel();
    super.dispose();
  }

  /////////////////////////////////////////////////////////////////////////////////////
  // 地图相关
  /////////////////////////////////////////////////////////////////////////////////////

  Future<void> _onMapViewCreate(MapControl mapControl) async {
    print("_MyHomePageState::_onCreate");

    SmartDialog.showLoading(
      msg: '算路中...',
      background: Colors.blue,
    );

    var route = {
      'start': '123.287613,42.405856',
      'end': '116.374438,39.912659'
    };

    _mapControl = mapControl;
    _mapControl.setLocationCallback(_onLocationChange);
    _mapControl.setMapClickCallback(_onMapClick);
    _mapControl.setCalculateRouteCallback(_onCalculateRoute);
    _mapControl.startLocation();
    _mapControl.calculateRoute(route);
  }

  void _onLocationChange(List<double> latlng) {
    print(sprintf("_MyHomePageState::_onLocationChange: latlng = %s", [latlng.toString()]));
  }

  void _onMapClick(List<double> latlng) {
    print(sprintf("_MyHomePageState::_onMapClick: latlng = %s", [latlng.toString()]));
  }

  void _onCalculateRoute(bool success) {
    print(sprintf("_MyHomePageState::_onCalculateRoute: success = %s", [success.toString()]));
    SmartDialog.dismiss();
  }

  /////////////////////////////////////////////////////////////////////////////////////
  // 导航相关
  /////////////////////////////////////////////////////////////////////////////////////

  MethodChannel _naviComMethodChannel = MethodChannel("plugins.navigation.com");
  
  Future<void> _enterNavigation() async {
    print("MyHomePageState::_enterNavigation");

    // try {
    //   await _naviComMethodChannel.invokeMethod('enterNavigation');
    // } on PlatformException catch (e) {
    //   print(e);
    // }

    Navigator.of(context).push(
      new MaterialPageRoute(
        builder: (context) {
          return new Scaffold(
            appBar: new AppBar(
              title: new Text('导航'),
            ),
            body: Center(
              child: NaviView(
                type: 1,
                onCreate: _onNaviViewCreate,
              ),
            ),
          );
        },
      ),
    );
  }

  void _onNaviViewCreate() {
    print("_MyHomePageState::_onMyNaviViewCreated");
  }
}
