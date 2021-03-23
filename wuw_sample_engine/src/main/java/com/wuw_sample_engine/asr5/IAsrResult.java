package com.wuw_sample_engine.asr5;

import org.json.JSONException;

public interface IAsrResult {

    AsrEvent parseResult(String result);

}
