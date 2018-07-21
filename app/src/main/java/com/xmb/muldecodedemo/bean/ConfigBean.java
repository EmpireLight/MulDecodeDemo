package com.xmb.muldecodedemo.bean;

import java.io.Serializable;
import java.util.ArrayList;

/**
 * Created by Administrator on 2017/12/26 0026.
 */

public class ConfigBean implements Serializable{

    public ArrayList<Layers> layers;
    public ArrayList<Assets> assets;
    public int[] size;
    public int duration;

    public static class Layers implements Serializable{
        public String data;
    }

    public static class Assets implements Serializable{
        //视频文件用这个名字
        public String name;
        public int type;
        //需要用的头名字
        public String uiData;
        //资源文件用这个文件名字
        public String uiFile;
        //1为替换资源    0为视频
        public int group;
    }

}
