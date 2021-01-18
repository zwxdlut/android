//
// Created by Administrator on 2020/8/12.
//

#include <stdio.h>
#include <malloc.h>
#include <string.h>
#include <android/log.h>

#include "vad_api.h"
#include "vad_api_jni.h"

#ifdef __cplusplus
extern "C" {
#endif

static const char *TAG = "vad_api_jni";
static JavaVM *g_vm = NULL;
static jobject g_callback = NULL;
static struct api_vad *g_engine = NULL;

int vad_result_handler(void *_ptr, int _status)
{
    if (NULL == g_callback)
    {
        __android_log_print(ANDROID_LOG_ERROR, TAG, "vad_result_handler: The callback is null!\n");
        return -1;
    }

    int ret = 0;
    bool is_attached = false;
    JNIEnv *env = NULL;

    // get the env
    ret = g_vm->GetEnv((void **) &env, JNI_VERSION_1_6);

    if (JNI_OK != ret) {
        __android_log_print(ANDROID_LOG_ERROR, TAG, "vad_result_handler: Get the env failed(%d), need to attach!\n", ret);
        ret =  g_vm->AttachCurrentThread(&env, NULL);

        if (JNI_OK != ret)
        {
            __android_log_print(ANDROID_LOG_ERROR, TAG, "vad_result_handler: attach failed(%d)!\n", ret);
            return ret;
        }

        is_attached = true;
    }

    // get the callback class
    //jobject callback = (jobject)_ptr;
    jclass java_class = env->GetObjectClass(g_callback);

    if (NULL == java_class)
    {
        __android_log_print(ANDROID_LOG_ERROR, TAG, "vad_result_handler: unable to find the callback class!\n");
        return -1;
    }

    // get the method "onResult" of the callback class
    jmethodID id = env->GetMethodID(java_class,"onResult", "(I)I");

    if (NULL == id)
    {
        __android_log_print(ANDROID_LOG_ERROR, TAG, "vad_result_handler: unable to find the method onResult!\n");
        return -1;
    }

    ret = env->CallIntMethod(g_callback, id, _status);

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

    if (NULL == g_engine)
    {
        __android_log_print(ANDROID_LOG_ERROR, TAG, "create: create the vad engine failed!\n");
        ret = -1;
    }

    _env->ReleaseStringUTFChars(_res_path, res_path);

    return ret;
}

JNIEXPORT jint JNICALL Java_com_wuw_1sample_1engine_vad_VadApi_native_1delete(JNIEnv *_env, jobject _thiz)
{
    if (NULL == g_engine)
    {
        return 0;
    }

    jint ret = vad_delete(g_engine);

    if (0 == ret)
    {
        g_engine = NULL;
    }

    return ret;
}

JNIEXPORT jint JNICALL Java_com_wuw_1sample_1engine_vad_VadApi_native_1start(JNIEnv *_env, jobject _thiz, jobject _callback)
{
    if (NULL == g_engine)
    {
        return -1;
    }

    g_callback = _env->NewGlobalRef(_callback);
    _env->GetJavaVM(&g_vm);

    return vad_start(g_engine, NULL, vad_result_handler);
}

JNIEXPORT jint JNICALL Java_com_wuw_1sample_1engine_vad_VadApi_native_1feed(JNIEnv *_env, jobject _thiz, jbyteArray _buf, jint _size)
{
    if (NULL == g_engine)
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
//    if (NULL == buf)
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
    if (NULL == g_engine)
    {
        return -1;
    }

    jint ret = vad_stop(g_engine);
    _env->DeleteGlobalRef(g_callback);
    g_callback = NULL;

    return ret;
}

JNIEXPORT jint JNICALL Java_com_wuw_1sample_1engine_vad_VadApi_native_1reset(JNIEnv *env, jobject thiz)
{
    if (NULL == g_engine)
    {
        return -1;
    }

    return vad_reset(g_engine);
}

JNIEXPORT jint JNICALL Java_com_wuw_1sample_1engine_vad_VadApi_native_1setting(JNIEnv *_env, jobject _thiz, jstring _config)
{
    if (NULL == g_engine)
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