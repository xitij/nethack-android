/* winandroid.c */

#include "hack.h"

#include "wintty.h"

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
    tty_create_nhwindow,
    tty_clear_nhwindow,
    tty_display_nhwindow,
    tty_destroy_nhwindow,
    tty_curs,
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

void android_putstr(winid window, int attr, const char *str)
{
	struct WinDesc *cw = wins[window];
	if(cw && cw->type == NHW_MESSAGE)
	{
#if 0
		char buff[1024];
		snprintf(buff, sizeof(buff), "%cA35m%s", 27, str);
		update_topl(buff);
#endif
		update_topl(str);

		return;
	}
	tty_putstr(window, attr, str);
}

/* End of file winandroid.c */
