package com.demo.hdz.mediacodecdemo;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private static final int PERMISSION_REQUEST_CODE          = 1000;
    private static final int PERMISSION_REQUEST_CODE_STORAGE  = 1001;
    private static final int PERMISSION_REQUEST_CODE_RECORD   = 1002;

    private static final int CAPTURE_REQUEST_CODE = 1003;

    private static final int ACTIVITY_REQUEST_CODE_SELECT_SAVE_PATH = 2001;
    private static final int ACTIVITY_REQUEST_CODE_SELECT_H264_PATH = 2002;

    private EditText m_etSavePath;
    private EditText m_etH264FilePath;

    private String m_sSdcardPath = "";

    private MediaProjectionManager mMediaProjectionManager = null;
    private ScreenRecordThread mRecordThread = null;
    private MediaProjection mMediaProjection = null;
    private int m_iScreenWidth = 0;
    private int m_iScreenHeight = 0;
    private int m_iScreenDensity = 0;

    private AVCodec m_avCodec = null;
    private SurfaceView mSurfaceView;

    private final int MSG_DECODE_INIT_FAILED = 1;
    private final int MSG_DECODE_START       = 2;
    private final int MSG_DECODE_FINISH      = 3;
    private Handler m_handler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
            switch (msg.what) {
                case MSG_DECODE_INIT_FAILED:
                    if (mSurfaceView != null) {
                        mSurfaceView.setVisibility(View.GONE);
                    }
                    showToast("解码器初始化失败！");
                    break;
                case MSG_DECODE_START:
                    if (mSurfaceView != null) {
                        mSurfaceView.setVisibility(View.VISIBLE);
                    }
                    break;
                case MSG_DECODE_FINISH:
                    if (mSurfaceView != null) {
                        mSurfaceView.setVisibility(View.GONE);
                    }
                    break;
                default:
                    break;
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        DisplayMetrics metrics = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(metrics);
        m_iScreenWidth   = metrics.widthPixels;
        m_iScreenHeight  = metrics.heightPixels;
        m_iScreenDensity = metrics.densityDpi;

        m_etSavePath     = (EditText)findViewById(R.id.etSavePath);
        m_etH264FilePath = (EditText)findViewById(R.id.etH264FilePath);
        m_etH264FilePath.setText("/sdcard/carPlay_800x480.h264");

        Button btnSelectSavePath = (Button)findViewById(R.id.btnSelectSavePath);
        btnSelectSavePath.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                selectSavePath();
            }
        });

        Button btnSelectH264File = (Button)findViewById(R.id.btnSelectH264File);
        btnSelectH264File.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                selectH264File();
            }
        });

        Button btnRecordScreen = (Button)findViewById(R.id.btnRecordScreen);
        btnRecordScreen.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                recordScreen();
            }
        });

        Button btnPlayH264File = (Button)findViewById(R.id.btnPlayH264);
        btnPlayH264File.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                //playH264File();
            }
        });

        try {
            //判断设备是否插入了SD卡
            if (Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED)) {
                m_sSdcardPath = Environment.getExternalStorageDirectory().getCanonicalPath();
                Logger.d("SdcardPath: " + m_sSdcardPath);
            }
        } catch (Exception e) {
            m_sSdcardPath = "";
            e.printStackTrace();
            Logger.e(Log.getStackTraceString(e));
        }

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
            //第一步：通过 getSystemService（）得到MediaProjectionManager服务
            mMediaProjectionManager = (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);
        }

        initSurface();

        AskForPermission();
    }

    private void initSurface() {
        mSurfaceView = (SurfaceView)findViewById(R.id.playView);
        mSurfaceView.getHolder().addCallback(new SurfaceHolder.Callback() {
            @Override
            public void surfaceCreated(SurfaceHolder holder) {

            }

            @Override
            public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {

            }

            @Override
            public void surfaceDestroyed(SurfaceHolder holder) {

            }
        });
    }

    @Override
    public void onBackPressed() {
        moveTaskToBack(false);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (resultCode == Activity.RESULT_OK) {
            if (requestCode == ACTIVITY_REQUEST_CODE_SELECT_SAVE_PATH) {
                Uri uri = data.getData();
                String filePath = uri.getPath() + "/screen.h264";
                m_etSavePath.setText(filePath);
                m_etSavePath.setSelection(m_etSavePath.getText().length()); //光标始终移动到末尾
            } else if (requestCode == ACTIVITY_REQUEST_CODE_SELECT_H264_PATH) {
                Uri uri = data.getData();
                m_etH264FilePath.setText(uri.getPath());
                m_etH264FilePath.setSelection(m_etH264FilePath.getText().length());
            } else if (requestCode == CAPTURE_REQUEST_CODE) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    goSystemHome();
                    mMediaProjection = mMediaProjectionManager.getMediaProjection(resultCode, data);
                    mRecordThread = new ScreenRecordThread(m_iScreenWidth, m_iScreenHeight, m_iScreenDensity, mMediaProjection, "/sdcard/screen.MP4");
                    mRecordThread.start();
                }
            }
        } else {
            if (requestCode == CAPTURE_REQUEST_CODE) {
                showToast("User denied screen sharing permission");
            }
        }
        super.onActivityResult(requestCode, resultCode, data);
    }


    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        if (requestCode == PERMISSION_REQUEST_CODE_STORAGE) {
            if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                showToast("文件读写权限已开启");
            } else {
                showToast("文件读写权限已拒绝");
            }
        } else if (requestCode == PERMISSION_REQUEST_CODE_RECORD) {
            if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                showToast("录音权限已开启");
            } else {
                showToast("录音权限已拒绝");
            }
        } else if (requestCode == PERMISSION_REQUEST_CODE){
            String sToast = "";
            for (int i = 0; i < grantResults.length; i++) {
                if (grantResults[i] != PackageManager.PERMISSION_GRANTED) {
                    boolean showRequestPermission = ActivityCompat.shouldShowRequestPermissionRationale(MainActivity.this, permissions[i]);
                    if (showRequestPermission) {
                        //禁止后但没有勾选不再询问
                        if (permissions[i].equals(Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
                            if (!sToast.equals("")) {
                                sToast += "、";
                            }
                            sToast += "文件读写权限";
                        } else if (permissions[i].equals(Manifest.permission.RECORD_AUDIO)) {
                            if (!sToast.equals("")) {
                                sToast += "、";
                            }
                            sToast += "录音权限";
                        }
                    } else {
                        //勾选不再询问并禁止,转到设置
                        if (permissions[i].equals(Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
                            if (!sToast.equals("")) {
                                sToast += "、";
                            }
                            sToast += "文件读写权限";
                        } else if (permissions[i].equals(Manifest.permission.RECORD_AUDIO)) {
                            if (!sToast.equals("")) {
                                sToast += "、";
                            }
                            sToast += "录音权限";
                        }
                    }
                }
            }
            if (!sToast.equals("")) {
                showToast("请开启"+sToast);
                goAppSettings(this);
            }
        }
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }

    private void AskForPermission(){
        if (Build.VERSION.SDK_INT >= 23) {
            List<String> permissionList = new ArrayList<>();
            if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                permissionList.add(Manifest.permission.WRITE_EXTERNAL_STORAGE);
            }
            if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                permissionList.add(Manifest.permission.RECORD_AUDIO);
            }
            if (permissionList.size() > 0) {
                String[] permissions = permissionList.toArray(new String[permissionList.size()]);//将List转为数组
                ActivityCompat.requestPermissions(this, permissions, PERMISSION_REQUEST_CODE);
            }
        }
    }

    private void goAppSettings(Context context){
        Intent intent = new Intent();
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        if(Build.VERSION.SDK_INT >= 9){
            intent.setAction("android.settings.APPLICATION_DETAILS_SETTINGS");
            intent.setData(Uri.fromParts("package", getPackageName(), null));
        } else if(Build.VERSION.SDK_INT <= 8){
            intent.setAction(Intent.ACTION_VIEW);
            intent.setClassName("com.android.settings","com.android.settings.InstalledAppDetails");
            intent.putExtra("com.android.settings.ApplicationPkgName", getPackageName());
        }
        context.startActivity(intent);
    }

    private void goSystemHome() {
        Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.addCategory(Intent.CATEGORY_HOME);
        MainActivity.this.startActivity(intent);
    }

    void selectSavePath() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
            startActivityForResult(intent,ACTIVITY_REQUEST_CODE_SELECT_SAVE_PATH);
        }
    }
    void selectH264File() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("*/*");//设置类型，我这里是任意类型，任意后缀的可以这样写。
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        startActivityForResult(Intent.createChooser(intent, "请选择一个h264文件"), ACTIVITY_REQUEST_CODE_SELECT_H264_PATH);
        //startActivityForResult(intent,ACTIVITY_REQUEST_CODE_SELECT_H264_PATH);
    }
    void recordScreen() {
        if (m_etSavePath.getText().length() <= 0) {
            m_etSavePath.requestFocus();
            showToast("请选择一个保存路径");
            return;
        }

        if (mMediaProjectionManager == null) {
            return;
        }
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
            //第二步：通过MediaProjectionManager创建一个屏幕捕捉意图
            Intent captureIntent = mMediaProjectionManager.createScreenCaptureIntent();
            //第三步：通过startActivityForResult开启该意图
            startActivityForResult(captureIntent, CAPTURE_REQUEST_CODE);
        } else {
            showToast("系统不支持");
        }
    }

//    void playH264File() {
//        if (m_etH264FilePath.getText().length() <= 0) {
//            m_etH264FilePath.requestFocus();
//            showToast("请选择一个h264文件");
//            return;
//        }
//
//        File file = new File(m_etH264FilePath.getText().toString());
//        if (file==null || !file.isFile() || !file.exists()) {
//            showToast("文件不存在："+ m_etH264FilePath.getText().toString());
//            return;
//        }
//
//        sendMessage(MSG_DECODE_START, "");
//
//        new Thread(new Runnable() {
//            @Override
//            public void run() {
//                m_avCodec = new AVCodec();
//                if (!m_avCodec.initDecode(800, 480, mSurfaceView.getHolder().getSurface())) {
//                    m_avCodec.close();
//                    sendMessage(MSG_DECODE_INIT_FAILED, "");
//                    return;
//                }
//                try {
//                    InputStream inFile = new FileInputStream(new File(m_etH264FilePath.getText().toString()));
//                    m_avCodec.decodeFile(inFile);
//                } catch (FileNotFoundException e) {
//                    e.printStackTrace();
//                    Logger.e(Log.getStackTraceString(e));
//                } finally {
//                    m_avCodec.close();
//                    sendMessage(MSG_DECODE_FINISH, "");
//                }
//            }
//        }).start();
//    }

    private void showToast(String str) {
        try {
            Toast.makeText(MainActivity.this, str, Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    protected void onRestart() {
        super.onRestart();
        if (mRecordThread != null) {
            mRecordThread.release();
            mRecordThread = null;
            Toast.makeText(this, "录制结束", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        if (mRecordThread != null) {
            mRecordThread.release();
            mRecordThread = null;
        }

        if (m_avCodec != null) {
            m_avCodec.close();
            m_avCodec = null;
        }
    }

    private void sendMessage(int what, Object obj) {
        if (m_handler != null) {
            Message message = new Message();
            message.what = what;
            message.obj = obj;
            m_handler.sendMessage(message);
        }
    }
}
