#!/bin/sh

ANDROID_NDK_DIR=/home/astaroth/android/android-ndk-1.5_r1

rm NetHackApp/libs
rm $ANDROID_NDK_DIR/apps/NetHackNative
rm $ANDROID_NDK_DIR/sources/NetHackNative
rm NetHackNative/nethack
rm NetHackApp/assets/dat
rm ../../src/Android.mk
rm ../../include/androidconf.h
