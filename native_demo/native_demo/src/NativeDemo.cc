#include "NativeDemo.h"

#include "native_demo.h"

static native_demo* g_nd = NULL;

JNIEXPORT void JNICALL Java_NativeDemo_init(JNIEnv*, jobject)
{
	g_nd = new native_demo();
}

JNIEXPORT void JNICALL Java_NativeDemo_deinit(JNIEnv*, jobject)
{
	if (NULL != g_nd)
	{
		delete g_nd;
		g_nd = NULL;
	}
}


JNIEXPORT void JNICALL Java_NativeDemo_hello(JNIEnv*, jobject, jint _param)
{
	if (NULL == g_nd)
	{
		perror("Java_NativeDemo_hello: native_demo is null!\n");
		return;
	}
		
	g_nd->hello((uint32_t)_param);
}