package com.example.app.data;

import com.example.app.App;
import android.content.Context;
import com.example.app.share.Friend;
import com.example.app.model.Place;
import com.google.firebase.FirebaseApp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QuerySnapshot;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.WriteBatch;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

public final class FirebaseShareRepository {
    public interface Callback {
        void onSuccess();
        void onError(Exception error);
    }

    public interface RecentShareCallback {
        void onResult(String placeName, String recipientName);
        void onError(Exception error);
    }

    public interface CountCallback {
        void onResult(int count);
        void onError(Exception error);
    }

    public interface ReceivedCallback {
        void onResult(List<Place> shares);
        void onError(Exception error);
    }

    private FirebaseShareRepository() {}

    public static void share(Context context, String placeId, String placeName,
                             List<Friend> recipients, Callback callback) {
        if (FirebaseApp.getApps(context.getApplicationContext()).isEmpty()) {
            callback.onError(new IllegalStateException("Firebase chưa được khởi tạo."));
            return;
        }
        App.ensureSignedIn(context, new App.SessionCallback() {
            @Override public void onSuccess() {
                saveBatch(recipients, placeId, placeName, callback);
            }
            @Override public void onError(Exception error) { callback.onError(error); }
        });
    }

    public static void shareMultiple(Context context, List<Place> places,
                                     List<Friend> recipients, Callback callback) {
        if (FirebaseApp.getApps(context.getApplicationContext()).isEmpty()) {
            callback.onError(new IllegalStateException("Firebase chưa được khởi tạo."));
            return;
        }
        if (places == null || places.isEmpty() || recipients == null || recipients.isEmpty()) {
            callback.onError(new IllegalArgumentException("Chọn ít nhất một địa điểm và một người bạn."));
            return;
        }
        if ((long) places.size() * recipients.size() > 450) {
            callback.onError(new IllegalArgumentException("Số địa điểm và người nhận vượt giới hạn chia sẻ một lần."));
            return;
        }
        App.ensureSignedIn(context, new App.SessionCallback() {
            @Override public void onSuccess() {
                FirebaseAuth auth = FirebaseAuth.getInstance();
                String senderUid = auth.getCurrentUser().getUid();
                FirebaseFirestore db = FirebaseFirestore.getInstance();
                WriteBatch batch = db.batch();
                for (Place place : places) {
                    for (Friend friend : recipients) {
                        Map<String, Object> share = new HashMap<>();
                        share.put("senderUid", senderUid);
                        share.put("recipientUid", friend.getId());
                        share.put("recipientName", friend.getName());
                        share.put("placeId", place.id);
                        share.put("placeName", place.name);
                        share.put("sharedAt", FieldValue.serverTimestamp());
                        batch.set(db.collection("shares").document(), share);
                    }
                }
                batch.commit().addOnSuccessListener(unused -> callback.onSuccess())
                        .addOnFailureListener(callback::onError);
            }
            @Override public void onError(Exception error) { callback.onError(error); }
        });
    }

    public static ListenerRegistration loadLatest(Context context, RecentShareCallback callback) {
        AtomicBoolean removed = new AtomicBoolean();
        AtomicReference<ListenerRegistration> delegate = new AtomicReference<>();
        App.ensureSignedIn(context, new App.SessionCallback() {
            @Override public void onSuccess() {
                if (removed.get()) return;
                String uid = FirebaseAuth.getInstance().getCurrentUser().getUid();
                ListenerRegistration registration = FirebaseFirestore.getInstance().collection("shares")
                        .whereEqualTo("senderUid", uid)
                        .addSnapshotListener((snapshot, error) -> {
                            if (error != null) {
                                callback.onError(error);
                                return;
                            }
                            callback.onResult(latestValue(snapshot, "placeName"),
                                    latestValue(snapshot, "recipientName"));
                        });
                delegate.set(registration);
                if (removed.get()) registration.remove();
            }
            @Override public void onError(Exception error) { callback.onError(error); }
        });
        return () -> {
            removed.set(true);
            ListenerRegistration registration = delegate.get();
            if (registration != null) registration.remove();
        };
    }

    public static ListenerRegistration listenToCount(Context context, CountCallback callback) {
        AtomicBoolean removed = new AtomicBoolean();
        AtomicReference<ListenerRegistration> delegate = new AtomicReference<>();
        App.ensureSignedIn(context, new App.SessionCallback() {
            @Override public void onSuccess() {
                if (removed.get()) return;
                String uid = FirebaseAuth.getInstance().getCurrentUser().getUid();
                ListenerRegistration registration = FirebaseFirestore.getInstance().collection("shares")
                        .whereEqualTo("senderUid", uid)
                        .addSnapshotListener((snapshot, error) -> {
                            if (error != null) callback.onError(error);
                            else callback.onResult(snapshot == null ? 0 : snapshot.size());
                        });
                delegate.set(registration);
                if (removed.get()) registration.remove();
            }
            @Override public void onError(Exception error) { callback.onError(error); }
        });
        return () -> {
            removed.set(true);
            ListenerRegistration registration = delegate.get();
            if (registration != null) registration.remove();
        };
    }

    public static ListenerRegistration listenReceived(Context context, ReceivedCallback callback) {
        AtomicBoolean removed = new AtomicBoolean();
        AtomicReference<ListenerRegistration> delegate = new AtomicReference<>();
        App.ensureSignedIn(context, new App.SessionCallback() {
            @Override public void onSuccess() {
                if (removed.get()) return;
                String uid = FirebaseAuth.getInstance().getCurrentUser().getUid();
                ListenerRegistration registration = FirebaseFirestore.getInstance().collection("shares")
                        .whereEqualTo("recipientUid", uid)
                        .addSnapshotListener((snapshot, error) -> {
                            if (error != null) {
                                callback.onError(error);
                                return;
                            }
                            List<Place> shares = new ArrayList<>();
                            if (snapshot != null) {
                                for (DocumentSnapshot document : snapshot.getDocuments()) {
                                    String placeId = stringValue(document, "placeId");
                                    String placeName = stringValue(document, "placeName");
                                    String senderUid = stringValue(document, "senderUid");
                                    if (!placeId.isEmpty()) {
                                        shares.add(new Place(placeId, senderUid, placeName, "", "", "", "", "", ""));
                                    }
                                }
                            }
                            callback.onResult(Collections.unmodifiableList(shares));
                        });
                delegate.set(registration);
                if (removed.get()) registration.remove();
            }
            @Override public void onError(Exception error) { callback.onError(error); }
        });
        return () -> {
            removed.set(true);
            ListenerRegistration registration = delegate.get();
            if (registration != null) registration.remove();
        };
    }

    private static String stringValue(DocumentSnapshot document, String field) {
        Object value = document.get(field);
        return value instanceof String ? (String) value : "";
    }

    private static String latestValue(QuerySnapshot snapshot, String field) {
        DocumentSnapshot latest = null;
        Timestamp latestTime = null;
        if (snapshot != null) {
            for (DocumentSnapshot document : snapshot.getDocuments()) {
                Object rawTime = document.get("sharedAt");
                Timestamp time = rawTime instanceof Timestamp ? (Timestamp) rawTime : null;
                if (latest == null || (time != null && (latestTime == null || time.compareTo(latestTime) > 0))) {
                    latest = document;
                    latestTime = time;
                }
            }
        }
        if (latest == null) return "";
        Object value = latest.get(field);
        return value instanceof String ? (String) value : "";
    }

    private static void saveBatch(List<Friend> recipients, String placeId, String placeName, Callback callback) {
        FirebaseAuth auth = FirebaseAuth.getInstance();
        String senderUid = auth.getCurrentUser().getUid();
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        WriteBatch batch = db.batch();
        for (Friend friend : recipients) {
            Map<String, Object> share = new HashMap<>();
            share.put("senderUid", senderUid);
            share.put("recipientUid", friend.getId());
            share.put("recipientName", friend.getName());
            share.put("placeId", placeId == null ? "" : placeId);
            share.put("placeName", placeName == null ? "Danh sách địa điểm yêu thích" : placeName);
            share.put("sharedAt", FieldValue.serverTimestamp());
            batch.set(db.collection("shares").document(), share);
        }
        batch.commit().addOnSuccessListener(unused -> callback.onSuccess())
                .addOnFailureListener(callback::onError);
    }

}
