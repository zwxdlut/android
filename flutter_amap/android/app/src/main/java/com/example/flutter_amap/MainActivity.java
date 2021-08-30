package com.example.flutter_amap;

import android.Manifest;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;

import com.amap.api.navi.AmapNaviPage;
import com.amap.api.navi.AmapNaviParams;
import com.amap.api.navi.AmapNaviType;
import com.amap.api.navi.AmapPageType;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import io.flutter.embedding.android.FlutterActivity;
import io.flutter.plugin.common.MethodCall;
import io.flutter.plugin.common.MethodChannel;

public class MainActivity extends FlutterActivity implements MethodChannel.MethodCallHandler {
    private static final String TAG = MainActivity.class.getSimpleName();
    private static final int PERMISSION_REQUEST = 1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.e(TAG, "onCreate");

        requestPermission();
        ViewPlugin.registerWith(getFlutterEngine());
        // MyNaviViewFlutterPlugin.registerWith(getFlutterEngine());
        // 新版的Flutter SDK默认使用的是 import io.flutter.embedding.android.FlutterActivity; 包，
        // 则在MethodChannel方法中的第一个参数填写 getFlutterEngine().getDartExecutor().getBinaryMessenger()
        // 如果你使用的Flutter的SDK是旧版本，那么默认的是 import io.flutter.app.FlutterActivity; 包
        // 则MethodChannel方法中的第一个参数填写 getFlutterView()
        new MethodChannel(Objects.requireNonNull(getFlutterEngine()).getDartExecutor().getBinaryMessenger(), "plugins.navigation.com").setMethodCallHandler(this);
    }

    @Override
    protected void onDestroy() {
        Log.e(TAG, "onDestroy");
        // 退出导航组件
        AmapNaviPage.getInstance().exitRouteActivity();
        super.onDestroy();
    }

    @Override
    public void onMethodCall(@NonNull @NotNull MethodCall call, @NonNull @NotNull MethodChannel.Result result) {
        Log.d(TAG, "onMethodCall-" + call.method);

        if ("enterNavigation".equals(call.method)) {
            // 构建导航组件配置类，没有传入起点，所以起点默认为 “我的位置”
            AmapNaviParams params = new AmapNaviParams(null, null, null, AmapNaviType.DRIVER, AmapPageType.ROUTE);
            // 启动导航组件
            AmapNaviPage.getInstance().showRouteActivity(getApplicationContext(), params, null);
            result.success(0);
        } else {
            result.notImplemented();
        }
    }

    private void requestPermission() {
        List<String> permissions = new ArrayList<>();

        permissions.add(Manifest.permission.INTERNET);
        permissions.add(Manifest.permission.WRITE_EXTERNAL_STORAGE);
        permissions.add(Manifest.permission.ACCESS_NETWORK_STATE);
        permissions.add(Manifest.permission.ACCESS_WIFI_STATE);
        permissions.add(Manifest.permission.READ_PHONE_STATE);
        permissions.add(Manifest.permission.ACCESS_COARSE_LOCATION);
        permissions.add(Manifest.permission.ACCESS_FINE_LOCATION);
        permissions.add(Manifest.permission.CHANGE_WIFI_STATE);
        permissions.add(Manifest.permission.ACCESS_LOCATION_EXTRA_COMMANDS);
        permissions.add(Manifest.permission.BLUETOOTH);
        permissions.add(Manifest.permission.BLUETOOTH_ADMIN);
        permissions.add(Manifest.permission.WAKE_LOCK);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            permissions.add(Manifest.permission.FOREGROUND_SERVICE);
            permissions.add(Manifest.permission.ACCESS_BACKGROUND_LOCATION);
        }

        ActivityCompat.requestPermissions(this, permissions.toArray(new String[0]), PERMISSION_REQUEST);
    }
}
