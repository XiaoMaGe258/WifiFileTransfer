package github.leavesc.wififiletransfer.service;

import android.app.IntentService;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.net.Uri;
import android.os.Binder;
import android.os.IBinder;
import android.text.TextUtils;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.Nullable;

import com.blankj.utilcode.util.ConvertUtils;

import org.greenrobot.eventbus.EventBus;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Random;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import github.leavesc.wififiletransfer.BuildConfig;
import github.leavesc.wififiletransfer.common.Constants;
import github.leavesc.wififiletransfer.common.Logger;
import github.leavesc.wififiletransfer.common.Md5Util;
import github.leavesc.wififiletransfer.manager.WifiLManager;
import github.leavesc.wififiletransfer.model.ActionEvent;
import github.leavesc.wififiletransfer.model.FileTransfer;

/**
 * @Author: leavesC
 * @Date: 2018/4/3 17:32
 * @Desc:
 * @Github：https://github.com/leavesC
 */
public class CallbackSenderService extends IntentService {

    private Socket socket;

    private OutputStream outputStream;

//    private ObjectOutputStream objectOutputStream;

//    private InputStream inputStream;

//    private OnSendProgressChangListener progressChangListener;

    private static final String ACTION_START_SEND = BuildConfig.APPLICATION_ID + ".service.action.startSend";

    private static final String EXTRA_PARAM_FILE_TRANSFER = BuildConfig.APPLICATION_ID + ".service.extra.FileUri";

    private static final String EXTRA_PARAM_IP_ADDRESS = BuildConfig.APPLICATION_ID + ".service.extra.IpAddress";

    private static final String EXTRA_PARAM_LOCAL_IP_ADDRESS = BuildConfig.APPLICATION_ID + ".service.extra.Local.IpAddress";

    private static final String EXTRA_PARAM_JSON = BuildConfig.APPLICATION_ID + ".service.extra.Json";

    private static final String TAG = "xmg";

//    public interface OnSendProgressChangListener {
//
//        /**
//         * 如果待发送的文件还没计算MD5码，则在开始计算MD5码时回调
//         */
//        void onStartComputeMD5();
//
//        /**
//         * 当传输进度发生变化时回调
//         *
//         * @param fileTransfer         待发送的文件模型
//         * @param totalTime            传输到现在所用的时间
//         * @param progress             文件传输进度
//         * @param instantSpeed         瞬时-文件传输速率
//         * @param instantRemainingTime 瞬时-预估的剩余完成时间
//         * @param averageSpeed         平均-文件传输速率
//         * @param averageRemainingTime 平均-预估的剩余完成时间
//         */
//        void onProgressChanged(FileTransfer fileTransfer, long totalTime, int progress, double instantSpeed, long instantRemainingTime, double averageSpeed, long averageRemainingTime);
//
//        /**
//         * 当文件传输成功时回调
//         *
//         * @param fileTransfer FileTransfer
//         */
//        void onTransferSucceed(FileTransfer fileTransfer);
//
//        /**
//         * 当文件传输失败时回调
//         *
//         * @param fileTransfer FileTransfer
//         * @param e            Exception
//         */
//        void onTransferFailed(FileTransfer fileTransfer, Exception e);
//
//    }

    public CallbackSenderService() {
        super("CallbackSenderService");
    }

    public class MyBinder extends Binder {
        public CallbackSenderService getService() {
            return CallbackSenderService.this;
        }
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return new CallbackSenderService.MyBinder();
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        Log.d("xmg", "test 0 ");
        if (intent != null && ACTION_START_SEND.equals(intent.getAction())) {
            try {
                clean();
//                String localIp = intent.getStringExtra(EXTRA_PARAM_LOCAL_IP_ADDRESS);
                String ipAddress = intent.getStringExtra(EXTRA_PARAM_IP_ADDRESS);
                Log.e(TAG, "CallbackSenderService  IP地址：" + ipAddress);
                if (TextUtils.isEmpty(ipAddress)) {
                    return;
                }

                int index = 0;
                while (ipAddress.equals("0.0.0.0") && index < 5) {
                    Log.e(TAG, "CallbackSenderService  ip: " + ipAddress);
                    ipAddress = WifiLManager.getHotspotIpAddress(this);
                    index++;
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
                if (ipAddress.equals("0.0.0.0")) {
                    return;
                }
//                fileTransfer.setServerIp(ipAddress);
                Log.d("xmg", "test 1 ipAddress="+ipAddress);
                socket = new Socket();
                socket.bind(null);
                Log.d("xmg", "test 2");
                socket.connect((new InetSocketAddress(ipAddress, Constants.PORT)), 20000);
                Log.d("xmg", "test 3");
                outputStream = socket.getOutputStream();
                Log.d("xmg", "test 4");
//                objectOutputStream = new ObjectOutputStream(outputStream);
//                objectOutputStream.writeObject(fileTransfer);
                //TODO 自定义传输协议。 头4字节代表后续json数据长度，接着Json(传递相关数据)，再接着发文件。
                JSONObject jsonObject = new JSONObject();
                jsonObject.put("json", "{\"content\":\"SUCCESS!\"}");

                String jsonStr = jsonObject.toString();
                Log.d("xmg", "jsonStr.length="+jsonStr.length());
                byte[] headJson = jsonStr.getBytes(StandardCharsets.UTF_8);
                Log.d("xmg", "headJson.length="+headJson.length);

                String headLenStr = ConvertUtils.int2HexString(headJson.length);
                Log.d("xmg", "headLenStr="+headLenStr);//ea
                String headLenStr8 = String.format("%08X", headJson.length);
                Log.d("xmg", "headLenStr4="+(headLenStr8));//000000EA
                byte[] headLen = ConvertUtils.hexString2Bytes(headLenStr8);
                Log.d("xmg", "headLen.length="+headLen.length);//4
                byte[] dataBytes = addBytes(headLen, headJson);
                outputStream.write(dataBytes, 0, dataBytes.length);

                Log.e(TAG, "CallbackSenderService  callback发送成功");

            } catch (Exception e) {
                e.printStackTrace();
                Log.e(TAG, "CallbackSenderService  发送异常 Exception: " + e.getMessage());
            } finally {
                clean();
            }

            EventBus.getDefault().post(new ActionEvent(ActionEvent.TYPE_STOP_SENDER_CALLBACK_SERVICES));
            EventBus.getDefault().post(new ActionEvent(ActionEvent.TYPE_START_RECEIVER_SERVICES));
        }
    }

    public static byte[] addBytes(byte[] data1, byte[] data2) {
        byte[] data3 = new byte[data1.length + data2.length];
        System.arraycopy(data1, 0, data3, 0, data1.length);
        System.arraycopy(data2, 0, data3, data1.length, data2.length);
        return data3;
    }


    private String receiveMsg;
    private BufferedReader in;
    private PrintWriter printWriter;

    private void receiveMsg(){
        try {
            while (printWriter != null) {                                      //步骤三
                if ((receiveMsg = in.readLine()) != null) {
                    Log.d(TAG, "receiveMsg:" + receiveMsg);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        clean();
    }

    public void clean() {
        if (socket != null && !socket.isClosed()) {
            try {
                socket.close();
                socket = null;
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        if (outputStream != null) {
            try {
                outputStream.close();
                outputStream = null;
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        if (printWriter != null) {
            try {
                printWriter.close();
                printWriter = null;
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        if (in != null) {
            try {
                in.close();
                in = null;
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public static void startActionTransfer(Context context, String fileUri, String serverIp, String clientIp, String type) {
        Log.d("xmg", "CallbackSenderService.startActionTransfer  fileUri="+fileUri
                +"  serverIp="+serverIp+"  clientIp="+clientIp+"  type="+type);
        Intent intent = new Intent(context, CallbackSenderService.class);
        intent.setAction(ACTION_START_SEND);
        intent.putExtra(EXTRA_PARAM_FILE_TRANSFER, fileUri);
        intent.putExtra(EXTRA_PARAM_IP_ADDRESS, serverIp);
        intent.putExtra(EXTRA_PARAM_LOCAL_IP_ADDRESS, clientIp);
        intent.putExtra(EXTRA_PARAM_JSON, type);
        context.startService(intent);
    }

    public void stopActionTransfer(Context context) {
        clean();

        Intent intent = new Intent(context, CallbackSenderService.class);
        context.stopService(intent);
    }

}
