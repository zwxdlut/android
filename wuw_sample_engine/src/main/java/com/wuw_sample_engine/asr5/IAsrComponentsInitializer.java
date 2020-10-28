package com.wuw_sample_engine.asr5;

import com.wuw_sample_engine.custom_audio.audioIn;

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
