package github.leavesc.wififiletransfer;

import android.app.ProgressDialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.net.Uri;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.IBinder;
import android.text.TextUtils;
import android.util.Log;
import android.webkit.MimeTypeMap;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.bumptech.glide.Glide;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.text.MessageFormat;
import java.util.Locale;

import github.leavesc.wififiletransfer.common.Constants;
import github.leavesc.wififiletransfer.model.ActionEvent;
import github.leavesc.wififiletransfer.model.FileTransfer;
import github.leavesc.wififiletransfer.service.CallbackSenderService;
import github.leavesc.wififiletransfer.service.FileReceiverService;
import github.leavesc.wififiletransfer.service.HotSpotStateReceiver;

/**
 * @Author: leavesC
 * @Date: 2018/4/3 14:53
 * @Desc:
 * @Github：https://github.com/leavesC
 */
public class FileReceiverActivity extends BaseActivity {

    private FileReceiverService fileReceiverService;

    private CallbackSenderService callbackSenderService;

    private ProgressDialog progressDialog;

    private static final String TAG = "xmg";

    private final ServiceConnection serviceConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            FileReceiverService.MyBinder binder = (FileReceiverService.MyBinder) service;
            fileReceiverService = binder.getService();
            fileReceiverService.setProgressChangListener(progressChangListener);
            if (!fileReceiverService.isRunning()) {
                FileReceiverService.startActionTransfer(FileReceiverActivity.this);
            }
            Toast.makeText(FileReceiverActivity.this, "网络连接", Toast.LENGTH_SHORT).show();
            Log.e(TAG, "FileReceiverActivity  onServiceConnected");
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            fileReceiverService = null;
            bindService(FileReceiverService.class, serviceConnection);
            Toast.makeText(FileReceiverActivity.this, "网络断开", Toast.LENGTH_SHORT).show();
            Log.e(TAG, "FileReceiverActivity  onServiceDisconnected");
        }
    };

    private final ServiceConnection mCallbackServiceConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            CallbackSenderService.MyBinder binder = (CallbackSenderService.MyBinder) service;
            callbackSenderService = binder.getService();
//                callbackSenderService.setProgressChangListener(progressChangListener);
            Log.e(TAG, "FileSenderActivity  onServiceConnected");
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            callbackSenderService = null;
//                bindService(FileSenderService.class, serviceConnection);
            Log.e(TAG, "FileSenderActivity  onServiceDisconnected");
        }
    };

    private final FileReceiverService.OnReceiveProgressChangListener progressChangListener = new FileReceiverService.OnReceiveProgressChangListener() {

        private FileTransfer originFileTransfer = new FileTransfer();

        @Override
        public void onProgressChanged(final FileTransfer fileTransfer, final long totalTime, final int progress, final double instantSpeed, final long instantRemainingTime, final double averageSpeed, final long averageRemainingTime) {
            this.originFileTransfer = fileTransfer;
            runOnUiThread(() -> {
                if (isCreated()) {
                    progressDialog.setTitle("正在接收的文件： " + originFileTransfer.getFileName());
                    if (progress != 100) {
                        progressDialog.setMessage("原始文件的MD5码是：" + originFileTransfer.getMd5()
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
        public void onStartComputeMD5() {
            runOnUiThread(() -> {
                if (isCreated()) {
                    progressDialog.setTitle("传输结束，正在计算本地文件的MD5码以校验文件完整性");
                    progressDialog.setMessage("原始文件的MD5码是：" + originFileTransfer.getMd5());
                    progressDialog.setCancelable(false);
                    progressDialog.show();
                }
            });
        }

        @Override
        public void onTransferSucceed(final FileTransfer fileTransfer) {
            runOnUiThread(() -> {
                if (isCreated()) {
                    progressDialog.setTitle("传输成功");
                    progressDialog.setMessage("原始文件的MD5码是：" + originFileTransfer.getMd5()
                            + "\n" + "本地文件的MD5码是：" + fileTransfer.getMd5()
                            + "\n" + "文件位置：" + fileTransfer.getFilePath());
                    progressDialog.setCancelable(true);
                    progressDialog.setCanceledOnTouchOutside(true);
                    progressDialog.show();
                    Glide.with(FileReceiverActivity.this).load(fileTransfer.getFilePath()).into(iv_image);
                }
            });
        }

        @Override
        public void onTransferFailed(final FileTransfer fileTransfer, final Exception e) {
            runOnUiThread(() -> {
                if (isCreated()) {
                    progressDialog.setTitle("传输失败");
                    progressDialog.setMessage("原始文件的MD5码是：" + originFileTransfer.getMd5()
                            + "\n" + "本地文件的MD5码是：" + fileTransfer.getMd5()
                            + "\n" + "文件位置：" + fileTransfer.getFilePath()
                            + "\n" + "异常信息：" + e.getMessage());
                    progressDialog.setCancelable(true);
                    progressDialog.setCanceledOnTouchOutside(true);
                    progressDialog.show();
                }
            });
        }
    };

    private ImageView iv_image;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_file_receiver);
        EventBus.getDefault().register(this);
        initView();
        bindService(FileReceiverService.class, serviceConnection);
        bindService(CallbackSenderService.class, mCallbackServiceConnection);
        //注册热点监听
        listenAp();
    }

    private void initView() {
        setTitle("接收文件");
        iv_image = findViewById(R.id.iv_image);
        TextView tv_hint = findViewById(R.id.tv_hint);
        tv_hint.setText(MessageFormat.format("接收文件前，需要先主动开启Wifi热点让文件发送端连接\n热点名：{0}\n密码：{1}", Constants.AP_SSID, Constants.AP_PASSWORD));
        progressDialog = new ProgressDialog(this);
        progressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
        progressDialog.setCancelable(false);
        progressDialog.setCanceledOnTouchOutside(false);
        progressDialog.setTitle("正在接收文件");
        progressDialog.setMax(100);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (fileReceiverService != null) {
            fileReceiverService.setProgressChangListener(null);
            unbindService(serviceConnection);
        }
        if (progressDialog != null && progressDialog.isShowing()) {
            progressDialog.dismiss();
        }

        try {
            //反注册热点监听
            unregisterReceiver(hotSpotStateReceiver);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void openFile(String filePath) {
        String ext = filePath.substring(filePath.lastIndexOf('.')).toLowerCase(Locale.US);
        try {
            MimeTypeMap mimeTypeMap = MimeTypeMap.getSingleton();
            String mime = mimeTypeMap.getMimeTypeFromExtension(ext.substring(1));
            mime = TextUtils.isEmpty(mime) ? "" : mime;
            Intent intent = new Intent();
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            intent.setAction(android.content.Intent.ACTION_VIEW);
            intent.setDataAndType(Uri.fromFile(new File(filePath)), mime);
            startActivity(intent);
        } catch (Exception e) {
            Log.e(TAG, "ReceiverActivity  文件打开异常：" + e.getMessage());
            showToast("文件打开异常：" + e.getMessage());
        }
    }

    /**
     * 注册广播，监听热点连接状态变化。 比如是否连接了热点。
     */
    private HotSpotStateReceiver hotSpotStateReceiver;
    private void listenAp(){
        //注册广播
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction("android.net.wifi.WIFI_AP_STATE_CHANGED");
        intentFilter.addAction("android.net.conn.TETHER_STATE_CHANGED");
        hotSpotStateReceiver = new HotSpotStateReceiver();
        registerReceiver(hotSpotStateReceiver, intentFilter);
    }

    public static boolean isHotSpotApOpen(Context context) {
        boolean isAPEnable = false;
        try {
            WifiManager wifiManager = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
            Method method = wifiManager.getClass().getDeclaredMethod("getWifiApState");
            int state = (int) method.invoke(wifiManager);
            Field field = wifiManager.getClass().getDeclaredField("WIFI_AP_STATE_ENABLED");
            int value = (int) field.get(wifiManager);
            isAPEnable = state == value;
        } catch (Exception e) {
            e.printStackTrace();
        }
        if(!isAPEnable && HotSpotStateReceiver.isCompatCheckApOpen) isAPEnable = true;
        return isAPEnable;
    }

    @Subscribe
    public void onEventBusCalled(ActionEvent event) {
        Log.i(TAG, "FileReceiverActivity  onEventBusCalled事件："+event.type);
        switch (event.type){
            case ActionEvent.TYPE_RESET_APP:
                //重启应用
                if (fileReceiverService != null) {
                    fileReceiverService.stopService(FileReceiverActivity.this);
                }
                if(callbackSenderService != null){
                    callbackSenderService.stopActionTransfer(FileReceiverActivity.this);
                }
                restartApp();
                break;
            case ActionEvent.TYPE_START_RECEIVER_SERVICES:
                if (!fileReceiverService.isRunning()) {
                    FileReceiverService.startActionTransfer(FileReceiverActivity.this);
                }else{
                    fileReceiverService.clean();
                    FileReceiverService.startActionTransfer(FileReceiverActivity.this);
                }
                break;
            case ActionEvent.TYPE_START_SENDER_CALLBACK_SERVICES:
                if(callbackSenderService != null){
                    String clientIp = "";
                    String serverIp = "";
                    try {
                        JSONObject jsonObject = new JSONObject(event.action);
                        serverIp = jsonObject.optString("serverIp");
                        clientIp = jsonObject.optString("clientIp");
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                    CallbackSenderService.startActionTransfer(FileReceiverActivity.this,
                            "", clientIp, serverIp,  "callback");
                }
                break;
            case ActionEvent.TYPE_STOP_SENDER_CALLBACK_SERVICES:
                if(callbackSenderService != null){
                    callbackSenderService.stopActionTransfer(FileReceiverActivity.this);
                }
                break;
        }

    }
}