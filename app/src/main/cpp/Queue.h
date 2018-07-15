//
// Created by Administrator on 2018/7/2 0002.
//

#ifndef MULDECODEDEMO_BLOCKINGQUEUE_H
#define MULDECODEDEMO_BLOCKINGQUEUE_H

#include <pthread.h>
#include <sys/types.h>
#include "FFmpegDEcode.h"
#include "Queue.h"

typedef struct MyAVPacketList {
    AVPacket pkt;
    struct MyAVPacketList *next;
    int serial;
} MyAVPacketList;

typedef struct PacketQueue {
    MyAVPacketList *first_pkt, *last_pkt;
    int nb_packets;
    int size;
    int64_t duration;
    int abort_request;
    int serial;
    pthread_mutex_t mutex;
    pthread_cond_t cond;
} PacketQueue;

class MyPacketQueue {

public:
    AVPacket flush_pkt;

    MyPacketQueue();
    ~MyPacketQueue();

    int packet_queue_put_private(PacketQueue *q, AVPacket *pkt);
    int packet_queue_put(PacketQueue *q, AVPacket *pkt);
    int packet_queue_put_nullpacket(PacketQueue *q, int stream_index);
    int packet_queue_init(PacketQueue *q);
    void packet_queue_flush(PacketQueue *q);
    void packet_queue_destroy(PacketQueue *q);
    void packet_queue_abort(PacketQueue *q);
    void packet_queue_start(PacketQueue *q);
    int packet_queue_get(PacketQueue *q, AVPacket *pkt, int block, int *serial);
};


typedef struct MyAVFrameList {
    char* data;
    struct MyAVFrameList *next;
} MyFrameList;

typedef struct FrameQueue {
    MyAVFrameList *first_pkt, *last_pkt;
    int nb_frames;
    int size;
    int abort_request;
    pthread_mutex_t mutex;
    pthread_cond_t cond;
} FrameQueue;

class MyFrameQueue  {
public:
    MyFrameQueue();
    ~MyFrameQueue();

    int frame_queue_put_private(FrameQueue *q, char *data);
    int frame_queue_put(FrameQueue *q, char *data);
    int frame_queue_get(FrameQueue *q, char **data, int block);
    int frame_queue_init(FrameQueue *q);
    void frame_queue_flush(FrameQueue *q);
    void frame_queue_destroy(FrameQueue *q);
    void frame_queue_abort(FrameQueue *q);
};

#endif //MULDECODEDEMO_BLOCKINGQUEUE_H
