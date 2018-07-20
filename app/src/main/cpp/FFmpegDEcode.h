// 编解码所需要的ffmpeg头文件
// Created by Administrator on 2018/6/28 0028.
//

#ifndef MULDECODEDEMO_FFMPEGDECODE_H
#define MULDECODEDEMO_FFMPEGDECODE_H

extern "C"
{
#include <libavcodec/avcodec.h>
#include <libavformat/avformat.h>
#include <libswscale/swscale.h>
#include <libavutil/imgutils.h>
#include <libswresample/swresample.h>
};

#endif //MULDECODEDEMO_FFMPEGDECODE_H
