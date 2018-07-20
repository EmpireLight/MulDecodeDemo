package com.xmb.muldecodedemo;

import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.os.Build;
import android.util.Log;
import android.view.Surface;

import com.xmb.muldecodedemo.egl.EglCore;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import static android.media.MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface;

/**
 * Created by Administrator on 2018/7/12 0012.
 */

public class RecordEncoderCore {
    private static final String TAG = "RecordEncoderCore";

    private static final boolean DEBUG = true;
    private static final int FRAME_RATE = 15;               // 30fps
    private static final int I_FRAME_INTERVAL = 1;          // I-frames 间隔 5s

    BlockingQueue<byte[]> chunkPCMDataContainer = new LinkedBlockingQueue<>(20);//PCM数据块容器

    MediaExtractor mediaExtractor;

    private MediaCodec mAudioDecoder;
    private MediaCodec.BufferInfo audioDecodeBI;//audioDecodeBufferInfo
    private ByteBuffer[] audioDecodeIB;//AudioDecodeInputBuffers
    private ByteBuffer[] audioDecodeOB;//AudioDecodeOutputBuffers

    private MediaCodec mAudioEncoder;
    private MediaCodec.BufferInfo audioEncodeBI;//audioEncodeBufferInfo
    private ByteBuffer[] audioEncodeIB;//AudioEncodeInputBuffers
    private ByteBuffer[] audioEncodeOB;//AudioEncodeOutputBuffers

    private int audioDecodeSize;
    private int audioEncodeSize;

    private MediaCodec mVideoEncoder;
    private MediaCodec.BufferInfo videoEncodeBI;
    private ByteBuffer[] videoDecodeOB;////videoDecodeOutputBuffers

    private int mVideoTrackIndex;
    private int mAudioTrackIndex;
    private volatile boolean isVideoAdd = false;
    private volatile boolean isAudioAdd = false;
    private boolean mMuxerStarted;
    private static final int TIMEOUT_USEC = 10000;

    private Surface mInputSurface;
    private MediaMuxer mMuxer;
    private boolean mEndOfStream = false;

    private final Object lock = new Object();

    /**
     * 配置 编码器和合成器的各种状态，准备输入源供外部喂养数据。
     * @param width 编码视频的宽度
     * @param height 编码视频的高度
     * @param bitRate 比特率/码率
     * @param outputFile 输出mp4路径
     */
    public RecordEncoderCore(int width, int height, int bitRate, File outputFile, String audioPath)
            throws IOException {

        initVideoEncoder(width, height, bitRate);

        initAudioDecode(audioPath);
        Log.w(TAG, "RecordEncoderCore:audioPath " + outputFile.toString());
        Log.w(TAG, "RecordEncoderCore:audioPath " + audioPath);
        initAACMediaEncode();
        startAsync();

        // 4. 创建混合器，但我们不能在这里start，因为我们还没有编码后的视频数据，
        // 更没有把编码后的数据以track（轨道）的形式加到合成器。
        mMuxer = new MediaMuxer(outputFile.toString(),
                MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);

        mVideoTrackIndex = -1;
        mAudioTrackIndex = -1;
        mMuxerStarted = false;
    }

    private void initVideoEncoder(int width, int height, int bitRate) {
        //Raw Video Buffers
        // In ByteBuffer mode video buffers are laid out according to their color format.
        // You can get the supported color formats as an array from getCodecInfo().getCapabilitiesForType(…).colorFormats.
        // Video codecs may support three kinds of color formats:
        // I、native raw video format: This is marked by COLOR_FormatSurface and
        //      it can be used with an input or output Surface.
        // II、flexible YUV buffers (such as COLOR_FormatYUV420Flexible): These can be used with an input/output Surface,
        //      as well as in ByteBuffer mode, by using getInput/OutputImage(int).
        // III、other, specific formats: These are normally only supported in ByteBuffer mode.
        //      Some color formats are vendor specific. Others are defined in MediaCodecInfo.CodecCapabilities.
        //      For color formats that are equivalent to a flexible format, you can still use getInput/OutputImage(int).

        // 1. 设置编码器类型
        // MediaFormat.MIMETYPE_VIDEO_AVC = "video/avc"; // H.264 Advanced Video Coding
        MediaFormat format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, width, height);
        format.setInteger(MediaFormat.KEY_BIT_RATE, bitRate);
        format.setInteger(MediaFormat.KEY_FRAME_RATE, FRAME_RATE);
        format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, I_FRAME_INTERVAL);
        format.setInteger(MediaFormat.KEY_COLOR_FORMAT,//设置输入源类型为原生Surface 重点1 参考下面官网复制过来的说明
                COLOR_FormatSurface);
        // 2. 创建我们的编码器，配置我们以上的设置
        try {
            mVideoEncoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC);
        } catch (IOException e) {
            Log.e(TAG, "initVideoEncoder: createEncoderByType is failed");
            e.printStackTrace();
        }

        mVideoEncoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
        // 3. 获取编码喂养数据的输入源surface
        mInputSurface = mVideoEncoder.createInputSurface();
        mVideoEncoder.start();//启动MediaCodec ，等待传入数据
        videoEncodeBI = new MediaCodec.BufferInfo();

        // 1. 获取编码输出队列
        videoDecodeOB = mVideoEncoder.getOutputBuffers();
    }

    /**
     * 由于音频的数据格式是固定的，所以写死（将来有机会改成动态的）
     * @param audioPath
     */
    private void initAudioDecode(String audioPath) {
        try {
            mediaExtractor = new MediaExtractor();//此类可分离视频文件的音轨和视频轨道
            mediaExtractor.setDataSource(audioPath);//媒体文件的位置

            for (int i = 0; i < mediaExtractor.getTrackCount(); i++) {//遍历媒体轨道 此处我们传入的是音频文件，所以也就只有一条轨道
                MediaFormat format = mediaExtractor.getTrackFormat(i);
                String mime = format.getString(MediaFormat.KEY_MIME);
                if (mime.startsWith("audio")) {//获取音频轨道
                    mediaExtractor.selectTrack(i);//选择此音频轨道
                    mAudioDecoder = MediaCodec.createDecoderByType(mime);//创建Decode解码器
                    mAudioDecoder.configure(format, null, null, 0);
                    break;
                }
            }
        } catch (IOException e) {
            Log.e(TAG, "initAudioEncoder: failed");
            e.printStackTrace();
        }

        if (mAudioDecoder == null) {
            Log.e(TAG, "create mediaDecode failed");
            return;
        }
        mAudioDecoder.start();//启动MediaCodec ，等待传入数据
        audioDecodeIB = mAudioDecoder.getInputBuffers();//MediaCodec在此ByteBuffer[]中获取输入数据
        audioDecodeOB = mAudioDecoder.getOutputBuffers();//MediaCodec将解码后的数据放到此ByteBuffer[]中 我们可以直接在这里面得到PCM数据
        audioDecodeBI = new MediaCodec.BufferInfo();//用于描述解码得到的byte[]数据的相关信息
        Log.i(TAG, "buffers: " + audioDecodeIB.length);
    }

    private void initAACMediaEncode() {
        try {
            MediaFormat encodeFormat = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, 44100, 2);//参数对应-> mime type、采样率、声道数
            encodeFormat.setInteger(MediaFormat.KEY_BIT_RATE, 96000);//比特率
            encodeFormat.setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC);
            encodeFormat.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 100 * 1024);//作用于inputBuffer的大小
            mAudioEncoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC);
            mAudioEncoder.configure(encodeFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
        } catch (IOException e) {
            e.printStackTrace();
        }

        if (mAudioEncoder == null) {
            Log.e(TAG, "initAACMediaEncode: failed");
        }

        mAudioEncoder.start();
        audioEncodeIB = mAudioEncoder.getInputBuffers();
        audioEncodeOB = mAudioEncoder.getOutputBuffers();
        audioEncodeBI = new MediaCodec.BufferInfo();
    }

    /**
     * 开始转码
     * 音频数据从MP3解码成PCM，PCM在编码成想要的格式
     * mp3->PCM->aac
     */
    public void startAsync() {
        new Thread(new AudioDecodeRunable()).start();
        new Thread(new AudiEnecodeRunable()).start();
    }

    /**
     * 解码音频文件，得到pcm数据块
     * @return 是否解码完所有数据
     */
    private void srcAudioFormatToPCM() {
//        Log.w(TAG, "srcAudioFormatToPCM: decode to pcm" );
//        for (int i = 0; i < audioDecodeIB.length-1; i++) {
            // 2. 从编码的输出队列中检索出各种状态，对应处理
            int inputIndex = mAudioDecoder.dequeueInputBuffer(-1);//获取可用的inputBuffer -1代表一直等待，0表示不等待 建议-1,避免丢帧
            if (inputIndex < 0) {
                Log.e(TAG, "srcAudioFormatToPCM: inputIndex = " + inputIndex );
                return;
            }
            ByteBuffer inputBuffer = audioDecodeIB[inputIndex];//拿到inputBuffer
            inputBuffer.clear();//清空之前传入inputBuffer内的数据
            int sampleSize = mediaExtractor.readSampleData(inputBuffer, 0);//MediaExtractor读取数据到inputBuffer中
            if (sampleSize <0) {//小于0 代表所有数据已读取完成
                Log.e(TAG, "srcAudioFormatToPCM: audio stream is end");
            } else {
                mAudioDecoder.queueInputBuffer(inputIndex, 0, sampleSize, 0, 0);//通知MediaDecode解码刚刚传入的数据
                mediaExtractor.advance();//MediaExtractor移动到下一取样处
                audioDecodeSize += sampleSize;
            }
            Log.e(TAG, "srcAudioFormatToPCM: audioDecodeSize = " + audioDecodeSize );
//        }
//        MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED
//        MediaCodec.INFO_OUTPUT_FORMAT_CHANGED
//        MediaCodec.INFO_TRY_AGAIN_LATER
//        MediaCodec.BUFFER_FLAG_CODEC_CONFIG

        //获取解码得到的byte[]数据 参数BufferInfo上面已介绍 10000同样为等待时间 同上-1代表一直等待，0代表不等待。此处单位为微秒
        //此处建议不要填-1 有些时候并没有数据输出，那么他就会一直卡在这 等待
        ByteBuffer outputBuffer;
        byte[] chunkPCM;
        int outputIndex = mAudioDecoder.dequeueOutputBuffer(audioDecodeBI, TIMEOUT_USEC);
        Log.e(TAG, "mAudioDecoder: outputIndex " + outputIndex);
        while (outputIndex >= 0) {//每次解码完成的数据不一定能一次吐出 所以用while循环，保证解码器吐出所有数据
            outputBuffer = audioDecodeOB[outputIndex];//拿到用于存放PCM数据的Buffer
            chunkPCM = new byte[audioDecodeBI.size];//BufferInfo内定义了此数据块的大小
            Log.e(TAG, "srcAudioFormatToPCM: audioDecodeBI.size = " + audioDecodeBI.size);
            outputBuffer.get(chunkPCM);//将Buffer内的数据取出到字节数组中
            outputBuffer.clear();//数据取出后一定记得清空此Buffer MediaCodec是循环使用这些Buffer的，不清空下次会得到同样的数据

            try {
                chunkPCMDataContainer.put(chunkPCM);//自己定义的方法，供编码器所在的线程获取数据,下面会贴出代码
                Log.w(TAG, "srcAudioFormatToPCM.put sum = " + chunkPCMDataContainer.size() );
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

            mAudioDecoder.releaseOutputBuffer(outputIndex, false);//此操作一定要做，不然MediaCodec用完所有的Buffer后 将不能向外输出数据
            outputIndex = mAudioDecoder.dequeueOutputBuffer(audioDecodeBI, TIMEOUT_USEC);//再次获取数据，如果没有数据输出则outputIndex=-1 循环结束
        }
    }

    /**
     * 编码PCM数据，并放入muxer
     */
    private void dstAudioFormatFromPCM() {
        int inputIndex;
        ByteBuffer inputBuffer;
        int outputIndex;
        ByteBuffer outputBuffer;
        byte[] chunkAudio;
        int outBitSize;
        int outPacketSize;
        byte[] chunkPCM = null;
        Log.w(TAG, "chunkPCMDataContainer.take: start size" + chunkPCMDataContainer.size() );
        try {
            chunkPCM = chunkPCMDataContainer.take();
            Log.w(TAG, "chunkPCMDataContainer.take: end size " + chunkPCMDataContainer.size() );
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        if (chunkPCM == null) {
            Log.w(TAG, "dstAudioFormatFromPCM: chunkPCM is null" );

        }

        inputIndex = mAudioEncoder.dequeueInputBuffer(TIMEOUT_USEC);//同解码器
        if (inputIndex >= 0) {
            inputBuffer = audioEncodeIB[inputIndex];//同解码器
            inputBuffer.clear();//同解码器
            inputBuffer.limit(chunkPCM.length);
            inputBuffer.put(chunkPCM);//PCM数据填充给inputBuffer
            mAudioEncoder.queueInputBuffer(inputIndex, 0, chunkPCM.length, 0, 0);//通知编码器 编码
            audioEncodeSize += chunkPCM.length;
            Log.e(TAG, "audioEncodeSize: " + audioEncodeSize );
        }

        Log.e(TAG, "dstAudioFormatFromPCM: loop" );
        outputIndex = mAudioEncoder.dequeueOutputBuffer(audioEncodeBI, TIMEOUT_USEC);//同解码器
        if (outputIndex == MediaCodec.INFO_TRY_AGAIN_LATER) {// 暂时还没输出的数据能捕获
                Log.e(TAG, "no output available, spinning to await EOS");
        } else if (outputIndex == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {// 这个状态说明输出队列对象改变了，请重新获取一遍。
            Log.e(TAG, "INFO_OUTPUT_BUFFERS_CHANGED");
            audioEncodeOB = mAudioEncoder.getOutputBuffers();
        } else if (outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {// 现在我们已经得到想要的编码数据了，让我们开始合成进mp4容器文件里面吧。
            MediaFormat audioFormat = mAudioEncoder.getOutputFormat();
            mAudioTrackIndex = mMuxer.addTrack(audioFormat);
            // 获取track轨道号，等下写入编码数据的时候需要用到
            isAudioAdd = true;
            Log.w(TAG, "FORMAT_CHANGED: mAudioTrackIndex = " + mAudioTrackIndex + " isAudioAdd = " + isAudioAdd );
            startMuxer();
            Log.w(TAG, "FORMAT_CHANGED: AudiostartMuxer end");
        } else if (outputIndex < 0) {
            Log.e(TAG, "unexpected result from encoder.dequeueOutputBuffer: " + outputIndex);
            // Continue while(true)
        } else {
            outputBuffer = audioEncodeOB[outputIndex];//拿到输出Buffer
            //TODO 写入muxer muxer是不需要adts头的
            Log.e(TAG, "audio write to muxer");
            // write encoded data to muxer(need to adjust presentationTimeUs.
            audioEncodeBI.presentationTimeUs = getPTSUs();
            mMuxer.writeSampleData(mAudioTrackIndex, outputBuffer, audioEncodeBI);
            prevOutputPTSUs = audioEncodeBI.presentationTimeUs;

            mAudioEncoder.releaseOutputBuffer(outputIndex, false);
        }
    }

    /**
     * previous presentationTimeUs for writing
     */
    private long prevOutputPTSUs = 0;
    /**
     * get next encoding presentationTimeUs
     * @return
     */
    protected long getPTSUs() {
        long result = System.nanoTime() / 1000L;
        // presentationTimeUs should be monotonic
        // otherwise muxer fail to write
        if (result < prevOutputPTSUs)
            result = (prevOutputPTSUs - result) + result;
        return result;
    }

    private boolean isPrepare() {
        Log.w(TAG, "isPrepare: isVideoAdd = " + isVideoAdd + " isAudioAdd = " + isAudioAdd );
        return isVideoAdd && isAudioAdd;
    }

    private void startMuxer() {
        if (!isPrepare()) {
            synchronized(lock) {
                try {
                    Log.e(TAG, "startMuxer: wait");
                    lock.wait();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }

        } else {
            synchronized (lock) {
                mMuxer.start();
                mMuxerStarted = true;
                Log.e(TAG, "startMuxer: notifyAll" );
                lock.notifyAll();
            }
        }
    }

    /**
     * 音频解码线程
     */
    private class AudioDecodeRunable implements Runnable{
        @Override
        public void run() {
            while (!mEndOfStream) {
                srcAudioFormatToPCM();
//                Log.w(TAG, "run: Audio To PCM" );
            }
        }
    }

    /**
     * 音频编码线程
     */
    private class AudiEnecodeRunable implements Runnable{
        @Override
        public void run() {
            long t=System.currentTimeMillis();
            while (!mEndOfStream || !chunkPCMDataContainer.isEmpty()) {
                dstAudioFormatFromPCM();
            }

            Log.i(TAG, "AudiEnecodeRunable: done");
        }
    }

    public Surface getInputSurface() {
        return mInputSurface;
    }

    /**
     * 从编码器中提取所有未处理的数据，并将其转发给Muxer。
     * endOfStream是代表是否编码结束的终结符，
     * 如果是false就是正常请求输入数据去编码，按正常流程走这次编码操作。
     * 如果是true我们需要告诉编码器编码工作结束了，发送一个EOS结束标志位到输入源，
     * 然后等到我们在编码输出的数据发现EOS的时候，证明最后的一批编码数据已经编码成功了。
     */
    public void drainEncoder(boolean endOfStream) {
        this.mEndOfStream = endOfStream;
//        Log.w(TAG, "drainEncoder: mEndOfStream = " + mEndOfStream );
        if (mEndOfStream) {
            if (DEBUG) Log.d(TAG, "sending EOS to encoder");
            mVideoEncoder.signalEndOfInputStream();
        }

        while (true) {
            // 2. 从编码的输出队列中检索出各种状态，对应处理
            // 参数一是MediaCodec.BufferInfo，主要是用来承载对应buffer的附加信息。
            // 参数二是超时时间，请注意单位是微秒，1毫秒=1000微秒
            int encoderStatus = mVideoEncoder.dequeueOutputBuffer(videoEncodeBI, TIMEOUT_USEC);
//            Log.e(TAG, "mVideoEncoder.dequeueOutputBuffer: outputIndex = " + encoderStatus);
            if(encoderStatus == MediaCodec.INFO_TRY_AGAIN_LATER) {
                // 暂时还没输出的数据能捕获
                if (!endOfStream) {
                    break;      // out of while(true){}
                } else {
                    if (DEBUG) Log.d(TAG, "no output available, spinning to await EOS");
                }
            } else if(encoderStatus == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
                // 这个状态说明输出队列对象改变了，请重新获取一遍。
                videoDecodeOB = mVideoEncoder.getOutputBuffers();
            } else if(encoderStatus == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                Log.e(TAG, "videoFormat FORMAT_CHANGED: ");
                // 当我们接收到编码后的输出数据，会通过格式已转变这个标志触发，而且只会发生一次格式转变
                // 因为不可能从设置指定的格式变成其他，难不成一个视频能有两种编码格式？

                MediaFormat videoFormat = mVideoEncoder.getOutputFormat();
                // 现在我们已经得到想要的编码数据了，让我们开始合成进mp4容器文件里面吧。
                mVideoTrackIndex = mMuxer.addTrack(videoFormat);
                // 获取track轨道号，等下写入编码数据的时候需要用到
                isVideoAdd = true;
                Log.w(TAG, "FORMAT_CHANGED: mVideoTrackIndex = " + mVideoTrackIndex + " isVideoAdd = " + isVideoAdd );
                startMuxer();
                Log.w(TAG, "FORMAT_CHANGED: mVideoTrackIndex = " + mVideoTrackIndex + " isVideoAdd = " + isVideoAdd );

//                MediaFormat videoFormat = mVideoEncoder.getOutputFormat();
//                // 现在我们已经得到想要的编码数据了，让我们开始合成进mp4容器文件里面吧。
//                mVideoTrackIndex = mMuxer.addTrack(videoFormat);
//                // 获取track轨道号，等下写入编码数据的时候需要用到
//
//                if (!mMuxerStarted) {
////                    throw new RuntimeException("format changed twice");
//                    Log.e(TAG, "drainEncoder: format changed twice");
//                    mMuxer.start();
//                    mMuxerStarted = true;
//                } else {
//                    Log.i(TAG, "drainEncoder: had started");
//                }

            } else if(encoderStatus < 0) {
                Log.w(TAG, "unexpected result from encoder.dequeueOutputBuffer: " + encoderStatus);
                // Continue while(true)
            } else {
                // 3. 各种状态处理之后，大于0的encoderStatus则是指出了编码数据是在编码队列的具体位置。
                ByteBuffer encodedData = videoDecodeOB[encoderStatus];
                if (encodedData == null) {
                    throw new RuntimeException("encoderOutputBuffer " + encoderStatus + " was null");
                }
                if ((videoEncodeBI.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                    // 这表明，标记为这样的缓冲器包含编解码器初始化/编解码器特定数据而不是媒体数据。
                    if (DEBUG) Log.d(TAG, "ignoring BUFFER_FLAG_CODEC_CONFIG");
                    videoEncodeBI.size = 0;
                }
                if (videoEncodeBI.size != 0) {
                    if (!mMuxerStarted) {
                        throw new RuntimeException("muxer hasn't started");
                    }
                    // adjust the ByteBuffer values to match BufferInfo (not needed?)
                    encodedData.position(videoEncodeBI.offset);
                    encodedData.limit(videoEncodeBI.offset + videoEncodeBI.size);
                    mMuxer.writeSampleData(mVideoTrackIndex, encodedData, videoEncodeBI);
                    if (DEBUG) {
                        Log.d(TAG, "sent " + videoEncodeBI.size + " bytes to muxer, ts=" +
                                videoEncodeBI.presentationTimeUs);
                    }
                }
                // 释放编码器的输出队列中 指定位置的buffer，第二个参数指定是否渲染其buffer到解码Surface
                mVideoEncoder.releaseOutputBuffer(encoderStatus, false);
//                Log.e(TAG, "drainEncoder: videoEncodeBI.flags = " + videoEncodeBI.flags );
                if ((videoEncodeBI.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                    if (!endOfStream) {
                        Log.w(TAG, "reached end of stream unexpectedly");
                    } else {
                        if (DEBUG) Log.d(TAG, "end of stream reached");
                    }
                    break;      // out of while
                }
            }
        }
    }

    /**
     * Releases encoder resources.
     */
    public void release() {
        if (DEBUG) Log.d(TAG, "releasing encoder objects");
        if (mVideoEncoder != null ) {
            mVideoEncoder.stop();
            mVideoEncoder.release();
            mVideoEncoder = null;
        }

        if (mAudioEncoder != null) {
            mAudioEncoder.stop();
            mAudioEncoder.release();
            mAudioEncoder = null;
        }

        if (mAudioDecoder != null) {
            mAudioDecoder.stop();
            mAudioDecoder.release();
            mAudioDecoder = null;
        }

        if (mediaExtractor != null) {
            mediaExtractor.release();
            mediaExtractor=null;
        }

        if (mMuxer != null && mVideoTrackIndex != -1) {
            // stop() throws an exception if you haven't fed it any data.
            // Keep track of frames submitted, and don't call stop() if we haven't written anything.
            // Once the muxer stops, it can not be restarted.
            mMuxer.stop();
            mMuxer.release();
            mMuxer = null;
        }

//        if (onCompleteListener != null) {
//            onCompleteListener=null;
//        }
//        if (onProgressListener != null) {
//            onProgressListener=null;
//        }

//        try {
//            if (bos != null) {
//                bos.flush();
//            }
//        } catch (IOException e) {
//            e.printStackTrace();
//        }finally {
//            if (bos != null) {
//                try {
//                    bos.close();
//                } catch (IOException e) {
//                    e.printStackTrace();
//                }finally {
//                    bos=null;
//                }
//            }
//        }
//
//        try {
//            if (fos != null) {
//                fos.close();
//            }
//        } catch (IOException e) {
//            e.printStackTrace();
//                    }finally {
//            fos=null;
//        }
    }

//    /**
//     * 音频转码完成回调接口
//     */
//    public interface OnCompleteListener{
//        void completed();
//    }
//
//    /**
//     * 音频转码进度监听器
//     */
//    public interface OnProgressListener{
//        void progress();
//    }
//
//    /**
//     * 设置转码完成监听器
//     * @param onCompleteListener
//     */
//    public void setOnCompleteListener(OnCompleteListener onCompleteListener) {
//        this.onCompleteListener = onCompleteListener;
//    }
//    public void setOnProgressListener(OnProgressListener onProgressListener) {
//        this.onProgressListener = onProgressListener;
//    }

    // 判断一下系统是否支持MediaCodec编码h264
    private boolean SupportAvcCodec(){
        if(Build.VERSION.SDK_INT>=18){
            for(int j = MediaCodecList.getCodecCount() - 1; j >= 0; j--){
                MediaCodecInfo codecInfo = MediaCodecList.getCodecInfoAt(j);

                String[] types = codecInfo.getSupportedTypes();
                for (int i = 0; i < types.length; i++) {
                    if (types[i].equalsIgnoreCase("video/avc")) {
                        return true;
                    }
                }
            }
        }
        return false;
    }
}
