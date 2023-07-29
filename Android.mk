# Local modifications:
# * removed com.google.android.geo.API_KEY key. This should be added to
#      the manifest files in java/com/android/incallui/calllocation/impl/
#      and /java/com/android/incallui/maps/impl/
# * b/62417801 modify translation string naming convention:
#      $ find . -type d | grep 262 | rename 's/(values)\-([a-zA-Z\+\-]+)\-(mcc262-mnc01)/$1-$3-$2/'
# * b/37077388 temporarily disable proguard with javac
# * b/62875795 include manually generated GRPC service class:
#      $ protoc --plugin=protoc-gen-grpc-java=prebuilts/tools/common/m2/repository/io/grpc/protoc-gen-grpc-java/1.0.3/protoc-gen-grpc-java-1.0.3-linux-x86_64.exe \
#               --grpc-java_out=lite:"packages/apps/Dialer/java/com/android/voicemail/impl/" \
#               --proto_path="packages/apps/Dialer/java/com/android/voicemail/impl/transcribe/grpc/" "packages/apps/Dialer/java/com/android/voicemail/impl/transcribe/grpc/voicemail_transcription.proto"
LOCAL_PATH:= $(call my-dir)
include $(CLEAR_VARS)

# The base directory for Dialer sources.
BASE_DIR := java/com/android

# Exclude testing only class, not used anywhere here
EXCLUDE_FILES += \
	$(BASE_DIR)/contacts/common/format/testing/SpannedTestUtils.java

# Exclude rootcomponentgenerator
EXCLUDE_FILES += \
	$(call all-java-files-under, $(BASE_DIR)/dialer/rootcomponentgenerator) \

# All Dialer resources.
RES_DIRS := $(call all-subdir-named-dirs,res,.)

# Dialer manifest files to merge.
DIALER_MANIFEST_FILES := $(call all-named-files-under,AndroidManifest.xml,.)

# Merge all manifest files.
LOCAL_FULL_LIBS_MANIFEST_FILES := \
	$(addprefix $(LOCAL_PATH)/, $(DIALER_MANIFEST_FILES))

LOCAL_SRC_FILES := $(call all-java-files-under, $(BASE_DIR))
LOCAL_SRC_FILES += $(call all-proto-files-under, $(BASE_DIR))
LOCAL_SRC_FILES += $(call all-Iaidl-files-under, $(BASE_DIR))
LOCAL_AIDL_INCLUDES :=  $(LOCAL_PATH)/java/
LOCAL_SRC_FILES := $(filter-out $(EXCLUDE_FILES),$(LOCAL_SRC_FILES))

LOCAL_AIDL_INCLUDES := $(LOCAL_PATH)/java

LOCAL_PROTOC_FLAGS := --proto_path=$(LOCAL_PATH)

LOCAL_RESOURCE_DIR := $(addprefix $(LOCAL_PATH)/, $(RES_DIRS))

# We specify each package explicitly to glob resource files.
include ${LOCAL_PATH}/packages.mk

LOCAL_AAPT_FLAGS := $(addprefix --extra-packages , $(LOCAL_AAPT_FLAGS))
LOCAL_AAPT_FLAGS += \
	--auto-add-overlay \

LOCAL_STATIC_JAVA_LIBRARIES := \
	android-common \
	dialer-common-m2-target-deps \
	dialer-glide-target-deps \
	error_prone_annotations \
	jsr305 \
	libphonenumber \
	volley \
	org.lineageos.lib.phone \
	androidx.annotation_annotation \
	androidx.cardview_cardview \
	androidx.collection_collection \
	androidx.coordinatorlayout_coordinatorlayout \
	androidx.core_core \
	androidx.dynamicanimation_dynamicanimation \
	androidx.localbroadcastmanager_localbroadcastmanager \
	androidx.preference_preference \
	androidx.recyclerview_recyclerview \
	com.google.android.material_material

LOCAL_JAVA_LIBRARIES := \
	auto_value_annotations \
	org.apache.http.legacy \

LOCAL_ANNOTATION_PROCESSORS := \
	auto_value_plugin \
	javapoet \
	dialer-common-m2-host-deps \
	dialer-dagger2-compiler-deps \
	dialer-glide-host-deps \
	dialer-rootcomponentprocessor

LOCAL_ANNOTATION_PROCESSOR_CLASSES := \
  com.google.auto.value.processor.AutoValueProcessor,dagger.internal.codegen.ComponentProcessor,com.bumptech.glide.annotation.compiler.GlideAnnotationProcessor,com.android.dialer.rootcomponentgenerator.RootComponentProcessor

# Proguard includes
LOCAL_PROGUARD_FLAG_FILES := proguard.flags $(call all-named-files-under,proguard.*flags,$(BASE_DIR))
LOCAL_PROGUARD_ENABLED := custom

LOCAL_PROGUARD_ENABLED += optimization

LOCAL_SDK_VERSION := system_current
LOCAL_MODULE_TAGS := optional
LOCAL_PACKAGE_NAME := Dialer
LOCAL_CERTIFICATE := shared
LOCAL_PRIVILEGED_MODULE := true
LOCAL_PRODUCT_MODULE := true
LOCAL_USE_AAPT2 := true
LOCAL_REQUIRED_MODULES := privapp_whitelist_com.android.dialer
LOCAL_REQUIRED_MODULES += privapp_whitelist_com.android.dialer-ext.xml
LOCAL_USES_LIBRARIES := org.apache.http.legacy

LOCAL_LICENSE_KINDS := SPDX-license-identifier-Apache-2.0
LOCAL_LICENSE_CONDITIONS := notice
LOCAL_NOTICE_FILE := $(LOCAL_PATH)/LICENSE
include $(BUILD_PACKAGE)

include $(CLEAR_VARS)
LOCAL_MODULE := privapp_whitelist_com.android.dialer-ext.xml
LOCAL_MODULE_CLASS := ETC
LOCAL_MODULE_TAGS := optional
LOCAL_MODULE_PATH := $(TARGET_OUT_PRODUCT_ETC)/permissions
LOCAL_PRODUCT_MODULE := true
LOCAL_SRC_FILES := $(LOCAL_MODULE)
include $(BUILD_PREBUILT)

# Cleanup local state
BASE_DIR :=
EXCLUDE_FILES :=
RES_DIRS :=
DIALER_MANIFEST_FILES :=
EXCLUDE_MANIFESTS :=
EXCLUDE_EXTRA_PACKAGES :=

include $(CLEAR_VARS)

LOCAL_MODULE := dialer-rootcomponentprocessor
LOCAL_LICENSE_KINDS := SPDX-license-identifier-Apache-2.0
LOCAL_LICENSE_CONDITIONS := notice
LOCAL_NOTICE_FILE := $(LOCAL_PATH)/LICENSE
LOCAL_MODULE_CLASS := JAVA_LIBRARIES
LOCAL_IS_HOST_MODULE := true
BASE_DIR := java/com/android

LOCAL_SRC_FILES := \
	$(call all-java-files-under, $(BASE_DIR)/dialer/rootcomponentgenerator) \
        $(BASE_DIR)/dialer/inject/HasRootComponent.java \
        $(BASE_DIR)/dialer/inject/IncludeInDialerRoot.java \
        $(BASE_DIR)/dialer/inject/RootComponentGeneratorMetadata.java

LOCAL_STATIC_JAVA_LIBRARIES := \
	dialer-common-m2-host-deps \
	javapoet \
	auto_service_annotations \
	auto_common \
	error_prone_annotations

LOCAL_JAVA_LANGUAGE_VERSION := 1.8

include $(BUILD_HOST_JAVA_LIBRARY)

include $(CLEAR_VARS)
