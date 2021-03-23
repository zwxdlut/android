package com.wuw_sample_engine.asr5;


import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;

import java.util.ArrayList;

import nuance.common.ResultCode;

/* ASR result parser
 * The parseResult method in this class takes a result JSON string as input,
 * and will place an event on ASR event queue based on it.
 * Current strategy:
 * - if the result is empty (no hypotheses), we call it an UNQUALIFIED_RESULT
 * - if the result has hypotheses, but they do not originate from the WUW start rule,
 *   or if the confidence value is below the threshold (see AsrConfigParam.java), we
 *   call it a NO_WUW_RESULT
 * - if the result has hypotheses, and the first one has the right start rule and
 *   high enough overall confidence and word confidences, we call it a WUW_RESULT.
 *
 * The event is placed on the ASR event queue, so the main application loop can
 * process it and take action as needed.
 */
public class AsrResultParser implements IAsrResult {

    public static final String START_RULE = "startRule";
    public static final String HYPOTHESES = "hypotheses";
    public static final String BEGIN_TIME = "beginTime";
    public static final String END_TIME = "endTime";
    public static final String ITEMS = "items";
    public static final String TYPE = "type";
    public static final String TAG = "tag";
    public static final String CONFIDENCE = "confidence";
    public static final String ORTHOGRAPHY = "orthography";

    private IAsrConfigParam asrConfigParam = null;
    private IAsrEventQueue asrEventHandler = null;

    public AsrResultParser(IAsrConfigParam asrConfigParam, IAsrEventQueue asrEventHandler) {
        this.asrConfigParam = asrConfigParam;
        this.asrEventHandler = asrEventHandler;
    }

    private class Result {
        public int beginTime = 0;
        public int endTime = 0;
        public ArrayList<Hypothesis> hypotheses = new ArrayList<>();
    }

    private class Hypothesis {
        public String startRule;
        public int beginTime = 0;
        public int endTime = 0;
        public int confidence = 0;
        public ArrayList<Item> items = new ArrayList<>();
    }

    private enum ItemType { WORD, TAG }

    private class Item {
        public ItemType type;
        public String orthography;
        public int confidence;
        public int endTime;
        // Items can be nested: tags are represented by items,
        // with child items representing the nodes inside the tag
        public ArrayList<Item> items = new ArrayList<>();
    }

    // The DataHolder acts as temporary storage while we are traversing the
    // result. We use this to keep track of the confidence value and end time
    // value that we want to use after the traversal.
    class DataHolder {
        int confidence = 0;
        int endTime = 0;
    };

    @Override
    public AsrEvent parseResult(String result) {
        Result res = null;
        DataHolder dataHolder = new DataHolder();
        AsrEvent event = new AsrEvent(AsrEvent.ASR_EVENT.UNQUALIFIED_RESULT);

        try {
            res = reconstructResultFromJSON(result);
        } catch (JSONException e) {
            event.setResultCode(ResultCode.FATAL);
            event.setMessage(e.getMessage());

            return event;
        }

        // the event we return must have an end time, because that will be
        // used as the time from which to re-activate the ASR applications on
        // the recognizer. If the result has hypotheses, we can update the
        // end time to be more accurate. If not, we use the result's overall
        // end time, because that tells us the time up to which audio has been
        // processed.
        event.setEndTime(res.endTime);

        // Check if we have actual hypotheses, and if we do, check if they
        // are good or not.
        if (res.hypotheses.size() > 0) {
            Hypothesis hyp = res.hypotheses.get(0);
            if (asrConfigParam.getWUwStartRule().equals(hyp.startRule) &&
                    tagConfidenceOK(hyp, dataHolder) &&
                    tagWordConfidencesOK(hyp, dataHolder)) {
                event.setConfidence(dataHolder.confidence);
                event.setEndTime(dataHolder.endTime);
                event.setResult(result);
                Log.i("ParseResult", "WUW confidence: " + event.getConfidence() + "\n");
                Log.i("ParseResult", "WUW end time:   " + event.getEndTime() + "\n");
                event.setEvent(AsrEvent.ASR_EVENT.WUW_RESULT);
            } else {
                event.setEvent(AsrEvent.ASR_EVENT.NO_WUW_RESULT);
            }
        }

        return event;
    }

    private boolean tagConfidenceOK(Hypothesis hyp, DataHolder dataHolder) {
        Item tag = null;

        for (Item item: hyp.items) {
            if (ItemType.TAG.equals(item.type)) {
                tag = item;
            }
        }

        if (tag != null) {
            int tagConfidence = tag.confidence;
            if (tagConfidence >= AsrConfigParam.WUW_CONFIDENCE_THRESHOLD) {
                dataHolder.confidence = tag.confidence;
                return true;
            } else {
                return false;
            }
        } else {
            return false;
        }
    }

    // Check individual word confidences.
    // If any word in the hypothesis is below the WUW_WORD_CONFIDENCE_THRESHOLD
    // the hypothesis will be rejected, even if is globally above the WUW_CONFIDENCE_THRESHOLD.
    private boolean tagWordConfidencesOK(Hypothesis hyp, DataHolder dataHolder) {
        boolean result = true;
        Item tag = null;

        for (Item item: hyp.items) {
            if (ItemType.TAG.equals(item.type)) {
                tag = item;
            }
        }

        if (tag != null) {
            result = recursivelyCheckItemConfidences(tag, dataHolder);
        }

        return result;
    }

    private boolean recursivelyCheckItemConfidences(Item item, DataHolder dataHolder) {
        boolean result = true;
        if (item.type == ItemType.TAG) {
            for (Item child: item.items) {
                result = result &&
                        recursivelyCheckItemConfidences(child, dataHolder);
            }
        } else {
            result = (item.confidence >= AsrConfigParam.WUW_WORD_CONFIDENCE_THRESHOLD);
            dataHolder.endTime = item.endTime;
            if (!result) {
                Log.i("RCIC", "Item result below word confidence: " + item.orthography + " -> " + item.confidence);
            }
        }
        return result;
    }

    private Item parseItem(JSONObject item) throws JSONException {
        Item itm = new Item();
        itm.confidence = item.getInt(CONFIDENCE);

        if ("tag".equals(item.getString(TYPE))) {
            itm.type = ItemType.TAG;
            JSONArray items = item.getJSONArray(ITEMS);
            for (int item_idx=0; item_idx<items.length(); item_idx++) {
                JSONObject child = items.getJSONObject(item_idx);
                itm.items.add(parseItem(child));
            }
        } else {
            itm.type = ItemType.WORD;
            itm.orthography = item.getString(ORTHOGRAPHY);
            itm.endTime = item.getInt(END_TIME);
        }
        return itm;
    }

    private Result reconstructResultFromJSON(String result) throws JSONException {
        Result res = new Result();

        JSONObject jsonObject = (JSONObject) new JSONTokener(result).nextValue();
        res.beginTime = jsonObject.getInt(BEGIN_TIME);
        res.endTime = jsonObject.getInt(END_TIME);

        JSONArray hypotheses = jsonObject.getJSONArray(HYPOTHESES);
        for (int hyp_idx=0; hyp_idx<hypotheses.length(); hyp_idx++) {
            JSONObject hypothesis = hypotheses.getJSONObject(hyp_idx);
            Hypothesis hyp = new Hypothesis();
            hyp.beginTime = hypothesis.getInt(BEGIN_TIME);
            hyp.endTime = hypothesis.getInt(END_TIME);
            hyp.confidence = hypothesis.getInt(CONFIDENCE);
            hyp.startRule = hypothesis.getString(START_RULE);

            JSONArray items = hypothesis.getJSONArray(ITEMS);
            for (int item_idx=0; item_idx<items.length(); item_idx++) {
                JSONObject item = items.getJSONObject(item_idx);
                hyp.items.add(parseItem(item));
            }

            res.hypotheses.add(hyp);
        }

        return res;
    }

}

