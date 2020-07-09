#include <jni.h>
#include <string>

#include "my_native_service.h"

static my_native_service *g_service = NULL;

extern "C" JNIEXPORT jstring JNICALL
Java_com_example_my_1native_1service_MainActivity_stringFromJNI(
        JNIEnv* env,
        jobject /* this */) {
    std::string hello = "Hello from C++";
    return env->NewStringUTF(hello.c_str());
}extern "C"
JNIEXPORT void JNICALL
Java_com_example_my_1native_1service_MyNativeService_init(JNIEnv *env, jobject thiz) {
    g_service = new my_native_service();
}extern "C"
JNIEXPORT void JNICALL
Java_com_example_my_1native_1service_MyNativeService_deinit(JNIEnv *env, jobject thiz) {
    if (NULL != g_service) {
        delete g_service;
        g_service = NULL;
    }
}extern "C"
JNIEXPORT jint JNICALL
Java_com_example_my_1native_1service_MyNativeService_call(JNIEnv *env, jobject thiz, jint param) {
    if (NULL == g_service) {
        printf("Java_com_example_my_1native_1service_MyNativeService_call: g_service is null!\n");
        return -1;
    }

    return g_service->call(param);
}