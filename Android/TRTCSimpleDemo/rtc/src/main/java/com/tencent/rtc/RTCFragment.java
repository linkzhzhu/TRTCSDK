package com.tencent.rtc;

import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.Toast;

import com.tencent.liteav.TXLiteAVCode;
import com.tencent.liteav.beauty.TXBeautyManager;
import com.tencent.liteav.debug.Constant;
import com.tencent.liteav.debug.GenerateTestUserSig;
import com.tencent.rtmp.ui.TXCloudVideoView;
import com.tencent.trtc.TRTCCloud;
import com.tencent.trtc.TRTCCloudDef;
import com.tencent.trtc.TRTCCloudListener;

import java.lang.ref.WeakReference;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import static com.tencent.trtc.TRTCCloudDef.TRTCRoleAnchor;
import static com.tencent.trtc.TRTCCloudDef.TRTC_APP_SCENE_VIDEOCALL;

public class RTCFragment extends Fragment {
    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_rtc, container, false);
        initView(view);
        enterRoom();
        return view;
    }

    private void initView(View root) {
        mLocalVideoViewContainer = root.findViewById(R.id.small_video_view_container);
        mRemoteVideoViewContainer = root.findViewById(R.id.big_video_view_container);
    }

    private void enterRoom() {
        mTRTCCloud = TRTCCloud.sharedInstance(getActivity().getApplicationContext());
        mTRTCCloud.setListener(new TRTCCloudImplListener());

        // 初始化配置 SDK 参数
        TRTCCloudDef.TRTCParams trtcParams = new TRTCCloudDef.TRTCParams();
        trtcParams.sdkAppId = GenerateTestUserSig.SDKAPPID;
        trtcParams.userId ="link_android_simple_demo_dep";
        trtcParams.roomId = Integer.parseInt("111");
        // userSig是进入房间的用户签名，相当于密码（这里生成的是测试签名，正确做法需要业务服务器来生成，然后下发给客户端）
        trtcParams.userSig = GenerateTestUserSig.genTestUserSig(trtcParams.userId);
        trtcParams.role = TRTCRoleAnchor;

        // 进入通话
        mTRTCCloud.enterRoom(trtcParams, TRTC_APP_SCENE_VIDEOCALL);
        // 开启本地声音采集并上行
        mTRTCCloud.startLocalAudio();
        // 开启本地画面采集并上行

        SurfaceView surfaceView = new SurfaceView(getActivity().getApplicationContext());
        surfaceView.setZOrderMediaOverlay(true);
        mLocalVideoViewContainer.addView(surfaceView);
        mLocalVideoView = new TXCloudVideoView(surfaceView);
        mTRTCCloud.startLocalPreview(true, mLocalVideoView);

        /**
         * 设置默认美颜效果（美颜效果：自然，美颜级别：5, 美白级别：1）
         * 美颜风格.三种美颜风格：0 ：光滑  1：自然  2：朦胧
         * 视频通话场景推荐使用“自然”美颜效果
         */
        TXBeautyManager beautyManager = mTRTCCloud.getBeautyManager();
        beautyManager.setBeautyStyle(Constant.BEAUTY_STYLE_NATURE);
        beautyManager.setBeautyLevel(5);
        beautyManager.setWhitenessLevel(1);

        TRTCCloudDef.TRTCVideoEncParam encParam = new TRTCCloudDef.TRTCVideoEncParam();
        encParam.videoResolution = TRTCCloudDef.TRTC_VIDEO_RESOLUTION_640_360;
        encParam.videoFps = Constant.VIDEO_FPS;
        encParam.videoBitrate = Constant.RTC_VIDEO_BITRATE;
        encParam.videoResolutionMode = TRTCCloudDef.TRTC_VIDEO_RESOLUTION_MODE_PORTRAIT;
        mTRTCCloud.setVideoEncoderParam(encParam);

        if(mPeerUserId != null){
            SurfaceView surfaceViewRemote = new SurfaceView(getActivity().getApplicationContext());
            mRemoteVideoViewContainer.addView(surfaceViewRemote);
            mRemoteVideoView = new TXCloudVideoView(surfaceViewRemote);
            mTRTCCloud.startRemoteView(mPeerUserId, mRemoteVideoView);
        }
    }


    public String getPeerUserId(){
        return mPeerUserId;
    }
    private TRTCCloud mTRTCCloud;
    private String mPeerUserId;
    private class TRTCCloudImplListener extends TRTCCloudListener {


        public TRTCCloudImplListener() {
            super();
        }

        @Override
        public void onUserVideoAvailable(String userId, boolean available) {
            if (available) {
                SurfaceView surfaceView = new SurfaceView(getActivity().getApplicationContext());
                mRemoteVideoViewContainer.addView(surfaceView);
                mRemoteVideoView = new TXCloudVideoView(surfaceView);
                mTRTCCloud.startRemoteView(userId, mRemoteVideoView);
                mPeerUserId = userId;
            } else {
                mTRTCCloud.stopRemoteView(userId);
            }
        }

        // 错误通知监听，错误通知意味着 SDK 不能继续运行
        @Override
        public void onError(int errCode, String errMsg, Bundle extraInfo) {
            Log.d(TAG, "sdk callback onError");
            Toast.makeText(getActivity(), "onError: " + errMsg + "[" + errCode+ "]" , Toast.LENGTH_SHORT).show();
        }
    }

    private static final String TAG = "RTCFragment";
    private TXCloudVideoView mRemoteVideoView;
    private TXCloudVideoView mLocalVideoView;
    private ViewGroup mLocalVideoViewContainer;
    private ViewGroup mRemoteVideoViewContainer;
}
