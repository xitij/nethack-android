LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)

LOCAL_MODULE    := javainterface
LOCAL_SRC_FILES := javainterface.c

include $(BUILD_SHARED_LIBRARY)
