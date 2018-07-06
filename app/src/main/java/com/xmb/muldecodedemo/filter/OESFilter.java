package com.xmb.muldecodedemo.filter;

import android.content.Context;
import android.graphics.SurfaceTexture;
import android.opengl.GLES11Ext;
import android.opengl.GLES20;
import android.opengl.Matrix;
import android.util.Log;
import android.view.Surface;

import com.xmb.muldecodedemo.R;
import com.xmb.muldecodedemo.VideoDecoder;
import com.xmb.muldecodedemo.programs.GLAbsProgram;
import com.xmb.muldecodedemo.utils.FileUtils;
import com.xmb.muldecodedemo.utils.OpenGlUtils;

/**
 * Created by Administrator on 2018/7/2 0002.
 */

public class OESFilter extends AbsFilter{
    protected static final String TAG = "OESFilter";

    private int OESTextureID;
    private GLAbsProgram glOESProgram;

    public VideoDecoder videoDecoder;

    public boolean hasInit;

    public OESFilter(Context context) {
        super();
        glOESProgram = new GLAbsProgram(context, R.raw.oes_base_vertex, R.raw.oes_base_fragment);
        glOESProgram.create();
        OESTextureID = OpenGlUtils.loadExternalOESTextureID();
    }

    @Override
    public void init() {
        hasInit = true;
    }

    public void init(final String filePath, SurfaceTexture SurfaceTexture) {
        start_decode(filePath, SurfaceTexture);
        hasInit = true;
    }

    @Override
    public void destroy() {
        glOESProgram.onDestroy();
        OpenGlUtils.deleteTexture(OESTextureID);
    }

    @Override
    public void onSurfaceCreated(int width, int height) {
        /**用户所选视频以视口宽高为基准算矩阵*/
        //设置透视投影
        float screenRatio=(float) width / height;
        float videoRatio=(float) videoDecoder.videoWidth / videoDecoder.videoHeight;
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
        glOESProgram.use();

        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, OESTextureID);
        GLES20.glUniform1i(glOESProgram.getSampleTexHandle(), 0);

        GLES20.glUniformMatrix4fv(glOESProgram.getMVPMatrixHandle(),1,false, mMVPMatrix,0);

        // Enable a handle to the triangle vertices
        GLES20.glEnableVertexAttribArray(glOESProgram.getPositionHandle());
        // Prepare the <insert shape here> coordinate data
        GLES20.glVertexAttribPointer(glOESProgram.getPositionHandle(), COORDS_PER_VERTEX, GLES20.GL_FLOAT, false, vertexStride, mVerBuffer);

        GLES20.glEnableVertexAttribArray(glOESProgram.getTextureCoordinateHandle());
        GLES20.glVertexAttribPointer(glOESProgram.getTextureCoordinateHandle(), COORDS_PER_VERTEX, GLES20.GL_FLOAT, false, vertexStride, mTexBuffer);
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP,0,4);

        GLES20.glDisableVertexAttribArray(glOESProgram.getPositionHandle());
        GLES20.glDisableVertexAttribArray(glOESProgram.getTextureCoordinateHandle());
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0);
    }

    public void setOESTextureID(int OESTextureID) {
        this.OESTextureID = OESTextureID;
    }

    public int getTextureID() {
        return this.OESTextureID;
    }

    public void start_decode(final String filePath, SurfaceTexture surfaceTexture) {
        Surface surface = new Surface(surfaceTexture);

        videoDecoder = new VideoDecoder();
        videoDecoder.createDecoder(filePath, surface);

        Thread thread = new Thread() {
            public void run() {
                videoDecoder.videoDecode();
            }
        };
        thread.start();
    }
}
