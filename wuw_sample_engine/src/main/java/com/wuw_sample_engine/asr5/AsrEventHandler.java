package com.wuw_sample_engine.asr5;

import java.util.LinkedList;

public class AsrEventHandler implements IAsrEventHandler {

    private LinkedList<ASR_EVENT> eventsList_ = null;

    public AsrEventHandler() {
        eventsList_ = new LinkedList<>();
    }

    public synchronized void addEvent(ASR_EVENT event) {
        eventsList_.add(event);
    }

    public synchronized ASR_EVENT removeEvent() {
        return eventsList_.remove();
    }

    @Override
    public boolean isEmpty() {
        return eventsList_.isEmpty();
    }
}
