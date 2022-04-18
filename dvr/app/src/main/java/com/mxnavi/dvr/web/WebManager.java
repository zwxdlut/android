package com.mxnavi.dvr.web;

import android.util.Log;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.logging.HttpLoggingInterceptor;
import retrofit2.Retrofit;
import retrofit2.adapter.rxjava2.RxJava2CallAdapterFactory;
import retrofit2.converter.gson.GsonConverterFactory;


public class WebManager {
    private static final WebManager webManager = new WebManager();
    private static final String TAG = "DVR-" + WebManager.class.getName();
    private WebManager() {}

    private WebApi webService;

    public static WebManager getInstance() {
        return webManager;
    }

    public void init() {
        HttpLoggingInterceptor interceptor=new HttpLoggingInterceptor(new HttpLoggingInterceptor.Logger() {
            @Override
            public void log(String message) {
                try {
                    String text = URLDecoder.decode(message, "utf-8");
                    Log.e(TAG, text);
                } catch (UnsupportedEncodingException e) {
                    e.printStackTrace();
                    Log.e(TAG, message);
                }
            }
        });

        interceptor.setLevel(HttpLoggingInterceptor.Level.BODY);

        OkHttpClient okHttpClient=new OkHttpClient.Builder()
                .addNetworkInterceptor(interceptor)
                //.addInterceptor(interceptor)
                .readTimeout(8, TimeUnit.SECONDS)
                .connectTimeout(8,TimeUnit.SECONDS)
                .build();

        Retrofit retrofit = new Retrofit.Builder()
                .client(okHttpClient)
                .addConverterFactory(GsonConverterFactory.create())
                .addCallAdapterFactory(RxJava2CallAdapterFactory.create())
                .baseUrl(WebApi.BASE_URL)
                .build();
        webService = retrofit.create(WebApi.class);
    }

    public WebApi getWebService() {
        return webService;
    }
}
