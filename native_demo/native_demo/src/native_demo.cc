#include "native_demo.h"

#include <stdio.h>

native_demo::native_demo(void)
{
	printf("native_demo::native_demo\n");
}

native_demo::~native_demo(void)
{
	printf("native_demo::~native_demo\n");
}

void native_demo::hello(const int32_t _param)
{
	printf("native_demo::hello: param = %d\n", _param);
}
