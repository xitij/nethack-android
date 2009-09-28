#ifndef ANDROIDCONF_H
#define ANDROIDCONF_H

#if 0
#define MICRO		/* prevents TERMLIB from being defined, maybe */
#endif

// TODO
#if 0

#define TEXTCOLOR		/* Use colored monsters and objects */
#define HACKFONT		/* Use special hack.font */
#define SHELL			/* Have a shell escape command (!) */
#define MAIL			/* Get mail at unexpected occasions */
#define DEFAULT_ICON "NetHack:default.icon"	/* private icon */
#define AMIFLUSH		/* toss typeahead (select flush in .cnf) */
/* #define OPT_DISPMAP		/* enable fast_map option */

#endif

#if 0

#ifndef MICRO_H
#include "micro.h"
#endif

/* From pcconf.h: */
#define PATHLEN		64	/* maximum pathlength */
#define FILENAME	80	/* maximum filename length (conservative) */

#ifndef SYSTEM_H
#include "system.h"		/* For SIG_RET_TYPE, etc */
#endif

#endif

/*#undef	UNIX*/		/* Not sure... seems to not work with MICRO otherwise */
#undef	TERMLIB

#ifdef TTY_GRAPHICS
# define ANSI_DEFAULT
#endif


extern void android_putchar(int c);
extern void android_puts(const char *s);
extern int android_getch(void);

/* Is there a better way to remap this? I sure hope so. */
#undef putchar
#define putchar android_putchar
#undef puts
#define puts android_puts
#undef getchar
#define getchar android_getch

#if 0
#undef stdin
#define stdin UNDEFINED_stdin
#endif

#endif /* ANDROIDCONF_H */
