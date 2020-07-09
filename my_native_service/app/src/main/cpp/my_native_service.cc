//
// Created by Administrator on 2020/3/25.
//

#include "my_native_service.h"

#include <stdio.h>
#include <android/log.h>

my_native_service::my_native_service() {
    __android_log_print(ANDROID_LOG_DEBUG, TAG, "my_native_service");
}

my_native_service::~my_native_service() {
    __android_log_print(ANDROID_LOG_DEBUG, TAG, "~my_native_service");
}

int32_t my_native_service::call(const int32_t _param) {
    __android_log_print(ANDROID_LOG_DEBUG, TAG, "call: param = %d ", _param);
    return 0;
}
