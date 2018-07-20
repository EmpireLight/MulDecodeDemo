package com.xmb.muldecodedemo.filter;

import android.content.Context;
import android.opengl.GLES20;

import com.xmb.muldecodedemo.R;
import com.xmb.muldecodedemo.programs.BaseProgram;
import com.xmb.muldecodedemo.programs.GLAbsProgram;
import com.xmb.muldecodedemo.utils.OpenGlUtils;


/**
 * Created by Administrator on 2018/7/2 0002.
 */

public class PassThroughFilter extends AbsFilter {
    protected BaseProgram glPassThroughProgram;

    public boolean hasInit;

    public PassThroughFilter() {
        super(true);
//        super();
        glPassThroughProgram = new BaseProgram();
    }

    @Override
    public void init() {
        glPassThroughProgram.create();
        hasInit = true;
    }

    @Override
    public void destroy() {
        glPassThroughProgram.onDestroy();
    }

    @Override
    public void onDrawFrame(int textureId) {

        glPassThroughProgram.use();

        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        OpenGlUtils.checkGlError("glActiveTexture");
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId);
        OpenGlUtils.checkGlError("glBindTexture");
        GLES20.glUniform1i(glPassThroughProgram.getSampleTexHandle(), 0);
        OpenGlUtils.checkGlError("glUniform1i");
        GLES20.glUniformMatrix4fv(glPassThroughProgram.getMVPMatrixHandle(),1,false, mMVPMatrix,0);
        OpenGlUtils.checkGlError("glUniformMatrix4fv");
        //顶点坐标填充
        GLES20.glEnableVertexAttribArray(glPassThroughProgram.getPositionHandle());
        OpenGlUtils.checkGlError("glEnableVertexAttribArray");
        GLES20.glVertexAttribPointer(glPassThroughProgram.getPositionHandle(), COORDS_PER_VERTEX, GLES20.GL_FLOAT, false, vertexStride, mVerBuffer);
        OpenGlUtils.checkGlError("glVertexAttribPointer");
        //纹理坐标填充*/
        GLES20.glEnableVertexAttribArray(glPassThroughProgram.getTextureCoordinateHandle());
        OpenGlUtils.checkGlError("glEnableVertexAttribArray");
        GLES20.glVertexAttribPointer(glPassThroughProgram.getTextureCoordinateHandle(), COORDS_PER_VERTEX, GLES20.GL_FLOAT, false, vertexStride, mTexBuffer);
        OpenGlUtils.checkGlError("glVertexAttribPointer");
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP,0,4);
        OpenGlUtils.checkGlError("glDrawArrays");
        GLES20.glDisableVertexAttribArray(glPassThroughProgram.getPositionHandle());
        OpenGlUtils.checkGlError("glDisableVertexAttribArray");
        GLES20.glDisableVertexAttribArray(glPassThroughProgram.getTextureCoordinateHandle());
        OpenGlUtils.checkGlError("glDisableVertexAttribArray");
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0);
    }

    public void setTextureID(int textureID) {
//        this.TextureID = textureID;
    }
}
