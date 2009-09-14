#include <string.h>
#include <jni.h>

#include <stdio.h>
#include <unistd.h>
#include <pthread.h>

int g_Test1 = 42;

#if 0
      int pthread_create(pthread_t *thread, const pthread_attr_t *attr,
                          void *(*start_routine) (void *), void *arg);
#endif

pthread_t g_ThreadHandle;

static void *sThreadFunc()
{
#if 0
	while(g_Test1 <= 1000)
#endif
	while(1)
	{
		g_Test1++;
		usleep(1000);	/* 1 ms */
	}
	return NULL;
}


int Java_com_ff_nativetest2_NativeTest2_TestInit(JNIEnv *env, jobject thiz)
{
	if(g_ThreadHandle)
	{
		return 0;
	}

	g_Test1 = 25;
	printf("??? TEST-- What happens to this?\n");

	if(pthread_create(&g_ThreadHandle, NULL, sThreadFunc, NULL) != 0)
	{
		g_ThreadHandle = 0;
		return 0;	/* Failure. */
	}

	return 1;
}

void Java_com_ff_nativetest2_NativeTest2_TestShutdown(JNIEnv *env, jobject thiz)
{
	if(g_ThreadHandle)
	{
#if 0
		pthread_cancel(g_ThreadHandle);	/* Would return 0 on success. */
#endif
		g_ThreadHandle = 0;
	}
}

void Java_com_ff_nativetest2_NativeTest2_TestUpdate(JNIEnv *env, jobject thiz)
{
		usleep(2000*1000);	/* 2 s */
}

/* This is a trivial JNI example where we use a native method
 * to return a new VM String. See the corresponding Java source
 * file located at:
 *
 *   apps/samples/hello-jni/project/src/com/example/HelloJni/HelloJni.java
 */
jstring
Java_com_ff_nativetest2_NativeTest2_stringFromJNI( JNIEnv* env,
                                                  jobject thiz )
{
	char buff[256];
	snprintf(buff, sizeof(buff), "Test1 %d", g_Test1);
#if 0
	g_Test1++;
    return (*env)->NewStringUTF(env, "Hello from JNI !");
#endif
    return (*env)->NewStringUTF(env, buff);
}
