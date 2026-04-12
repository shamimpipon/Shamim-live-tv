package com.example.tv;

public class PlaylistModel {
    private String name;
    private String url;

    public PlaylistModel(String name, String url) {
        this.name = name;
        this.url = url;
    }

    public String getName() { return name; }
    public String getUrl() { return url; }
}
