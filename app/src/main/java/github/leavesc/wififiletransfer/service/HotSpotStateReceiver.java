package github.leavesc.wififiletransfer.service;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.text.TextUtils;
import android.util.Log;

import java.util.ArrayList;

public class HotSpotStateReceiver extends BroadcastReceiver {

    //某些华为鸿蒙机型(比如JEF-AN00)需要兼容处理
    public static boolean isCompatCheckApOpen = false;

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        if ("android.net.wifi.WIFI_AP_STATE_CHANGED".equals(action)) {
            switch (intent.getIntExtra("wifi_state", 0)){
                //便携式热点的状态为：10---正在关闭；11---已关闭；12---正在开启；13---已开启
                case 10:
                    //正在关闭
                    Log.e("xmg", "正在关闭");
                    break;
                case 11:
                    //已关闭
                    Log.e("xmg", "已关闭");
                    break;
                case 12:
                    //正在开启
                    Log.e("xmg", "正在开启");
                    break;
                case 13:
                    //已开启
                    Log.e("xmg", "已开启");
                    break;
            }

        } else if ("android.net.conn.TETHER_STATE_CHANGED".equals(action)) {
            ArrayList<String> available = intent.getStringArrayListExtra("availableArray");
            ArrayList<String> active = intent.getStringArrayListExtra("tetherArray");
            ArrayList<String> error = intent.getStringArrayListExtra("erroredArray");
            HotSpotStateReceiver.isCompatCheckApOpen = false;
            if(active != null && active.size() > 0){

                for(String it : active){
                    if(!TextUtils.isEmpty(it) && (it.contains("p2p0") || (it.contains("ap0")))){
                        HotSpotStateReceiver.isCompatCheckApOpen = true;
                    }
                }
            }
            //end of if
            Log.e("xmg", "HotSpotStateReceiver==TETHER_STATE_CHANGED");
            Log.e("xmg", "available="+getStringArray(available));
            Log.e("xmg", "active="+getStringArray(active));
            Log.e("xmg", "error="+getStringArray(error));
            Log.e("xmg", "isCompatCheckApOpen="+HotSpotStateReceiver.isCompatCheckApOpen);

        }
    }

    private String getStringArray(ArrayList<String> array){
        if(array == null){
            return "null";
        }
        StringBuilder sb = new StringBuilder();
        for(String str : array){
            sb.append(str).append(",");
        }
        return sb.toString();
    }

}



/*
class HotSpotStateReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val action = intent.action
            if (action == "android.net.wifi.WIFI_AP_STATE_CHANGED") {
            when(intent.getIntExtra("wifi_state", 0)){//便携式热点的状态为：10---正在关闭；11---已关闭；12---正在开启；13---已开启
            10 -> EventBus.getDefault().post(MsgOperationEvent(MsgOperationEvent.MsgHotSpotChangeState, -1))//正在关闭
            11 -> EventBus.getDefault().post(MsgOperationEvent(MsgOperationEvent.MsgHotSpotChangeState, 0))//已关闭
            12 -> EventBus.getDefault().post(MsgOperationEvent(MsgOperationEvent.MsgHotSpotChangeState, -2))//正在开启
            13 -> EventBus.getDefault().post(MsgOperationEvent(MsgOperationEvent.MsgHotSpotChangeState, 1))//已开启
            }
            } else if (action == "android.net.conn.TETHER_STATE_CHANGED") {
            val available = intent.getStringArrayListExtra("availableArray")
            val active = intent.getStringArrayListExtra("tetherArray")
            val error = intent.getStringArrayListExtra("erroredArray")
            UtilsJava.isCompatCheckApOpen = false
            if(!active.isNullOrEmpty()){
            active.forEach {
            if(!it.isNullOrBlank() && (it.contains("p2p0") || (it.contains("ap0"))))UtilsJava.isCompatCheckApOpen = true
            }
            }//end of if
            Utils.debugPrintln("======Stephen=======HotSpotStateReceiver==TETHER_STATE_CHANGED====available:$available===active:$active====>" +
            "error:$error===>isCompatCheckApOpen:${UtilsJava.isCompatCheckApOpen}")
            EventBus.getDefault().post(MsgOperationEvent(MsgOperationEvent.MsgHotSpotChangeState, if(UtilsJava.isCompatCheckApOpen) 1 else 0))
            }
            Utils.debugPrintln("======Stephen=======HotSpotStateReceiver==>"+UtilsJava.isHotSpotApOpen(context)+"/"+UtilsJava.isHotSpotApOpen2(context))
        }
}*/
