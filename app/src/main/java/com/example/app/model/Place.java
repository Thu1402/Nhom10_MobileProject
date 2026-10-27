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
    public String title;
    public String subtitle;
    public String openingHours = "";
    public String phone = "";
    public String website = "";
    public String email = "";
    public String extraInfo = "";
    public float distanceMeters;
    public int fameScore;
    public int savedPlaceIndex = -1;
    public int shareCount;

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
        this.title = name == null ? "" : name;
        this.subtitle = address == null ? "" : address;
    }

    public boolean hasCoordinates() {
        return !Double.isNaN(latitude) && !Double.isNaN(longitude)
                && latitude >= -90 && latitude <= 90 && longitude >= -180 && longitude <= 180;
    }

    public static Place savedSearchResult(int index, Place place) {
        Place result = new Place(place.id, place.ownerId, place.name, place.category, place.address,
                place.distance, place.imageUrl, place.description, place.tags, place.latitude, place.longitude);
        result.savedPlaceIndex = index;
        return result;
    }

    public static Place addressSearchResult(String title, String subtitle, double latitude, double longitude) {
        Place result = new Place("", "", title, "", subtitle, "", "", "", "", latitude, longitude);
        result.title = title == null ? "" : title;
        result.subtitle = subtitle == null ? "" : subtitle;
        return result;
    }

    public static Place nearby(String name, String category, double latitude, double longitude,
                               float distanceMeters, String address, String openingHours, String phone,
                               String website, String email, String extraInfo, int fameScore) {
        Place result = new Place("", "", name, category, address, String.valueOf(distanceMeters),
                "", extraInfo, "", latitude, longitude);
        result.distanceMeters = distanceMeters;
        result.openingHours = openingHours == null ? "" : openingHours;
        result.phone = phone == null ? "" : phone;
        result.website = website == null ? "" : website;
        result.email = email == null ? "" : email;
        result.extraInfo = extraInfo == null ? "" : extraInfo;
        result.fameScore = fameScore;
        return result;
    }
}
