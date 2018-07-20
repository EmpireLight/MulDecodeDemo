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
    int videoWidth;
    int videoHeight;
    int convertWidth;
    int convertHeight;

    int currentTime;//时间单位应该转换为毫秒
    int duration;
    int decoderNum;

public:
    Player();
    ~Player();

    AVFormatContext * avFormatContext;
    AVCodecContext * avctxAudio;
    AVCodecContext * avctxVideo;
    AVCodecContext * avctxSubtitle;
    int st_index[AVMEDIA_TYPE_NB];
    uint8_t *frame_buffer_out;

    size_t yuvSize;
    size_t yFrameSize;
    size_t uvFrameSize;

    AVPacket flush_pkt;

    MyPacketQueue *q;
    MyFrameQueue *frameQueue;

    int abort_request;
    int paused;
    int seek_req;
    int64_t seek_pos;

    FrameQueue sampq;//音频队列
    FrameQueue pictq;//图像队列
    FrameQueue subpq;//字幕队列

//Video
    int video_stream;
    AVStream *video_st;
    PacketQueue videoq;
    //int width, height, xleft, ytop;
    struct SwsContext *img_convert_ctx;
    FrameQueue vFrameQueue;
//Audio
    int audio_stream;
    AVStream *audio_st;
    PacketQueue audioq;
    int sample_rate;
    int nb_channels;
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
    int getVideoData(uint8_t* data);
    int getAudioData(uint8_t* data);

    void stream_component_close(int stream_index);

    int restart();
    bool is_end();
    int display_video();
    int display_audio();

    static void* read_pth(void *arg);
    static void* video_thread(void *arg);
    static void* audio_thread(void *arg);

    int getCurTime();//获取当前时间
    int getDuration();
    int getVideoWidth();
    int getVideoHeight();
    int getConvertWidth();
    int getConvettHeight();

    pthread_t video_tid;
    pthread_t audio_tid;
    pthread_t read_tid;
    pthread_cond_t continue_read_thread;
};

#endif //MULDECODEDEMO_DECODER_H
