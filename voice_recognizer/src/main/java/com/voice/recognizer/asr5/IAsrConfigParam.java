package com.voice.recognizer.asr5;

public interface IAsrConfigParam {
    int getNbrConfiguredApplications();

    String getRecognizerName();

    String getWuwApplicationName();

    String getAsrManagerName();

    String getAudioScenarioName();

    String getConfigDir();

    String getWUwStartRule();

    int getWUWConfidenceThreshold();
}
