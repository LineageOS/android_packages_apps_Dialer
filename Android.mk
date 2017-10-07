# Local modifications:
# * removed com.google.android.backup.api_key. This should be added to
#      the manifest in the top level directory.
# * removed com.google.android.geo.API_KEY key. This should be added to
#      the manifest files in java/com/android/incallui/calllocation/impl/
#      and /java/com/android/incallui/maps/impl/
# * b/62417801 modify translation string naming convention:
#      $ find . -type d | grep 262 | rename 's/(values)\-([a-zA-Z\+\-]+)\-(mcc262-mnc01)/$1-$3-$2/'
# * b/62343966 include manually generated GRPC service class:
#      $ protoc --plugin=protoc-gen-grpc-java=prebuilts/tools/common/m2/repository/io/grpc/protoc-gen-grpc-java/1.0.3/protoc-gen-grpc-java-1.0.3-linux-x86_64.exe \
#               --grpc-java_out=lite:"packages/apps/Dialer/java/com/android/voicemail/impl/" \
#               --proto_path="packages/apps/Dialer/java/com/android/voicemail/impl/transcribe/grpc/" "packages/apps/Dialer/java/com/android/voicemail/impl/transcribe/grpc/voicemail_transcription.proto"
# * b/37077388 temporarily disable proguard with javac
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
	$(BASE_DIR)/dialershared \
	$(BASE_DIR)/incallui \
	$(BASE_DIR)/voicemail

# Exclude files incompatible with AOSP.
EXCLUDE_FILES := \
	$(BASE_DIR)/incallui/calllocation/impl/AuthException.java \
	$(BASE_DIR)/incallui/calllocation/impl/CallLocationImpl.java \
	$(BASE_DIR)/incallui/calllocation/impl/CallLocationModule.java \
	$(BASE_DIR)/incallui/calllocation/impl/DownloadMapImageTask.java \
	$(BASE_DIR)/incallui/calllocation/impl/GoogleLocationSettingHelper.java \
	$(BASE_DIR)/incallui/calllocation/impl/HttpFetcher.java \
	$(BASE_DIR)/incallui/calllocation/impl/LocationFragment.java \
	$(BASE_DIR)/incallui/calllocation/impl/LocationHelper.java \
	$(BASE_DIR)/incallui/calllocation/impl/LocationPresenter.java \
	$(BASE_DIR)/incallui/calllocation/impl/LocationUrlBuilder.java \
	$(BASE_DIR)/incallui/calllocation/impl/ReverseGeocodeTask.java \
	$(BASE_DIR)/incallui/calllocation/impl/TrafficStatsTags.java \
	$(BASE_DIR)/incallui/maps/impl/MapsImpl.java \
	$(BASE_DIR)/incallui/maps/impl/MapsModule.java \
	$(BASE_DIR)/incallui/maps/impl/StaticMapFragment.java \

# Exclude testing only class, not used anywhere here
EXCLUDE_FILES += \
	$(BASE_DIR)/contacts/common/format/testing/SpannedTestUtils.java

# Exclude build variants for now
EXCLUDE_FILES += \
	$(BASE_DIR)/dialer/buildtype/bugfood/BuildTypeAccessorImpl.java \
	$(BASE_DIR)/dialer/buildtype/dogfood/BuildTypeAccessorImpl.java \
	$(BASE_DIR)/dialer/buildtype/fishfood/BuildTypeAccessorImpl.java \
	$(BASE_DIR)/dialer/buildtype/test/BuildTypeAccessorImpl.java \
	$(BASE_DIR)/dialer/constants/googledialer/ConstantsImpl.java \
	$(BASE_DIR)/dialer/binary/google/GoogleStubDialerRootComponent.java \
	$(BASE_DIR)/dialer/binary/google/GoogleStubDialerApplication.java

# All Dialers resources.
# find . -type d -name "res" | uniq | sort
RES_DIRS := \
	assets/product/res \
	assets/quantum/res \
	$(BASE_DIR)/contacts/common/res \
	$(BASE_DIR)/dialer/about/res \
	$(BASE_DIR)/dialer/app/res \
	$(BASE_DIR)/dialer/app/voicemail/error/res \
	$(BASE_DIR)/dialer/blocking/res \
	$(BASE_DIR)/dialer/callcomposer/camera/camerafocus/res \
	$(BASE_DIR)/dialer/callcomposer/cameraui/res \
	$(BASE_DIR)/dialer/callcomposer/res \
	$(BASE_DIR)/dialer/calldetails/res \
	$(BASE_DIR)/dialer/calllog/ui/res \
	$(BASE_DIR)/dialer/calllogutils/res \
	$(BASE_DIR)/dialer/common/res \
	$(BASE_DIR)/dialer/contactactions/res \
	$(BASE_DIR)/dialer/contactsfragment/res \
	$(BASE_DIR)/dialer/dialpadview/res \
	$(BASE_DIR)/dialer/enrichedcall/simulator/res \
	$(BASE_DIR)/dialer/interactions/res \
	$(BASE_DIR)/dialer/lookup/res \
	$(BASE_DIR)/dialer/main/impl/res \
	$(BASE_DIR)/dialer/notification/res \
	$(BASE_DIR)/dialer/oem/res \
	$(BASE_DIR)/dialer/phonenumberutil/res \
	$(BASE_DIR)/dialer/postcall/res \
	$(BASE_DIR)/dialer/searchfragment/common/res \
	$(BASE_DIR)/dialer/searchfragment/list/res \
	$(BASE_DIR)/dialer/searchfragment/nearbyplaces/res \
	$(BASE_DIR)/dialershared/bubble/res \
	$(BASE_DIR)/dialer/shortcuts/res \
	$(BASE_DIR)/dialer/speeddial/res \
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
	$(BASE_DIR)/incallui/calllocation/impl/res \
	$(BASE_DIR)/incallui/commontheme/res \
	$(BASE_DIR)/incallui/contactgrid/res \
	$(BASE_DIR)/incallui/disconnectdialog/res \
	$(BASE_DIR)/incallui/hold/res \
	$(BASE_DIR)/incallui/incall/impl/res \
	$(BASE_DIR)/incallui/res \
	$(BASE_DIR)/incallui/sessiondata/res \
	$(BASE_DIR)/incallui/speakerbuttonlogic/res \
	$(BASE_DIR)/incallui/telecomeventui/res \
	$(BASE_DIR)/incallui/video/impl/res \
	$(BASE_DIR)/incallui/video/protocol/res \
	$(BASE_DIR)/voicemail/impl/configui/res \
	$(BASE_DIR)/voicemail/impl/res \


# Dialer manifest files to merge.
# find . -type f -name "AndroidManifest.xml" | uniq | sort
DIALER_MANIFEST_FILES += \
	$(BASE_DIR)/contacts/common/AndroidManifest.xml \
	$(BASE_DIR)/dialer/about/AndroidManifest.xml \
	$(BASE_DIR)/dialer/app/AndroidManifest.xml \
	$(BASE_DIR)/dialer/app/manifests/activities/AndroidManifest.xml \
	$(BASE_DIR)/dialer/app/voicemail/error/AndroidManifest.xml \
	$(BASE_DIR)/dialer/backup/AndroidManifest.xml \
	$(BASE_DIR)/dialer/blocking/AndroidManifest.xml \
	$(BASE_DIR)/dialer/callcomposer/AndroidManifest.xml \
	$(BASE_DIR)/dialer/callcomposer/camera/AndroidManifest.xml \
	$(BASE_DIR)/dialer/callcomposer/camera/camerafocus/AndroidManifest.xml \
	$(BASE_DIR)/dialer/callcomposer/cameraui/AndroidManifest.xml \
	$(BASE_DIR)/dialer/calldetails/AndroidManifest.xml \
	$(BASE_DIR)/dialer/calllog/ui/AndroidManifest.xml \
	$(BASE_DIR)/dialer/calllogutils/AndroidManifest.xml \
	$(BASE_DIR)/dialer/common/AndroidManifest.xml \
	$(BASE_DIR)/dialer/contactactions/AndroidManifest.xml \
	$(BASE_DIR)/dialer/contactsfragment/AndroidManifest.xml \
	$(BASE_DIR)/dialer/dialpadview/AndroidManifest.xml \
	$(BASE_DIR)/dialer/enrichedcall/simulator/AndroidManifest.xml \
	$(BASE_DIR)/dialer/interactions/AndroidManifest.xml \
	$(BASE_DIR)/dialer/lookup/AndroidManifest.xml \
	$(BASE_DIR)/dialer/main/impl/AndroidManifest.xml \
	$(BASE_DIR)/dialer/notification/AndroidManifest.xml \
	$(BASE_DIR)/dialer/oem/AndroidManifest.xml \
	$(BASE_DIR)/dialer/phonenumberutil/AndroidManifest.xml \
	$(BASE_DIR)/dialer/postcall/AndroidManifest.xml \
	$(BASE_DIR)/dialer/searchfragment/common/AndroidManifest.xml \
	$(BASE_DIR)/dialer/searchfragment/list/AndroidManifest.xml \
	$(BASE_DIR)/dialer/searchfragment/nearbyplaces/AndroidManifest.xml \
	$(BASE_DIR)/dialershared/bubble/AndroidManifest.xml \
	$(BASE_DIR)/dialer/shortcuts/AndroidManifest.xml \
	$(BASE_DIR)/dialer/simulator/impl/AndroidManifest.xml \
	$(BASE_DIR)/dialer/speeddial/AndroidManifest.xml \
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
	$(BASE_DIR)/incallui/disconnectdialog/AndroidManifest.xml \
	$(BASE_DIR)/incallui/hold/AndroidManifest.xml \
	$(BASE_DIR)/incallui/incall/impl/AndroidManifest.xml \
	$(BASE_DIR)/incallui/sessiondata/AndroidManifest.xml \
	$(BASE_DIR)/incallui/speakerbuttonlogic/AndroidManifest.xml \
	$(BASE_DIR)/incallui/telecomeventui/AndroidManifest.xml \
	$(BASE_DIR)/incallui/video/impl/AndroidManifest.xml \
	$(BASE_DIR)/incallui/video/protocol/AndroidManifest.xml \
	$(BASE_DIR)/voicemail/AndroidManifest.xml \
	$(BASE_DIR)/voicemail/impl/configui/AndroidManifest.xml \
	$(BASE_DIR)/voicemail/impl/AndroidManifest.xml \


# Merge all manifest files.
LOCAL_FULL_LIBS_MANIFEST_FILES := \
	$(addprefix $(LOCAL_PATH)/, $(DIALER_MANIFEST_FILES))
LOCAL_SRC_FILES := $(call all-java-files-under, $(SRC_DIRS))
LOCAL_SRC_FILES := $(filter-out $(EXCLUDE_FILES),$(LOCAL_SRC_FILES))
LOCAL_SRC_FILES += $(call all-proto-files-under, $(SRC_DIRS))

# Backup Library
BACKUP_LIB_SRC_DIR := ../../../external/libbackup/src/com/google/android/libraries/backup
EXCLUDE_BACKUP_LIB_SRCS := $(call all-java-files-under, $(BACKUP_LIB_SRC_DIR)/shadow)
LOCAL_SRC_FILES += $(call all-java-files-under, $(BACKUP_LIB_SRC_DIR))
LOCAL_SRC_FILES := $(filter-out $(EXCLUDE_BACKUP_LIB_SRCS),$(LOCAL_SRC_FILES))

LOCAL_PROTOC_FLAGS := --proto_path=$(LOCAL_PATH)

LOCAL_RESOURCE_DIR := \
	$(addprefix $(LOCAL_PATH)/, $(RES_DIRS)) \
	$(support_library_root_dir)/design/res \
	$(support_library_root_dir)/transition/res \
	$(support_library_root_dir)/v7/appcompat/res \
	$(support_library_root_dir)/v7/cardview/res \
	$(support_library_root_dir)/v7/recyclerview/res

# We specify each package explicitly to glob resource files.
LOCAL_AAPT_FLAGS := \
	--auto-add-overlay \
	--extra-packages com.android.contacts.common \
	--extra-packages com.android.dialer.about \
	--extra-packages com.android.dialer.app \
	--extra-packages com.android.dialer.app.voicemail.error \
	--extra-packages com.android.dialer.blocking \
	--extra-packages com.android.dialer.callcomposer \
	--extra-packages com.android.dialer.callcomposer \
	--extra-packages com.android.dialer.callcomposer.camera \
	--extra-packages com.android.dialer.callcomposer.camera.camerafocus \
	--extra-packages com.android.dialer.callcomposer.cameraui \
	--extra-packages com.android.dialer.calldetails \
	--extra-packages com.android.dialer.calllog.ui \
	--extra-packages com.android.dialer.calllogutils \
	--extra-packages com.android.dialer.common \
        --extra-packages com.android.dialer.contactactions \
	--extra-packages com.android.dialer.contactsfragment \
	--extra-packages com.android.dialer.dialpadview \
	--extra-packages com.android.dialer.enrichedcall.simulator \
	--extra-packages com.android.dialer.interactions \
	--extra-packages com.android.dialer.main.impl \
	--extra-packages com.android.dialer.notification \
	--extra-packages com.android.dialer.oem \
	--extra-packages com.android.dialer.phonenumberutil \
	--extra-packages com.android.dialer.postcall \
	--extra-packages com.android.dialer.searchfragment.common \
	--extra-packages com.android.dialer.searchfragment.list \
	--extra-packages com.android.dialer.searchfragment.nearbyplaces \
	--extra-packages com.android.dialershared.bubble \
	--extra-packages com.android.dialer.shortcuts \
	--extra-packages com.android.dialer.speeddial \
	--extra-packages com.android.dialer.theme \
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
	--extra-packages com.android.incallui.calllocation \
	--extra-packages com.android.incallui.calllocation.impl \
	--extra-packages com.android.incallui.commontheme \
	--extra-packages com.android.incallui.contactgrid \
	--extra-packages com.android.incallui.disconnectdialog \
	--extra-packages com.android.incallui.hold \
	--extra-packages com.android.incallui.incall.impl \
	--extra-packages com.android.incallui.maps.impl \
	--extra-packages com.android.incallui.sessiondata \
	--extra-packages com.android.incallui.speakerbuttonlogic \
	--extra-packages com.android.incallui.telecomeventui \
	--extra-packages com.android.incallui.video \
	--extra-packages com.android.incallui.video.impl \
	--extra-packages com.android.phone.common \
	--extra-packages com.android.voicemail \
	--extra-packages com.android.voicemail.impl.configui \
	--extra-packages com.android.voicemail.impl \
	--extra-packages com.android.voicemail.impl.fetch \
	--extra-packages com.android.voicemail.impl.settings \
	--extra-packages com.android.voicemail.settings \
	--extra-packages me.leolin.shortcutbadger \


LOCAL_STATIC_JAVA_LIBRARIES := \
	android-common \
	android-support-dynamic-animation \
	com.android.vcard \
	dialer-commons-io-target \
	dialer-dagger2-target \
	dialer-disklrucache-target \
	dialer-gifdecoder-target \
	dialer-glide-target \
        dialer-grpc-all-target \
        dialer-grpc-context-target \
        dialer-grpc-core-target \
	dialer-grpc-okhttp-target \
	dialer-grpc-protobuf-lite-target \
        dialer-grpc-stub-target \
	dialer-guava-target \
	dialer-javax-annotation-api-target \
	dialer-javax-inject-target \
	dialer-libshortcutbadger-target \
	dialer-mime4j-core-target \
	dialer-mime4j-dom-target \
	jsr305 \
	legacy-test \
	libphonenumber \
	okhttp \
	volley \
	org.lineageos.platform.sdk

LOCAL_STATIC_ANDROID_LIBRARIES := \
	android-support-design \
	android-support-transition \
	android-support-v13 \
	android-support-v4 \
	android-support-v7-appcompat \
	android-support-v7-cardview \
	android-support-v7-recyclerview \

LOCAL_JAVA_LIBRARIES := \
	dialer-auto-value \
	org.apache.http.legacy \

# Libraries needed by the compiler (JACK) to generate code.
PROCESSOR_LIBRARIES_TARGET := \
	dialer-auto-value \
	dialer-dagger2 \
	dialer-dagger2-compiler \
	dialer-dagger2-producers \
	dialer-guava \
	dialer-javax-annotation-api \
	dialer-javax-inject \

# Resolve the jar paths.
PROCESSOR_JARS := $(call java-lib-deps, $(PROCESSOR_LIBRARIES_TARGET))
# Necessary for annotation processors to work correctly.
LOCAL_ADDITIONAL_DEPENDENCIES += $(PROCESSOR_JARS)

LOCAL_JACK_FLAGS += --processorpath $(call normalize-path-list,$(PROCESSOR_JARS))
LOCAL_JAVACFLAGS += -processorpath $(call normalize-path-list,$(PROCESSOR_JARS))


# Begin Bug: 37077388
LOCAL_DX_FLAGS := --multi-dex
LOCAL_JACK_FLAGS := --multi-dex native

LOCAL_PROGUARD_ENABLED := disabled
ifdef LOCAL_JACK_ENABLED
# Proguard includes
LOCAL_PROGUARD_FLAG_FILES := \
    java/com/android/dialer/common/proguard.flags \
    java/com/android/dialer/proguard/proguard_base.flags \
    java/com/android/dialer/proguard/proguard.flags \
    java/com/android/dialer/proguard/proguard_release.flags \
    java/com/android/incallui/answer/impl/proguard.flags
LOCAL_PROGUARD_ENABLED := custom

LOCAL_PROGUARD_ENABLED += optimization
else
LOCAL_PROGUARD_ENABLED := disabled
LOCAL_DX_FLAGS := --multi-dex
endif

# End Bug: 37077388

LOCAL_MODULE_TAGS := optional
LOCAL_PACKAGE_NAME := Dialer
LOCAL_CERTIFICATE := shared
LOCAL_PRIVILEGED_MODULE := true
LOCAL_USE_AAPT2 := true

# b/37483961 - Jack Not Compiling Dagger Class Properly
LOCAL_JACK_ENABLED := javac_frontend

include $(BUILD_PACKAGE)

# Cleanup local state
BASE_DIR :=
SRC_DIRS :=
EXCLUDE_FILES :=
RES_DIRS :=
DIALER_MANIFEST_FILES :=
PROCESSOR_LIBRARIES_TARGET :=
PROCESSOR_JARS :=
BACKUP_LIB_SRC_DIR :=
EXCLUDE_BACKUP_LIB_SRCS :=

# Create references to prebuilt libraries.
include $(CLEAR_VARS)

LOCAL_PREBUILT_STATIC_JAVA_LIBRARIES := \
    dialer-auto-value:../../../prebuilts/tools/common/m2/repository/com/google/auto/value/auto-value/1.3/auto-value-1.3$(COMMON_JAVA_PACKAGE_SUFFIX) \
    dialer-dagger2-compiler:../../../prebuilts/tools/common/m2/repository/com/google/dagger/dagger-compiler/2.7/dagger-compiler-2.7$(COMMON_JAVA_PACKAGE_SUFFIX) \
    dialer-dagger2:../../../prebuilts/tools/common/m2/repository/com/google/dagger/dagger/2.7/dagger-2.7$(COMMON_JAVA_PACKAGE_SUFFIX) \
    dialer-dagger2-producers:../../../prebuilts/tools/common/m2/repository/com/google/dagger/dagger-producers/2.7/dagger-producers-2.7$(COMMON_JAVA_PACKAGE_SUFFIX) \
    dialer-grpc-all:../../../prebuilts/tools/common/m2/repository/io/grpc/grpc-all/1.0.3/grpc-all-1.0.3$(COMMON_JAVA_PACKAGE_SUFFIX) \
    dialer-grpc-core:../../../prebuilts/tools/common/m2/repository/io/grpc/grpc-core/1.0.3/grpc-core-1.0.3$(COMMON_JAVA_PACKAGE_SUFFIX) \
    dialer-grpc-okhttp:../../../prebuilts/tools/common/m2/repository/io/grpc/grpc-okhttp/1.0.3/grpc-okhttp-1.0.3$(COMMON_JAVA_PACKAGE_SUFFIX) \
    dialer-grpc-protobuf-lite:../../../prebuilts/tools/common/m2/repository/io/grpc/grpc-protobuf-lite/1.0.3/grpc-protobuf-lite-1.0.3$(COMMON_JAVA_PACKAGE_SUFFIX) \
    dialer-grpc-stub:../../../prebuilts/tools/common/m2/repository/io/grpc/grpc-stub/1.0.3/grpc-stub-1.0.3$(COMMON_JAVA_PACKAGE_SUFFIX) \
    dialer-guava:../../../prebuilts/tools/common/m2/repository/com/google/guava/guava/20.0/guava-20.0$(COMMON_JAVA_PACKAGE_SUFFIX) \
    dialer-javax-annotation-api:../../../prebuilts/tools/common/m2/repository/javax/annotation/javax.annotation-api/1.2/javax.annotation-api-1.2$(COMMON_JAVA_PACKAGE_SUFFIX) \
    dialer-javax-inject:../../../prebuilts/tools/common/m2/repository/javax/inject/javax.inject/1/javax.inject-1$(COMMON_JAVA_PACKAGE_SUFFIX)

include $(BUILD_MULTI_PREBUILT)

# Enumerate target prebuilts to avoid linker warnings like
# Dialer (java:sdk) should not link to dialer-guava (java:platform)
include $(CLEAR_VARS)

LOCAL_MODULE_CLASS := JAVA_LIBRARIES
LOCAL_MODULE := dialer-guava-target
LOCAL_SDK_VERSION := current
LOCAL_SRC_FILES := ../../../prebuilts/tools/common/m2/repository/com/google/guava/guava/20.0/guava-20.0$(COMMON_JAVA_PACKAGE_SUFFIX)
LOCAL_UNINSTALLABLE_MODULE := true

include $(BUILD_PREBUILT)

include $(CLEAR_VARS)

LOCAL_MODULE_CLASS := JAVA_LIBRARIES
LOCAL_MODULE := dialer-dagger2-target
LOCAL_SDK_VERSION := current
LOCAL_SRC_FILES := ../../../prebuilts/tools/common/m2/repository/com/google/dagger/dagger/2.7/dagger-2.7$(COMMON_JAVA_PACKAGE_SUFFIX)
LOCAL_UNINSTALLABLE_MODULE := true

include $(BUILD_PREBUILT)

include $(CLEAR_VARS)

LOCAL_MODULE_CLASS := JAVA_LIBRARIES
LOCAL_MODULE := dialer-disklrucache-target
LOCAL_SDK_VERSION := current
LOCAL_SRC_FILES := ../../../prebuilts/maven_repo/bumptech/com/github/bumptech/glide/disklrucache/SNAPSHOT/disklrucache-SNAPSHOT$(COMMON_JAVA_PACKAGE_SUFFIX)
LOCAL_UNINSTALLABLE_MODULE := true

include $(BUILD_PREBUILT)

include $(CLEAR_VARS)

LOCAL_MODULE_CLASS := JAVA_LIBRARIES
LOCAL_MODULE := dialer-gifdecoder-target
LOCAL_SDK_VERSION := current
LOCAL_SRC_FILES := ../../../prebuilts/maven_repo/bumptech/com/github/bumptech/glide/gifdecoder/SNAPSHOT/gifdecoder-SNAPSHOT$(COMMON_JAVA_PACKAGE_SUFFIX)
LOCAL_UNINSTALLABLE_MODULE := true

include $(BUILD_PREBUILT)

include $(CLEAR_VARS)

LOCAL_MODULE_CLASS := JAVA_LIBRARIES
LOCAL_MODULE := dialer-glide-target
LOCAL_SDK_VERSION := current
LOCAL_SRC_FILES := ../../../prebuilts/maven_repo/bumptech/com/github/bumptech/glide/glide/SNAPSHOT/glide-SNAPSHOT$(COMMON_JAVA_PACKAGE_SUFFIX)
LOCAL_UNINSTALLABLE_MODULE := true

include $(BUILD_PREBUILT)

include $(CLEAR_VARS)

LOCAL_MODULE_CLASS := JAVA_LIBRARIES
LOCAL_MODULE := dialer-javax-annotation-api-target
LOCAL_SDK_VERSION := current
LOCAL_SRC_FILES := ../../../prebuilts/tools/common/m2/repository/javax/annotation/javax.annotation-api/1.2/javax.annotation-api-1.2$(COMMON_JAVA_PACKAGE_SUFFIX)
LOCAL_UNINSTALLABLE_MODULE := true

include $(BUILD_PREBUILT)

include $(CLEAR_VARS)

LOCAL_MODULE_CLASS := JAVA_LIBRARIES
LOCAL_MODULE := dialer-libshortcutbadger-target
LOCAL_SDK_VERSION := current
LOCAL_SRC_FILES := ../../../prebuilts/tools/common/m2/repository/me/leolin/ShortcutBadger/1.1.13/ShortcutBadger-1.1.13$(COMMON_JAVA_PACKAGE_SUFFIX)
LOCAL_UNINSTALLABLE_MODULE := true

include $(BUILD_PREBUILT)

include $(CLEAR_VARS)

LOCAL_MODULE_CLASS := JAVA_LIBRARIES
LOCAL_MODULE := dialer-javax-inject-target
LOCAL_SDK_VERSION := current
LOCAL_SRC_FILES := ../../../prebuilts/tools/common/m2/repository/javax/inject/javax.inject/1/javax.inject-1$(COMMON_JAVA_PACKAGE_SUFFIX)
LOCAL_UNINSTALLABLE_MODULE := true

include $(BUILD_PREBUILT)

include $(CLEAR_VARS)

LOCAL_MODULE_CLASS := JAVA_LIBRARIES
LOCAL_MODULE := dialer-commons-io-target
LOCAL_SDK_VERSION := current
LOCAL_SRC_FILES := ../../../prebuilts/tools/common/m2/repository/commons-io/commons-io/2.4/commons-io-2.4$(COMMON_JAVA_PACKAGE_SUFFIX)
LOCAL_UNINSTALLABLE_MODULE := true

include $(BUILD_PREBUILT)

include $(CLEAR_VARS)

LOCAL_MODULE_CLASS := JAVA_LIBRARIES
LOCAL_MODULE := dialer-mime4j-core-target
LOCAL_SDK_VERSION := current
LOCAL_SRC_FILES := ../../../prebuilts/tools/common/m2/repository/org/apache/james/apache-mime4j-core/0.7.2/apache-mime4j-core-0.7.2$(COMMON_JAVA_PACKAGE_SUFFIX)
LOCAL_UNINSTALLABLE_MODULE := true

include $(BUILD_PREBUILT)

include $(CLEAR_VARS)

LOCAL_MODULE_CLASS := JAVA_LIBRARIES
LOCAL_MODULE := dialer-mime4j-dom-target
LOCAL_SDK_VERSION := current
LOCAL_SRC_FILES := ../../../prebuilts/tools/common/m2/repository/org/apache/james/apache-mime4j-dom/0.7.2/apache-mime4j-dom-0.7.2$(COMMON_JAVA_PACKAGE_SUFFIX)
LOCAL_UNINSTALLABLE_MODULE := true

include $(BUILD_PREBUILT)

include $(CLEAR_VARS)

LOCAL_MODULE_CLASS := JAVA_LIBRARIES
LOCAL_MODULE := dialer-grpc-core-target
LOCAL_SDK_VERSION := current
LOCAL_SRC_FILES := ../../../prebuilts/tools/common/m2/repository/io/grpc/grpc-core/1.0.3/grpc-core-1.0.3$(COMMON_JAVA_PACKAGE_SUFFIX)
LOCAL_UNINSTALLABLE_MODULE := true

include $(BUILD_PREBUILT)

include $(CLEAR_VARS)

LOCAL_MODULE_CLASS := JAVA_LIBRARIES
LOCAL_MODULE := dialer-grpc-okhttp-target
LOCAL_SDK_VERSION := current
LOCAL_SRC_FILES := ../../../prebuilts/tools/common/m2/repository/io/grpc/grpc-okhttp/1.0.3/grpc-okhttp-1.0.3$(COMMON_JAVA_PACKAGE_SUFFIX)
LOCAL_UNINSTALLABLE_MODULE := true

include $(BUILD_PREBUILT)

include $(CLEAR_VARS)

LOCAL_MODULE_CLASS := JAVA_LIBRARIES
LOCAL_MODULE := dialer-grpc-protobuf-lite-target
LOCAL_SDK_VERSION := current
LOCAL_SRC_FILES := ../../../prebuilts/tools/common/m2/repository/io/grpc/grpc-protobuf-lite/1.0.3/grpc-protobuf-lite-1.0.3$(COMMON_JAVA_PACKAGE_SUFFIX)
LOCAL_UNINSTALLABLE_MODULE := true

include $(BUILD_PREBUILT)

include $(CLEAR_VARS)

LOCAL_MODULE_CLASS := JAVA_LIBRARIES
LOCAL_MODULE := dialer-grpc-stub-target
LOCAL_SDK_VERSION := current
LOCAL_SRC_FILES := ../../../prebuilts/tools/common/m2/repository/io/grpc/grpc-stub/1.0.3/grpc-stub-1.0.3$(COMMON_JAVA_PACKAGE_SUFFIX)
LOCAL_UNINSTALLABLE_MODULE := true

include $(BUILD_PREBUILT)

include $(CLEAR_VARS)

LOCAL_MODULE_CLASS := JAVA_LIBRARIES
LOCAL_MODULE := dialer-grpc-all-target
LOCAL_SDK_VERSION := current
LOCAL_SRC_FILES := ../../../prebuilts/tools/common/m2/repository/io/grpc/grpc-all/1.0.3/grpc-all-1.0.3$(COMMON_JAVA_PACKAGE_SUFFIX)
LOCAL_UNINSTALLABLE_MODULE := true

include $(BUILD_PREBUILT)

include $(CLEAR_VARS)

LOCAL_MODULE_CLASS := JAVA_LIBRARIES
LOCAL_MODULE := dialer-grpc-context-target
LOCAL_SDK_VERSION := current
LOCAL_SRC_FILES := ../../../prebuilts/tools/common/m2/repository/io/grpc/grpc-context/1.0.3/grpc-context-1.0.3$(COMMON_JAVA_PACKAGE_SUFFIX)
LOCAL_UNINSTALLABLE_MODULE := true

include $(BUILD_PREBUILT)

include $(CLEAR_VARS)