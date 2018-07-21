package com.xmb.muldecodedemo.filter;

import android.content.Context;
import android.graphics.SurfaceTexture;
import android.media.AudioManager;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.MediaMetadataRetriever;
import android.media.MediaPlayer;
import android.net.Uri;
import android.opengl.GLES11Ext;
import android.opengl.GLES20;
import android.opengl.Matrix;
import android.util.Log;
import android.view.Surface;

import com.xmb.muldecodedemo.R;
import com.xmb.muldecodedemo.VideoDecoder;
import com.xmb.muldecodedemo.programs.GLAbsProgram;
import com.xmb.muldecodedemo.utils.OpenGlUtils;

import java.io.IOException;

import javax.xml.datatype.Duration;

/**
 * Created by Administrator on 2018/7/6 0006.
 */

public class BackVideoFilter extends AbsFilter {
    protected static final String TAG = "BackVideoFilter";

    private int OESTextureID;
    private GLAbsProgram glOESProgram;
    public VideoDecoder videoDecoder;
    public boolean hasInit;

    private int screenWidth;
    private int screenHeight;

    private int videoWidth;
    private int videoHeight;
    private int videoFPS;
    private long videoDuration;
    private MediaPlayer mediaPlayer;

    public BackVideoFilter(Context context) {
        super();
        glOESProgram = new GLAbsProgram(context, R.raw.oes_base_vertex, R.raw.oes_base_fragment);
        glOESProgram.create();
        OESTextureID = OpenGlUtils.loadExternalOESTextureID();
    }

    @Override
    public void init() {
        hasInit = true;
    }

    public void init(final String filePath, SurfaceTexture SurfaceTexture) {
        start_decode(filePath, SurfaceTexture);
        hasInit = true;
    }

    @Override
    public void destroy() {
        glOESProgram.onDestroy();
        OpenGlUtils.deleteTexture(OESTextureID);
    }

    //用图像帧需要换算矩阵，否则出现的图像变形
    @Override
    public void onSurfaceCreated(int width, int height) {
        /**用户所选视频以视口宽高为基准算矩阵*/
        //初始化矩阵
        Matrix.setIdentityM(mProjectMatrix, 0);
        Matrix.setIdentityM(mModelMatrix, 0);
        //设置透视投影
        float screenRatio=(float) width / height;
        float videoRatio=(float) videoWidth / videoHeight;
        Log.e(TAG, "onSurfaceCreated: screenWidth = " + width);
        Log.e(TAG, "onSurfaceCreated: screenHeight = " + height);
        Log.e(TAG, "onSurfaceCreated: videoWidth = " + videoWidth);
        Log.e(TAG, "onSurfaceCreated: videoHeight = " + videoHeight);
        if (videoRatio>screenRatio){
            Matrix.orthoM(mProjectMatrix,0,-1f,1f,-videoRatio/screenRatio,videoRatio/screenRatio,-1f,1f);
        }else {
            Matrix.orthoM(mProjectMatrix,0,-screenRatio/videoRatio,screenRatio/videoRatio,-1f,1f,-1f,1f);
        }
//        //设置相机位置
//        Matrix.setLookAtM(mViewMatrix, 0, 0, 0, 5.0f, 0f, 0f, 0f, 0f, 1.0f, 0.0f);
//        //计算变换矩阵
        Matrix.multiplyMM(mMVPMatrix, 0, mProjectMatrix, 0, mModelMatrix,0);
    }

    @Override
    public void onDrawFrame(int textureId) {
        glOESProgram.use();

        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, OESTextureID);
        GLES20.glUniform1i(glOESProgram.getSampleTexHandle(), 0);

        GLES20.glUniformMatrix4fv(glOESProgram.getMVPMatrixHandle(),1,false, mMVPMatrix,0);

        // Enable a handle to the triangle vertices
        GLES20.glEnableVertexAttribArray(glOESProgram.getPositionHandle());
        // Prepare the <insert shape here> coordinate data
        GLES20.glVertexAttribPointer(glOESProgram.getPositionHandle(), COORDS_PER_VERTEX, GLES20.GL_FLOAT, false, vertexStride, mVerBuffer);

        GLES20.glEnableVertexAttribArray(glOESProgram.getTextureCoordinateHandle());
        GLES20.glVertexAttribPointer(glOESProgram.getTextureCoordinateHandle(), COORDS_PER_VERTEX, GLES20.GL_FLOAT, false, vertexStride, mTexBuffer);
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP,0,4);

        GLES20.glDisableVertexAttribArray(glOESProgram.getPositionHandle());
        GLES20.glDisableVertexAttribArray(glOESProgram.getTextureCoordinateHandle());
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0);
    }

    public void setOESTextureID(int OESTextureID) {
        this.OESTextureID = OESTextureID;
    }

    public int getTextureId() {
        return this.OESTextureID;
    }

    private void start_decode(final String filePath, SurfaceTexture surfaceTexture) {
        MediaMetadataRetriever mmr = new MediaMetadataRetriever();
        mmr.setDataSource(filePath);
        videoDuration = Long.parseLong(mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION));
        Log.i(TAG, "start_decode: videoDuration = " + videoDuration);

        MediaExtractor mExtractor = new MediaExtractor();//创建对象
        try {
            mExtractor.setDataSource(filePath);//设置视频文件路径
        } catch (IOException e) {
            throw new RuntimeException("path is wrong");
        }
        int count = mExtractor.getTrackCount();//得到轨道数
        for (int i = 0; i < count; i++) {
            MediaFormat mediaFormat = mExtractor.getTrackFormat(i);    //获得第id个Track对应的MediaForamt
            String mime = mediaFormat.getString(MediaFormat.KEY_MIME);    //再获取该Track对应的KEY_MIME字段
            if(mime.startsWith("video/")){
                videoFPS = mediaFormat.getInteger(MediaFormat.KEY_FRAME_RATE);
                Log.i(TAG, "start_decode: videoFPS = " + videoFPS);
            }
        }

        mediaPlayer = new MediaPlayer();
        mediaPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
        mediaPlayer.setSurface(new Surface(surfaceTexture));
        //播放完成的监听
        mediaPlayer.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
            @Override
            public void onCompletion(MediaPlayer mp) {
                restart();
            }
        });
        Log.i(TAG, "start_decode: end");
//         异步准备的一个监听函数，准备好了就调用里面的方法
        mediaPlayer.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
            @Override
            public void onPrepared(MediaPlayer mp) {
                videoWidth = mp.getVideoWidth();
                videoHeight = mp.getVideoHeight();
                Log.i(TAG, "onPrepared: videoWidth = " + videoWidth);
                Log.i(TAG, "onPrepared: videoHeight = " + videoHeight);
                onSurfaceCreated(540, 960);// TODO 这个值应该是模板视频的宽度，不应该是固定值，应该在config.json里面读取
                mp.start();
                mp.setVolume(0,0);//默认不播放声音
            }
        });
        //播放错误的监听
//        mediaPlayer.setOnErrorListener(this);
        //添加播放路径
        try {
            mediaPlayer.setDataSource(filePath);
            // 准备开始,异步准备，自动在子线程中
            mediaPlayer.prepareAsync();
        } catch (IOException e) {
            e.printStackTrace();
        }

        if (mmr != null) {
            mmr.release();
        }

        if (mExtractor != null) {
            mExtractor.release();
            Log.i(TAG, "start_decode:  mExtractor.release();");
        }
    }

    public void setScreenWH(int width, int height) {
        this.screenWidth = width;
        this.screenHeight = height;
    }

    public void restart() {
        mediaPlayer.seekTo(0);
        mediaPlayer.start();
    }

    public int getVideoFPS() {
        return videoFPS;
    }

    public long getVideoDuration() {
        return videoDuration;
    }

    public int getVideoCurtime () {
        return mediaPlayer.getCurrentPosition();
    }
}
