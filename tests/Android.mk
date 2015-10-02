LOCAL_PATH:= $(call my-dir)
include $(CLEAR_VARS)

# We only want this apk build for tests.
LOCAL_MODULE_TAGS := tests
LOCAL_CERTIFICATE := shared

LOCAL_STATIC_JAVA_LIBRARIES := android-support-test

# Include all test java files.
LOCAL_SRC_FILES := $(call all-java-files-under, src)

src_dirs := src \
    ../../ContactsCommon/TestCommon/src

# Include all test java files.
LOCAL_SRC_FILES := $(call all-java-files-under, $(src_dirs))

LOCAL_STATIC_JAVA_LIBRARIES += \
        mockito-target

LOCAL_PACKAGE_NAME := DialerTests

LOCAL_INSTRUMENTATION_FOR := Dialer

LOCAL_SDK_VERSION := current

include $(BUILD_PACKAGE)
