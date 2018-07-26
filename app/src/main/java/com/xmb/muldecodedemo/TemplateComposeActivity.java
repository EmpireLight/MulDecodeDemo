package com.xmb.muldecodedemo;

import android.app.Activity;

import android.content.Context;
import android.graphics.Rect;
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

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.xmb.muldecodedemo.bean.ConfigBean;
import com.xmb.muldecodedemo.egl.EglCore;
import com.xmb.muldecodedemo.egl.WindowSurface;
import com.xmb.muldecodedemo.filter.AbsFilter;
import com.xmb.muldecodedemo.filter.BackVideoFilter;
import com.xmb.muldecodedemo.filter.ImageFilter;
import com.xmb.muldecodedemo.filter.PassThroughFilter;
import com.xmb.muldecodedemo.filter.YUVFilter;
import com.xmb.muldecodedemo.utils.FBO;
import com.xmb.muldecodedemo.utils.FileUtils;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.ref.WeakReference;
import java.lang.reflect.Type;


/**
 * Created by Administrator on 2018/7/11 0011.
 */

public class TemplateComposeActivity extends Activity implements SurfaceHolder.Callback,
        View.OnClickListener{
    private final static String TAG = "TemplateComposeActivity";

    String asset0MP4 = FileUtils.getSDPath() + "/" + "asset0.mp4";
    String asset1MP4 = FileUtils.getSDPath() + "/" + "asset1.mp4";
    String assetMP4 = FileUtils.getSDPath() + "/" + "asset1.mp4";
    String musicMP3 =  FileUtils.getSDPath() + "/" + "music.mp3";
    String pngImg = FileUtils.getSDPath() + "/" + "ui2.png";
    String config = FileUtils.getSDPath() + "/" + "config.json";

    SurfaceView sv;
    private EglCore mEglCore;
    private WindowSurface mDisplaySurface;

    public BackVideoFilter backFilter;
    public YUVFilter middleFilter;
    public YUVFilter frontFilter;
    public SurfaceTexture mVideoSurfaceTexture;

    public ImageFilter pngFilter;

    public PassThroughFilter passThroughFilter;

    private float mLastX = 0;
    private float mLastY = 0;
    // 记录视口的坐标
    private Rect viewportRect;
    //显示视口参数
    private int disVPx, disVPy;
    private int disVPWidth, disVPHeight;
    //标识符，判断当前是否在触摸
    private boolean touchFlag;

    MainHandler mHandler;
    //判断Viewport是否初始化
    private boolean isViewportInit = false;

    private File outputFile;
    private volatile boolean mRequestRecord;
    private RecordEncoder mRecordEncoder;
    private boolean recording;

    public FBO fbo = null;

    String asset0Video;//模板视频0（黑白），用作middleFilter中景纹理
    String asset1Video;//模板视频1（素材），用作frontFilter中景纹理
    String userSelVideo;//用户所选视频
    String musicAudio;//模板自带音频

    private Gson gson;

    ConfigBean configBean;

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

        asset0Video = asset0MP4;
        asset1Video = asset1MP4;
        userSelVideo = assetMP4;
        musicAudio = musicMP3;

        initData();
        Log.w(TAG, "onCreate: initData");
    }

    private void initData() {
        gson = new Gson();
        configBean = getConfigBean();
        Log.w(TAG, "initData: ");
        Log.d(TAG, "initData: size[0] = " + configBean.size[0] );
    }

    private ConfigBean getConfigBean() {
        Type configType = new TypeToken<ConfigBean>() {
        }.getType();
        return gson.fromJson(getJson(config), configType);
    }

    /**
     * 将json文件中的文件转换成字符串
     *
     * @param path
     * @return
     */
    private String getJson(String path) {
        File file = new File(path);

        InputStream stream = null;
        try {
            stream = new FileInputStream(file);
            BufferedReader reader = new BufferedReader(new InputStreamReader(stream, "utf-8"));
            StringBuilder sb = new StringBuilder();
            String line = null;
            while ((line = reader.readLine()) != null) {
                sb.append(line + "\n");
            }
            stream.close();
            reader.close();
            return sb.toString();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return "";
    }

    @Override
    protected void onPause() {
        super.onPause();
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        // 准备好EGL环境，创建渲染介质mDisplaySurface
        mEglCore = new EglCore(null, EglCore.FLAG_RECORDABLE);

        mDisplaySurface = new WindowSurface(mEglCore, holder.getSurface(), false);
        mDisplaySurface.makeCurrent();

        middleFilter = new YUVFilter(this);
        middleFilter.init(asset0Video, 0);

        frontFilter = new YUVFilter(this);
        frontFilter.init(asset1Video, 1);

        backFilter = new BackVideoFilter(this);
        //注意这里，创建了一个SurfaceTexture
        mVideoSurfaceTexture = new SurfaceTexture(backFilter.getTextureId());
        mVideoSurfaceTexture.setOnFrameAvailableListener(new SurfaceTexture.OnFrameAvailableListener() {
            @Override
            public void onFrameAvailable(SurfaceTexture surfaceTexture) {
                mHandler.sendEmptyMessage(MainHandler.MSG_FRAME_AVAILABLE);
            }
        });
        backFilter.init(userSelVideo, mVideoSurfaceTexture);

        passThroughFilter = new PassThroughFilter();
        passThroughFilter.init();
        mRecordEncoder = new RecordEncoder();

        outputFile = new File(Environment.getExternalStorageDirectory().getPath(), "camera-test.mp4");

        recording = mRecordEncoder.isRecording();
        mRequestRecord = false;

        pngFilter = new ImageFilter(this);
        pngFilter.init(pngImg);
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        Log.d(TAG, "surfaceChanged:  holder = " + holder + ", width" + width +", height = " + height);
        backFilter.setScreenWH(width, height);

        //设置清屏颜色
        GLES20.glClearColor(0.0f, 0.0f, 0.0f, 1.0f);
    }


    private void drawFrame() {
        if (mEglCore == null) {
            Log.d(TAG, "Skipping drawFrame after shutdown");
            return;
        }
        GLES20.glClear(GLES20.GL_DEPTH_BUFFER_BIT | GLES20.GL_COLOR_BUFFER_BIT);
        mDisplaySurface.makeCurrent();
        mVideoSurfaceTexture.updateTexImage();

        if (!isViewportInit) {
            viewportRect = calDisViewPort(sv.getWidth(), sv.getHeight(),
                    frontFilter.getVideoWidth(), frontFilter.getVideoHeight());
            isViewportInit = true;
        }


        if (frontFilter.isEnd()||middleFilter.isEnd()) {
            Log.e(TAG, "frontFilter isEnd");
            mRequestRecord = false;
        } else {
            if (mRequestRecord) {
                recordDraw();
            } else {
                previewDraw();
            }
            mDisplaySurface.swapBuffers();
        }

        // 录制 状态设置
        if(mRequestRecord) {
            if(!recording) {
                mRecordEncoder.startRecording(new RecordEncoder.EncoderConfig(
                        outputFile, musicMP3,
                        frontFilter.getVideoWidth(), frontFilter.getVideoHeight(),
                        2500000,
                        EGL14.eglGetCurrentContext(), TemplateComposeActivity.this));
                mRecordEncoder.setTextureId(fbo.getTextureId());
                recording = mRecordEncoder.isRecording();
            }

            mRecordEncoder.frameAvailable(mVideoSurfaceTexture);
        } else {
            if(recording) {
                mRecordEncoder.stopRecording();
                middleFilter.stop();
                frontFilter.stop();
                recording = false;
//                encodeFinish();
                finish();
            }
        }
    }

    private void previewDraw() {
        GLES20.glViewport(viewportRect.left, viewportRect.top, viewportRect.width(), viewportRect.height());

        backFilter.onDrawFrame(backFilter.getTextureId());
        if(touchFlag) {
            GLES20.glEnable(GLES20.GL_BLEND);
            GLES20.glBlendFunc(GLES20.GL_ONE_MINUS_DST_COLOR, GLES20.GL_DST_ALPHA);
            pngFilter.onDrawFrame(pngFilter.getTextureID());
            GLES20.glDisable(GLES20.GL_BLEND);
        } else {
            GLES20.glEnable(GLES20.GL_BLEND);
            GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA);//GL_DST_ALPHA  GL_ONE_MINUS_SRC_ALPHA
            pngFilter.onDrawFrame(pngFilter.getTextureID());
            GLES20.glDisable(GLES20.GL_BLEND);
        }
    }

    private void recordDraw() {
        if (fbo == null) {
            fbo = FBO.newInstance().create(frontFilter.getVideoWidth(), frontFilter.getVideoHeight());
            Log.i(TAG, "recordDraw: ");
        }

        fbo.bind();
        GLES20.glViewport(0, 0,
                frontFilter.getVideoWidth(),
                frontFilter.getVideoHeight());
        backFilter.onDrawFrame(backFilter.getTextureId());

        GLES20.glEnable(GLES20.GL_BLEND);
        GLES20.glBlendFunc(GLES20.GL_ONE, GLES20.GL_ONE);
        middleFilter.onDrawFrame(true);
        GLES20.glDisable(GLES20.GL_BLEND);

        GLES20.glEnable(GLES20.GL_BLEND);
        GLES20.glBlendFunc(GLES20.GL_ZERO, GLES20.GL_SRC_COLOR);
        frontFilter.onDrawFrame(true);
        GLES20.glDisable(GLES20.GL_BLEND);
        fbo.unbind();
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
                mRequestRecord = false;
                break;
            case R.id.template_editor_next:
                Log.d(TAG, "onClick: next");
                backFilter.restart();
                mRequestRecord = true;
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
                    translate(current_x, current_y, mLastX, mLastY, backFilter);
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
    public void translate(float current_x, float current_y, float last_x, float last_y, AbsFilter absFilter){
        float normalizedX = ((current_x - viewportRect.left) / viewportRect.width()) * 2 - 1;
        float normalizedY = -(((current_y - viewportRect.top)/ viewportRect.height()) * 2 - 1);
        float lastNormalizedX = ((last_x - viewportRect.left) / viewportRect.width()) * 2 - 1;
        float lastNormalizedY = -(((last_y - viewportRect.top)/ viewportRect.height()) * 2 - 1);
        float moveX = (normalizedX - lastNormalizedX)*2.5f;
        float moveY = (normalizedY - lastNormalizedY)*2.5f;
        Log.i(TAG, "onTouchEvent: ACTION_MOVE moveX = " + moveX + ", moveY = " + moveY);

        Matrix.setIdentityM(absFilter.getModelMatrix(), 0);
        Matrix.translateM(absFilter.getModelMatrix(),0, moveX, moveY, 0);
        Matrix.multiplyMM(
                absFilter.getMVPMatrix(), 0,
                absFilter.getMVPMatrix(), 0,
                absFilter.getModelMatrix(),0);
    }

    public void rotate(float angle, AbsFilter absFilter) {
        Matrix.setIdentityM(absFilter.getModelMatrix(), 0);
        Matrix.rotateM(absFilter.getModelMatrix(),0, angle, 0, 0, 1);

        Matrix.multiplyMM(
                absFilter.getMVPMatrix(), 0,
                absFilter.getMVPMatrix(), 0,
                absFilter.getModelMatrix(),0);
    }

    private Rect calDisViewPort(int viewWidth, int viewHeight, int imageWidth, int imageHeight) {

        /**
         * 视口（Viewport）就是最终渲染结果显示的目的地
         * viewport视口以模板视频的宽高为基准满屏适配
         *
         * 应该有两个视口，一个是用户调试用的视口
         * 一个是最后合成渲染的视口，这个视口应该是前景或中景的分辨率大小
         */
        float videoRatio = (float)imageWidth/imageHeight;
        disVPWidth = (int)((float)viewHeight*videoRatio);//视口宽度;
        disVPHeight = viewHeight;

        Log.i(TAG, "onSurfaceChanged: VPWidth:" + disVPWidth +" VPHeight:" + disVPHeight);
        disVPx = viewWidth/2- disVPWidth/2;
        disVPy = viewHeight/2- disVPHeight/2;

        Log.i(TAG, "disVPx: "+disVPx );
        Log.i(TAG, "disVPy: "+disVPy );
        Log.i(TAG, "disVPWidth: "+disVPWidth );
        Log.i(TAG, "disVPHeight: "+disVPHeight );
        return new Rect(disVPx, disVPy, disVPx + disVPWidth, disVPy + disVPHeight);
    }

    public static class MainHandler extends Handler {
        private WeakReference<TemplateComposeActivity> mWeakActivity;

        public static final int MSG_FRAME_AVAILABLE = 1;

        MainHandler(TemplateComposeActivity activity) {
            mWeakActivity = new WeakReference<TemplateComposeActivity>(activity);
        }

        @Override
        public void handleMessage(Message msg) {
            TemplateComposeActivity activity = mWeakActivity.get();
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
