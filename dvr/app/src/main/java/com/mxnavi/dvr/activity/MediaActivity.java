package com.mxnavi.dvr.activity;

import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.databinding.DataBindingUtil;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentPagerAdapter;
import androidx.lifecycle.Observer;

import com.google.android.material.tabs.TabLayout;
import com.mxnavi.dvr.R;
import com.mxnavi.dvr.databinding.ActivityMediaBinding;
import com.mxnavi.dvr.fragment.ImageFragment;
import com.mxnavi.dvr.fragment.VideoFragment;
import com.storage.util.Constant;

import java.util.ArrayList;
import java.util.List;

public class MediaActivity extends AppCompatActivity implements View.OnClickListener, TabLayout.OnTabSelectedListener {
    public static final int TYPE_PLAYBACK = 0;
    public static final int TYPE_UPLOAD = 1;
    public static final int TYPE_DELETE = 2;
    private static final String TAG = "DVR-" + MediaActivity.class.getSimpleName();
    private int curPosition = 0;
    private String[] tabTitles = new String[2];
    private ActivityMediaBinding binding = null;
    private VideoFragment videoFragment = null;
    private ImageFragment imageFragment = null;
    private List<Fragment> tabFragments = new ArrayList<>();

    private class TabFragmentPagerAdapter extends FragmentPagerAdapter {
        public TabFragmentPagerAdapter(@NonNull FragmentManager fm, int behavior) {
            super(fm, behavior);
        }

        @NonNull
        @Override
        public Fragment getItem(int position) {
            return tabFragments.get(position);
        }

        @Override
        public int getCount() {
            return tabFragments.size();
        }

        @Override
        public CharSequence getPageTitle(int position) {
            return tabTitles[position];
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.i(TAG, "onCreate");

        int type = getIntent().getIntExtra("type", TYPE_PLAYBACK);

        tabTitles[0] = getString(R.string.media_tab_title_video);
        tabTitles[1] = getString(R.string.media_tab_title_image);
        videoFragment = VideoFragment.newInstance(type);
        imageFragment = ImageFragment.newInstance(type);
        tabFragments.add(videoFragment);
        tabFragments.add(imageFragment);

        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        binding = DataBindingUtil.setContentView(this, R.layout.activity_media);
        binding.setType(type);
        binding.setAll(false);
        binding.setEnable(true);
        binding.btnBack.setOnClickListener(this);
        binding.btnAll.setOnClickListener(this);
        binding.btnOpt.setOnClickListener(this);
        binding.tabLayout.addTab(binding.tabLayout.newTab().setText(tabTitles[0]));
        binding.tabLayout.addTab(binding.tabLayout.newTab().setText(tabTitles[1]));
        binding.viewPager.setAdapter(new TabFragmentPagerAdapter(getSupportFragmentManager(), FragmentPagerAdapter.BEHAVIOR_RESUME_ONLY_CURRENT_FRAGMENT));
        binding.tabLayout.setupWithViewPager(binding.viewPager);
        binding.tabLayout.addOnTabSelectedListener(this);

        videoFragment.getOperationCompleted().observe(this, new Observer<Boolean>() {
            @Override
            public void onChanged(Boolean aBoolean) {
                binding.setAll(false);
                binding.setEnable(true);
            }
        });

        imageFragment.getOperationCompleted().observe(this, new Observer<Boolean>() {
            @Override
            public void onChanged(Boolean aBoolean) {
                binding.setAll(false);
                binding.setEnable(true);
            }
        });
    }

    @Override
    protected void onDestroy() {
        Log.i(TAG, "onDestroy");
        super.onDestroy();
    }

    @Override
    public void onClick(View v) {
        if (binding.btnBack.equals(v)){
            finish();
        } else if (binding.btnOpt.equals(v)) {
            if (TYPE_PLAYBACK == binding.getType()) {
                if (binding.ivArrowOrder.isActivated()) {
                    binding.ivArrowOrder.setActivated(false);
                    videoFragment.sort(Constant.OrderType.DESCENDING);
                    imageFragment.sort(Constant.OrderType.DESCENDING);
                } else {
                    binding.ivArrowOrder.setActivated(true);
                    videoFragment.sort(Constant.OrderType.ASCENDING);
                    imageFragment.sort(Constant.OrderType.ASCENDING);
                }
            } else if (TYPE_UPLOAD == binding.getType()) {
                int ret = 0;

                if (0 == curPosition) {
                    ret = videoFragment.upload();
                } else if (1 == curPosition) {
                    ret = imageFragment.upload();
                }

                if (0 < ret) {
                    binding.setEnable(false);
                }
            } else if (TYPE_DELETE == binding.getType()) {
                int ret = 0;

                if (0 == curPosition) {
                    ret =videoFragment.delete();
                } else if (1 == curPosition) {
                    ret = imageFragment.delete();
                }

                if (0 < ret) {
                    binding.setEnable(false);
                }
            }
        } else if (binding.btnAll.equals(v)) {
            binding.setAll(!binding.getAll());

            if (0 == curPosition) {
                videoFragment.selectAll(binding.getAll());
            } else if (1 == curPosition) {
                imageFragment.selectAll(binding.getAll());
            }
        }
    }

    @Override
    public void onTabSelected(TabLayout.Tab tab) {
        binding.setAll(false);
        curPosition = tab.getPosition();
        Log.i(TAG, "onTabSelected: position = " + curPosition);
    }

    @Override
    public void onTabUnselected(TabLayout.Tab tab) {}

    @Override
    public void onTabReselected(TabLayout.Tab tab) {}
}