
#include <string.h>
#include <jni.h>
#include <ndnrtc/ndnrtc-defines.hpp>

jstring Java_com_example_nrtpttv2_MainActivity_helloWorld(JNIEnv* env, jobject obj) {

    int x = RESULT_OK + RESULT_ERR;

    return (*env)->NewStringUTF(env,"Hello world");

}