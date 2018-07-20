//
// Created by Administrator on 2018/6/25 0025.
//


//#include <include/libswresample/swresample.h>
#include "LogJni.h"
#include "FFmpegDEcode.h"
#include "decoder.h"
#include "Queue.h"

void get_abstime_wait(long timeout_ms, struct timespec *abstime)
{
    struct timeval now;

    gettimeofday(&now, NULL);
    long nsec = now.tv_usec * 1000 + (timeout_ms % 1000) * 1000000;
    abstime->tv_nsec = nsec % 1000000000;
    abstime->tv_sec = now.tv_sec + nsec / 1000000000 + timeout_ms / 1000;
}

Player::Player() {
    videoWidth = -1;
    videoHeight = -1;
    duration = -1;

    abort_request = 0;

    paused = 0;
    seek_req = 0;
    seek_pos = 0;

    avFormatContext = NULL;
    avctxAudio= NULL;
    avctxVideo = NULL;
    avctxSubtitle = NULL;

    img_convert_ctx = NULL;

    memset(st_index, -1, sizeof(st_index));
    audio_stream = -1;
    video_stream = -1;
    subtitle_stream = -1;
    eof = 0;

    //TODO 记得清除
    q = new MyPacketQueue();
    frameQueue = new MyFrameQueue();

    pthread_cond_init(&continue_read_thread, NULL);

    av_init_packet(&flush_pkt);
}

Player::~Player() {
    delete q;
    delete frameQueue;
}

void Player::stream_component_close(int stream_index)
{
    AVFormatContext *ic = avFormatContext;
    AVCodecParameters *codecpar;

    if (stream_index < 0 || stream_index >= ic->nb_streams)
        return;
    codecpar = ic->streams[stream_index]->codecpar;

    q->packet_queue_destroy(&videoq);
    q->packet_queue_destroy(&audioq);
    q->packet_queue_destroy(&subtitleq);

    switch (codecpar->codec_type) {
        case AVMEDIA_TYPE_AUDIO:
            q->packet_queue_abort(&audioq);
            frameQueue->frame_queue_abort(&sampq);

            pthread_join(audio_tid, NULL);
            audio_tid = 0;

            q->packet_queue_flush(&audioq);
            frameQueue->frame_queue_flush(&sampq);

            avcodec_free_context(&avctxAudio);

//            swr_free(&swr_ctx);
            //todo 释放音频解码缓冲区
//            av_freep(&is->audio_buf1);

            break;
        case AVMEDIA_TYPE_VIDEO:
            q->packet_queue_abort(&videoq);
            frameQueue->frame_queue_abort(&pictq);

            pthread_join(video_tid, NULL);
            video_tid = 0;

            q->packet_queue_flush(&videoq);
            frameQueue->frame_queue_flush(&pictq);

            avcodec_free_context(&avctxVideo);
            break;
        case AVMEDIA_TYPE_SUBTITLE:
            q->packet_queue_abort(&subtitleq);
            frameQueue->frame_queue_abort(&subpq);
//            pthread_join(video_tid, NULL);
//            video_tid = NULL;

            q->packet_queue_flush(&videoq);
            frameQueue->frame_queue_flush(&pictq);

            avcodec_free_context(&avctxSubtitle);
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

int Player::display_audio() {
    int ret = -1;
    AVPacket pkt;

    pthread_mutex_t wait_mutex;
    pthread_mutex_init(&wait_mutex, NULL);

    AVFrame *avFrame = av_frame_alloc();

    for (;;) {
        if (abort_request) {
            LOGE("abort_request = %d, num = %d", abort_request, decoderNum);
            break;
        }

        if (sampq.nb_frames >= 10) {
//            LOGI("nb_frames >= 10, num = %d", decoderNum);
            struct timespec abstime;
            pthread_mutex_lock(&wait_mutex);
            get_abstime_wait(100, &abstime);
            pthread_cond_timedwait(&continue_read_thread, &wait_mutex, &abstime);
            pthread_mutex_unlock(&wait_mutex);
            continue;
        }

        do {
//        LOGI("packet_queue_get start, num = %d", decoderNum);
            ret = q->packet_queue_get(&audioq, &pkt, 1, NULL);
            if (ret < 0) {
                LOGI("decode ret = %d ", ret);
                return -1;
            }
//        LOGI("packet_queue_get end, num = %d", decoderNum);
            if (pkt.data == q->flush_pkt.data) {
                avcodec_flush_buffers(avctxAudio);
            }
//        LOGI("Decoder::decode while in, num = %d", decoderNum);
        } while(pkt.data == q->flush_pkt.data);

//        LOGI("Decoder::decode while, num = %d", decoderNum);
        if (pkt.stream_index == audio_stream) {//解码videopacket
            //YUV
            ret = avcodec_send_packet(avctxAudio, &pkt);
            if (ret < 0 && ret != AVERROR(EAGAIN) && ret != AVERROR_EOF) {
                LOGI("ret = %d", ret);
                av_packet_unref(&pkt);
                return -1;
            }

            ret = avcodec_receive_frame(avctxAudio, avFrame);
            if (ret < 0 && ret != AVERROR(EAGAIN) && ret != AVERROR_EOF) {
                LOGI("ret = %d", ret);
                av_packet_unref(&pkt);
                return -1;
            }

//            sws_scale(img_convert_ctx, (const uint8_t* const*)avFrame->data, avFrame->linesize, 0,
//                      avctxAudio->height, YUVFrame->data, YUVFrame->linesize);
//
//            char *tmp = (char *)calloc(1, yuvSize);
//            memcpy(tmp, YUVFrame->data[0], yFrameSize );
//            memcpy(tmp + yFrameSize, YUVFrame->data[1], uvFrameSize );
//            memcpy(tmp + yFrameSize + uvFrameSize, YUVFrame->data[2], uvFrameSize );
//            ret = frameQueue->frame_queue_put(&pictq, tmp);
        }
        av_packet_unref(&pkt);
    }

    return ret;
}

void *Player::audio_thread(void *arg) {
    Player *ptr = (Player *)arg;

    ptr->display_audio();
    return NULL;
}

int Player::display_video() {
    int ret = -1;
    AVPacket pkt;

    pthread_mutex_t wait_mutex;
    pthread_mutex_init(&wait_mutex, NULL);

    AVFrame *avFrame = av_frame_alloc();
    AVFrame *YUVFrame = av_frame_alloc();

    //视频宽度必须为8的倍数
    this->convertWidth = videoWidth + (8 - videoWidth%8);
    this->convertHeight = videoHeight;
    LOGD("this->convertWidth = %d", this->convertWidth);

    yFrameSize = convertWidth*convertHeight;
    uvFrameSize= yFrameSize>>2;
    yuvSize = convertWidth*convertHeight * 3 / 2;

    int sizeYuv = av_image_get_buffer_size(AV_PIX_FMT_YUV420P, convertWidth, convertHeight, 1);
    frame_buffer_out = (uint8_t *)av_malloc(sizeYuv);
    av_image_fill_arrays(YUVFrame->data, YUVFrame->linesize, frame_buffer_out, AV_PIX_FMT_YUV420P, convertWidth, convertHeight, 1);
    img_convert_ctx = sws_getContext(avctxVideo->width, avctxVideo->height, avctxVideo->pix_fmt,
                                     convertWidth, convertHeight, AV_PIX_FMT_YUV420P, SWS_BICUBIC, NULL, NULL, NULL);

    for (;;) {
        if (abort_request) {
            LOGE("abort_request = %d, num = %d", abort_request, decoderNum);
            break;
        }

        if (pictq.nb_frames >= 10) {
//            LOGI("nb_frames >= 10, num = %d", decoderNum);
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
            currentTime = pkt.pts * av_q2d(avFormatContext->streams[video_stream]->time_base) * 1000;//转换出来的是秒，*1000转换为毫秒
            sws_scale(img_convert_ctx, (const uint8_t* const*)avFrame->data, avFrame->linesize, 0,
                      avctxVideo->height, YUVFrame->data, YUVFrame->linesize);

            char *tmp = (char *)calloc(1, yuvSize);
            memcpy(tmp, YUVFrame->data[0], yFrameSize );
            memcpy(tmp + yFrameSize, YUVFrame->data[1], uvFrameSize );
            memcpy(tmp + yFrameSize + uvFrameSize, YUVFrame->data[2], uvFrameSize );
            ret = frameQueue->frame_queue_put(&pictq, tmp);
        }
        av_packet_unref(&pkt);
    }

    av_frame_free(&avFrame);
    av_frame_free(&YUVFrame);

    return ret;
}

void *Player::video_thread(void *arg) {
    Player *ptr = (Player *)arg;

    ptr->display_video();
    return NULL;
}

FILE *fp_AAC0 = NULL;
FILE *fp_AAC1 = NULL;

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

            sample_rate    = avctx->sample_rate;
            nb_channels    = avctx->channels;
            channel_layout = avctx->channel_layout;
            LOGI("sample_rate = %d num = %d", sample_rate, decoderNum);
            LOGI("nb_channels = %d num = %d", nb_channels, decoderNum);
            LOGI("channel_layout = %d num = %d\"", channel_layout, decoderNum);
            LOGI("decoder name：%s, num = %d", codec->name, decoderNum);

//            if (decoderNum == 0) {
//                fp_AAC0 = fopen("/storage/emulated/0/fp_AAC0.aac","wb+");
//
//            } else if (decoderNum == 1) {
//                fp_AAC1 = fopen("/storage/emulated/0/fp_AAC1.aac","wb+");
//            }
        }
            break;
        case AVMEDIA_TYPE_VIDEO: {
            video_stream = stream_index;
            video_st = avFormatContext->streams[stream_index];

            avctxVideo = avctx;
            q->packet_queue_start(&videoq);
            this->videoWidth = avctxVideo->width;
            this->videoHeight = avctxVideo->height;
            this->duration = avFormatContext->duration / 1000;
            LOGI("video format: %s, num = %d", avFormatContext->iformat->name, decoderNum);
            LOGI("video duration: %d ms, num = %d", duration, decoderNum);
            LOGI("video w = %d, h = %d, num = %d", avctxVideo->width, avctxVideo->height, decoderNum);
            LOGI("decoder name：%s, num = %d", codec->name, decoderNum);

            // TODO 失败处理
            if(pthread_create(&video_tid, NULL, video_thread, (void *)this) != 0) {
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

    if (frameQueue->frame_queue_init(&pictq) < 0 ||
        frameQueue->frame_queue_init(&sampq) < 0 ||
        frameQueue->frame_queue_init(&subpq) < 0)
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

        if (videoq.nb_packets >= 10) {//TODO 应该做音频读取大小的限制，防止内存溢出
//            LOGI("nb_packets >= 10, num = %d", decoderNum);
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
//            q->packet_queue_put(&audioq, pkt);
            av_packet_unref(pkt);
        } else if (pkt->stream_index == video_stream
                   && !(video_st->disposition & AV_DISPOSITION_ATTACHED_PIC)) {
            q->packet_queue_put(&videoq, pkt);
        } else if (pkt->stream_index == subtitle_stream) {
//            q->packet_queue_put(&subtitleq, pkt);
            av_packet_unref(pkt);
        } else {
            av_packet_unref(pkt);
        }

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
    if(pthread_create(&read_tid, NULL, read_pth, (void *)this) != 0) {
        LOGI("pthread_create initDecoder fail");
    }
}

int Player::stop() {
    //TODO
    /* XXX: use a special url_shutdown call to abort parse cleanly */
    abort_request = 1;
    LOGW("abort_request = %d  num = %d", abort_request, decoderNum);
    pthread_join(read_tid, NULL);
    LOGW("read_tid num = %d", decoderNum);
    /* close each stream */
    if (audio_stream >= 0)
//        stream_component_close(audio_stream);
    if (video_stream >= 0)
        stream_component_close(video_stream);
    if (subtitle_stream >= 0)
//        stream_component_close(subtitle_stream);
    LOGW("subtitle_stream stream_component_close num = %d", decoderNum);
    avformat_close_input(&avFormatContext);

    q->packet_queue_destroy(&videoq);
//    q->packet_queue_destroy(&audioq);
//    q->packet_queue_destroy(&subtitleq);
//
//    /* free all pictures */
    frameQueue->frame_queue_destroy(&pictq);
//    frameQueue->frame_queue_destroy(&sampq);
//    frameQueue->frame_queue_destroy(&subpq);
    pthread_cond_destroy(&continue_read_thread);
    sws_freeContext(img_convert_ctx);
//    sws_freeContext(sub_convert_ctx);

    av_free(filename);
    LOGI("stop");
    return 0;
}

int Player::getVideoData(uint8_t* data) {
    char *tmp = NULL;

    if ((pictq.nb_frames == 0)&&(eof)) {
        LOGE("video frame had done");
        return -1;
    }

    do {
        LOGD("frame_queue_get START");
        frameQueue->frame_queue_get(&pictq, &tmp, 1);
        LOGD("frame_queue_get END");
    } while (NULL == tmp);

    LOGD("memcpy");
    memcpy(data, tmp, yuvSize );
    free(tmp);
    return 0;
}

int Player::getAudioData(uint8_t *data) {
    char *tmp = NULL;
    do {
        LOGD("frame_queue_get START");
        frameQueue->frame_queue_get(&sampq, &tmp, 1);
        LOGD("frame_queue_get END");
    } while (NULL == tmp);

    LOGD("memcpy");
    //todo 写入本地文件 + 计算每帧大小
    memcpy(data, tmp, yuvSize );
    free(tmp);
    return 0;
}

bool Player::is_end() {
    if ((eof)&&(pictq.nb_frames == 0)) {
//        LOGE("video frame had done num = %d", decoderNum);
        return true;
    }
    return false;
}

int Player::getVideoWidth() {
    return this->videoWidth;
}

int Player::getVideoHeight() {
    return this->videoHeight;
}

int Player::getConvertWidth() {
    return this->convertWidth;
}

int Player::getConvettHeight() {
    return this->convertHeight;
}

int Player::getCurTime() {
    return this->currentTime;//注意现在获取的是ffmpeg解码时packet的时间，单位毫秒（应该是frame的时间才对）
}

int Player::getDuration() {
    return this->duration;
}