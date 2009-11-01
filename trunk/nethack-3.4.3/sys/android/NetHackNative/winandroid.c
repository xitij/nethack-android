/* winandroid.c */

#include "hack.h"

#include "wintty.h"

#include <jni.h>

winid android_create_nhwindow(int type);
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
    tty_clear_nhwindow,
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

void Java_com_nethackff_NetHackApp_NetHackSetScreenDim(
		JNIEnv *env, jobject thiz, int width)
{
	s_ScreenNumColumns = width;
}

#if 0
int g_android_refresh = 0;
#endif

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


void android_putstr(winid window, int attr, const char *str)
{
	struct WinDesc *cw = wins[window];
	if(cw && cw->type == NHW_MESSAGE)
	{
#if 0
		update_topl(str);
#else

		/* HACK */
		int oldco = CO;
		CO = s_ScreenNumColumns;

		android_puts("\033A1");
		update_topl(str);
		android_puts("\033A0");

		CO = oldco;
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
