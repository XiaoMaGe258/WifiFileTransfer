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

    public interface OnReceiveProgressChangListener {

        /**
         * 当传输进度发生变化时回调
         *
         * @param fileTransfer         待发送的文件模型
         * @param totalTime            传输到现在所用的时间
         * @param progress             文件传输进度
         * @param instantSpeed         瞬时-文件传输速率
         * @param instantRemainingTime 瞬时-预估的剩余完成时间
         * @param averageSpeed         平均-文件传输速率
         * @param averageRemainingTime 平均-预估的剩余完成时间
         */
        void onProgressChanged(FileTransfer fileTransfer, long totalTime, int progress, double instantSpeed, long instantRemainingTime, double averageSpeed, long averageRemainingTime);

        /**
         * 当文件传输结束后，开始计算MD5码时回调
         */
        void onStartComputeMD5();

        /**
         * 当文件传输成功时回调
         *
         * @param fileTransfer FileTransfer
         */
        void onTransferSucceed(FileTransfer fileTransfer);

        /**
         * 当文件传输失败时回调
         *
         * @param fileTransfer FileTransfer
         * @param e            Exception
         */
        void onTransferFailed(FileTransfer fileTransfer, Exception e);

    }

    private ServerSocket serverSocket;

    private InputStream inputStream;

//    private OutputStream outputStream;//接收完数据返回用
    private PrintWriter printWriter;//接收完数据返回用

    private ObjectInputStream objectInputStream;

    private FileOutputStream fileOutputStream;

    private OnReceiveProgressChangListener progressChangListener;

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

    private ScheduledExecutorService callbackService;

    private FileTransfer fileTransfer;

    //总的已接收字节数
    private long total;

    //在上一次更新进度时已接收的文件总字节数
    private long tempTotal = 0;

    //计算瞬时传输速率的间隔时间
    private static final int PERIOD = 400;

    //传输操作开始时间
    private Date startTime;

    //用于标记是否正在进行文件接收操作
    private boolean running;

    private void startCallback() {
        stopCallback();
        startTime = new Date();
        running = true;
        callbackService = Executors.newScheduledThreadPool(1);
        Runnable runnable = () -> {
            if (fileTransfer != null) {
                //过去 PERIOD 秒内文件的瞬时传输速率（Kb/s）
                double instantSpeed = 0;
                //根据瞬时速率计算的-预估的剩余完成时间（秒）
                long instantRemainingTime = 0;
                //到现在所用的总的传输时间
                long totalTime = 0;
                //总的平均文件传输速率（Kb/s）
                double averageSpeed = 0;
                //根据总的平均传输速率计算的预估的剩余完成时间（秒）
                long averageRemainingTime = 0;
                //文件大小
                long fileSize = fileTransfer.getFileSize();
                //当前的传输进度
                int progress = (int) (total * 100 / fileSize);
                //距离上一次计算进度到现在之间新传输的字节数
                long temp = total - tempTotal;
                if (temp > 0) {
                    instantSpeed = (temp / 1024.0 / PERIOD);
                    instantRemainingTime = (long) ((fileSize - total) / 1024.0 / instantSpeed);
                }
                if (startTime != null) {
                    totalTime = (new Date().getTime() - startTime.getTime()) / 1000;
                    averageSpeed = (total / 1024.0 / totalTime);
                    averageRemainingTime = (long) ((fileSize - total) / 1024.0 / averageSpeed);
                }
                tempTotal = total;
                Logger.e(TAG, "CallbackReceiverService ---------------------------");
                Logger.e(TAG, "CallbackReceiverService  传输进度（%）: " + progress);
                Logger.e(TAG, "CallbackReceiverService  所用时间：" + totalTime);
                Logger.e(TAG, "CallbackReceiverService  瞬时-传输速率（Kb/s）: " + instantSpeed);
                Logger.e(TAG, "CallbackReceiverService  瞬时-预估的剩余完成时间（秒）: " + instantRemainingTime);
                Logger.e(TAG, "CallbackReceiverService  平均-传输速率（Kb/s）: " + averageSpeed);
                Logger.e(TAG, "CallbackReceiverService  平均-预估的剩余完成时间（秒）: " + averageRemainingTime);
                Logger.e(TAG, "CallbackReceiverService  字节变化：" + temp);
                if (progressChangListener != null) {
                    progressChangListener.onProgressChanged(fileTransfer, totalTime, progress, instantSpeed, instantRemainingTime, averageSpeed, averageRemainingTime);
                }
            }
        };
        //每隔 PERIOD 毫秒执行一次任务 runnable（定时任务内部要捕获可能发生的异常，否则如果异常抛出到上层的话，会导致定时任务停止）
        callbackService.scheduleAtFixedRate(runnable, 0, PERIOD, TimeUnit.MILLISECONDS);
    }

    private void stopCallback() {
        running = false;
        if (callbackService != null) {
            if (!callbackService.isShutdown()) {
                callbackService.shutdownNow();
            }
            callbackService = null;
        }
    }

    public static String encodeHexString(byte[] data) {
        StringBuilder sb = new StringBuilder();
        for (byte b : data) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        if (intent != null && ACTION_START_RECEIVE.equals(intent.getAction())) {
            clean();
            File file = null;
            Exception exception = null;
            try {
                serverSocket = new ServerSocket();
                serverSocket.setReuseAddress(true);
                serverSocket.bind(new InetSocketAddress(Constants.PORT));
                Socket client = serverSocket.accept();
                Log.e(TAG, "CallbackReceiverService  客户端IP地址 : " + client.getInetAddress().getHostAddress());
                inputStream = client.getInputStream();

                //TODO 自定义解析数据协议
                fileTransfer = new FileTransfer();
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
                    fileTransfer.setFileName(jsonObject.optString("fileName"));
                    fileTransfer.setFilePath(jsonObject.optString("filePath"));
                    fileTransfer.setFileSize(jsonObject.optLong("fileSize"));
                    fileTransfer.setMd5(jsonObject.optString("md5"));
                    fileTransfer.setJson(jsonObject.optString("json"));
                    fileTransfer.setClientIp(jsonObject.optString("clientIp"));
                    fileTransfer.setServerIp(jsonObject.optString("serverIp"));

                    Log.i("xmg", "#############jsonObject="+jsonObject.toString());
                } catch (Exception e) {
                    e.printStackTrace();
                }


                //获取转换对象
//                try {
//                    objectInputStream = new ObjectInputStream(inputStream);
//                    fileTransfer = (FileTransfer) objectInputStream.readObject();
//                    Log.e(TAG, "CallbackReceiverService  待接收的文件: " + fileTransfer.toString());
//                } catch (Exception e) {
//                    e.printStackTrace();
//                }


                //TODO test 校验
//                fileTransfer.setFileName("iosTestImage.png");
//                fileTransfer.setFilePath("/storage/emulated/0/Android/data/github.leavesc.wififiletransfer/cache/41218518.jpg");
//                fileTransfer.setMd5("fef89a7d476dbc362e00f1c397c661bb");
//                fileTransfer.setFileSize(4628889);
//                fileTransfer.setJson("{\"name\":\"小明\",\"age\":12}");

//                if (fileTransfer == null) {
//                    exception = new Exception("从文件发送端发来的文件模型为null");
//                    return;
//                } else if (TextUtils.isEmpty(fileTransfer.getMd5())) {
//                    exception = new Exception("从文件发送端发来的文件模型不包含MD5码");
//                    return;
//                }

                //TODO test
                String json = fileTransfer.getJson();
                Log.w(TAG, "callback 成功="+json);


                //TODO TEST 发送callback数据
//                outputStream = client.getOutputStream();
//                outputStream.write("123456".getBytes(StandardCharsets.UTF_8));
//                printWriter = new PrintWriter(new BufferedWriter(
//                        new OutputStreamWriter(outputStream, StandardCharsets.UTF_8)), true);
//                printWriter.println("文件接收成功"+"（服务器发送）");
//                outputStream = new DataOutputStream(new BufferedOutputStream(
//                        client.getOutputStream()));
//                outputStream.writeInt(1);
//                outputStream.writeUTF("1111111111111111111");
//                outputStream.flush();


                stopCallback();
                if (progressChangListener != null) {
                    //因为上面在计算文件传输进度时因为小数点问题可能不会显示到100%，所以此处手动将之设为100%
                    progressChangListener.onProgressChanged(fileTransfer, 0, 100, 0, 0, 0, 0);
                    //开始计算传输到本地的文件的MD5码
                    progressChangListener.onStartComputeMD5();
                }
            } catch (Exception e) {
                Log.e(TAG, "CallbackReceiverService  文件接收 Exception: " + e.getMessage());
                exception = e;
            } finally {
                FileTransfer transfer = new FileTransfer();
                if (file != null && file.exists()) {
                    transfer.setFilePath(file.getPath());
                    transfer.setFileSize(file.length());
                    transfer.setMd5(Md5Util.getMd5(file));
                    Log.e(TAG, "CallbackReceiverService  计算出的文件的MD5码是：" + transfer.getMd5());
                }
                if (exception != null) {
                    if (progressChangListener != null) {
                        progressChangListener.onTransferFailed(transfer, exception);
                    }
                } else {
                    if (progressChangListener != null && fileTransfer != null) {
                        if (fileTransfer.getMd5().equals(transfer.getMd5())) {
                            progressChangListener.onTransferSucceed(transfer);
                        } else {
                            //如果本地计算出的MD5码和文件发送端传来的值不一致，则认为传输失败
                            progressChangListener.onTransferFailed(transfer, new Exception("MD5码不一致"));
                        }
                    }
                }


                //TODO TEST 发送callback数据
//                outputStream.write("123456".getBytes(StandardCharsets.UTF_8));
//                printWriter = new PrintWriter(new BufferedWriter(
//                        new OutputStreamWriter(outputStream, StandardCharsets.UTF_8)), true);
//                printWriter.println("文件接收成功"+"（服务器发送）");


                clean();
                //TODO 再次启动服务，等待客户端下次连接
//                startActionTransfer(this);
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
//        if (outputStream != null) {
//            try {
//                outputStream.close();
//                outputStream = null;
//            } catch (IOException e) {
//                e.printStackTrace();
//            }
//        }
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
        stopCallback();
        total = 0;
        tempTotal = 0;
        startTime = null;
//        fileTransfer = null;
    }

    public boolean isRunning() {
        return running;
    }

    public static void startActionTransfer(Context context) {
        Intent intent = new Intent(context, CallbackReceiverService.class);
        intent.setAction(ACTION_START_RECEIVE);
        context.startService(intent);
    }

    public void setProgressChangListener(OnReceiveProgressChangListener progressChangListener) {
        this.progressChangListener = progressChangListener;
    }

}
