package com.mxnavi.dvr.utils;

import android.content.Context;
import android.content.SharedPreferences;

public class PhoneNumberManager {
    private static final String PHONE_DATA_NAME = "phone";
    private static final String PHONE_KEY = "phone_number";
    private Context context;
    private static final PhoneNumberManager phoneNumberManager = new PhoneNumberManager();
    private PhoneNumberManager() {
    }

    public static PhoneNumberManager getInstance() {
        return phoneNumberManager;
    }

    public void init(Context context) {
        this.context = context;
    }

    public void setPhoneNumber(String phoneNumber) {
        SharedPreferences sharedPreferences = context.getSharedPreferences(PHONE_DATA_NAME, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putString(PHONE_KEY, phoneNumber);
        editor.commit();
    }

    public String getPhoneNumber() {
        SharedPreferences sharedPreferences = context.getSharedPreferences(PHONE_DATA_NAME, Context.MODE_PRIVATE);
        String phone = sharedPreferences.getString(PHONE_KEY, "");
        return phone;
    }

    public void deletePhoneNumber() {
        setPhoneNumber("");
    }

}
