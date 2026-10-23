package com.example.app.model;

public class UserProfile {
    public final String uid;
    public final String name;
    public final String username;
    public final String bio;
    public final String imageUrl;

    public UserProfile(String name, String username, String bio, String imageUrl) {
        this("", name, username, bio, imageUrl);
    }

    public UserProfile(String uid, String name, String username, String bio, String imageUrl) {
        this.uid = uid == null ? "" : uid;
        this.name = name == null ? "" : name;
        this.username = username == null ? "" : username;
        this.bio = bio == null ? "" : bio;
        this.imageUrl = imageUrl == null ? "" : imageUrl;
    }
}
