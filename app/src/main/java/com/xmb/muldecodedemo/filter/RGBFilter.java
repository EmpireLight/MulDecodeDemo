package com.xmb.muldecodedemo.filter;

import android.content.Context;
import android.opengl.GLES20;
import android.util.Log;

import com.xmb.muldecodedemo.R;
import com.xmb.muldecodedemo.programs.GLAbsProgram;
import com.xmb.muldecodedemo.utils.OpenGlUtils;

import java.nio.ByteBuffer;

/**
 * Created by Administrator on 2018/7/6 0006.
 */

public class RGBFilter extends AbsFilter {
    private final static String TAG = "ImageFilter";

    private GLAbsProgram glPassThroughProgram;
    private int TextureID;
    public boolean hasInit;
    public ByteBuffer data;
    int seqNumber = -1;//解码器序号（很重要）

    int width;
    int height;
    int size;

    public byte[] dataByte;

    public RGBFilter(Context context) {
        super();
        glPassThroughProgram = new GLAbsProgram(context, R.raw.base_vertex, R.raw.base_fragment);
        glPassThroughProgram.create();

        TextureID = OpenGlUtils.loadNormalTextureID();
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
        OpenGlUtils.deleteTexture(TextureID);
    }

    @Override
    public void onDrawFrame(int textureId) {

    }

    public void onDrawFrame () {
        glPassThroughProgram.use();

        if ((data == null)) {
            width = getYUVWidth(seqNumber);
            height = getYUVHeight(seqNumber);
            size = width * height * 4;
            Log.e(TAG, "init: width = " + width);
            Log.e(TAG, "init: height = " + height);
            data = ByteBuffer.allocate(size);
            dataByte = new byte[size];
        }

//      更新YUV数据
        int ret = updateData(dataByte, seqNumber);
        if (ret < 0) {
            Log.e(TAG, "onDrawFrame: ret = " + ret );
            return;
        }
        data.clear();
        data.put(dataByte,0,width*height*4);
        data.position(0);

        Log.e(TAG, "onDrawFrame: position"  );
        //更新纹理数据
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, TextureID);
        OpenGlUtils.checkGlError("glBindTexture");
        GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA, width, height, 0,
                GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE,  data );
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0);

        OpenGlUtils.checkGlError("glTexImage2D");
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0);

        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, TextureID);
        GLES20.glUniform1i(glPassThroughProgram.getSampleTexHandle(), 0);

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

    public native String initdecoder(String path, int seqNumber);
    public native int updateData(byte[] data, int seqNumber);

    public native int getYUVWidth(int seqNumber);
    public native int getYUVHeight(int seqNumber);
}
