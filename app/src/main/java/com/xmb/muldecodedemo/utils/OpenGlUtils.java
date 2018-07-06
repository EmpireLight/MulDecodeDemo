package com.xmb.muldecodedemo.utils;

import android.content.Context;
import android.content.res.AssetManager;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.opengl.GLES11Ext;
import android.opengl.GLES20;
import android.util.Log;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

import javax.microedition.khronos.opengles.GL10;

/**
 * Created by Administrator on 2018/5/17.
 */

public class OpenGlUtils {
    public static final int NO_TEXTURE = -1;
    public static final int NOT_INIT = -1;
    public static final int ON_DRAWN = 1;


    public static final float TEXTURE_NO_ROTATION[] = {
            0.0f, 1.0f,
            1.0f, 1.0f,
            0.0f, 0.0f,
            1.0f, 0.0f,
    };

    public static final float CUBE[] = {
            -1.0f, -1.0f,
            1.0f, -1.0f,
            -1.0f, 1.0f,
            1.0f, 1.0f,
    };

    public static void bindTexture2D(int textureId,int activeTextureID,int handle,int idx){
        if (textureId != NO_TEXTURE) {
            GLES20.glActiveTexture(activeTextureID);
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId);
            GLES20.glUniform1i(handle, idx);
        }
    }

    public static void bindTextureOES(int textureId,int activeTextureID,int handle,int idx){
        if (textureId != NO_TEXTURE) {
            GLES20.glActiveTexture(activeTextureID);
            GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textureId);
            GLES20.glUniform1i(handle, idx);
        }
    }

    public static int createProgram(String vertexSource, String fragmentSource) {
        int vertextShader;
        int fragmentShader;
        int programId;
        int[] link = new int[1];

        vertextShader = loadShader(vertexSource, GLES20.GL_VERTEX_SHADER);
        if (vertextShader == 0) {
            Log.e("Load Program", "Vertex Shader Failed");
            return 0;
        }
        fragmentShader = loadShader(fragmentSource, GLES20.GL_FRAGMENT_SHADER);
        if (fragmentShader == 0) {
            Log.e("Load Program", "Fragment Shader Failed");
            return 0;
        }

        /**创建着色器程序*/
        programId = GLES20.glCreateProgram();

        /**向程序中加入顶点着色器*/
        GLES20.glAttachShader(programId, vertextShader);
        GLES20.glAttachShader(programId, fragmentShader);

        /**链接shader到程序*/
        GLES20.glLinkProgram(programId);
        GLES20.glGetProgramiv(programId, GLES20.GL_LINK_STATUS, link, 0);
        if (link[0] <= 0) {
            Log.e("Load Program", "Linking Failed");
            return 0;
        }

        /**链接shader到program后就可以删掉shader了*/
        GLES20.glDeleteShader(vertextShader);
        GLES20.glDeleteShader(fragmentShader);
        return programId;
    }

    private static int loadShader(String shaderCode, int type){

        // create a vertex shader type (GLES20.GL_VERTEX_SHADER)
        // or a fragment shader type (GLES20.GL_FRAGMENT_SHADER)
        int shader = GLES20.glCreateShader(type);

        // add the source code to the shader and compile it
        GLES20.glShaderSource(shader, shaderCode);
        // 编译shader
        GLES20.glCompileShader(shader);
        int[] compiled = new int[1];
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, compiled, 0);
        if (compiled[0] == 0) {
            Log.e("Load Shader Failed", "Compilation\n" + GLES20.glGetShaderInfoLog(shader));
            GLES20.glDeleteShader(shader);
            return 0;
        }
        return shader;
    }

    private static Bitmap getImageFromAssetsFile(Context context, String fileName){
        Bitmap image = null;
        AssetManager am = context.getResources().getAssets();
        try{
            InputStream is = am.open(fileName);
            image = BitmapFactory.decodeStream(is);
            is.close();
        }catch (IOException e){
            e.printStackTrace();
        }
        return image;
    }

    public static int loadExternalOESTextureID(){
        int[] texture = new int[1];
        GLES20.glGenTextures(1, texture, 0);
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, texture[0]);
        checkGlError("glBindTexture");

        /**设置缩小过滤为使用纹理中坐标最接近的一个像素的颜色作为需要绘制的像素颜色*/
        GLES20.glTexParameterf(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GL10.GL_TEXTURE_MIN_FILTER,GL10.GL_LINEAR);
        /**设置放大过滤为使用纹理中坐标最接近的若干个颜色，通过加权平均算法得到需要绘制的像素颜色*/
        GLES20.glTexParameterf(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GL10.GL_TEXTURE_MAG_FILTER, GL10.GL_LINEAR);
        /**设置环绕方向S，截取纹理坐标到[1/2n,1-1/2n]。将导致永远不会与border融合*/
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GL10.GL_TEXTURE_WRAP_S, GL10.GL_CLAMP_TO_EDGE);
        /**设置环绕方向T，截取纹理坐标到[1/2n,1-1/2n]。将导致永远不会与border融合*/
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GL10.GL_TEXTURE_WRAP_T, GL10.GL_CLAMP_TO_EDGE);
        return texture[0];
    }

    public static int loadNormalTextureID() {
        int[] texture = new int[1];
        GLES20.glGenTextures(1, texture, 0);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texture[0]);
        checkGlError("glBindTexture");

        /**设置缩小过滤为使用纹理中坐标最接近的一个像素的颜色作为需要绘制的像素颜色*/
        GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D, GL10.GL_TEXTURE_MIN_FILTER,GL10.GL_LINEAR);
        /**设置放大过滤为使用纹理中坐标最接近的若干个颜色，通过加权平均算法得到需要绘制的像素颜色*/
        GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D, GL10.GL_TEXTURE_MAG_FILTER, GL10.GL_LINEAR);
        /**设置环绕方向S，截取纹理坐标到[1/2n,1-1/2n]。将导致永远不会与border融合*/
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GL10.GL_TEXTURE_WRAP_S, GL10.GL_CLAMP_TO_EDGE);
        /**设置环绕方向T，截取纹理坐标到[1/2n,1-1/2n]。将导致永远不会与border融合*/
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GL10.GL_TEXTURE_WRAP_T, GL10.GL_CLAMP_TO_EDGE);
        return texture[0];
    }

    /**
     * 读取raw文件中shader
     * @param context
     * @param resourceId
     * @return
     */
    public static String readShaderFromRawResource(Context context, int resourceId){
        StringBuilder body = new StringBuilder();

        try {
            InputStream inputStream =
                    context.getResources().openRawResource(resourceId);
            InputStreamReader inputStreamReader =
                    new InputStreamReader(inputStream);
            BufferedReader bufferedReader = new BufferedReader(inputStreamReader);

            String nextLine;

            while ( (nextLine = bufferedReader.readLine()) != null) {
                body.append(nextLine);
                body.append('\n');
            }
        } catch (IOException e) {
            throw new RuntimeException(
                    "Could not open resource: " + resourceId, e);
        } catch (Resources.NotFoundException nfe) {
            throw new RuntimeException("Resources not found: " + resourceId, nfe);
        }

        return body.toString();
    }

    /**
     * Checks to see if a GLES error has been raised.
     */
    public static void checkGlError(String op) {
        int error = GLES20.glGetError();
        if (error != GLES20.GL_NO_ERROR) {
            String msg = op + ": glError 0x" + Integer.toHexString(error);
            Log.e("OpenGlUtils", msg);
            throw new RuntimeException(msg);
        }
    }

    public static void deleteTexture(int textureId ){
        int[] textures = new int[1];
        textures[0] = textureId;
        GLES20.glDeleteTextures(1,textures,0);
    }
}
