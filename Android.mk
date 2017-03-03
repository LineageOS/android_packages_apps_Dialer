# Local modifications:
# * All location/maps code has been removed from the incallui.
# * Precompiled AutoValue classes have been included.
# * Precompiled Dagger classes have been included.
# * All autovalue imports and annotations have been stripped.
# * Precompiled proto classes have been included.
LOCAL_PATH:= $(call my-dir)
include $(CLEAR_VARS)

ifeq ($(TARGET_BUILD_APPS),)
support_library_root_dir := frameworks/support
else
support_library_root_dir := prebuilts/sdk/current/support
endif

# The base directory for Dialer sources.
BASE_DIR := java/com/android

# Primary dialer module sources.
SRC_DIRS := \
	$(BASE_DIR)/contacts/common \
	$(BASE_DIR)/dialer \
	$(BASE_DIR)/incallui \
	$(BASE_DIR)/voicemailomtp

# All Dialers resources.
# find . -type d -name "res" | uniq | sort
RES_DIRS := \
	assets/product/res \
	assets/quantum/res \
	$(BASE_DIR)/contacts/common/res \
	$(BASE_DIR)/dialer/app/res \
	$(BASE_DIR)/dialer/app/voicemail/error/res \
	$(BASE_DIR)/dialer/blocking/res \
	$(BASE_DIR)/dialer/callcomposer/camera/camerafocus/res \
	$(BASE_DIR)/dialer/callcomposer/cameraui/res \
	$(BASE_DIR)/dialer/callcomposer/res \
	$(BASE_DIR)/dialer/common/res \
	$(BASE_DIR)/dialer/dialpadview/res \
	$(BASE_DIR)/dialer/interactions/res \
	$(BASE_DIR)/dialer/phonenumberutil/res \
	$(BASE_DIR)/dialer/shortcuts/res \
	$(BASE_DIR)/dialer/theme/res \
	$(BASE_DIR)/dialer/util/res \
	$(BASE_DIR)/dialer/voicemailstatus/res \
	$(BASE_DIR)/dialer/widget/res \
	$(BASE_DIR)/incallui/answer/impl/affordance/res \
	$(BASE_DIR)/incallui/answer/impl/answermethod/res \
	$(BASE_DIR)/incallui/answer/impl/hint/res \
	$(BASE_DIR)/incallui/answer/impl/res \
	$(BASE_DIR)/incallui/audioroute/res \
	$(BASE_DIR)/incallui/autoresizetext/res \
	$(BASE_DIR)/incallui/commontheme/res \
	$(BASE_DIR)/incallui/contactgrid/res \
	$(BASE_DIR)/incallui/hold/res \
	$(BASE_DIR)/incallui/incall/impl/res \
	$(BASE_DIR)/incallui/res \
	$(BASE_DIR)/incallui/sessiondata/res \
	$(BASE_DIR)/incallui/video/impl/res \
	$(BASE_DIR)/incallui/wifi/res \
	$(BASE_DIR)/voicemailomtp/res

# Dialer manifest files to merge.
# find . -type f -name "AndroidManifest.xml" | uniq | sort
DIALER_MANIFEST_FILES += \
	$(BASE_DIR)/contacts/common/AndroidManifest.xml \
	$(BASE_DIR)/dialer/app/AndroidManifest.xml \
	$(BASE_DIR)/dialer/app/manifests/activities/AndroidManifest.xml \
	$(BASE_DIR)/dialer/app/voicemail/error/AndroidManifest.xml \
	$(BASE_DIR)/dialer/backup/AndroidManifest.xml \
	$(BASE_DIR)/dialer/blocking/AndroidManifest.xml \
	$(BASE_DIR)/dialer/callcomposer/AndroidManifest.xml \
	$(BASE_DIR)/dialer/callcomposer/camera/AndroidManifest.xml \
	$(BASE_DIR)/dialer/callcomposer/camera/camerafocus/AndroidManifest.xml \
	$(BASE_DIR)/dialer/callcomposer/cameraui/AndroidManifest.xml \
	$(BASE_DIR)/dialer/common/AndroidManifest.xml \
	$(BASE_DIR)/dialer/debug/AndroidManifest.xml \
	$(BASE_DIR)/dialer/debug/impl/AndroidManifest.xml \
	$(BASE_DIR)/dialer/dialpadview/AndroidManifest.xml \
	$(BASE_DIR)/dialer/interactions/AndroidManifest.xml \
	$(BASE_DIR)/dialer/phonenumberutil/AndroidManifest.xml \
	$(BASE_DIR)/dialer/shortcuts/AndroidManifest.xml \
	$(BASE_DIR)/dialer/simulator/impl/AndroidManifest.xml \
	$(BASE_DIR)/dialer/theme/AndroidManifest.xml \
	$(BASE_DIR)/dialer/util/AndroidManifest.xml \
	$(BASE_DIR)/dialer/voicemailstatus/AndroidManifest.xml \
	$(BASE_DIR)/dialer/widget/AndroidManifest.xml \
	$(BASE_DIR)/incallui/AndroidManifest.xml \
	$(BASE_DIR)/incallui/answer/impl/affordance/AndroidManifest.xml \
	$(BASE_DIR)/incallui/answer/impl/AndroidManifest.xml \
	$(BASE_DIR)/incallui/answer/impl/answermethod/AndroidManifest.xml \
	$(BASE_DIR)/incallui/answer/impl/hint/AndroidManifest.xml \
	$(BASE_DIR)/incallui/audioroute/AndroidManifest.xml \
	$(BASE_DIR)/incallui/autoresizetext/AndroidManifest.xml \
	$(BASE_DIR)/incallui/commontheme/AndroidManifest.xml \
	$(BASE_DIR)/incallui/contactgrid/AndroidManifest.xml \
	$(BASE_DIR)/incallui/hold/AndroidManifest.xml \
	$(BASE_DIR)/incallui/incall/impl/AndroidManifest.xml \
	$(BASE_DIR)/incallui/sessiondata/AndroidManifest.xml \
	$(BASE_DIR)/incallui/video/impl/AndroidManifest.xml \
	$(BASE_DIR)/incallui/wifi/AndroidManifest.xml \
	$(BASE_DIR)/voicemailomtp/AndroidManifest.xml

# Merge all manifest files.
LOCAL_FULL_LIBS_MANIFEST_FILES := \
	$(addprefix $(LOCAL_PATH)/, $(DIALER_MANIFEST_FILES))
LOCAL_SRC_FILES := $(call all-java-files-under, $(SRC_DIRS))
LOCAL_RESOURCE_DIR := \
	$(addprefix $(LOCAL_PATH)/, $(RES_DIRS)) \
	$(support_library_root_dir)/design/res \
	$(support_library_root_dir)/v7/appcompat/res \
	$(support_library_root_dir)/v7/cardview/res \
	$(support_library_root_dir)/v7/recyclerview/res

# We specify each package explicitly to glob resource files.
LOCAL_AAPT_FLAGS := \
	--auto-add-overlay \
	--extra-packages android.support.design \
	--extra-packages android.support.transition \
	--extra-packages android.support.v7.appcompat \
	--extra-packages android.support.v7.cardview \
	--extra-packages android.support.v7.recyclerview \
	--extra-packages com.android.contacts.common \
	--extra-packages com.android.dialer.app \
	--extra-packages com.android.dialer.app.voicemail.error \
	--extra-packages com.android.dialer.blocking \
	--extra-packages com.android.dialer.callcomposer \
	--extra-packages com.android.dialer.callcomposer \
	--extra-packages com.android.dialer.callcomposer.camera \
	--extra-packages com.android.dialer.callcomposer.camera.camerafocus \
	--extra-packages com.android.dialer.callcomposer.cameraui \
	--extra-packages com.android.dialer.common \
	--extra-packages com.android.dialer.dialpadview \
	--extra-packages com.android.dialer.interactions \
	--extra-packages com.android.dialer.phonenumberutil \
	--extra-packages com.android.dialer.shortcuts \
	--extra-packages com.android.dialer.util \
	--extra-packages com.android.dialer.voicemailstatus \
	--extra-packages com.android.dialer.widget \
	--extra-packages com.android.incallui \
	--extra-packages com.android.incallui.answer.impl \
	--extra-packages com.android.incallui.answer.impl.affordance \
	--extra-packages com.android.incallui.answer.impl.affordance \
	--extra-packages com.android.incallui.answer.impl.answermethod \
	--extra-packages com.android.incallui.answer.impl.hint \
	--extra-packages com.android.incallui.audioroute \
	--extra-packages com.android.incallui.autoresizetext \
	--extra-packages com.android.incallui.commontheme \
	--extra-packages com.android.incallui.contactgrid \
	--extra-packages com.android.incallui.hold \
	--extra-packages com.android.incallui.incall.impl \
	--extra-packages com.android.incallui.sessiondata \
	--extra-packages com.android.incallui.video \
	--extra-packages com.android.incallui.video.impl \
	--extra-packages com.android.incallui.wifi \
	--extra-packages com.android.phone.common \
	--extra-packages com.android.voicemailomtp \
	--extra-packages com.android.voicemailomtp.settings \
	--extra-packages me.leolin.shortcutbadger

LOCAL_STATIC_JAVA_LIBRARIES := \
	android-common \
	android-support-design \
	android-support-transition \
	android-support-v13 \
	android-support-v4 \
	android-support-v7-appcompat \
	android-support-v7-cardview \
	android-support-v7-recyclerview \
	com.android.vcard \
	dailer-dagger2-compiler \
	dialer-dagger2 \
	dialer-dagger2-producers \
	dialer-glide  \
	dialer-guava \
	dialer-javax-annotation-api \
	dialer-javax-inject \
	dialer-libshortcutbadger \
	jsr305 \
	libphonenumber \
	libprotobuf-java-nano \
	org.apache.http.legacy.boot \
	volley

LOCAL_JAVA_LIBRARIES := \
	android-support-annotations \
	android-support-transition \
	dailer-dagger2-compiler \
	dialer-dagger2 \
	dialer-dagger2-producers \
	dialer-glide  \
	dialer-guava \
	dialer-javax-annotation-api \
	dialer-javax-inject \
	dialer-libshortcutbadger \
	jsr305 \
	libprotobuf-java-nano

# Libraries needed by the compiler (JACK) to generate code.
PROCESSOR_LIBRARIES_TARGET := \
	dailer-dagger2-compiler \
	dialer-dagger2 \
	dialer-dagger2-producers \
	dialer-guava \
	dialer-javax-annotation-api \
	dialer-javax-inject

# TODO: Include when JACK properly supports AutoValue b/35360557
# (builders not generated successfully, javac duplicate issues) in
# LOCAL_STATIC_JAVA_LIBRARIES, LOCAL_JAVA_LIBRARIES, PROCESSOR_LIBRARIES_TARGET
# 	dialer-auto-value

# Resolve the jar paths.
PROCESSOR_JARS := $(call java-lib-deps, $(PROCESSOR_LIBRARIES_TARGET))
LOCAL_ADDITIONAL_DEPENDENCIES += $(PROCESSOR_JARS)

LOCAL_JACK_FLAGS += --processorpath $(call normalize-path-list,$(PROCESSOR_JARS))

LOCAL_PROGUARD_FLAG_FILES := proguard.flags $(incallui_dir)/proguard.flags

LOCAL_SDK_VERSION := current
LOCAL_MODULE_TAGS := optional
LOCAL_PACKAGE_NAME := Dialer
LOCAL_CERTIFICATE := shared
LOCAL_PRIVILEGED_MODULE := true
include $(BUILD_PACKAGE)

# Cleanup local state
BASE_DIR :=
SRC_DIRS :=
RES_DIRS :=
DIALER_MANIFEST_FILES :=
PROCESSOR_LIBRARIES_TARGET :=
PROCESSOR_JARS :=

# Create references to prebuilt libraries.
include $(CLEAR_VARS)

LOCAL_PREBUILT_STATIC_JAVA_LIBRARIES := \
	dailer-dagger2-compiler:../../../prebuilts/tools/common/m2/repository/com/google/dagger/dagger-compiler/2.6/dagger-compiler-2.6$(COMMON_JAVA_PACKAGE_SUFFIX) \
	dialer-auto-common:../../../prebuilts/tools/common/m2/repository/com/google/auto/auto-common/0.6/auto-common-0.6$(COMMON_JAVA_PACKAGE_SUFFIX) \
	dialer-auto-value:../../../prebuilts/tools/common/m2/repository/com/google/auto/value/auto-value/1.3/auto-value-1.3$(COMMON_JAVA_PACKAGE_SUFFIX) \
	dialer-dagger2:../../../prebuilts/tools/common/m2/repository/com/google/dagger/dagger/2.6/dagger-2.6$(COMMON_JAVA_PACKAGE_SUFFIX) \
	dialer-dagger2-producers:../../../prebuilts/tools/common/m2/repository/com/google/dagger/dagger-producers/2.6/dagger-producers-2.6$(COMMON_JAVA_PACKAGE_SUFFIX) \
	dialer-glide:../../../prebuilts/maven_repo/bumptech/com/github/bumptech/glide/glide/4.0.0-SNAPSHOT/glide-4.0.0-SNAPSHOT$(COMMON_JAVA_PACKAGE_SUFFIX) \
	dialer-guava:../../../prebuilts/tools/common/m2/repository/com/google/guava/guava/20.0/guava-20.0$(COMMON_JAVA_PACKAGE_SUFFIX) \
	dialer-javax-annotation-api:../../../prebuilts/tools/common/m2/repository/javax/annotation/javax.annotation-api/1.2/javax.annotation-api-1.2$(COMMON_JAVA_PACKAGE_SUFFIX) \
	dialer-javax-inject:../../../prebuilts/tools/common/m2/repository/javax/inject/javax.inject/1/javax.inject-1$(COMMON_JAVA_PACKAGE_SUFFIX) \
	dialer-libshortcutbadger:../../../prebuilts/tools/common/m2/repository/me/leolin/ShortcutBadger/1.1.13/ShortcutBadger-1.1.13$(COMMON_JAVA_PACKAGE_SUFFIX)

include $(BUILD_MULTI_PREBUILT)

include $(CLEAR_VARS)
