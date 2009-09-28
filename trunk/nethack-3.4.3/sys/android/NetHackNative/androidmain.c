#include "hack.h"

#include <string.h>
#include <jni.h>

#include <stdarg.h>
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

#if 0

void error(int status, int errnum, const char *format, ...)
{
	fflush(stdout);
	va_list args;
	va_start(args, format);
	fprintf(stderr, "nethack: ");
	vfprintf(stderr, format, args);
	if(errnum)
	{
		/* perror() ? */
	}
	va_end(args);
}

#endif


void android_putchar(int c)
{
	/* TODO: Thread protection */

	/* TODO: Wait for space to be available. */
	if(s_SendCnt < SENDBUFFSZ)
	{
		s_SendBuff[s_SendCnt++] = (char)c;
	}
}


void android_puts(const char *s)
{
	/* TODO: Thread protection */

	const char *ptr = s;
	while(*s)
	{
		xputc((int)(*s++));
	}
}


int android_getch(void)
{
	while(1)
	{
		if(s_ReceiveCnt > 0)
		{
			int ret = s_ReceiveBuff[0], i;
			/* Hmm, no good! */
			for(i = 0; i < s_ReceiveCnt - 1; i++)
			{
				s_ReceiveBuff[i] = s_ReceiveBuff[i + 1];
			}
			s_ReceiveCnt--;
			return ret;
		}
		usleep(1000);	/* 1 ms */
	}
	/*return EOF;*/
}

pthread_t g_ThreadHandle;

static void *sThreadFunc()
{
	int argc = 1;
	char *argv[] = {	"nethack"	};

	int i = 0;

	char buff[256];

	chdir("/data/data/com.nethackff/dat");

	choose_windows(DEFAULT_WINDOW_SYS);
	initoptions();

	init_nhwindows(&argc,argv);

/*
xputs("process_options\n");
	process_options(argc, argv);
*/

	askname();

	/*
	 * Initialization of the boundaries of the mazes
	 * Both boundaries have to be even.
	 */
	x_maze_max = COLNO-1;
	if (x_maze_max % 2)
		x_maze_max--;
	y_maze_max = ROWNO-1;
	if (y_maze_max % 2)
		y_maze_max--;

	/*
	 *  Initialize the vision system.  This must be before mklev() on a
	 *  new game or before a level restore on a saved game.
	 */
	vision_init();


	display_gamewindows();


		player_selection();
		newgame();

xputs("Done!\n");

#if 0
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
		sprintf(buff, "%d\n", i);
		xputs(buff);
		sleep(1);
	}
#endif
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
