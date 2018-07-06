package com.xmb.muldecodedemo.programs;

import android.content.Context;
import android.opengl.GLES20;
import android.util.Log;

import com.xmb.muldecodedemo.utils.OpenGlUtils;

import javax.security.auth.login.LoginException;

/**
 * Created by Administrator on 2018/7/2 0002.
 */

public class GLAbsProgram {
    private static final String TAG = "GLAbsProgram";
    Context context;

    int vertexResourceId;
    int fragmentresourceId;

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

    public GLAbsProgram(Context context, int vertexResourceId, int fragmentresourceId) {
        this.context = context;
        this.vertexResourceId = vertexResourceId;
        this.fragmentresourceId = fragmentresourceId;
    }

    public void create(){
        mProgramId = OpenGlUtils.createProgram(
                OpenGlUtils.readShaderFromRawResource(context, this.vertexResourceId),
                OpenGlUtils.readShaderFromRawResource(context, this.fragmentresourceId));
//        Log.e(TAG, "create: mProgramId: "+ mProgramId);
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
