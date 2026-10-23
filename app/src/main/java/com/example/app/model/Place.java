package com.example.app.model;

public class Place {
    public final String id;
    public final String ownerId;
    public final String name;
    public final String category;
    public final String address;
    public final String distance;
    public final String imageUrl;
    public final String description;
    public final String tags;
    public final double latitude;
    public final double longitude;

    public Place(String name, String category, String address, String distance, String imageUrl) {
        this("", name, category, address, distance, imageUrl, "", category);
    }

    public Place(String name, String category, String address, String distance, String imageUrl, String description) {
        this("", name, category, address, distance, imageUrl, description, category);
    }

    public Place(String name, String category, String address, String distance, String imageUrl, String description, String tags) {
        this("", name, category, address, distance, imageUrl, description, tags);
    }

    public Place(String id, String name, String category, String address, String distance, String imageUrl, String description, String tags) {
        this(id, "", name, category, address, distance, imageUrl, description, tags);
    }

    public Place(String id, String ownerId, String name, String category, String address, String distance, String imageUrl, String description, String tags) {
        this(id, ownerId, name, category, address, distance, imageUrl, description, tags,
                Double.NaN, Double.NaN);
    }

    public Place(String id, String ownerId, String name, String category, String address, String distance,
                 String imageUrl, String description, String tags, double latitude, double longitude) {
        this.id = id == null ? "" : id;
        this.ownerId = ownerId == null ? "" : ownerId;
        this.name = name;
        this.category = category;
        this.address = address;
        this.distance = distance;
        this.imageUrl = imageUrl;
        this.description = description;
        this.tags = tags == null || tags.trim().isEmpty() ? category : tags.trim();
        this.latitude = latitude;
        this.longitude = longitude;
    }

    public boolean hasCoordinates() {
        return !Double.isNaN(latitude) && !Double.isNaN(longitude)
                && latitude >= -90 && latitude <= 90 && longitude >= -180 && longitude <= 180;
    }
}
