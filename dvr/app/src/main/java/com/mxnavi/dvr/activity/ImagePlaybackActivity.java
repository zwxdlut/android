package com.mxnavi.dvr.activity;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;

import androidx.appcompat.app.AppCompatActivity;
import androidx.databinding.DataBindingUtil;

import com.bumptech.glide.Glide;
import com.mxnavi.dvr.R;
import com.mxnavi.dvr.databinding.ActivityImagePlaybackBinding;
import com.storage.MediaBean;

import java.util.ArrayList;
import java.util.List;

public class ImagePlaybackActivity extends AppCompatActivity implements View.OnClickListener {
    private static final String TAG = "DVR-" + ImagePlaybackActivity.class.getSimpleName();
    private int index = 0;
    private List<MediaBean> beans = new ArrayList<>();
    private ActivityImagePlaybackBinding binding = null;

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

        index = intent.getIntExtra("index", 0);
        if (0 > index || beans.size() <= index) {
            Log.e(TAG, "onCreate: index out of range!");
            finish();
        }

        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        binding = DataBindingUtil.setContentView(this, R.layout.activity_image_playback);
        binding.btnBack.setOnClickListener(this);
        binding.btnPrev.setOnClickListener(this);
        binding.btnNext.setOnClickListener(this);
        invalidate(index);
    }

    @Override
    protected void onDestroy() {
        Log.i(TAG, "onDestroy");
        super.onDestroy();
    }

    @Override
    public void onClick(View v) {
        if (binding.btnBack.equals(v)) {
            finish();
        } else if (binding.btnPrev.equals(v)) {
            if (0 > index - 1) {
                return;
            }

            invalidate(--index);
        } else if (binding.btnNext.equals(v)) {
            if (beans.size() <= index + 1) {
                return;
            }

            invalidate(++index);
        }
    }

    private void invalidate(int index) {
        binding.tvTitle.setText(beans.get(index).getName());
        Glide.with(this).load(beans.get(index).getPath()).into(binding.ivPreview);
    }
}