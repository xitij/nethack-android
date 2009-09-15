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
