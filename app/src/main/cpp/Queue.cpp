//
// Created by Administrator on 2018/7/2 0002.
//

#include <malloc.h>
#include "Queue.h"
#include "LogJni.h"


MyPacketQueue::MyPacketQueue() {

    pthread_mutex_init(&mutex, NULL);
    pthread_cond_init(&cond, NULL);

    av_init_packet(&flush_pkt);
    flush_pkt.data = (uint8_t *)&flush_pkt;
}

MyPacketQueue::~MyPacketQueue() {
//    flush();
    pthread_mutex_destroy(&mutex);
    pthread_cond_destroy(&cond);
}

int MyPacketQueue::packet_queue_put_private(PacketQueue *q, AVPacket *pkt)
{
    MyAVPacketList *pkt1;

    if (q->abort_request)
        return -1;

    pkt1 = ( MyAVPacketList *)av_malloc(sizeof(MyAVPacketList));
    if (!pkt1)
        return -1;
    pkt1->pkt = *pkt;
    pkt1->next = NULL;
    if (pkt == &flush_pkt)
        q->serial++;
    pkt1->serial = q->serial;

    if (!q->last_pkt)
        q->first_pkt = pkt1;
    else
        q->last_pkt->next = pkt1;
    q->last_pkt = pkt1;
    q->nb_packets++;
//    LOGE("packet_queue_put_private nb_packets = %d", q->nb_packets);
    q->size += pkt1->pkt.size + sizeof(*pkt1);
    q->duration += pkt1->pkt.duration;
    /* XXX: should duplicate packet data in DV case */
    pthread_cond_signal(&q->cond);
    return 0;
}

int MyPacketQueue::packet_queue_put(PacketQueue *q, AVPacket *pkt)
{
    int ret;

    pthread_mutex_lock(&q->mutex);
    ret = packet_queue_put_private(q, pkt);
    pthread_mutex_unlock(&q->mutex);

    if (pkt != &flush_pkt && ret < 0)
        av_packet_unref(pkt);

    return ret;
}

int MyPacketQueue::packet_queue_put_nullpacket(PacketQueue *q, int stream_index)
{
    AVPacket pkt1, *pkt = &pkt1;
    av_init_packet(pkt);
    pkt->data = NULL;
    pkt->size = 0;
    pkt->stream_index = stream_index;
    return packet_queue_put(q, pkt);
}

/* packet queue handling */
int MyPacketQueue::packet_queue_init(PacketQueue *q)
{
    memset(q, 0, sizeof(PacketQueue));
    pthread_mutex_init(&q->mutex, NULL);
    pthread_cond_init(&q->cond, NULL);

    q->abort_request = 1;
    return 0;
}

void MyPacketQueue::packet_queue_flush(PacketQueue *q)
{
    MyAVPacketList *pkt, *pkt1;

    pthread_mutex_lock(&q->mutex);
    for (pkt = q->first_pkt; pkt; pkt = pkt1) {
        pkt1 = pkt->next;
        av_packet_unref(&pkt->pkt);
        av_freep(&pkt);
    }
    q->last_pkt = NULL;
    q->first_pkt = NULL;
    q->nb_packets = 0;
    q->size = 0;
    q->duration = 0;
    pthread_mutex_unlock(&q->mutex);
}

void MyPacketQueue::packet_queue_destroy(PacketQueue *q)
{
    packet_queue_flush(q);

    pthread_mutex_destroy(&q->mutex);
    pthread_cond_destroy(&q->cond);
}

void MyPacketQueue::packet_queue_abort(PacketQueue *q)
{
    pthread_mutex_lock(&q->mutex);

    q->abort_request = 1;

    pthread_cond_signal(&q->cond);

    pthread_mutex_unlock(&q->mutex);
}

void MyPacketQueue::packet_queue_start(PacketQueue *q)
{
    pthread_mutex_lock(&q->mutex);
    q->abort_request = 0;
    packet_queue_put_private(q, &flush_pkt);
    pthread_mutex_unlock(&q->mutex);
}

//block 阻塞:1 非阻塞:0
/* return < 0 if aborted, 0 if no packet and > 0 if packet.  */
int MyPacketQueue::packet_queue_get(PacketQueue *q, AVPacket *pkt, int block, int *serial)
{
    MyAVPacketList *pkt1;
    int ret;

    pthread_mutex_lock(&q->mutex);

    for (;;) {
        if (q->abort_request) {
            ret = -1;
            break;
        }

        pkt1 = q->first_pkt;
        if (pkt1) {
            q->first_pkt = pkt1->next;
            if (!q->first_pkt)
                q->last_pkt = NULL;
            q->nb_packets--;
            LOGE("packet_queue_get nb_packets = %d", q->nb_packets);
            q->size -= pkt1->pkt.size + sizeof(*pkt1);
            q->duration -= pkt1->pkt.duration;
            *pkt = pkt1->pkt;
            if (serial)
                *serial = pkt1->serial;
            av_free(pkt1);
            ret = 1;
            break;
        } else if (!block) {
            ret = 0;
            break;
        } else {
            LOGE("packet_queue_get nb_packets = %d pthread_cond_wait", q->nb_packets);
            pthread_cond_wait(&q->cond, &q->mutex);
        }
    }
    pthread_mutex_unlock(&q->mutex);
    return ret;
}

int MyFrameQueue::frame_queue_init(queue<char *> *q) {
    pthread_mutex_init(&mutex, NULL);
    pthread_cond_init(&cond, NULL);
}

int MyFrameQueue::frame_queue_put(queue<char *> *q, char *data) {
    pthread_mutex_lock(&mutex);
    q->push(data);
    pthread_cond_signal(&cond);
    pthread_mutex_unlock(&mutex);
}

int MyFrameQueue::frame_queue_get(queue<char *> *q, char *data) {
    pthread_mutex_lock(&mutex);

    if (q->empty()) {
        pthread_cond_wait(&cond, &mutex);
    } else {
        data = q->front();
        q->pop();
    }

    pthread_mutex_unlock(&mutex);
}