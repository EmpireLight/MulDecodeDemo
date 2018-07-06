package com.xmb.muldecodedemo;

import android.content.Context;
import android.graphics.SurfaceTexture;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.util.AttributeSet;
import android.util.Log;

import com.xmb.muldecodedemo.filter.BackVideoFilter;
import com.xmb.muldecodedemo.filter.RGBFilter;
import com.xmb.muldecodedemo.utils.FBO;
import com.xmb.muldecodedemo.filter.ImageFilter;
import com.xmb.muldecodedemo.filter.PassThroughFilter;
import com.xmb.muldecodedemo.filter.YUVFilter;
import com.xmb.muldecodedemo.utils.FileUtils;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

/**
 * 合成:
 * 前景图层(模板)：frontFilter
 * 中景图层(黑白)：middleFilter
 * 背景图层(用户选择)：backFilter
 * Created by Administrator on 2018/6/20 0020.
 */

public class VideoEditorView extends GLSurfaceView implements GLSurfaceView.Renderer, SurfaceTexture.OnFrameAvailableListener {
    private final static String TAG = "VideoEditorView";

    Context context;
    private boolean first;

    private long currentTime;
    private long lastTime;
    private long diff;

    public VideoEditorView(Context context) {
        this(context, null);
    }

    public VideoEditorView(Context context, AttributeSet attrs) {
        super(context, attrs);
        this.context = context;

        // 设置OpenGl ES的版本为2.0
        setEGLContextClientVersion(3);
        setEGLConfigChooser(8, 8, 8, 8, 16, 0);
        // 设置与当前GLSurfaceView绑定的Renderer
        setRenderer(this);
        // 设置渲染的模式
        setRenderMode(RENDERMODE_WHEN_DIRTY);
    }

    public BackVideoFilter backFilter;

    public YUVFilter middleFilter;
    public YUVFilter frontFilter;

//    public RGBFilter middleFilter;
//    public RGBFilter frontFilter;

    public ImageFilter middleImgFilter;
    public ImageFilter frontImgFilter;
    public PassThroughFilter passThroughFilter;
    public FBO fbo;

    private int viewWidth, viewHeight;
    private int videoWidth, videoHeight;

    public SurfaceTexture mVideoSurfaceTexture;

    //显示视口参数
    int disVPx, disVPy;
    int disVPWidth, disVPHeight;

    @Override
    public void onSurfaceCreated(GL10 gl, EGLConfig config) {
        Log.e(TAG, "onSurfaceCreated: ");

        String DirAsset0 = FileUtils.getSDPath() + "/" + "asset0";
        String DirAsset1 = FileUtils.getSDPath() + "/" + "asset1";

        String asset0MP4 = FileUtils.getSDPath() + "/" + "asset0.mp4";
        String asset1MP4 = FileUtils.getSDPath() + "/" + "asset1.mp4";
        String assetMP4 = FileUtils.getSDPath() + "/" + "asset.mp4";

        int frame_count = 1;
        String loadFile = null;
        loadFile = String.format(DirAsset0 + "/" + "frame_%05d.jpg", frame_count);
        middleImgFilter = new ImageFilter(context);
        middleImgFilter.init(loadFile);

        loadFile = String.format(DirAsset1 + "/" + "frame_%05d.jpg", frame_count);
        frontImgFilter = new ImageFilter(context);
        frontImgFilter.init(loadFile);

        middleFilter = new YUVFilter(context);
        middleFilter.init(asset0MP4, 0);

        frontFilter = new YUVFilter(context);
        frontFilter.init(asset1MP4, 1);

//        middleFilter = new RGBFilter(context);
//        middleFilter.init(asset0MP4, 0);
//
//        frontFilter = new RGBFilter(context);
//        frontFilter.init(asset1MP4, 1);

        backFilter = new BackVideoFilter(context);
        //注意这里，创建了一个SurfaceTexture
        mVideoSurfaceTexture = new SurfaceTexture(backFilter.getTextureID());
        mVideoSurfaceTexture.setOnFrameAvailableListener(this);

        Log.e(TAG, "init: fileName = " + assetMP4);
        backFilter.init(assetMP4, mVideoSurfaceTexture);

        passThroughFilter = new PassThroughFilter(context);
        passThroughFilter.init();
    }

    @Override
    public void onSurfaceChanged(GL10 gl, int width, int height) {
        GLES20.glViewport(0, 0, width, height);
        viewWidth = width;
        viewHeight = height;
        Log.d(TAG, "onSurfaceChanged: viewWidth:" + viewWidth + " viewHeight:" + viewHeight);

        backFilter.onSurfaceCreated(width, height);
//        middleFilter.onSurfaceCreated(width, height);

        /**
         * 视口（Viewport）就是最终渲染结果显示的目的地
         * viewport视口以模板视频的宽高为基准满屏适配
         *
         * 应该有两个视口，一个是用户调试用的视口
         * 一个是最后合成渲染的视口，这个视口应该是前景或中景的分辨率大小
         */
//        float videoRatio = (float)frontFilter.getYUVWidth(0)/frontFilter.getYUVHeight(0);
//        disVPWidth = (int)((float)viewHeight*videoRatio);//视口宽度;
//        disVPHeight = viewHeight;
//        Log.i(TAG, "onSurfaceChanged: VPWidth:" + disVPWidth +" VPHeight:" + disVPHeight);
//        disVPx = viewWidth/2- disVPWidth/2;
//        disVPy = viewHeight/2- disVPHeight/2;
//        GLES20.glViewport(disVPx, disVPy, disVPWidth, disVPHeight);
//
//        Log.i(TAG, "onSurfaceChanged: VPx: "+disVPx );
//        Log.i(TAG, "onSurfaceChanged: VPy: "+disVPy );
//        Log.i(TAG, "onSurfaceChanged: VPWidth: "+disVPWidth );
//        Log.i(TAG, "onSurfaceChanged: VPHeight: "+disVPHeight );

        fbo = FBO.newInstance().create(1920, 1080);

        //若是不改变Viewport大小视频视频， 那么最后编码的时候应该会导致黑边也会被录制下来
    }

    int count = 0;

    @Override
    public void onDrawFrame(GL10 gl) {
        Log.i(TAG, "onDrawFrame: "+count++);

        if ( !first ) {//创建时默认会启动一次，若这时媒体文件尚未打开将会导致程序闪退
            first = true;
            return;
        }

        diff = System.currentTimeMillis() - lastTime;
        if (diff < 66) {
            mVideoSurfaceTexture.updateTexImage();
            return;
        }
        Log.e(TAG, "onDrawFrame: diff " + diff);
        lastTime = System.currentTimeMillis();

//        GLES20.glClearColor(0.0f, 0.0f, 0.0f, 1.0f);
//        GLES20.glClearColor(1.0f, 0.0f, 0.0f, 1.0f);
//        GLES20.glClearColor(0.0f, 1.0f, 0.0f, 1.0f);
//        GLES20.glClearColor(0.0f, 0.0f, 1.0f, 1.0f);
        GLES20.glClearColor(1.0f, 1.0f, 1.0f, 1.0f);
        GLES20.glClear(GLES20.GL_DEPTH_BUFFER_BIT | GLES20.GL_COLOR_BUFFER_BIT);

        mVideoSurfaceTexture.updateTexImage();
//        backFilter.onDrawFrame(backFilter.getTextureID());

//      fbo.bind();
//        GLES20.glEnable(GLES20.GL_BLEND);
//        GLES20.glBlendFunc(GLES20.GL_ONE, GLES20.GL_ONE);
//        middleFilter.onDrawFrame();
//        GLES20.glDisable(GLES20.GL_BLEND);
//
//    fbo.unbind();
//       passThroughFilter.onDrawFrame(fbo.getFrameBufferTextureId());
//
//        GLES20.glEnable(GLES20.GL_BLEND);
//        GLES20.glBlendFunc(GLES20.GL_ZERO, GLES20.GL_SRC_COLOR);
//        GLES20.glBlendFunc(GLES20.GL_ONE, GLES20.GL_ONE);
        frontFilter.onDrawFrame();
//        GLES20.glDisable(GLES20.GL_BLEND);

        //        GLES20.glEnable(GLES20.GL_BLEND);
//        GLES20.glBlendFunc(GLES20.GL_DST_COLOR, GLES20.GL_ZERO);
//        frontImgFilter.onDrawFrame(frontImgFilter.getTextureID());
//        GLES20.glDisable(GLES20.GL_BLEND);

//        GLES20.glEnable(GLES20.GL_BLEND);
//        GLES20.glBlendFunc(GLES20.GL_ONE, GLES20.GL_ONE);
//        middleImgFilter.onDrawFrame(middleImgFilter.getTextureID());
//        GLES20.glDisable(GLES20.GL_BLEND);
    }

    @Override
    public void onFrameAvailable(SurfaceTexture surfaceTexture) {
        this.requestRender();
    }

}
