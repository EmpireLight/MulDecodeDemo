package com.xmb.muldecodedemo;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Environment;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

public class MainActivity extends AppCompatActivity implements View.OnClickListener{
    private static final String TAG = "MainActivity";

    ImageView Img_back;
    TextView Tex_next;

    VideoEditorView videoEditorView;

    // Used to load the 'native-lib' library on application startup.
    static {
        System.loadLibrary("decoder");
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Img_back = (ImageView) findViewById(R.id.main_back);
        Img_back.setOnClickListener(this);

        Tex_next = (TextView) findViewById(R.id.main_next);
        Tex_next.setOnClickListener(this);

//        videoEditorView = (VideoEditorView) findViewById(R.id.main_VideoEditorView);

        checkPermission();
    }

    @Override
    public void onClick(View v) {
        Intent intent = new Intent();
        switch (v.getId()) {
            case R.id.main_back:
                Log.e(TAG, "onClick: back");
                intent.setClass(MainActivity.this, TemplateVideoPreeditActivity.class);
                startActivity(intent);
                break;
            case R.id.main_next:
                intent = new Intent();
                intent.setClass(MainActivity.this, TemplateComposeActivity.class);
                startActivity(intent);

                Log.d(TAG, "onClick: next");
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
}
