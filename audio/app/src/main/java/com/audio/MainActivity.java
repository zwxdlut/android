package com.audio;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import android.Manifest;
import android.content.pm.PackageManager;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.AudioTrack;
import android.media.MediaRecorder;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.View;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class MainActivity extends AppCompatActivity implements View.OnClickListener {
    private static final String TAG = MainActivity.class.getSimpleName();
    private static final int PERMISSION_REQUEST = 1;
    private static final int SAMPLE_RATE = 16000;
    private static final int ENCODING_FORMAT = AudioFormat.ENCODING_PCM_16BIT;
    private static final int CHANNEL_IN = AudioFormat.CHANNEL_IN_MONO;
    private static final int CHANNEL_OUT = AudioFormat.CHANNEL_OUT_MONO;
    private AudioRecord recorder = null;
    private AudioTrack track = null;
    private int recordSize = 0;
    private int playSize = 0;
    private boolean isRecording = false;
    private boolean isPlaying = false;
    private RecordThread recordThread = null;
    private PlayThread playThread = null;
    private File pcmFile = null;

    private class RecordThread extends Thread {
        @Override
        public void run() {
            recorder.startRecording();

            try {
                FileOutputStream fos = new FileOutputStream(pcmFile);
                byte[] buf = new byte[recordSize];

                while (isRecording) {
                    int size = recorder.read(buf, 0, buf.length);

                    Log.i(TAG, "run(RecordThread): read " + size + " bytes from recorder");
                    fos.write(buf, 0, size);
                    fos.flush();
                }

                fos.flush();
                fos.close();
            } catch (IOException e) {
                isRecording = false;
                e.printStackTrace();
            }

            recorder.stop();
            recorder.release();
            recorder = null;
        }
    }

    private class PlayThread extends Thread {
        @Override
        public void run() {
            track.play();

            try {
                FileInputStream fis = new FileInputStream(pcmFile);
                byte[] buf = new byte[playSize];

                while (isPlaying) {
                    int size = fis.read(buf, 0, buf.length);

                    if (0 >= size) {
                        Log.i(TAG, "run(PlayThread): reach the end of the file");
                        isPlaying = false;
                        break;
                    }

                    size = track.write(buf, 0, size);
                    Log.i(TAG, "run(PlayThread): write " + size + " bytes to track");
                }

                fis.close();
            } catch (IOException e) {
                isPlaying = false;
                e.printStackTrace();
            }

            track.stop();
            track.release();
            track = null;
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        List<String> permissions = new ArrayList<>();

        permissions.add(Manifest.permission.RECORD_AUDIO);
        permissions.add(Manifest.permission.WRITE_EXTERNAL_STORAGE);
        permissions.add(Manifest.permission.READ_EXTERNAL_STORAGE);

        ActivityCompat.requestPermissions(this, permissions.toArray(new String[0]), PERMISSION_REQUEST);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopRecord();
        stopPlay();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        Log.i(TAG, "onRequestPermissionsResult: requestCode = " + requestCode + ", permissions = " + Arrays.asList(permissions)
                + ", grantResults = " + Arrays.stream(grantResults).boxed().collect(Collectors.toList()));

        if (requestCode == PERMISSION_REQUEST) {
            boolean isGranted = true;

            for (int result : grantResults) {
                if (PackageManager.PERMISSION_GRANTED != result) {
                    isGranted = false;
                    break;
                }
            }

            if (isGranted) {
                Log.i(TAG, "onRequestPermissionsResult: permission granted requestCode = " + requestCode);
                init();
            } else {
                Log.e(TAG, "onRequestPermissionsResult: permission denied requestCode = " + requestCode);
                finish();
            }
        }
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.btn_start_record:
                startRecord();
                break;
            case R.id.btn_stop_record:
                stopRecord();
                break;
            case R.id.btn_start_play:
                startPlay();
                break;
            case R.id.btn_stop_play:
                stopPlay();
                break;
            default:
                break;
        }
    }

    private void init() {
        // initialize the parameters of the recorder and track
        recordSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_IN, ENCODING_FORMAT);
        playSize = AudioTrack.getMinBufferSize(SAMPLE_RATE, CHANNEL_OUT, ENCODING_FORMAT);
        pcmFile = new File(getExternalFilesDir(null), "test.pcm");
        Log.i(TAG, "init: record buffer size is " + recordSize + ", track buffer size is " + playSize + ", pcm file is" + pcmFile.getPath());

        // initialize UI
        findViewById(R.id.btn_start_record).setOnClickListener(this);
        findViewById(R.id.btn_stop_record).setOnClickListener(this);
        findViewById(R.id.btn_start_play).setOnClickListener(this);
        findViewById(R.id.btn_stop_play).setOnClickListener(this);
    }

    private void startRecord() {
        Log.i(TAG, "startRecord: isRecording = " + isRecording + ", isPlaying = " + isPlaying);

        if (isRecording || isPlaying) {
            return;
        }

        if (pcmFile.exists()) {
            pcmFile.delete();
            try {
                pcmFile.createNewFile();
            } catch (IOException e) {
                e.printStackTrace();
                return;
            }
        }

        recorder = new AudioRecord(MediaRecorder.AudioSource.MIC, SAMPLE_RATE, CHANNEL_IN, ENCODING_FORMAT, recordSize);

        if (AudioRecord.STATE_INITIALIZED != recorder.getState()) {
            Log.e(TAG, "startRecord: create recorder failed!");
            recorder = null;
            return;
        }

        isRecording = true;
        recordThread = new RecordThread();
        recordThread.start();
    }

    private void stopRecord() {
        Log.i(TAG, "stopRecord: isRecording = " + isRecording + ", isPlaying = " + isPlaying);

        isRecording = false;

        if (null != recordThread) {
            try {
                recordThread.join();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

            recordThread = null;
        }
    }

    private void startPlay() {
        Log.i(TAG, "startPlay: isRecording = " + isRecording + ", isPlaying = " + isPlaying);

        if (isRecording || isPlaying) {
            return;
        }

        if (!pcmFile.exists()) {
            Log.w(TAG, "startPlay: pcm file is not exit");
            return;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            track = new AudioTrack.Builder()
                    .setAudioAttributes(new AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                            .build())
                    .setAudioFormat(new AudioFormat.Builder()
                            .setEncoding(ENCODING_FORMAT)
                            .setSampleRate(SAMPLE_RATE)
                            .setChannelMask(CHANNEL_OUT)
                            .build())
                    .setBufferSizeInBytes(playSize)
                    .setTransferMode(AudioTrack.MODE_STREAM)
                    .build();
        } else {
            track = new AudioTrack(AudioManager.STREAM_MUSIC, SAMPLE_RATE, CHANNEL_OUT,
                    ENCODING_FORMAT, playSize, AudioTrack.MODE_STREAM);
        }

        if (AudioTrack.STATE_INITIALIZED != track.getState()) {
            Log.e(TAG, "startPlay: create track failed!");
            track = null;
            return;
        }

        isPlaying = true;
        playThread = new PlayThread();
        playThread.start();
    }

    private void stopPlay() {
        Log.i(TAG, "stopPlay: isRecording = " + isRecording + ", isPlaying = " + isPlaying);

        isPlaying = false;

        if (null != playThread) {
            try {
                playThread.join();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

            playThread = null;
        }
    }
}