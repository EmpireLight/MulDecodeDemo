package com.xmb.muldecodedemo;

import android.graphics.SurfaceTexture;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.MediaMetadataRetriever;
import android.os.Build;
import android.util.Log;
import android.view.Surface;
import android.widget.EditText;

import com.xmb.muldecodedemo.utils.OpenGlUtils;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * Created by Administrator on 2018/6/20 0020.
 */

public class VideoDecoder {
    private static final String TAG = "VideoDecoder";

    /** How long to wait for the next buffer to become available. */
    private static final int TIMEOUT_US = 10000;

    MediaExtractor mExtractor ;
    MediaMetadataRetriever mMetRet;
    MediaCodec mVideoDecoder;
    MediaCodec mVideoEncoder;
    Surface mOutputSurface;
    boolean isVideoEOS = false;
    MediaFormat videoFormat;
    MediaFormat audioFormat;

    Surface surface;

    public static LinkedBlockingQueue<byte[]> videoQueue;

    public int videoWidth = -1;
    public int videoHeight= -1;
    public long duration = -1;

    public VideoDecoder() {}

    public void createDecoder(String path, Surface surface) {
        this.surface = surface;
        mMetRet = new MediaMetadataRetriever();
        mExtractor = new MediaExtractor();//创建对象

        videoQueue = new LinkedBlockingQueue<>();//创建编码数据队列

        try {
            mExtractor.setDataSource(path);//设置视频文件路径
        } catch (IOException e) {
            throw new RuntimeException("path is wrong");
        }

        int count = mExtractor.getTrackCount();//得到轨道数
        Log.v(TAG, "create: count = "+ count);

        //解析Mp4,找到对应的流
        int videoTrackIndex = -1;//定义trackIndex为视轨的id
        int audioTrackIndex = -1;//定义audioTrackIndex为音轨的id
        for (int i = 0; i < count; i++) {
            MediaFormat mediaFormat = mExtractor.getTrackFormat(i);    //获得第id个Track对应的MediaForamt
            String mime = mediaFormat.getString(MediaFormat.KEY_MIME);    //再获取该Track对应的KEY_MIME字段
            Log.v(TAG, "mime: " + mime);

            if (mime.startsWith("audio")) {//视轨的KEY_MIME是以"video/"开头的，音轨是"audio/"
                audioTrackIndex = i;
                Log.v(TAG, "createDecoder: audio count = " + i);
                //TODO 音频解码器
            } else if (mime.startsWith("video")) {
                videoTrackIndex = i;
                Log.v(TAG, "createDecoder: video count = " + i);
                videoWidth = mediaFormat.getInteger(MediaFormat.KEY_WIDTH);
                videoHeight = mediaFormat.getInteger(MediaFormat.KEY_HEIGHT);
                duration = mediaFormat.getLong(MediaFormat.KEY_DURATION);//us
                Log.v(TAG, "createDecoder: mInputVideoWidth = " +videoWidth+ ", mInputVideoHeight = " + videoHeight);
                Log.v(TAG, "createDecoder: duration = " + duration/1000000 + " second");

                //选择视轨所在的轨道子集(这样在之后调用readSampleData()/getSampleTrackIndex()方法时候，
                // 返回的就只是视轨的数据了，其他轨的数据不会被返回)
                mExtractor.selectTrack(videoTrackIndex);

                //根据上面获取到的信息创建解码器
                try {
                    mVideoDecoder = MediaCodec.createDecoderByType(mime);

                } catch (IOException e) {
                    e.printStackTrace();
                    throw new RuntimeException("createDecoderByType fail");
                }

//              showSupportedColorFormat(mVideoDecoder.getCodecInfo());
                if(mVideoDecoder == null) {
                    Log.v(TAG, "create: decode fail");
                }

                //将SurfaceTexture作为参数创建一个Surface，用来接收解码视频流
                //第一个参数是待解码的数据格式(也可用于编码操作);
                //第二个参数是设置surface，用来在其上绘制解码器解码出的数据；
                //第三个参数于数据加密有关；
                //第四个参数上1表示编码器，0是否表示解码器呢？？
                mVideoDecoder.configure(mediaFormat, surface,null,0);
                mVideoDecoder.start();  //当configure好后，就可以调用start()方法来请求向MediaCodec的inputBuffer中写入数据了
                Log.v(TAG, "create: decode successful");
            }
        }
    }

    /**
     * 列举AVC编码器支持的420编码格式
     * @param mediaCodecInfo
     * @return
     */
    private static int showSupportedColorFormat(MediaCodecInfo mediaCodecInfo) {
        int matchformat = -1;
        MediaCodecInfo.CodecCapabilities codecCapabilities = mediaCodecInfo.getCapabilitiesForType(MediaFormat.MIMETYPE_VIDEO_AVC);
        for (int i = 0; i < codecCapabilities.colorFormats.length; i++) {
            switch (codecCapabilities.colorFormats[i]) {
                /**
                 * 原则上I420 和 yv12的yv排列不一样，但是根据网站
                 * https://bigflake.com/mediacodec/
                 * 得知，只有I420
                 */
                case MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Planar://I420 或  YV12
                    Log.v(TAG, "supported color format::" + codecCapabilities.colorFormats[i] + " COLOR_FormatYUV420Planar");
                    break;
                case MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420PackedPlanar:
                    Log.v(TAG, "supported color format::" + codecCapabilities.colorFormats[i] + " COLOR_FormatYUV420PackedPlanar");
                    break;
                case MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar://NV12 YYYYYYYY UVUV或NV21 YYYYYYYY VUVU（NV12大多数都支持）
                    matchformat = MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar;
                    Log.v(TAG, "supported color format::" + codecCapabilities.colorFormats[i] + " COLOR_FormatYUV420SemiPlanar");
                    break;
                case MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420PackedSemiPlanar:
                    Log.v(TAG, "supported color format::" + codecCapabilities.colorFormats[i] + " COLOR_FormatYUV420PackedSemiPlanar");
                    break;
                case MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible:
                    Log.v(TAG, "supported color format::" + codecCapabilities.colorFormats[i] + " COLOR_FormatYUV420Flexible");
                    break;
                default:
                    Log.v(TAG, "Unknow Format");
                    break;
            }
        }

        return 0;
    }

    private long diff, lastTime;

    public void videoDecode() {
        //向MediaCodec的inputBuffer中写入数据，而数据就是来自上面MediaExtractor中解析出的Track
        MediaCodec.BufferInfo videoBufferInfo = new MediaCodec.BufferInfo();
        //获取MediaCodec中等待数据写入的ByteBuffer的集合,大概有10个ByteBuffer，这个方法获取的是整个待写入数据的
        //ByteBuffer的集合，在MediaExtractor向MediaCodec中写入数据的过程中，需要判断哪些ByteBuffer
        //是可用的，这就可以通过dequeueInputBuffer得到。

        //输入ByteBuffer
        ByteBuffer[] inputBuffers = mVideoDecoder.getInputBuffers();
        //输出ByteBuffer
        ByteBuffer[] outputBuffers = mVideoDecoder.getOutputBuffers();

        int count = 0;
        while(!isVideoEOS) {
            //得到那个可以使用的ByteBuffer的id
            int inputBufferIndex = mVideoDecoder.dequeueInputBuffer(TIMEOUT_US);
            if (inputBufferIndex >= 0) {   //返回的inputBufferIndex为-1，说明暂无可用的ByteBuffer
                ByteBuffer inputBuffer = inputBuffers[inputBufferIndex];//有可以就从inputBuffers中拿出那个可用的ByteBuffer的对象
                int sampleSize = mExtractor.readSampleData(inputBuffer, 0);  //把mExtractor中的数据写入到这个可用的ByteBuffer对象中去，返回值为-1表示MediaExtractor中数据已全部读完
                if (sampleSize < 0) {
                    isVideoEOS = true;
                    mVideoDecoder.queueInputBuffer(inputBufferIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                } else {
                    mVideoDecoder.queueInputBuffer(inputBufferIndex, 0, sampleSize, mExtractor.getSampleTime(), 0);
                    mExtractor.advance();  //在MediaExtractor执行完一次readSampleData方法后，需要调用advance()去跳到下一个sample，然后再次读取数据
                }
            }
            
            int outputBufferIndex = mVideoDecoder.dequeueOutputBuffer(videoBufferInfo, TIMEOUT_US);  //获得已经成功解码的ByteBuffer的id
            if (surface == null) {
                while (outputBufferIndex >= 0) {
                    ByteBuffer outputBuffer = outputBuffers[outputBufferIndex];
                    byte[] outData = new byte[videoBufferInfo.size];
                    outputBuffer.get(outData);//将bytebuffer内的数据放入byte数组中

                    try {
                        videoQueue.put(outData);
                        Log.v(TAG, "put " + count++);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                    //释放已经解码的buffer
                    mVideoDecoder.releaseOutputBuffer(outputBufferIndex, false);

                    //解码未解完的数据
                    outputBufferIndex = mVideoDecoder.dequeueOutputBuffer(videoBufferInfo, TIMEOUT_US);
                    Log.v(TAG, "surface = null ," + count++);
                }
            } else {
                while(outputBufferIndex >=0) {
                    switch (outputBufferIndex) {
                        case MediaCodec.INFO_OUTPUT_FORMAT_CHANGED:
                            Log.v(TAG, "format changed");
                            break;
                        case MediaCodec.INFO_TRY_AGAIN_LATER:
                            Log.v(TAG, "解码当前帧超时");
                            break;
                        case MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED:
                            Log.v(TAG, "output buffers changed");
                            break;
                        default:
                            //直接渲染到Surface时使用不到outputBuffer
                            //ByteBuffer outputBuffer = outputBuffers[outputBufferIndex];
                            //延时操作
                            //如果缓冲区里的可展示时间>当前视频播放的进度，就休眠一下
                            mVideoDecoder.releaseOutputBuffer(outputBufferIndex, true);
                            outputBufferIndex = mVideoDecoder.dequeueOutputBuffer(videoBufferInfo, TIMEOUT_US);
                            //Log.v(TAG, "videoDecode: " + count++);
                            break;
                        //将该ByteBuffer释放掉，以供缓冲区的循环使用。如果没有这一步的话，
                        //会导致上面返回的inputBufferIndex一直为-1，使数据读写操作无法进行下去。
                        //如果在configure中配置了surface，
                        //则首先将缓冲区中数据发送给surface，surface一旦不再使用，就将缓冲区释放给MediaCodec
                    }
                }
            }
        }
    }
}