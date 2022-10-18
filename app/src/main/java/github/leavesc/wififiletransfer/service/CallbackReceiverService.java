package github.leavesc.wififiletransfer.service;

import android.app.IntentService;
import android.content.Context;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.Nullable;

import com.blankj.utilcode.util.ConvertUtils;

import org.greenrobot.eventbus.EventBus;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.PrintWriter;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import github.leavesc.wififiletransfer.BuildConfig;
import github.leavesc.wififiletransfer.common.Constants;
import github.leavesc.wififiletransfer.common.Logger;
import github.leavesc.wififiletransfer.common.Md5Util;
import github.leavesc.wififiletransfer.model.ActionEvent;
import github.leavesc.wififiletransfer.model.FileTransfer;

/**
 * @Author: leavesC
 * @Date: 2018/4/3 15:23
 * @Desc:
 * @Github：https://github.com/leavesC
 */
public class CallbackReceiverService extends IntentService {

    private static final String ACTION_START_RECEIVE = BuildConfig.APPLICATION_ID + ".service.action.startReceive";

    private static final String TAG = "xmg";

    private ServerSocket serverSocket;

    private InputStream inputStream;

    private PrintWriter printWriter;//接收完数据返回用

    private ObjectInputStream objectInputStream;

    private FileOutputStream fileOutputStream;

    public class MyBinder extends Binder {
        public CallbackReceiverService getService() {
            return CallbackReceiverService.this;
        }
    }

    public CallbackReceiverService() {
        super("CallbackReceiverService");
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return new MyBinder();
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        if (intent != null && ACTION_START_RECEIVE.equals(intent.getAction())) {
            clean();
            try {
                serverSocket = new ServerSocket();
                serverSocket.setReuseAddress(true);
                serverSocket.bind(new InetSocketAddress(Constants.PORT));
                Socket client = serverSocket.accept();
                Log.e(TAG, "CallbackReceiverService  客户端IP地址 : " + client.getInetAddress().getHostAddress());
                inputStream = client.getInputStream();
                String json = "";
                //TODO 自定义解析数据协议
                try {
                    byte[] bufLen = new byte[4];
                    inputStream.read(bufLen);
                    String headLenStr = ConvertUtils.bytes2HexString(bufLen);
                    Log.e("xmg", "#############headLenStr="+headLenStr);//000000EA
                    int len = ConvertUtils.hexString2Int(headLenStr);
                    Log.e("xmg", "#############headLen="+len);//234

                    byte[] bufJson = new byte[len];
                    inputStream.read(bufJson);
                    String dataStr = new String(bufJson, StandardCharsets.UTF_8);
                    Log.e("xmg", "#############dataStr="+dataStr);

                    JSONObject jsonObject = new JSONObject(dataStr);
                    json = jsonObject.optString("json");
                } catch (Exception e) {
                    e.printStackTrace();
                }

                //TODO test
                Log.w(TAG, "callback 成功="+json);


            } catch (Exception e) {
                e.printStackTrace();
                Log.e(TAG, "CallbackReceiverService  文件接收 Exception: " + e.getMessage());
            } finally {
                clean();
            }
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        clean();
    }

    private void clean() {
        if (serverSocket != null && !serverSocket.isClosed()) {
            try {
                serverSocket.close();
                serverSocket = null;
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        if (inputStream != null) {
            try {
                inputStream.close();
                inputStream = null;
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
        if (objectInputStream != null) {
            try {
                objectInputStream.close();
                objectInputStream = null;
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        if (fileOutputStream != null) {
            try {
                fileOutputStream.close();
                fileOutputStream = null;
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public static void startActionTransfer(Context context) {
        Intent intent = new Intent(context, CallbackReceiverService.class);
        intent.setAction(ACTION_START_RECEIVE);
        context.startService(intent);
    }

    public void stopService(Context context){
        clean();
        Intent intent = new Intent(context, CallbackReceiverService.class);
        context.stopService(intent);
    }

}
