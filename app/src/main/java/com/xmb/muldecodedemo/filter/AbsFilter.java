package com.xmb.muldecodedemo.filter;

import android.opengl.GLES20;

import com.xmb.muldecodedemo.utils.FBO;
import com.xmb.muldecodedemo.utils.MatrixUtils;
import com.xmb.muldecodedemo.utils.TextureRotationUtil;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.LinkedList;

/**
 * Created by Administrator on 2018/7/2 0002.
 */

public abstract class AbsFilter {
    private final LinkedList<Runnable> mPreDrawTaskList;
    protected int surfaceWidth,surfaceHeight;

    /**变换矩阵*/
    protected float[] mMVPMatrix;//
    protected float[] mModelMatrix;//模型矩阵
    protected float[] mProjectMatrix;//透视矩阵

    /**顶点坐标Buffer*/
    protected FloatBuffer mVerBuffer;
    /**纹理坐标Buffer*/
    protected FloatBuffer mTexBuffer;

    // number of coordinates per vertex in this array(x,y)
    protected static final int COORDS_PER_VERTEX = 2;
    protected final int vertexStride = COORDS_PER_VERTEX * 4; // 4 bytes per

    public AbsFilter() {
        mPreDrawTaskList = new LinkedList<Runnable>();

        /**顶点坐标Buffer初始化*/
        mVerBuffer = ByteBuffer.allocateDirect(TextureRotationUtil.CUBE.length * 4)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer();
        mVerBuffer.put(TextureRotationUtil.CUBE).position(0);

        /**纹理坐标Buffer初始化*/
        mTexBuffer = ByteBuffer.allocateDirect(TextureRotationUtil.TEXTURE_NO_ROTATION.length * 4)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer();
        mTexBuffer.put(TextureRotationUtil.TEXTURE_NO_ROTATION).position(0);
        /**默认将s纹理Y轴翻转*/
//        mTexBuffer.put(TextureRotationUtil.getRotation(Rotation.NORMAL, false, true)).position(0);

        /**默认4*4单位矩阵*/
        mMVPMatrix = MatrixUtils.getOriginalMatrix();
        mModelMatrix =  MatrixUtils.getOriginalMatrix();
        mProjectMatrix =  MatrixUtils.getOriginalMatrix();
    }

    abstract public void init();

    public void onPreDrawElements(){
        GLES20.glClearColor(0.0f, 0.0f, 0.0f, 1.0f);
        GLES20.glClear( GLES20.GL_DEPTH_BUFFER_BIT | GLES20.GL_COLOR_BUFFER_BIT);
    }

    public void onSurfaceCreated(int width, int height) {}

    abstract public void destroy();

    public void onFilterChanged(int surfaceWidth, int surfaceHeight){
        this.surfaceWidth=surfaceWidth;
        this.surfaceHeight=surfaceHeight;
    }

    void setViewport(){
        GLES20.glViewport(0, 0, surfaceWidth, surfaceHeight);
    }

    public FBO createFBO(){
        return FBO.newInstance().create(surfaceWidth,surfaceHeight);
    }

    abstract public void onDrawFrame(final int textureId);

    public void runPreDrawTasks() {
        while (!mPreDrawTaskList.isEmpty()) {
            mPreDrawTaskList.removeFirst().run();
        }
    }

    public void addPreDrawTask(final Runnable runnable) {
        synchronized (mPreDrawTaskList) {
            mPreDrawTaskList.addLast(runnable);
        }
    }

    public void setUniform1f(final int programId, final String name , final float floatValue) {
        int location=GLES20.glGetUniformLocation(programId,name);
        GLES20.glUniform1f(location,floatValue);
    }

    public void setMVPMatrix(float[] MVPMatrix) {
        this.mMVPMatrix = MVPMatrix;
    }

    public float[] getMVPMatrix() {
        return mMVPMatrix;
    }
}
