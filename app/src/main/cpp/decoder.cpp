//
// Created by Administrator on 2018/6/25 0025.
//


#include "LogJni.h"
#include "FFmpegDEcode.h"
#include "decoder.h"
#include "Queue.h"

typedef struct Decoder {
    AVPacket pkt;
    AVPacket pkt_temp;
    PacketQueue *queue;
    AVCodecContext *avctx;
    int pkt_serial;
    int finished;
    int packet_pending;
    pthread_cond_t *empty_queue_cond;
    int64_t start_pts;
    AVRational start_pts_tb;
    int64_t next_pts;
    AVRational next_pts_tb;
//    Thread *decoder_tid;
} Decoder;
#define MAX_QUEUE_SIZE (15 * 1024 * 1024)


void get_abstime_wait(long timeout_ms, struct timespec *abstime)
{
    struct timeval now;

    gettimeofday(&now, NULL);
    long nsec = now.tv_usec * 1000 + (timeout_ms % 1000) * 1000000;
    abstime->tv_nsec = nsec % 1000000000;
    abstime->tv_sec = now.tv_sec + nsec / 1000000000 + timeout_ms / 1000;
}

Player::Player() {
    frame_count = 0;
    width = -1;
    height = -1;
    duration = -1;

    abort_request = 0;

    paused = 0;
    seek_req = 0;
    seek_pos = -1;

    avFormatContext = NULL;
    avctxAudio= NULL;
    avctxVideo = NULL;
    avctxSubtitle = NULL;

    memset(st_index, -1, sizeof(st_index));
    audio_stream = -1;
    video_stream = -1;
    subtitle_stream = -1;
    eof = 0;

    //TODO 记得清除
    q = new MyPacketQueue();
    frameQueue = new MyFrameQueue();

    pthread_cond_init(&continue_read_thread, NULL);
}

Player::~Player() {
    /* XXX: use a special url_shutdown call to abort parse cleanly */
//    is->abort_request = 1;
//    SDL_WaitThread(is->read_tid, NULL);

    /* close each stream */
    if (audio_stream >= 0)
        stream_component_close(audio_stream);
    if (video_stream >= 0)
        stream_component_close(video_stream);
    if (subtitle_stream >= 0)
        stream_component_close(subtitle_stream);
}

void Player::stream_component_close(int stream_index)
{
    AVFormatContext *ic = avFormatContext;
    AVCodecParameters *codecpar;

    if (stream_index < 0 || stream_index >= ic->nb_streams)
        return;
    codecpar = ic->streams[stream_index]->codecpar;

    switch (codecpar->codec_type) {
        case AVMEDIA_TYPE_AUDIO:
//            decoder_destroy(&is->auddec);
//            swr_free(&is->swr_ctx);
            break;
        case AVMEDIA_TYPE_VIDEO:
//            av_packet_unref(&d->pkt);
            avcodec_free_context(&avctxVideo);
//        decoder_abort(&is->viddec, &is->pictq);
//            decoder_destroy(&is->viddec);
            break;
        case AVMEDIA_TYPE_SUBTITLE:
//		decoder_abort(&is->subdec, &is->subpq);
//            decoder_destroy(&is->subdec);
            break;
        default:
            break;
    }

    ic->streams[stream_index]->discard = AVDISCARD_ALL;
    switch (codecpar->codec_type) {
        case AVMEDIA_TYPE_AUDIO:
            audio_st = NULL;
            audio_stream = -1;
            break;
        case AVMEDIA_TYPE_VIDEO:
            video_st = NULL;
            video_stream = -1;
            break;
        case AVMEDIA_TYPE_SUBTITLE:
            subtitle_st = NULL;
            subtitle_stream = -1;
            break;
        default:
            break;
    }
}

FILE *fp_yuv = NULL;

int Player::display_video() {
    int ret = -1;
    AVPacket pkt;

    pthread_mutex_t wait_mutex;
    pthread_mutex_init(&wait_mutex, NULL);
    for (;;) {

        if (abort_request) {
            LOGE("abort_request = %d, num = %d", abort_request, decoderNum);
            break;
        }

        if (videoFrameq.nb_frames >= 10) {
            LOGI("nb_frames >= 10, num = %d", decoderNum);
            struct timespec abstime;
            pthread_mutex_lock(&wait_mutex);
            get_abstime_wait(100, &abstime);
            pthread_cond_timedwait(&continue_read_thread, &wait_mutex, &abstime);
            pthread_mutex_unlock(&wait_mutex);
            continue;
        }

        do {
//        LOGI("packet_queue_get start, num = %d", decoderNum);
            ret = q->packet_queue_get(&videoq, &pkt, 1, NULL);
            if (ret < 0) {
                LOGI("decode ret = %d ", ret);
                return -1;
            }
//        LOGI("packet_queue_get end, num = %d", decoderNum);
            if (pkt.data == q->flush_pkt.data) {
                avcodec_flush_buffers(avctxVideo);
        }
//        LOGI("Decoder::decode while in, num = %d", decoderNum);
        } while(pkt.data == q->flush_pkt.data);

//        LOGI("Decoder::decode while, num = %d", decoderNum);
        if (pkt.stream_index == video_stream) {//解码videopacket
            //YUV
            ret = avcodec_send_packet(avctxVideo, &pkt);
            if (ret < 0 && ret != AVERROR(EAGAIN) && ret != AVERROR_EOF) {
                LOGI("ret = %d", ret);
                av_packet_unref(&pkt);
                return -1;
            }

            ret = avcodec_receive_frame(avctxVideo, avFrame);
            if (ret < 0 && ret != AVERROR(EAGAIN) && ret != AVERROR_EOF) {
                LOGI("ret = %d", ret);
                av_packet_unref(&pkt);
                return -1;
            }

            sws_scale(img_convert_ctx, (const uint8_t* const*)avFrame->data, avFrame->linesize, 0,
                      avctxVideo->height, YUVFrame->data, YUVFrame->linesize);

            char *tmp = (char *)calloc(1, yuvSize);
            memcpy(tmp, YUVFrame->data[0], yFrameSize );
            memcpy(tmp + yFrameSize, YUVFrame->data[1], uvFrameSize );
            memcpy(tmp + yFrameSize + uvFrameSize, YUVFrame->data[2], uvFrameSize );
            LOGI("Decoder::decode frame_queue_put");
            ret = frameQueue->frame_queue_put(&videoFrameq, tmp);
            LOGE("ret = %d", ret);
//            int size;
//            size = fwrite(tmp, 1, yuvSize, fp_yuv);
//            LOGE("size = %d", size);
        }
//        LOGI("Decoder::decode av_packet_unref");
        av_packet_unref(&pkt);
    }

    return ret;
}

void *Player::video_thread(void *arg) {
    Player *ptr = (Player *)arg;

    ptr->display_video();
}

int Player::stream_component_open(int stream_index) {
    AVCodecContext * avctx;
    AVCodec *codec;
    int ret = -1;

    if (stream_index < 0 || stream_index >= avFormatContext->nb_streams)
        return -1;
    avctx = avcodec_alloc_context3(NULL);
    if (!avctx)
        return AVERROR(ENOMEM);

    ret = avcodec_parameters_to_context(avctx, avFormatContext->streams[stream_index]->codecpar);
    if (ret < 0)
        return -1;//avcodec_free_context(&avctx);

    av_codec_set_pkt_timebase(avctx, avFormatContext->streams[stream_index]->time_base);

    //根据编解码上下文中的编码id查找对应的解码
    codec = avcodec_find_decoder(avctx->codec_id);
    avctx->codec_id = codec->id;
    //打开解码器
    ret = avcodec_open2(avctx, codec, NULL);
    if (ret < 0) {
        LOGI("avcodec_open2 fail");
        return -1;
    }

    eof = 0;
    avFormatContext->streams[stream_index]->discard = AVDISCARD_DEFAULT;
    switch (avctx->codec_type) {
        case AVMEDIA_TYPE_AUDIO: {
            audio_stream = stream_index;
            audio_st = avFormatContext->streams[stream_index];

            avctxAudio = avctx;
            q->packet_queue_start(&audioq);
        }
            break;
        case AVMEDIA_TYPE_VIDEO: {
            video_stream = stream_index;
            video_st = avFormatContext->streams[stream_index];

            avctxVideo = avctx;
            q->packet_queue_start(&videoq);
            this->width = avctxVideo->width;
            this->height = avctxVideo->height;
            LOGI("video format: %s, num = %d", avFormatContext->iformat->name, decoderNum);
            LOGI("video duration: %lld, num = %d", avFormatContext->duration, decoderNum);
            LOGI("video w = %d, h = %d, num = %d", avctxVideo->width, avctxVideo->height, decoderNum);
            LOGI("decoder name：%s, num = %d", codec->name, decoderNum);

            avFrame = av_frame_alloc();
            YUVFrame = av_frame_alloc();

//            yFrameSize = width*height;
//            uvFrameSize= yFrameSize>>2;
//            yuvSize = width*height * 3 / 2;
//
//            int sizeYuv = av_image_get_buffer_size(AV_PIX_FMT_YUV420P, width, height, 1);
//            frame_buffer_out = (uint8_t *)av_malloc(sizeYuv);
//            av_image_fill_arrays(YUVFrame->data, YUVFrame->linesize, frame_buffer_out, AV_PIX_FMT_YUV420P, width, height, 1);
//            img_convert_ctx = sws_getContext(avctxVideo->width, avctxVideo->height, avctxVideo->pix_fmt,
//                                             avctxVideo->width, avctxVideo->height, AV_PIX_FMT_YUV420P, SWS_BICUBIC, NULL, NULL, NULL);
            this->width = 544;
            this->height = 960;

            yFrameSize = width*height;
            uvFrameSize= yFrameSize>>2;
            yuvSize = width*height * 3 / 2;

            int sizeYuv = av_image_get_buffer_size(AV_PIX_FMT_YUV420P, width, height, 1);
            frame_buffer_out = (uint8_t *)av_malloc(sizeYuv);
            av_image_fill_arrays(YUVFrame->data, YUVFrame->linesize, frame_buffer_out, AV_PIX_FMT_YUV420P, width, height, 1);
            img_convert_ctx = sws_getContext(avctxVideo->width, avctxVideo->height, avctxVideo->pix_fmt,
                                             width, height, AV_PIX_FMT_YUV420P, SWS_BICUBIC, NULL, NULL, NULL);

            // TODO 失败处理
            pthread_t thr1;
            if(pthread_create(&thr1, NULL, video_thread, (void *)this) != 0) {
                LOGI("pthread_create initDecoder fail");
            }
        }

            break;
        case AVMEDIA_TYPE_SUBTITLE: {
            subtitle_stream = stream_index;
            subtitle_st = avFormatContext->streams[stream_index];

            avctxSubtitle = avctx;
            q->packet_queue_start(&subtitleq);
        }
            break;
        default:
            break;
    }

    return ret;
}

int Player::init() {
    int ret = -1;
//    LOGI("av_frame_alloc");
    fp_yuv = fopen("/storage/emulated/0/outyuv.yuv","wb+");

    if (filename == NULL) {
        LOGI("path is null");
        return -1;
    }

    /* start video display */
    if (q->packet_queue_init(&videoq) < 0 ||
        q->packet_queue_init(&audioq) < 0 ||
        q->packet_queue_init(&subtitleq) < 0)
        LOGI("packet_queue_init FAIL");
//        goto fail;

    if (frameQueue->frame_queue_init(&videoFrameq) < 0 ||
        frameQueue->frame_queue_init(&audioFrameq) < 0)
        LOGI("frame_queue_init FAIL");

    //1.注册所有组件
    av_register_all();
    //封装格式上下文，统领全局的结构体，保存了视频文件封装格式的相关信息
    avFormatContext = avformat_alloc_context();
    if (!avFormatContext) {
        LOGI("ic is null!!");
        return -1;
    }

    //2.打开输入视频文件
    ret = avformat_open_input(&avFormatContext, filename, NULL, NULL);
    if (ret < 0) {
        LOGI("avformat_open_input is fail!!");
        return -1;
    }

    //3.获取视频文件信息
    ret = avformat_find_stream_info(avFormatContext, NULL);
    if (ret < 0) {
        LOGI("avformat_find_stream_info is fail!!");
        return -1;
    }
//    LOGI("avformat_find_stream_info");

    //获取视频流的索引位置
    st_index[AVMEDIA_TYPE_VIDEO] =
            av_find_best_stream(avFormatContext, AVMEDIA_TYPE_VIDEO,
                                st_index[AVMEDIA_TYPE_VIDEO], -1, NULL, 0);

    st_index[AVMEDIA_TYPE_AUDIO] =
            av_find_best_stream(avFormatContext, AVMEDIA_TYPE_AUDIO,
                                st_index[AVMEDIA_TYPE_AUDIO],
                                st_index[AVMEDIA_TYPE_VIDEO],
                                NULL, 0);

    st_index[AVMEDIA_TYPE_SUBTITLE] =
            av_find_best_stream(avFormatContext, AVMEDIA_TYPE_SUBTITLE,
                                st_index[AVMEDIA_TYPE_SUBTITLE],
                                (st_index[AVMEDIA_TYPE_AUDIO] >= 0 ?
                                 st_index[AVMEDIA_TYPE_AUDIO] :
                                 st_index[AVMEDIA_TYPE_VIDEO]),
                                NULL, 0);

/* open the streams */
    if (st_index[AVMEDIA_TYPE_AUDIO] >= 0) {
        LOGI("AUDIO streamIndex: %d， Num = %d", st_index[AVMEDIA_TYPE_AUDIO], decoderNum);
        stream_component_open(st_index[AVMEDIA_TYPE_AUDIO]) ;
    }

    if (st_index[AVMEDIA_TYPE_VIDEO] >= 0) {
        LOGI("VIDEO streamIndex: %d, Num = %d", st_index[AVMEDIA_TYPE_VIDEO], decoderNum);
        stream_component_open(st_index[AVMEDIA_TYPE_VIDEO]) ;
    }

    if (st_index[AVMEDIA_TYPE_SUBTITLE] >= 0) {
        LOGI("SUBTITLE streamIndex: %d, Num = %d", st_index[AVMEDIA_TYPE_SUBTITLE], decoderNum);
        stream_component_open(st_index[AVMEDIA_TYPE_SUBTITLE]) ;
    }

    return 0;
}

void Player::read() {
    init();

    int ret = -1;
    AVPacket pkt1, *pkt = &pkt1;

    pthread_mutex_t wait_mutex;
    pthread_mutex_init(&wait_mutex, NULL);
    LOGI("Decoder::read(), num = %d", decoderNum);
    for (;;) {
        if (abort_request) {
            LOGE("abort_request = %d, num = %d", abort_request, decoderNum);
            break;
        }

        if (videoq.nb_packets >= 10) {
            LOGI("nb_packets >= 10, num = %d", decoderNum);
            struct timespec abstime;
            pthread_mutex_lock(&wait_mutex);
            get_abstime_wait(100, &abstime);
            pthread_cond_timedwait(&continue_read_thread, &wait_mutex, &abstime);
            pthread_mutex_unlock(&wait_mutex);
            continue;
        }
        ret = av_read_frame(avFormatContext, pkt);
        if (ret < 0) {
            LOGI("av_read_frame fail ret= %d \n", ret);
            if (ret == AVERROR_EOF) {
                LOGE("AVERROR_EOF , num = %d", decoderNum);
            }
            if ((ret == AVERROR_EOF || avio_feof(avFormatContext->pb)) && !eof) {
                LOGI("AVERROR_EOF");
                if (video_stream >= 0)
                    q->packet_queue_put_nullpacket(&videoq, video_stream);
                if (audio_stream >= 0)
                    q->packet_queue_put_nullpacket(&audioq, audio_stream);
                if (subtitle_stream >= 0)
                    q->packet_queue_put_nullpacket(&subtitleq, subtitle_stream);
                eof = 1;
            }
            if (avFormatContext->pb && avFormatContext->pb->error) {
                break;
            }
            //TODO why lock
            struct timespec abstime;
            pthread_mutex_lock(&wait_mutex);
            get_abstime_wait(1000, &abstime);
            pthread_cond_timedwait(&continue_read_thread, &wait_mutex, &abstime);
            pthread_mutex_unlock(&wait_mutex);
            continue;
        } else {
            eof = 0;
        }

        if (pkt->stream_index == audio_stream) {
            av_packet_unref(pkt);
        } else if (pkt->stream_index == video_stream
                   && !(video_st->disposition & AV_DISPOSITION_ATTACHED_PIC)) {
            q->packet_queue_put(&videoq, pkt);
        } else if (pkt->stream_index == subtitle_stream) {
            av_packet_unref(pkt);
        } else {
            av_packet_unref(pkt);
        }
//            q->packet_queue_put(&audioq, pkt);
//            q->packet_queue_put(&subtitleq, pkt);
    }
}

void* Player::read_pth(void* arg) {
    Player *ptr = (Player *)arg;
    ptr->read();

    return NULL;
}

void Player::start(const char *path, int num) {
    filename = av_strdup(path);
    decoderNum = num;

    // TODO 失败处理
    pthread_t thr1;
    if(pthread_create(&thr1, NULL, read_pth, (void *)this) != 0) {
        LOGI("pthread_create initDecoder fail");
    }
}

int Player::stop() {
    //TODO
    LOGI("stop");
    return 0;
}

//int Player::decode(uint8_t* data) {
//    int ret = -1;
//    AVPacket pkt;
//
//    do {
////        LOGI("packet_queue_get start, num = %d", decoderNum);
//        ret = q->packet_queue_get(&videoq, &pkt, 1, NULL);
//        if (ret < 0) {
//            LOGI("decode ret = %d ", ret);
//            return -1;
//        }
////        LOGI("packet_queue_get end, num = %d", decoderNum);
//        if (pkt.data == q->flush_pkt.data) {
//            avcodec_flush_buffers(avctxVideo);
//        }
//        LOGI("Decoder::decode while in, num = %d", decoderNum);
//    } while(pkt.data == q->flush_pkt.data);
//
////    LOGI("Decoder::decode while, num = %d", decoderNum);
//    if (pkt.stream_index == video_stream) {//解码videopacket
//        //YUV
//        ret = avcodec_send_packet(avctxVideo, &pkt);
//        if (ret < 0 && ret != AVERROR(EAGAIN) && ret != AVERROR_EOF) {
//            LOGI("ret = %d", ret);
//            av_packet_unref(&pkt);
//            return -1;
//        }
//
//        ret = avcodec_receive_frame(avctxVideo, avFrame);
//        if (ret < 0 && ret != AVERROR(EAGAIN) && ret != AVERROR_EOF) {
//            LOGI("ret = %d", ret);
//            av_packet_unref(&pkt);
//            return -1;
//        }
//
//        sws_scale(img_convert_ctx, (const uint8_t* const*)avFrame->data, avFrame->linesize, 0,
//                  avctxVideo->height, YUVFrame->data, YUVFrame->linesize);
//
//        memcpy(data, YUVFrame->data[0], yFrameSize );
//        memcpy(data + yFrameSize, YUVFrame->data[1], uvFrameSize );
//        memcpy(data + yFrameSize + uvFrameSize, YUVFrame->data[2], uvFrameSize );
//
////        fwrite(data, 1, yuvSize, fp_yuv);
//    }
////    LOGI("Decoder::decode av_packet_unref");
//    av_packet_unref(&pkt);
//
//    return ret;
//}

int Player::decode(uint8_t* data) {
    char *tmp = NULL;

    do {
        LOGD("frame_queue_get START");
        frameQueue->frame_queue_get(&videoFrameq, &tmp, 1);
        LOGD("frame_queue_get END");
    } while (NULL == tmp);

    LOGD("memcpy");
    memcpy(data, tmp, yuvSize );
    free(tmp);
    return 0;
}

int Player::getVideoWidth() {
    return this->width;
}

int Player::getVideoHeight() {
    return this->height;
}