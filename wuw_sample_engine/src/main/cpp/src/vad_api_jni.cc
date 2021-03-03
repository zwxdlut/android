//
// Created by Administrator on 2020/8/12.
//

#include <android/log.h>
#include <cstdio>
#include <malloc.h>
#include <cstdint>
#include <cstring>
#include <mutex>

#include "vad_api.h"
#include "vad_api_jni.h"

#ifdef __cplusplus
extern "C" {
#endif

static const char *TAG = "vad_api_jni";
static JavaVM *g_vm = nullptr;
static jobject g_callback = nullptr;
static std::mutex g_mutex;
static struct api_vad *g_engine = nullptr;

int vad_result_handler(void *_ptr, int _status)
{
    if (nullptr == g_callback)
    {
        __android_log_print(ANDROID_LOG_ERROR, TAG, "vad_result_handler: The callback is null!\n");
        return -1;
    }

    if (nullptr == g_vm)
    {
        __android_log_print(ANDROID_LOG_ERROR, TAG, "vad_result_handler: JavaVM is null!\n");
        return -1;
    }

    int ret = 0;
    bool is_attached = false;
    JNIEnv *env = nullptr;

    // get the env
    ret = g_vm->GetEnv((void **) &env, JNI_VERSION_1_6);

    if (JNI_OK != ret) {
        __android_log_print(ANDROID_LOG_ERROR, TAG, "vad_result_handler: get the env failed(%d)!\n", ret);

        if (JNI_EDETACHED != ret)
        {
            return ret;
        }

        ret =  g_vm->AttachCurrentThread(&env, nullptr);

        if (JNI_OK != ret)
        {
            __android_log_print(ANDROID_LOG_ERROR, TAG, "vad_result_handler: attach failed(%d)!\n", ret);
            return ret;
        }

        is_attached = true;
    }

    g_mutex.lock();

    if (nullptr == g_callback)
    {
        __android_log_print(ANDROID_LOG_ERROR, TAG, "vad_result_handler: The callback is null!\n");
        g_mutex.unlock();
        return -1;
    }

    // get the callback class
    jclass java_class = env->GetObjectClass(g_callback);

    if (nullptr == java_class)
    {
        __android_log_print(ANDROID_LOG_ERROR, TAG, "vad_result_handler: unable to find the callback class!\n");
        g_mutex.unlock();
        return -1;
    }

    // get the method "onResult" of the callback class
    jmethodID id = env->GetMethodID(java_class,"onResult", "(I)I");

    if (nullptr == id)
    {
        __android_log_print(ANDROID_LOG_ERROR, TAG, "vad_result_handler: unable to find the method onResult!\n");
        g_mutex.unlock();
        return -1;
    }

    ret = env->CallIntMethod(g_callback, id, _status);

    g_mutex.unlock();

    if (is_attached)
    {
        g_vm->DetachCurrentThread();
    }

    return ret;
}

JNIEXPORT jint JNICALL Java_com_wuw_1sample_1engine_vad_VadApi_native_1create(JNIEnv *_env, jobject _thiz, jstring _res_path)
{
    jint ret = 0;
    const char *res_path = _env->GetStringUTFChars(_res_path, JNI_FALSE);

    g_engine = vad_new(res_path);

    if (nullptr == g_engine)
    {
        __android_log_print(ANDROID_LOG_ERROR, TAG, "create: create the vad engine failed!\n");
        ret = -1;
    }

    _env->ReleaseStringUTFChars(_res_path, res_path);

    return ret;
}

JNIEXPORT jint JNICALL Java_com_wuw_1sample_1engine_vad_VadApi_native_1delete(JNIEnv *_env, jobject _thiz)
{
    int ret = 0;

    if (nullptr != g_engine)
    {
        ret = vad_delete(g_engine);
        g_engine = nullptr;
    }

    g_mutex.lock();

    if (nullptr != g_callback)
    {
        _env->DeleteGlobalRef(g_callback);
        g_callback = nullptr;
    }

    g_mutex.unlock();

    return ret;
}

JNIEXPORT jint JNICALL Java_com_wuw_1sample_1engine_vad_VadApi_native_1start(JNIEnv *_env, jobject _thiz, jobject _callback)
{
    if (nullptr == g_engine)
    {
        return -1;
    }

    _env->GetJavaVM(&g_vm);

    g_mutex.lock();

    if (nullptr != g_callback)
    {
        _env->DeleteGlobalRef(g_callback);
    }

    g_callback = _env->NewGlobalRef(_callback);

    g_mutex.unlock();

    return vad_start(g_engine, nullptr, vad_result_handler);
}

JNIEXPORT jint JNICALL Java_com_wuw_1sample_1engine_vad_VadApi_native_1feed(JNIEnv *_env, jobject _thiz, jbyteArray _buf, jint _size)
{
    if (nullptr == g_engine)
    {
        return -1;
    }

    // use GetByteArrayRegion
//    jsize size = _env->GetArrayLength(_buf);
//
//    if (0 == size)
//    {
//        __android_log_print(ANDROID_LOG_ERROR, TAG, "feed：The buffer size is 0!\n");
//        return -2;
//    }
//
//    jbyte *buf = (jbyte *)malloc(sizeof(jbyte) * size);
//
//    if (nullptr == buf)
//    {
//        __android_log_print(ANDROID_LOG_ERROR, TAG, "feed: malloc failed!\n");
//        return -3;
//    }
//
//    jint ret = 0;
//
//    memset(buf, 0, sizeof(jbyte) * size);
//    _env->GetByteArrayRegion(_buf, 0, size, buf);
//    ret = vad_feed(g_engine, (char *)buf, size);
//    free(buf);

    // use GetByteArrayElements
    jbyte *buf = _env->GetByteArrayElements(_buf, 0);
    jsize size = _env->GetArrayLength(_buf);
    jint ret = vad_feed(g_engine, (char *)buf, size);
    _env->ReleaseByteArrayElements(_buf, buf, 0);

    return ret;
}

JNIEXPORT jint JNICALL Java_com_wuw_1sample_1engine_vad_VadApi_native_1stop(JNIEnv *_env, jobject _thiz)
{
    if (nullptr == g_engine)
    {
        return -1;
    }

    return vad_stop(g_engine);
}

JNIEXPORT jint JNICALL Java_com_wuw_1sample_1engine_vad_VadApi_native_1reset(JNIEnv *env, jobject thiz)
{
    if (nullptr == g_engine)
    {
        return -1;
    }

    return vad_reset(g_engine);
}

JNIEXPORT jint JNICALL Java_com_wuw_1sample_1engine_vad_VadApi_native_1setting(JNIEnv *_env, jobject _thiz, jstring _config)
{
    if (nullptr == g_engine)
    {
        return -1;
    }

    jint ret = 0;
    const char *config = _env->GetStringUTFChars(_config, JNI_FALSE);

    ret = vad_setting(g_engine, config);
    _env->ReleaseStringUTFChars(_config, config);

    return ret;
}

#ifdef __cplusplus
}
#endif