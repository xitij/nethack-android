#include <string.h>
#include <jni.h>

#include <stdio.h>
#include <unistd.h>
#include <pthread.h>


static char *s_MainViewText;
static int s_MainViewNumCols;
static int s_MainViewNumRows;

int g_Test1 = 42;

#if 0
      int pthread_create(pthread_t *thread, const pthread_attr_t *attr,
                          void *(*start_routine) (void *), void *arg);
#endif

static int s_MainViewX = 0;
static int s_MainViewY = 0;

static int s_MainViewChanged = 0;

void xputc(int c)
{
	if(s_MainViewX < s_MainViewNumCols && s_MainViewY < s_MainViewNumRows)
	{
		s_MainViewText[s_MainViewNumCols*s_MainViewY + s_MainViewX] = (char)c;
	}

	s_MainViewX++;
	if(s_MainViewX >= s_MainViewNumCols)
	{
		s_MainViewX = 0;
		s_MainViewY++;
	}

	s_MainViewChanged = 1;
}


void xputs(const char *s)
{
	const char *ptr = s;
	while(*s)
	{
		xputc((int)(*s++));
	}
}

pthread_t g_ThreadHandle;

static void *sThreadFunc()
{
/*
	xputc('');
	xputc('B');
*/
	char buff[256];

	sprintf(buff, "%d x %d", s_MainViewNumCols, s_MainViewNumRows);
	xputs(buff);


	while(1)
	{
		g_Test1++;
		usleep(1000);	/* 1 ms */
	}
	return NULL;
}


int Java_com_nethackff_NetHackApp_TestInit(JNIEnv *env, jobject thiz,
		int numcols, int numrows)
{
	char *p;
	int x, y;

	if(g_ThreadHandle)
	{
		return 0;
	}

	if(s_MainViewText)
	{
		return 0;
	}

	s_MainViewNumCols = numcols;
	s_MainViewNumRows = numrows;

	s_MainViewText = malloc(s_MainViewNumCols*s_MainViewNumRows + 1);
	s_MainViewText[s_MainViewNumCols*s_MainViewNumRows] = '\0';

	p = s_MainViewText;
	for(y = 0; y < s_MainViewNumRows; y++)
	{
		for(x = 0; x < s_MainViewNumCols; x++)
		{
			*p++ = ' ';
		}
	}


/*
	sprintf(s_MainViewText, "123456789012345\n6");
	sprintf(s_MainViewText + s_MainViewNumCols, "67890");
*/
	g_Test1 = 25;
	printf("??? TEST-- What happens to this?\n");

	if(pthread_create(&g_ThreadHandle, NULL, sThreadFunc, NULL) != 0)
	{
		g_ThreadHandle = 0;
		return 0;	/* Failure. */
	}

	return 1;
}

void Java_com_nethackff_NetHackApp_TestShutdown(JNIEnv *env, jobject thiz)
{
	if(g_ThreadHandle)
	{
#if 0
		pthread_cancel(g_ThreadHandle);	/* Would return 0 on success. */
#endif
		g_ThreadHandle = 0;
	}

	if(s_MainViewText)
	{
		free(s_MainViewText);
		s_MainViewText = NULL;
	}
}

void Java_com_nethackff_NetHackApp_TestUpdate(JNIEnv *env, jobject thiz)
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
Java_com_nethackff_NetHackApp_stringFromJNI( JNIEnv* env,
                                                  jobject thiz )
{
	char buff[256];
	snprintf(buff, sizeof(buff), "Test1 %d", g_Test1);
#if 0
	g_Test1++;
    return (*env)->NewStringUTF(env, "Hello from JNI !");
#endif
    return (*env)->NewStringUTF(env, s_MainViewText);
}
