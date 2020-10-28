package com.wuw_sample_engine.asr5;

public class AsrConfigParam implements IAsrConfigParam {

    private final String AUDIO_SCENARIO_NAME = "mic";
    private final String WUW_APPLICATION_NAME = "WUW";
    private final String RECOGNIZER_NAME = "rec";
    private final String ASR_NAME = "asr";
    private final String ASR_DATA_CONFIG_PATH = "/app/asr/config";
    private final String WUW_START_RULE = "wuw_anyspeech#_main_";
    private final int WUW_CONFIDENCE_THRESHOLD = 5000;

    private String configDir;
    private String audioScenarioName;
    private String asrMgrName;
    private String applicationName;
    private String recognizerName;
    private int nbrConfiguredApplications;

    public AsrConfigParam(String absPath) {
        this.configDir = absPath + ASR_DATA_CONFIG_PATH;
        // asr configuration json files location
        audioScenarioName = AUDIO_SCENARIO_NAME;
        asrMgrName = ASR_NAME;
        applicationName = WUW_APPLICATION_NAME;
        recognizerName = RECOGNIZER_NAME;
        nbrConfiguredApplications = 1;
    }

    @Override
    public int getNbrConfiguredApplications() {
        return nbrConfiguredApplications;
    }

    @Override
    public String getRecognizerName() {
        return recognizerName;
    }

    @Override
    public String getWuwApplicationName() {
        return applicationName;
    }

    public String getAsrManagerName() {
        return asrMgrName;
    }

    @Override
    public String getAudioScenarioName() {
        return audioScenarioName;
    }

    @Override
    public String getConfigDir() {
        return configDir;
    }

    @Override
    public String getWUwStartRule() {
        return WUW_START_RULE;
    }

    @Override
    public int getWUWConfidenceThreshold() {
        return WUW_CONFIDENCE_THRESHOLD;
    }
}
