package github.leavesc.wififiletransfer;

import android.Manifest;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.util.Log;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;

/**
 * @Author: leavesC
 * @Date: 2018/4/3 14:56
 * @Desc:
 * @Github：https://github.com/leavesC
 */
public class MainActivity extends BaseActivity {

    private static final int CODE_REQ_PERMISSIONS = 665;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == CODE_REQ_PERMISSIONS) {
            for (int grantResult : grantResults) {
                if (grantResult != PackageManager.PERMISSION_GRANTED) {
                    showToast("缺少权限，请先授予权限");
                    return;
                }
            }
            showToast("已获得权限");
        }
    }

    public void checkPermission(View view) {
        ActivityCompat.requestPermissions(MainActivity.this,
                new String[]{Manifest.permission.CHANGE_NETWORK_STATE,
                        Manifest.permission.ACCESS_NETWORK_STATE,
                        Manifest.permission.WRITE_EXTERNAL_STORAGE,
                        Manifest.permission.READ_EXTERNAL_STORAGE,
                        Manifest.permission.ACCESS_WIFI_STATE,
                        Manifest.permission.CHANGE_WIFI_STATE,
                        Manifest.permission.ACCESS_FINE_LOCATION}, CODE_REQ_PERMISSIONS);
    }

    public void startFileSenderActivity(View view) {
        startActivity(FileSenderActivity.class);
    }

    public void startFileReceiverActivity(View view) {
        startActivity(FileReceiverActivity.class);
    }

    public void createAp(View view) {


        Intent it = new Intent();
        ComponentName cn = new ComponentName("com.android.settings","com.android.settings.wifi.WifiSettings");
        it.setComponent(cn);
        startActivity(it);

//        WifiApManager wifiApManager = new WifiApManager(this);
//        wifiApManager.createWifiAp("newAp", "123456789");
//        Log.w("xmg", "wifi enabled ="+wifiApManager.isWifiApEnabled());
//        if(!wifiApManager.isWifiApEnabled()){
//            wifiApManager.openWifiAp();
//        }
//        wifiApManager.getWifiApInfo();
    }


    public static String encodeHexString(byte[] data) {
        StringBuilder sb = new StringBuilder();
        for (byte b : data) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}