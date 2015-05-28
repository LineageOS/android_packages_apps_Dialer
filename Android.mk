LOCAL_PATH:= $(call my-dir)
include $(CLEAR_VARS)

LOCAL_MODULE_TAGS := optional

incallui_dir := ../InCallUI
contacts_common_dir := ../ContactsCommon
phone_common_dir := ../PhoneCommon

src_dirs := src \
    $(incallui_dir)/src \
    $(contacts_common_dir)/src \
    $(phone_common_dir)/src

res_dirs := res \
    $(incallui_dir)/res \
    $(contacts_common_dir)/res \
    $(phone_common_dir)/res

LOCAL_SRC_FILES := $(call all-java-files-under, $(src_dirs))
LOCAL_RESOURCE_DIR := $(addprefix $(LOCAL_PATH)/, $(res_dirs)) \
    frameworks/support/v7/cardview/res frameworks/support/v7/recyclerview/res

LOCAL_AAPT_FLAGS := \
    --auto-add-overlay \
    --extra-packages android.support.v7.cardview \
    --extra-packages android.support.v7.recyclerview \
    --extra-packages com.android.incallui \
    --extra-packages com.android.contacts.common \
    --extra-packages com.android.phone.common

LOCAL_JAVA_LIBRARIES := telephony-common
LOCAL_STATIC_JAVA_LIBRARIES := \
    android-common \
    android-support-v13 \
    android-support-v4 \
    android-support-v7-cardview \
    android-support-v7-recyclerview \
    com.android.services.telephony.common \
    com.android.vcard \
    guava \
    libphonenumber

LOCAL_PACKAGE_NAME := Dialer
LOCAL_CERTIFICATE := shared
LOCAL_PRIVILEGED_MODULE := true

LOCAL_PROGUARD_FLAG_FILES := proguard.flags $(incallui_dir)/proguard.flags

# Uncomment the following line to build against the current SDK
# This is required for building an unbundled app.
LOCAL_SDK_VERSION := current

include $(BUILD_PACKAGE)

# Use the following include to make our test apk.
include $(call all-makefiles-under,$(LOCAL_PATH))
