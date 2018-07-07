//
// Created by Administrator on 2018/7/5 0005.
//

#include <unistd.h>
#include "FFmpegJni.h"

JavaVM *g_jvm = NULL;
jobject g_obj = NULL;

Decoder *decoder0 = NULL;
Decoder *decoder1 = NULL;

Decoder *decoder[2];

char* jstring2string(JNIEnv* env, jstring jstr)
{
    char* rtn = NULL;
    jclass clsstring = env->FindClass("java/lang/String");
    jstring strencode = env->NewStringUTF("utf-8");
    jmethodID mid = env->GetMethodID(clsstring, "getBytes", "(Ljava/lang/String;)[B");
    jbyteArray barr= (jbyteArray)env->CallObjectMethod(jstr, mid, strencode);
    jsize alen = env->GetArrayLength(barr);
    jbyte* ba = env->GetByteArrayElements(barr, JNI_FALSE);

    if (alen > 0)
    {
        //TODO 记得free
        rtn = (char*)malloc(alen + 1);

        memcpy(rtn, ba, alen);
        rtn[alen] = 0;
    }
    env->ReleaseByteArrayElements(barr, ba, 0);
    return rtn;
}

void setJNIEnv(JNIEnv* env, jobject obj) {
    //保存全局JVM以便在子线程中使用
    env->GetJavaVM(&g_jvm);
    //不能直接赋值(g_obj = obj)
    g_obj = env->NewGlobalRef(obj);
}
/**************************************************************************************************************************
YUV转换
/**************************************************************************************************************************/
FILE *fp_yuv0 = NULL;
FILE *fp_yuv1 = NULL;

extern "C"
JNIEXPORT jint JNICALL
Java_com_xmb_muldecodedemo_filter_YUVFilter_initdecoder(JNIEnv *env, jobject instance,
                                                        jstring path_, jint seqNumber) {
    const char *path = env->GetStringUTFChars(path_, 0);

    // TODO
    LOGE("path = %s", path);
    decoder[seqNumber] = new Decoder();
    decoder[seqNumber]->start(path, seqNumber);

//    if (seqNumber == 0) {
//        fp_yuv0 = fopen("/storage/emulated/0/outyuv0.yuv","wb+");
//
//    } else if (seqNumber == 1) {
//        fp_yuv1 = fopen("/storage/emulated/0/outyuv1.yuv","wb+");
//    }

    env->ReleaseStringUTFChars(path_, path);
    return 0;
}

extern "C"
JNIEXPORT jint JNICALL
Java_com_xmb_muldecodedemo_filter_YUVFilter_updateData(JNIEnv *env, jobject instance,
                                                       jbyteArray data_, jint seqNumber) {
    jbyte *data = env->GetByteArrayElements(data_, JNI_FALSE);
    jint data_lenght = env->GetArrayLength(data_);;

    int ret = -1;
    ret = decoder[seqNumber]->decode((uint8_t *)data);
//    int size = decoder[seqNumber]->getVideoWidth() * decoder[seqNumber]->getVideoHeight() * 3 /2;
//    if (seqNumber == 0) {
//        fwrite(data, 1, size, fp_yuv0);
//    } else if (seqNumber == 1) {
//        fwrite(data, 1, size, fp_yuv1);
//    }
//    sync();

    env->ReleaseByteArrayElements(data_, data, 0);

    return ret;
}

extern "C"
JNIEXPORT jint JNICALL
Java_com_xmb_muldecodedemo_filter_YUVFilter_getYUVWidth(JNIEnv *env, jobject instance, jint seqNumber) {

    // TODO
    return decoder[seqNumber]->getVideoWidth();
}

extern "C"
JNIEXPORT jint JNICALL
Java_com_xmb_muldecodedemo_filter_YUVFilter_getYUVHeight(JNIEnv *env, jobject instance, jint seqNumber) {

    // TODO
    return decoder[seqNumber]->getVideoHeight();
}