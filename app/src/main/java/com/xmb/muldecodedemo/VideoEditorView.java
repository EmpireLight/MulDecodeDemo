package com.xmb.muldecodedemo;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.SurfaceTexture;
import android.opengl.GLES20;
import android.opengl.GLES30;
import android.opengl.GLSurfaceView;
import android.util.AttributeSet;
import android.util.Log;
import android.view.Surface;

import com.xmb.muldecodedemo.filter.OesFilter;
import com.xmb.muldecodedemo.filter.WaterMarkFilter;
import com.xmb.muldecodedemo.utils.FileUtils;
import com.xmb.muldecodedemo.utils.OpenGlUtils;
import com.xmb.muldecodedemo.utils.OutputImageFormat;

import java.io.File;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

/**
 * Created by Administrator on 2018/6/20 0020.
 */

public class VideoEditorView extends GLSurfaceView implements GLSurfaceView.Renderer, SurfaceTexture.OnFrameAvailableListener{
    private final static String TAG = "VideoEditorView";

    static {
        System.loadLibrary("decoder_demo");
    }

    private Context mContext;

    int mVideoTextureId;
    SurfaceTexture mVideoSurfaceTexture;
    VideoDecoder videoDecoder;

    int viewWidth, viewHeight;
    int screenWidth, screenHeight;

    OesFilter oesFilter;
    WaterMarkFilter waterMarkFilter0;
    WaterMarkFilter waterMarkFilter1;

    float[] matrix;

    String DirAsset0;
    String DirAsset1;
    Bitmap asset0Bitmap;
    Bitmap asset1Bitmap;
    String loadFile;

    private OutputImageFormat outputImageFormat;

    int frame_count;

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

        oesFilter = new OesFilter(mContext);
        oesFilter.setTextureID(mVideoTextureId);

        DirAsset0 = FileUtils.getSDPath() +"/" + "asset0";
        DirAsset1 = FileUtils.getSDPath() +"/" + "asset1";

        frame_count = 1;

        waterMarkFilter0 = new WaterMarkFilter(mContext, 1);

        loadFile = String.format(DirAsset0+ "/" + "frame_%05d.jpg", frame_count);
        Log.d(TAG, "init: loadFile:" + loadFile);
        File f=new File(loadFile);
        if(!f.exists())
        {
            Log.e(TAG, "onDrawFrame: loadFile no exit");
        }
        asset0Bitmap = BitmapFactory.decodeFile(loadFile);

        waterMarkFilter0.setWaterMark(asset0Bitmap);

        waterMarkFilter1 = new WaterMarkFilter(mContext, 2);
        loadFile = String.format(DirAsset1+ "/" + "frame_%05d.jpg", frame_count);
        Log.d(TAG, "init: loadFile:" + loadFile);
        asset1Bitmap = BitmapFactory.decodeFile(loadFile);

        if ((asset0Bitmap == null) | (asset1Bitmap == null)) {
            Log.e(TAG, "init: bitmap null" );
        }

//        waterMarkFilter0.setPosition(0, 0, 0, 0);
        waterMarkFilter1.setWaterMark(asset1Bitmap);
//        waterMarkFilter1.setPosition(0, 0, 0, 0);
    }

    public void start_decode() {
        Surface surface = new Surface(mVideoSurfaceTexture);

        String SDpath = FileUtils.getSDPath();

        String inputFile = SDpath +"/" + "asset.mp4";
        Log.d(TAG, "init: fileName = " + inputFile);

        videoDecoder = new VideoDecoder();
        videoDecoder.createDecoder(inputFile, surface);

        float dividedWidth = viewWidth * 1.0f / videoDecoder.mInputVideoWidth;
        float dividedHeight = viewHeight * 1.0f / videoDecoder.mInputVideoHeight;
        if (dividedWidth > dividedHeight) {//屏幕比较宽
            screenWidth = (int) (videoDecoder.mInputVideoWidth * dividedHeight);
            screenHeight = viewHeight;
        } else {
            screenWidth = viewWidth;
            screenHeight = (int) (videoDecoder.mInputVideoHeight * dividedWidth);
        }

        Thread thread = new Thread() {
            public void run() {
                videoDecoder.videoDecode();
            }
        };
        thread.start();
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
//        oesFilter.setMVPMatrix(matrix);
        Log.e(TAG, "onSurfaceChanged: " + width +" " + height);

        start_decode();

        GLES20.glClearColor(0.0f, 0.0f, 0.0f, 0.0f);
    }

    @Override
    public void onDrawFrame(GL10 gl) {
        //Log.d(TAG, "onDrawFrame: ");
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);
        //GLES20.glViewport(0, 0, screenWidth, screenHeight);
        GLES20.glViewport(viewWidth/2-waterMarkFilter0.imgWitdh/2,
                          viewHeight/2-waterMarkFilter0.imgHeight/2,
                waterMarkFilter0.imgWitdh, waterMarkFilter0.imgHeight);
        mVideoSurfaceTexture.updateTexImage();
        oesFilter.draw();

//        loadFile = String.format(DirAsset0 + "/" + "frame_%05d.jpg", frame_count);
//        asset0Bitmap = BitmapFactory.decodeFile(loadFile);
//        Log.d(TAG, "init: loadFile:" + loadFile);
//
//        loadFile = String.format(DirAsset1+ "/" + "frame_%05d.jpg", frame_count);
//        asset1Bitmap = BitmapFactory.decodeFile(loadFile);
//        Log.d(TAG, "init: loadFile:" + loadFile);
//
//        if ((asset0Bitmap == null) | (asset1Bitmap == null)) {
//            Log.e(TAG, "onDrawFrame: Bitmap is null");
//        } else {
//            waterMarkFilter0.setWaterMark(asset0Bitmap);
//            waterMarkFilter1.setWaterMark(asset1Bitmap);
//
            GLES20.glEnable(GLES20.GL_BLEND);
            GLES20.glBlendFunc(GLES20.GL_ONE, GLES20.GL_ONE);
            waterMarkFilter0.draw();
            GLES20.glDisable(GLES20.GL_BLEND);
//
            GLES20.glEnable(GLES20.GL_BLEND);
            GLES20.glBlendFunc(GLES20.GL_DST_COLOR, GLES20.GL_ZERO);
            waterMarkFilter1.draw();
            GLES20.glDisable(GLES20.GL_BLEND);
//            frame_count++;
//        }
    }

    @Override
    public void onFrameAvailable(SurfaceTexture surfaceTexture) {
        this.requestRender();
    }

    public native int initdecoder(String path);
    //public native void decodeFrame();
}
