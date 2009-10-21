#include "hack.h"
#include "dlb.h"

#include <string.h>
#include <jni.h>

#include <stdarg.h>
#include <stdio.h>
#include <unistd.h>
#include <pthread.h>
#include <semaphore.h>

#define RECEIVEBUFFSZ 255
#define SENDBUFFSZ 1023

static char s_ReceiveBuff[RECEIVEBUFFSZ + 1];
static char s_SendBuff[SENDBUFFSZ + 1];
static int s_ReceiveCnt;
static int s_SendCnt;

static pthread_mutex_t s_ReceiveMutex;
static pthread_mutex_t s_SendMutex;
static sem_t s_ReceiveWaitingForDataSema;
static sem_t s_ReceiveWaitingForConsumptionSema;
static sem_t s_SendNotFullSema;

static int s_SendWaitingForNotFull;
static int s_ReceiveWaitingForData;
static int s_ReceiveWaitingForConsumption;

static int s_Quit = 0;

static void NDECL(wd_message);
#ifdef WIZARD
static boolean wiz_error_flag = FALSE;
#endif

static void android_putchar_internal(int c)
{
	while(1)
	{
		pthread_mutex_lock(&s_SendMutex);

		if(s_SendCnt < SENDBUFFSZ)
		{
			s_SendBuff[s_SendCnt++] = (char)c;

			pthread_mutex_unlock(&s_SendMutex);

			return;
		}

		s_SendWaitingForNotFull = 1;

		pthread_mutex_unlock(&s_SendMutex);

		sem_wait(&s_SendNotFullSema);
	}
}


void android_putchar(int c)
{

	android_putchar_internal(c);

}


void android_puts(const char *s)
{
	const char *ptr = s;

	while(*s)
	{
		android_putchar_internal((int)(*s++));
	}
}


int android_printf(const char *fmt, ...)
{
	char buff[1024];
	int r;

	va_list args;
	va_start(args, fmt);
	r = vsnprintf(buff, sizeof(buff), fmt, args);
	va_end(args);

	android_puts(buff);

	return r;
}


int android_getch(void)
{
	while(1)
	{
		pthread_mutex_lock(&s_ReceiveMutex);
		if(s_ReceiveCnt > 0)
		{
			int ret = s_ReceiveBuff[0], i;
			/* Hmm, no good! */
			for(i = 0; i < s_ReceiveCnt - 1; i++)
			{
				s_ReceiveBuff[i] = s_ReceiveBuff[i + 1];
			}
			s_ReceiveCnt--;

			if(s_ReceiveWaitingForConsumption)
			{
				s_ReceiveWaitingForConsumption = 0;
				sem_post(&s_ReceiveWaitingForConsumptionSema);
			}

			pthread_mutex_unlock(&s_ReceiveMutex);
			return ret;
		}

		s_ReceiveWaitingForData = 1;

		pthread_mutex_unlock(&s_ReceiveMutex);

		sem_wait(&s_ReceiveWaitingForDataSema);
	}
	/*return EOF;*/
}

pthread_t g_ThreadHandle;

void nethack_exit(int result)
{
	s_Quit = 1;
	while(1)
	{
		sleep(60);
	}
}

static void *sThreadFunc()
{
	int fd;

	int argc = 1;
	char *argv[] = {	"nethack"	};

	int i = 0;

	char buff[256];

	chdir("/data/data/com.nethackff/nethackdir");

	choose_windows(DEFAULT_WINDOW_SYS);
	initoptions();

	init_nhwindows(&argc,argv);

	askname();

#ifdef WIZARD
	if(!wizard) {
#endif
		/*
		 * check for multiple games under the same name
		 * (if !locknum) or check max nr of players (otherwise)
		 */
		(void) signal(SIGQUIT,SIG_IGN);
		(void) signal(SIGINT,SIG_IGN);
		if(!locknum)
			Sprintf(lock, "%d%s", (int)getuid(), plname);
		getlock();
#ifdef WIZARD
	} else {
		Sprintf(lock, "%d%s", (int)getuid(), plname);
		getlock();
	}
#endif /* WIZARD */

	dlb_init();	/* must be before newgame() */

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

	if ((fd = restore_saved_game()) >= 0) {

#ifdef WIZARD
		/* Since wizard is actually flags.debug, restoring might
		 * overwrite it.
		 */
		boolean remember_wiz_mode = wizard;
#endif
		const char *fq_save = fqname(SAVEF, SAVEPREFIX, 1);

		(void) chmod(fq_save,0);	/* disallow parallel restores */
		(void) signal(SIGINT, (SIG_RET_TYPE) done1);
#ifdef NEWS
		if(iflags.news) {
		    display_file(NEWS, FALSE);
		    iflags.news = FALSE; /* in case dorecover() fails */
		}
#endif
		pline("Restoring save file...");
		mark_synch();	/* flush output */
		if(!dorecover(fd))
			goto not_recovered;
#ifdef WIZARD
		if(!wizard && remember_wiz_mode) wizard = TRUE;
#endif
		check_special_room(FALSE);
		wd_message();

		if (discover || wizard) {
			if(yn("Do you want to keep the save file?") == 'n')
			    (void) delete_savefile();
			else {
			    (void) chmod(fq_save,FCMASK); /* back to readable */
			    compress(fq_save);
			}
		}
		flags.move = 0;
	} else {
not_recovered:
		player_selection();

		newgame();
		wd_message();

		flags.move = 0;
		set_wear();
		(void) pickup(1);
	}

	moveloop();

	nethack_exit(EXIT_SUCCESS);

	return(0);
}


int Java_com_nethackff_NetHackApp_NetHackInit(JNIEnv *env, jobject thiz)
{
	char *p;
	int x, y;

	if(g_ThreadHandle)
	{
		return 0;
	}

	s_SendWaitingForNotFull = 0;
	s_ReceiveWaitingForData = 0;
	s_ReceiveWaitingForConsumption = 0;
	s_ReceiveCnt = 0;
	s_SendCnt = 0;
	s_Quit = 0;	

	if(pthread_mutex_init(&s_SendMutex, 0) != 0)
	{
		return 0;
	}

	if(pthread_mutex_init(&s_ReceiveMutex, 0) != 0)
	{
		pthread_mutex_destroy(&s_SendMutex);
		return 0;
	}

	if(sem_init(&s_ReceiveWaitingForDataSema, 0, 0) != 0)
	{
		pthread_mutex_destroy(&s_ReceiveMutex);
		pthread_mutex_destroy(&s_SendMutex);
		return 0;
	}

	if(sem_init(&s_ReceiveWaitingForConsumptionSema, 0, 0) != 0)
	{
		pthread_mutex_destroy(&s_ReceiveMutex);
		pthread_mutex_destroy(&s_SendMutex);
		return 0;
	}

	if(sem_init(&s_SendNotFullSema, 0, 0) != 0)
	{
		pthread_mutex_destroy(&s_ReceiveMutex);
		pthread_mutex_destroy(&s_SendMutex);
		sem_destroy(&s_ReceiveWaitingForConsumptionSema);
		sem_destroy(&s_ReceiveWaitingForDataSema);

		return 0;
	}

	if(pthread_create(&g_ThreadHandle, NULL, sThreadFunc, NULL) != 0)
	{
		pthread_mutex_destroy(&s_ReceiveMutex);
		pthread_mutex_destroy(&s_SendMutex);
		sem_destroy(&s_ReceiveWaitingForConsumptionSema);
		sem_destroy(&s_ReceiveWaitingForDataSema);
		sem_destroy(&s_SendNotFullSema);

		g_ThreadHandle = 0;
		return 0;	/* Failure. */
	}

	return 1;
}

void Java_com_nethackff_NetHackApp_NetHackShutdown(JNIEnv *env, jobject thiz)
{
	if(g_ThreadHandle)
	{
#if 0
		pthread_cancel(g_ThreadHandle);	/* Would return 0 on success. */
#endif

		pthread_mutex_destroy(&s_SendMutex);
		pthread_mutex_destroy(&s_ReceiveMutex);
		sem_destroy(&s_ReceiveWaitingForConsumptionSema);
		sem_destroy(&s_ReceiveWaitingForDataSema);
		sem_destroy(&s_SendNotFullSema);
		g_ThreadHandle = 0;
	}
}


int Java_com_nethackff_NetHackApp_NetHackHasQuit(JNIEnv *env, jobject thiz)
{
	return s_Quit;
}


void Java_com_nethackff_NetHackApp_NetHackTerminalSend(JNIEnv *env, jobject thiz,
		jstring str)
{
	const char *nativestr = (*env)->GetStringUTFChars(env, str, 0);

	pthread_mutex_lock(&s_ReceiveMutex);

	const char *ptr = nativestr;
	for(; *ptr;)
	{
		if(s_ReceiveCnt < RECEIVEBUFFSZ)
		{
			s_ReceiveBuff[s_ReceiveCnt++] = *ptr++;
		}
		else
		{
			s_ReceiveWaitingForConsumption = 1;

			pthread_mutex_unlock(&s_ReceiveMutex);

			sem_wait(&s_ReceiveWaitingForConsumptionSema);

			pthread_mutex_lock(&s_ReceiveMutex);
		}
	}

	if(s_ReceiveWaitingForData)
	{
		s_ReceiveWaitingForData = 0;
		sem_post(&s_ReceiveWaitingForDataSema);
	}

	pthread_mutex_unlock(&s_ReceiveMutex);

	(*env)->ReleaseStringUTFChars(env, str, nativestr);
}

jstring Java_com_nethackff_NetHackApp_NetHackTerminalReceive(JNIEnv *env,
		jobject thiz)
{
	pthread_mutex_lock(&s_SendMutex);

	s_SendBuff[s_SendCnt] = '\0';
	jstring str = (*env)->NewStringUTF(env, s_SendBuff);
	s_SendCnt = 0;

	if(s_SendWaitingForNotFull)
	{
		s_SendWaitingForNotFull = 0;
		sem_post(&s_SendNotFullSema);
	}

	pthread_mutex_unlock(&s_SendMutex);

	return str;
}

static void
wd_message()
{
#ifdef WIZARD
	if (wiz_error_flag) {
		pline("Only user \"%s\" may access debug (wizard) mode.",
# ifndef KR1ED
			WIZARD);
# else
			WIZARD_NAME);
# endif
		pline("Entering discovery mode instead.");
	} else
#endif
	if (discover)
		You("are in non-scoring discovery mode.");
}

/* End of file */
