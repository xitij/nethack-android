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
static sem_t s_CommandPerformedSema;

static int s_SendWaitingForNotFull;
static int s_ReceiveWaitingForData;
static int s_ReceiveWaitingForConsumption;
static int s_WaitingForCommandPerformed;

enum
{
	kCmdNone = 0,
	kCmdSave = 1
};

static int s_ReadyForSave = 0;
static int s_Command = kCmdNone;
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



void android_makelock()
{
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
#ifdef WIZARD
	} else {
		Sprintf(lock, "%d%s", (int)getuid(), plname);
	}
#endif /* WIZARD */
}




/* TEMP */
#if 1
#define SAVESIZE	(PL_NSIZ + 13)	/* save/99999player.e */
char savename[SAVESIZE]; /* holds relative path of save file from playground */
int FDECL(TMP_restore_savefile, (char *));
int TMP_main(int argc, char *argv[]);
#endif

#define AUTOSAVE_FILENAME "android_autosave.txt"

void android_autosave_save()
{
	FILE *f = fopen(AUTOSAVE_FILENAME, "w");
	if(f)
	{
		fprintf(f, "%s\n", plname);
		fclose(f);
	}

#ifdef INSURANCE
	save_currentstate();
#else
# error "Can't save without INSURANCE."
#endif
}


void android_autosave_restore()
{
	char name[PL_NSIZ + 1];
	int found = 0;

	FILE *f = fopen(AUTOSAVE_FILENAME, "r");
	if(!f)
	{
		return;
	}
	if(fgets(name, PL_NSIZ + 1, f))
	{
		char *ptr = name + strlen(name);
		while(ptr >= name)
		{
			char c = *ptr;
			if(c && c != ' ' && c != '\n' && c != '\t' && c != '\r')
			{
				found = 1;
				ptr[1] = '\0';
				break;
			}
			ptr--;
		}
	}
	fclose(f);

	if(!found)
	{
		return;
	}

	strncpy(plname, name, PL_NSIZ - 1);
	plname[PL_NSIZ - 1] = '\0';

	android_makelock();

	if(TMP_restore_savefile(lock) == 0)
	{
		/* Success */
	}
	else
	{
	}
}


void android_autosave_remove()
{
	remove(AUTOSAVE_FILENAME);
}


int android_getch(void)
{
	while(1)
	{
		pthread_mutex_lock(&s_ReceiveMutex);

		if(s_Command != kCmdNone)
		{
			int cmd = s_Command;
			s_Command = kCmdNone;

			if(s_ReceiveWaitingForConsumption)
			{
				s_ReceiveWaitingForConsumption = 0;
				sem_post(&s_ReceiveWaitingForConsumptionSema);
			}

			pthread_mutex_unlock(&s_ReceiveMutex);

			int shouldterminate = 0;

			if(cmd == kCmdSave)
			{
#if 0
				if(dosave0())
				{
					shouldterminate = 1;
				}
#endif

				android_autosave_save();
			}

			if(s_WaitingForCommandPerformed)
			{
				s_WaitingForCommandPerformed = 0;
				sem_post(&s_CommandPerformedSema);
			}

			if(shouldterminate)
			{
				exit_nhwindows("Be seeing you...");
				terminate(EXIT_SUCCESS);
			}

			continue;
		}

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


static void sSendCmd(int cmd, int sync)
{
	pthread_mutex_lock(&s_ReceiveMutex);

	while(1)
	{
		if(s_Command == kCmdNone)
		{
			s_Command = cmd;

			if(sync)
			{
				s_WaitingForCommandPerformed = 1;
			}

			break;
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

	if(sync)
	{
		sem_wait(&s_CommandPerformedSema);
	}
}


pthread_t g_ThreadHandle;

void nethack_exit(int result)
{
	android_autosave_remove();

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
	char *argv[] = {	"nethack"  	};

	int i = 0;

	char buff[256];

	chdir("/data/data/com.nethackff/nethackdir");

	choose_windows(DEFAULT_WINDOW_SYS);
	initoptions();

	init_nhwindows(&argc,argv);

	android_autosave_restore();

	if(!*plname || !strncmp(plname, "player", 4)
		    || !strncmp(plname, "games", 4)) {
		askname();
	}

	android_makelock();

	getlock();

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

	s_ReadyForSave = 1;
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
	s_ReadyForSave = 0;
	s_WaitingForCommandPerformed = 0;

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

	if(sem_init(&s_CommandPerformedSema, 0, 0) != 0)
	{
		pthread_mutex_destroy(&s_ReceiveMutex);
		pthread_mutex_destroy(&s_SendMutex);
		sem_destroy(&s_ReceiveWaitingForConsumptionSema);
		sem_destroy(&s_ReceiveWaitingForDataSema);
		sem_destroy(&s_SendNotFullSema);

		return 0;
	}

	if(pthread_create(&g_ThreadHandle, NULL, sThreadFunc, NULL) != 0)
	{
		pthread_mutex_destroy(&s_ReceiveMutex);
		pthread_mutex_destroy(&s_SendMutex);
		sem_destroy(&s_ReceiveWaitingForConsumptionSema);
		sem_destroy(&s_ReceiveWaitingForDataSema);
		sem_destroy(&s_SendNotFullSema);
		sem_destroy(&s_CommandPerformedSema);

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
		sem_destroy(&s_CommandPerformedSema);
		g_ThreadHandle = 0;
	}
}


int Java_com_nethackff_NetHackApp_NetHackHasQuit(JNIEnv *env, jobject thiz)
{
	return s_Quit;
}

extern int dosave0();

int Java_com_nethackff_NetHackApp_NetHackSave(JNIEnv *env, jobject thiz)
{
	if(!s_ReadyForSave)
	{
		return 0;
	}

	sSendCmd(kCmdSave, 1);

	return 1;
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

/*------------------------------------------------------------------*/
/* TEMP */

/*	SCCS Id: @(#)recover.c	3.4	1999/10/23	*/
/*	Copyright (c) Janet Walz, 1992.				  */
/* NetHack may be freely redistributed.  See license for details. */

/*
 *  Utility for reconstructing NetHack save file from a set of individual
 *  level files.  Requires that the `checkpoint' option be enabled at the
 *  time NetHack creates those level files.
 */
#include "config.h"
#if !defined(O_WRONLY) && !defined(LSC) && !defined(AZTEC_C)
#include <fcntl.h>
#endif
#ifdef WIN32
#include <errno.h>
#include "win32api.h"
#endif

#ifdef VMS
extern int FDECL(vms_creat, (const char *,unsigned));
extern int FDECL(vms_open, (const char *,int,unsigned));
#endif	/* VMS */

#if 1
int FDECL(TMP_restore_savefile, (char *));
void FDECL(TMP_set_levelfile_name, (int));
int FDECL(TMP_open_levelfile, (int));
int NDECL(TMP_create_savefile);
void FDECL(TMP_copy_bytes, (int,int));
#endif

#ifndef WIN_CE
#define Fprintf	(void)fprintf1
#else
#define Fprintf	(void)nhce_message
static void nhce_message(FILE*, const char*, ...);
#endif

/* TEMP */
int fprintf1(FILE *stream, const char *fmt, ...)
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

#define Close	(void)close


#if defined(EXEPATH)
char *FDECL(exepath, (char *));
#endif

#if defined(__BORLANDC__) && !defined(_WIN32)
extern unsigned _stklen = STKSIZ;
#endif
#if 0
char savename[SAVESIZE]; /* holds relative path of save file from playground */
#endif

int
TMP_main(argc, argv)
int argc;
char *argv[];
{
	int argno;

	const char *dir = (char *)0;
#ifdef AMIGA
	char *startdir = (char *)0;
#endif


	if (!dir) dir = getenv("NETHACKDIR");
	if (!dir) dir = getenv("HACKDIR");
#if defined(EXEPATH)
	if (!dir) dir = exepath(argv[0]);
#endif
	if (argc == 1 || (argc == 2 && !strcmp(argv[1], "-"))) {
	    Fprintf(stderr,
		"Usage: %s [ -d directory ] base1 [ base2 ... ]\n", argv[0]);
#if defined(WIN32) || defined(MSDOS)
	    if (dir) {
	    	Fprintf(stderr, "\t(Unless you override it with -d, recover will look \n");
	    	Fprintf(stderr, "\t in the %s directory on your system)\n", dir);
	    }
#endif
	    exit(EXIT_FAILURE);
	}

	argno = 1;
	if (!strncmp(argv[argno], "-d", 2)) {
		dir = argv[argno]+2;
		if (*dir == '=' || *dir == ':') dir++;
		if (!*dir && argc > argno) {
			argno++;
			dir = argv[argno];
		}
		if (!*dir) {
		    Fprintf(stderr,
			"%s: flag -d must be followed by a directory name.\n",
			argv[0]);
		    exit(EXIT_FAILURE);
		}
		argno++;
	}
#if defined(SECURE) && !defined(VMS)
	if (dir
# ifdef HACKDIR
		&& strcmp(dir, HACKDIR)
# endif
		) {
		(void) setgid(getgid());
		(void) setuid(getuid());
	}
#endif	/* SECURE && !VMS */

#ifdef HACKDIR
	if (!dir) dir = HACKDIR;
#endif

#ifdef AMIGA
	startdir = getcwd(0,255);
#endif
	if (dir && chdir((char *) dir) < 0) {
		Fprintf(stderr, "%s: cannot chdir to %s.\n", argv[0], dir);
		exit(EXIT_FAILURE);
	}

	while (argc > argno) {
		if (TMP_restore_savefile(argv[argno]) == 0)
		{
#if 0
		    Fprintf(stderr, "recovered \"%s\" to %s\n",
			    argv[argno], savename);
#endif
		}
		argno++;
	}
#ifdef AMIGA
	if (startdir) (void)chdir(startdir);
#endif
	exit(EXIT_SUCCESS);
	/*NOTREACHED*/
	return 0;
}

static char TMP_lock[256];

void
TMP_set_levelfile_name(lev)
int lev;
{
	char *tf;

	tf = rindex(TMP_lock, '.');
	if (!tf) tf = TMP_lock + strlen(TMP_lock);
	(void) sprintf(tf, ".%d", lev);
#ifdef VMS
	(void) strcat(tf, ";1");
#endif
}

int
TMP_open_levelfile(lev)
int lev;
{
	int fd;

	TMP_set_levelfile_name(lev);
#if defined(MICRO) || defined(WIN32) || defined(MSDOS)
	fd = open(TMP_lock, O_RDONLY | O_BINARY);
#else
	fd = open(TMP_lock, O_RDONLY, 0);
#endif
	return fd;
}

int
TMP_create_savefile()
{
	int fd;

#if defined(MICRO) || defined(WIN32) || defined(MSDOS)
	fd = open(savename, O_WRONLY | O_BINARY | O_CREAT | O_TRUNC, FCMASK);
#else
	fd = creat(savename, FCMASK);
#endif
	return fd;
}

void
copy_bytes(ifd, ofd)
int ifd, ofd;
{
	char buf[BUFSIZ];
	int nfrom, nto;

	do {
		nfrom = read(ifd, buf, BUFSIZ);
		nto = write(ofd, buf, nfrom);
		if (nto != nfrom) {
			Fprintf(stderr, "file copy failed!\n");
			exit(EXIT_FAILURE);
		}
	} while (nfrom == BUFSIZ);
}

int
TMP_restore_savefile(basename)
char *basename;
{
	int gfd, lfd, sfd;
	int lev, savelev, hpid;
	xchar levc;
	struct version_info version_data;

	/* level 0 file contains:
	 *	pid of creating process (ignored here)
	 *	level number for current level of save file
	 *	name of save file nethack would have created
	 *	and game state
	 */
	(void) strcpy(TMP_lock, basename);
	gfd = TMP_open_levelfile(0);
	if (gfd < 0) {
#if defined(WIN32) && !defined(WIN_CE)
 	    if(errno == EACCES) {
	  	Fprintf(stderr,
			"\nThere are files from a game in progress under your name.");
		Fprintf(stderr,"\nThe files are locked or inaccessible.");
		Fprintf(stderr,"\nPerhaps the other game is still running?\n");
	    } else
	  	Fprintf(stderr,
			"\nTrouble accessing level 0 (errno = %d).\n", errno);
#endif
	    Fprintf(stderr, "Cannot open level 0 for %s.\n", basename);
	    return(-1);
	}
	if (read(gfd, (genericptr_t) &hpid, sizeof hpid) != sizeof hpid) {
	    Fprintf(stderr, "%s\n%s%s%s\n",
	     "Checkpoint data incompletely written or subsequently clobbered;",
		    "recovery for \"", basename, "\" impossible.");
	    Close(gfd);
	    return(-1);
	}
	if (read(gfd, (genericptr_t) &savelev, sizeof(savelev))
							!= sizeof(savelev)) {
	    Fprintf(stderr,
	    "Checkpointing was not in effect for %s -- recovery impossible.\n",
		    basename);
	    Close(gfd);
	    return(-1);
	}
	if ((read(gfd, (genericptr_t) savename, sizeof savename)
		!= sizeof savename) ||
	    (read(gfd, (genericptr_t) &version_data, sizeof version_data)
		!= sizeof version_data)) {
	    Fprintf(stderr, "Error reading %s -- can't recover.\n", TMP_lock);
	    Close(gfd);
	    return(-1);
	}

	/* save file should contain:
	 *	version info
	 *	current level (including pets)
	 *	(non-level-based) game state
	 *	other levels
	 */
	sfd = TMP_create_savefile();
	if (sfd < 0) {
	    Fprintf(stderr, "Cannot create savefile %s.\n", savename);
	    Close(gfd);
	    return(-1);
	}

	lfd = TMP_open_levelfile(savelev);
	if (lfd < 0) {
	    Fprintf(stderr, "Cannot open level of save for %s.\n", basename);
	    Close(gfd);
	    Close(sfd);
	    return(-1);
	}

	if (write(sfd, (genericptr_t) &version_data, sizeof version_data)
		!= sizeof version_data) {
	    Fprintf(stderr, "Error writing %s; recovery failed.\n", savename);
	    Close(gfd);
	    Close(sfd);
	    return(-1);
	}

	copy_bytes(lfd, sfd);
	Close(lfd);
	(void) unlink(TMP_lock);

	copy_bytes(gfd, sfd);
	Close(gfd);
	TMP_set_levelfile_name(0);
	(void) unlink(TMP_lock);

	for (lev = 1; lev < 256; lev++) {
		/* level numbers are kept in xchars in save.c, so the
		 * maximum level number (for the endlevel) must be < 256
		 */
		if (lev != savelev) {
			lfd = TMP_open_levelfile(lev);
			if (lfd >= 0) {
				/* any or all of these may not exist */
				levc = (xchar) lev;
				write(sfd, (genericptr_t) &levc, sizeof(levc));
				copy_bytes(lfd, sfd);
				Close(lfd);
				(void) unlink(TMP_lock);
			}
		}
	}

	Close(sfd);

	return(0);
}

#ifdef EXEPATH
# ifdef __DJGPP__
#define PATH_SEPARATOR '/'
# else
#define PATH_SEPARATOR '\\'
# endif

#define EXEPATHBUFSZ 256
char exepathbuf[EXEPATHBUFSZ];

char *exepath(str)
char *str;
{
	char *tmp, *tmp2;
	int bsize;

	if (!str) return (char *)0;
	bsize = EXEPATHBUFSZ;
	tmp = exepathbuf;
#if !defined(WIN32)
	strcpy (tmp, str);
#else
# if defined(WIN_CE)
	{
	  TCHAR wbuf[EXEPATHBUFSZ];
	  GetModuleFileName((HANDLE)0, wbuf, EXEPATHBUFSZ);
	  NH_W2A(wbuf, tmp, bsize);
	}
# else
	*(tmp + GetModuleFileName((HANDLE)0, tmp, bsize)) = '\0';
# endif
#endif
	tmp2 = strrchr(tmp, PATH_SEPARATOR);
	if (tmp2) *tmp2 = '\0';
	return tmp;
}
#endif /* EXEPATH */

#ifdef AMIGA
#include "date.h"
const char amiga_version_string[] = AMIGA_VERSION_STRING;
#endif

#ifdef WIN_CE
void nhce_message(FILE* f, const char* str, ...)
{
    va_list ap;
	TCHAR wbuf[NHSTR_BUFSIZE];
	char buf[NHSTR_BUFSIZE];

    va_start(ap, str);
	vsprintf(buf, str, ap);
    va_end(ap);

	MessageBox(NULL, NH_A2W(buf, wbuf, NHSTR_BUFSIZE), TEXT("Recover"), MB_OK);
}
#endif

/*recover.c*/


/* End of file */
