//
// Created by Administrator on 2020/8/12.
//

#include <stdio.h>
#include <malloc.h>
#include <android/log.h>

#include "vad_api.h"
#include "vad_api_jni.h"

#ifdef __cplusplus
extern "C" {
#endif

static const char *TAG = "vad_api_jni";
static JavaVM *g_vm = NULL;
static struct api_vad *g_engine = NULL;
static bool started = false;

int vad_result_handler(void *_ptr, int _status)
{
    if (NULL == _ptr)
    {
        __android_log_print(ANDROID_LOG_ERROR, TAG, "vad_result_handler: the callback pointer is null!!!\n");
        return -1;
    }

    // Get the env
    int ret = 0;
    JNIEnv *env = NULL;

    ret = g_vm->GetEnv((void **) &env, JNI_VERSION_1_6);

    if (JNI_OK != ret)
    {
        __android_log_print(ANDROID_LOG_ERROR, TAG, "vad_result_handler: get the env failed!!!\n");
        return ret;
    }

    // Get the callback class
    jobject callback = (jobject)_ptr;
    jclass java_class = env->GetObjectClass(callback);

    if (0 == java_class)
    {
        __android_log_print(ANDROID_LOG_ERROR, TAG, "vad_result_handler: unable to find the callback class!!!\n");
        return -1;
    }

    // Get the method "onResult" of the callback class
    jmethodID id = env->GetMethodID(java_class,"onResult", "(I)I");

    if (NULL == id)
    {
        __android_log_print(ANDROID_LOG_ERROR, TAG, "vad_result_handler: unable to find the method onResult!!!\n");
        return -1;
    }

    return env->CallIntMethod(callback, id, _status);;
}

JNIEXPORT jint JNICALL Java_com_wuw_1sample_1engine_vad_VadApi_create(JNIEnv *_env, jobject _thiz, jstring _res_path)
{
    int ret = 0;
    const char *res_path = _env->GetStringUTFChars(_res_path, JNI_FALSE);

    g_engine = vad_new(res_path);

    if (NULL == g_engine)
    {
        __android_log_print(ANDROID_LOG_ERROR, TAG, "create: create vad engine failed!!!\n");
        ret = -1;
    }

    _env->ReleaseStringUTFChars(_res_path, res_path);

    return ret;
}

JNIEXPORT jint JNICALL Java_com_wuw_1sample_1engine_vad_VadApi_delete(JNIEnv *_env, jobject _thiz)
{
    if (NULL == g_engine)
    {
        return 0;
    }

    if (0 == vad_delete(g_engine))
    {
        g_engine = NULL;
        return 0;
    }
    else
    {
        __android_log_print(ANDROID_LOG_ERROR, TAG, "delete： delete vad engine failed!!!");
        return -1;
    }
}

JNIEXPORT jint JNICALL Java_com_wuw_1sample_1engine_vad_VadApi_start(JNIEnv *_env, jobject _thiz, jobject _callback)
{
    if (NULL == g_engine)
    {
        return -1;
    }

    if (started)
    {
        return 0;
    }

    jobject callback = _env->NewGlobalRef(_callback);

    _env->GetJavaVM(&g_vm);
    started = true;

    return vad_start(g_engine, callback, vad_result_handler);
}

JNIEXPORT jint JNICALL Java_com_wuw_1sample_1engine_vad_VadApi_stop(JNIEnv *_env, jobject _thiz)
{
    if (NULL == g_engine)
    {
        return -1;
    }

    started = false;

    return vad_stop(g_engine);
}

JNIEXPORT jint JNICALL Java_com_wuw_1sample_1engine_vad_VadApi_reset(JNIEnv *env, jobject thiz)
{
    if (NULL == g_engine)
    {
        return -1;
    }

    return vad_reset(g_engine);
}

JNIEXPORT jint JNICALL Java_com_wuw_1sample_1engine_vad_VadApi_feed(JNIEnv *_env, jobject _thiz, jbyteArray _buf, jint _size)
{
    if (NULL == g_engine)
    {
        return -1;
    }

    if (!started)
    {
        return -2;
    }

    int ret = 0;
    char *buf = (char *)malloc(_size);

    _env->GetByteArrayRegion(_buf, 0, _size, reinterpret_cast<jbyte *>(buf));

    ret = vad_feed(g_engine, buf, _size);

    free(buf);

    return ret;
}

JNIEXPORT jint JNICALL Java_com_wuw_1sample_1engine_vad_VadApi_setting(JNIEnv *_env, jobject _thiz, jstring _config)
{
    int ret = 0;
    const char *config = _env->GetStringUTFChars(_config, JNI_FALSE);

    ret = vad_setting(g_engine, config);

    _env->ReleaseStringUTFChars(_config, config);

    return ret;
}

#ifdef __cplusplus
}
#endif