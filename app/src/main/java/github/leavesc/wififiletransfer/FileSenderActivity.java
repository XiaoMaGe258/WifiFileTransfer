package github.leavesc.wififiletransfer;

import static com.vincent.filepicker.activity.ImagePickActivity.IS_NEED_CAMERA;

import android.app.ProgressDialog;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.provider.MediaStore;
import android.util.Log;
import android.view.View;
import android.widget.TextView;

import com.shuyu.gsyvideoplayer.model.GSYVideoModel;
import com.shuyu.gsyvideoplayer.video.base.GSYBaseVideoPlayer;
import com.vincent.filepicker.FPConstant;
import com.vincent.filepicker.activity.ImagePickActivity;
import com.vincent.filepicker.activity.VideoPickActivity;
import com.vincent.filepicker.filter.entity.ImageFile;
import com.vincent.filepicker.filter.entity.VideoFile;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;

import java.io.File;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.List;

import github.leavesc.wififiletransfer.common.Constants;
import github.leavesc.wififiletransfer.videoplayer.MyListVideoPlayer;
import github.leavesc.wififiletransfer.manager.WifiLManager;
import github.leavesc.wififiletransfer.model.ActionEvent;
import github.leavesc.wififiletransfer.model.FileTransfer;
import github.leavesc.wififiletransfer.service.CallbackReceiverService;
import github.leavesc.wififiletransfer.service.FileSenderService;

/**
 * @Author: leavesC
 * @Date: 2018/4/3 14:53
 * @Desc:
 * @Github：https://github.com/leavesC
 */
public class FileSenderActivity extends BaseActivity {

    public static final String TAG = "xmg";

    private static final int CODE_CHOOSE_FILE = 100;

    private FileSenderService fileSenderService;

    private CallbackReceiverService callbackReceiverService;

    private ProgressDialog progressDialog;

    private final FileSenderService.OnSendProgressChangListener progressChangListener = new FileSenderService.OnSendProgressChangListener() {

        @Override
        public void onStartComputeMD5() {
            runOnUiThread(() -> {
                if (isCreated()) {
                    progressDialog.setTitle("发送文件");
                    progressDialog.setMessage("正在计算文件的MD5码");
                    progressDialog.setMax(100);
                    progressDialog.setProgress(0);
                    progressDialog.setCancelable(false);
                    progressDialog.show();
                }
            });
        }

        @Override
        public void onProgressChanged(final FileTransfer fileTransfer, final long totalTime, final int progress, final double instantSpeed, final long instantRemainingTime, final double averageSpeed, final long averageRemainingTime) {
            runOnUiThread(() -> {
                if (isCreated()) {
                    progressDialog.setTitle("正在发送文件： " + new File(fileTransfer.getFilePath()).getName());
                    if (progress != 100) {
                        progressDialog.setMessage("文件的MD5码：" + fileTransfer.getMd5()
                                + "\n\n" + "总的传输时间：" + totalTime + " 秒"
                                + "\n\n" + "瞬时-传输速率：" + (int) instantSpeed + " Kb/s"
                                + "\n" + "瞬时-预估的剩余完成时间：" + instantRemainingTime + " 秒"
                                + "\n\n" + "平均-传输速率：" + (int) averageSpeed + " Kb/s"
                                + "\n" + "平均-预估的剩余完成时间：" + averageRemainingTime + " 秒"
                        );
                    }
                    progressDialog.setProgress(progress);
                    progressDialog.setCancelable(true);
                    progressDialog.show();
                }
            });
        }

        @Override
        public void onTransferSucceed(FileTransfer fileTransfer) {
            runOnUiThread(() -> {
                if (isCreated()) {
                    progressDialog.setTitle("文件发送成功");
                    progressDialog.setCancelable(true);
                    progressDialog.setCanceledOnTouchOutside(true);
                    progressDialog.show();
                }
            });
        }

        @Override
        public void onTransferFailed(FileTransfer fileTransfer, final Exception e) {
            runOnUiThread(() -> {
                if (isCreated()) {
                    progressDialog.setTitle("文件发送失败");
                    progressDialog.setMessage("异常信息： " + e.getMessage());
                    progressDialog.setCancelable(true);
                    progressDialog.setCanceledOnTouchOutside(true);
                    progressDialog.show();
                }
            });
        }
    };

    private final ServiceConnection serviceConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            FileSenderService.MyBinder binder = (FileSenderService.MyBinder) service;
            fileSenderService = binder.getService();
            fileSenderService.setProgressChangListener(progressChangListener);
            Log.e(TAG, "FileSenderActivity  onServiceConnected");
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            fileSenderService = null;
            bindService(FileSenderService.class, serviceConnection);
            Log.e(TAG, "FileSenderActivity  onServiceDisconnected");
        }
    };

    private final ServiceConnection callbackServiceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            CallbackReceiverService.MyBinder binder = (CallbackReceiverService.MyBinder) service;
            callbackReceiverService = binder.getService();
//                fileReceiverService.setProgressChangListener(progressChangListener);

//            Toast.makeText(context, "网络连接", Toast.LENGTH_SHORT).show();
            Log.e(TAG, "FileReceiverActivity  onServiceConnected");
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            callbackReceiverService = null;
            bindService(CallbackReceiverService.class, callbackServiceConnection);
//            Toast.makeText(context, "网络断开", Toast.LENGTH_SHORT).show();
            Log.e(TAG, "FileReceiverActivity  onServiceDisconnected");
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_file_sender);
        EventBus.getDefault().register(this);
        initView();
        bindService(FileSenderService.class, serviceConnection);
        bindService(CallbackReceiverService.class, callbackServiceConnection);
    }

    MyListVideoPlayer videoPlayer;
    private void initView() {
        setTitle("发送文件");
        TextView tv_hint = findViewById(R.id.tv_hint);
        tv_hint.setText(MessageFormat.format("在发送文件前需要先连上文件接收端开启的Wifi热点\n热点名：{0} \n密码：{1}", Constants.AP_SSID, Constants.AP_PASSWORD));
        progressDialog = new ProgressDialog(this);
        progressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
        progressDialog.setCancelable(false);
        progressDialog.setCanceledOnTouchOutside(false);
        progressDialog.setTitle("发送文件");
        progressDialog.setMax(100);
        progressDialog.setIndeterminate(false);

        videoPlayer = findViewById(R.id.video_player);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (fileSenderService != null) {
            fileSenderService.setProgressChangListener(null);
            unbindService(serviceConnection);
        }
        progressDialog.dismiss();
    }

    public void sendFile(View view) {
//        if (!Constants.AP_SSID.equals(WifiLManager.getConnectedSSID(this))) {
//            showToast("当前连接的 Wifi 并非文件接收端开启的 Wifi 热点，请重试或者检查权限");
//            return;
//        }
//        navToChosePicture();
        Intent intent1 = new Intent(this, ImagePickActivity.class);
        intent1.putExtra(IS_NEED_CAMERA, false);
        intent1.putExtra(FPConstant.MAX_NUMBER, 1);
        startActivityForResult(intent1, FPConstant.REQUEST_CODE_PICK_IMAGE);
    }
    public void sendVideo(View view) {
//        if (!Constants.AP_SSID.equals(WifiLManager.getConnectedSSID(this))) {
//            showToast("当前连接的 Wifi 并非文件接收端开启的 Wifi 热点，请重试或者检查权限");
//            return;
//        }
        if(videoPlayer != null){
            videoPlayer.release();
        }
        Intent intent = new Intent(this, VideoPickActivity.class);
        intent.putExtra(IS_NEED_CAMERA, false);
        intent.putExtra(FPConstant.MAX_NUMBER, 3);
        startActivityForResult(intent, FPConstant.REQUEST_CODE_PICK_VIDEO);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == CODE_CHOOSE_FILE) {
            if (resultCode == RESULT_OK) {
                String imageUri = data.getData().toString();
                Log.e(TAG, "FileSenderActivity  文件路径：" + imageUri);
                FileSenderService.startActionTransfer(this, imageUri,
                        WifiLManager.getHotspotIpAddress(this),
                        WifiLManager.getLocalIpAddress(this), "send");
            }
        }else if(requestCode == FPConstant.REQUEST_CODE_PICK_IMAGE && resultCode == RESULT_OK){
            ArrayList<ImageFile> list = data.getParcelableArrayListExtra(FPConstant.RESULT_PICK_IMAGE);
            for(ImageFile file : list){
                FileSenderService.startActionTransfer(this, file.getPath(),
                        WifiLManager.getHotspotIpAddress(this),
                        WifiLManager.getLocalIpAddress(this), "image");
            }
        }else if(requestCode == FPConstant.REQUEST_CODE_PICK_VIDEO && resultCode == RESULT_OK){
            ArrayList<VideoFile> list = data.getParcelableArrayListExtra(FPConstant.RESULT_PICK_VIDEO);
            List<GSYVideoModel> urls = new ArrayList<>();
            int index = 0;
            for(VideoFile file : list){

                urls.add(new GSYVideoModel(file.getPath(), "标题"+index));
                index++;
//                FileSenderService.startActionTransfer(this, file.getPath(),
//                        WifiLManager.getHotspotIpAddress(this),
//                        WifiLManager.getLocalIpAddress(this), "video");
            }
            if(videoPlayer != null){
                videoPlayer.setUp(urls, false, 0);
                videoPlayer.setListLoop(true);
                videoPlayer.startPlayLogic();
            }
        }
    }

    private void navToChosePicture() {
        Intent intent = new Intent(Intent.ACTION_PICK, null);
        intent.setDataAndType(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, "image/*");
        startActivityForResult(intent, CODE_CHOOSE_FILE);
    }

    @Subscribe
    public void onEventBusCalled(ActionEvent event) {
        Log.i(TAG, "onEventBusCalled事件："+event.type);
        switch (event.type){
            case ActionEvent.TYPE_RESET_APP:
                //重启应用
                if (fileSenderService != null) {
                    fileSenderService.clean();
                }
                if(callbackReceiverService != null){
                    callbackReceiverService.stopService(FileSenderActivity.this);
                }

                restartApp();
                break;
            case ActionEvent.TYPE_START_SENDER_CALLBACK_SERVICES:
                break;
            case ActionEvent.TYPE_START_RECEIVER_CALLBACK_SERVICES:
                if(callbackReceiverService != null){
                    if (!callbackReceiverService.isRunning()) {
                        CallbackReceiverService.startActionTransfer(FileSenderActivity.this);
                    }
                }
                break;
        }

    }

}