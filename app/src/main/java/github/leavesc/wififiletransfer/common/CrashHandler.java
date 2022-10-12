package github.leavesc.wififiletransfer.common;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.util.Log;

import org.greenrobot.eventbus.EventBus;

import github.leavesc.wififiletransfer.model.ActionEvent;

public class CrashHandler implements Thread.UncaughtExceptionHandler {
    //系统默认UncaughtExceptionHandler
    private Thread.UncaughtExceptionHandler mDefaultHandler;
    private Context mContext;
    private static CrashHandler mInstance;
    private String DeviceUrl = "xxx";//上传错误网址
    //空的构造方法
    private CrashHandler() {

    }

    //单例，获取CrashHandler实例
    public static synchronized CrashHandler getInstance() {
        if (null == mInstance) {
            mInstance = new CrashHandler();
        }
        return mInstance;
    }

    public void init(Context context) {
        mContext = context;
        mDefaultHandler = Thread.getDefaultUncaughtExceptionHandler();
        //设置该CrashHandler为系统默认的
        Thread.setDefaultUncaughtExceptionHandler(this);
    }

    //uncaughtException 回调函数
    @Override
    public void uncaughtException(Thread thread, Throwable ex) {

        Log.e("xmg", "################ restart app ################");
        EventBus.getDefault().post(new ActionEvent(ActionEvent.TYPE_RESET_APP));

//        if (!handleException(ex) && mDefaultHandler != null) {
//            //如果自己没处理交给系统处理
//            mDefaultHandler.uncaughtException(thread, ex);
//        }
    }

    //收集错误信息.发送到服务器, 处理了该异常返回true, 否则false
    private boolean handleException(Throwable ex) {
        if (ex == null) {
            return false;
        }

        return true;
    }

    //获取SN号
    @SuppressLint("MissingPermission")
    public static String getSN() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            return Build.getSerial();
        } else
            return Build.SERIAL;
    }

    //获取版本号
    public static String packageName(Context context) {
        PackageManager manager = context.getPackageManager();
        String name = null;
        try {
            PackageInfo info = manager.getPackageInfo(context.getPackageName(), 0);
            name = info.versionName;
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
        }

        return name;
    }
}