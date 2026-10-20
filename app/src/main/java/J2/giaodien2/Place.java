package J2.giaodien2;

public class Place {
    private String name;
    private String address;
    private String category;
    private int imageResId;

    public Place(String name, String address, String category, int imageResId) {
        this.name = name;
        this.address = address;
        this.category = category;
        this.imageResId = imageResId;
    }

    public String getName() { return name; }
    public String getAddress() { return address; }
    public String getCategory() { return category; }
    public int getImageResId() { return imageResId; }
}