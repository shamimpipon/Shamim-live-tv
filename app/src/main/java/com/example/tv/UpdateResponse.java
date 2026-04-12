package com.example.tv;

public class UpdateResponse {
    private int versionCode;
    private String versionName;
    private String apkUrl;
    private String updateMessage;

    public int getVersionCode() { return versionCode; }
    public String getVersionName() { return versionName; }
    public String getApkUrl() { return apkUrl; }
    public String getUpdateMessage() { return updateMessage; }
}