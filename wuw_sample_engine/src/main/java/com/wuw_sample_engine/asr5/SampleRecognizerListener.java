package com.wuw_sample_engine.asr5;

import nuance.asr5.IRecognizerListener;
import nuance.asr5.RecogEvent;
import nuance.asr5.RecognizerError;
import nuance.asr5.ResultType;
import nuance.common.ResultCode;

public class SampleRecognizerListener extends IRecognizerListener {

    private boolean silenceDetected_ = false;
    private boolean speechDetected_ = false;

    private IAsrResult asrResult = null;
    private IAsrEventHandler asrEventHandler = null;

    private String publisherMessage_ = "";
    private ResultCode rc = ResultCode.OK;

    public SampleRecognizerListener(IAsrResult asrResult, IAsrEventHandler asrEventHandler) {
        this.asrResult = asrResult;
        this.asrEventHandler = asrEventHandler;
    }

    @Override
    public void onEvent(RecogEvent recogEvent, int timeMarker, String message) {

        if (recogEvent == RecogEvent.RECOGNIZER_SILENCE_DETECTED && !silenceDetected_) {
            publisherMessage_ = "EVENT: " + recogEvent.toString() + "\nEvent message: " + (message.isEmpty() ? "null" : message) + "\n\n";
            silenceDetected_ = true;
            speechDetected_ = false;
            asrEventHandler.addEvent(IAsrEventHandler.ASR_EVENT.OTHER_EVENT);
        }

        if (recogEvent == RecogEvent.RECOGNIZER_SPEECH_DETECTED && !speechDetected_) {
            publisherMessage_ = "EVENT: " + recogEvent.toString() + "\nEvent message: " + (message.isEmpty() ? "null" : message) + "\n\n";
            silenceDetected_ = false;
            speechDetected_ = true;
            asrEventHandler.addEvent(IAsrEventHandler.ASR_EVENT.OTHER_EVENT);
        }

        if (recogEvent != RecogEvent.RECOGNIZER_SILENCE_DETECTED && recogEvent != RecogEvent.RECOGNIZER_SPEECH_DETECTED) {
            publisherMessage_ = "EVENT: " + recogEvent.toString() + "\nEvent message: " + (message.isEmpty() ? "null" : message) + "\n\n";
            asrEventHandler.addEvent(IAsrEventHandler.ASR_EVENT.OTHER_EVENT);
        }
    }

    @Override
    public void onResult(String result, ResultType resultType, boolean isFinal) {
        if (resultType == ResultType.RESULT_TYPE_ASR) {
            asrResult.parseResult(result, rc, publisherMessage_);
        } else {
            publisherMessage_ = "Unexpected result type returned.";
            asrEventHandler.addEvent(IAsrEventHandler.ASR_EVENT.OTHER_EVENT);
        }

    }

    @Override
    public void onError(RecognizerError recognizerError, String message) {
        publisherMessage_ = "ERROR: " + recognizerError.toString() +
                "\n     Error message: " + (message.isEmpty() ? "null" : message) + '\n';
        asrEventHandler.addEvent(IAsrEventHandler.ASR_EVENT.OTHER_EVENT);
    }

    @Override
    public void onWarning(RecognizerError recognizerError, String message) {
        publisherMessage_ = "WARNING: " + recognizerError.toString() +
                "\n     Warning message: " + (message.isEmpty() ? "null" : message) + '\n';
        asrEventHandler.addEvent(IAsrEventHandler.ASR_EVENT.OTHER_EVENT);
    }

    public String getPublisherMessage() {
        return publisherMessage_;
    }


    public ResultCode getResultCode() {
        return rc;
    }

    public void reset() {
        rc = ResultCode.OK;
        publisherMessage_ = "";
    }
}
