package com.xmb.muldecodedemo.filter;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.opengl.GLES20;
import android.opengl.GLUtils;
import android.util.Log;

import com.xmb.muldecodedemo.R;
import com.xmb.muldecodedemo.programs.GLAbsProgram;
import com.xmb.muldecodedemo.utils.OpenGlUtils;

import java.io.File;

/**
 * Created by Administrator on 2018/7/6 0006.
 */

public class ImageFilter extends AbsFilter{
    private final static String TAG = "ImageFilter";

    private GLAbsProgram glPassThroughProgram;
    private int TextureID;

    public boolean hasInit;

    /**水印的放置位置和宽高*/
    public int x,y,w,h;
    /**图片宽高*/
    public int imgWitdh, imgHeight;

    public ImageFilter(Context context) {
        super();
        glPassThroughProgram = new GLAbsProgram(context, R.raw.base_vertex, R.raw.base_fragment);
        glPassThroughProgram.create();

        TextureID = OpenGlUtils.loadNormalTextureID();
    }

    @Override
    public void init() {
        hasInit = true;
    }

    public void init(final String filePath) {
        File f = new File(filePath);
        if (!f.exists()) {
            Log.e(TAG, "onDrawFrame: loadFile no exit");
        }
        Log.d(TAG, "init: filePath: " + filePath);
        updateImg(filePath);
    }

    @Override
    public void destroy() {
        glPassThroughProgram.onDestroy();
        OpenGlUtils.deleteTexture(TextureID);
    }

    @Override
    public void onDrawFrame(int textureId) {
        glPassThroughProgram.use();

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

    private int updateImg(final String filePath) {
        Bitmap bitmap = BitmapFactory.decodeFile(filePath);
        if (bitmap == null) {
            Log.e(TAG, "updateImg: bitmap is null");
            return -1;
        }
        imgWitdh = bitmap.getWidth();
        imgHeight = bitmap.getHeight();
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bitmap, 0);//给纹理加载图片
        bitmap.recycle();
        return 0;
    }

    public void setTextureID(int TextureID) {
        this.TextureID = TextureID;
    }
    public int getTextureID() {
        return this.TextureID;
    }

    public int getImgWitdh() {
        return this.imgWitdh;
    }
    public int getImgHeight() {
        return this.imgHeight;
    }
}
