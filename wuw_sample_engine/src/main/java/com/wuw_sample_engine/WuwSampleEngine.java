package com.wuw_sample_engine;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.media.AudioManager;
import android.text.Html;
import android.util.Log;

import androidx.core.content.ContextCompat;

import com.wuw_sample_engine.asr5.AsrAssetExtractor;
import com.wuw_sample_engine.asr5.AsrComponentsInitializer;
import com.wuw_sample_engine.asr5.AsrConfigParam;
import com.wuw_sample_engine.asr5.AsrEventHandler;
import com.wuw_sample_engine.asr5.AsrResult;
import com.wuw_sample_engine.asr5.IAsrAssetExtractor;
import com.wuw_sample_engine.asr5.IAsrComponentsInitializer;
import com.wuw_sample_engine.asr5.IAsrConfigParam;
import com.wuw_sample_engine.asr5.IAsrEventHandler;
import com.wuw_sample_engine.asr5.IAsrResult;
import com.wuw_sample_engine.asr5.SampleRecognizerListener;
import com.wuw_sample_engine.custom_audio.audioIn;
import com.wuw_sample_engine.vad.VadApi;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Timer;
import java.util.TimerTask;

import nuance.common.ILogging;
import nuance.common.ResultCode;

public class WuwSampleEngine {
    private static final String TAG = "WuwSampleEngine";
    private static final String VAD_RES_FILE_NAME = "vad_tdnn_0627.bin";
    private static WuwSampleEngine instance = null;
    private Context context;

    private IAsrConfigParam asrConfigParam = null;
    private IAsrAssetExtractor assetExtractor = null;
    private IAsrComponentsInitializer asrComponentsInitializer = null;
    private IAsrResult asrResult = null;
    private IAsrEventHandler asrEventHandler = null;

    private boolean done = true;
    private int vadStatus = 0;
    private int g_timeoutMs = 15000;
    private ASR_STATE asrState = ASR_STATE.IDLE;
    private OneShotWuwSampleThread thread = null;
    private VadApi vad = VadApi.getInstance();
    private Timer timer = null;
    private FileOutputStream fos = null;
    private IVoiceCallback voiceCallback = null;

    static {
        System.loadLibrary("vad_api_jni");
    }

    private audioIn.IAudioDataCallback audioDataCallback = new audioIn.IAudioDataCallback() {
        @Override
        public void onCapture(byte buf[], int size) {
            if (ASR_STATE.AWAKE == asrState) {
                vad.feed(buf, size);
                writePcm(buf, size);
            }

            if (null != voiceCallback) {
                voiceCallback.onCapture(buf, size);
            }
        }
    };

    private VadApi.ResultCallback vadResultCallback = new VadApi.ResultCallback() {
        @Override
        public int onResult(int status) {
            if (0 == vadStatus) {
                // rising edge
                if (1 == status) {
                    stopTimer();
                }
            } else {
                // falling edge
                if (0 == status) {
                    asrEventHandler.addEvent(IAsrEventHandler.ASR_EVENT.COMMAND_RESULT);

                }
            }

            vadStatus = status;

            if (null != voiceCallback) {
                voiceCallback.onResult(status);
            }

            return 0;
        }
    };

    public enum ASR_STATE {
        IDLE,
        ASLEEP,
        AWAKE
    }

    public class OneShotWuwSampleThread extends Thread {
        @Override
        public void run() {
            preExecute();
            execute();
            postExecute();
        }
    }

    public interface IVoiceCallback {
        void onState(ASR_STATE state);

        void onCapture(byte buf[], int size);

        void onResult(int status);
    }

    public static WuwSampleEngine getInstance(Context context) {
        if (null == instance) {
            synchronized (WuwSampleEngine.class) {
                if (null == instance) {
                    instance = new WuwSampleEngine(context);
                }
            }
        }

        return instance;
    }

    private WuwSampleEngine(Context context) {
        this.context = context.getApplicationContext();
        asrConfigParam = new AsrConfigParam(context.getExternalFilesDir(null).getAbsolutePath());
        assetExtractor = new AsrAssetExtractor();
    }

    public void setVoiceCallback(IVoiceCallback callback) {
        voiceCallback = callback;
    }

    public void extractAssetsFiles() throws IOException {
        assetExtractor.extractAssetsFiles(context);
    }

    public void start() {
        Log.i(TAG, "start: done = " + done);

        if (PackageManager.PERMISSION_GRANTED != ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)) {
            Log.i(TAG, "start: no record audio permission!");
            return;
        }

        if (PackageManager.PERMISSION_GRANTED != ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
            Log.i(TAG, "start: no write external storage permission!");
            return;
        }

        if (!done) {
            Log.i(TAG, "start: asr has already been started");
            return;
        }

        done = false;
        thread = new OneShotWuwSampleThread();
        thread.start();
    }

    public void stop() {
        publishProgress("stop: done = " + done);

        done = true;

        if (null != thread) {
            try {
                thread.join();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

            thread = null;
        }
    }

    public void wakeup() {
        Log.i(TAG, "wakeup: asrState = " + asrState);

        if (ASR_STATE.IDLE == asrState) {
            return;
        }

        asrEventHandler.addEvent(IAsrEventHandler.ASR_EVENT.WUW_RESULT);
    }

    public void sleep() {
        Log.i(TAG, "sleep: asrState = " + asrState);

        if (ASR_STATE.IDLE == asrState) {
            return;
        }

        asrEventHandler.addEvent(IAsrEventHandler.ASR_EVENT.COMMAND_RESULT);
    }

    private void preExecute() {
        Log.i(TAG, "preExecute");

        AudioManager am = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);

        try {
            am.startBluetoothSco();
        } catch (NullPointerException e) {
            // workaround for Lollipop when bluetooth device isn't connected
            Log.i(TAG, "preExecute: startBluetoothSco failed! No bluetooth device is connected!");
        }
    }

    private void execute() {
        Log.i(TAG, "execute");

        String errorMessage = "";
        ResultCode resultCode = ResultCode.OK;

        // initialize vad
        vadStatus = 0;
        vad.create(context.getExternalFilesDir(null).getAbsolutePath() + "/app/asr/data/" + VAD_RES_FILE_NAME);
        vad.setting("{\"AdditionalPauseTime\" : 1000}");

        initAsr();

        asrComponentsInitializer.setAudioDataCallback(audioDataCallback);
        asrComponentsInitializer.initializeAsrComponents(errorMessage, resultCode);
        errorCheck(resultCode, errorMessage);

        addApplications();
        startRecognizer();
        asrState = ASR_STATE.ASLEEP;

        if (null != voiceCallback) {
            voiceCallback.onState(asrState);
        }

        SampleRecognizerListener recognizerListener = (SampleRecognizerListener) asrComponentsInitializer.getRecognizerListener();

        while (true) {
            IAsrEventHandler.ASR_EVENT event = IAsrEventHandler.ASR_EVENT.UNQUALIFIED_RESULT;

            if (!asrEventHandler.isEmpty()) {
                event = asrEventHandler.removeEvent();
            }

            if (IAsrEventHandler.ASR_EVENT.WUW_RESULT == event) {
                errorCheck(recognizerListener.getResultCode(), recognizerListener.getPublisherMessage());
                publishProgress("RESULT: " + asrResult.getTopResult() + "\n");
                publishProgress("EndTime: " + asrResult.getEndTime() + "\n");
                publishProgress("wakeup word found!\n");

                if (ASR_STATE.AWAKE == asrState) {
                    publishProgress("wuw sample engine has been awake!\n");
                    stopTimer();
                    asrState = ASR_STATE.ASLEEP;
                    vad.stop();
                    vadStatus = 0;
                    closePcm();

                    // avoid falling edge
                    if (!asrEventHandler.isEmpty()) {
                        publishProgress("remove event!\n");
                        asrEventHandler.removeEvent();
                    }

                    //continue;
                }

                addApplications();
                openPcm();
                vad.start(vadResultCallback);
                asrState = ASR_STATE.AWAKE;
                startTimer(g_timeoutMs);

                if (null != voiceCallback) {
                    voiceCallback.onState(asrState);
                }
            } else if (event == IAsrEventHandler.ASR_EVENT.NO_WUW_RESULT) {
                errorCheck(recognizerListener.getResultCode(), recognizerListener.getPublisherMessage());
                addApplications();
            } else if (IAsrEventHandler.ASR_EVENT.COMMAND_RESULT == event || IAsrEventHandler.ASR_EVENT.INITIAL_TIMEOUT == event) {
                if (ASR_STATE.ASLEEP == asrState) {
                    continue;
                }

                publishProgress("go to asleep!\n");
                addApplications();
                stopTimer();
                asrState = ASR_STATE.ASLEEP;
                vad.stop();
                vadStatus = 0;
                closePcm();

                if (null != voiceCallback) {
                    voiceCallback.onState(asrState);
                }
            } else if (IAsrEventHandler.ASR_EVENT.OTHER_EVENT == event) {
                publishProgress(recognizerListener.getPublisherMessage());
            }

            if (done) {
                break;
            }
        }

        publishProgress("go to idle!\n");
        stopTimer();
        asrState = ASR_STATE.IDLE;
        vad.stop();
        closePcm();

        if (null != voiceCallback) {
            voiceCallback.onState(asrState);
        }

        stopRecognizer();
        ILogging.getInstance().flush();
        asrComponentsInitializer.deInitializeComponents(errorMessage, resultCode);
        asrComponentsInitializer.setAudioDataCallback(null);
        errorCheck(resultCode, errorMessage);
        ILogging.deleteInstance();
        vad.delete();
    }

    private void postExecute() {
        Log.i(TAG, "postExecute");

        AudioManager am = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        am.stopBluetoothSco();
    }

    private void publishProgress(String... progress) {
        StringBuilder sb = new StringBuilder();

        if (progress[0].startsWith("<b>")) {
            sb.append(Html.fromHtml(progress[0]));
            sb.append("\n");
        } else {
            sb.append(progress[0]);
        }

        Log.i(TAG, sb.toString());
    }

    private void addApplications() {
        int startTimeMs = asrResult.getEndTime();

        asrResult.reset();
        ((SampleRecognizerListener) asrComponentsInitializer.getRecognizerListener()).reset();

        String applications[] = new String[asrConfigParam.getNbrConfiguredApplications()];
        StringBuilder addedApplications = new StringBuilder();

        applications[0] = asrConfigParam.getWuwApplicationName();
        addedApplications.append(asrConfigParam.getWuwApplicationName());

        try {
            asrComponentsInitializer.getAsrManager().setApplications(applications, asrConfigParam.getRecognizerName(), startTimeMs);
        } catch (ResultCode rc) {
            errorCheck(rc, "ERROR: Failed to add applications");
        }
    }

    private void errorCheck(ResultCode rc, String msg) {
        if (rc != ResultCode.OK) {
            String resultCode = rc == ResultCode.ERROR ? "ERROR" : "FATAL";
            publishProgress(resultCode + ": " + msg + "\n");
            publishProgress("Quit application");
            ILogging.getInstance().flush();
            done = true;
        }
    }

    private void printAsrConfiguratio() {
        publishProgress(":: Running with parameters:\n");
        publishProgress("configDir: " + asrConfigParam.getConfigDir() + "\n\n");
        publishProgress("audioScenario: " + asrConfigParam.getAudioScenarioName() + "\n");
        publishProgress("asr manager: " + asrConfigParam.getAsrManagerName() + "\n");
        publishProgress("application: " + asrConfigParam.getWuwApplicationName() + "\n");
        publishProgress("recognizer: " + asrConfigParam.getRecognizerName() + "\n");
        publishProgress("wuwStartRule: " + asrConfigParam.getWUwStartRule() + "\n");
        publishProgress("confidenceThreshold: " + asrConfigParam.getWUWConfidenceThreshold() + "\n");
    }

    private void startRecognizer() {
        // START RECOGNITION
        try {
            asrComponentsInitializer.getRecognizer().start();
        } catch (ResultCode rc) {
            errorCheck(rc, "Failed to start recognizer!");
        }
    }

    private void stopRecognizer() {
        // STOP ASR
        try {
            asrComponentsInitializer.getRecognizer().stop();
        } catch (ResultCode rc) {
            errorCheck(rc, "Failed to stop recognizer!");
        }
    }

    private void initAsr() {
        asrEventHandler = new AsrEventHandler();
        asrResult = new AsrResult(this.asrConfigParam, asrEventHandler);
        asrComponentsInitializer = new AsrComponentsInitializer(this.asrConfigParam, asrResult, asrEventHandler);
        printAsrConfiguratio();
    }

    private void startTimer(int timeoutMs) {
        TimerTask task = new TimerTask() {
            @Override
            public void run() {
                publishProgress("command should be within " + g_timeoutMs + " ms after wake-up word for one-shot wuw case!\n");
                publishProgress("<b>initial timeout, wuw sample engine go to sleep!</b>");
                asrEventHandler.addEvent(IAsrEventHandler.ASR_EVENT.INITIAL_TIMEOUT);
            }
        };

        timer = new Timer();
        timer.schedule(task, timeoutMs);
    }

    private void stopTimer() {
        if (null != timer) {
            timer.cancel();
            timer = null;
        }

    }

    private void openPcm() {
        File file = new File(context.getExternalFilesDir(null), "command.pcm");

        if (file.exists()) {
            file.delete();
            try {
                file.createNewFile();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        try {
            fos = new FileOutputStream(file);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
    }

    private void writePcm(byte buf[], int size) {
        if (null != fos) {
            try {
                fos.write(buf, 0, size);
                fos.flush();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private void closePcm() {
        if (null != fos) {
            try {
                fos.flush();
                fos.close();
                fos = null;
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}