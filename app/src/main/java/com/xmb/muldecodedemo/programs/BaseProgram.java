package com.xmb.muldecodedemo.programs;

import android.opengl.GLES20;

import com.xmb.muldecodedemo.utils.OpenGlUtils;

/**
 * Created by Administrator on 2018/7/12 0012.
 */

public class BaseProgram {

    /**程序句柄*/
    private int mProgramId;

    /**顶点坐标句柄*/
    protected int mPositionHandle;
    /**纹理坐标句柄*/
    protected int mTextureCoordHandle;
    /**默认纹理贴图句柄*/
    protected int mSampleTexHandle;
    /**总变换矩阵句柄*/
    protected int mMVPMatrixHandle;

    private static final String VERTEX_SHADER =
                "attribute vec4 aPosition;\n" +
                        "attribute vec2 aTextureCoord;\n" +
                        "uniform mat4 uMatrix;\n" +
                        "varying vec2 vTextureCoord;\n" +
                        "void main() {\n" +
                        "     gl_Position = uMatrix * aPosition;\n" +
                        "     vTextureCoord = aTextureCoord;\n" +
                        "}\n";

    private static final String FRAGMENT_SHADER =
            "precision mediump float;\n" +
                    "varying vec2 vTextureCoord;\n" +
                    "uniform sampler2D sTexture;\n" +
                    "void main() {\n" +
                    "    gl_FragColor = texture2D( sTexture, vTextureCoord );\n" +
                    "}\n";

    public BaseProgram() {
    }

    public void create() {
        mProgramId = OpenGlUtils.createProgram(VERTEX_SHADER, FRAGMENT_SHADER);
        if (mProgramId != 0) {
            mMVPMatrixHandle = GLES20.glGetUniformLocation(mProgramId,"uMatrix");
            OpenGlUtils.checkGlError("glGetUniformLocation uMatrix");
            mSampleTexHandle = GLES20.glGetUniformLocation(mProgramId,"sTexture");
            OpenGlUtils.checkGlError("glGetUniformLocation sTexture");
            mPositionHandle = GLES20.glGetAttribLocation(mProgramId, "aPosition");
            OpenGlUtils.checkGlError("glGetAttribLocation aPosition");
            mTextureCoordHandle = GLES20.glGetAttribLocation(mProgramId, "aTextureCoord");
            OpenGlUtils.checkGlError("glGetAttribLocation aTextureCoord");
        } else {
            throw new RuntimeException("failed creating program");
        }
    }

    public void use(){
        GLES20.glUseProgram(mProgramId);
        OpenGlUtils.checkGlError("glUseProgram");
    }

    public int getProgramId() {
        return mProgramId;
    }

    public void onDestroy(){
        GLES20.glDeleteProgram(mProgramId);
    }

    public int getPositionHandle() {
        return mPositionHandle;
    }

    public int getTextureCoordinateHandle() {
        return mTextureCoordHandle;
    }

    public int getSampleTexHandle() {
        return mSampleTexHandle;
    }

    public int getMVPMatrixHandle() {
        return mMVPMatrixHandle;
    }
}
