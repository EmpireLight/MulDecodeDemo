//
// Created by Administrator on 2018/6/25 0025.
//

#ifndef MULDECODEDEMO_DECODER_H
#define MULDECODEDEMO_DECODER_H

#include <pthread.h>
#include <unistd.h>
#include "Queue.h"

class Player {
private:
    int width;
    int height;
    long duration;
    int decoderNum;

public:
    Player();
    ~Player();

    AVFormatContext * avFormatContext;
    AVCodecContext * avctxAudio;
    AVCodecContext * avctxVideo;
    AVCodecContext * avctxSubtitle;
    AVFrame * avFrame;
    AVFrame * YUVFrame;
    int st_index[AVMEDIA_TYPE_NB];
    uint8_t *frame_buffer_out;

    size_t yuvSize;
    size_t yFrameSize;
    size_t uvFrameSize;

    int frame_count;
    MyPacketQueue *q;
    MyFrameQueue *frameQueue;

    int abort_request;
    int paused;
    int seek_req;
    int64_t seek_pos;

//Video
    int video_stream;
    AVStream *video_st;
    PacketQueue videoq;
    FrameQueue videoFrameq;
    //int width, height, xleft, ytop;
    struct SwsContext *img_convert_ctx;
    FrameQueue vFrameQueue;
//Audio
    int audio_stream;
    AVStream *audio_st;
    PacketQueue audioq;
    int audio_volume;
    int sample_rate;
    int channels;
    int64_t channel_layout;
    struct SwrContext *swr_ctx;
//Subtitle
    int subtitle_stream;
    AVStream *subtitle_st;
    PacketQueue subtitleq;

    int eof;
    char *filename;

    int stream_component_open(int stream_index);
    int init();
    void start(const char *filename, int num);
    int stop();
    void read();
    int decode(uint8_t* data);

    void stream_component_close(int stream_index);

    static void* read_pth(void *arg);

    int getVideoWidth();
    int getVideoHeight();

    pthread_cond_t continue_read_thread;
};

#endif //MULDECODEDEMO_DECODER_H
