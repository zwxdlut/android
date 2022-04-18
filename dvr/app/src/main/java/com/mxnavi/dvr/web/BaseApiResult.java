package com.mxnavi.dvr.web;

public class BaseApiResult {
    private int ok;
    private String error;

    public String getError() {
        return error;
    }

    public void setError(String error) {
        this.error = error;
    }

    public int getOk() {
        return ok;
    }

    public void setOk(int ok) {
        this.ok = ok;
    }

    @Override
    public String toString() {
        return "BaseApiResult{" +
                "ok=" + ok +
                ", error='" + error + '\'' +
                '}';
    }
}
