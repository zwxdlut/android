package com.wuw_sample_engine.asr5;

public interface IAsrEventHandler {
    enum ASR_EVENT {
        UNQUALIFIED_RESULT,
        WUW_RESULT,
        NO_WUW_RESULT,
        COMMAND_RESULT,
        INITIAL_TIMEOUT,
        OTHER_EVENT,
    }

    void addEvent(ASR_EVENT event);

    ASR_EVENT removeEvent();

    boolean isEmpty();
}
