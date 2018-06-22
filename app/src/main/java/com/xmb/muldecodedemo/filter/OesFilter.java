package com.xmb.muldecodedemo.filter;

import android.content.Context;
import android.opengl.GLES11Ext;
import android.opengl.GLES20;

import com.xmb.muldecodedemo.R;
import com.xmb.muldecodedemo.utils.OpenGlUtils;


/**
 * Created by Administrator on 2018/6/13 0013.
 */

public class OesFilter extends BaseFilter{
    public OesFilter(Context context) {
        super(context);
    }

    @Override
    public void createProgram() {
        super.mProgram = OpenGlUtils.createProgram(
                OpenGlUtils.readShaderFromRawResource(super.context, R.raw.oes_base_vertex),
                OpenGlUtils.readShaderFromRawResource(super.context, R.raw.oes_base_fragment));
    }

    public void onBindTexture() {
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, super.textureID);
        GLES20.glUniform1i(super.mSampleTexHandle, super.textureUnit);
    }

    @Override
    protected void onSizeChanged(int width, int height) {

    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        GLES20.glDeleteTextures(1, new int[]{super.getTextureID()},0);
    }
}
