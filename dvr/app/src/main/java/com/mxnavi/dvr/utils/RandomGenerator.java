package com.mxnavi.dvr.utils;

import java.util.Random;

public class RandomGenerator {
    private static final int SMS_CODE_LENGTH = 6;
    public String getSMSCode() {
        String code = "";
        Random random = new Random();
        for (int i = 0; i < SMS_CODE_LENGTH; i++) {
            code += random.nextInt(10);
        }
        return code;
    }
}
