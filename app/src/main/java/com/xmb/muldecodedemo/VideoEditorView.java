package com.xmb.muldecodedemo;

import android.content.Context;
import android.graphics.RectF;
import android.graphics.SurfaceTexture;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.opengl.Matrix;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.view.ViewConfiguration;

import com.xmb.muldecodedemo.filter.BackVideoFilter;
import com.xmb.muldecodedemo.utils.FBO;
import com.xmb.muldecodedemo.filter.ImageFilter;
import com.xmb.muldecodedemo.filter.PassThroughFilter;
import com.xmb.muldecodedemo.filter.YUVFilter;
import com.xmb.muldecodedemo.utils.FileUtils;

import java.text.SimpleDateFormat;
import java.util.Locale;

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

    // 记录视口的坐标
    private RectF viewportRect;

    // 按下时手指的x坐标值
    private float mDownX = 0;
    // 按下时手指的y坐标值
    private float mDownY = 0;

    // 标识符，判断手指按下的范围是否在小视频的坐标内
    private boolean mTouchViewport = false;
    private float mLastX = 0;
    private float mLastY = 0;

    //最小的滑动距离
    private int mTouchSlop;

    //显示视口参数
    private int disVPx, disVPy;
    private int disVPWidth, disVPHeight;

    //标识符，判断当前正在触摸
    private boolean touchFlag;

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

        ViewConfiguration configuration = ViewConfiguration.get(context);
        mTouchSlop = configuration.getScaledTouchSlop();//最小的滑动距离
    }

    public BackVideoFilter backFilter;

    public YUVFilter middleFilter;
    public YUVFilter frontFilter;

    public ImageFilter middleImgFilter;
    public ImageFilter frontImgFilter;
    public PassThroughFilter passThroughFilter;
    public FBO fbo;

    public SurfaceTexture mVideoSurfaceTexture;

    @Override
    public void onSurfaceCreated(GL10 gl, EGLConfig config) {
        Log.e(TAG, "onSurfaceCreated: ");

        String DirAsset0 = FileUtils.getSDPath() + "/" + "asset0";
        String DirAsset1 = FileUtils.getSDPath() + "/" + "asset1";
        String asset0MP4 = FileUtils.getSDPath() + "/" + "asset0.mp4";
        String asset1MP4 = FileUtils.getSDPath() + "/" + "asset1.mp4";
        String assetMP4 = FileUtils.getSDPath() + "/" + "asset.mp4";

        middleFilter = new YUVFilter(context);
        middleFilter.init(asset0MP4, 0);

        frontFilter = new YUVFilter(context);
        frontFilter.init(asset1MP4, 1);

        try {
            Thread.sleep(500);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        backFilter = new BackVideoFilter(context);
        //注意这里，创建了一个SurfaceTexture
        mVideoSurfaceTexture = new SurfaceTexture(backFilter.getTextureId());
        mVideoSurfaceTexture.setOnFrameAvailableListener(this);

        Log.e(TAG, "init: fileName = " + assetMP4);
        backFilter.init(assetMP4, mVideoSurfaceTexture);

        passThroughFilter = new PassThroughFilter();
        passThroughFilter.init();
    }

    @Override
    public void onSurfaceChanged(GL10 gl, int width, int height) {
        Log.d(TAG, "onSurfaceChanged: viewWidth:" + width + " viewHeight:" + height);

        backFilter.onSurfaceCreated(width, height);
//        middleFilter.onSurfaceCreated(width, height);
//        frontFilter.onSurfaceCreated(width, height);
//        fbo = FBO.newInstance().create(1920, 1080);
        setDisViewPort(width, height, 540, 960);

        //若是不改变Viewport大小视频视频， 那么最后编码的时候应该会导致黑边也会被录制下来
        //设置清屏颜色
        GLES20.glClearColor(0.0f, 0.0f, 0.0f, 1.0f);
    }

    int count = 0;

    @Override
    public void onDrawFrame(GL10 gl) {
        Log.i(TAG, "onDrawFrame: "+count++);

        if ( !first ) {//创建时默认会启动一次，若这时媒体文件尚未打开将会导致程序闪退
            first = true;
            return;
        }
//        diff = System.currentTimeMillis() - lastTime;
//        if (diff < 66) {
//            mVideoSurfaceTexture.updateTexImage();
//            return;
//        }
//        Log.e(TAG, "onDrawFrame: diff " + diff);
//        lastTime = System.currentTimeMillis();

        GLES20.glClear(GLES20.GL_DEPTH_BUFFER_BIT | GLES20.GL_COLOR_BUFFER_BIT);

        mVideoSurfaceTexture.updateTexImage();
        backFilter.onDrawFrame(backFilter.getTextureId());

        if(touchFlag) {
            GLES20.glEnable(GLES20.GL_BLEND);
            GLES20.glBlendFunc(GLES20.GL_ONE_MINUS_DST_COLOR, GLES20.GL_ONE);
            frontFilter.onDrawFrame(true);
            GLES20.glDisable(GLES20.GL_BLEND);
        } else {
//            long time = 0, lasttime = 0;
//            lasttime = System.currentTimeMillis();

            GLES20.glEnable(GLES20.GL_BLEND);
            GLES20.glBlendFunc(GLES20.GL_ONE, GLES20.GL_ONE);
            middleFilter.onDrawFrame(true);
            GLES20.glDisable(GLES20.GL_BLEND);

//            time = System.currentTimeMillis();
//            Log.i(TAG, "diff: "+ (time - lasttime));
//            lasttime = time;

            GLES20.glEnable(GLES20.GL_BLEND);
            GLES20.glBlendFunc(GLES20.GL_ZERO, GLES20.GL_SRC_COLOR);
            frontFilter.onDrawFrame(true);
            GLES20.glDisable(GLES20.GL_BLEND);
//
//            time = System.currentTimeMillis();
//            Log.i(TAG, "diff: "+ (time - lasttime));
        }

//       passThroughFilter.onDrawFrame(fbo.getFrameBufferTextureId());

        //        GLES20.glEnable(GLES20.GL_BLEND);
//        GLES20.glBlendFunc(GLES20.GL_DST_COLOR, GLES20.GL_ZERO);
//        frontImgFilter.onDrawFrame(frontImgFilter.getTextureID());
//        GLES20.glDisable(GLES20.GL_BLEND);

//        GLES20.glEnable(GLES20.GL_BLEND);
//        GLES20.glBlendFunc(GLES20.GL_ONE, GLES20.GL_ONE);
//        middleImgFilter.onDrawFrame(middleImgFilter.getTextureID());
//        GLES20.glDisable(GLES20.GL_BLEND);
        //      fbo.bind();
        //    fbo.unbind();
    }

    @Override
    public void onFrameAvailable(SurfaceTexture surfaceTexture) {
        this.requestRender();
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        switch (event.getAction() & MotionEvent.ACTION_MASK) {
            case MotionEvent.ACTION_DOWN:
                mLastX = event.getX();
                mLastY = event.getY();
                touchFlag = true;
                break;
            case MotionEvent.ACTION_MOVE:
                float current_x = event.getX();
                float current_y = event.getY();
                if (inViewportRect(current_x, current_y)) {
                    translate(current_x, current_y, mLastX, mLastY);
                    mLastX = current_x;
                    mLastY = current_y;
                    return true;
                }
                break;
            case MotionEvent.ACTION_UP:
                touchFlag = false;
                Log.i(TAG, "onTouchEvent: ACTION_UP ");
                break;
            default:
                break;
        }
        return super.onTouchEvent(event);
    }

    private boolean inViewportRect(float x, float y) {
        if (x > viewportRect.left
                && x < viewportRect.right
                && y > viewportRect.top
                && y < viewportRect.bottom) {
            return true;
        } else {
            return false;
        }
    }

    //平移变换
    public void translate(float current_x, float current_y, float last_x, float last_y){
        float normalizedX = ((current_x - viewportRect.left) / viewportRect.width()) * 2 - 1;
        float normalizedY = -(((current_y - viewportRect.top)/ viewportRect.height()) * 2 - 1);
        float lastNormalizedX = ((last_x - viewportRect.left) / viewportRect.width()) * 2 - 1;
        float lastNormalizedY = -(((last_y - viewportRect.top)/ viewportRect.height()) * 2 - 1);
        float moveX = (normalizedX - lastNormalizedX)*2.5f;
        float moveY = (normalizedY - lastNormalizedY)*2.5f;
        Log.i(TAG, "onTouchEvent: ACTION_MOVE moveX = " + moveX + ", moveY = " + moveY);

        Matrix.setIdentityM(backFilter.getModelMatrix(), 0);
        Matrix.translateM(backFilter.getModelMatrix(),0, moveX, moveY, 0);

        Matrix.multiplyMM(
                backFilter.getMVPMatrix(), 0,
                backFilter.getMVPMatrix(), 0,
                backFilter.getModelMatrix(),0);
    }
//    //旋转变换
//    public void rotate(float angle,float x,float y,float z){
//        final float normalizedX = (x / viewportRect.width()) * 2 - 1;
//        final float normalizedY = -((y / viewportRect.height()) * 2 - 1);
//        Matrix.rotateM(mMatrixCurrent,0,angle,x,y,z);
//    }
//    //缩放变换
//    public void scale(float x,float y,float z){
//        final float normalizedX = (x / viewportRect.width()) * 2 - 1;
//        final float normalizedY = -((y / viewportRect.height()) * 2 - 1);
//        Matrix.scaleM(mMatrixCurrent,0,x,y,z);
//    }
//    //设置相机
//    private void scaleMatrics(AbsFilter filter, ) {
//    }

    private void setDisViewPort(int viewWidth, int viewHeight, int videoWidth, int videoHeight) {

        /**
         * 视口（Viewport）就是最终渲染结果显示的目的地
         * viewport视口以模板视频的宽高为基准满屏适配
         *
         * 应该有两个视口，一个是用户调试用的视口
         * 一个是最后合成渲染的视口，这个视口应该是前景或中景的分辨率大小
         */
        float videoRatio = (float)videoWidth/videoHeight;
        disVPWidth = (int)((float)viewHeight*videoRatio);//视口宽度;
        disVPHeight = viewHeight;

        Log.i(TAG, "onSurfaceChanged: VPWidth:" + disVPWidth +" VPHeight:" + disVPHeight);
        disVPx = viewWidth/2- disVPWidth/2;
        disVPy = viewHeight/2- disVPHeight/2;
        GLES20.glViewport(disVPx, disVPy, disVPWidth, disVPHeight);

        Log.i(TAG, "onSurfaceChanged: VPx: "+disVPx );
        Log.i(TAG, "onSurfaceChanged: VPy: "+disVPy );
        Log.i(TAG, "onSurfaceChanged: VPWidth: "+disVPWidth );
        Log.i(TAG, "onSurfaceChanged: VPHeight: "+disVPHeight );
        viewportRect = new RectF(disVPx, disVPy, disVPx + disVPWidth, disVPy + disVPHeight);
    }
}

