package com.xmb.muldecodedemo.filter;

import android.content.Context;
import android.opengl.GLES20;
import android.opengl.Matrix;
import android.util.Log;

import com.xmb.muldecodedemo.R;
import com.xmb.muldecodedemo.programs.GLAbsProgram;
import com.xmb.muldecodedemo.utils.FileUtils;
import com.xmb.muldecodedemo.utils.OpenGlUtils;

import java.lang.invoke.ConstantCallSite;
import java.net.PortUnreachableException;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.Locale;

/**
 * Created by Administrator on 2018/7/3 0003.
 */

public class YUVFilter extends AbsFilter{
    private final static String TAG = "YUVFilter";

    protected GLAbsProgram glPassThroughProgram;

    int seqNumber = -1;//解码器序号（很重要）
    public byte[] data;

    private int[] textures=new int[3];
    private int[] mHTexs=new int[3];
    private ByteBuffer y,u,v;

    int YUVwidth;
    int YUVheight;
    int YUVsize;

    public boolean hasInit;

    public YUVFilter(Context context) {
        super();
        glPassThroughProgram = new GLAbsProgram(context, R.raw.base_vertex, R.raw.yuv_fragment);
        glPassThroughProgram.create();

        //获取纹理句柄
        mHTexs[0]= GLES20.glGetUniformLocation(glPassThroughProgram.getProgramId(),"texY");
        OpenGlUtils.checkGlError("mHTexs[0]");
        mHTexs[1]=GLES20.glGetUniformLocation(glPassThroughProgram.getProgramId(),"texU");
        OpenGlUtils.checkGlError("mHTexs[1]");
        mHTexs[2]=GLES20.glGetUniformLocation(glPassThroughProgram.getProgramId(),"texV");
        OpenGlUtils.checkGlError("mHTexs[2]");

        //生成YUV纹理
        GLES20.glGenTextures(3,textures,0);
        OpenGlUtils.checkGlError("glGenTextures");
        for (int i=0;i<3;i++) {
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D,textures[i]);
            OpenGlUtils.checkGlError("glBindTexture");
            GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D,GLES20.GL_TEXTURE_MAG_FILTER,GLES20.GL_LINEAR);
            GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D,GLES20.GL_TEXTURE_MIN_FILTER,GLES20.GL_LINEAR);
            GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D,GLES20.GL_TEXTURE_WRAP_S,GLES20.GL_CLAMP_TO_EDGE);
            GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D,GLES20.GL_TEXTURE_WRAP_T,GLES20.GL_CLAMP_TO_EDGE);
        }
    }

    @Override
    public void init() {

    }

    public void init(final String filePath, int seqNumber) {
        this.seqNumber = seqNumber;
        initdecoder(filePath, seqNumber);
        hasInit = true;
    }

    @Override
    public void destroy() {
        glPassThroughProgram.onDestroy();
        for (int i=0; i<3; i++) {
            OpenGlUtils.deleteTexture(textures[i]);
        }
    }

    @Override
    public void onSurfaceCreated(int width, int height) {
        /**用户所选视频以视口宽高为基准算矩阵*/
        //设置透视投影
        float screenRatio=(float) width / height;
        float videoRatio=(float) getYUVWidth(seqNumber) / getYUVHeight(seqNumber);
        if (videoRatio>screenRatio){
            Matrix.orthoM(mProjectMatrix,0,-1f,1f,-videoRatio/screenRatio,videoRatio/screenRatio,-1f,1f);
        }else {
            Matrix.orthoM(mProjectMatrix,0,-screenRatio/videoRatio,screenRatio/videoRatio,-1f,1f,-1f,1f);
        }
//        //设置相机位置
//        Matrix.setLookAtM(mViewMatrix, 0, 0, 0, 5.0f, 0f, 0f, 0f, 0f, 1.0f, 0.0f);
//        //计算变换矩阵
//        Matrix.multiplyMM(mMVPMatrix, 0, mProjectMatrix, 0, mViewMatrix,0);
        super.setMVPMatrix(mProjectMatrix);
    }

    @Override
    public void onDrawFrame(int textureId) {
    }

    public void onDrawFrame() {
        glPassThroughProgram.use();

        if ((data == null) || (data.length != YUVsize)) {
            YUVwidth = getYUVWidth(seqNumber);
            YUVheight = getYUVHeight(seqNumber);
            YUVsize = YUVwidth * YUVheight * 3 / 2;
            Log.e(TAG, "init: YUVwidth = " + YUVwidth);
            Log.e(TAG, "init: YUVheight = " + YUVheight);

            data = new byte[YUVsize];
            Log.e(TAG, "onDrawFrame: yuvFilter.data.length = " + this.data.length);
        }

        //更新YUV数据
        int ret = updateData(data, seqNumber);
//        if (ret < 0) {
//            Log.e(TAG, "onDrawFrame: ret = " + ret );
//            return;
//        }

        //更新纹理数据
        updateFrame(data);

        //绑定纹理
        for (int i=0;i<3;i++){
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0+i);
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textures[i]);
            GLES20.glUniform1i(mHTexs[i], i);
        }

        GLES20.glUniformMatrix4fv(glPassThroughProgram.getMVPMatrixHandle(),1,false, mMVPMatrix,0);

        // Enable a handle to the triangle vertices
        GLES20.glEnableVertexAttribArray(glPassThroughProgram.getPositionHandle());
        // Prepare the <insert shape here> coordinate data
        GLES20.glVertexAttribPointer(glPassThroughProgram.getPositionHandle(), COORDS_PER_VERTEX, GLES20.GL_FLOAT, false, vertexStride, mVerBuffer);

        GLES20.glEnableVertexAttribArray(glPassThroughProgram.getTextureCoordinateHandle());
        GLES20.glVertexAttribPointer(glPassThroughProgram.getTextureCoordinateHandle(), COORDS_PER_VERTEX, GLES20.GL_FLOAT, false, vertexStride, mTexBuffer);

        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP,0,4);

        GLES20.glDisableVertexAttribArray(glPassThroughProgram.getPositionHandle());
        GLES20.glDisableVertexAttribArray(glPassThroughProgram.getTextureCoordinateHandle());

        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0);
    }

    public void updateFrame(byte[] data) {
        if (y == null) {
            y = ByteBuffer.allocate(YUVwidth*YUVheight);
            u = ByteBuffer.allocate(YUVwidth*YUVheight>>2);
            v = ByteBuffer.allocate(YUVwidth*YUVheight>>2);
        }

        y.clear();
        y.put(data,0,YUVwidth*YUVheight);

        u.clear();
        u.put(data,YUVwidth*YUVheight,YUVwidth*YUVheight>>2);

        v.clear();
        v.put(data,YUVwidth*YUVheight+(YUVwidth*YUVheight>>2),YUVwidth*YUVheight>>2);
        y.position(0);
        u.position(0);
        v.position(0);

//        Log.e(TAG, "updateFrame: YUVwidth*YUVheight = " + YUVwidth*YUVheight);
//        Log.e(TAG, "updateFrame: YUVwidth*YUVheight>>2 = " + (YUVwidth*YUVheight+(YUVwidth*YUVheight>>2)));
//        Log.e(TAG, "updateFrame: YUVwidth*YUVheight+(YUVwidth*YUVheight>>2) = " + (YUVwidth*YUVheight+(YUVwidth*YUVheight>>2) + (YUVwidth*YUVheight>>2)));

        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D,textures[0]);
        GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D,0,GLES20.GL_LUMINANCE,YUVwidth,YUVheight,0,
                GLES20.GL_LUMINANCE, GLES20.GL_UNSIGNED_BYTE, y);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0);

        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D,textures[1]);
        GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D,0,GLES20.GL_LUMINANCE,YUVwidth>>1,YUVheight>>1, 0,
                GLES20.GL_LUMINANCE, GLES20.GL_UNSIGNED_BYTE, u);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0);

        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D,textures[2]);
        GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D,0,GLES20.GL_LUMINANCE,YUVwidth>>1,YUVheight>>1, 0,
                GLES20.GL_LUMINANCE, GLES20.GL_UNSIGNED_BYTE, v);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0);
    }

    /**
     * 第一个参数为文件路径，第二参数为解码器的序号
     * @param path
     * @param seqNumber
     * @return
     */
    public native String initdecoder(String path, int seqNumber);

    public native int updateData(byte[] data, int seqNumber);

    public native int getYUVWidth(int seqNumber);
    public native int getYUVHeight(int seqNumber);
}
