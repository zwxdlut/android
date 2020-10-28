package com.wuw_sample_engine.asr5;

import nuance.common.ResultCode;

public interface IAsrResult {

    int getEndTime();

    String getTopResult();

    void parseResult(String result, ResultCode rc, String message);

    void reset();

}
