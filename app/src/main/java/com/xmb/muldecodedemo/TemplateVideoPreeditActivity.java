package com.xmb.muldecodedemo;

import android.app.Activity;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.VideoView;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.xmb.muldecodedemo.bean.ConfigBean;
import com.xmb.muldecodedemo.utils.FileUtils;
import com.xmb.muldecodedemo.wiget.DoubleSlideSeekBar;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Type;

/**
 * Created by Administrator on 2018/7/21 0021.
 */

public class TemplateVideoPreeditActivity extends Activity implements View.OnClickListener {
    private final static String TAG = "TempVideoPreedit";

    private DoubleSlideSeekBar mDoubleslideWithoutrule;

    private VideoView videoView;
    private ImageView back;
    private TextView next;
    private ImageView iconPlay;

    String assetMP4 = FileUtils.getSDPath() + File.separator + "asset.mp4";
    String asset1 = FileUtils.getSDPath() + File.separator + "asset1.mp4";
    String config = FileUtils.getSDPath() + File.separator + "config.json";
    private Gson gson;

    ConfigBean configBean;

    private int startTime;
    private int endTime;

    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.e(TAG, "onCreate: seekbar1");
        setContentView(R.layout.activity_template_preedit);

        initData();
        initView();
    }

    private void initView() {
        videoView = (VideoView) findViewById(R.id.vv_videoview);
        mDoubleslideWithoutrule = (DoubleSlideSeekBar) findViewById(R.id.doubleslide_withoutrule);
        back = (ImageView) findViewById(R.id.template_preedit_back);
        next = (TextView) findViewById(R.id.template_preedit_next);
        iconPlay = (ImageView) findViewById(R.id.icon_video_play);
        iconPlay.setVisibility(View.INVISIBLE);

        back.setOnClickListener(this);
        next.setOnClickListener(this);
        videoView.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {

                if(videoView.isPlaying()) {
                    videoView.pause();
                    iconPlay.setVisibility(View.VISIBLE);
                } else {
                    videoView.start();
                    iconPlay.setVisibility(View.INVISIBLE);
                }

                return false;
            }
        });

        mDoubleslideWithoutrule.setOnRangeListener(new DoubleSlideSeekBar.onRangeListener() {
            @Override
            public void onRange(float low, float big) {
                startTime = (int)(low*1000);
                endTime = (int)(big*1000);
                videoView.seekTo(startTime);
                Log.e(TAG, "onRange: startTime = " + startTime );
                Log.e(TAG, "onRange: endTime = " + endTime );
            }
        });

        Log.i(TAG, " 获取视频文件地址");
        File file = new File(assetMP4);//asset1   assetMP4
        if (!file.exists()) {
            Log.e(TAG, "play: VideoFile is wrong");
            return;
        }

        Log.i(TAG, "指定视频源路径");
        videoView.setVideoPath(assetMP4);

        videoView.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
            @Override
            public void onPrepared(MediaPlayer mp) {
                startTime = 0;
                endTime = videoView.getDuration();
                mDoubleslideWithoutrule.setRange(0.0f, endTime/1000);

                videoView.start();

                new Thread() {
                    @Override
                    public void run() {
                        try {
                            while(videoView.isPlaying()) {
                                // 如果正在播放，每100毫秒查看是否达到播放endtime
                                int current = videoView.getCurrentPosition();
                                if (current >= endTime) {
                                    videoView.seekTo(startTime);
                                    Log.e(TAG, "run: current " + current );
                                    Log.e(TAG, "run: endTime " + endTime );
                                    Log.e(TAG, "run: seekTo  startTime " + startTime );
                                }

                                sleep(100);
                            }
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                        Log.e(TAG, "run: end");
                    }
                }.start();
            }
        });

        videoView.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
            @Override
            public void onCompletion(MediaPlayer mp) {
                videoView.seekTo(0);
                videoView.start();
            }
        });

        videoView.setOnErrorListener(new MediaPlayer.OnErrorListener() {
            @Override
            public boolean onError(MediaPlayer mp, int what, int extra) {
                // 发生错误重新播放
                Log.e(TAG, "onError: ");
                return false;
            }
        });
    }

    private void initData() {
        gson = new Gson();
        configBean = getConfigBean();
        Log.e(TAG, "initData: size[0] = " + configBean.size[0] );
        Log.e(TAG, "initData: size[1] = " + configBean.size[1] );
        Log.e(TAG, "initData: configBean.duration = " + configBean.duration);
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.template_preedit_next:
                Log.e(TAG, "onClick: template_preedit_next");
                String startMS = convertSecondsToTime(startTime);
                String endMS = convertSecondsToTime(endTime);
                Log.e(TAG, "onClick: startMS " + startMS );
                Log.e(TAG, "onClick: endMS " + endMS );
                break;

            case R.id.template_preedit_back:
                Log.e(TAG, "onClick: template_preedit_back");
                this.finish();
                break;
        }
    }

    private ConfigBean getConfigBean() {
        Type configType = new TypeToken<ConfigBean>() {
        }.getType();
        return gson.fromJson(getJson(config), configType);
    }

    /**
     * 将json文件中的文件转换成字符串
     *
     * @param path
     * @return
     */
    private String getJson(String path) {
        File file = new File(path);

        InputStream stream = null;
        try {
            stream = new FileInputStream(file);
            BufferedReader reader = new BufferedReader(new InputStreamReader(stream, "utf-8"));
            StringBuilder sb = new StringBuilder();
            String line = null;
            while ((line = reader.readLine()) != null) {
                sb.append(line + "\n");
            }
            stream.close();
            reader.close();
            return sb.toString();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return "";
    }

    private String convertSecondsToTime(int ms) {
        String timeStr = null;
        int hour = 0;
        int minutes = 0;
        int minute = 0;
        int second = 0;
        int seconds = ms / 1000;
        minutes = seconds / 60;

        second = seconds % 60;
        minute = minutes % 60;
        hour = minutes / 60;

        if (seconds <= 0) {
            return "00:00:00";
        } else {
            if (hour > 99) return "99:59:59";

            timeStr = unitFormat(hour) + ":" + unitFormat(minute) + ":" + unitFormat(second);
        }
        return timeStr;
    }

    private String unitFormat(int i) {
        String retStr = null;
        if (i >= 0 && i < 10) {
            retStr = "0" + Integer.toString(i);
        } else {
            retStr = "" + i;
        }
        return retStr;
    }
}
