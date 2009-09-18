#include <string.h>
#include <jni.h>

#include <stdio.h>
#include <unistd.h>
#include <pthread.h>

#define RECEIVEBUFFSZ 255
#define SENDBUFFSZ 1023

static int s_MainViewNumCols;
static int s_MainViewNumRows;

static char s_ReceiveBuff[RECEIVEBUFFSZ + 1];
static char s_SendBuff[SENDBUFFSZ + 1];
static int s_ReceiveCnt;
static int s_SendCnt;


void xputc(int c)
{
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

	char buff[256];

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

	s_ReceiveCnt = 0;
	s_SendCnt = 0;

	s_MainViewNumCols = numcols;
	s_MainViewNumRows = numrows;

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
}


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
	return str;
}

/* End of file */
