package com.xmb.muldecodedemo;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Environment;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.RelativeLayout;

import com.xmb.muldecodedemo.utils.FileUtils;
import com.xmb.muldecodedemo.utils.OutputImageFormat;
import com.xmb.muldecodedemo.utils.VideoToFrames;

public class MainActivity extends AppCompatActivity implements View.OnClickListener{
    private static final String TAG = "MainActivity";

    VideoEditorView videoEditorView;

    // Used to load the 'native-lib' library on application startup.
    static {
        System.loadLibrary("decoder");
    }

    private boolean decodeFlag0 = false;
    private boolean decodeFlag1 = false;
    OutputImageFormat outputImageFormat;
    String inputFile0;
    String inputFile1;
    String outputDirasset0;
    String outputDirasset1;

    RelativeLayout rootLayout;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        findViewById(R.id.back).setOnClickListener(this);
        findViewById(R.id.next).setOnClickListener(this);
        videoEditorView = (VideoEditorView)findViewById(R.id.video_editor);

        checkPermission();
    }

    private void video2jpeg() {

        String inputFile = FileUtils.getSDPath() +"/" + "asset.mp4";
        Log.d(TAG, "init: fileName = " + inputFile);

        inputFile0 = FileUtils.getSDPath() +"/" + "asset0.mp4";
        Log.d(TAG, "init: fileName = " + inputFile0);

        inputFile1 = FileUtils.getSDPath() +"/" + "asset1.mp4";
        Log.d(TAG, "init: fileName = " + inputFile1);

        //设置jpeg文件目录名
        outputDirasset0 = FileUtils.getSDPath() +"/" + "asset0";
        outputDirasset1 = FileUtils.getSDPath() +"/" + "asset1";

        //创建文件夹
        FileUtils.createDir(outputDirasset0);
        FileUtils.createDir(outputDirasset1);
        Log.d(TAG, "init: Dir = " + outputDirasset0);
        Log.d(TAG, "init: Dir = " + outputDirasset1);

        //设置输出格式
        outputImageFormat = OutputImageFormat.values()[2];
        Log.d(TAG, "onCreate: outputImageFormat: " + outputImageFormat.toString());

        VideoToFrames videoToFrames = new VideoToFrames();
        try {
            videoToFrames.setSaveFrames(outputDirasset0, outputImageFormat);
            //updateInfo("运行中...");
            videoToFrames.decode(inputFile0);
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.back:
                Log.d(TAG, "onClick: back");

                break;
            case R.id.next:
                Log.d(TAG, "onClick: next");
                String inputFile = FileUtils.getSDPath() +"/" + "asset.mp4";
                initdecoder(inputFile);
                //startActivity(new Intent(MainActivity.this, VideoEditorActivity.class));
                break;
        }
    }

    /**检查权限*/
    private void checkPermission() {
        if (!Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED)) {
            return;
        }

        int hasWriteContactsPermission = ContextCompat.checkSelfPermission(getApplication(), Manifest.permission.INTERNET);
        if (hasWriteContactsPermission == PackageManager.PERMISSION_DENIED) {
            ActivityCompat.requestPermissions(MainActivity.this,
                    new String[]{Manifest.permission.INTERNET}, 1);
        }

        hasWriteContactsPermission = ContextCompat.checkSelfPermission(getApplication(), Manifest.permission.WRITE_EXTERNAL_STORAGE);
        if (hasWriteContactsPermission == PackageManager.PERMISSION_DENIED) {
            ActivityCompat.requestPermissions(MainActivity.this,
                    new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, 1);
        }

        hasWriteContactsPermission = ContextCompat.checkSelfPermission(getApplication(), Manifest.permission.READ_EXTERNAL_STORAGE);
        if (hasWriteContactsPermission == PackageManager.PERMISSION_DENIED) {
            ActivityCompat.requestPermissions(MainActivity.this,
                    new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, 1);
        }
    }


    /**
     * A native method that is implemented by the 'native-lib' native library,
     * which is packaged with this application.
     */

    public native String initdecoder(String path);
}
