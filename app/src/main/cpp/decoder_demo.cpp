//
// Created by Administrator on 2018/6/25 0025.
//

#include <jni.h>

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

char *input_filename;

int frame_count;
AVPacket *packet;
AVFrame *pFrame;
AVCodec *codec;
AVCodecContext *avctx;
AVFormatContext *ic;

int video_width;
int video_height;

//rgb frame cache
AVFrame	*pFrameRGB;

//返回解码数据给上层调用
unsigned char *out_buffer;
int Ysize;
int Usize;
int Vsize;
int YUVsize;

void initdecoder(const char *filename) {
    int err, i, ret;
    int st_index[AVMEDIA_TYPE_NB];
    //TODO 记得释放（可能不必申请内存，放栈中也可以）
    packet = (AVPacket*)av_malloc(sizeof(AVPacket));

    av_register_all();

    ic = avformat_alloc_context();
    if (!ic) {
        LOGE("ic is null!!");
        return;
    }

    err = avformat_open_input(&ic, filename, NULL, NULL);
    if (err < 0) {
        LOGE("avformat_open_input is fail!!");
        return;
    }

    err = avformat_find_stream_info(ic, NULL);
    if (err < 0) {
        LOGE("avformat_find_stream_info is fail!!");
        return;
    }

    av_dump_format(ic, 0, filename, false);

    st_index[AVMEDIA_TYPE_VIDEO] =
            av_find_best_stream(ic, AVMEDIA_TYPE_VIDEO,
                                st_index[AVMEDIA_TYPE_VIDEO], -1, NULL, 0);

    st_index[AVMEDIA_TYPE_AUDIO] =
            av_find_best_stream(ic, AVMEDIA_TYPE_AUDIO,
                                st_index[AVMEDIA_TYPE_AUDIO],
                                st_index[AVMEDIA_TYPE_VIDEO],
                                NULL, 0);

    st_index[AVMEDIA_TYPE_SUBTITLE] =
            av_find_best_stream(ic, AVMEDIA_TYPE_SUBTITLE,
                                st_index[AVMEDIA_TYPE_SUBTITLE],
                                (st_index[AVMEDIA_TYPE_AUDIO] >= 0 ?
                                 st_index[AVMEDIA_TYPE_AUDIO] :
                                 st_index[AVMEDIA_TYPE_VIDEO]),
                                NULL, 0);

    /* open the streams */
    if (st_index[AVMEDIA_TYPE_AUDIO] >= 0) {
        LOGD("audio is exited");
    }

    if (st_index[AVMEDIA_TYPE_VIDEO] >= 0) {

        avctx = avcodec_alloc_context3(NULL);
        if (!avctx) {
            LOGE("avctx is null !!!");
        }

        ret = avcodec_parameters_to_context(avctx, ic->streams[st_index[AVMEDIA_TYPE_VIDEO]]->codecpar);
        if (ret < 0) {
            LOGE("ret is wrong !!!");
        }

        //根据编解码上下文中的编码id查找对应的解码
        codec = avcodec_find_decoder(avctx->codec_id);
        avctx->codec_id = codec->id;
        //打开解码器
        if ((ret = avcodec_open2(avctx, codec, NULL)) < 0) {
            LOGE("avcodec_open2 fail");
            return;
        }

        ic->streams[st_index[AVMEDIA_TYPE_VIDEO]]->discard = AVDISCARD_DEFAULT;

        LOGD("video format: %s", ic->iformat->name);
        LOGD("video duration: %lld", ic->duration);
        LOGD("video w = %d, h = %d", avctx->width, avctx->height);
        LOGD("decoder name：%s", codec->name);

//        //参考:https://www.cnblogs.com/gune/articles/4040937.html
//        //只有指定了AVFrame的像素格式、画面大小才能真正分配内存
//        //缓冲区分配内存
//        out_buffer = (uint8_t *)av_malloc(av_image_get_buffer_size(AV_PIX_FMT_RGB24, avctx->width, avctx->height, 1));
//        av_image_fill_arrays(pFrameRGB->data, pFrameRGB->linesize, out_buffer, AV_PIX_FMT_RGB24, avctx->width, avctx->height, 1);
//
//        //用于转码（缩放）的参数，转之前的宽高，转之后的宽高，格式等
//        struct SwsContext *sws_ctx = sws_getContext(avctx->width,avctx->height,avctx->pix_fmt,
//                                                    avctx->width, avctx->height, AV_PIX_FMT_YUV420P,
//                                                    SWS_BICUBIC, NULL, NULL, NULL);
    }

    if (st_index[AVMEDIA_TYPE_SUBTITLE] >= 0) {
        LOGD("subtile is exited");
    }

    Ysize = avctx->width * avctx->height;
    Usize = avctx->width * avctx->height/4;
    Vsize = avctx->width * avctx->height/4;
    YUVsize = avctx->width * avctx->height * 2 / 3;
    out_buffer = (unsigned char *)av_malloc(YUVsize);
}

void decodeOneFrame() {
    int ret;

    // 解码
    avcodec_send_packet(avctx, packet);
    ret = avcodec_receive_frame(avctx, pFrame);
    if (ret < 0)
        LOGD("ret = %x", ret);
    frame_count++;

    memset(out_buffer, 0, YUVsize);

    memcpy(out_buffer, pFrame->data[0], Ysize);
    memcpy(out_buffer, pFrame->data[1], Usize);
    memcpy(out_buffer, pFrame->data[2], Vsize);
    LOGD("decode %d frame", frame_count);
}

void close() {
    avformat_close_input(&ic);
    av_frame_free(&pFrame);
    av_freep(out_buffer);
}

extern "C"
JNIEXPORT jint JNICALL
Java_com_xmb_muldecodedemo_VideoEditorView_initdecoder(JNIEnv *env, jobject instance,
                                                       jstring path_) {
    const char *path = env->GetStringUTFChars(path_, 0);

    // TODO
    initdecoder(path);

    env->ReleaseStringUTFChars(path_, path);

    return 0;
}