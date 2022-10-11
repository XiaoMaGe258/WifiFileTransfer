package github.leavesc.wififiletransfer.model;

import androidx.annotation.NonNull;

import java.io.Serializable;

/**
 * @Author: leavesC
 * @Date: 2018/4/3 15:10
 * @Desc:
 * @Github：https://github.com/leavesC
 */
public class FileTransfer implements Serializable {

    //文件名
    private String fileName;

    //文件路径
    private String filePath;

    //文件大小
    private long fileSize;

    //MD5码
    private String md5;

    //json数据
    private String json;

    //服务端Ip
    private String serverIp;

    //客户端Ip
    private String clientIp;

    public FileTransfer() {

    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public String getFilePath() {
        return filePath;
    }

    public void setFilePath(String filePath) {
        this.filePath = filePath;
    }

    public long getFileSize() {
        return fileSize;
    }

    public void setFileSize(long fileSize) {
        this.fileSize = fileSize;
    }

    public String getMd5() {
        return md5;
    }

    public void setMd5(String md5) {
        this.md5 = md5;
    }

    public String getJson() {
        return json;
    }

    public void setJson(String json) {
        this.json = json;
    }

    public String getServerIp() {
        return serverIp;
    }

    public void setServerIp(String serverIp) {
        this.serverIp = serverIp;
    }

    public String getClientIp() {
        return clientIp;
    }

    public void setClientIp(String clientIp) {
        this.clientIp = clientIp;
    }

    @NonNull
    @Override
    public String toString() {
        return "FileTransfer{" +
                "fileName='" + fileName + '\'' +
                ", filePath='" + filePath + '\'' +
                ", fileSize=" + fileSize +
                ", md5='" + md5 + '\'' +
                ", json='" + json + '\'' +
                ", serverIp='" + serverIp + '\'' +
                ", clientIp='" + clientIp + '\'' +
                '}';
    }

}
