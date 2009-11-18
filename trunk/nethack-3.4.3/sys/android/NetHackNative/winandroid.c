/* winandroid.c */

#include "hack.h"

#include "wintty.h"

#include "func_tab.h"	/* extcmdlist */

#include <jni.h>

winid android_create_nhwindow(int type);
void android_clear_nhwindow(winid window);
void android_display_nhwindow(winid window, BOOLEAN_P blocking);
void android_destroy_nhwindow(winid window);
/*
E void FDECL(android_display_nhwindow, (winid, BOOLEAN_P));
*/
void android_curs(winid window, int x, int y);
void android_putstr(winid window, int attr, const char *str);
int android_select_menu(winid window, int how, menu_item **menu_list);
char android_yn_function(const char *query, const char *resp, CHAR_P def);
/*E char FDECL(android_yn_function, (const char *, const char *, CHAR_P));*/
int android_get_ext_cmd();


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
    android_display_nhwindow,
	android_destroy_nhwindow,
	android_curs,
	android_putstr,
    tty_display_file,
    tty_start_menu,
    tty_add_menu,
    tty_end_menu,
/*	tty_select_menu,*/
	android_select_menu,
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
    android_yn_function,
    tty_getlin,
    android_get_ext_cmd,
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

static char winpanicstr[] = "Bad window id %d";


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

#if 0
static AndroidGameState s_PreMenuGameState = kAndroidGameStateInvalid;
#endif

/*
void android_display_nhwindow(winid window, boolean blocking)
*/
void android_display_nhwindow(winid window, BOOLEAN_P blocking)
{
    register struct WinDesc *cw = 0;

    if(window == WIN_ERR || (cw = wins[window]) == (struct WinDesc *) 0)
	{
		panic(winpanicstr,  window);
	}
    if(cw->flags & WIN_CANCELLED)
	{
		return;
	}

	if(cw->type == NHW_MENU || cw->type == NHW_TEXT)
	{
#if 0
if(s_PreMenuGameState == kAndroidGameStateInvalid)
{
		s_PreMenuGameState = android_getgamestate();
}
#endif
		if(!cw->active)
		{
			if(cw->type == NHW_MENU)
			{
				android_pushgamestate(kAndroidGameStateMenu);
			}
			else
			{
				android_pushgamestate(kAndroidGameStateText);
			}
		}

		android_puts("\033A4\033AS");

		cw->active = 1;
		cw->offx = 0;
	    cw->offy = 0;
		clear_screen();

		if (cw->data || !cw->maxrow)
			process_text_window(window, cw);
		else
			process_menu_window(window, cw);
		return;
	}

	tty_display_nhwindow(window, blocking);
}


static void android_dismiss_nhwindow(winid window)
{
	struct WinDesc *cw = 0;

    if(window == WIN_ERR || (cw = wins[window]) == (struct WinDesc *) 0)
	{
		panic(winpanicstr,  window);
	}

	switch(cw->type)
	{
		case NHW_MESSAGE:
			if (ttyDisplay->toplin)
				tty_display_nhwindow(WIN_MESSAGE, TRUE);
		/*FALLTHRU*/
		case NHW_STATUS:
		case NHW_BASE:
		case NHW_MAP:
			/*
			 * these should only get dismissed when the game is going away
			 * or suspending
			 */
			tty_curs(BASE_WINDOW, 1, (int)ttyDisplay->rows-1);
			cw->active = 0;
			break;
	    case NHW_MENU:
	    case NHW_TEXT:
			if(cw->active)
			{
				android_puts("\033A4\033AH");

				if (iflags.window_inited)
				{
					/* otherwise dismissing the text endwin after other windows
					 * are dismissed tries to redraw the map and panics.  since
					 * the whole reason for dismissing the other windows was to
					 * leave the ending window on the screen, we don't want to
					 * erase it anyway.
					 */
/*
					erase_menu_or_text(window, cw, FALSE);
*/
				    clear_screen();
			    }
			    cw->active = 0;

				android_puts("\033A0");

				android_popgamestate();
			}

#if 0
			if(s_PreMenuGameState != kAndroidGameStateInvalid)
			{
				android_setgamestate(s_PreMenuGameState);
				s_PreMenuGameState = kAndroidGameStateInvalid;
			}
#endif
			break;
    }
    cw->flags = 0;
}


void android_destroy_nhwindow(winid window)
{
    register struct WinDesc *cw = 0;

    if(window == WIN_ERR || (cw = wins[window]) == (struct WinDesc *) 0)
	panic(winpanicstr,  window);

    if(cw->active)
	{
		android_dismiss_nhwindow(window);
	}
    if(cw->type == NHW_MESSAGE)
	{
		iflags.window_inited = 0;
		if(cw->type == NHW_MAP)
			clear_screen();
	}

    free_window_info(cw, TRUE);
    free((genericptr_t)cw);
    wins[window] = 0;
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

#if 0

void android_putstr_menu(struct WinDesc *cw, const char *str)
{
	android_puts("\033A4");

	android_puts(str);

	android_puts("\033A0");

}

#endif

void android_putstr_text(winid window, int attr,
		struct WinDesc *cw, const char *str)
{
	int i, n0;
	char *ob;

	android_puts("\033A4");

	android_puts(str);

	if(cw->type == NHW_TEXT && cw->cury == ttyDisplay->rows-1) {
	    /* not a menu, so save memory and output 1 page at a time */
	    cw->maxcol = ttyDisplay->cols; /* force full-screen mode */
		android_display_nhwindow(window, TRUE);
	    for(i=0; i<cw->maxrow; i++)
		if(cw->data[i]){
		    free((genericptr_t)cw->data[i]);
		    cw->data[i] = 0;
		}
	    cw->maxrow = cw->cury = 0;
	}
	/* always grows one at a time, but alloc 12 at a time */
	if(cw->cury >= cw->rows) {
	    char **tmp;

	    cw->rows += 12;
	    tmp = (char **) alloc(sizeof(char *) * (unsigned)cw->rows);
	    for(i=0; i<cw->maxrow; i++)
		tmp[i] = cw->data[i];
	    if(cw->data)
		free((genericptr_t)cw->data);
	    cw->data = tmp;

	    for(i=cw->maxrow; i<cw->rows; i++)
		cw->data[i] = 0;
	}
	if(cw->data[cw->cury])
	    free((genericptr_t)cw->data[cw->cury]);
	n0 = strlen(str) + 1;
	ob = cw->data[cw->cury] = (char *)alloc((unsigned)n0 + 1);
	*ob++ = (char)(attr + 1);	/* avoid nuls, for convenience */
	Strcpy(ob, str);

	if(n0 > cw->maxcol)
	    cw->maxcol = n0;
	if(++cw->cury > cw->maxrow)
	    cw->maxrow = cw->cury;
	if(n0 > CO) {
	    /* attempt to break the line */
	    for(i = CO-1; i && str[i] != ' ' && str[i] != '\n';)
		i--;
	    if(i) {
		cw->data[cw->cury-1][++i] = '\0';
		tty_putstr(window, attr, &str[i]);
	    }
	}

	android_puts("\033A0");

}


void android_putstr(winid window, int attr, const char *str)
{
	struct WinDesc *cw = wins[window];

#if 0
android_debuglog("msg %d: '%s'", cw ? cw->type : -1, str);
#endif

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
#if 1
	else if(cw && cw->type == NHW_TEXT)
	{
		android_puts("\033A4");
#if 0
		tty_putstr(window, attr, str);
#endif
		android_putstr_text(window, attr, cw, str);

		android_puts("\033A0");
		return;
	}
#endif
#if 0
	else if(cw && cw->type == NHW_MENU)
	{
android_debuglog("GOT MENU");
		android_putstr_menu(cw, str);
		return;
	}
#endif
	tty_putstr(window, attr, str);
}


int android_select_menu(winid window, int how, menu_item **menu_list)
{
	/* Adapted from tty_select_menu(). */

	struct WinDesc *cw = 0;
	tty_menu_item *curr;
	menu_item *mi;
	int n, cancelled;

#if 0
	android_puts("\033A4\033AS");
#endif
	if(window == WIN_ERR || (cw = wins[window]) == (struct WinDesc *) 0
			|| cw->type != NHW_MENU)
	{
		panic(winpanicstr,  window);
	}

	*menu_list = (menu_item *) 0;
	cw->how = (short) how;
	morc = 0;

	android_display_nhwindow(window, TRUE);
	cancelled = !!(cw->flags & WIN_CANCELLED);
#if 0
	tty_dismiss_nhwindow(window);	/* does not destroy window data */
#endif
	android_dismiss_nhwindow(window);	/* does not destroy window data */

 	if(cancelled)
	{
		n = -1;
    }
	else
	{
		for (n = 0, curr = cw->mlist; curr; curr = curr->next)
		{
			if(curr->selected)
			{
				n++;
			}
		}
    }

	if(n > 0)
	{
		*menu_list = (menu_item *) alloc(n * sizeof(menu_item));
		for(mi = *menu_list, curr = cw->mlist; curr; curr = curr->next)
		{
			if(curr->selected)
			{
				mi->item = curr->identifier;
				mi->count = curr->count;
				mi++;
	    	}
    	}
	}

	android_puts("\033AH\033A0");

	return n;
}

typedef boolean FDECL((*getlin_hook_proc), (char *));
extern char erase_char, kill_char;	/* from appropriate tty.c file */
STATIC_DCL boolean FDECL(ext_cmd_getlin_hook, (char *));
extern int NDECL(extcmd_via_menu);	/* cmd.c */

STATIC_OVL void
android_hooked_tty_getlin(query, bufp, hook)
const char *query;
register char *bufp;
getlin_hook_proc hook;
{
	register char *obufp = bufp;
	register int c;
	struct WinDesc *cw = wins[WIN_MESSAGE];
	boolean doprev = 0;

	if(ttyDisplay->toplin == 1 && !(cw->flags & WIN_STOP)) more();
	cw->flags &= ~WIN_STOP;
	ttyDisplay->toplin = 3; /* special prompt state */
	ttyDisplay->inread++;
#if 0
	pline("%s ", query);
#endif
	android_printf("%s ", query);
	*obufp = 0;
	for(;;) {
#if 0
		(void) fflush(stdout);
#endif
		Sprintf(toplines, "%s ", query);
		Strcat(toplines, obufp);
		if((c = Getchar()) == EOF) {
			break;
		}
		if(c == '\033') {
			*obufp = c;
			obufp[1] = 0;
			break;
		}
		if (ttyDisplay->intr) {
		    ttyDisplay->intr--;
		    *bufp = 0;
		}
		if(c == '\020') { /* ctrl-P */
		    if (iflags.prevmsg_window != 's') {
			int sav = ttyDisplay->inread;
			ttyDisplay->inread = 0;
			(void) tty_doprev_message();
			ttyDisplay->inread = sav;
			tty_clear_nhwindow(WIN_MESSAGE);
			cw->maxcol = cw->maxrow;
			addtopl(query);
			addtopl(" ");
			*bufp = 0;
			addtopl(obufp);
		    } else {
			if (!doprev)
			    (void) tty_doprev_message();/* need two initially */
			(void) tty_doprev_message();
			doprev = 1;
			continue;
		    }
		} else if (doprev && iflags.prevmsg_window == 's') {
		    tty_clear_nhwindow(WIN_MESSAGE);
		    cw->maxcol = cw->maxrow;
		    doprev = 0;
		    addtopl(query);
		    addtopl(" ");
		    *bufp = 0;
		    addtopl(obufp);
		}
		if(c == erase_char || c == '\b') {
			if(bufp != obufp) {
				char *i;
				bufp--;
				android_puts("\b");
				for (i = bufp; *i; ++i) android_puts(" ");
				for (; i > bufp; --i) android_puts("\b");
				*bufp = 0;
			} else	tty_nhbell();
		} else if(c == '\n') {
			break;
		} else if(' ' <= (unsigned char) c && c != '\177' &&
			    (bufp-obufp < BUFSZ-1 && bufp-obufp < COLNO)) {
				/* avoid isprint() - some people don't have it
				   ' ' is not always a printing char */
			char *i = eos(bufp);
			*bufp = c;
			bufp[1] = 0;
			android_puts(bufp);
			bufp++;
			if (hook && (*hook)(obufp)) {
			    android_puts(bufp);
			    /* pointer and cursor left where they were */
			    for (i = bufp; *i; ++i) android_puts("\b");
			} else if (i > bufp) {
			    char *s = i;

			    /* erase rest of prior guess */
			    for (; i > bufp; --i) android_puts(" ");
			    for (; s > bufp; --s) android_puts("\b");
			}
		} else if(c == kill_char || c == '\177') { /* Robert Viduya */
				/* this test last - @ might be the kill_char */
			for (; *bufp; ++bufp) android_puts(" ");
			for (; bufp != obufp; --bufp) android_puts("\b \b");
			*bufp = 0;
		} else
			tty_nhbell();
	}
	ttyDisplay->toplin = 2;		/* nonempty, no --More-- required */
	ttyDisplay->inread--;
	clear_nhwindow(WIN_MESSAGE);	/* clean up after ourselves */
}


char android_yn_function(const char *query, const char *resp, CHAR_P def)
{
	char ret;

	android_pushgamestate(kAndroidGameStateWaitingForResponse);

	ret = tty_yn_function(query, resp, def);

	android_popgamestate();

	return ret;
}


/*
 * Read in an extended command, doing command line completion.  We
 * stop when we have found enough characters to make a unique command.
 */
int android_get_ext_cmd()
{
	int ret;

	android_puts("\033A1");

#if 1
	int i;
	char buf[BUFSZ];

	if (iflags.extmenu) return extcmd_via_menu();
	/* maybe a runtime option? */
	/* hooked_tty_getlin("#", buf, flags.cmd_comp ? ext_cmd_getlin_hook : (getlin_hook_proc) 0); */

	android_pushgamestate(kAndroidGameStateExtCmd);

#ifdef REDO
	android_hooked_tty_getlin("#", buf, in_doagain ? (getlin_hook_proc)0
		: ext_cmd_getlin_hook);
#else
	android_hooked_tty_getlin("#", buf, ext_cmd_getlin_hook);
#endif

	android_popgamestate();

	(void) mungspaces(buf);
	if (buf[0] == 0 || buf[0] == '\033') return -1;

	for (i = 0; extcmdlist[i].ef_txt != (char *)0; i++)
		if (!strcmpi(buf, extcmdlist[i].ef_txt)) break;

#ifdef REDO
	if (!in_doagain) {
	    int j;
	    for (j = 0; buf[j]; j++)
		savech(buf[j]);
	    savech('\n');
	}
#endif

	if (extcmdlist[i].ef_txt == (char *)0) {
		pline("%s: unknown extended command.", buf);
		i = -1;
	}
#endif

	android_puts("\033A0");

	return i;
}


/*
 * Implement extended command completion by using this hook into
 * tty_getlin.  Check the characters already typed, if they uniquely
 * identify an extended command, expand the string to the whole
 * command.
 *
 * Return TRUE if we've extended the string at base.  Otherwise return FALSE.
 * Assumptions:
 *
 *	+ we don't change the characters that are already in base
 *	+ base has enough room to hold our string
 */
STATIC_OVL boolean
ext_cmd_getlin_hook(base)
	char *base;
{
	int oindex, com_index;

	com_index = -1;
	for (oindex = 0; extcmdlist[oindex].ef_txt != (char *)0; oindex++) {
		if (!strncmpi(base, extcmdlist[oindex].ef_txt, strlen(base))) {
			if (com_index == -1)	/* no matches yet */
			    com_index = oindex;
			else			/* more than 1 match */
			    return FALSE;
		}
	}
	if (com_index >= 0) {
		Strcpy(base, extcmdlist[com_index].ef_txt);
		return TRUE;
	}

	return FALSE;	/* didn't match anything */
}

/* End of file winandroid.c */
