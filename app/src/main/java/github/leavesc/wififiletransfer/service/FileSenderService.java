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
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Random;
import java.util.Scanner;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import github.leavesc.wififiletransfer.BuildConfig;
import github.leavesc.wififiletransfer.FileReceiverActivity;
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
public class FileSenderService extends IntentService {

    private Socket socket;

    private OutputStream outputStream;

//    private ObjectOutputStream objectOutputStream;

    private InputStream inputStream;

    private OnSendProgressChangListener progressChangListener;

    private static final String ACTION_START_SEND = BuildConfig.APPLICATION_ID + ".service.action.startSend";

    private static final String EXTRA_PARAM_FILE_TRANSFER = BuildConfig.APPLICATION_ID + ".service.extra.FileUri";

    private static final String EXTRA_PARAM_IP_ADDRESS = BuildConfig.APPLICATION_ID + ".service.extra.IpAddress";

    private static final String EXTRA_PARAM_LOCAL_IP_ADDRESS = BuildConfig.APPLICATION_ID + ".service.extra.Local.IpAddress";

    private static final String EXTRA_PARAM_JSON = BuildConfig.APPLICATION_ID + ".service.extra.Json";

    private static final String TAG = "xmg";

    public interface OnSendProgressChangListener {

        /**
         * 如果待发送的文件还没计算MD5码，则在开始计算MD5码时回调
         */
        void onStartComputeMD5();

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

    public FileSenderService() {
        super("FileSenderService");
    }

    public class MyBinder extends Binder {
        public FileSenderService getService() {
            return FileSenderService.this;
        }
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return new FileSenderService.MyBinder();
    }

    private ScheduledExecutorService callbackService;

    private FileTransfer fileTransfer;

    //总的已传输字节数
    private long total;

    //在上一次更新进度时已传输的文件总字节数
    private long tempTotal = 0;

    //计算瞬时传输速率的间隔时间
    private static final int PERIOD = 400;

    //传输操作开始时间
    private Date startTime;

    private void startCallback() {
        stopCallback();
        startTime = new Date();
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
                Logger.e(TAG, "FileSenderService  ---------------------------");
                Logger.e(TAG, "FileSenderService  传输进度（%）: " + progress);
                Logger.e(TAG, "FileSenderService  所用时间：" + totalTime);
                Logger.e(TAG, "FileSenderService  瞬时-传输速率（Kb/s）: " + instantSpeed);
                Logger.e(TAG, "FileSenderService  瞬时-预估的剩余完成时间（秒）: " + instantRemainingTime);
                Logger.e(TAG, "FileSenderService  平均-传输速率（Kb/s）: " + averageSpeed);
                Logger.e(TAG, "FileSenderService  平均-预估的剩余完成时间（秒）: " + averageRemainingTime);
                Logger.e(TAG, "FileSenderService  字节变化：" + temp);
                if (progressChangListener != null) {
                    progressChangListener.onProgressChanged(fileTransfer, totalTime, progress, instantSpeed, instantRemainingTime, averageSpeed, averageRemainingTime);
                }
            }
        };
        //每隔 PERIOD 毫秒执行一次任务 runnable（定时任务内部要捕获可能发生的异常，否则如果异常抛出到上层的话，会导致定时任务停止）
        callbackService.scheduleAtFixedRate(runnable, 0, PERIOD, TimeUnit.MILLISECONDS);
    }

    private void stopCallback() {
        if (callbackService != null) {
            if (!callbackService.isShutdown()) {
                callbackService.shutdownNow();
            }
            callbackService = null;
        }
    }

    private String getOutputFilePath(Context context, Uri fileUri) throws Exception {
        String outputFilePath = context.getExternalCacheDir().getAbsolutePath() +
                File.separatorChar + new Random().nextInt(10000) +
                new Random().nextInt(10000) + ".jpg";
        File outputFile = new File(outputFilePath);
        if (!outputFile.exists()) {
            outputFile.getParentFile().mkdirs();
            outputFile.createNewFile();
        }
        Uri outputFileUri = Uri.fromFile(outputFile);
        copyFile(context, fileUri, outputFileUri);
        return outputFilePath;
    }

    private void copyFile(Context context, Uri inputUri, Uri outputUri) throws NullPointerException,
            IOException {
        try (InputStream inputStream = context.getContentResolver().openInputStream(inputUri);
             OutputStream outputStream = new FileOutputStream(outputUri.getPath())) {
            if (inputStream == null) {
                throw new NullPointerException("InputStream for given input Uri is null");
            }
            byte[] buffer = new byte[1024];
            int length;
            while ((length = inputStream.read(buffer)) > 0) {
                outputStream.write(buffer, 0, length);
            }
        }
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        if (intent != null && ACTION_START_SEND.equals(intent.getAction())) {
            try {
                clean();
                String localIp = intent.getStringExtra(EXTRA_PARAM_LOCAL_IP_ADDRESS);
                String ipAddress = intent.getStringExtra(EXTRA_PARAM_IP_ADDRESS);
                Log.e(TAG, "FileSenderService  IP地址：" + ipAddress);
                if (TextUtils.isEmpty(ipAddress)) {
                    return;
                }

//                Uri imageUri = Uri.parse(intent.getStringExtra(EXTRA_PARAM_FILE_TRANSFER));
//                String outputFilePath = getOutputFilePath(this, imageUri);
                //将Uri改为Path
                String outputFilePath = intent.getStringExtra(EXTRA_PARAM_FILE_TRANSFER);

                File outputFile = new File(outputFilePath);
                Log.d("xmg", "outputFilePath="+outputFilePath);
                fileTransfer = new FileTransfer();
                fileTransfer.setFileName(outputFile.getName());
                fileTransfer.setFileSize(outputFile.length());
                fileTransfer.setFilePath(outputFilePath);
                fileTransfer.setClientIp(localIp);

                String json = intent.getStringExtra(EXTRA_PARAM_JSON);
                fileTransfer.setJson(json);

                if (TextUtils.isEmpty(fileTransfer.getMd5())) {
                    Logger.e(TAG, "FileSenderService  MD5码为空，开始计算文件的MD5码");
                    if (progressChangListener != null) {
                        progressChangListener.onStartComputeMD5();
                    }
                    fileTransfer.setMd5(Md5Util.getMd5(new File(fileTransfer.getFilePath())));
                    Log.e(TAG, "FileSenderService  计算结束，文件的MD5码值是：" + fileTransfer.getMd5());
                } else {
                    Logger.e(TAG, "FileSenderService  MD5码不为空，无需再次计算，MD5码为：" + fileTransfer.getMd5());
                }
                int index = 0;
                while (ipAddress.equals("0.0.0.0") && index < 5) {
                    Log.e(TAG, "FileSenderService  ip: " + ipAddress);
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
                fileTransfer.setServerIp(ipAddress);
                Log.d("xmg", "ipAddress="+ipAddress+"  FileName="+fileTransfer.getFileName());
                socket = new Socket();
                socket.bind(null);
                socket.connect((new InetSocketAddress(ipAddress, Constants.PORT)), 20000);
                outputStream = socket.getOutputStream();
//                objectOutputStream = new ObjectOutputStream(outputStream);
//                objectOutputStream.writeObject(fileTransfer);
                //TODO 自定义传输协议。 头4字节代表后续json数据长度，接着Json(传递相关数据)，再接着发文件。
                JSONObject jsonObject = new JSONObject();
                jsonObject.put("fileName", fileTransfer.getFileName());
                jsonObject.put("filePath", fileTransfer.getFilePath());
                jsonObject.put("fileSize", fileTransfer.getFileSize());
                jsonObject.put("md5", fileTransfer.getMd5());
                jsonObject.put("json", "{\"otherInfo\":\""+json+"\"}");
                jsonObject.put("clientIp", fileTransfer.getClientIp());
                jsonObject.put("serverIp", fileTransfer.getServerIp());

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

                //TODO TEST
                if(!"callback".equals(fileTransfer.getJson())){
                    inputStream = new FileInputStream(new File(fileTransfer.getFilePath()));
                    startCallback();
                    byte[] buf = new byte[512];
                    int len;
                    while ((len = inputStream.read(buf)) != -1) {
                        outputStream.write(buf, 0, len);
                        total += len;
                    }
                    Log.e(TAG, "FileSenderService  文件发送成功");
                }


                //TODO TEST
//                Scanner scanner = new Scanner(System.in);
//                Scanner in = new Scanner(socket.getInputStream());
//                PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
//                while (scanner.hasNextLine()) {
//                    out.println(scanner.nextLine());
//                    System.out.println("Server Response:"+ in.nextLine());
//                    Log.w("xmg", "Server Response:"+ in.nextLine());
//                }

//                printWriter = new PrintWriter(new BufferedWriter(new OutputStreamWriter(   //步骤二
//                        socket.getOutputStream(), "UTF-8")), true);
//                in = new BufferedReader(new InputStreamReader(socket.getInputStream(), "UTF-8"));
//                receiveMsg();



                stopCallback();
                if (progressChangListener != null) {
                    //因为上面在计算文件传输进度时因为小数点问题可能不会显示到100%，所以此处手动将之设为100%
                    progressChangListener.onProgressChanged(fileTransfer, 0, 100, 0, 0, 0, 0);
                    progressChangListener.onTransferSucceed(fileTransfer);
                }
            } catch (Exception e) {
                e.printStackTrace();
                Log.e(TAG, "FileSenderService  文件发送异常 Exception: " + e.getMessage());
                if (progressChangListener != null) {
                    progressChangListener.onTransferFailed(fileTransfer, e);
                }
            } finally {
                clean();
            }

            EventBus.getDefault().post(new ActionEvent(ActionEvent.TYPE_START_RECEIVER_CALLBACK_SERVICES));

//            startReceiverService(this);
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
//        if (objectOutputStream != null) {
//            try {
//                objectOutputStream.close();
//                objectOutputStream = null;
//            } catch (IOException e) {
//                e.printStackTrace();
//            }
//        }
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
        if (in != null) {
            try {
                in.close();
                in = null;
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        stopCallback();
        total = 0;
        tempTotal = 0;
        startTime = null;
        fileTransfer = null;
    }

    public static void startActionTransfer(Context context, String filePath, String serverIp, String clientIp, String json) {
        Log.d("xmg", "FileSenderService.startActionTransfer  filePath="+filePath
                +"  serverIp="+serverIp+"  clientIp="+clientIp+"  json="+json);
        Intent intent = new Intent();
        intent.setClass(context, FileSenderService.class);
        intent.setAction(ACTION_START_SEND);
        intent.putExtra(EXTRA_PARAM_FILE_TRANSFER, filePath);
        intent.putExtra(EXTRA_PARAM_IP_ADDRESS, serverIp);
        intent.putExtra(EXTRA_PARAM_LOCAL_IP_ADDRESS, clientIp);
        intent.putExtra(EXTRA_PARAM_JSON, json);
        context.startService(intent);
    }


//    private CallbackReceiverService callbackReceiverService;
//    public void startReceiverService(Context context){
//        bindService(new Intent(context, CallbackReceiverService.class), new ServiceConnection() {
//            @Override
//            public void onServiceConnected(ComponentName name, IBinder service) {
//                CallbackReceiverService.MyBinder binder = (CallbackReceiverService.MyBinder) service;
//                callbackReceiverService = binder.getService();
////                fileReceiverService.setProgressChangListener(progressChangListener);
//
//                Toast.makeText(context, "网络连接", Toast.LENGTH_SHORT).show();
//                Log.e(TAG, "FileReceiverActivity  onServiceConnected");
//                if (!callbackReceiverService.isRunning()) {
//                    CallbackReceiverService.startActionTransfer(context);
//                }
//            }
//
//            @Override
//            public void onServiceDisconnected(ComponentName name) {
//                callbackReceiverService = null;
////                bindService(FileReceiverService.class, serviceConnection);
//                Toast.makeText(context, "网络断开", Toast.LENGTH_SHORT).show();
//                Log.e(TAG, "FileReceiverActivity  onServiceDisconnected");
//            }
//        }, Context.BIND_AUTO_CREATE);
//    }

    public void setProgressChangListener(OnSendProgressChangListener progressChangListener) {
        this.progressChangListener = progressChangListener;
    }

}
