#include <stdio.h>

#include "native_example_jni.h"

#ifdef __cplusplus
extern "C" {
#endif

JNIEXPORT void JNICALL Java_NativeExample_hello(JNIEnv* _env, jobject _thiz, jstring _content)
{
	const char* content = _env->GetStringUTFChars(_content, JNI_FALSE);
	printf("Java_NativeExample_hello: %s\n", content);
	_env->ReleaseStringUTFChars(_content, content);
}

#ifdef __cplusplus
}
#endif