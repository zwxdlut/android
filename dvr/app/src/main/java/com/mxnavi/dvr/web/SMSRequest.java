package com.mxnavi.dvr.web;

public class SMSRequest {
    private String phone;
    private String code;

    public SMSRequest(String phone, String code) {
        this.phone = phone;
        this.code = code;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }
}
