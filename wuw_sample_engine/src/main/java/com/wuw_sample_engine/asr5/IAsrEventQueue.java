package com.wuw_sample_engine.asr5;

public interface IAsrEventQueue {

    void addEvent(AsrEvent event);

    AsrEvent removeEvent();

    boolean removeEvent(AsrEvent event);

    boolean isEmpty();

}
