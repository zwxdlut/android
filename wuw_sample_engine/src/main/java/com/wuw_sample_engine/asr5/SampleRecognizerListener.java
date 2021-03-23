package com.wuw_sample_engine.asr5;

import android.util.Log;

import java.util.Timer;
import java.util.TimerTask;

import nuance.asr5.IRecognizerListener;
import nuance.asr5.RecogEvent;
import nuance.asr5.RecognizerError;
import nuance.asr5.ResultType;

import static com.wuw_sample_engine.asr5.AsrConfigParam.SPEECH_TIMEOUT_MS;

/* The SampleRecognizerListener illustrates how you can handle speech
 * events sent by the recognizer.
 *
 * The recognizer will call the onEvent, onResult, onWarning and onError methods
 * in this listener when events occur. The event handlers in the listener can then
 * take appropriate action for each event type.
 *
 * This listener contains a speech timeout timer. This is for safety. Normally, the
 * engine will return results relatively frequently, and reset itself. But if you
 * use a context with longer utterances or a loop in it, the recognizer will accumulate
 * partial result data for longer, and use more memory as it processes more speech.
 * The speech timeout timer helps to place an upper bound on this, by signaling the
 * main application that a timeout event occurred. The application can then choose
 * reset the recognizer.
 */
public class SampleRecognizerListener extends IRecognizerListener {

    private boolean speechDetected = false;

    private IAsrResult asrResult = null;
    private IAsrEventQueue asrEventQueue = null;

    private Timer speechTimeoutTimer = null;

    public SampleRecognizerListener(IAsrResult asrResult, IAsrEventQueue asrEventHandler) {
        this.asrResult = asrResult;
        this.asrEventQueue = asrEventHandler;
    }

    private String constructMessage(String type, Object recogEvent, String message) {
        StringBuilder eventMessage = new StringBuilder();
        eventMessage.append(type);
        eventMessage.append(recogEvent.toString());
        if (!message.isEmpty()) {
            eventMessage.append("\nEvent message: ");
            eventMessage.append(message);
        }
        eventMessage.append("\n");
        return eventMessage.toString();
    }

    @Override
    public void onEvent(RecogEvent recogEvent, int timeMarker, String message) {

        if (recogEvent == RecogEvent.RECOGNIZER_SILENCE_DETECTED && speechDetected) {
            speechDetected = false;
            stopSpeechTimeoutTimer();
            AsrEvent event = new AsrEvent(AsrEvent.ASR_EVENT.OTHER_EVENT);
            event.setMessage(constructMessage("EVENT: ", recogEvent, message));
            asrEventQueue.addEvent(event);
        }

        if (recogEvent == RecogEvent.RECOGNIZER_SPEECH_DETECTED && !speechDetected) {
            speechDetected = true;
            startSpeechTimeoutTimer(timeMarker, SPEECH_TIMEOUT_MS);
            AsrEvent event = new AsrEvent(AsrEvent.ASR_EVENT.OTHER_EVENT);
            event.setMessage(constructMessage("EVENT: ", recogEvent, message));
            asrEventQueue.addEvent(event);
        }

        if (recogEvent != RecogEvent.RECOGNIZER_SILENCE_DETECTED && recogEvent != RecogEvent.RECOGNIZER_SPEECH_DETECTED) {
            AsrEvent event = new AsrEvent(AsrEvent.ASR_EVENT.OTHER_EVENT);
            event.setMessage(constructMessage("EVENT: ", recogEvent, message));
            asrEventQueue.addEvent(event);
        }
    }

    @Override
    public void onResult(String result, ResultType resultType, boolean isFinal) {
        stopSpeechTimeoutTimer();
        AsrEvent event = null;
        if (resultType == ResultType.RESULT_TYPE_ASR) {
            event = asrResult.parseResult(result);
        } else {
            event = new AsrEvent(AsrEvent.ASR_EVENT.OTHER_EVENT);
            event.setMessage("Unexpected result type returned.");
        }
        asrEventQueue.addEvent(event);
    }

    @Override
    public void onError(RecognizerError recognizerError, String message) {
        AsrEvent event = new AsrEvent(AsrEvent.ASR_EVENT.OTHER_EVENT);
        event.setMessage(constructMessage("ERROR: ", recognizerError, message));
        asrEventQueue.addEvent(event);
    }

    @Override
    public void onWarning(RecognizerError recognizerError, String message) {
        AsrEvent event = new AsrEvent(AsrEvent.ASR_EVENT.OTHER_EVENT);
        event.setMessage(constructMessage("WARNING: ", recognizerError, message));
        asrEventQueue.addEvent(event);
    }

    private void startSpeechTimeoutTimer(final int timeMarker, final int timeoutMs)
    {
        if (timeoutMs > 0) {
            Log.i("SpeechTimeoutTimer", "Start timeout timer (" + timeoutMs + "ms).");
            TimerTask task = new TimerTask() {
                @Override
                public void run() {
                    Log.i("SpeechTimeoutTimer", "Timeout elapsed; sending timeout event.");
                    AsrEvent timeoutEvent = new AsrEvent(AsrEvent.ASR_EVENT.SPEECH_TIMEOUT_EVENT);
                    timeoutEvent.setMessage(constructMessage("SPEECH TIMEOUT", "", "@ time: " + timeMarker));
                    timeoutEvent.setEndTime(timeMarker + timeoutMs);
                    asrEventQueue.addEvent(timeoutEvent);
                }
            };

            this.speechTimeoutTimer = new Timer();
            this.speechTimeoutTimer.schedule(task, timeoutMs);
        } else {
            this.speechTimeoutTimer = null;
        }
    }

    private void stopSpeechTimeoutTimer()
    {
        if (speechTimeoutTimer != null) {
            Log.i("SpeechTimeoutTimer", "Stop timeout timer.");
            speechTimeoutTimer.cancel();
        }
    }


}
