package com.xmb.muldecodedemo.filter;

import android.content.Context;
import android.graphics.SurfaceTexture;
import android.media.AudioManager;
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
import java.util.concurrent.Semaphore;

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

    @Override
    public void onSurfaceCreated(int width, int height) {
        /**用户所选视频以视口宽高为基准算矩阵*/
        //设置透视投影
        float screenRatio=(float) width / height;
        float videoRatio=(float) videoWidth / videoHeight;
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
        mediaPlayer = new MediaPlayer();
        mediaPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
        mediaPlayer.setSurface(new Surface(surfaceTexture));
        //播放完成的监听
        mediaPlayer.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
            @Override
            public void onCompletion(MediaPlayer mp) {
//                mp.seekTo(0);
//                mp.start();
                restart();
            }
        });
        Log.d(TAG, "start_decode: end");
//         异步准备的一个监听函数，准备好了就调用里面的方法
        mediaPlayer.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
            @Override
            public void onPrepared(MediaPlayer mp) {
                videoWidth = mp.getVideoWidth();
                videoHeight = mp.getVideoHeight();
                onSurfaceCreated(screenWidth, screenHeight);
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
    }

    public void setScreenWH(int width, int height) {
        this.screenWidth = width;
        this.screenHeight = height;
    }

    public void restart() {
        mediaPlayer.seekTo(0);
        mediaPlayer.start();
    }
}
