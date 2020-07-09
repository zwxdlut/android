//
// Created by Administrator on 2020/3/25.
//

#ifndef MY_NATIVE_SERVICE_MY_NATIVE_SERVICE_H
#define MY_NATIVE_SERVICE_MY_NATIVE_SERVICE_H

#include <stdint.h>

class my_native_service {
public:
    my_native_service();
    ~my_native_service();
    int32_t call(const int32_t _param);

    const char *TAG = "my_native_service";
};


#endif //MY_NATIVE_SERVICE_MY_NATIVE_SERVICE_H
