package com.wuw_sample_engine.asr5;

import com.wuw_sample_engine.custom_audio.audioIn;
import com.wuw_sample_engine.custom_audio.customInputAdapterFactory;

import nuance.asr5.IApplication;
import nuance.asr5.IAsrManager;
import nuance.asr5.IRecognizer;
import nuance.asr5.IRecognizerListener;
import nuance.audio.IAudioFromFile;
import nuance.audio.IAudioInput;
import nuance.audio.IAudioInputAdapterFactory;
import nuance.audio.IAudioManager;
import nuance.common.IConfiguration;
import nuance.common.ISystemManager;
import nuance.common.ResultCode;

public class AsrComponentsInitializer implements IAsrComponentsInitializer {

    private IConfiguration configuration = null;
    private ISystemManager systemMgr = null;
    private IAudioManager audioMgr = null;
    private IAsrManager asrMgr = null;
    private IAudioInputAdapterFactory adapterFactory = null;
    private IAsrConfigParam asrConfigParam = null;
    private IRecognizer recognizer = null;
    private IApplication wuwApplication = null;
    private IRecognizerListener recognizerListener = null;
    private audioIn.IAudioDataCallback audioDataCallback = null;

    public AsrComponentsInitializer(IAsrConfigParam asrConfigParam, IAsrResult asrResult, IAsrEventQueue asrEventHandler) {
        this.asrConfigParam = asrConfigParam;
        recognizerListener = new SampleRecognizerListener(asrResult, asrEventHandler);   // SETUP LISTENERS
    }

    @Override
    public void initializeAsrComponents(String errorMessage, ResultCode resultCode) {

        // LOAD CONFIGURATION
        try {
            configuration = IConfiguration.create(asrConfigParam.getConfigDir(), null, false);

        } catch (ResultCode rc) {
            resultCode = rc;
            errorMessage = "Could not create IConfiguration object for " + asrConfigParam.getConfigDir();
            return;
        }

        try {
            systemMgr = ISystemManager.create("MAIN", configuration);
        } catch (ResultCode rc) {
            resultCode = rc;
            errorMessage = "Could not create system manager";
            return;
        }

        // SETUP AUDIO
        try {
            audioMgr = IAudioManager.create("MAIN", systemMgr, configuration, null);
        } catch (ResultCode rc) {
            resultCode = rc;
            errorMessage = "Could not create audio manager";
            return;
        }

        /* trigger implicit creation feature for the file IO modules */
        try {
            IAudioFromFile.registerFactory(audioMgr);
        } catch (ResultCode rc) {
            resultCode = rc;
            errorMessage = "registerFactory for IAudioFromFile failed";
            return;
        }

        /* trigger implicit creation feature for custom input adapters */
        try {
            adapterFactory = new customInputAdapterFactory();
            ((customInputAdapterFactory)adapterFactory).setAudioDataCallback(audioDataCallback);
            audioMgr.registerAudioInputAdapterFactory(adapterFactory);
        } catch (ResultCode rc) {
            resultCode = rc;
            errorMessage = "AudioManager.registerAudioInputAdapterFactory failed";
            return;
        }

        /* trigger implicit creation feature for the audio IO modules */
        try {
            IAudioInput.registerFactory(audioMgr);
        } catch (ResultCode rc) {
            resultCode = rc;
            errorMessage = "registerFactory for IAudioInput failed";
            return;
        }

        // SETUP ASR MANAGER
        try {
            asrMgr = IAsrManager.create(asrConfigParam.getAsrManagerName(), configuration, null, audioMgr, null);
        } catch (ResultCode rc) {
            resultCode = rc;
            errorMessage = "AsrManager creation failed";
            return;
        }

        // SETUP RECOGNIZER
        try {
            recognizer = IRecognizer.create(asrConfigParam.getRecognizerName(), asrMgr, recognizerListener);
        } catch (ResultCode rc) {
            resultCode = rc;
            errorMessage = "Recognizer creation failed";
            return;
        }

        // INIT APPLICATIONS
        try {
            wuwApplication = IApplication.create(asrMgr, asrConfigParam.getWuwApplicationName(), null);

        } catch (ResultCode rc) {
            resultCode = rc;
            errorMessage = "Application creation failed";
            return;
        }

        /* only one audio scenario is supported, which simulates continuous speech */
        String audioScenaioName = asrConfigParam.getAudioScenarioName();
        try {
            audioMgr.activateScenario(audioScenaioName);

        } catch (ResultCode rc) {
            resultCode = rc;
            errorMessage = "Audio scenario activation failed";
            return;
        }

    }

    @Override
    public void deInitializeComponents(String errorMessage, ResultCode resultCode) {
        // CLEANUP
        try {
            String audioScenaioName = asrConfigParam.getAudioScenarioName();
            audioMgr.deActivateScenario(audioScenaioName);

        } catch (ResultCode rc) {
            resultCode = rc;
            errorMessage = "Application creation failed";
            return;
        }


        wuwApplication.destroy();
        recognizer.destroy();
        recognizerListener.destroy();
        asrMgr.destroy();
        audioMgr.destroy();
        systemMgr.destroy();
        configuration.destroy();
    }

    @Override
    public IAsrManager getAsrManager() {
        return asrMgr;
    }

    @Override
    public IRecognizer getRecognizer() {
        return recognizer;
    }

    @Override
    public IRecognizerListener getRecognizerListener() {
        return recognizerListener;
    }

    @Override
    public void setAudioDataCallback(audioIn.IAudioDataCallback callback) {
        audioDataCallback = callback;

        if (null != adapterFactory) {
            ((customInputAdapterFactory)adapterFactory).setAudioDataCallback(audioDataCallback);
        }
    }
}
