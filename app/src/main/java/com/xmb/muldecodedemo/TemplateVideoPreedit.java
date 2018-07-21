package com.xmb.muldecodedemo;

import android.app.Activity;
import android.content.pm.ActivityInfo;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.annotation.Nullable;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.widget.Button;
import android.widget.SeekBar;

import com.xmb.muldecodedemo.utils.FileUtils;

/**
 * Created by Administrator on 2018/7/21 0021.
 */

public class TemplateVideoPreedit extends Activity implements View.OnClickListener {
    private final static String TAG = "TemplateVideoPreedit";

    private SurfaceView surfaceView;
    private Button btnPause, btnPlayUrl, btnStop;
    private SeekBar skbProgress;
    private Player player;

    String assetMP4 = FileUtils.getSDPath() + "/" + "asset.mp4";

    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_template_preedit);

        surfaceView = (SurfaceView) this.findViewById(R.id.surfaceView1);

        btnPlayUrl = (Button) this.findViewById(R.id.btnPlay);
        btnPlayUrl.setOnClickListener(this);

        btnPause = (Button) this.findViewById(R.id.btnPause);
        btnPause.setOnClickListener(this);

        btnStop = (Button) this.findViewById(R.id.btnStop);
        btnStop.setOnClickListener(this);

        skbProgress = (SeekBar) this.findViewById(R.id.skbProgress);
        skbProgress.setOnSeekBarChangeListener(new SeekBarChangeEvent());
        player = new Player(surfaceView, skbProgress);
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.btnPlay:
                player.playUrl(assetMP4);
                break;
            case R.id.btnPause:
                player.pause();
                break;
            case R.id.btnStop:
                player.stop();
                break;
        }
    }

    class SeekBarChangeEvent implements SeekBar.OnSeekBarChangeListener {
        int progress;

        @Override
        public void onProgressChanged(SeekBar seekBar, int progress,
                                      boolean fromUser) {
//             原本是(progress/seekBar.getMax())*player.mediaPlayer.getDuration()
            this.progress = progress * player.mediaPlayer.getDuration()
                    / seekBar.getMax();
        }

        @Override
        public void onStartTrackingTouch(SeekBar seekBar) {

        }

        @Override
        public void onStopTrackingTouch(SeekBar seekBar) {
            // seekTo()的参数是相对与影片时间的数字，而不是与seekBar.getMax()相对的数字
            player.mediaPlayer.seekTo(progress);
        }
    }

}
