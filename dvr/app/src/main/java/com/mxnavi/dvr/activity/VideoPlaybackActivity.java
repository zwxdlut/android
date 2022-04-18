package com.mxnavi.dvr.activity;

import android.content.Context;
import android.content.Intent;
import android.icu.util.LocaleData;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.util.Pair;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;

import androidx.appcompat.app.AppCompatActivity;
import androidx.databinding.DataBindingUtil;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.ExoPlaybackException;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.Timeline;
import com.google.android.exoplayer2.analytics.AnalyticsListener;
import com.google.android.exoplayer2.analytics.PlaybackStatsListener;
import com.google.android.exoplayer2.database.ExoDatabaseProvider;
import com.google.android.exoplayer2.source.ConcatenatingMediaSource;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.source.ProgressiveMediaSource;
import com.google.android.exoplayer2.ui.PlayerView;
import com.google.android.exoplayer2.upstream.DefaultDataSourceFactory;
import com.google.android.exoplayer2.upstream.cache.CacheDataSource;
import com.google.android.exoplayer2.upstream.cache.LeastRecentlyUsedCacheEvictor;
import com.google.android.exoplayer2.upstream.cache.SimpleCache;
import com.google.android.exoplayer2.util.ErrorMessageProvider;
import com.google.android.exoplayer2.util.Util;
import com.google.android.exoplayer2.video.VideoListener;
import com.mxnavi.dvr.R;
import com.mxnavi.dvr.databinding.ActivityVideoPlaybackBinding;
import com.storage.MediaBean;

import java.io.File;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class VideoPlaybackActivity extends AppCompatActivity implements View.OnClickListener {
    private static final String TAG = "DVR-" + VideoPlaybackActivity.class.getSimpleName();
    private static SimpleCache cache = null;
    private List<MediaBean> beans = new ArrayList<>();
    private SimpleExoPlayer player = null;
    private SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd  HH:mm:ss", Locale.getDefault());
    private SimpleDateFormat nameFormat = new SimpleDateFormat("yyyyMMddHHmmss", Locale.getDefault());
    private ActivityVideoPlaybackBinding binding = null;

    public static SimpleCache getVideoCache(Context context) {
        if (cache == null) {
            cache = new SimpleCache(
                    new File(context.getExternalCacheDir(), "exoplayer"),
                    new LeastRecentlyUsedCacheEvictor(512 * 1024 * 1024),
                    new ExoDatabaseProvider(context));
        }

        return cache;
    }

    private Player.EventListener eventListener = new Player.EventListener() {
        @Override
        public void onPlayerError(ExoPlaybackException error) {
            Log.e(TAG, "onPlayerError: " + error.toString());
        }

        @Override
        public void onPositionDiscontinuity(int reason) {
            int index = player.getCurrentWindowIndex();
            Log.i(TAG, "onPositionDiscontinuity: reason = " + reason + ", index = " + index);
            //binding.tvTitle.setText(dateFormat.format(new Date(beans.get(index).getTime())));

            // workaround
            try {
                binding.tvTitle.setText(dateFormat.format(nameFormat.parse(beans.get(index).getName())));
            } catch (ParseException e) {
                e.printStackTrace();
            }
        }
    };

    private  ErrorMessageProvider<? super ExoPlaybackException> errorMessageProvider = new ErrorMessageProvider<ExoPlaybackException>() {
        @Override
        public Pair<Integer, String> getErrorMessage(ExoPlaybackException throwable) {
            Log.e(TAG, "getErrorMessage:" + throwable.toString());
            return Pair.create(0, "视频播放出了问题");
            //return null;
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.i(TAG, "onCreate");

        Intent intent = getIntent();
        beans = intent.getParcelableArrayListExtra("beans");
        if (null == beans || beans.isEmpty()) {
            Log.e(TAG, "onCreate: no beans!");
            finish();
        }

        int index = intent.getIntExtra("index", 0);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        binding = DataBindingUtil.setContentView(this, R.layout.activity_video_playback);
        binding.btnBack.setOnClickListener(this);
        //binding.tvTitle.setText(dateFormat.format(new Date(beans.get(index).getTime())));

        // workaround
        try {
            binding.tvTitle.setText(dateFormat.format(nameFormat.parse(beans.get(index).getName())));
        } catch (ParseException e) {
            e.printStackTrace();
        }

        if (null == cache) {
            cache = new SimpleCache(
                    new File(getExternalCacheDir(), "exoplayer"),
                    new LeastRecentlyUsedCacheEvictor(512 * 1024 * 1024),
                    new ExoDatabaseProvider(this));
        }

        List<MediaSource> mediaSources = new ArrayList<>();
        PlayerView playerView = findViewById(R.id.player_view);
        CacheDataSource.Factory dataSourceFactory = new CacheDataSource.Factory();
        dataSourceFactory.setCache(cache);
        dataSourceFactory.setUpstreamDataSourceFactory(
                new DefaultDataSourceFactory(this, Util.getUserAgent(this, "VideoPlayer")));

        for (MediaBean bean : beans) {
            Uri uri = bean.getUri();
            if (null == uri) {
                uri = Uri.parse(bean.getPath());
            }

            mediaSources.add(new ProgressiveMediaSource.Factory(dataSourceFactory)
                    .createMediaSource(new MediaItem.Builder().setUri(uri).build()));
        }

        player = new SimpleExoPlayer.Builder(this).build();
        playerView.setPlayer(player);
        playerView.setErrorMessageProvider(errorMessageProvider);
        player.setMediaSources(mediaSources);
        player.addListener(eventListener);
        player.seekTo(index, C.TIME_UNSET);
        player.prepare();
        player.setPlayWhenReady(true);
    }

    @Override
    protected void onDestroy() {
        Log.i(TAG, "onDestroy");

        if (null != player) {
            player.stop(true);
            player.release();
        }

        super.onDestroy();
    }

    @Override
    public void onClick(View v) {
        if (binding.btnBack.equals(v)) {
            finish();
        }
    }
}