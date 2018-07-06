//
// Created by Administrator on 2018/7/2 0002.
//

#ifndef MULDECODEDEMO_BLOCKINGQUEUE_H
#define MULDECODEDEMO_BLOCKINGQUEUE_H

#include <pthread.h>
#include <sys/types.h>
#include "FFmpegDEcode.h"


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

class BlockingQueue {

public:
    AVPacket flush_pkt;

    BlockingQueue();
    ~BlockingQueue();

    int packet_queue_put_private(PacketQueue *q, AVPacket *pkt);
    int packet_queue_put(PacketQueue *q, AVPacket *pkt);
    int packet_queue_put_nullpacket(PacketQueue *q, int stream_index);
    int packet_queue_init(PacketQueue *q);
    void packet_queue_flush(PacketQueue *q);
    void packet_queue_destroy(PacketQueue *q);
    void packet_queue_abort(PacketQueue *q);
    void packet_queue_start(PacketQueue *q);
    int packet_queue_get(PacketQueue *q, AVPacket *pkt, int block, int *serial);

private:
    pthread_mutex_t mutex;
    pthread_cond_t cond;
};
#endif //MULDECODEDEMO_BLOCKINGQUEUE_H
