package com.wuw_sample_engine.asr5;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;

public class AsrConfigParam implements IAsrConfigParam {

    // Stop listening to speech, if the speech goes on for longer than this
    // time. This is to avoid the situation where, in a noisy environment,
    // the engine would continue to listen indefinitely. This timeout must
    // be longer than your longest expected WUW + command!
    // Set to 0 is you wish to disable this timeout.
    public static final int SPEECH_TIMEOUT_MS = 4500;

    public static int WUW_CONFIDENCE_THRESHOLD = 5200;
    public static final int WUW_WORD_CONFIDENCE_THRESHOLD = 5000;

    private final String AUDIO_SCENARIO_NAME = "mic";
    private final String WUW_APPLICATION_NAME = "WUW";
    private final String RECOGNIZER_NAME = "rec";
    private final String ASR_NAME = "asr";
    private final String ASR_DATA_CONFIG_PATH = "/app/asr/config";
    private final String WUW_START_RULE = "wuw_anyspeech#_main_";

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

        try {
            FileInputStream fileInputStream = new FileInputStream(absPath + "/app/asr/asr_config.json");
            InputStreamReader inputStreamReader = new InputStreamReader(fileInputStream);
            BufferedReader bufferedReader = new BufferedReader(inputStreamReader);
            String line = null;
            StringBuilder stringBuilder = new StringBuilder();

            while(null != (line = bufferedReader.readLine())) {
                stringBuilder.append(line);
            }

            bufferedReader.close();
            inputStreamReader.close();
            fileInputStream.close();
            JSONObject jsonObject = new JSONObject(stringBuilder.toString());
            WUW_CONFIDENCE_THRESHOLD = jsonObject.getInt("wuw_confidence_threshold");
        } catch (JSONException | IOException e) {
            e.printStackTrace();
        }
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
}
