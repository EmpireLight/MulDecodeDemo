//
// Created by Administrator on 2018/6/25 0025.
//

#ifndef MULDECODEDEMO_DECODER_H
#define MULDECODEDEMO_DECODER_H

#include <android/log.h>

extern "C"
{
#include <libavcodec/avcodec.h>
#include <libavformat/avformat.h>
#include <libswscale/swscale.h>
#include <libavutil/imgutils.h>
};

#define LOGV(...) __android_log_print(ANDROID_LOG_VERBOSE, "ProjectName", __VA_ARGS__)
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG ,  "ProjectName", __VA_ARGS__)
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO  ,  "ProjectName", __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN  ,  "ProjectName", __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR  , "ProjectName", __VA_ARGS__)

#endif //MULDECODEDEMO_DECODER_H
