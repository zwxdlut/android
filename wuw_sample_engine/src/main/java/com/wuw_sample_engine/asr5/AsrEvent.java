package com.wuw_sample_engine.asr5;

import java.util.Objects;

import nuance.common.ResultCode;

public class AsrEvent {
    public enum ASR_EVENT {
        UNQUALIFIED_RESULT,
        WUW_RESULT,
        NO_WUW_RESULT,
        OTHER_EVENT,
        SPEECH_TIMEOUT_EVENT,
        COMMAND_RESULT,
        INITIAL_TIMEOUT
    }

    // Store result parameters if the event is a result event
    private int confidence = 0;
    private int endTime = -1;
    private String startRule = null;
    private String result = null;

    // publisher message, if one accompanies the event
    private String message = null;
    private ResultCode rc = ResultCode.OK;

    ASR_EVENT event = ASR_EVENT.UNQUALIFIED_RESULT;

    public AsrEvent() {
    }

    public AsrEvent(ASR_EVENT event) {
        this.event = event;
    }

    public void setStartRule(String startRule) {
        this.startRule = startRule;
    }

    public void setConfidence(int confidence) {
        this.confidence = confidence;
    }

    public void setEndTime(int endTime) {
        this.endTime = endTime;
    }

    public void setResult(String result) {
        this.result = result;
    }

    public int getConfidence() {
        return this.confidence;
    }

    public int getEndTime() {
        return this.endTime;
    }

    public String getStartRule() {
        return this.startRule;
    }

    public String getResult() {
        return this.result;
    }

    public void setEvent(ASR_EVENT event) {
        this.event = event;
    }

    public ASR_EVENT getEvent() {
        return this.event;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getMessage() {
        return this.message;
    }

    public void setResultCode(ResultCode rc) {
        this.rc = rc;
    }

    public ResultCode getResultCode() {
        return this.rc;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        AsrEvent asrEvent = (AsrEvent) o;
        return event == asrEvent.event;
    }

    @Override
    public int hashCode() {
        return Objects.hash(event);
    }
}
