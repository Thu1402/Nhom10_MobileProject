package J2.giaodien2;

public class Friend {
    private String id;
    private String name;
    private String subtitle;
    private int avatarResId;
    private boolean isSelected;

    public Friend(String id, String name, String subtitle, int avatarResId) {
        this.id = id;
        this.name = name;
        this.subtitle = subtitle;
        this.avatarResId = avatarResId;
        this.isSelected = false;
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public String getSubtitle() { return subtitle; }
    public int getAvatarResId() { return avatarResId; }
    public boolean isSelected() { return isSelected; }
    public void setSelected(boolean selected) { isSelected = selected; }
}