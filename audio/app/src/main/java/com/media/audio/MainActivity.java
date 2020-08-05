package com.media.audio;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.content.pm.PackageManager;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.AudioTrack;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.util.Log;
import android.view.View;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.stream.Collectors;

public class MainActivity extends AppCompatActivity implements View.OnClickListener {
    private static final String TAG = "MainActivity";
    private static final int PERMISSION_REQUEST = 1;
    private static final int SAMPLE_RATE = 8000;
    private AudioRecord recorder = null;
    private AudioTrack track = null;
    private int iBufSize = 0;
    private int oBufSize = 0;
    private boolean isRecording = false;
    private boolean isPlaying = false;
    private Thread recordThread = null;
    private Thread playThread = null;
    private File pcmFile = null;

    private class RecordThread extends Thread {
        @Override
        public void run() {
            recorder.startRecording();

            try {
                FileOutputStream fos = new FileOutputStream(pcmFile);
                byte[] buf = new byte[iBufSize];

                while (isRecording) {
                    int count = recorder.read(buf, 0, buf.length);

                    Log.i(TAG, "run(record_thread): read " + count + " bytes from recorder");
                    fos.write(buf, 0, count);
                    fos.flush();
                }

                fos.flush();
                fos.close();
            } catch (IOException e) {
                isRecording = false;
                e.printStackTrace();
            }

            recorder.stop();
        }
    }

    private class PlayThread extends Thread {
        @Override
        public void run() {
            track.play();

            try {
                FileInputStream fis = new FileInputStream(pcmFile);
                byte[] buf = new byte[oBufSize];

                while(isPlaying) {
                    int count = fis.read(buf, 0, buf.length);

                    if (0 >= count) {
                        Log.i(TAG, "run(play_thread): reach the end of the file");
                        isPlaying = false;
                        break;
                    }

                    count = track.write(buf, 0, count);
                    Log.i(TAG, "run(play_thread): write " + count + " bytes to track");
                }

                fis.close();
            } catch (IOException e) {
                isPlaying = false;
                e.printStackTrace();
            }

            track.stop();
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        ActivityCompat.requestPermissions(this,
                new String[]{Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.RECORD_AUDIO},
                PERMISSION_REQUEST);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        stopRecord();
        stopPlay();

        if (null != recorder) {
            recorder.stop();
            recorder.release();
            recorder = null;
        }

        if (null != track) {
            track.stop();
            track.release();
            track = null;
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        Log.i(TAG, "onRequestPermissionsResult: requestCode = " + requestCode + ", permissions = " + Arrays.asList(permissions)
                + ", grantResults = " + Arrays.stream(grantResults).boxed().collect(Collectors.toList()));

        if (requestCode == PERMISSION_REQUEST) {
            if (PackageManager.PERMISSION_GRANTED == grantResults[0]
                    && PackageManager.PERMISSION_GRANTED == grantResults[1]
                    && PackageManager.PERMISSION_GRANTED == grantResults[2]) {
                Log.i(TAG, "onRequestPermissionsResult: permission granted requestCode = " + requestCode);
                init();
            } else {
                Log.w(TAG, "onRequestPermissionsResult: permission denied requestCode = " + requestCode);
                finish();
            }
        } else {
            super.onRequestPermissionsResult(requestCode, permissions, grantResults);
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
        // Initialize the recorder and the track
        iBufSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_IN_STEREO, AudioFormat.ENCODING_PCM_16BIT);
        recorder = new AudioRecord(MediaRecorder.AudioSource.MIC, SAMPLE_RATE, AudioFormat.CHANNEL_IN_STEREO, AudioFormat.ENCODING_PCM_16BIT, iBufSize);
        oBufSize = AudioTrack.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_OUT_STEREO, AudioFormat.ENCODING_PCM_16BIT);
        track = new AudioTrack(AudioManager.STREAM_MUSIC, SAMPLE_RATE, AudioFormat.CHANNEL_OUT_STEREO, AudioFormat.ENCODING_PCM_16BIT, oBufSize, AudioTrack.MODE_STREAM);
        pcmFile = new File(getExternalFilesDir(null), "test.pcm");
        Log.i(TAG, "init: record buffer size is " + iBufSize + ", track buffer size is " + oBufSize + ", pcm file is" + pcmFile.getPath());

        // Initialize UI
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