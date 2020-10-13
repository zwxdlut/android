package com.voice.recognizer.asr5;

import org.json.JSONException;

import nuance.common.ResultCode;

public interface IAsrResult {

    int getEndTime();

    String getTopResult();

    void parseResult(String result, ResultCode rc, String message);

    void reset();

}
