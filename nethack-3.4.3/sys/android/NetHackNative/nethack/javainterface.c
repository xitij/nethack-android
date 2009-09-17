#include <string.h>
#include <jni.h>

#include <stdio.h>
#include <unistd.h>
#include <pthread.h>

#define RECEIVEBUFFSZ 255
#define SENDBUFFSZ 1023

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



static char s_ReceiveBuff[RECEIVEBUFFSZ + 1];
static char s_SendBuff[SENDBUFFSZ + 1];
static int s_ReceiveCnt;
static int s_SendCnt;


void xputc(int c)
{
#if 0
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
#endif

	/* TODO: Thread protection */

	/* TODO: Wait for space to be available. */
	if(s_SendCnt < SENDBUFFSZ)
	{
		s_SendBuff[s_SendCnt++] = (char)c;
	}
}


void xputs(const char *s)
{
	/* TODO: Thread protection */

	const char *ptr = s;
	while(*s)
	{
		xputc((int)(*s++));
	}
}

pthread_t g_ThreadHandle;

static void *sThreadFunc()
{
	int i;

/*
	xputc('');
	xputc('B');
*/
	char buff[256];

	sprintf(buff, "-- %d x %d\n", s_MainViewNumCols, s_MainViewNumRows);
	xputs(buff);


	while(1)
	{
		if(s_ReceiveCnt > 0)
		{
			for(i = 0; i < s_ReceiveCnt; i++)
			{
				xputc((int)(s_ReceiveBuff[i]));
			}
			s_ReceiveCnt = 0;
		}
		g_Test1++;
#if 1

		usleep(1000);	/* 1 ms */
#endif

//		sleep(1);

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

	s_ReceiveCnt = 0;
	s_SendCnt = 0;

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


//void Java_com_nethackff_NetHackApp_TerminalSend(JNIEnv *env, jobject thiz,
//		const char *buff)
void Java_com_nethackff_NetHackApp_TerminalSend(JNIEnv *env, jobject thiz,
		jstring str)
{
	/* TODO: Thread protection! */

	const char *nativestr = (*env)->GetStringUTFChars(env, str, 0);

	const char *ptr = nativestr;
	for(; *ptr; ptr++)
	{
		if(s_ReceiveCnt < RECEIVEBUFFSZ)
		{
			s_ReceiveBuff[s_ReceiveCnt++] = *ptr;
//			s_ReceiveBuff[s_ReceiveCnt++] = 'Y';
		}
		/* TODO: Wait for consumption? */
	}

	(*env)->ReleaseStringUTFChars(env, str, nativestr);
}

jstring Java_com_nethackff_NetHackApp_TerminalReceive(JNIEnv *env,
		jobject thiz)
{
	/* TODO: Thread protection! */

	s_SendBuff[s_SendCnt] = '\0';
	jstring str = (*env)->NewStringUTF(env, s_SendBuff);
	s_SendCnt = 0;
/* TODO: Possibly free the string somehow here? */
	return str;
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
