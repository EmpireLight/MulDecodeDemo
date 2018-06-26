package com.xmb.muldecodedemo.utils;

import android.os.Environment;

import java.io.File;

/**
 * Created by Administrator on 2018/6/26 0026.
 */

public class FileUtils {
    public static String getSDPath(){
        File sdDir = null;
        boolean sdCardExist = Environment.getExternalStorageState()
                .equals(android.os.Environment.MEDIA_MOUNTED);//判断sd卡是否存在
        if(sdCardExist)
        {
            sdDir = Environment.getExternalStorageDirectory();//获取跟目录
        }
        return sdDir.toString();
    }

    public static void createDir(String outputDir) {
        //新建一个File，传入文件夹目录
        File file = new File(outputDir);
        file = new File(outputDir);
        //判断文件夹是否存在，如果不存在就创建，否则不创建
        if (!file.exists()) {
            //通过file的mkdirs()方法创建
            file.mkdir();
        } else {
            deleteAllFiles(file);
        }
    }

    public static void deleteAllFiles(File root) {
        File files[] = root.listFiles();
        if (files != null) {
            for (File f : files) {
                if (f.isDirectory()) { // 判断是否为文件夹
                    deleteAllFiles(f);
                    try {
                        f.delete();
                    } catch (Exception e) {
                    }
                } else {
                    if (f.exists()) { // 判断是否存在
                        deleteAllFiles(f);
                        try {
                            f.delete();
                        } catch (Exception e) {
                        }
                    }
                }
            }
        }
    }

}
