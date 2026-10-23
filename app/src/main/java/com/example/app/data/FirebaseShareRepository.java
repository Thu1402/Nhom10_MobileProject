package com.example.app.data;

import android.content.Context;
import com.example.app.share.Friend;
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
        void onResult(List<SharedPlace> shares);
        void onError(Exception error);
    }

    public static final class SharedPlace {
        public final String placeId;
        public final String placeName;
        public final String senderUid;

        SharedPlace(String placeId, String placeName, String senderUid) {
            this.placeId = placeId;
            this.placeName = placeName;
            this.senderUid = senderUid;
        }
    }

    private FirebaseShareRepository() {}

    public static void share(Context context, String placeId, String placeName,
                             List<Friend> recipients, Callback callback) {
        if (FirebaseApp.getApps(context.getApplicationContext()).isEmpty()) {
            callback.onError(new IllegalStateException("Firebase chưa được khởi tạo."));
            return;
        }
        FirebaseSession.ensureSignedIn(context, new FirebaseSession.Callback() {
            @Override public void onSuccess() {
                saveBatch(recipients, placeId, placeName, callback);
            }
            @Override public void onError(Exception error) { callback.onError(error); }
        });
    }

    public static ListenerRegistration loadLatest(Context context, RecentShareCallback callback) {
        ListenerHolder holder = new ListenerHolder();
        FirebaseSession.ensureSignedIn(context, new FirebaseSession.Callback() {
            @Override public void onSuccess() {
                if (holder.removed) return;
                String uid = FirebaseAuth.getInstance().getCurrentUser().getUid();
                holder.delegate = FirebaseFirestore.getInstance().collection("shares")
                        .whereEqualTo("senderUid", uid)
                        .addSnapshotListener((snapshot, error) -> {
                            if (error != null) {
                                callback.onError(error);
                                return;
                            }
                            callback.onResult(latestValue(snapshot, "placeName"),
                                    latestValue(snapshot, "recipientName"));
                        });
                if (holder.removed) holder.delegate.remove();
            }
            @Override public void onError(Exception error) { callback.onError(error); }
        });
        return holder;
    }

    public static ListenerRegistration listenToCount(Context context, CountCallback callback) {
        ListenerHolder holder = new ListenerHolder();
        FirebaseSession.ensureSignedIn(context, new FirebaseSession.Callback() {
            @Override public void onSuccess() {
                if (holder.removed) return;
                String uid = FirebaseAuth.getInstance().getCurrentUser().getUid();
                holder.delegate = FirebaseFirestore.getInstance().collection("shares")
                        .whereEqualTo("senderUid", uid)
                        .addSnapshotListener((snapshot, error) -> {
                            if (error != null) callback.onError(error);
                            else callback.onResult(snapshot == null ? 0 : snapshot.size());
                        });
                if (holder.removed) holder.delegate.remove();
            }
            @Override public void onError(Exception error) { callback.onError(error); }
        });
        return holder;
    }

    public static ListenerRegistration listenReceived(Context context, ReceivedCallback callback) {
        ListenerHolder holder = new ListenerHolder();
        FirebaseSession.ensureSignedIn(context, new FirebaseSession.Callback() {
            @Override public void onSuccess() {
                if (holder.removed) return;
                String uid = FirebaseAuth.getInstance().getCurrentUser().getUid();
                holder.delegate = FirebaseFirestore.getInstance().collection("shares")
                        .whereEqualTo("recipientUid", uid)
                        .addSnapshotListener((snapshot, error) -> {
                            if (error != null) {
                                callback.onError(error);
                                return;
                            }
                            List<SharedPlace> shares = new ArrayList<>();
                            if (snapshot != null) {
                                for (DocumentSnapshot document : snapshot.getDocuments()) {
                                    String placeId = stringValue(document, "placeId");
                                    String placeName = stringValue(document, "placeName");
                                    String senderUid = stringValue(document, "senderUid");
                                    if (!placeId.isEmpty()) shares.add(new SharedPlace(placeId, placeName, senderUid));
                                }
                            }
                            callback.onResult(Collections.unmodifiableList(shares));
                        });
                if (holder.removed) holder.delegate.remove();
            }
            @Override public void onError(Exception error) { callback.onError(error); }
        });
        return holder;
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

    private static final class ListenerHolder implements ListenerRegistration {
        private volatile ListenerRegistration delegate;
        private volatile boolean removed;
        @Override public void remove() {
            removed = true;
            ListenerRegistration current = delegate;
            if (current != null) current.remove();
        }
    }
}
