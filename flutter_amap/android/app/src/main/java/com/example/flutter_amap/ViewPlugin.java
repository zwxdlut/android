package com.example.flutter_amap;

import android.util.Log;

import io.flutter.embedding.engine.FlutterEngine;
import io.flutter.embedding.engine.plugins.shim.ShimPluginRegistry;
import io.flutter.plugin.common.PluginRegistry;

public class ViewPlugin {
    private static final String TAG = ViewPlugin.class.getSimpleName();

    public static void registerWith(FlutterEngine flutterEngine) {
        ShimPluginRegistry registry = new ShimPluginRegistry(flutterEngine);
        final String key = ViewPlugin.class.getCanonicalName();

        Log.e(TAG, "registerWith: key = " + key);

        if (registry.hasPlugin(key)) {
            Log.e(TAG, "registerWith: registry hasPlugin!");
            return;
        }

        PluginRegistry.Registrar registrar = registry.registrarFor(key);
        registrar.platformViewRegistry().registerViewFactory(
                "plugins.mapview", new MyMapViewFactory(registrar.messenger()));
        registrar.platformViewRegistry().registerViewFactory(
                "plugins.naviview", new NaviViewFactory(registrar.messenger()));
    }
}
