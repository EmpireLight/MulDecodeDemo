package com.xmb.muldecodedemo.egl;

import android.graphics.SurfaceTexture;
import android.opengl.EGL14;
import android.opengl.EGLConfig;
import android.opengl.EGLContext;
import android.opengl.EGLDisplay;
import android.opengl.EGLExt;
import android.opengl.EGLSurface;
import android.util.Log;
import android.view.Surface;

import com.xmb.muldecodedemo.utils.OpenGlUtils;

import static android.opengl.EGLExt.EGL_RECORDABLE_ANDROID;

/**
 * 参考：https://blog.csdn.net/a360940265a/article/details/80090070
 * 还有参考grafika 的TextureMovieEncoder
 * 1、获取EGLDisplay对象
 * 2、初始化与EGLDisplay之间的关联。
 * 3、获取EGLConfig对象
 * 4、创建EGLContext 实例
 * 5、创建EGLSurface实例
 * 6、连接EGLContext和EGLSurface.
 *（以上封装在GLSurfaceview，对使用者透明）
 * 7、使用GL指令绘制图形         <—— renderer三大回调 渲染死循环。
 *（以下也是在GLSurfaceview，对使用者透明）
 * 8、断开并释放与EGLSurface关联的EGLContext对象
 * 9、删除EGLSurface对象
 * 10、删除EGLContext对象
 * 11、终止与EGLDisplay之间的连接。
 * Created by Administrator on 2018/7/11 0011.
 */

public class EglCore {
    private final static String TAG = "EglCore";

    public static final int FLAG_RECORDABLE = 0x01;
    public static final int FLAG_TRY_GLES2 = 0x02;
    public static final int FLAG_TRY_GLES3 = 0x04;

    // EGLExt.EGL_RECORDABLE_ANDROID = 12610; (required 26)要求SDK26，我们自己定义就好了

    private EGLDisplay mEGLDisplay = EGL14.EGL_NO_DISPLAY;
    private EGLContext mEGLContext = EGL14.EGL_NO_CONTEXT;
    private EGLConfig mEGLConfig = null;
    private int mGlVersion = -1;

    public int getGlVersion() {
        return mGlVersion;
    }

    public EglCore() {
        this(null, FLAG_TRY_GLES2);
    }

    /**
     * 查看当前的 EGLDisplay, EGLContext, EGLSurface.
     */
    public static void logCurrent() {
        EGLDisplay display;
        EGLContext context;
        EGLSurface surface;

        display = EGL14.eglGetCurrentDisplay();
        context = EGL14.eglGetCurrentContext();
        surface = EGL14.eglGetCurrentSurface(EGL14.EGL_DRAW);
        Log.i(TAG, "Current EGL state : display=" + display + ", context=" + context +
                ", surface=" + surface);
    }

    /**
     * 执行EGL的display context.
     * @param sharedContext The context to share, or null if sharing is not desired.
     * @param flags Configuration bit flags, e.g. FLAG_RECORDABLE.
     */
    public EglCore(EGLContext sharedContext, int flags) {
        if (mEGLDisplay != EGL14.EGL_NO_DISPLAY) {
            throw new RuntimeException("EGL already set up");
        }
        if (sharedContext == null) {
            sharedContext = EGL14.EGL_NO_CONTEXT;
        }

        // 1、获取EGLDisplay对象
        mEGLDisplay = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY);
        if (mEGLDisplay == EGL14.EGL_NO_DISPLAY) {
            throw new RuntimeException("unable to get EGL14 display");
        }

        // 2、初始化与EGLDisplay之间的关联。
        int[] version = new int[2];
        if (!EGL14.eglInitialize(mEGLDisplay, version, 0, version, 1)) {
            mEGLDisplay = null;
            throw new RuntimeException("unable to initialize EGL14");
        }

        // 3、获取EGLConfig对象
        if ((flags & FLAG_TRY_GLES3) != 0) {
            EGLConfig config = getConfig(flags, 3);
            if (config != null) {
                int[] attrib3_list = {
                        EGL14.EGL_CONTEXT_CLIENT_VERSION, 3,
                        EGL14.EGL_NONE
                };
                EGLContext context = EGL14.eglCreateContext(mEGLDisplay, config, sharedContext, attrib3_list, 0);
                if (EGL14.eglGetError() == EGL14.EGL_SUCCESS) {
                    Log.d(TAG, "Got GLES 3 config");
                    mEGLConfig = config;
                    mEGLContext = context;
                    mGlVersion = 3;
                }
            }
        }

        //4、创建EGLContext 实例
        if (mEGLContext == EGL14.EGL_NO_CONTEXT) {  //如果只要求GLES版本2  又或者GLES3失败了。
            Log.d(TAG, "Trying GLES 2");
            EGLConfig config = getConfig(flags, 2);
            if (config != null) {
                int[] attrib2_list = {
                        EGL14.EGL_CONTEXT_CLIENT_VERSION, 2,
                        EGL14.EGL_NONE
                };
                EGLContext context = EGL14.eglCreateContext(mEGLDisplay, config, sharedContext, attrib2_list, 0);
                if (EGL14.eglGetError() == EGL14.EGL_SUCCESS) {
                    Log.d(TAG, "Got GLES 2 config");
                    mEGLConfig = config;
                    mEGLContext = context;
                    mGlVersion = 2;
                }
            }
        }
    }
    /**
     * 从本地设备中寻找合适的 EGLConfig.
     */
    private EGLConfig getConfig(int flags, int version) {
        int renderableType = EGL14.EGL_OPENGL_ES2_BIT;
        if (version >= 3) {
            renderableType |= EGLExt.EGL_OPENGL_ES3_BIT_KHR;
        }

        int[] attribList = {
                EGL14.EGL_RED_SIZE, 8,
                EGL14.EGL_GREEN_SIZE, 8,
                EGL14.EGL_BLUE_SIZE, 8,
                EGL14.EGL_ALPHA_SIZE, 8,
                //EGL14.EGL_DEPTH_SIZE, 16,
                //EGL14.EGL_STENCIL_SIZE, 8,
                EGL14.EGL_RENDERABLE_TYPE, renderableType,
                EGL14.EGL_NONE, 0,      // placeholder for recordable [@-3]
                EGL14.EGL_NONE
        };
        if ((flags & FLAG_RECORDABLE) != 0) {
            attribList[attribList.length - 3] = EGL_RECORDABLE_ANDROID;
            // EGLExt.EGL_RECORDABLE_ANDROID;0x3142(required 26)
            // 如果说希望保留自己的最低版本SDK，我们可以自己定义一个EGL_RECORDABLE_ANDROID=0x3142;
            attribList[attribList.length - 2] = 1;
        }
        EGLConfig[] configs = new EGLConfig[1];
        int[] numConfigs = new int[1];
        if (!EGL14.eglChooseConfig(mEGLDisplay, attribList, 0, configs, 0, configs.length, numConfigs, 0)) {
            Log.w(TAG, "unable to find RGBA8888 / " + version + " EGLConfig");
            return null;
        }
        return configs[0];
    }

    /**
     * 创建一个 EGL+Surface
     * 5、创建EGLSurface实例
     * @param surface
     * @return
     */
    public EGLSurface createWindowSurface(Object surface) {
        if (!(surface instanceof Surface) && !(surface instanceof SurfaceTexture)) {
            throw new RuntimeException("invalid surface: " + surface);
        }
        // 创建EGLSurface, 绑定传入进来的surface
        int[] surfaceAttribs = {
                EGL14.EGL_NONE
        };
        EGLSurface eglSurface = EGL14.eglCreateWindowSurface(mEGLDisplay, mEGLConfig, surface,
                surfaceAttribs, 0);
        OpenGlUtils.checkGlError("eglCreateWindowSurface");
        if (eglSurface == null) {
            throw new RuntimeException("surface was null");
        }
        return eglSurface;
    }

    /**
     * 查询当前surface的状态值。
     */
    public int querySurface(EGLSurface eglSurface, int what) {
        int[] value = new int[1];
        EGL14.eglQuerySurface(mEGLDisplay, eglSurface, what, value, 0);
        return value[0];
    }

    //6、连接EGLContext和EGLSurface.
    /**
     * Makes our EGL context current, using the supplied surface for both "draw" and "read".
     */
    public void makeCurrent(EGLSurface eglSurface) {
        if (mEGLDisplay == EGL14.EGL_NO_DISPLAY) {
            Log.d(TAG, "NOTE: makeCurrent w/r display");
        }
        if (!EGL14.eglMakeCurrent(mEGLDisplay, eglSurface, eglSurface, mEGLContext)) {
            throw new RuntimeException("eglMakeCurrent failed");
        }
    }

    /**
     * Makes our EGL context current, using the supplied "draw" and "read" surfaces.
     */
    public void makeCurrent(EGLSurface drawSurface, EGLSurface readSurface) {
        if (mEGLDisplay == EGL14.EGL_NO_DISPLAY) {
            Log.d(TAG, "NOTE: makeCurrent w/o display");
        }
        if (!EGL14.eglMakeCurrent(mEGLDisplay, drawSurface, readSurface, mEGLContext)) {
            throw new RuntimeException("eglMakeCurrent(draw,read) failed");
        }
    }

    // 7、使用GL指令绘制图形 <—— renderer三大回调 渲染死循环。
    /**
     * Calls eglSwapBuffers.  Use this to "publish" the current frame.
     * @return false on failure
     */
    public boolean swapBuffers(EGLSurface eglSurface) {
        return EGL14.eglSwapBuffers(mEGLDisplay, eglSurface);
    }

    // 8、断开并释放与EGLSurface关联的EGLContext对象
    /**
     * Makes no context current.
     */
    public void makeNothingCurrent() {
        if (!EGL14.eglMakeCurrent(mEGLDisplay, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE,
                EGL14.EGL_NO_CONTEXT)) {
            throw new RuntimeException("eglMakeCurrent To EGL_NO_SURFACE failed");
        }
    }

    /**
     * Sends the presentation time stamp to EGL.  Time is expressed in nanoseconds.
     */
    public void setPresentationTime(EGLSurface eglSurface, long nsecs) {
        EGLExt.eglPresentationTimeANDROID(mEGLDisplay, eglSurface, nsecs);
    }

    /**
     * 判断当前的EGLContext 和 EGLSurface是否同一个EGL
     */
    public boolean isCurrent(EGLSurface eglSurface) {
        return mEGLContext.equals(EGL14.eglGetCurrentContext()) &&
                eglSurface.equals(EGL14.eglGetCurrentSurface(EGL14.EGL_DRAW));
    }

    // 9、删除EGLSurface对象
    /**
     * Destroys the specified surface.
     * Note the EGLSurface won't actually be destroyed if it's still current in a context.
     */
    public void releaseSurface(EGLSurface eglSurface) {
        EGL14.eglDestroySurface(mEGLDisplay, eglSurface);
    }

    // 释放EGL资源 10、删除EGLContext对象 11、终止与EGLDisplay之间的连接
    public void release() {
        if (mEGLDisplay != EGL14.EGL_NO_DISPLAY) {
            // Android 使用一个引用计数EGLDisplay。
            // 因此，对于每个eglInitialize，我们需要一个eglTerminate。
            EGL14.eglMakeCurrent(mEGLDisplay, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE,
                    EGL14.EGL_NO_CONTEXT); // 确保EglSurface和EGLContext已经分离
            EGL14.eglDestroyContext(mEGLDisplay, mEGLContext);
            EGL14.eglReleaseThread();
            EGL14.eglTerminate(mEGLDisplay);
        }
        mEGLDisplay = EGL14.EGL_NO_DISPLAY;
        mEGLContext = EGL14.EGL_NO_CONTEXT;
        mEGLConfig = null;
    }

    @Override
    protected void finalize() throws Throwable {
        try {
            try {
                if (mEGLDisplay != EGL14.EGL_NO_DISPLAY) {
                    // (意外终止)终结器finalizer是不在保持EGL状态的线程上运行的，
                    // 我们要在这里完全释放它，或者这里直接抛出的异常。不过在这里抛异常都是没啥卵用了
                    Log.w(TAG, "WARNING: EglCore was not explicitly released -- state may be leaked");
                    release();
                }
            } finally {
                super.finalize();
            }
        } finally {
            super.finalize();
        }
    }
}
