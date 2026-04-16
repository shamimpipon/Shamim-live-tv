package com.example.tv;

import java.io.Serializable;

public class Channel implements Serializable {
    private String name;
    private String url;
    private String logoUrl;
    private boolean isOnline = false;
    private boolean isChecking = true;

    public Channel() {
    }

    public Channel(String name, String url, String logoUrl) {
        this.name = name;
        this.url = url;
        this.logoUrl = logoUrl;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getLogoUrl() {
        return logoUrl;
    }

    public void setLogoUrl(String logoUrl) {
        this.logoUrl = logoUrl;
    }

    public boolean isOnline() {
        return isOnline;
    }

    public void setOnline(boolean online) {
        isOnline = online;
        this.isChecking = false;
    }

    public boolean isChecking() {
        return isChecking;
    }

    public void setChecking(boolean checking) {
        isChecking = checking;
    }
}
