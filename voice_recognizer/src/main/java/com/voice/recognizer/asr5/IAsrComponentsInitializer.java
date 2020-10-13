package com.voice.recognizer.asr5;

import com.voice.recognizer.custom_audio.audioIn;

import nuance.asr5.IApplication;
import nuance.asr5.IAsrManager;
import nuance.asr5.IRecognizer;
import nuance.asr5.IRecognizerListener;
import nuance.common.ResultCode;

public interface IAsrComponentsInitializer {
    void initializeAsrComponents(String errorMessage, ResultCode resultCode);

    void deInitializeComponents(String errorMessage, ResultCode resultCode);

    IAsrManager getAsrManager();

    IRecognizer getRecognizer();

    IRecognizerListener getRecognizerListener();

    void setAudioDataCallback(audioIn.IAudioDataCallback callback);
}
