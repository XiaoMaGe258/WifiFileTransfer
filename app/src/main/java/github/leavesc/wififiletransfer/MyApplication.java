package github.leavesc.wififiletransfer;

import android.app.Application;

import github.leavesc.wififiletransfer.common.CrashHandler;

public class MyApplication extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        CrashHandler.getInstance().init(getApplicationContext());
    }
}
