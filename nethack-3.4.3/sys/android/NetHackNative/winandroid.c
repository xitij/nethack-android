/* winandroid.c */

#include "hack.h"

#include "wintty.h"

#include <jni.h>

winid android_create_nhwindow(int type);
void android_clear_nhwindow(winid window);
void android_curs(winid window, int x, int y);
void android_putstr(winid window, int attr, const char *str);

/* Interface definition, for windows.c */
struct window_procs android_procs = {
    "androidtty",
#ifdef MSDOS
    WC_TILED_MAP|WC_ASCII_MAP|
#endif
#if defined(WIN32CON)
    WC_MOUSE_SUPPORT|
#endif
    WC_COLOR|WC_HILITE_PET|WC_INVERSE|WC_EIGHT_BIT_IN,
    0L,
    tty_init_nhwindows,
    tty_player_selection,
    tty_askname,
    tty_get_nh_event,
    tty_exit_nhwindows,
    tty_suspend_nhwindows,
    tty_resume_nhwindows,
	android_create_nhwindow,
	android_clear_nhwindow,
    tty_display_nhwindow,
    tty_destroy_nhwindow,
	android_curs,
	android_putstr,
    tty_display_file,
    tty_start_menu,
    tty_add_menu,
    tty_end_menu,
    tty_select_menu,
    tty_message_menu,
    tty_update_inventory,
    tty_mark_synch,
    tty_wait_synch,
#ifdef CLIPPING
    tty_cliparound,
#endif
#ifdef POSITIONBAR
    tty_update_positionbar,
#endif
    tty_print_glyph,
    tty_raw_print,
    tty_raw_print_bold,
    tty_nhgetch,
    tty_nh_poskey,
    tty_nhbell,
    tty_doprev_message,
    tty_yn_function,
    tty_getlin,
    tty_get_ext_cmd,
    tty_number_pad,
    tty_delay_output,
#ifdef CHANGE_COLOR	/* the Mac uses a palette device */
    tty_change_color,
#ifdef MAC
    tty_change_background,
    set_tty_font_name,
#endif
    tty_get_color_string,
#endif

    /* other defs that really should go away (they're tty specific) */
    tty_start_screen,
    tty_end_screen,
    genl_outrip,
#if defined(WIN32CON)
    nttty_preference_update,
#else
    genl_preference_update,
#endif
};

void android_wininit_data(int *argcp, char **argv)	/* should we have these params? */
{
	win_tty_init();
}

#if 0

void android_askname()
{
	plname[0] = 'a';
	plname[1] = 'n';
	plname[2] = 'd';
	plname[3] = 'r';
	plname[4] = 'o';
	plname[5] = 'i';
	plname[6] = 'd';
	plname[7] = '\0';
}

#endif

static int s_ScreenNumColumns = 80;
static int s_NumMsgLines = 1;

void Java_com_nethackff_NetHackApp_NetHackSetScreenDim(
		JNIEnv *env, jobject thiz, int width, int nummsglines)
{
	s_ScreenNumColumns = width;
	s_NumMsgLines = nummsglines;
}


static int s_MsgCol = 0;
static int s_MsgRow = 0;




/*
extern struct WinDesc *wins[MAXWIN];
*/
winid android_create_nhwindow(int type)
{
	winid newid = tty_create_nhwindow(type);

	if(newid >= 0 && newid < MAXWIN)
	{
		struct WinDesc *newwin = wins[newid];
		if(newwin)
		{
			if(newwin->type == NHW_STATUS)
			{
				newwin->offy = 0;
			}
		}
	}

	return newid;
}


void android_clear_nhwindow(winid window)
{
	struct WinDesc *cw = wins[window];

	if(cw && cw->type == NHW_MESSAGE)
	{
		android_puts("\033A1");
		android_puts("\033[H\033[J");
		s_MsgCol = 0;
		s_MsgRow = 0;
		android_puts("\033A0");
		return;
	}
	else if(cw && cw->type == NHW_STATUS)
	{
		android_puts("\033A2");
		android_puts("\033[H\033[J");
		android_puts("\033A0");
		return;
	}
	tty_clear_nhwindow(window);
}


void android_curs(winid window, int x, int y)
{
	struct WinDesc *cw = wins[window];
	if(cw && cw->type == NHW_MESSAGE)
	{
		/* HACK */
		int oldco = CO;
		CO = s_ScreenNumColumns;

		android_puts("\033A1");
		tty_curs(window, x, y);
		android_puts("\033A0");

		CO = oldco;
		return;
	}
	else if(cw && cw->type == NHW_STATUS)
	{
		android_puts("\033A2");
#if 0
		int oldco = CO;
		CO = s_ScreenNumColumns;
		tty_curs(window, x, y);
		CO = oldco;
#endif
		cmov(x - 1, y);

		android_puts("\033A0");
		return;
	}
	tty_curs(window, x, y);
}

#if 0

#endif

static void android_putstr_status(struct WinDesc *cw, const char *str)
{
	/* Adapted from tty_putstr(). */

	int j;
	char *ob;

	android_puts("\033A2");

	ob = &cw->data[cw->cury][j = cw->curx];
	if(flags.botlx) *ob = 0;

	if(!cw->cury && (int)strlen(str) >= s_ScreenNumColumns)
	{
		const char *nb;

	    /* the characters before "St:" are unnecessary */
	    nb = index(str, ':');
	    if(nb && nb > str+2)
			str = nb - 2;
	}
#if 0
	nb = str;
	for(i = cw->curx + 1, n0 = cw->cols; i < n0; i++, nb++)
	{
	    if(!*nb)
		{
			if(*ob || flags.botlx)
			{
			    /* last char printed may be in middle of line */
			 	android_curs(WIN_STATUS, i, cw->cury);
			    cl_end();
			}
			break;
	    }
	    if(*ob != *nb)
			tty_putsym(WIN_STATUS, i, cw->cury, *nb);
	    if(*ob)
			ob++;
	}
#endif

	android_puts(str);
	cl_end();

	/* Note: not sure exactly if there really is a point to storing
	   the current contents here. */
	(void)strncpy(&cw->data[cw->cury][j], str, cw->cols - j - 1);
	cw->data[cw->cury][cw->cols-1] = '\0'; /* null terminate */
	cw->cury = (cw->cury+1) % 2;
	cw->curx = 0;

	android_puts("\033A0");
}


static void android_redotoplin(const char *str)
{
	int otoplin = ttyDisplay->toplin;

#if 0
	home();
	if(*str & 0x80) {
		/* kludge for the / command, the only time we ever want a */
		/* graphics character on the top line */
		g_putch((int)*str++);
		ttyDisplay->curx++;
	}
	end_glyphout();	/* in case message printed during graphics output */
#endif
	clear_screen();
	putsyms(str);
	cl_end();
	ttyDisplay->toplin = 1;
	if(ttyDisplay->cury && otoplin != 3)
		more();
}


static void android_addtopl(const char *s)
{
    register struct WinDesc *cw = wins[WIN_MESSAGE];

#if 0
    tty_curs(BASE_WINDOW,cw->curx+1,cw->cury);
#endif
    putsyms(s);
    cl_end();
    ttyDisplay->toplin = 1;
}


static void android_update_topl_word(const char *wordstart,
		const char *wordend)
{
	char buff[256];

	const int wordlen = wordend - wordstart;
	if(s_MsgCol > 0)
	{
		int maxcol = s_ScreenNumColumns;
#if 0
		if(s_MsgRow == s_NumMsgLines - 1)
		{
			maxcol -= 8;	/* Room for --More-- */
		}
#endif

		if(s_MsgCol + wordlen + 1 > maxcol)
		{
#if 0
			if(s_MsgRow == s_NumMsgLines - 1)
			{
				android_puts("--More--");
				s_MsgCol += 8;
			}
#endif
			/* The word doesn't fit, advance to the next line, unless
			   we just wrapped around anyway. */
			if(s_MsgCol != s_ScreenNumColumns)
			{
				android_puts("\n");
			}
			s_MsgCol = 0;
			s_MsgRow++;
		}
		else
		{
			android_puts(" ");
			s_MsgCol++;
		}
	}

	strncpy(buff, wordstart, sizeof(buff) - 1);
	if(wordlen < sizeof(buff))
	{
		buff[wordlen] = '\0';
	}
	else
	{
		buff[sizeof(buff) - 1] = '\0';
	}
	android_puts(buff);
	s_MsgCol += strlen(buff);
	while(s_MsgCol >= s_ScreenNumColumns)
	{
		s_MsgCol -= s_ScreenNumColumns;
		s_MsgRow++;
	}

	if(s_MsgRow >= s_NumMsgLines)
	{
		s_MsgRow = s_NumMsgLines - 1;
	}
}

/* Hopefully temporary */
int g_android_prevent_output = 0;

static void android_update_topl(struct WinDesc *cw, const char *str)
{
	int i;
	const char *ptr;
	const char *wordstart = NULL;
	int foundend = 0;
	int continued = 0;

while(!foundend)
{
	ptr = str;
	if(s_MsgRow < s_NumMsgLines - 1 || continued)
	{
		wordstart = NULL;
		while(1)
		{
			char c = *ptr++;
			if(c == ' ' || !c)
			{
				if(wordstart)
				{
					if(continued && (s_MsgRow == s_NumMsgLines - 1))
					{
#if 0
						if(s_MsgCol + ptr - 1 - wordstart >= s_ScreenNumColumns - 8)
#endif
						if(s_MsgCol + ptr - wordstart >= s_ScreenNumColumns - 8)
						{
							ptr = wordstart;
							break;
						}
					}
					android_update_topl_word(wordstart, ptr - 1);
/* NOT SURE */
continued = 1;
					if(s_MsgRow >= s_NumMsgLines - 1 && !continued)
					{
						break;
					}
				}
				wordstart = NULL;

				if(!c)
				{
					foundend = 1;
					break;
				}
			}
			else if(!wordstart)
			{
				wordstart = ptr - 1;
			}
		}
	}

	if(!foundend)
	{
		str = ptr;

		const int len = strlen(str);
		int lastcol = s_MsgCol + len - 1;
		if(s_MsgCol)
		{
			lastcol++;
		}
		if(lastcol < s_ScreenNumColumns - 8)	/* Room for --More-- */
		{
			if(s_MsgCol)
			{
				android_puts(" ");
				s_MsgCol++;
			}
			android_puts(str);
			s_MsgCol += len;
			foundend = 1;
		}
		else
		{
			android_puts("--More--");

			s_MsgCol += 8;

			g_android_prevent_output++;
			xwaitforspace("\033 ");
			g_android_prevent_output--;

			android_puts("\033[H\033[J");

			s_MsgCol = 0;
			s_MsgRow = 0;
			continued = 1;
		}
	}
}


#if 0
	register const char *bp = str;
	register char *tl, *otl;
	register int n0;
	int notdied = 1;

	/* If there is room on the line, print message on same line */
	/* But messages like "You die..." deserve their own line */
	n0 = strlen(bp);
	if ((ttyDisplay->toplin == 1 || (cw->flags & WIN_STOP)) &&
	    cw->cury == 0 &&
	    n0 + (int)strlen(toplines) + 3 < CO-8 &&  /* room for --More-- */
	    (notdied = strncmp(bp, "You die", 7))) {
		Strcat(toplines, "  ");
		Strcat(toplines, bp);
		cw->curx += 2;
		if(!(cw->flags & WIN_STOP))
		    android_addtopl(bp);
		return;
	} else if (!(cw->flags & WIN_STOP)) {
	    if(ttyDisplay->toplin == 1) more();
	    else if(cw->cury) {	/* for when flags.toplin == 2 && cury > 1 */
		docorner(1, cw->cury+1); /* reset cury = 0 if redraw screen */
		cw->curx = cw->cury = 0;/* from home--cls() & docorner(1,n) */
	    }
	}
#if 0
	remember_topl();
#endif
	(void) strncpy(toplines, bp, TBUFSZ);
	toplines[TBUFSZ - 1] = 0;

	for(tl = toplines; n0 >= CO; ){
	    otl = tl;
	    for(tl+=CO-1; tl != otl && !isspace(*tl); --tl) ;
	    if(tl == otl) {
		/* Eek!  A huge token.  Try splitting after it. */
		tl = index(otl, ' ');
		if (!tl) break;    /* No choice but to spit it out whole. */
	    }
	    *tl++ = '\n';
	    n0 = strlen(tl);
	}
	if(!notdied) cw->flags &= ~WIN_STOP;
#if 0
	if(!(cw->flags & WIN_STOP)) android_redotoplin(toplines);
#endif

#endif
}

void android_putstr_message(struct WinDesc *cw, const char *str)
{
	android_puts("\033A1");

#if 0
	android_puts(str);
#endif
	android_update_topl(cw, str);

	android_puts("\033A0");

}


void android_putstr(winid window, int attr, const char *str)
{
	struct WinDesc *cw = wins[window];

#if 0
	if(cw && cw->type == NHW_MESSAGE)
{
android_puts("\033A3");
char buff3[256];
sprintf(buff3, "[win=%d]", (int)cw->type);
android_puts(buff3);
android_puts(str);
android_puts("\033A0");
}
#endif

	if(cw && cw->type == NHW_MESSAGE)
	{
#if 0
		update_topl(str);
#else

#if 0
		/* HACK */
		int oldco = CO;
		CO = s_ScreenNumColumns;

		android_puts("\033A1");
		update_topl(str);
		android_puts("\033A0");

		CO = oldco;
#else

#if 0
		char buff[256];
		snprintf(buff, sizeof(buff), ":%d:", s_MsgCol + 1);
		android_putstr_message(cw, buff);
#endif

		android_putstr_message(cw, str);
#endif

#endif
		return;
	}
	else if(cw && cw->type == NHW_STATUS)
	{
		android_putstr_status(cw, str);
		return;
	}
	tty_putstr(window, attr, str);
}

/* End of file winandroid.c */
