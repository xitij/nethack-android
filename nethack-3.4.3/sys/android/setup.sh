#!/bin/sh

echo "Copying Makefiles."

cp Makefile.top ../../Makefile
cp Makefile.dat ../../dat/Makefile
cp Makefile.doc ../../doc/Makefile
cp Makefile.src ../../src/Makefile
cp Makefile.utl ../../util/Makefile

echo "Setting absolute path"

sed $(echo s@ABSOLUTE_PATH@$(pwd)@g) < NetHackNative/Application0.mk > NetHackNative/Application.mk

echo "Setting up links"

cd NetHackApp
ln -s ../NetHackNative/libs
cd ..

ANDROID_NDK_DIR=/home/astaroth/android/android-ndk-1.5_r1

ln -s $(pwd)/NetHackNative $ANDROID_NDK_DIR/apps/
ln -s $(pwd)/NetHackNative $ANDROID_NDK_DIR/sources/

# Set up the 'src' directory as project in the Android native directory.
ln -s $(pwd)/../../src NetHackNative/nethack

# Put a link to the Android makefile in the 'src' directory,
# and link in other Android-specific files into the source tree.
ln -s $(pwd)/NetHackNative/nethack_Android.mk ../../src/Android.mk
ln -s $(pwd)/NetHackNative/androidconf.h ../../include/androidconf.h

# HACK! These are files which are supposed to be automatically generated,
# but for now, we have stored off a copy in the 'gen' directory. We use
# symbolic links to get them to the right place where they can be used
# for the build - this has the benefit of making them easily distinguishable
# from the true source files.
ln -s $(pwd)/gen/date.h ../../include/
ln -s $(pwd)/gen/onames.h ../../include/
ln -s $(pwd)/gen/pm.h ../../include/
ln -s $(pwd)/gen/monstr.c ../../src/
ln -s $(pwd)/gen/vis_tab.c ../../src/
