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

cd NetHackNative/assets
ln -s ../../../../dat
cd ../../

ANDROID_NDK_DIR=/home/astaroth/android/android-ndk-1.5_r1

ln -s $(pwd)/NetHackNative $ANDROID_NDK_DIR/apps/
ln -s $(pwd)/NetHackNative $ANDROID_NDK_DIR/sources/

# Set up the 'src' directory as project in the Android native directory.
ln -s $(pwd)/../../src NetHackNative/nethack

# Put a link to the Android makefile in the 'src' directory,
# and link in other Android-specific files into the source tree.
ln -s $(pwd)/NetHackNative/nethack_Android.mk ../../src/Android.mk
ln -s $(pwd)/NetHackNative/androidconf.h ../../include/androidconf.h


# Current procedure:
# 1. Run 'setup.sh'
# 2. Go to 'dat' folder, type 'make'.
# 3. Go to ANDROID_NDK_DIR, type 'make APP=NetHackNative'.
# 4. Run Eclipse, import project from sys/android/NetHackApp.
# 5. Build and launch application.
