package com.demo.hdz.mediacodecdemo;

import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.MediaMuxer;
import android.media.projection.MediaProjection;


/**
 * Created by hdz on 2018/4/4.
 */

public class ScreenRecordThread extends Thread {
    private int m_iBitRate = 6000000;     //比特率
    private int m_iFrameRate = 60;  //帧率
    private final int I_FRAME_INTERVAL = 10;//关键帧

    private int m_iScreenWidth   = 0; //屏幕宽度
    private int m_iScreenHeight  = 0; //屏幕高度
    private int m_iScreenDensity = 0; //屏幕像素密度

    private MediaProjection m_mediaProjection = null;
    private String m_sFilePath = "";

    private VirtualDisplay m_virtualDisplay = null;
    private MediaMuxer m_mediaMuxer = null;
    private AVCodec m_avCodec = null;

    public ScreenRecordThread(int width, int height, int density, MediaProjection mediaProjection, String sFilePath) {
        m_iScreenWidth = width;
        m_iScreenHeight = height;
        m_iScreenDensity = density;
        m_sFilePath = sFilePath;
        m_mediaProjection = mediaProjection;

        m_iBitRate = width * height * m_iFrameRate;
    }

    @Override
    public void run() {
        super.run();
        try {
            m_avCodec = new AVCodec();

            //创建MediaMuxer对象
            //第一个参数是输出的地址；第二个参数是输出的格式，这里设置的是MP4格式
            m_mediaMuxer = new MediaMuxer(m_sFilePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);

            if (!m_avCodec.initEncode(m_iScreenWidth, m_iScreenHeight, m_iBitRate, m_iFrameRate, I_FRAME_INTERVAL, m_mediaMuxer)) {
                return;
            }

            //创建VirtualDisplay，把MediaCodec的surface传进去
            m_virtualDisplay = m_mediaProjection.createVirtualDisplay("V", m_iScreenWidth, m_iScreenHeight, m_iScreenDensity, DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC, m_avCodec.getSurface(), null, null);
            m_avCodec.startRecord();
            m_avCodec.recording();
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            release();
        }
    }

    public void release() {
        if (m_virtualDisplay != null) {
            m_virtualDisplay.release();
            m_virtualDisplay = null;
        }
        if (m_mediaProjection !=null) {
            m_mediaProjection.stop();
            m_mediaProjection = null;
        }
        if (m_mediaMuxer != null) {
            m_mediaMuxer.stop();
            m_mediaMuxer.release();
            m_mediaMuxer = null;
        }
        if (m_avCodec != null) {

        }
    }
}
