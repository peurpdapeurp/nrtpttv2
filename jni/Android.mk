
JNI_FOLDER_PATH := $(call my-dir)
NDK_PACKAGES_ROOT := $(JNI_FOLDER_PATH)/../../../../../../AppData/Local/Android/Sdk/ndk-bundle/packages

# Build my test file
include $(CLEAR_VARS)
LOCAL_PATH := $(JNI_FOLDER_PATH)
LOCAL_C_INCLUDES := \
    $(NDK_PACKAGES_ROOT)/ndnrtc/0.0.2/include \
    $(NDK_PACKAGES_ROOT)/ndn_cpp/0.16-48-g4ace2ff4/include
LOCAL_MODULE := test
LOCAL_SRC_FILES := test.c
include $(BUILD_SHARED_LIBRARY)

# Build the NDN-RTC client
include $(CLEAR_VARS)
LOCAL_PATH := $(JNI_FOLDER_PATH)/ndnrtc/cpp/client
LOCAL_CFLAGS := -D__ANDROID__
LOCAL_C_INCLUDES := \
    $(NDK_PACKAGES_ROOT)/ndnrtc/0.0.2/include \
    $(NDK_PACKAGES_ROOT)/ndn_cpp/0.16-48-g4ace2ff4/include \
    $(JNI_FOLDER_PATH)/ndnrtc/cpp/client/src \
    $(JNI_FOLDER_PATH)/ndnrtc/cpp/client/nanopipe-adaptor \
    $(NDK_PACKAGES_ROOT)/boost/1.70.0/include
LOCAL_MODULE := ndn-rtc-client
LOCAL_SRC_FILES := \
    src/client.cpp \
    src/config.cpp \
    src/frame-io.cpp \
    src/main.cpp \
    src/precise-generator.cpp \
    src/renderer.cpp \
    src/stat-collector.cpp \
    src/video-source.cpp
include $(BUILD_SHARED_LIBRARY)

# Explicitly define versions of precompiled modules
$(call import-module,../packages/boost/1.70.0)
$(call import-module,../packages/ndn_cpp/0.16-48-g4ace2ff4)
$(call import-module,../packages/ndnrtc/0.0.2)
$(call import-module,../packages/openfec/1.4.2)
$(call import-module,../packages/openssl/1.1.1-pre8)
$(call import-module,../packages/protobuf/3.7.1)
$(call import-module,../packages/sqlite/3.18.0)
$(call import-module,../packages/webrtc/59)
