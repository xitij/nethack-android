#include "hack.h"
#include "dlb.h"
#include "wintty.h"

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

static int s_PlayerPosShouldRecenter = 0;	/* Protected by s_ReceiveMutex */
static int s_PlayerPosX = 0;				/* Protected by s_ReceiveMutex */
static int s_PlayerPosY = 0;				/* Protected by s_ReceiveMutex */
static d_level s_PrevDungeonLevel;

static AndroidGameState s_GameState = kAndroidGameStateInvalid;

enum
{
	kGameStateStackSize = 1
};
static AndroidGameState s_GameStateStack[kGameStateStackSize];
static int s_GameStateStackCount = 0;

enum
{
	kCmdNone,
	kCmdSave,
	kCmdRefresh,
	kCmdSwitchTo128,
	kCmdSwitchToAmiga,
	kCmdSwitchToIBM
};

enum
{
	kCharSet128,
	kCharSetIBM,
	kCharSetAmiga
};

static int s_CurrentCharSet = kCharSet128;
static int s_InitCharSet = kCharSet128;

static int s_ReadyForSave = 0;
static int s_Command = kCmdNone;
static int s_Quit = 0;
int g_AndroidPureTTY = 0;

static void NDECL(wd_message);
#ifdef WIZARD
static boolean wiz_error_flag = FALSE;
#endif

#if 0	/* Enable for debug output */

void android_debuglog(const char *fmt, ...)
{
	char buff[1024];
	int r;

	va_list args;
	va_start(args, fmt);
	r = vsnprintf(buff, sizeof(buff), fmt, args);
	va_end(args);

	android_puts("\033A3");
	android_puts(buff);
	android_puts("\033A0");
}


void android_debugerr(const char *fmt, ...)
{
	/* TODO: Make this more sophisticated. */

	char buff[1024];
	int r;

	va_list args;
	va_start(args, fmt);
	r = vsnprintf(buff, sizeof(buff), fmt, args);
	va_end(args);

	android_puts("\033A3");
	android_puts(buff);
	android_puts("\033A0");
}

#else

void android_debuglog(const char *fmt, ...)
{
}

void android_debugerr(const char *fmt, ...)
{
}

#endif


void error(const char *s, ...)
{
	char buff[1024];

	va_list args;
	va_start(args, s);

/*
	if(settty_needed)
		settty((char *)0);
*/
	vsnprintf(buff, sizeof(buff), s, args);
	android_putchar('\n');
	android_puts(buff);
	android_putchar('\n');

	/* Sleep for a bit so we see something even if the user is typing.*/
	sleep(1);
	getchar();

	exit(1);
}

AndroidGameState android_getgamestate()
{
	return s_GameState;
}


static const char *s_statenames[] =
{
	"Invalid",
	"ExtCmd",
	"Init",
	"Menu",
	"MoveLoop",
	"Text",
	"WaitingForResponse"
};


void android_switchgamestate(AndroidGameState s)
{
	if(s != s_GameState)
	{
		const char *statename;
		if(s >= 0 && s < kAndroidNumGameStates)
		{
			statename = s_statenames[s];
		}
		else
		{
			statename = "?";
		}
		android_debuglog("Switching state to %s.", statename);
	}
	s_GameState = s;
}



void android_pushgamestate(AndroidGameState s)
{
	if(s_GameStateStackCount < kGameStateStackSize)
	{
		const char *statename;
		if(s >= 0 && s < kAndroidNumGameStates)
		{
			statename = s_statenames[s];
		}
		else
		{
			statename = "?";
		}

		android_debuglog("Pushing state %s.", statename);
		s_GameStateStack[s_GameStateStackCount++] = s_GameState;

		s_GameState = s;
	}
	else
	{
		android_debugerr("Game state stack full.");
	}
}


void android_popgamestate()
{
	if(s_GameStateStackCount >= 0)
	{
		s_GameState = s_GameStateStack[--s_GameStateStackCount];

		const char *statename;
		if(s_GameState >= 0 && s_GameState < kAndroidNumGameStates)
		{
			statename = s_statenames[s_GameState];
		}
		else
		{
			statename = "?";
		}
		android_debuglog("Popping state back to %s.", statename);
	}
	else
	{
		android_debugerr("Popped from empty stack.");
	}
}


void android_setcharset(int charsetindex)
{
	switch(charsetindex)
	{
		case kCharSetAmiga:
			read_config_file("charset_amiga.cnf");
			s_CurrentCharSet = kCharSetAmiga;
			break;
		case kCharSetIBM:
			read_config_file("charset_ibm.cnf");
			s_CurrentCharSet = kCharSetIBM;
			break;
		case kCharSet128:
			read_config_file("charset_128.cnf");
			s_CurrentCharSet = kCharSet128;
			break;
		default:
			break;
	}
}


struct AndroidUnicodeRemap
{
	char		ascii;
	uint16_t	unicode;
};

static const struct AndroidUnicodeRemap s_IbmGraphicsRemap[] =
{
	{	0xb3, 0x2502	},
	{	0xc4, 0x2500	},
	{	0xda, 0x250c	},
	{	0xbf, 0x2510	},
	{	0xc0, 0x2514	},
	{	0xd9, 0x2518	},
	{	0xc5, 0x253c	},
	{	0xc1, 0x2534	},
	{	0xc2, 0x252c	},
	{	0xb4, 0x2524	},
	{	0xc3, 0x251c	},
/*	{	0xb0, 0x2591	}, 		Missing in Droid monospace font? */
/*	{	0xb0, '#'		},*/
	{	0xb0, 0x7000 + 256	},	/* Use extra char from "Amiga" font. */
	{	0xb1, 0x2592	},
	{	0xf0, 0x2261	},
	{	0xf1, 0x00b1	},
/*	{	0xf4, 0x2320	},		Missing in Droid monospace font? */
/*	{	0xf4, '{'		}, */
	{	0xf4, 0x7000 + 257	},	/* Use extra char from "Amiga" font. */
	{	0xf7, 0x2248	},
	{	0xfa, 0x00b7	},
	{	0xfe, 0x25a0	},

	{	0xba, 0x2551	},
	{	0xcd, 0x2550	},
	{	0xc9, 0x2554	},
	{	0xbb, 0x2557	},
	{	0xc8, 0x255a	},
	{	0xbc, 0x255d	},
	{	0xce, 0x256c	},
	{	0xca, 0x2569	},
	{	0xcb, 0x2566	},
	{	0xb9, 0x2563	},
	{	0xcc, 0x2560	},
/*	{	0x01, 0x263b	},	Missing from font */
	{	0x01, 0x7000 + 258	},
	{	0x04, 0x2666	},	/* Working */
	{	0x05, 0x2663	},	/* Working */
	{	0x0c, 0x2640	},	/* Working */
/*	{	0x0e, 0x266b	},	Missing */
	{	0x0e, 0x7000 + 259	},
/*	{	0x0f, 0x263c	},	Missing */
	{	0x0f, 0x7000 + 260	},
	{	0x18, 0x2191	},	/* Working */
	{	0xad, 0xa1		},	/* Working */
	{	0xb2, 0x2593	},	/* Working */
	{	0xe7, 0x3c4		},	/* Working */



	{	0x00, 0x0000	}
};

static void android_putchar_internal(int c)
{
	while(1)
	{
		pthread_mutex_lock(&s_SendMutex);

		uint16_t unicode = c;

		if(s_CurrentCharSet != kCharSetAmiga)
		{
			if(c >= 128 || c < 32)
			{
				int found = 0;

				/* Here, we map the relevant extended characters from MSDOS
					to their Unicode equivalent. */

				const struct AndroidUnicodeRemap *remapPtr = s_IbmGraphicsRemap;

				unicode = 0xbf;	/* Inverted question mark to indicate unknown */

				/* TODO: Would be nice with binary search here. */
				while(remapPtr->ascii)
				{
					if(remapPtr->ascii == c)
					{
						unicode = remapPtr->unicode;
						found = 1;
						break;
					}
					remapPtr++;
				}

				if(c < 32 && !found)
				{
					unicode = c;	/* Preserve \n, etc. */
				}
			}
		}
		else
		{
			if(c >= 128)
			{
				unicode = 0x7000 + (unsigned int)c;
			}
		}

		/* Store the Unicode in the buffer using UTF8 encoding. Note:
		   we could potentially just store them with 2 byte per character
		   Unicode in the array. */

		if(unicode >= 0x800)
		{
			if(s_SendCnt < SENDBUFFSZ - 2)
			{
				unsigned char c1 = 0xe0 + ((unicode & 0xf000) >> 12);
				unsigned char c2 = 0x80 + ((unicode & 0x0f00) >> 6) + ((unicode & 0x00c0) >> 6);
				unsigned char c3 = 0x80 + (unicode & 0x003f);

				s_SendBuff[s_SendCnt++] = (char)c1;
				s_SendBuff[s_SendCnt++] = (char)c2;
				s_SendBuff[s_SendCnt++] = (char)c3;

				pthread_mutex_unlock(&s_SendMutex);

				return;
			}
		}
		else if(unicode >= 0x80)
		{
			if(s_SendCnt < SENDBUFFSZ - 1)
			{
				unsigned char c1 = 0xc0 + ((unicode & 0x0700) >> 6) + ((unicode & 0x00c0) >> 6);
				unsigned char c2 = 0x80 + (unicode & 0x003f);

				s_SendBuff[s_SendCnt++] = (char)c1;
				s_SendBuff[s_SendCnt++] = (char)c2;

				pthread_mutex_unlock(&s_SendMutex);

				return;
			}

		}
		else
		{
			if(s_SendCnt < SENDBUFFSZ)
			{
				s_SendBuff[s_SendCnt++] = (char)unicode;

				pthread_mutex_unlock(&s_SendMutex);

				return;
			}
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

/* If we need the status lines to redraw, we may need to clear out the
   previous state like this - if not, characters may not be reprinted if
   android_putstr_status() thinks they are already there.
*/
static void android_clear_winstatus_state()
{
	struct WinDesc *cw = wins[WIN_STATUS];
	int i, j;

	if(cw)
	{
		for(i = 0; i < cw->maxrow; i++)
		{
			if(cw->data[i])
			{
				for(j = 0; j < cw->maxcol; j++)
				{
					cw->data[i][j] = '\0';	/* Should this be ' ' instead? */
				}
			}
		}
	}
}

/*
extern int g_android_prevent_output;
*/
static int s_ShouldRefresh = 0;
static int s_SwitchCharSetCmd = -1;

int android_getch(void)
{
	while(1)
	{
		pthread_mutex_lock(&s_ReceiveMutex);

		/* Request recentering on the player if the dungeon level changed. */
		if(s_PrevDungeonLevel.dnum != u.uz.dnum || s_PrevDungeonLevel.dlevel != u.uz.dlevel)
		{
			s_PrevDungeonLevel.dnum = u.uz.dnum;
			s_PrevDungeonLevel.dlevel = u.uz.dlevel;
			s_PlayerPosShouldRecenter = 1;
		}

		s_PlayerPosX = u.ux;
		s_PlayerPosY = u.uy;

		/* Not sure */
		if(s_Command != kCmdNone)	/* && !g_android_prevent_output)*/
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

				if(s_ReadyForSave)
				{
					android_autosave_save();
				}
			}
			else if(cmd == kCmdRefresh)
			{
				s_ShouldRefresh = 1;
			}
			else if(cmd == kCmdSwitchToAmiga ||
					cmd == kCmdSwitchToIBM ||
					cmd == kCmdSwitchTo128)
			{
				s_SwitchCharSetCmd = cmd;
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

		if(s_SwitchCharSetCmd >= 0)
		{
			if(android_getgamestate() == kAndroidGameStateMoveLoop)
			{
				int cmd = s_SwitchCharSetCmd;
				s_SwitchCharSetCmd = -1;

				pthread_mutex_unlock(&s_ReceiveMutex);

				switch(cmd)
				{
					case kCmdSwitchToAmiga:
						android_setcharset(kCharSetAmiga);
						break;
					case kCmdSwitchToIBM:
						android_setcharset(kCharSetIBM);
						break;
					case kCmdSwitchTo128:
						android_setcharset(kCharSet128);
						break;
					default:
						break;
				}

				if(s_ReceiveCnt < RECEIVEBUFFSZ)
				{
					s_ReceiveBuff[s_ReceiveCnt++] = 18;	/* ^R */
				}

				continue;
			}
		}

		if(s_ShouldRefresh)
		{
			if(android_getgamestate() == kAndroidGameStateMoveLoop)
			{
				s_ShouldRefresh = 0;

				pthread_mutex_unlock(&s_ReceiveMutex);

				if(!g_AndroidPureTTY)
				{
					android_clear_winstatus_state();
				}

				bot();

				continue;
			}
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
char *g_NetHackDir = NULL;	// "/data/data/com.nethackff/nethackdir"

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

	android_switchgamestate(kAndroidGameStateInit);

	if(g_NetHackDir)
	{
		chdir(g_NetHackDir);
	}

	if(g_AndroidPureTTY)
	{
		choose_windows("tty");
	}
	else
	{
		choose_windows(DEFAULT_WINDOW_SYS);
	}

	if(!g_AndroidPureTTY)
	{
		/* As far as the Java side is concerned, we actually pretend to
		   use the menu window for the startup screen. This is done to get
		   word wrapping if needed.
		*/
		android_puts("\033A4\033AS");
	}

	initoptions();

	init_nhwindows(&argc,argv);

	android_autosave_restore();

	if(!*plname || !strncmp(plname, "player", 4)
		    || !strncmp(plname, "games", 4)) {
		askname();
	}

	int charset = s_InitCharSet;
	s_InitCharSet = -1;
	android_setcharset(charset);

	android_makelock();

	getlock();

	/* When asked if an old game with the same name should be destroyed,
	   I had some issue where the cursor didn't reset to the beginning of the
	   line. Simply printing an extra line break here probably takes care of
	   that issue. */
	printf("\n");

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

	if(!g_AndroidPureTTY)
	{
		/* Since we pretend to use a menu window, we need to tell the Java
		   side of the UI to go back to the main view now, and close the
		   menu window. */
		android_puts("\033A4\033AH");
		android_puts("\033A0");
	}


		check_special_room(FALSE);
		wd_message();

		if (discover || wizard) {
			if(yn("Do you want to keep the save file?") == 'n')
			    (void) delete_savefile();
			else {
#ifndef FILE_AREAS
			    (void) chmod(fq_save,FCMASK); /* back to readable */
			    compress_area(NULL, fq_save);
#else
			    (void) chmod_area(FILE_AREA_SAVE, SAVEF, FCMASK);
			    compress_area(FILE_AREA_SAVE, SAVEF);
#endif
			}
		}
		flags.move = 0;
	} else {
not_recovered:
		player_selection();

	if(!g_AndroidPureTTY)
	{
		/* Since we pretend to use a menu window, we need to tell the Java
		   side of the UI to go back to the main view now, and close the
		   menu window. */
		android_puts("\033A4\033AH");
		android_puts("\033A0");
	}


		newgame();
		wd_message();

		flags.move = 0;
		set_wear();
		(void) pickup(1);
	}

	s_ReadyForSave = 1;
	android_switchgamestate(kAndroidGameStateMoveLoop);

	pthread_mutex_lock(&s_ReceiveMutex);
	s_PlayerPosShouldRecenter = 1;
	s_PrevDungeonLevel.dnum = u.uz.dnum;
	s_PrevDungeonLevel.dlevel = u.uz.dlevel;
	pthread_mutex_unlock(&s_ReceiveMutex);

	moveloop();

	nethack_exit(EXIT_SUCCESS);

	return(0);
}

int Java_com_slashemff_NetHackApp_NetHackInit(JNIEnv *env, jobject thiz,
		int puretty, jstring nethackdir)
{
	char *p;
	int x, y;
	const char *nethackdirnative;

	if(g_ThreadHandle)
	{
		return 0;
	}

	nethackdirnative = (*env)->GetStringUTFChars(env, nethackdir, 0);
	if(g_NetHackDir)
	{
		/* This shouldn't happen, but probably best to deal with it in case. */
		free((void*)g_NetHackDir);
		g_NetHackDir = NULL;
	}
	g_NetHackDir = malloc(strlen(nethackdirnative + 1));
	strcpy(g_NetHackDir, nethackdirnative);
	(*env)->ReleaseStringUTFChars(env, nethackdir, nethackdirnative);

	g_AndroidPureTTY = puretty;
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

void Java_com_slashemff_NetHackApp_NetHackShutdown(JNIEnv *env, jobject thiz)
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

	if(g_NetHackDir)
	{
		free((void*)g_NetHackDir);
		g_NetHackDir = NULL;
	}
}


int Java_com_slashemff_NetHackApp_NetHackHasQuit(JNIEnv *env, jobject thiz)
{
	return s_Quit;
}

extern int dosave0();

int Java_com_slashemff_NetHackApp_NetHackSave(JNIEnv *env, jobject thiz)
{
	if(!s_ReadyForSave)
	{
		return 0;
	}

	sSendCmd(kCmdSave, 1);

	return 1;
}


void Java_com_slashemff_NetHackApp_NetHackRefreshDisplay(
		JNIEnv *env, jobject thiz)
{
	/* Do we need to do anything to check if we are in a state where
	   this is appropriate? */
#if 0
	doredraw();
    flags.botlx = 1;	/* force a redraw of the bottom line */
	g_android_refresh = 1;
#endif

	sSendCmd(kCmdRefresh, 0);
}


void Java_com_slashemff_NetHackApp_NetHackSwitchCharSet(
		JNIEnv *env, jobject thiz, int charset)
{
	int cmd = -1;

	if(s_InitCharSet >= 0)
	{
		s_InitCharSet = charset;
		return;
	}

	switch(charset)
	{
		case kCharSet128:
			cmd = kCmdSwitchTo128;
			break;
		case kCharSetIBM:
			cmd = kCmdSwitchToIBM;
			break;
		case kCharSetAmiga:
			cmd = kCmdSwitchToAmiga;
			break;
		default:
			break;
	}

	if(cmd < 0)
	{
		return;
	}

	sSendCmd(cmd, 0);
}


int Java_com_slashemff_NetHackApp_NetHackGetPlayerPosX(JNIEnv *env,
		jobject thiz)
{
	int ret;

	pthread_mutex_lock(&s_ReceiveMutex);

	ret = s_PlayerPosX;

	pthread_mutex_unlock(&s_ReceiveMutex);

	return ret;
}


int Java_com_slashemff_NetHackApp_NetHackGetPlayerPosY(JNIEnv *env,
		jobject thiz)
{
	int ret;

	pthread_mutex_lock(&s_ReceiveMutex);

	ret = s_PlayerPosY;

	pthread_mutex_unlock(&s_ReceiveMutex);

	return ret;
}


int Java_com_slashemff_NetHackApp_NetHackGetPlayerPosShouldRecenter(JNIEnv *env,
		jobject thiz)
{
	int ret;

	pthread_mutex_lock(&s_ReceiveMutex);

	ret = s_PlayerPosShouldRecenter;

	/* Not sure: */
	s_PlayerPosShouldRecenter = 0;

	pthread_mutex_unlock(&s_ReceiveMutex);

	return ret;
}


void Java_com_slashemff_NetHackApp_NetHackTerminalSend(JNIEnv *env, jobject thiz,
		jstring str)
{
	const char *nativestr = (*env)->GetStringUTFChars(env, str, 0);

	pthread_mutex_lock(&s_ReceiveMutex);

	const char *ptr = nativestr;
	for(; *ptr;)
	{
		if(s_ReceiveCnt < RECEIVEBUFFSZ)
		{
			unsigned int c = *ptr++;

			/* For the meta keys to work, we need to convert the UTF
			   encoding back to 8 bit chars, which we do here. */
			if((c & 0xe0) == 0xc0)
			{
				unsigned int d = *ptr++;
				c = ((c << 6) & 0xc0) | (d & 0x3f);
			}

			s_ReceiveBuff[s_ReceiveCnt++] = (char)c;
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

jstring Java_com_slashemff_NetHackApp_NetHackTerminalReceive(JNIEnv *env,
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
