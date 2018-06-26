package com.xmb.muldecodedemo.filter;

import android.opengl.GLES20;

/**
 * Created by Administrator on 2018/6/15 0015.
 */

public class FBO {
    private int frameBuffer;
    private int frameBufferTexture;

    private FBO() {}

    public static FBO newInstance(){
        return new FBO();
    }

    public FBO create(int width, int height) {
        int[] frameBuffers = new int[1];
        int[] frameBufferTextures = new int[1];
        GLES20.glGenFramebuffers(1, frameBuffers, 0);

        GLES20.glGenTextures(1, frameBufferTextures, 0);//创建纹理对象
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, frameBufferTextures[0]);//纹理对象绑定GL_TEXTURE_2D状态（类别？）并激活
        GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA,
                width,height, 0,
                GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, null);//为纹理对象申请显存，大小为width x height
        GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D,
                GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D,
                GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D,
                GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
        GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D,
                GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);

        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, frameBuffers[0]);//创建FBO
        GLES20.glFramebufferTexture2D(GLES20.GL_FRAMEBUFFER, GLES20.GL_COLOR_ATTACHMENT0,//将纹理附着于颜色附着点
                GLES20.GL_TEXTURE_2D, frameBufferTextures[0], 0);

        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0);
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0);

        frameBuffer=frameBuffers[0];
        frameBufferTexture=frameBufferTextures[0];

        return this;
    }

    public void destroy(){
        GLES20.glDeleteTextures(1, new int[]{frameBufferTexture}, 0);
        GLES20.glDeleteFramebuffers(1, new int[]{frameBuffer}, 0);
    }

    public void bind(){
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, frameBuffer);
    }
    public void unbind(){
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0);
    }

    public int getFrameBufferTextureId() {
        return frameBufferTexture;
    }
    public int getFrameBuffer() {
        return frameBuffer;
    }
}

