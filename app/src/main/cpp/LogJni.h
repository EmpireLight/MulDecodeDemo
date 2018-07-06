//
// Created by Administrator on 2018/6/28 0028.
//

#ifndef MULDECODEDEMO_LOGJNI_H
#define MULDECODEDEMO_LOGJNI_H

#include <android/log.h>

#define  LOG_TAG "decoder"

#define LOGV(...) __android_log_print(ANDROID_LOG_VERBOSE, LOG_TAG, __VA_ARGS__)
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG ,  LOG_TAG, __VA_ARGS__)
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO  ,  LOG_TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN  ,  LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR  , LOG_TAG, __VA_ARGS__)

#endif //MULDECODEDEMO_LOGJNI_H
