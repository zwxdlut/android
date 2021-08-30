package com.example.flutter_amap;

import android.content.Context;
import android.location.Location;
import android.os.Bundle;
import android.util.Log;
import android.util.SparseArray;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;

import com.amap.api.maps.AMap;
import com.amap.api.maps.CameraUpdateFactory;
import com.amap.api.maps.MapView;
import com.amap.api.maps.model.LatLng;
import com.amap.api.maps.model.Marker;
import com.amap.api.maps.model.MyLocationStyle;
import com.amap.api.navi.AMapNavi;
import com.amap.api.navi.AMapNaviListener;
import com.amap.api.navi.AMapNaviView;
import com.amap.api.navi.enums.PathPlanningStrategy;
import com.amap.api.navi.model.AMapCalcRouteResult;
import com.amap.api.navi.model.AMapLaneInfo;
import com.amap.api.navi.model.AMapModelCross;
import com.amap.api.navi.model.AMapNaviCameraInfo;
import com.amap.api.navi.model.AMapNaviCross;
import com.amap.api.navi.model.AMapNaviLocation;
import com.amap.api.navi.model.AMapNaviPath;
import com.amap.api.navi.model.AMapNaviRouteNotifyData;
import com.amap.api.navi.model.AMapNaviTrafficFacilityInfo;
import com.amap.api.navi.model.AMapServiceAreaInfo;
import com.amap.api.navi.model.AimLessModeCongestionInfo;
import com.amap.api.navi.model.AimLessModeStat;
import com.amap.api.navi.model.NaviInfo;
import com.amap.api.navi.model.NaviLatLng;
import com.amap.api.navi.view.RouteOverLay;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.flutter.plugin.common.BinaryMessenger;
import io.flutter.plugin.common.MethodCall;
import io.flutter.plugin.common.MethodChannel;
import io.flutter.plugin.platform.PlatformView;

public class MyMapView implements PlatformView, MethodChannel.MethodCallHandler, AMap.OnMyLocationChangeListener, AMap.OnMapClickListener, AMapNaviListener {
    private static final String TAG = MyMapView.class.getSimpleName();
    private Context context = null;
    private EventChannelPlugin eventChannelPlugin = null;
    private MapView mapView = null;
    private AMapNaviView mapNaviView = null;
    private Marker mStartMarker = null;
    private Marker mEndMarker = null;
    private AMap aMap = null;
    private AMapNavi mapNavi = null;
    private List<NaviLatLng> startList = new ArrayList<NaviLatLng>();
    private List<NaviLatLng> endList = new ArrayList<NaviLatLng>();
    private List<NaviLatLng> wayPointList = new ArrayList<NaviLatLng>();

    /**
     * 保存当前算好的路线
     */
    private SparseArray<RouteOverLay> routeOverlays = new SparseArray<RouteOverLay>();

    MyMapView(Context context, BinaryMessenger messenger, int id, Map<String, Object> params) {
        this.context = context;
        new MethodChannel(messenger, "plugins.mapview_" + id + ".method").setMethodCallHandler(this);
        eventChannelPlugin = EventChannelPlugin.registerWith(messenger, "plugins.mapview_" + id + ".event");

        Integer type = AMap.MAP_TYPE_NORMAL;

        if (params.containsKey("type")) {
            type = (Integer) params.get("type");
            if (null == type) {
                type = AMap.MAP_TYPE_NORMAL;
            }
        }

        Log.e(TAG, "constructor: type = " + type);

        mapView = new MapView(context);
        mapView.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        mapView.onCreate(new Bundle());

        aMap = mapView.getMap();
        aMap.setTrafficEnabled(true);
        aMap.setMapType(type);
        aMap.addOnMapClickListener(this);

        mapNavi = AMapNavi.getInstance(context);
        mapNavi.addAMapNaviListener(this);
    }

    @Override
    public View getView() {
        Log.e(TAG, "getView");
        return mapView;
    }

    @Override
    public void dispose() {
        Log.e(TAG, "dispose");
        routeOverlays.clear();
        mapNavi.removeAMapNaviListener(this);
        aMap.removeOnMapClickListener(this);
        mapView.onDestroy();
        eventChannelPlugin.cancel();
    }

    @Override
    public void onMethodCall(@NonNull @NotNull MethodCall call, @NonNull @NotNull MethodChannel.Result result) {
        Log.e(TAG, "onMethodCall-" + call.method);

        if ("startLocation".equals(call.method)) {
            MyLocationStyle locationStyle = new MyLocationStyle();
            locationStyle.myLocationType(MyLocationStyle.LOCATION_TYPE_LOCATION_ROTATE_NO_CENTER);
            locationStyle.interval(2000);
            aMap.setOnMyLocationChangeListener(this);
            aMap.setMyLocationStyle(locationStyle);
            aMap.getUiSettings().setMyLocationButtonEnabled(true);
            aMap.setMyLocationEnabled(true);
            result.success(null);
        } else if ("calculateRoute".equals(call.method)) {
            String start = null;
            String end = null;
            String wayPoint = null;

            if (call.hasArgument("start")) {
                start = (String) call.argument("start");
                if (null != start && !start.isEmpty()) {
                    String[] starts = start.split(",");
                    startList.add(new NaviLatLng(Double.parseDouble(starts[1]), Double.parseDouble(starts[0])));
                }
            }

            if (call.hasArgument("end")) {
                end = (String) call.argument("end");
                if (null != end && !end.isEmpty()) {
                    String[] ends = end.split(",");
                    endList.add(new NaviLatLng(Double.parseDouble(ends[1]), Double.parseDouble(ends[0])));
                }
            }

            if (call.hasArgument("wayPoint")) {
                wayPoint = (String) call.argument("wayPoint");
                if (null != wayPoint && !wayPoint.isEmpty()) {
                    String[] wayPoints = wayPoint.split(";");
                    for (String point : wayPoints) {
                        String[] points = point.split(",");
                        wayPointList.add(new NaviLatLng(Double.parseDouble(points[1]), Double.parseDouble(points[0])));
                    }
                }
            }

            Log.e(TAG, "route: start = " + start + ", end = " + end + ", wayPoint = " + wayPoint);
//            Log.e(TAG, "route: startList = " + Arrays.toString(startList.toArray())
//                    + ", endList = " + Arrays.toString(endList.toArray())
//                    + ", wayPointList = " + Arrays.toString(wayPointList.toArray()));
            mapNavi.calculateDriveRoute(startList, endList, wayPointList, PathPlanningStrategy.DRIVING_MULTIPLE_ROUTES_DEFAULT);
            result.success(null);
        } else {
            result.notImplemented();
        }
    }

    @Override
    public void onMyLocationChange(Location location) {
        // 定位回调监听
        if(null != location) {
            double latitude = location.getLatitude();
            double longitude = location.getLongitude();
            Bundle bundle = location.getExtras();

            Log.e(TAG, "定位成功: latitude = " + latitude + ", longitude = " + longitude);

            if(bundle != null) {
                int errorCode = bundle.getInt(MyLocationStyle.ERROR_CODE);
                String errorInfo = bundle.getString(MyLocationStyle.ERROR_INFO);
                // 定位类型，可能为GPS WIFI等，具体可以参考官网的定位SDK介绍
                int locationType = bundle.getInt(MyLocationStyle.LOCATION_TYPE);

                /*
                errorCode
                errorInfo
                locationType
                */
                Log.e(TAG, "定位信息: errorCode = " + errorCode + ", errorInfo = " + errorInfo + ", locationType = " + locationType);
            } else {
                Log.e(TAG, "无定位信息!");
            }

            Map<String, Object> event = new HashMap<>();
            event.put("location", new double[]{latitude, longitude});
            eventChannelPlugin.send(event);
        } else {
            Log.e("TAG", "定位失败!");
            eventChannelPlugin.sendError("定位失败!", null, null);
        }
    }

    @Override
    public void onMapClick(LatLng latLng) {
        Log.e(TAG, "onMapClick: latLng = " + latLng);
        Map<String, Object> event = new HashMap<>();
        event.put("map_click", new double[]{latLng.latitude, latLng.longitude});
        eventChannelPlugin.send(event);
    }

    @Override
    public void onInitNaviFailure() {

    }

    @Override
    public void onInitNaviSuccess() {

    }

    @Override
    public void onStartNavi(int i) {

    }

    @Override
    public void onTrafficStatusUpdate() {

    }

    @Override
    public void onLocationChange(AMapNaviLocation aMapNaviLocation) {

    }

    @Override
    public void onGetNavigationText(int i, String s) {

    }

    @Override
    public void onGetNavigationText(String s) {

    }

    @Override
    public void onEndEmulatorNavi() {

    }

    @Override
    public void onArriveDestination() {

    }

    @Override
    public void onCalculateRouteFailure(int i) {

    }

    @Override
    public void onReCalculateRouteForYaw() {

    }

    @Override
    public void onReCalculateRouteForTrafficJam() {

    }

    @Override
    public void onArrivedWayPoint(int i) {

    }

    @Override
    public void onGpsOpenStatus(boolean b) {

    }

    @Override
    public void onNaviInfoUpdate(NaviInfo naviInfo) {

    }

    @Override
    public void updateCameraInfo(AMapNaviCameraInfo[] aMapNaviCameraInfos) {

    }

    @Override
    public void updateIntervalCameraInfo(AMapNaviCameraInfo aMapNaviCameraInfo, AMapNaviCameraInfo aMapNaviCameraInfo1, int i) {

    }

    @Override
    public void onServiceAreaUpdate(AMapServiceAreaInfo[] aMapServiceAreaInfos) {

    }

    @Override
    public void showCross(AMapNaviCross aMapNaviCross) {

    }

    @Override
    public void hideCross() {

    }

    @Override
    public void showModeCross(AMapModelCross aMapModelCross) {

    }

    @Override
    public void hideModeCross() {

    }

    @Override
    public void showLaneInfo(AMapLaneInfo[] aMapLaneInfos, byte[] bytes, byte[] bytes1) {

    }

    @Override
    public void showLaneInfo(AMapLaneInfo aMapLaneInfo) {

    }

    @Override
    public void hideLaneInfo() {

    }

    @Override
    public void onCalculateRouteSuccess(int[] ints) {

    }

    @Override
    public void notifyParallelRoad(int i) {

    }

    @Override
    public void OnUpdateTrafficFacility(AMapNaviTrafficFacilityInfo[] aMapNaviTrafficFacilityInfos) {

    }

    @Override
    public void OnUpdateTrafficFacility(AMapNaviTrafficFacilityInfo aMapNaviTrafficFacilityInfo) {

    }

    @Override
    public void updateAimlessModeStatistics(AimLessModeStat aimLessModeStat) {

    }

    @Override
    public void updateAimlessModeCongestionInfo(AimLessModeCongestionInfo aimLessModeCongestionInfo) {

    }

    @Override
    public void onPlayRing(int i) {

    }

    @Override
    public void onCalculateRouteSuccess(AMapCalcRouteResult aMapCalcRouteResult) {
        int routeType = aMapCalcRouteResult.getCalcRouteType();
        int[] routeIds = aMapCalcRouteResult.getRouteid();
        int errorCode = aMapCalcRouteResult.getErrorCode();
        String errorDescription = aMapCalcRouteResult.getErrorDescription();
        String errorDetail = aMapCalcRouteResult.getErrorDetail();

        Log.e(TAG, "onCalculateRouteSuccess: routeType = " + routeType + ", routeId = " + Arrays.toString(routeIds)
                + ", errorCode = " + errorCode + ", errorDescription =" + errorDescription + ", errorDetail = " + errorDetail);

        // 清空上次计算的路径列表
        routeOverlays.clear();

        HashMap<Integer, AMapNaviPath> paths = mapNavi.getNaviPaths();

        for (int routeId : routeIds) {
            AMapNaviPath path = paths.get(routeId);
            if (path != null) {
                drawRoutes(routeId, path);
            }
        }

        // 改变地图状态
        aMap.moveCamera(CameraUpdateFactory.changeTilt(0));
        aMap.moveCamera(CameraUpdateFactory.newLatLngBounds(mapNavi.getNaviPath().getBoundsForPath(), 200));

        Map<String, Object> event = new HashMap<>();
        event.put("calculate_route_result", true);
        eventChannelPlugin.send(event);
    }

    @Override
    public void onCalculateRouteFailure(AMapCalcRouteResult aMapCalcRouteResult) {
        int routeType = aMapCalcRouteResult.getCalcRouteType();
        int[] routeId = aMapCalcRouteResult.getRouteid();
        int errorCode = aMapCalcRouteResult.getErrorCode();
        String errorDescription = aMapCalcRouteResult.getErrorDescription();
        String errorDetail = aMapCalcRouteResult.getErrorDetail();
        Log.e(TAG, "onCalculateRouteFailure: routeType = " + routeType + ", routeId = " + Arrays.toString(routeId)
                + ", errorCode = " + errorCode + ", errorDescription =" + errorDescription + ", errorDetail = " + errorDetail);

        Map<String, Object> event = new HashMap<>();
        event.put("calculate_route_result", false);
        eventChannelPlugin.send(event);
    }

    @Override
    public void onNaviRouteNotify(AMapNaviRouteNotifyData aMapNaviRouteNotifyData) {

    }

    @Override
    public void onGpsSignalWeak(boolean b) {

    }

    private void drawRoutes(int routeId, AMapNaviPath path) {
        RouteOverLay routeOverLay = new RouteOverLay(aMap, path, context);
        routeOverLay.setTrafficLine(false);
        routeOverLay.addToMap();
        routeOverlays.put(routeId, routeOverLay);
    }
}
