package com.media.player;

import android.Manifest;
import android.content.pm.PackageManager;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.widget.RelativeLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.source.ProgressiveMediaSource;
import com.google.android.exoplayer2.ui.PlayerControlView;
import com.google.android.exoplayer2.ui.PlayerView;
import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.DefaultDataSourceFactory;
import com.google.android.exoplayer2.upstream.cache.CacheDataSourceFactory;
import com.google.android.exoplayer2.util.Util;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Timer;
import java.util.TimerTask;
import java.util.stream.Collectors;

public class MainActivity extends AppCompatActivity implements View.OnClickListener{
    private static final String TAG = "MainActivity" ;
    private static final int PERMISSION_REQUEST = 1;
    private String videoPath = "https://mx-cloud-storage-test-upyun.mxnavi.com/storage/emulated/0/travel/videos/202006/20200615134350701/20200615054218.mp4";
    private MediaPlayer mPlayer = null;
    private SimpleExoPlayer exoPlayer = null;
    private Timer timer = new Timer();
    private TimerTask task = new TimerTask() {
        @Override
        public void run() {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    if (mPlayer.isPlaying()) {
                        ((TextView)findViewById(R.id.tv_position)).setText(String.valueOf(mPlayer.getCurrentPosition()));
                    }
                }
            });
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        if (PackageManager.PERMISSION_GRANTED != ContextCompat.checkSelfPermission(MainActivity.this, Manifest.permission.INTERNET)
                || PackageManager.PERMISSION_GRANTED != ContextCompat.checkSelfPermission(MainActivity.this, Manifest.permission.READ_EXTERNAL_STORAGE)) {
            ActivityCompat.requestPermissions(MainActivity.this,
                    new String[]{Manifest.permission.INTERNET, Manifest.permission.READ_EXTERNAL_STORAGE},
                    PERMISSION_REQUEST);
        }

        initMediaPlayer();
        initExoPlayer();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        Log.d(TAG, "onRequestPermissionsResult: requestCode = " + requestCode + ", permissions = " + Arrays.asList(permissions)
                + ", grantResults = " + Arrays.stream(grantResults).boxed().collect(Collectors.toList()));
        switch (requestCode) {
            case PERMISSION_REQUEST:
                if (PackageManager.PERMISSION_GRANTED == grantResults[0]
                        && PackageManager.PERMISSION_GRANTED == grantResults[1]) {
                    Log.d(TAG, "onRequestPermissionsResult: permission granted requestCode = " + requestCode);
                    initMediaPlayer();
                    initExoPlayer();
                } else {
                    Log.w(TAG, "onRequestPermissionsResult: permission denied requestCode = " + requestCode);
                }
                break;
            default:
                super.onRequestPermissionsResult(requestCode, permissions, grantResults);
                break;
        }
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.btn_play:
                try {
                    mPlayer.reset();
                    mPlayer.setDataSource(this, Uri.parse("android.resource://" + getPackageName() + File.separator + R.raw.test));
                    //mPlayer.setDataSource(videoPath);
                    mPlayer.prepareAsync();
                } catch (IOException e) {
                    e.printStackTrace();
                }
                break;
            case R.id.btn_seek:
                mPlayer.seekTo(7000, MediaPlayer.SEEK_CLOSEST);
                break;
            default:
                break;
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        if (null != mPlayer) {
            mPlayer.stop();
            mPlayer.reset();
            mPlayer.release();
            mPlayer = null;
        }

        if (null != exoPlayer) {
            exoPlayer.stop(true);
            exoPlayer.release();
            exoPlayer = null;
        }
    }

    private void initMediaPlayer() {
        SurfaceView playerView = findViewById(R.id.sf_player_view);
        RelativeLayout.LayoutParams params = new RelativeLayout.LayoutParams(640, 480);

        mPlayer = new MediaPlayer();
        mPlayer.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
            @Override
            public void onPrepared(MediaPlayer mp) {
                mp.start();
            }
        });

        mPlayer.setOnErrorListener(new MediaPlayer.OnErrorListener() {
            @Override
            public boolean onError(MediaPlayer mp, int what, int extra) {
                Log.d(TAG, "onError: what = " + what + ", extra = " + extra);
                return false;
            }
        });

        mPlayer.setOnBufferingUpdateListener(new MediaPlayer.OnBufferingUpdateListener() {
            @Override
            public void onBufferingUpdate(MediaPlayer mp, int percent) {
                Log.d(TAG, "onBufferingUpdate: percent = " + percent);
            }
        });

        mPlayer.setOnSeekCompleteListener(new MediaPlayer.OnSeekCompleteListener() {
            @Override
            public void onSeekComplete(MediaPlayer mp) {
                mp.start();
            }
        });

        params.leftMargin = 0;
        params.topMargin = 0;
        playerView.setLayoutParams(params);
        playerView.getHolder().addCallback(new SurfaceHolder.Callback() {
            @Override
            public void surfaceCreated(SurfaceHolder holder) {
                mPlayer.setDisplay(holder);
            }

            @Override
            public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
            }

            @Override
            public void surfaceDestroyed(SurfaceHolder holder) {
            }
        });

        findViewById(R.id.btn_play).setOnClickListener(this);
        findViewById(R.id.btn_seek).setOnClickListener(this);
        timer.schedule(task, 0, 500);
    }

    private void initExoPlayer() {
        MxPlayerView playerView = findViewById(R.id.mx_player_view);
        DataSource.Factory dataSourceFactory = new CacheDataSourceFactory(MxPlayerViewUtil.getVideoCache(this), new DefaultDataSourceFactory(this, Util.getUserAgent(this, "ExoPlayer")));
        //MediaSource videoSource = new ProgressiveMediaSource.Factory(dataSourceFactory).createMediaSource(RawResourceDataSource.buildRawResourceUri(R.raw.test));
        MediaSource videoSource = new ProgressiveMediaSource.Factory(dataSourceFactory).createMediaSource(Uri.parse(videoPath));

        exoPlayer = new SimpleExoPlayer.Builder(this).build();
        playerView.setPlayer(exoPlayer);
        exoPlayer.prepare(videoSource);
        exoPlayer.setPlayWhenReady(true);
    }
}
