package com.example.jetpack_demo;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.databinding.DataBindingUtil;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import android.annotation.SuppressLint;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;

import com.example.jetpack_demo.databinding.ActivityMainBinding;

import java.io.IOException;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.adapter.rxjava2.RxJava2CallAdapterFactory;
import retrofit2.converter.gson.GsonConverterFactory;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "MainActivity";
    private static final int MSG_LOCATION_UPDATE = 1;
    private ActivityMainBinding binding = null;
    @SuppressLint("HandlerLeak")
    private Handler handler = new Handler() {
        @SuppressLint("SetTextI18n")
        @Override
        public void handleMessage(@NonNull Message msg) {
            switch (msg.what) {
                case MSG_LOCATION_UPDATE:
                    /* View binding */
                    binding.textView.setText("View binding: location = " + msg.arg1);
                    /* Data binding*/
                    binding.setLocation(msg.arg1);
                    break;
                default:
                    super.handleMessage(msg);
                    break;
            }
        }
    };

    public static class MyViewModel extends ViewModel {
        private MutableLiveData<String> users = null;

        public LiveData<String> getUsers() {
            if (users == null) {
                users = new MutableLiveData<>();
            }
            new Timer().schedule(new TimerTask() {
                @Override
                public void run() {
                    loadUsers();
                }
            }, 0, 1000);
            return users;
        }

        private void loadUsers() {
            users.postValue(String.valueOf((int)(1 + Math.random() * (1000 -1 + 1))));
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        //setContentView(R.layout.activity_main);
        //binding = ActivityMainBinding.inflate(getLayoutInflater());
        //setContentView(binding.getRoot());
        binding = DataBindingUtil.setContentView(this, R.layout.activity_main);

        /* Lifecycle */
        new MyLocationListener(getLifecycle(), location -> {
            /* Update UI */
            handler.sendMessage(Message.obtain(null, MSG_LOCATION_UPDATE, location, 0));
        });

        /* View model */
        MyViewModel model = new ViewModelProvider(this).get(MainActivity.MyViewModel.class);
        model.getUsers().observe(this, users -> {
            /* Update UI */
            binding.setUsers(users);
        });

        /* Retrofit */
        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl("https://api.github.com/")
                .addConverterFactory(GsonConverterFactory.create())
                //.callbackExecutor(Executors.newSingleThreadExecutor())
                .addCallAdapterFactory(RxJava2CallAdapterFactory.create())
                .build();
        GitHub github = retrofit.create(GitHub.class);
        Call<List<Repo>> call = github.listRepos("zwxdlut");

        call.enqueue(new Callback<List<Repo>>() {
            @Override
            public void onResponse(Call<List<Repo>> call, Response<List<Repo>> response) {
                if (response.isSuccessful()) {
                    Log.d(TAG, "onResponse: " + response.toString());
                    for (Repo repo : response.body()) {
                        Log.d(TAG, "onResponse: repo name = " + repo.getName());
                    }
                }
                else {
                    Log.e(TAG, "onResponse: failed!");

                }
            }

            @Override
            public void onFailure(Call<List<Repo>> call, Throwable t) {
                Log.e(TAG, "onFailure: " + t.getMessage());
            }
        });
    }
}
