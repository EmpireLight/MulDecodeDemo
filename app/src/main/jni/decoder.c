#include <jni.h>

JNIEXPORT jstring JNICALL
Java_com_xmb_muldecodedemo_MainActivity_initdecoder(JNIEnv *env, jobject instance, jstring payh_) {
    const char *payh = (*env)->GetStringUTFChars(env, payh_, 0);

    // TODO

    (*env)->ReleaseStringUTFChars(env, payh_, payh);

    return (*env)->NewStringUTF(env, returnValue);
}