package com.example.app.share;

public class Friend {
    private String id;
    private String name;
    private String subtitle;
    private String avatarUrl;
    private boolean isSelected;
    private String relationshipStatus = "none";

    public Friend(String id, String name, String subtitle, String avatarUrl) {
        this.id = id;
        this.name = name;
        this.subtitle = subtitle;
        this.avatarUrl = avatarUrl == null ? "" : avatarUrl;
        this.isSelected = false;
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public String getSubtitle() { return subtitle; }
    public String getAvatarUrl() { return avatarUrl; }
    public boolean isSelected() { return isSelected; }
    public void setSelected(boolean selected) { isSelected = selected; }
    public String getRelationshipStatus() { return relationshipStatus; }
    public void setRelationshipStatus(String status) {
        relationshipStatus = status == null ? "none" : status;
    }
}

