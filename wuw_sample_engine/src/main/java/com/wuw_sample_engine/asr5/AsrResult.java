package com.wuw_sample_engine.asr5;


import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;

import nuance.common.ResultCode;

public class AsrResult implements IAsrResult {
    final String START_RULE = "startRule";
    final String HYPOTHESES = "hypotheses";
    final String END_TIME = "endTime";
    final String ITEMS = "items";
    final String TYPE = "type";
    final String TAG = "tag";
    final String ANYSPEECH = "<...>";
    final String CONFIDENCE = "confidence";
    final String ORTHOGRAPHY = "orthography";

    private int confidence;
    private int endTime;
    private String startRule;
    private String result;

    private IAsrConfigParam asrConfigParam = null;
    private IAsrEventHandler asrEventHandler = null;

    public AsrResult(IAsrConfigParam asrConfigParam, IAsrEventHandler asrEventHandler) {
        this.asrConfigParam = asrConfigParam;
        this.asrEventHandler = asrEventHandler;
    }

    @Override
    public int getEndTime() {
        return endTime;
    }

    @Override
    public String getTopResult() {
        return result;
    }


    @Override
    public void parseResult(String result, ResultCode rc, String message) {

        try {
            JSONObject jsonObject = (JSONObject) new JSONTokener(result).nextValue();

            JSONArray hypotheses = jsonObject.getJSONArray(HYPOTHESES);
            if (hypotheses != null && hypotheses.length() > 0) {
                JSONObject hypothesis = hypotheses.getJSONObject(0);

                if (hypothesis != null) {
                    startRule = hypothesis.getString(START_RULE);
                    parseWUWEndTimeAndConfidence(hypotheses);
                }
            }

            if (startRule.equals(asrConfigParam.getWUwStartRule()) && confidence >= asrConfigParam.getWUWConfidenceThreshold()) {
                this.result = result;
                Log.i("Result:", result);
                Log.i("WuWConfidence:", String.valueOf(confidence));
                Log.i("WuWEndTime:", String.valueOf(endTime));
                asrEventHandler.addEvent(IAsrEventHandler.ASR_EVENT.WUW_RESULT);
            } else {
                asrEventHandler.addEvent(IAsrEventHandler.ASR_EVENT.NO_WUW_RESULT);
            }

        } catch (Exception e) {
            rc = ResultCode.FATAL;
            message = e.getMessage();
        }
    }

    @Override
    public void reset() {
        startRule = "";
        result = "";
        confidence = 0;
    }

    private void parseWUWEndTimeAndConfidence(JSONArray hypothese) throws JSONException {

        JSONObject hypoIdxFirst = hypothese.getJSONObject(0);
        JSONArray hypotheseItems = hypoIdxFirst.getJSONArray(ITEMS);
        parseItems(hypotheseItems, true);
    }

    private void parseItems(JSONArray items, boolean needSetConfindece) throws JSONException {
        int idx = 0;
        int size = 0;
        String type = null;
        String ortho = null;

        String tempItem = null;
        JSONObject currentItem = null;
        JSONArray internalItems = null;

        size = items.length();

        for (idx = size - 1; idx >= 0; idx--) {

            currentItem = items.getJSONObject(idx);

            //check if current has TYPE
            tempItem = currentItem.getString(TYPE);
            if (tempItem == null) {
                return;
            }
            type = tempItem;

            if (type.equals(TAG)) {
                //set the confidence of the item
                if (needSetConfindece) {
                    tempItem = currentItem.getString(CONFIDENCE);
                    if (tempItem == null) {
                        return;
                    }
                    confidence = Integer.parseInt(tempItem);
                    needSetConfindece = false;
                }

                //continue with items in it
                internalItems = currentItem.getJSONArray(ITEMS);
                if (internalItems == null) {
                    return;
                }
                parseItems(internalItems, needSetConfindece);

            } else //item is terminal
            {
                //skip any speech
                tempItem = currentItem.getString(ORTHOGRAPHY);
                if (tempItem == null) {
                    return;
                }
                ortho = tempItem;
                if (ortho.equals(ANYSPEECH)) {
                    continue;
                }

                //parse last item end time
                tempItem = currentItem.getString(END_TIME);
                if (tempItem == null) {
                    break;
                }
                endTime = Integer.parseInt(tempItem);
                break;
            }

        }
    }


}
