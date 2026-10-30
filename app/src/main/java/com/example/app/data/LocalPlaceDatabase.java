package com.example.app.data;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import com.example.app.model.Place;

import java.util.ArrayList;
import java.util.List;

/** Durable cache of places already fetched from Firestore. */
public final class LocalPlaceDatabase extends SQLiteOpenHelper {
    private static final String DB_NAME = "places_cache.db";
    private static final int DB_VERSION = 1;
    private static volatile LocalPlaceDatabase instance;

    public static LocalPlaceDatabase getInstance(Context context) {
        if (instance == null) {
            synchronized (LocalPlaceDatabase.class) {
                if (instance == null) instance = new LocalPlaceDatabase(context.getApplicationContext());
            }
        }
        return instance;
    }

    private LocalPlaceDatabase(Context context) { super(context, DB_NAME, null, DB_VERSION); }

    @Override public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE places (id TEXT PRIMARY KEY NOT NULL, owner_id TEXT, name TEXT, category TEXT, "
                + "address TEXT, distance TEXT, image_url TEXT, description TEXT, tags TEXT, latitude REAL, longitude REAL)");
    }

    @Override public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // Keep this database schema backward compatible as new columns are added.
    }

    public synchronized void replaceOwnerPlaces(String ownerId, List<Place> places) {
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            db.delete("places", "owner_id=?", new String[]{ownerId});
            for (Place place : places) put(db, place);
            db.setTransactionSuccessful();
        } finally { db.endTransaction(); }
    }

    public synchronized void put(Place place) { put(getWritableDatabase(), place); }

    private void put(SQLiteDatabase db, Place place) {
        if (place == null || place.id == null || place.id.isEmpty()) return;
        ContentValues values = new ContentValues();
        values.put("id", place.id); values.put("owner_id", place.ownerId); values.put("name", place.name);
        values.put("category", place.category); values.put("address", place.address); values.put("distance", place.distance);
        values.put("image_url", place.imageUrl); values.put("description", place.description); values.put("tags", place.tags);
        if (place.hasCoordinates()) { values.put("latitude", place.latitude); values.put("longitude", place.longitude); }
        db.insertWithOnConflict("places", null, values, SQLiteDatabase.CONFLICT_REPLACE);
    }

    public synchronized List<Place> getOwnerPlaces(String ownerId) { return query("owner_id=?", new String[]{ownerId}); }

    public synchronized List<Place> getAllPlaces() { return query(null, null); }

    public synchronized void replaceAllPlaces(List<Place> places) {
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            db.delete("places", null, null);
            for (Place place : places) put(db, place);
            db.setTransactionSuccessful();
        } finally { db.endTransaction(); }
    }

    public synchronized Place getPlace(String id) {
        List<Place> result = query("id=?", new String[]{id});
        return result.isEmpty() ? null : result.get(0);
    }

    public synchronized void deletePlace(String id) {
        getWritableDatabase().delete("places", "id=?", new String[]{id});
    }

    private List<Place> query(String selection, String[] args) {
        List<Place> result = new ArrayList<>();
        try (Cursor c = getReadableDatabase().query("places", null, selection, args, null, null, "name COLLATE NOCASE")) {
            while (c.moveToNext()) {
                result.add(new Place(c.getString(c.getColumnIndexOrThrow("id")), c.getString(c.getColumnIndexOrThrow("owner_id")),
                        c.getString(c.getColumnIndexOrThrow("name")), c.getString(c.getColumnIndexOrThrow("category")),
                        c.getString(c.getColumnIndexOrThrow("address")), c.getString(c.getColumnIndexOrThrow("distance")),
                        c.getString(c.getColumnIndexOrThrow("image_url")), c.getString(c.getColumnIndexOrThrow("description")),
                        c.getString(c.getColumnIndexOrThrow("tags")), c.isNull(c.getColumnIndexOrThrow("latitude")) ? Double.NaN : c.getDouble(c.getColumnIndexOrThrow("latitude")),
                        c.isNull(c.getColumnIndexOrThrow("longitude")) ? Double.NaN : c.getDouble(c.getColumnIndexOrThrow("longitude"))));
            }
        }
        return result;
    }
}
