package github.leavesc.wififiletransfer.model;

public class ActionEvent {

    public static final int TYPE_RESET_APP = -1; //重启应用

    public static final int TYPE_START_RECEIVER_SERVICES = 1; //启动接收文件服务
    public static final int TYPE_START_SENDER_SERVICES = 2; //启动发送文件服务

    public static final int TYPE_START_RECEIVER_CALLBACK_SERVICES = 3; //启动反馈接收端
    public static final int TYPE_STOP_RECEIVER_CALLBACK_SERVICES = 4; //停止反馈接收端
    public static final int TYPE_START_SENDER_CALLBACK_SERVICES = 5; //启动反馈发送端
    public static final int TYPE_STOP_SENDER_CALLBACK_SERVICES = 6; //停止反馈发送端

    public int type;
    public String action;

    public ActionEvent(int type){
        this.type = type;
    }
    public ActionEvent(int type, String action){
        this.type = type;
        this.action = action;
    }
}
