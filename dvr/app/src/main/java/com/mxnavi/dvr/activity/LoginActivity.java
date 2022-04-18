package com.mxnavi.dvr.activity;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.camera.CameraController;
import com.mxnavi.dvr.R;
import com.mxnavi.dvr.utils.PhoneNumberManager;
import com.mxnavi.dvr.utils.RandomGenerator;
import com.mxnavi.dvr.web.BaseApiResult;
import com.mxnavi.dvr.web.SMSRequest;
import com.mxnavi.dvr.web.WebManager;
import com.storage.util.ToastUtil;
import com.tbruyelle.rxpermissions2.RxPermissions;

import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.annotations.NonNull;
import io.reactivex.functions.Consumer;
import io.reactivex.observers.DefaultObserver;
import io.reactivex.schedulers.Schedulers;


public class LoginActivity extends AppCompatActivity {

    private final static String TAG = "DVR-" + LoginActivity.class.getName();

    private EditText phoneText;
    private EditText smsText;
    private Button smsButton;
    private String smsCode;
    private String phone;
    private boolean isSentSMS = false;
    private Timer timer = new Timer();
    private int secondLeft = 60;

    @SuppressLint("CheckResult")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);
        requestPermission();
    }

    private void jumpToMainActivity() {
        Intent intent = new Intent(LoginActivity.this, MainActivity.class);
        startActivity(intent);
    }

    @SuppressLint("CheckResult")
    private void requestPermission() {
        List<String> permissions = new ArrayList<>();

        permissions.add(Manifest.permission.CAMERA);
        permissions.add(Manifest.permission.RECORD_AUDIO);
        permissions.add(Manifest.permission.READ_EXTERNAL_STORAGE);
        permissions.add(Manifest.permission.WRITE_EXTERNAL_STORAGE);
        permissions.add(Manifest.permission.ACCESS_COARSE_LOCATION);
        permissions.add(Manifest.permission.ACCESS_FINE_LOCATION);
        permissions.add(Manifest.permission.ACCESS_NETWORK_STATE);
        permissions.add(Manifest.permission.ACCESS_WIFI_STATE);
        permissions.add(Manifest.permission.CHANGE_WIFI_STATE);
        permissions.add(Manifest.permission.INTERNET);
        permissions.add(Manifest.permission.READ_PHONE_STATE);
        permissions.add(Manifest.permission.ACCESS_LOCATION_EXTRA_COMMANDS);
        permissions.add(Manifest.permission.BLUETOOTH);
        permissions.add(Manifest.permission.BLUETOOTH_ADMIN);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            permissions.add(Manifest.permission.FOREGROUND_SERVICE);
            permissions.add(Manifest.permission.ACCESS_MEDIA_LOCATION);
        }

        RxPermissions rxPermissions = new RxPermissions(this);
        rxPermissions.request( permissions.toArray(new String[0]))
                .subscribe(new Consumer<Boolean>() {
                    @Override
                    public void accept(Boolean aBoolean) {
                        if (aBoolean) {
                            if (0 < CameraController.getInstance().getCameraIdList().length) {
                                init();
                                return;
                            } else {
                                Log.e(TAG, "accept: no camera!");
                                ToastUtil.show(LoginActivity.this, "无摄像头！");
                            }
                        }

                        finish();
                    }
                });
    }

    private void init() {
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        if (PhoneNumberManager.getInstance().getPhoneNumber().length() != 0) {
            jumpToMainActivity();
            finish();
            return;
        }
        smsButton = (Button) findViewById(R.id.smsButton);
        Button loginButton = (Button) findViewById(R.id.loginButton);
        phoneText = (EditText)findViewById(R.id.phoneEditView);
        smsText = (EditText)findViewById(R.id.smsEditView);

        loginButton.setOnClickListener(view -> {
            if (!isSentSMS) {
                Toast.makeText(this, getString(R.string.sms_first), Toast.LENGTH_LONG).show();
            }
            String inputSMSCode = smsText.getText().toString().trim();
            if (inputSMSCode.equals(smsCode)) {
                PhoneNumberManager.getInstance().setPhoneNumber(phone);
                jumpToMainActivity();
                finish();
            } else {
                Toast.makeText(this, getString(R.string.sms_error), Toast.LENGTH_LONG).show();
            }
        });

        smsButton.setOnClickListener(view -> {
            Log.i(TAG, "send sms.");
            RandomGenerator randomGenerator = new RandomGenerator();
            smsCode = randomGenerator.getSMSCode();
            phone = phoneText.getText().toString();
            if (phone.length() < 11) {
                Log.e(TAG, "length is too short.");
                Toast.makeText(this, getString(R.string.phone_error), Toast.LENGTH_LONG).show();
                return;
            }
            Log.i(TAG, smsCode);
            SMSRequest smsRequest = new SMSRequest(phone, smsCode);
            WebManager.getInstance().getWebService()
                    .getSMSCode(smsRequest)
                    .subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(new DefaultObserver<BaseApiResult>() {
                        @Override
                        public void onNext(@NonNull BaseApiResult baseApiResult) {
                            Log.i(TAG, baseApiResult.toString());
                            if (baseApiResult.getOk() == 1) {
                                isSentSMS = true;
                                Toast.makeText(LoginActivity.this, getString(R.string.sms_success), Toast.LENGTH_LONG).show();
                                timer.schedule(task, 0, 1000);
                                smsButton.setEnabled(false);
                                smsText.setText(smsCode);
                            }
                        }

                        @Override
                        public void onError(@NonNull Throwable e) {
                            Log.e(TAG, e.getMessage());
                        }

                        @Override
                        public void onComplete() {
                            Log.i(TAG, "on complete.");
                        }
                    });
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        timer.cancel();
    }

    TimerTask task = new TimerTask() {

        @Override
        public void run() {
            // TODO Auto-generated method stub
            runOnUiThread(new Runnable() {

                @Override
                public void run() {
                    // TODO Auto-generated method stub
                    secondLeft--;
                    smsButton.setText("" + secondLeft);

                    if (secondLeft < 0) {
                        timer.cancel();
                        smsButton.setText(getString(R.string.verify_code));
                        smsButton.setEnabled(true);
                    }
                }
            });
        }
    };
}