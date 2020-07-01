package com.tencent.rtc;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.text.TextUtils;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;

import com.tencent.liteav.TXLiteAVCode;
import com.tencent.liteav.beauty.TXBeautyManager;
import com.tencent.liteav.debug.Constant;
import com.tencent.liteav.debug.GenerateTestUserSig;
import com.tencent.rtc.R;
import com.tencent.rtmp.ui.TXCloudVideoView;
import com.tencent.trtc.TRTCCloud;
import com.tencent.trtc.TRTCCloudDef;
import com.tencent.trtc.TRTCCloudListener;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;

import static com.tencent.trtc.TRTCCloudDef.TRTCRoleAnchor;
import static com.tencent.trtc.TRTCCloudDef.TRTC_APP_SCENE_VIDEOCALL;

/**
 * RTC视频通话的主页面
 *
 * 包含如下简单功能：
 * - 进入/退出视频通话房间
 * - 切换前置/后置摄像头
 * - 打开/关闭摄像头
 * - 打开/关闭麦克风
 * - 显示房间内其他用户的视频画面（当前示例最多可显示6个其他用户的视频画面）
 */
public class RTCActivity extends AppCompatActivity implements View.OnClickListener {

    private static final String TAG = "RTCActivity";
    private static final int REQ_PERMISSION_CODE  = 0x1000;

    private TextView                        mTitleText;                 //【控件】页面Title
    private ImageView                       mBackButton;                //【控件】返回上一级页面
    private Button                          mMuteVideo;                 //【控件】是否停止推送本地的视频数据
    private Button                          mMuteAudio;                 //【控件】开启、关闭本地声音采集并上行
    private Button                          mSwitchCamera;              //【控件】切换摄像头
    private Button                          mLogInfo;                   //【控件】开启、关闭日志显示
    private LinearLayout                    mVideoMutedTipsView;        //【控件】关闭视频时，显示默认头像

    private TRTCCloud                       mTRTCCloud;                 // SDK 核心类
    private boolean                         mIsFrontCamera = true;      // 默认摄像头前置
    private int                             mGrantedCount = 0;          // 权限个数计数，获取Android系统权限
    private int                             mUserCount = 0;             // 房间通话人数个数
    private int                             mLogLevel = 0;              // 日志等级
    private String                          mRoomId;                    // 房间Id
    private String                          mUserId;                    // 用户Id

    private SurfaceView                     mSmallVideoSurface;
    private TXCloudVideoView                mSmallVideoView;
    private ViewGroup                       mSmallVideoContainer;
    private Handler                         mMainThreadHandler;
    private RTCFragment                     mVideoFragment;
    private ViewGroup                       mFragmentContainer;
    private boolean                         mShowVideoFragment;
    private FragmentManager                 mFragmentManager;
    private Button                          mTestBtn;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_rtc);
        mMainThreadHandler = new Handler(getMainLooper());
        getSupportActionBar().hide();
        handleIntent();
        // 先检查权限再加入通话
        if (checkPermission()) {
            initView();
            enterRoom();
        }
    }

    private void handleIntent() {
        mUserId = "link_android_simple_demo_dep";
        mRoomId = "111";
    }

    private void initView() {
        mTitleText          = findViewById(R.id.trtc_tv_room_number);
        mBackButton         = findViewById(R.id.trtc_ic_back);
        mMuteVideo          = findViewById(R.id.trtc_btn_mute_video);
        mMuteAudio          = findViewById(R.id.trtc_btn_mute_audio);
        mSwitchCamera       = findViewById(R.id.trtc_btn_switch_camera);
        mLogInfo            = findViewById(R.id.trtc_btn_log_info);
        mVideoMutedTipsView = findViewById(R.id.ll_trtc_mute_video_default);
        mSmallVideoContainer = findViewById(R.id.small_view_container);
        mFragmentContainer = findViewById(R.id.video_fragment_container);
        mFragmentManager = getSupportFragmentManager();
        mTestBtn            = findViewById(R.id.btn_test);
        mTestBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                switchVideoFragment();
            }
        });

        if (!TextUtils.isEmpty(mRoomId)) {
            mTitleText.setText(mRoomId);
        }
        mBackButton.setOnClickListener(this);
        mMuteVideo.setOnClickListener(this);
        mMuteAudio.setOnClickListener(this);
        mSwitchCamera.setOnClickListener(this);
        mLogInfo.setOnClickListener(this);

    }

    private void switchVideoFragment(){
        mShowVideoFragment = !mShowVideoFragment;
        FragmentTransaction transaction = mFragmentManager.beginTransaction();
        if(mShowVideoFragment){
            if(mVideoFragment == null){
                mVideoFragment = new RTCFragment();
            }
            transaction.add(R.id.video_fragment_container, mVideoFragment);
            transaction.show(mVideoFragment);
        } else {
            transaction.remove(mVideoFragment);
            showRemoteView();
        }
        transaction.commit();
    }

    private void enterRoom() {
        mTRTCCloud = TRTCCloud.sharedInstance(getApplicationContext());
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        exitRoom();
    }

    /**
     * 离开通话
     */
    private void exitRoom() {
        mTRTCCloud.stopLocalAudio();
        mTRTCCloud.stopLocalPreview();
        mTRTCCloud.exitRoom();
        //销毁 trtc 实例
        if (mTRTCCloud != null) {
            mTRTCCloud.setListener(null);
        }
        mTRTCCloud = null;
        TRTCCloud.destroySharedInstance();
    }

    private void showRemoteView(){
        if(mSmallVideoSurface == null){
            mSmallVideoSurface = new SurfaceView(getApplicationContext());
            mSmallVideoContainer.addView(mSmallVideoSurface);
            mSmallVideoView = new TXCloudVideoView(mSmallVideoSurface);
        }
//        if(mSmallVideoContainer.getChildCount() > 0){
//            mSmallVideoContainer.removeAllViews();
//        }
//        mSmallVideoSurface = new SurfaceView(getApplicationContext());
//        mSmallVideoContainer.addView(mSmallVideoSurface);
//        mSmallVideoView = new TXCloudVideoView(mSmallVideoSurface);
        mTRTCCloud.startRemoteView(mVideoFragment.getPeerUserId(), mSmallVideoView);
    }

    //////////////////////////////////    Android动态权限申请   ////////////////////////////////////////

    private boolean checkPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            List<String> permissions = new ArrayList<>();
            if (PackageManager.PERMISSION_GRANTED != ActivityCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
                permissions.add(Manifest.permission.WRITE_EXTERNAL_STORAGE);
            }
            if (PackageManager.PERMISSION_GRANTED != ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA)) {
                permissions.add(Manifest.permission.CAMERA);
            }
            if (PackageManager.PERMISSION_GRANTED != ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)) {
                permissions.add(Manifest.permission.RECORD_AUDIO);
            }
            if (PackageManager.PERMISSION_GRANTED != ActivityCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)) {
                permissions.add(Manifest.permission.READ_EXTERNAL_STORAGE);
            }
            if (permissions.size() != 0) {
                ActivityCompat.requestPermissions(RTCActivity.this,
                        permissions.toArray(new String[0]),
                        REQ_PERMISSION_CODE);
                return false;
            }
        }
        return true;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_PERMISSION_CODE) {
            for (int ret : grantResults) {
                if (PackageManager.PERMISSION_GRANTED == ret) mGrantedCount++;
            }
            if (mGrantedCount == permissions.length) {
                initView();
                enterRoom(); //首次启动，权限都获取到，才能正常进入通话
            } else {
                Toast.makeText(this, getString(R.string.rtc_permisson_error_tip), Toast.LENGTH_SHORT).show();
            }
            mGrantedCount = 0;
        }
    }

    @Override
    public void onClick(View v) {
        int id = v.getId();
        if (id == R.id.trtc_ic_back) {
            finish();
        } else if (id == R.id.trtc_btn_mute_video) {
            muteVideo();
        } else if (id == R.id.trtc_btn_mute_audio) {
            muteAudio();
        } else if (id == R.id.trtc_btn_switch_camera) {
            switchCamera();
        } else if (id == R.id.trtc_btn_log_info) {
            showDebugView();
        }
    }

    private void muteVideo() {
//        boolean isSelected = mMuteVideo.isSelected();
//        if (!isSelected) {
//            mTRTCCloud.stopLocalPreview();
//            mMuteVideo.setBackground(getResources().getDrawable(R.mipmap.rtc_camera_off));
//            mVideoMutedTipsView.setVisibility(View.VISIBLE);
//        } else {
//            mTRTCCloud.startLocalPreview(mIsFrontCamera, mLocalPreviewView);
//            mMuteVideo.setBackground(getResources().getDrawable(R.mipmap.rtc_camera_on));
//            mVideoMutedTipsView.setVisibility(View.GONE);
//        }
//        mMuteVideo.setSelected(!isSelected);
    }

    private void muteAudio() {
        boolean isSelected = mMuteAudio.isSelected();
        if (!isSelected) {
            mTRTCCloud.stopLocalAudio();
            mMuteAudio.setBackground(getResources().getDrawable(R.mipmap.rtc_mic_off));
        } else {
            mTRTCCloud.startLocalAudio();
            mMuteAudio.setBackground(getResources().getDrawable(R.mipmap.rtc_mic_on));
        }
        mMuteAudio.setSelected(!isSelected);
    }

    private void switchCamera() {
        mTRTCCloud.switchCamera();
        boolean isSelected = mSwitchCamera.isSelected();
        mIsFrontCamera = !isSelected;
        mSwitchCamera.setSelected(!isSelected);
    }

    private void showDebugView() {
        mLogLevel = (mLogLevel + 1) % 3;
        mTRTCCloud.showDebugView(mLogLevel);
    }

}
