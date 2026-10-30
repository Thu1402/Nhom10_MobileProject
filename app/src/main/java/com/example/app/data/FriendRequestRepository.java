package com.example.app.data;

import com.example.app.App;
import android.content.Context;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.SetOptions;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class FriendRequestRepository {
    public static final String NONE = "none";
    public static final String INCOMING = "incoming_pending";
    public static final String OUTGOING = "outgoing_pending";
    public static final String ACCEPTED = "accepted";

    public interface Listener {
        void onChanged(List<Map.Entry<String, String>> incoming, Map<String, String> relationships);
        void onError(Exception error);
    }

    public interface Callback {
        void onSuccess();
        void onError(Exception error);
    }

    private FriendRequestRepository() { }

    public static ListenerRegistration listen(Context context, Listener callback) {
        Holder holder = new Holder() {
            private volatile boolean removed;
            private volatile ListenerRegistration incomingRegistration;
            private volatile ListenerRegistration outgoingRegistration;
            private List<DocumentSnapshot> incoming = new ArrayList<>();
            private List<DocumentSnapshot> outgoing = new ArrayList<>();

            @Override public boolean isRemoved() { return removed; }
            @Override public void setIncomingRegistration(ListenerRegistration value) { incomingRegistration = value; }
            @Override public void setOutgoingRegistration(ListenerRegistration value) { outgoingRegistration = value; }
            @Override public void setIncoming(List<DocumentSnapshot> value) { incoming = value; }
            @Override public void setOutgoing(List<DocumentSnapshot> value) { outgoing = value; }

            @Override public synchronized void publish(Listener listener) {
                if (removed) return;
                List<Map.Entry<String, String>> requests = new ArrayList<>();
                Map<String, String> relations = new HashMap<>();
                for (DocumentSnapshot document : incoming) {
                    String senderUid = document.getString("senderUid");
                    if (senderUid == null || senderUid.isEmpty()) continue;
                    String status = document.getString("status");
                    if ("pending".equals(status)) {
                        requests.add(new java.util.AbstractMap.SimpleImmutableEntry<>(document.getId(), senderUid));
                        relations.put(senderUid, INCOMING);
                    } else if ("accepted".equals(status)) relations.put(senderUid, ACCEPTED);
                }
                for (DocumentSnapshot document : outgoing) {
                    String recipientUid = document.getString("recipientUid");
                    String status = document.getString("status");
                    if (recipientUid == null || recipientUid.isEmpty()) continue;
                    if ("accepted".equals(status)) relations.put(recipientUid, ACCEPTED);
                    else if ("pending".equals(status) && !ACCEPTED.equals(relations.get(recipientUid))) {
                        relations.put(recipientUid, OUTGOING);
                    }
                }
                listener.onChanged(requests, relations);
            }

            @Override public void removeChildren() {
                if (incomingRegistration != null) incomingRegistration.remove();
                if (outgoingRegistration != null) outgoingRegistration.remove();
            }

            @Override public void remove() {
                removed = true;
                removeChildren();
            }
        };
        App.ensureSignedIn(context, new App.SessionCallback() {
            @Override public void onSuccess() {
                if (holder.isRemoved()) return;
                String uid = FirebaseAuth.getInstance().getCurrentUser().getUid();
                FirebaseFirestore db = FirebaseFirestore.getInstance();
                holder.setIncomingRegistration(db.collection("friendRequests")
                        .whereEqualTo("recipientUid", uid)
                        .addSnapshotListener((snapshot, error) -> {
                            if (error != null) { callback.onError(error); return; }
                            holder.setIncoming(snapshot == null
                                    ? new ArrayList<>() : new ArrayList<>(snapshot.getDocuments()));
                            holder.publish(callback);
                        }));
                holder.setOutgoingRegistration(db.collection("friendRequests")
                        .whereEqualTo("senderUid", uid)
                        .addSnapshotListener((snapshot, error) -> {
                            if (error != null) { callback.onError(error); return; }
                            holder.setOutgoing(snapshot == null
                                    ? new ArrayList<>() : new ArrayList<>(snapshot.getDocuments()));
                            holder.publish(callback);
                        }));
                if (holder.isRemoved()) holder.removeChildren();
            }
            @Override public void onError(Exception error) { callback.onError(error); }
        });
        return holder;
    }

    public static void send(Context context, String recipientUid, Callback callback) {
        App.ensureSignedIn(context, new App.SessionCallback() {
            @Override public void onSuccess() {
                String senderUid = FirebaseAuth.getInstance().getCurrentUser().getUid();
                if (recipientUid == null || recipientUid.isEmpty() || senderUid.equals(recipientUid)) {
                    callback.onError(new IllegalArgumentException("Không thể gửi lời mời cho tài khoản này."));
                    return;
                }
                String requestId = senderUid + "_" + recipientUid;
                FirebaseFirestore db = FirebaseFirestore.getInstance();
                db.collection("friendRequests").document(requestId).get()
                        .addOnSuccessListener(existing -> {
                            String status = existing.getString("status");
                            if ("pending".equals(status) || "accepted".equals(status)) {
                                callback.onError(new IllegalStateException("Lời mời đã được gửi hoặc hai bạn đã là bạn bè."));
                                return;
                            }
                            Map<String, Object> data = new HashMap<>();
                            data.put("senderUid", senderUid);
                            data.put("recipientUid", recipientUid);
                            data.put("status", "pending");
                            data.put("updatedAt", FieldValue.serverTimestamp());
                            if (!existing.exists()) data.put("createdAt", FieldValue.serverTimestamp());
                            db.collection("friendRequests").document(requestId)
                                    .set(data, SetOptions.merge())
                                    .addOnSuccessListener(unused -> callback.onSuccess())
                                    .addOnFailureListener(callback::onError);
                        })
                        .addOnFailureListener(callback::onError);
            }
            @Override public void onError(Exception error) { callback.onError(error); }
        });
    }

    public static void respond(Context context, String requestId, boolean accept, Callback callback) {
        App.ensureSignedIn(context, new App.SessionCallback() {
            @Override public void onSuccess() {
                Map<String, Object> update = new HashMap<>();
                update.put("status", accept ? "accepted" : "rejected");
                update.put("updatedAt", FieldValue.serverTimestamp());
                FirebaseFirestore.getInstance().collection("friendRequests").document(requestId)
                        .update(update)
                        .addOnSuccessListener(unused -> callback.onSuccess())
                        .addOnFailureListener(callback::onError);
            }
            @Override public void onError(Exception error) { callback.onError(error); }
        });
    }

    private interface Holder extends ListenerRegistration {
        boolean isRemoved();
        void setIncomingRegistration(ListenerRegistration registration);
        void setOutgoingRegistration(ListenerRegistration registration);
        void setIncoming(List<DocumentSnapshot> documents);
        void setOutgoing(List<DocumentSnapshot> documents);
        void publish(Listener callback);
        void removeChildren();
    }
}
