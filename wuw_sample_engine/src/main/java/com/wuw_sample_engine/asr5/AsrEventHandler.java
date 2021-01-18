package com.wuw_sample_engine.asr5;

import java.util.LinkedList;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

public class AsrEventHandler implements IAsrEventHandler {
    private LinkedList<ASR_EVENT> eventsList_ = new LinkedList<>();
    private ReentrantLock lock = new ReentrantLock();
    private Condition condition = lock.newCondition();

    public AsrEventHandler() {
    }

    public void addEvent(ASR_EVENT event) {
        lock.lock();
        eventsList_.offer(event);
        condition.signalAll();
        lock.unlock();
    }

    public ASR_EVENT removeEvent() {
        ASR_EVENT event = null;

        lock.lock();

        if (eventsList_.isEmpty()) {
            try {
                condition.await();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        event =  eventsList_.poll();

        lock.unlock();

        return event;
    }

    public boolean removeEvent(ASR_EVENT event) {
        boolean ret = false;

        lock.lock();
        ret = eventsList_.remove(event);
        lock.unlock();

        return ret;
    }
}
