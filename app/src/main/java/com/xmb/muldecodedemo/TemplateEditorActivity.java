package com.xmb.muldecodedemo;

import android.app.Activity;

import android.graphics.RectF;
import android.graphics.SurfaceTexture;
import android.opengl.EGL14;
import android.opengl.GLES20;
import android.opengl.Matrix;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.support.annotation.Nullable;
import android.util.Log;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;

import com.xmb.muldecodedemo.egl.EglCore;
import com.xmb.muldecodedemo.egl.WindowSurface;
import com.xmb.muldecodedemo.filter.BackVideoFilter;
import com.xmb.muldecodedemo.filter.PassThroughFilter;
import com.xmb.muldecodedemo.filter.YUVFilter;
import com.xmb.muldecodedemo.utils.FBO;
import com.xmb.muldecodedemo.utils.FileUtils;

import java.io.File;
import java.lang.ref.WeakReference;

/**
 * Created by Administrator on 2018/7/11 0011.
 */

public class TemplateEditorActivity extends Activity implements SurfaceHolder.Callback,
        View.OnClickListener{
    private final static String TAG = "TemplateEditorActivity";

    String DirAsset0 = FileUtils.getSDPath() + "/" + "asset0";
    String DirAsset1 = FileUtils.getSDPath() + "/" + "asset1";
    String asset0MP4 = FileUtils.getSDPath() + "/" + "asset0.mp4";
    String asset1MP4 = FileUtils.getSDPath() + "/" + "asset1.mp4";
    String assetMP4 = FileUtils.getSDPath() + "/" + "asset.mp4";

    SurfaceView sv;
    private EglCore mEglCore;
    private WindowSurface mDisplaySurface;

    public BackVideoFilter backFilter;
    public YUVFilter middleFilter;
    public YUVFilter frontFilter;
    public SurfaceTexture mVideoSurfaceTexture;

    public PassThroughFilter passThroughFilter;

    private float mLastX = 0;
    private float mLastY = 0;
    // 记录视口的坐标
    private RectF viewportRect;
    //显示视口参数
    private int disVPx, disVPy;
    private int disVPWidth, disVPHeight;
    //标识符，判断当前是否在触摸
    private boolean touchFlag;

    MainHandler mHandler;

    private File outputFile;
    private volatile boolean mRequestRecord;
    private RecordEncoder mRecordEncoder;
    private boolean recording;

    private static final int VIDEO_WIDTH = 540;
    private static final int VIDEO_HEIGHT = 960;    // 540 960

    public FBO fbo;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_template_editor);

        findViewById(R.id.template_editor_back).setOnClickListener(this);
        findViewById(R.id.template_editor_next).setOnClickListener(this);

        sv = (SurfaceView) findViewById(R.id.template_editor_SurfaceView);
        SurfaceHolder sh = sv.getHolder();
        sh.addCallback(this);
        sv.setFocusable(true);

        mHandler = new MainHandler(this);
    }

    @Override
    protected void onPause() {
        super.onPause();
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        Log.d(TAG, "surfaceCreated holder = " + holder);
        // 准备好EGL环境，创建渲染介质mDisplaySurface
        mEglCore = new EglCore(null, EglCore.FLAG_RECORDABLE);

        mDisplaySurface = new WindowSurface(mEglCore, holder.getSurface(), false);
        mDisplaySurface.makeCurrent();

        middleFilter = new YUVFilter(this);
        middleFilter.init(asset0MP4, 0);

        frontFilter = new YUVFilter(this);
        frontFilter.init(asset1MP4, 1);

        backFilter = new BackVideoFilter(this);
        //注意这里，创建了一个SurfaceTexture
        mVideoSurfaceTexture = new SurfaceTexture(backFilter.getTextureID());
        mVideoSurfaceTexture.setOnFrameAvailableListener(new SurfaceTexture.OnFrameAvailableListener() {
            @Override
            public void onFrameAvailable(SurfaceTexture surfaceTexture) {
                mHandler.sendEmptyMessage(MainHandler.MSG_FRAME_AVAILABLE);
            }
        });
        Log.e(TAG, "init: fileName = " + assetMP4);
        backFilter.init(assetMP4, mVideoSurfaceTexture);

        passThroughFilter = new PassThroughFilter();
        passThroughFilter.init();

        mRecordEncoder = new RecordEncoder();

        outputFile = new File(Environment.getExternalStorageDirectory().getPath(), "camera-test.mp4");
        Log.e(TAG, "surfaceCreated: outputFile = " + outputFile );

        fbo = FBO.newInstance().create(VIDEO_WIDTH, VIDEO_HEIGHT);

        recording = mRecordEncoder.isRecording();
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        Log.d(TAG, "surfaceChanged:  holder = " + holder + ", width" + width +", height = " + height);
        backFilter.onSurfaceCreated(width, height);
        Log.e(TAG, "surfaceChanged: sv.getHeight() = " + sv.getHeight() + ", sv.getWidth()" + sv.getWidth());

        setDisViewPort(width, height, 540, 960);
//        GLES20.glViewport(0, 0, width, height);
        //设置清屏颜色
        GLES20.glClearColor(0.0f, 0.0f, 0.0f, 1.0f);
    }

    private void drawFrame() {
        if (mEglCore == null) {
            Log.d(TAG, "Skipping drawFrame after shutdown");
            return;
        }

        GLES20.glClearColor(0.0f, 0.0f, 0.0f, 1.0f);
        GLES20.glClear(GLES20.GL_DEPTH_BUFFER_BIT | GLES20.GL_COLOR_BUFFER_BIT);

        mDisplaySurface.makeCurrent();

//        fbo.bind();
//        GLES20.glViewport((int)viewportRect.left, (int)viewportRect.top, (int)viewportRect.width(), (int)viewportRect.height());
//
        mVideoSurfaceTexture.updateTexImage();
        backFilter.onDrawFrame(backFilter.getTextureID());

        if(touchFlag) {
            GLES20.glEnable(GLES20.GL_BLEND);
            GLES20.glBlendFunc(GLES20.GL_ONE_MINUS_DST_COLOR, GLES20.GL_ONE);
            frontFilter.onDrawFrame();
            GLES20.glDisable(GLES20.GL_BLEND);
        } else {
//            long time = 0, lasttime = 0;
//            lasttime = System.currentTimeMillis();

            GLES20.glEnable(GLES20.GL_BLEND);
            GLES20.glBlendFunc(GLES20.GL_ONE, GLES20.GL_ONE);
            middleFilter.onDrawFrame();
            GLES20.glDisable(GLES20.GL_BLEND);

//            time = System.currentTimeMillis();
//            Log.i(TAG, "diff: "+ (time - lasttime));
//            lasttime = time;

            GLES20.glEnable(GLES20.GL_BLEND);
            GLES20.glBlendFunc(GLES20.GL_ZERO, GLES20.GL_SRC_COLOR);
            frontFilter.onDrawFrame();
            GLES20.glDisable(GLES20.GL_BLEND);
//            time = System.currentTimeMillis();
//            Log.i(TAG, "diff: "+ (time - lasttime));
        }
//        fbo.unbind();
//
//        GLES20.glViewport((int)viewportRect.left, (int)viewportRect.top, (int)viewportRect.width(), (int)viewportRect.height());
//        passThroughFilter.onDrawFrame(fbo.getTextureId());

        mDisplaySurface.swapBuffers();

//        // 水印录制 状态设置
//        if(mRequestRecord) {
//            if(!recording) {
//                mRecordEncoder.startRecording(new RecordEncoder.EncoderConfig(
//                        outputFile, VIDEO_WIDTH, VIDEO_HEIGHT, 1000000,
//                        EGL14.eglGetCurrentContext(), TemplateEditorActivity.this));
//                mRecordEncoder.setTextureId(fbo.getTextureId());
//                recording = mRecordEncoder.isRecording();
//            }
//            // mRecordEncoder.setTextureId(mTextureId);
//            mRecordEncoder.frameAvailable(mVideoSurfaceTexture);
//        } else {
//            if(recording) {
//                mRecordEncoder.stopRecording();
//                recording = false;
//            }
//        }
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        Log.d(TAG, "surfaceDestroyed holder=" + holder);

        backFilter.destroy();
        middleFilter.destroy();
        frontFilter.destroy();

        if (mDisplaySurface != null) {
            mDisplaySurface.release();
            mDisplaySurface = null;
        }

        if (mEglCore != null) {
            mEglCore.release();
            mEglCore = null;
        }
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.template_editor_back:
                Log.d(TAG, "onClick: back");
                mRequestRecord = true;
                break;
            case R.id.template_editor_next:
                Log.d(TAG, "onClick: next");
                mRequestRecord = false;
                break;
        }
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

    public static class MainHandler extends Handler {
        private WeakReference<TemplateEditorActivity> mWeakActivity;

        public static final int MSG_FRAME_AVAILABLE = 1;

        MainHandler(TemplateEditorActivity activity) {
            mWeakActivity = new WeakReference<TemplateEditorActivity>(activity);
        }

        @Override
        public void handleMessage(Message msg) {
            TemplateEditorActivity activity = mWeakActivity.get();
            if (activity == null) {
                Log.d(TAG, "Got message for dead activity");
                return;
            }
            switch (msg.what) {
                case MSG_FRAME_AVAILABLE:
                    activity.drawFrame();
                    break;
                default:
                    super.handleMessage(msg);
                    break;
            }
        }
    }
}
