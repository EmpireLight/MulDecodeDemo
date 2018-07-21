package com.xmb.muldecodedemo.wiget;


import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import com.xmb.muldecodedemo.R;

public class WaitDialog extends Dialog implements
        View.OnClickListener {

    private Context context;

    private RoundProgressBar progressBar;

    private OnCancelNextListener listener;

    private TextView hint;

    private String hintStr;

    private boolean isShowCancel = true;
    private ImageView cancel;

    public void setShowCancel(boolean isShowCancel){
        this.isShowCancel = isShowCancel;
    }

    public WaitDialog(Context context, OnCancelNextListener listener, String hintStr) {
        super(context, R.style.details_dialog);
        setCanceledOnTouchOutside(false);
        setCancelable(false);
        this.context = context;

        this.listener = listener;

        this.hintStr = hintStr;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.wait_dialog);
        getWindow().setBackgroundDrawableResource(R.color.c500000);

        progressBar = (RoundProgressBar) findViewById(R.id.progressBar);

        hint = (TextView) findViewById(R.id.hint);

        cancel = findViewById(R.id.cancel);
        cancel.setOnClickListener(this);
    }

    public void setProgressBar(int position) {
        progressBar.setProgress(position);
    }

    @Override
    public void show() {
        super.show();
        setProgressBar(0);
        hint.setText(hintStr);
        if(!isShowCancel){
            cancel.setVisibility(View.GONE);
        }
    }

    @Override
    public void cancel() {
        super.cancel();
        setProgressBar(0);
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.cancel:
                listener.onCancel();
                break;
        }
    }

    public interface OnCancelNextListener{
        public void onCancel();
    }
}
