package com.wuw_sample_engine.asr5;

import java.util.LinkedList;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

/* This version of the Asr
 */
public class ConcurrentAsrEventQueue implements IAsrEventQueue {
    private LinkedList<AsrEvent> eventsList_ = null;
    private ReentrantLock lock = null;
    private Condition condition = null;

    public ConcurrentAsrEventQueue() {
       eventsList_ = new LinkedList<>();
       lock = new ReentrantLock();
       condition = lock.newCondition();
    }

    public void addEvent(AsrEvent event) {
        lock.lock();
        eventsList_.offer(event);
        condition.signalAll();
        lock.unlock();
    }

    public AsrEvent removeEvent() {
        AsrEvent event = null;

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

    public boolean removeEvent(AsrEvent event) {
        boolean ret = false;

        lock.lock();
        ret = eventsList_.remove(event);
        lock.unlock();

        return ret;
    }

    @Override
    public boolean isEmpty() {
        return eventsList_.isEmpty();
    }
}
