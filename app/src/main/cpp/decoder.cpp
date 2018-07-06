//
// Created by Administrator on 2018/6/25 0025.
//

#include <unistd.h>
#include <pthread.h>

#include "LogJni.h"
#include "FFmpegDEcode.h"
#include "decoder.h"

#define MAX_QUEUE_SIZE (15 * 1024 * 1024)


void get_abstime_wait(long timeout_ms, struct timespec *abstime)
{
    struct timeval now;

    gettimeofday(&now, NULL);
    long nsec = now.tv_usec * 1000 + (timeout_ms % 1000) * 1000000;
    abstime->tv_nsec = nsec % 1000000000;
    abstime->tv_sec = now.tv_sec + nsec / 1000000000 + timeout_ms / 1000;
}

Decoder::Decoder() {
    frame_count = 0;
    eof = 0;
    width = -1;
    height = -1;
    duration = -1;

    avFormatContext = NULL;
    avCodec= NULL;
    avCodecContext= NULL;

    memset(st_index, -1, sizeof(st_index));

    //TODO 记得清除
    q = new BlockingQueue();
    pthread_cond_init(&continue_read_thread, NULL);
}

Decoder::~Decoder() {
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

void Decoder::stream_component_close(int stream_index)
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
            avcodec_free_context(&avCodecContext);
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
FILE *fp_rgb = NULL;
int Decoder::init() {
    int ret = -1;

//    LOGE("av_frame_alloc");
    fp_yuv = fopen("/storage/emulated/0/outyuv.yuv","wb+");
    fp_rgb = fopen("/storage/emulated/0/outrgb.rgb","wb+");

    if (filename == NULL) {
        LOGE("path is null");
        return -1;
    }

    /* start video display */
    if (q->packet_queue_init(&videoq) < 0 ||
        q->packet_queue_init(&audioq) < 0 ||
        q->packet_queue_init(&subtitleq) < 0)
        LOGE("packet_queue_init FAIL");
//        goto fail;

    //1.注册所有组件
    av_register_all();
    //封装格式上下文，统领全局的结构体，保存了视频文件封装格式的相关信息
    avFormatContext = avformat_alloc_context();
    if (!avFormatContext) {
        LOGE("ic is null!!");
        return -1;
    }

    //2.打开输入视频文件
    ret = avformat_open_input(&avFormatContext, filename, NULL, NULL);
    if (ret < 0) {
        LOGE("avformat_open_input is fail!!");
        return -1;
    }

    //3.获取视频文件信息
    ret = avformat_find_stream_info(avFormatContext, NULL);
    if (ret < 0) {
        LOGE("avformat_find_stream_info is fail!!");
        return -1;
    }
//    LOGE("avformat_find_stream_info");

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
        LOGE("audio is exited, streamIndex: %d", st_index[AVMEDIA_TYPE_AUDIO]);
    }

    if (st_index[AVMEDIA_TYPE_VIDEO] >= 0) {
//        LOGE("video is exited, streamIndex: %d", st_index[AVMEDIA_TYPE_VIDEO]);
        video_stream = st_index[AVMEDIA_TYPE_VIDEO];
        video_st = avFormatContext->streams[video_stream];

        avCodecContext = avcodec_alloc_context3(NULL);
        if (!avCodecContext) {
            LOGE("avctx is null !!!");
        }

//        LOGE("avcodec_parameters_to_context");
        ret = avcodec_parameters_to_context(avCodecContext, avFormatContext->streams[st_index[AVMEDIA_TYPE_VIDEO]]->codecpar);
        if (ret < 0) {
            LOGE("ret is wrong !!!");
        }
//        LOGE("avcodec_find_decoder");

        //根据编解码上下文中的编码id查找对应的解码
        avCodec = avcodec_find_decoder(avCodecContext->codec_id);
        avCodecContext->codec_id = avCodec->id;
        //打开解码器
        ret = avcodec_open2(avCodecContext, avCodec, NULL);
        if (ret < 0) {
            LOGE("avcodec_open2 fail");
            return -1;
        }

//        LOGE("AVDISCARD_DEFAULT");
        avFormatContext->streams[st_index[AVMEDIA_TYPE_VIDEO]]->discard = AVDISCARD_DEFAULT;

        q->packet_queue_start(&videoq);

        this->width = avCodecContext->width;
        this->height = avCodecContext->height;

//        LOGE("video format: %s", avFormatContext->iformat->name);
//        LOGE("video duration: %lld", avFormatContext->duration);
        LOGE("video w = %d, h = %d", avCodecContext->width, avCodecContext->height);
//        LOGE("decoder name：%s", avCodec->name);

        avFrame = av_frame_alloc();
        YUVFrame = av_frame_alloc();

        yFrameSize = avCodecContext->width*avCodecContext->height;
        uvFrameSize= yFrameSize>>2;
        yuvSize = avCodecContext->width*avCodecContext->height * 3 / 2;

//        int sizeYuv = av_image_get_buffer_size(AV_PIX_FMT_YUV420P, width, height, 1);
//        uint8_t *frame_buffer_out = (uint8_t *)av_malloc(sizeYuv);
//        av_image_fill_arrays(YUVFrame->data, YUVFrame->linesize, frame_buffer_out, AV_PIX_FMT_YUV420P, width, height, 1);
//        img_convert_ctx = sws_getContext(avCodecContext->width, avCodecContext->height, avCodecContext->pix_fmt,
//                                         avCodecContext->width, avCodecContext->height, AV_PIX_FMT_YUV420P, SWS_BICUBIC, NULL, NULL, NULL);
//
        int sizergb = av_image_get_buffer_size(AV_PIX_FMT_RGB32, width, height, 1);//AV_PIX_FMT_RGB24   AV_PIX_FMT_RGB32
        uint8_t *frame_buffer_out = (uint8_t *)av_malloc(sizergb);
        av_image_fill_arrays(YUVFrame->data, YUVFrame->linesize, frame_buffer_out, AV_PIX_FMT_RGB32, width, height, 1);
        img_convert_ctx = sws_getContext(avCodecContext->width, avCodecContext->height, avCodecContext->pix_fmt,
                                         avCodecContext->width, avCodecContext->height, AV_PIX_FMT_RGB32, SWS_BICUBIC, NULL, NULL, NULL);
    }

    if (st_index[AVMEDIA_TYPE_SUBTITLE] >= 0) {
        LOGE("subtile is exited");
    }

    return 0;
}

void Decoder::read() {
    init();

    int ret = -1;
    AVPacket pkt1, *pkt = &pkt1;
    int got_picture = -1;

    pthread_mutex_t wait_mutex;
    pthread_mutex_init(&wait_mutex, NULL);

    for (;;) {
        if (videoq.nb_packets == 10) {
            struct timespec abstime;
            pthread_mutex_lock(&wait_mutex);
            get_abstime_wait(100, &abstime);
            pthread_cond_timedwait(&continue_read_thread, &wait_mutex, &abstime);
            pthread_mutex_unlock(&wait_mutex);
//            LOGE("nb_frames >= 10");
            continue;
        }

        if (avFormatContext == NULL) {
            LOGE("avFormatContext == NULL");
        }

        ret = av_read_frame(avFormatContext, pkt);
//        LOGE("av_read_frame");
        if (ret < 0) {
            LOGE("av_read_frame fail ret= %d \n", ret);
            if ((ret == AVERROR_EOF || avio_feof(avFormatContext->pb)) && !eof) {
                LOGE("AVERROR_EOF");
                if (video_stream >= 0) {
                    q->packet_queue_put_nullpacket(&videoq, video_stream);
                }
//                if (audio_stream >= 0)
//                    q->packet_queue_put_nullpacket(&audioq, audio_stream);
//                if (subtitle_stream >= 0)
//                    q->packet_queue_put_nullpacket(&subtitleq, subtitle_stream);
                eof = 1;
            }
            if (avFormatContext->pb && avFormatContext->pb->error) {
                break;
            }
            //TODO why lock
            struct timespec abstime;
            pthread_mutex_lock(&wait_mutex);
            get_abstime_wait(100, &abstime);
            pthread_cond_timedwait(&continue_read_thread, &wait_mutex, &abstime);
            pthread_mutex_unlock(&wait_mutex);
            continue;
        } else {
            eof = 0;
        }
//        if (pkt->stream_index == audio_stream) {
//            q->packet_queue_put(&audioq, pkt);
//        } else if (pkt->stream_index == video_stream
//                   && !(video_st->disposition & AV_DISPOSITION_ATTACHED_PIC)) {
//            q->packet_queue_put(&videoq, pkt);
//        } else if (pkt->stream_index == subtitle_stream) {
//            q->packet_queue_put(&subtitleq, pkt);
//        } else {
//            av_packet_unref(pkt);
//        }

        if (pkt->stream_index == video_stream) {
            q->packet_queue_put(&videoq, pkt);
        } else {
            av_packet_unref(pkt);
        }
    }
}

void* Decoder::read_pth(void* arg) {
    Decoder *ptr = (Decoder *)arg;
    ptr->read();

    return NULL;
}

void Decoder::start(const char *path) {
    filename = av_strdup(path);

    // TODO 失败处理
    pthread_t thr1;
    if(pthread_create(&thr1, NULL, read_pth, (void *)this) != 0) {
        LOGE("pthread_create initDecoder fail");
    }
}

int Decoder::stop() {
    //TODO
    LOGE("stop");
    return 0;
}

int Decoder::decode(uint8_t* data) {
    int ret = -1;
    int got_picture = -1;
    AVPacket pkt;

    do {
        if (q->packet_queue_get(&videoq, &pkt, 1, NULL) < 0) {
            LOGE("decode ret = %d", ret);
            return -1;
        }
        if (pkt.data == q->flush_pkt.data) {
            avcodec_flush_buffers(avCodecContext);
        }
    } while(pkt.data == q->flush_pkt.data);

    if (pkt.stream_index == video_stream) {//解码videopacket
//        ret = avcodec_send_packet(avCodecContext, &pkt);
//        if (ret < 0 && ret != AVERROR(EAGAIN) && ret != AVERROR_EOF) {
//            LOGE("ret = %d", ret);
//            av_packet_unref(&pkt);
//            return -1;
//        }
//        LOGE("avcodec_send_packet ret = %d", ret);
//        ret = avcodec_receive_frame(avCodecContext, avFrame);
//        if (ret < 0 && ret != AVERROR(EAGAIN) && ret != AVERROR_EOF) {
//            LOGE("ret = %d", ret);
//            av_packet_unref(&pkt);
//            return -1;
//        }
//        LOGE("avcodec_receive_frame ret = %d", ret);
//        sws_scale(img_convert_ctx, (const uint8_t* const*)avFrame->data, avFrame->linesize, 0,
//                  avCodecContext->height, YUVFrame->data, YUVFrame->linesize);
//
//        memcpy(data, YUVFrame->data[0], yFrameSize );
//        memcpy(data + yFrameSize, YUVFrame->data[1], uvFrameSize );
//        memcpy(data + yFrameSize + uvFrameSize, YUVFrame->data[2], uvFrameSize );
//        ret = avcodec_send_packet(avCodecContext, &pkt);
//        if (ret < 0 && ret != AVERROR(EAGAIN) && ret != AVERROR_EOF) {
//            LOGE("ret = %d", ret);
//            av_packet_unref(&pkt);
//            return -1;
//        }
//        LOGE("avcodec_send_packet ret = %d", ret);
//        ret = avcodec_receive_frame(avCodecContext, avFrame);
//        if (ret < 0 && ret != AVERROR(EAGAIN) && ret != AVERROR_EOF) {
//            LOGE("ret = %d", ret);
//            av_packet_unref(&pkt);
//            return -1;
//        }
//        LOGE("avcodec_receive_frame ret = %d", ret);

        ret = avcodec_send_packet(avCodecContext, &pkt);
        if (ret < 0 && ret != AVERROR(EAGAIN) && ret != AVERROR_EOF) {
            LOGE("ret = %d", ret);
            av_packet_unref(&pkt);
            return -1;
        }
        LOGE("avcodec_send_packet ret = %d", ret);

        ret = avcodec_receive_frame(avCodecContext, avFrame);
        if (ret != 0)
            return ret;
//        if (ret < 0 && ret != AVERROR(EAGAIN) && ret != AVERROR_EOF) {
//            LOGE("ret = %d", ret);
//            av_packet_unref(&pkt);
//            return -1;
//        }
//        LOGE("avcodec_receive_frame ret = %d", ret);

        LOGE("YUVFrame->linesize = %d", YUVFrame->linesize[0]);
        ret = sws_scale(img_convert_ctx, (const uint8_t* const*)avFrame->data, avFrame->linesize, 0,
                  avCodecContext->height, YUVFrame->data, YUVFrame->linesize);

        ret = fwrite(YUVFrame->data[0], 1, width*height*4, fp_rgb);
        LOGE("fwrite ret = %d", ret);
        memcpy(data, YUVFrame->data[0], width*height*4 );
        LOGE("frame_count = %d", frame_count++);
    }

    av_packet_unref(&pkt);

    return ret;
}

int Decoder::getVideoWidth() {
    return this->width;
}

int Decoder::getVideoHeight() {
    return this->height;
}