package com.demo.hdz.mediacodecdemo;

import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.media.MediaRecorder;
import android.os.Build;
import android.os.Process;
import android.util.Log;
import android.view.Surface;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Created by hdz on 2018/4/1.
 */

public class AVCodec {
    private final String TAG = "AVCodec";

    private final String MIME_TYPE = "video/avc"; //H264的MIME类型
    private static final int FRAME_RATE = 60;

    private MediaCodec m_mediaCodec = null;
    private Surface mSurface = null;
    private Lock m_lock = new ReentrantLock();

    private int m_validFrame = 0;
    private int frame_cnt = 0;
    private final int TIMEOUT_US = 10000;

    private MediaCodec.BufferInfo m_bufferInfo = new MediaCodec.BufferInfo();
    private AtomicBoolean m_quit = new AtomicBoolean(false);
    private int m_videoTrackIndex = -1;
    private int mAudioTrackIndex = -1;
    private boolean m_bMuxerStarted = false;
    private MediaMuxer m_mediaMuxer = null;

//    public boolean initDecode(int width, int height, Surface surface) {
//        //创建解码器
//        try {
//            if (m_mediaCodec == null) {
//                m_mediaCodec = MediaCodec.createDecoderByType(MIME_TYPE);
//                if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
//                    Logger.d("mediaCodec name: " + m_mediaCodec.getCodecInfo().getName());
//                }
//            }
//        } catch (Exception e) {
//            e.printStackTrace();
//            return false;
//        }
//
//        m_mediaCodec.stop();
//
//        MediaFormat format = MediaFormat.createVideoFormat(MIME_TYPE, width, height);
//        format.setInteger(MediaFormat.KEY_FRAME_RATE, FRAME_RATE);
//        format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1); //关键帧间隔时间 单位s
//        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
//            format.setInteger(MediaFormat.KEY_ROTATION, 90); //图像旋转90度
//        }
//
//        if (!surface.isValid()) {
//            Logger.e(TAG, "Surface is invalid!" + surface);
//            return false;
//        }
//
//        //配置解码器
//        try {
//            m_mediaCodec.configure(format, surface, null, 0);
//        } catch (Exception e) {
//            e.printStackTrace();
//            return false;
//        }
//
//        m_mediaCodec.start();
//
//        return true;
//    }

    public boolean initEncode(int width, int height, int bit_rate, int frame_rate, int r_frame_interval, MediaMuxer mediaMuxer) {
        m_mediaMuxer = mediaMuxer;

        MediaFormat format = MediaFormat.createVideoFormat(MIME_TYPE, width, height);
        //COLOR_FormatSurface这里表明数据将是一个graphicbuffer元数据
        format.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
        //设置码率，码率越大视频越清晰，相应的占用内存也要更大
        format.setInteger(MediaFormat.KEY_BIT_RATE, bit_rate);
        //设置帧率，通常这个值越高，视频会显得越流畅，一般默认设置成30，最低可以设置成24，不要低于这个值，低于24会明显卡顿
        format.setInteger(MediaFormat.KEY_FRAME_RATE, frame_rate);
        //设置两个关键帧的间隔
        format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, r_frame_interval);

        try {
            //创建编码器
            m_mediaCodec = MediaCodec.createEncoderByType(MIME_TYPE);

            m_mediaCodec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);

            //这一步非常关键，它设置的是MediaCodec的编码源，也就是说，要告诉m_Encoder解码哪些流。
            //很出乎大家的意料，MediaCodec并没有要求我们传一个流文件进去，而是要求我们指定一个surface
            //而这个surface，其实就是MediaProjection中用来展示屏幕采集数据的surface。获取MediaCodec的surface，
            //这个surface其实就是个入口，屏幕作为输入源就会进入这个入口，然后交给MediaCodec编码
            mSurface = m_mediaCodec.createInputSurface();

            m_mediaCodec.start();
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
        return true;
    }
    public Surface getSurface() {
        return mSurface;
    }


//    public void decodeFile(InputStream inputStream) {
//        try {
//            if (inputStream != null) {
//                while (true) {
//                    try {
//                        byte[] headData = new byte[4];
//                        int ret = inputStream.read(headData, 0, headData.length);
//                        if (ret == headData.length) {
//                            ByteBuffer byteBuffer = ByteBuffer.wrap(Arrays.copyOf(headData, 4));
//                            byteBuffer.order(ByteOrder.LITTLE_ENDIAN);
//
//                            int dataLen = byteBuffer.getInt(0);
//                            Logger.d("AvcDecoder, dataLen: " + dataLen);
//
//                            byte[] inData = new byte[dataLen];
//                            ret = inputStream.read(inData, 0, inData.length);
//                            if (ret == inData.length) {
//                                decode(inData);
//                            } else {
//                                inputStream.close();
//                                break;
//                            }
//                        } else {
//                            inputStream.close();
//                            break;
//                        }
//                    } catch (IOException e) {
//                        e.printStackTrace();
//                    }
//                }
//                decode(null);
//            }
//        } catch (Exception e) {
//            e.printStackTrace();
//        }
//    }

    //format naul + data:  00 00 00 01 xxxxxx
//    public void decode(byte[] input) {
//        long start_time = System.currentTimeMillis();
//
//        m_lock.lock();
//
//        try {
//            boolean inputSuccess = false;
//            while (!inputSuccess) {
//
//                ByteBuffer[] inputBuffers = m_mediaCodec.getInputBuffers();   //输入缓冲区
//
//                int inputBufferIndex = m_mediaCodec.dequeueInputBuffer(-1); //第一帧的帧索引
//                Logger.d("Frame index of the first frame: " + inputBufferIndex);
//
//                if (inputBufferIndex >= 0) {
//                    ByteBuffer inputBuffer = inputBuffers[inputBufferIndex];
//                    inputBuffer.clear();
//                    if (input != null) {
//                        inputBuffer.put(input);
//                        m_mediaCodec.queueInputBuffer(inputBufferIndex, 0, input.length, frame_cnt++ * 1000 * 1000 / FRAME_RATE, 0);
//                        Logger.d("input decode frame" + frame_cnt);
//                    } else {
//                        m_mediaCodec.queueInputBuffer(inputBufferIndex, 0, 0, frame_cnt++ * 1000 * 1000 / FRAME_RATE, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
//                    }
//                    inputSuccess = true;
//                }
//
//                MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
//                int outputBufferIndex = m_mediaCodec.dequeueOutputBuffer(bufferInfo, 1000 * 100);
//
//                do {
//                    if (outputBufferIndex == MediaCodec.INFO_TRY_AGAIN_LATER) {
//                        Logger.d("INFO_TRY_AGAIN_LATER");
//                    } else if (outputBufferIndex == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
//                        //encodeOutputBuffers = mDecodeMediaCodec.getOutputBuffers();
//                        Logger.d("INFO_OUTPUT_BUFFERS_CHANGED");
//                    } else if (outputBufferIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
//                        MediaFormat format = m_mediaCodec.getOutputFormat();
//                        Logger.d("onOutputFormatChanged: " + format.getInteger(MediaFormat.KEY_COLOR_FORMAT)
//                                + "size: " + format.getInteger(MediaFormat.KEY_WIDTH) + "x" + format.getInteger(MediaFormat.KEY_HEIGHT));
//                        //mediaformat changed
//                    } else if (outputBufferIndex < 0) {
//                        //unexpected result from encoder.dequeueOutputBuffer
//                        Logger.d("outputBufferIndex < 0");
//                    } else {
//                        m_mediaCodec.releaseOutputBuffer(outputBufferIndex, true);
//                        outputBufferIndex = m_mediaCodec.dequeueOutputBuffer(bufferInfo, 0);
//
//                        m_validFrame++;
//                        Logger.d("output decode frame: " + m_validFrame);
//                    }
//                } while (outputBufferIndex >= 0);
//            }
//
//        } catch (Throwable t) {
//            t.printStackTrace();
//        }
//        m_lock.unlock();
//
//        Logger.d("decode a frame use time: " + (System.currentTimeMillis() - start_time) + "ms");
//    }

    public void close() {
        if (m_mediaCodec != null) {
            try {
                m_mediaCodec.stop();
                m_mediaCodec.release();
                m_mediaCodec = null;
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        m_quit.set(true);
        stop();
    }



    public void recording() {
        while (!m_quit.get()) {
            //dequeueOutputBuffer方法可以这么理解：
            //它会出列一个输出buffer(可以理解为一帧画面)，
            //返回值是这一帧画面的顺序位置(类似于数组的下标)
            //第二个参数是超时时间，如果超过了这个时间还没成功出列，
            //那么就会跳过这一帧，去出列下一帧，并返回NFO_TRY_AGAIN_LATER标志位
            int index = m_mediaCodec.dequeueOutputBuffer(m_bufferInfo, TIMEOUT_US);

            //当格式改变的时候需要重新设置格式，第一次开始的时候会返回这个值
            if (index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                //resetOutputFormat();
                if (m_bMuxerStarted) {
                    return;
                }
                MediaFormat newFormat = m_mediaCodec.getOutputFormat();
                addTrack(newFormat,true);
                m_bMuxerStarted = true;
            } else if (index == MediaCodec.INFO_TRY_AGAIN_LATER) {
                try {
                    Thread.sleep(10);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            } else if (index > 0) { //到这里说明dequeueOutputBuffer执行正常
                if (!m_bMuxerStarted) {
                    continue;
                }
                //转换成mp4
                //通过index获取到ByteBuffer(可以理解为一帧)
                ByteBuffer encodeData = m_mediaCodec.getOutputBuffer(index);
                pumpStream(encodeData,m_bufferInfo,true);

                ////释放缓存的资源
                m_mediaCodec.releaseOutputBuffer(index, false);
            }
        }
    }

    public synchronized void addTrack(MediaFormat format, boolean isVideo) {
        // now that we have the Magic Goodies, start the muxer
        if (mAudioTrackIndex != -1 && m_videoTrackIndex != -1)
            throw new RuntimeException("already add all tracks");

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
            // 添加音频/视频的通道，此操作必须在mMuxer start方法之前调用:
            int track = m_mediaMuxer.addTrack(format);

            Log.i(TAG, String.format("addTrack %s result %d", isVideo ? "video" : "audio", track));


            if (isVideo) { //
                //mVideoFormat = format;
                m_videoTrackIndex = track;
                if (mAudioTrackIndex != -1) {
                    Log.i(TAG, "both audio and video added,and muxer is started");
                    m_mediaMuxer.start();
                    //mBeginMillis = System.currentTimeMillis();
                }
            } else {
//                mAudioFormat = format;
                mAudioTrackIndex = track;
                if (m_videoTrackIndex != -1) {
                    m_mediaMuxer.start();
//                    mBeginMillis = System.currentTimeMillis();
                }
            }
        }
    }

    public synchronized void pumpStream(ByteBuffer outputBuffer, MediaCodec.BufferInfo bufferInfo, boolean isVideo) {
        if (mAudioTrackIndex == -1 || m_videoTrackIndex == -1) {
            Log.i(TAG, String.format("pumpStream [%s] but muxer is not start.ignore..", isVideo ? "video" : "audio"));
            return;
        }

        if ((bufferInfo.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
            // The codec config data was pulled out and fed to the muxer when we got
            // the INFO_OUTPUT_FORMAT_CHANGED status.  Ignore it.
            bufferInfo.size = 0;
        } else if (bufferInfo.size != 0) {
            if (isVideo && m_videoTrackIndex == -1) {
                throw new RuntimeException("muxer hasn't started");
            }

            // adjust the ByteBuffer values to match BufferInfo (not needed?)
            outputBuffer.position(bufferInfo.offset);
            outputBuffer.limit(bufferInfo.offset + bufferInfo.size);

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
                /*
                 * 将H.264和AAC数据分别同时写入到MP4文件
                 *   BufferInfo对象的值一定要设置正确：
                 *       info.size 必须填入数据的大小
                 *       info.flags 需要给出是否为同步帧/关键帧
                 *       info.presentationTimeUs 必须给出正确的时间戳，注意单位是 us
                 * */
                if (m_mediaMuxer!=null){
                    m_mediaMuxer.writeSampleData(isVideo ? m_videoTrackIndex : mAudioTrackIndex, outputBuffer, bufferInfo);
                }

            }

            Log.d(TAG, String.format("sent %s [" + bufferInfo.size + "] with timestamp:[%d] to muxer", isVideo ? "video" : "audio", bufferInfo.presentationTimeUs / 1000));
        }

        if ((bufferInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
            Log.i(TAG, "BUFFER_FLAG_END_OF_STREAM received");
        }

//        if (System.currentTimeMillis() - mBeginMillis >= durationMillis) {
//            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
//                Log.i(TAG, String.format("record file reach expiration.create new file:" + index));
//
//                // 结束
//                mMuxer.stop();
//                mMuxer.release();
//                mMuxer = null;
//                mVideoTrackIndex = mAudioTrackIndex = -1;
//
//                try {
//                    mMuxer = new MediaMuxer(mFilePath + "-" + ++index + ".mp4", MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
//                    addTrack(mVideoFormat, true);
//                    addTrack(mAudioFormat, false);
//                } catch (IOException e) {
//                    e.printStackTrace();
//                }
//            }
//        }
    }




    /**
     * 编码
     */

    private Thread mThread = null;
    private int samplingRate = 8000;
    private int bitRate = 16000;
    private int BUFFER_SIZE = 1920;

    AudioRecord mAudioRecord;   // 底层的音频采集
    MediaCodec mMediaCodec;     // 音频硬编码器
    private Thread mWriter;
    protected MediaCodec.BufferInfo mBufferInfo = new MediaCodec.BufferInfo();
    protected ByteBuffer[] outputBuffers = null; // 编码后的数据
   // private MediaFormat newFormat;

    public void startRecord() {
        if (mThread != null)
            return;

        /**
         * 3、开启一个子线程，不断从AudioRecord的缓冲区将音频数据读出来。
         * 注意，这个过程一定要及时，否则就会出现“overrun”的错误，
         * 该错误在音频开发中比较常见，意味着应用层没有及时地取走音频数据，导致内部的音频缓冲区溢出。
         * */
        mThread = new Thread(new Runnable() {
            @Override
            public void run() {
                Process.setThreadPriority(Process.THREAD_PRIORITY_AUDIO);
                int len, bufferIndex;

                try {
                    // 计算bufferSizeInBytes：int size = 采样率 x 位宽 x 通道数
                    int bufferSize = AudioRecord.getMinBufferSize(samplingRate,
                            AudioFormat.CHANNEL_IN_MONO,
                            AudioFormat.ENCODING_PCM_16BIT);

                    /*
                     * 1、配置参数，初始化AudioRecord构造函数
                     * audioSource：音频采集的输入源，DEFAULT（默认），VOICE_RECOGNITION（用于语音识别，等同于DEFAULT），MIC（由手机麦克风输入），VOICE_COMMUNICATION（用于VoIP应用）等等
                     * sampleRateInHz：采样率，注意，目前44.1kHz是唯一可以保证兼容所有Android手机的采样率。
                     * channelConfig：通道数的配置，CHANNEL_IN_MONO（单通道），CHANNEL_IN_STEREO（双通道）
                     * audioFormat：配置“数据位宽”的,ENCODING_PCM_16BIT（16bit），ENCODING_PCM_8BIT（8bit）
                     * bufferSizeInBytes：配置的是 AudioRecord 内部的音频缓冲区的大小，该缓冲区的值不能低于一帧“音频帧”（Frame）的大小
                     * */
                    mAudioRecord = new AudioRecord(MediaRecorder.AudioSource.MIC,
                            samplingRate,
                            AudioFormat.CHANNEL_IN_MONO,
                            AudioFormat.ENCODING_PCM_16BIT,
                            bufferSize);

                    /*
                     * mp3为audio/mpeg, aac为audio/mp4a-latm, mp4为video/mp4v-es
                     * */
                    String encodeType = "audio/mp4a-latm";

                    // 初始化编码器
                    mMediaCodec = MediaCodec.createEncoderByType(encodeType);

                    MediaFormat format = new MediaFormat();
                    format.setString(MediaFormat.KEY_MIME, encodeType);
                    format.setInteger(MediaFormat.KEY_BIT_RATE, bitRate);// 比特率
                    format.setInteger(MediaFormat.KEY_CHANNEL_COUNT, 1);// 声道数
                    format.setInteger(MediaFormat.KEY_SAMPLE_RATE, samplingRate);// 采样率
                    format.setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC);
                    format.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, BUFFER_SIZE); // 作用于inputBuffer的大小

                    mMediaCodec.configure(format,
                            null,
                            null,
                            MediaCodec.CONFIGURE_FLAG_ENCODE);

                    mMediaCodec.start();

                    mWriter = new WriterThread();
                    mWriter.start();

                    // 2、开始采集
                    mAudioRecord.startRecording();

                    // 获取编码器的输入缓存inputBuffers
                    final ByteBuffer[] inputBuffers = mMediaCodec.getInputBuffers();

                    long presentationTimeUs = 0;

                    while (mThread != null) {
                        // 从input缓冲区队列申请empty buffer
                        bufferIndex = mMediaCodec.dequeueInputBuffer(1000);

                        if (bufferIndex >= 0) {
                            inputBuffers[bufferIndex].clear();

                            /*
                             * 不断的读取采集到的声音数据，放进编码器的输入缓存inputBuffers中 进行编码
                             *   audioBuffer 存储写入音频录制数据的缓冲区。
                             *   sizeInBytes 请求的最大字节数。
                             * public int read (ByteBuffer audioBuffer, int sizeInBytes)
                             *  */
                            len = mAudioRecord.read(inputBuffers[bufferIndex], BUFFER_SIZE);

                            long timeUs = System.nanoTime() / 1000;
                            Log.i(TAG, String.format("audio: %d [%d] ", timeUs, timeUs - presentationTimeUs));
                            presentationTimeUs = timeUs;

                            // 将要编解码的数据拷贝到empty buffer，然后放入input缓冲区队列
                            if (len == AudioRecord.ERROR_INVALID_OPERATION || len == AudioRecord.ERROR_BAD_VALUE) {
                                mMediaCodec.queueInputBuffer(bufferIndex, 0, 0, presentationTimeUs, 0);
                            } else {
                                mMediaCodec.queueInputBuffer(bufferIndex, 0, len, presentationTimeUs, 0);
                            }
                        }
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Record___Error!!!!!");
                    e.printStackTrace();
                } finally {
                    Thread t = mWriter;
                    mWriter = null;

                    while (t != null && t.isAlive()) {
                        try {
                            t.interrupt();
                            t.join();
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }

                    // 4、停止采集，释放资源。
                    if (mAudioRecord != null) {
                        mAudioRecord.stop();
                        mAudioRecord.release();
                        mAudioRecord = null;
                    }

                    // 停止编码
                    if (mMediaCodec != null) {
                        mMediaCodec.stop();
                        mMediaCodec.release();
                        mMediaCodec = null;
                    }
                }
            }
        }, "AACRecoder");

        mThread.start();
//        if (enableAudio) {
//
//        }
    }




    /**
     * 不断的从输出缓存中取出编码后的数据，然后push出去
     * */
    private class WriterThread extends Thread {
        public WriterThread() {
            super("WriteAudio");
        }

        @Override
        public void run() {
            int index;

            if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.LOLLIPOP) {
                // 获取编码器的输出缓存outputBuffers
                outputBuffers = mMediaCodec.getOutputBuffers();
            }

            ByteBuffer mBuffer = ByteBuffer.allocate(10240);

            do {
                /*
                 * 从output缓冲区队列申请编解码的buffer
                 * BufferInfo：用于存储ByteBuffer的信息
                 * TIMES_OUT：超时时间（在一个单独的线程专门取输出数据，为了避免CPU资源的浪费，需设置合适的值）
                 * */
                index = mMediaCodec.dequeueOutputBuffer(mBufferInfo, 10000);

                if (index >= 0) {
                    if (mBufferInfo.flags == MediaCodec.BUFFER_FLAG_CODEC_CONFIG) {
                        continue;
                    }

                    mBuffer.clear();
                    ByteBuffer outputBuffer;

                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
                        outputBuffer = mMediaCodec.getOutputBuffer(index);
                    } else {
                        outputBuffer = outputBuffers[index];
                    }

                    // 合成音频
//                    if (muxer != null)
//                        muxer.pumpStream(outputBuffer, mBufferInfo, false);

                    pumpStream(outputBuffer, mBufferInfo, false);
//                    outputBuffer.get(mBuffer.array(), 7, mBufferInfo.size);
//                    outputBuffer.clear();
//
//                    mBuffer.position(7 + mBufferInfo.size);
//                    addADTStoPacket(mBuffer.array(), mBufferInfo.size + 7);
//                    mBuffer.flip();
//                    Collection<Pusher> p;

//                    synchronized (AudioStream.this) {
//                        p = sets;
//                    }
//
//                    Iterator<Pusher> it = p.iterator();
//
//                    // 推流
//                    while (it.hasNext()) {
//                        Pusher ps = it.next();
//                        ps.push(mBuffer.array(),
//                                0,
//                                mBufferInfo.size + 7,
//                                0,// mBufferInfo.presentationTimeUs / 1000,
//                                0);
//                    }

                    // 处理完上面的步骤后再将该buffer放回到output缓冲区队列
                    mMediaCodec.releaseOutputBuffer(index, false);
                } else if (index == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
                    outputBuffers = mMediaCodec.getOutputBuffers();
                } else if (index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    synchronized (AVCodec.this) {
                        Log.v(TAG, "output format changed...");
                        MediaFormat  newFormat = mMediaCodec.getOutputFormat();
                        addTrack(newFormat, false);
                       // if (muxer != null)
                        //addTrack(newFormat, false);
                    }
                } else if (index == MediaCodec.INFO_TRY_AGAIN_LATER) {
                    Log.v(TAG, "No buffer available...");
                } else {
                    Log.e(TAG, "Message: " + index);
                }
            } while (mWriter != null);
        }
    }




    public void stop() {
        try {
            Thread t = mThread;
            mThread = null;

            if (t != null) {
                t.interrupt();
                t.join();
            }
        } catch (InterruptedException e) {
            e.fillInStackTrace();
        }
    }


}
