#!/bin/sh

ANDROID_NDK_DIR=/home/astaroth/android/android-ndk-1.5_r1

rm NetHackApp/libs
rm $ANDROID_NDK_DIR/apps/NetHackNative
rm $ANDROID_NDK_DIR/sources/NetHackNative
rm NetHackNative/nethack
rm ../../src/Android.mk
rm ../../include/androidconf.h

# Automatically generated files, which we currently fake with
# symbolic links.
rm ../../include/date.h
rm ../../include/onames.h
rm ../../include/pm.h
rm ../../src/monstr.c
rm ../../src/vis_tab.c
