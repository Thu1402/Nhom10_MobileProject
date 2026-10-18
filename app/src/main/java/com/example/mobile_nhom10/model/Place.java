package com.example.mobile_nhom10.model;

public class Place {
    public final String name;
    public final String category;
    public final String address;
    public final String distance;
    public final String imageUrl;
    public final String description;

    public Place(String name, String category, String address, String distance, String imageUrl) {
        this(name, category, address, distance, imageUrl, "Một góc nhỏ giữa rừng thông, có cà phê thơm và khoảng sân đầy nắng. Hợp để đọc sách buổi sáng.");
    }

    public Place(String name, String category, String address, String distance, String imageUrl, String description) {
        this.name = name;
        this.category = category;
        this.address = address;
        this.distance = distance;
        this.imageUrl = imageUrl;
        this.description = description;
    }
}