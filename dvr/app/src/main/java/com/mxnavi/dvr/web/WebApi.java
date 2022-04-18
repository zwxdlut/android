package com.mxnavi.dvr.web;

import io.reactivex.Observable;
import retrofit2.http.Body;
import retrofit2.http.POST;

public interface WebApi {

    public final static String BASE_URL = "http://data.loopon.cn/api/v1/";

    /**
     * 验证码
     * @param phone
     * @return
     */
    @POST("sms")
    Observable<BaseApiResult> getSMSCode(@Body SMSRequest smsRequest);

    /**
     * 上传轨迹.
     */
    @POST("driving_tracks")
    Observable<BaseApiResult> uploadRecord(@Body RecordBean bean);

    /**
     * 上传抓拍.
     */
    @POST("driving_photos")
    Observable<BaseApiResult> uploadCapture(@Body CaptureBean bean);
}
