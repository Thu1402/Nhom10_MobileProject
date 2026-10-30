package com.example.app.data;

import com.example.app.App;
import android.content.Context;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import com.example.app.model.UserProfile;
import com.google.firebase.FirebaseApp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.WriteBatch;
import java.util.ArrayList;
import java.util.List;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageMetadata;
import com.google.firebase.storage.StorageReference;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

public final class FirebaseUserRepository {
    public interface ProfileCallback {
        void onSuccess(UserProfile profile);
        void onError(Exception error);
    }

    public interface UsersCallback {
        void onSuccess(List<UserProfile> users);
        void onError(Exception error);
    }

    public interface BlockCallback { void onComplete(Exception error); }

    private FirebaseUserRepository() {}

    public static void setBlocked(Context context, String uid, boolean blocked, BlockCallback callback) {
        if (!App.isAdmin()) {
            callback.onComplete(new SecurityException("Chỉ quản trị viên mới được khóa tài khoản."));
            return;
        }
        Map<String, Object> update = new HashMap<>();
        update.put("blocked", blocked);
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        WriteBatch batch = db.batch();
        batch.update(db.collection("users").document(uid), update);
        batch.update(db.collection("publicProfiles").document(uid), update);
        batch.commit().addOnSuccessListener(unused -> callback.onComplete(null))
                .addOnFailureListener(callback::onComplete);
    }

    public static ListenerRegistration load(Context context, ProfileCallback callback) {
        if (!isConfigured(context)) {
            callback.onError(new IllegalStateException("Firebase chưa khởi tạo được."));
            return null;
        }
        AtomicBoolean removed = new AtomicBoolean();
        AtomicReference<ListenerRegistration> delegate = new AtomicReference<>();
        App.ensureSignedIn(context, new App.SessionCallback() {
            @Override public void onSuccess() {
                if (removed.get()) return;
                String uid = FirebaseAuth.getInstance().getCurrentUser().getUid();
                ListenerRegistration registration = FirebaseFirestore.getInstance().collection("users").document(uid)
                        .addSnapshotListener((document, error) -> {
                            if (error != null) callback.onError(error);
                            else if (document != null) callback.onSuccess(fromDocument(document));
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

    public static void save(Context context, UserProfile profile, Uri photo, ProfileCallback callback) {
        if (!isConfigured(context)) {
            callback.onError(new IllegalStateException("Firebase chưa khởi tạo được."));
            return;
        }
        App.ensureSignedIn(context, new App.SessionCallback() {
            @Override public void onSuccess() {
                FirebaseAuth auth = FirebaseAuth.getInstance();
                String uid = auth.getCurrentUser().getUid();
                if (photo == null) {
                    writeProfile(uid, profile, profile.imageUrl, callback);
                    return;
                }
                try {
                    String contentType = context.getContentResolver().getType(photo);
                    if (contentType == null || !contentType.startsWith("image/")) contentType = "image/jpeg";
                    StorageMetadata metadata = new StorageMetadata.Builder().setContentType(contentType).build();
                    StorageReference avatar = FirebaseStorage.getInstance().getReference()
                            .child("users/" + uid + "/profile/avatar");
                    avatar.putFile(photo, metadata)
                            .continueWithTask(task -> {
                                if (!task.isSuccessful()) throw task.getException() == null
                                        ? new IllegalStateException("Không thể tải ảnh đại diện lên Firebase Storage")
                                        : task.getException();
                                return avatar.getDownloadUrl();
                            })
                            .addOnSuccessListener(url -> writeProfile(uid, profile, url.toString(), callback))
                            .addOnFailureListener(storageError -> saveCompressedPhoto(context, photo, uid,
                                    profile, callback, storageError));
                } catch (Exception storageError) {
                    saveCompressedPhoto(context, photo, uid, profile, callback, storageError);
                }
            }
            @Override public void onError(Exception error) { callback.onError(error); }
        });
    }

    private static void saveCompressedPhoto(Context context, Uri photo, String uid, UserProfile profile,
                                            ProfileCallback callback, Exception storageError) {
        android.util.Log.w("FirebaseUserRepository",
                "Profile photo Storage upload failed; saving a compressed copy in Firestore", storageError);
        Handler main = new Handler(Looper.getMainLooper());
        new Thread(() -> {
            try {
                String dataUrl = FirebasePlaceRepository.compressAsDataUrl(context, photo);
                main.post(() -> writeProfile(uid, profile, dataUrl, callback));
            } catch (Exception imageError) {
                imageError.addSuppressed(storageError);
                main.post(() -> callback.onError(imageError));
            }
        }, "profile-photo-firestore-fallback").start();
    }

    public static ListenerRegistration listOtherUsers(Context context, UsersCallback callback) {
        if (!isConfigured(context)) {
            callback.onError(new IllegalStateException("Firebase chưa khởi tạo được."));
            return null;
        }
        AtomicBoolean removed = new AtomicBoolean();
        AtomicReference<ListenerRegistration> delegate = new AtomicReference<>();
        App.ensureSignedIn(context, new App.SessionCallback() {
            @Override public void onSuccess() {
                if (removed.get()) return;
                String currentUid = FirebaseAuth.getInstance().getCurrentUser().getUid();
                ListenerRegistration registration = FirebaseFirestore.getInstance().collection("publicProfiles")
                        .addSnapshotListener((snapshot, error) -> {
                            if (error != null) {
                                callback.onError(error);
                                return;
                            }
                            List<UserProfile> users = new ArrayList<>();
                            if (snapshot != null) {
                                for (DocumentSnapshot document : snapshot.getDocuments()) {
                                    if (!currentUid.equals(document.getId())) {
                                        UserProfile user = fromDocument(document);
                                        if (!user.name.trim().isEmpty() || !user.username.trim().isEmpty()) users.add(user);
                                    }
                                }
                            }
                            callback.onSuccess(users);
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

    private static void writeProfile(String uid, UserProfile profile, String imageUrl, ProfileCallback callback) {
        Map<String, Object> data = new HashMap<>();
        data.put("name", profile.name);
        data.put("username", profile.username);
        data.put("bio", profile.bio);
        data.put("imageUrl", imageUrl);
        data.put("updatedAt", FieldValue.serverTimestamp());
        Map<String, Object> publicData = new HashMap<>();
        publicData.put("name", profile.name);
        publicData.put("username", profile.username);
        publicData.put("bio", profile.bio);
        publicData.put("imageUrl", imageUrl);
        publicData.put("updatedAt", FieldValue.serverTimestamp());
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        WriteBatch batch = db.batch();
        batch.set(db.collection("users").document(uid), data, SetOptions.merge());
        batch.set(db.collection("publicProfiles").document(uid), publicData, SetOptions.merge());
        batch.commit()
                .addOnSuccessListener(unused -> callback.onSuccess(
                        new UserProfile(uid, profile.name, profile.username, profile.bio, imageUrl)))
                .addOnFailureListener(callback::onError);
    }

    private static UserProfile fromDocument(DocumentSnapshot document) {
        return new UserProfile(document.getId(), string(document, "name"), string(document, "username"),
                string(document, "bio"), string(document, "imageUrl"), Boolean.TRUE.equals(document.getBoolean("blocked")));
    }

    private static String string(DocumentSnapshot document, String field) {
        Object value = document.get(field);
        return value instanceof String ? (String) value : "";
    }

    private static boolean isConfigured(Context context) {
        return !FirebaseApp.getApps(context.getApplicationContext()).isEmpty();
    }

}
