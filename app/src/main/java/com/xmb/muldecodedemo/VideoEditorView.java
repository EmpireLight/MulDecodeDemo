package com.xmb.muldecodedemo;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.content.res.AssetManager;
import android.graphics.SurfaceTexture;
import android.media.MediaPlayer;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.opengl.Matrix;
import android.os.Environment;
import android.util.AttributeSet;
import android.util.Log;
import android.view.Surface;

import com.xmb.muldecodedemo.filter.OesFilter;
import com.xmb.muldecodedemo.utils.MatrixUtils;
import com.xmb.muldecodedemo.utils.OpenGlUtils;

import java.io.File;
import java.io.IOException;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

/**
 * Created by Administrator on 2018/6/20 0020.
 */

public class VideoEditorView extends GLSurfaceView implements GLSurfaceView.Renderer, SurfaceTexture.OnFrameAvailableListener{
    private final static String TAG = "VideoEditorView";

    private Context mContext;

    int mVideoTextureId;
    SurfaceTexture mVideoSurfaceTexture;
    VideoDecoder videoDecoder;

    int viewWidth, viewHeight;
    int screenWidth, screenHeight;

    OesFilter oesFilter;

    float[] matrix;

    public VideoEditorView(Context context) {
        this(context, null);
    }

    public VideoEditorView(Context context, AttributeSet attrs) {
        super(context, attrs);
        mContext = context;

        // 设置OpenGl ES的版本为2.0
        setEGLContextClientVersion(2);
        // 设置与当前GLSurfaceView绑定的Renderer
        setRenderer(this);
        // 设置渲染的模式
        setRenderMode(RENDERMODE_WHEN_DIRTY);
    }

    private void init() {
        //注意这里，创建了一个SurfaceTexture
        mVideoTextureId = OpenGlUtils.loadExternalOESTextureID();
        mVideoSurfaceTexture = new SurfaceTexture(mVideoTextureId);
        mVideoSurfaceTexture.setOnFrameAvailableListener(this);

        Surface surface = new Surface(mVideoSurfaceTexture);

        String SDpath = getSDPath();

        String asset = SDpath +"/" + "asset.mp4";
        Log.d(TAG, "init: fileName = " + asset);

        String asset0 = SDpath +"/" + "asset0.mp4";
        Log.d(TAG, "init: fileName = " + asset0);

        String asset1 = SDpath +"/" + "asset1.mp4";
        Log.d(TAG, "init: fileName = " + asset1);

        videoDecoder = new VideoDecoder();
//        videoDecoder.createDecoder(asset1, surface);
        videoDecoder.createDecoder(asset1, null);

        oesFilter = new OesFilter(mContext);
        oesFilter.setTextureID(mVideoTextureId);
    }

    public String getSDPath(){
        File sdDir = null;
        boolean sdCardExist = Environment.getExternalStorageState()
                .equals(android.os.Environment.MEDIA_MOUNTED);//判断sd卡是否存在
        if(sdCardExist)
        {
            sdDir = Environment.getExternalStorageDirectory();//获取跟目录
        }
        return sdDir.toString();
    }

    @Override
    public void onSurfaceCreated(GL10 gl, EGLConfig config) {
        init();
    }

    @Override
    public void onSurfaceChanged(GL10 gl, int width, int height) {
        GLES20.glViewport(0, 0, width, height);
        viewWidth = width;
        viewHeight = height;

//        MatrixUtils.getShowMatrix(matrix, videoDecoder.mInputVideoWidth, videoDecoder.mInputVideoHeight, viewWidth, viewHeight);
//
//        oesFilter.setMVPMatrix(matrix);

        Log.e(TAG, "onSurfaceChanged: " + width +" " + height);

        Thread thread = new Thread() {
          public void run() {
              videoDecoder.videoDecode();
          }
        };
        thread.start();

        float dividedWidth = viewWidth * 1.0f / videoDecoder.mInputVideoWidth;
        float dividedHeight = viewHeight * 1.0f / videoDecoder.mInputVideoHeight;
        if (dividedWidth > dividedHeight) {//屏幕比较宽
            screenWidth = (int) (videoDecoder.mInputVideoWidth * dividedHeight);
            screenHeight = viewHeight;
        } else {
            screenWidth = viewWidth;
            screenHeight = (int) (videoDecoder.mInputVideoHeight * dividedWidth);
        }
    }

    @Override
    public void onDrawFrame(GL10 gl) {

//        GLES20.glViewport(0, 0, screenWidth, screenHeight);
        GLES20.glViewport(viewWidth/2-screenWidth/2, viewHeight/2-screenHeight/2, screenWidth, screenHeight);
        mVideoSurfaceTexture.updateTexImage();
//
//        GLES20.glViewport(viewWidth/2-videoDecoder.mInputVideoWidth/2, viewHeight/2-videoDecoder.mInputVideoHeight/2,
//                videoDecoder.mInputVideoWidth, videoDecoder.mInputVideoHeight);
        oesFilter.draw();
    }

    @Override
    public void onFrameAvailable(SurfaceTexture surfaceTexture) {
        this.requestRender();
    }
}
