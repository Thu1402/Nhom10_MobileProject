package com.example.app.data;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.webkit.MimeTypeMap;
import android.util.Log;
import android.util.Base64;

import com.example.app.model.Place;
import com.example.app.App;
import com.google.firebase.FirebaseApp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.SetOptions;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.google.firebase.storage.StorageMetadata;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.io.InputStream;
import java.io.ByteArrayOutputStream;

public final class FirebasePlaceRepository {
    private static final String COLLECTION = "places";

    public interface PlacesCallback {
        void onPlaces(List<Place> places);
        void onError(Exception error);
    }

    public interface ResultCallback {
        void onSuccess();
        void onError(Exception error);
    }

    public interface PlaceCallback {
        void onSuccess(Place place);
        void onError(Exception error);
    }

    public interface PlaceListenerCallback {
        void onPlace(Place place);
        void onMissing();
        void onError(Exception error);
    }

    private FirebasePlaceRepository() {}

    public static boolean isConfigured(Context context) {
        return !FirebaseApp.getApps(context.getApplicationContext()).isEmpty();
    }

    public static ListenerRegistration listen(Context context, PlacesCallback callback) {
        if (!isConfigured(context)) {
            callback.onError(new IllegalStateException("Firebase chưa được cấu hình. Thêm google-services.json vào APP/app."));
            return null;
        }
        AtomicBoolean removed = new AtomicBoolean();
        AtomicReference<ListenerRegistration> delegate = new AtomicReference<>();
        ListenerRegistration registration = () -> {
            removed.set(true);
            ListenerRegistration current = delegate.get();
            if (current != null) current.remove();
        };
        App.ensureSignedIn(context, new App.SessionCallback() {
            @Override public void onSuccess() {
                if (removed.get()) return;
                String uid = FirebaseAuth.getInstance().getCurrentUser().getUid();
                boolean admin = App.isAdmin();
                Query query = FirebaseFirestore.getInstance().collection(COLLECTION);
                if (!admin) query = query.whereEqualTo("ownerUid", uid);
                delegate.set(query.addSnapshotListener((snapshot, error) -> {
                            if (error != null) {
                                List<Place> cached = admin
                                        ? LocalPlaceDatabase.getInstance(context).getAllPlaces()
                                        : LocalPlaceDatabase.getInstance(context).getOwnerPlaces(uid);
                                if (!cached.isEmpty()) callback.onPlaces(cached); else callback.onError(error);
                                return;
                            }
                            List<Place> places = new ArrayList<>();
                            if (snapshot != null) {
                                for (DocumentSnapshot document : snapshot.getDocuments()) {
                                    Place place = fromDocument(document);
                                    // Ignore empty placeholder documents created manually in the console.
                                    if (!place.name.trim().isEmpty()) places.add(place);
                                }
                            }
                            if (admin) LocalPlaceDatabase.getInstance(context).replaceAllPlaces(places);
                            else LocalPlaceDatabase.getInstance(context).replaceOwnerPlaces(uid, places);
                            callback.onPlaces(places);
                        }));
                if (removed.get()) delegate.get().remove();
            }
            @Override public void onError(Exception error) { callback.onError(error); }
        });
        return registration;
    }

    /** Loads only places whose IDs are present in the signed-in user's share inbox. */
    public static void loadByIds(Context context, Collection<String> ids, PlacesCallback callback) {
        if (!isConfigured(context)) {
            callback.onError(new IllegalStateException("Firebase chưa được cấu hình."));
            return;
        }
        App.ensureSignedIn(context, new App.SessionCallback() {
            @Override public void onSuccess() {
                List<Task<?>> requests = new ArrayList<>();
                if (ids != null) {
                    for (String id : new java.util.LinkedHashSet<>(ids)) {
                        if (id != null && !id.trim().isEmpty()) {
                            requests.add(FirebaseFirestore.getInstance().collection(COLLECTION)
                                    .document(id).get());
                        }
                    }
                }
                if (requests.isEmpty()) {
                    callback.onPlaces(new ArrayList<>());
                    return;
                }
                Tasks.whenAllComplete(requests)
                        .addOnSuccessListener(completed -> {
                            List<Place> shared = new ArrayList<>();
                            for (Task<?> task : completed) {
                                if (!task.isSuccessful()) {
                                    callback.onError(task.getException() == null
                                            ? new IllegalStateException("Không tải được địa điểm đã chia sẻ.")
                                            : task.getException());
                                    return;
                                }
                                Object result = task.getResult();
                                if (!(result instanceof DocumentSnapshot)) continue;
                                DocumentSnapshot document = (DocumentSnapshot) result;
                                if (document.exists()) {
                                    Place place = fromDocument(document);
                                    if (!place.name.trim().isEmpty()) shared.add(place);
                                }
                            }
                            callback.onPlaces(shared);
                        })
                        .addOnFailureListener(callback::onError);
            }
            @Override public void onError(Exception error) { callback.onError(error); }
        });
    }

    public static ListenerRegistration listenToPlace(Context context, String placeId,
                                                     PlaceListenerCallback callback) {
        if (!isConfigured(context)) {
            callback.onError(new IllegalStateException("Firebase chưa được cấu hình."));
            return null;
        }
        AtomicBoolean removed = new AtomicBoolean();
        AtomicReference<ListenerRegistration> delegate = new AtomicReference<>();
        ListenerRegistration registration = () -> {
            removed.set(true);
            ListenerRegistration current = delegate.get();
            if (current != null) current.remove();
        };
        App.ensureSignedIn(context, new App.SessionCallback() {
            @Override public void onSuccess() {
                if (removed.get()) return;
                delegate.set(FirebaseFirestore.getInstance().collection(COLLECTION).document(placeId)
                        .addSnapshotListener((document, error) -> {
                            if (error != null) callback.onError(error);
                            else if (document == null || !document.exists()) callback.onMissing();
                            else {
                                Place place = fromDocument(document);
                                if (place.name.trim().isEmpty()) callback.onMissing();
                                else callback.onPlace(place);
                            }
                        }));
                if (removed.get()) delegate.get().remove();
            }
            @Override public void onError(Exception error) { callback.onError(error); }
        });
        return registration;
    }

    public static void create(Context context, Place place, Uri imageUri, ResultCallback callback) {
        if (!isConfigured(context)) {
            callback.onError(new IllegalStateException("Firebase chưa được cấu hình. Thêm google-services.json vào APP/app."));
            return;
        }
        App.ensureSignedIn(context, new App.SessionCallback() {
            @Override public void onSuccess() {
                String id = FirebaseFirestore.getInstance().collection(COLLECTION).document().getId();
                uploadIfSelected(context, id, imageUri, imageUrl -> {
                    String ownerId = FirebaseAuth.getInstance().getCurrentUser().getUid();
                    Place saved = new Place(id, ownerId, place.name, place.category, place.address, place.distance,
                            imageUrl, place.description, place.tags, place.latitude, place.longitude);
                    FirebaseFirestore.getInstance().collection(COLLECTION).document(id)
                            .set(toDocument(saved))
                            .addOnSuccessListener(unused -> callback.onSuccess())
                            .addOnFailureListener(callback::onError);
                }, callback::onError);
            }
            @Override public void onError(Exception error) { callback.onError(error); }
        });
    }

    public static void update(Context context, Place place, Uri imageUri, PlaceCallback callback) {
        if (!isConfigured(context)) {
            callback.onError(new IllegalStateException("Firebase chưa được cấu hình. Thêm google-services.json vào APP/app."));
            return;
        }
        if (place.id.isEmpty()) {
            callback.onError(new IllegalStateException("Địa điểm này chưa có mã Firebase."));
            return;
        }
        App.ensureSignedIn(context, new App.SessionCallback() {
            @Override public void onSuccess() {
                uploadIfSelected(context, place.id, imageUri, imageUrl -> {
                    Place updated = new Place(place.id, place.ownerId, place.name, place.category, place.address, place.distance,
                            imageUrl == null ? place.imageUrl : imageUrl, place.description, place.tags,
                            place.latitude, place.longitude);
                    FirebaseFirestore.getInstance().collection(COLLECTION).document(place.id)
                            .set(toDocument(updated), SetOptions.merge())
                            .addOnSuccessListener(unused -> callback.onSuccess(updated))
                            .addOnFailureListener(callback::onError);
                }, callback::onError, place.imageUrl);
            }
            @Override public void onError(Exception error) { callback.onError(error); }
        });
    }

    public static void delete(Context context, String placeId, ResultCallback callback) {
        if (!isConfigured(context)) {
            callback.onError(new IllegalStateException("Firebase chưa được cấu hình."));
            return;
        }
        if (!App.isAdmin()) {
            callback.onError(new SecurityException("Chỉ admin mới có thể xóa bài đăng của tài khoản khác."));
            return;
        }
        FirebaseFirestore.getInstance().collection(COLLECTION).document(placeId).delete()
                .addOnSuccessListener(unused -> {
                    LocalPlaceDatabase.getInstance(context).deletePlace(placeId);
                    callback.onSuccess();
                })
                .addOnFailureListener(callback::onError);
    }

    public static void deleteOwned(Context context, Place place, ResultCallback callback) {
        if (!isConfigured(context)) {
            callback.onError(new IllegalStateException("Firebase chưa được cấu hình."));
            return;
        }
        String uid = FirebaseAuth.getInstance().getCurrentUser() == null
                ? "" : FirebaseAuth.getInstance().getCurrentUser().getUid();
        if (place == null || place.id.isEmpty() || !uid.equals(place.ownerId)) {
            callback.onError(new SecurityException("Chỉ chủ sở hữu mới có thể xóa địa điểm này."));
            return;
        }
        FirebaseFirestore.getInstance().collection(COLLECTION).document(place.id).delete()
                .addOnSuccessListener(unused -> {
                    LocalPlaceDatabase.getInstance(context).deletePlace(place.id);
                    callback.onSuccess();
                })
                .addOnFailureListener(callback::onError);
    }

    private interface UploadSuccess { void accept(String imageUrl); }

    private static void uploadIfSelected(Context context, String id, Uri imageUri,
                                         UploadSuccess success, java.util.function.Consumer<Exception> failure) {
        uploadIfSelected(context, id, imageUri, success, failure, "");
    }

    private static void uploadIfSelected(Context context, String id, Uri imageUri,
                                         UploadSuccess success, java.util.function.Consumer<Exception> failure,
                                         String currentImageUrl) {
        if (imageUri == null) {
            success.accept(currentImageUrl);
            return;
        }
        String ownerId = FirebaseAuth.getInstance().getCurrentUser().getUid();
        String contentType = context.getContentResolver().getType(imageUri);
        if (contentType == null || !contentType.startsWith("image/")) contentType = "image/jpeg";
        String extension = MimeTypeMap.getSingleton().getExtensionFromMimeType(contentType);
        if (extension == null || extension.isEmpty()) extension = "img";
        StorageMetadata metadata = new StorageMetadata.Builder()
                .setContentType(contentType)
                .build();
        // Store each replacement as a new object. This avoids stale references to
        // a missing "cover" object and ensures the URL points to this exact upload.
        StorageReference imageRef = FirebaseStorage.getInstance().getReference()
                .child("users/" + ownerId + "/places/" + id + "/cover-" + UUID.randomUUID()
                        + "." + extension);
        // Downloads and other document providers return content:// URIs. Open the
        // selected document through ContentResolver instead of relying on a file path.
        final InputStream imageStream;
        try {
            imageStream = context.getContentResolver().openInputStream(imageUri);
            if (imageStream == null) {
                failure.accept(new IllegalStateException("Không đọc được ảnh đã chọn. Hãy chọn lại ảnh trong Downloads."));
                return;
            }
        } catch (Exception error) {
            failure.accept(new IllegalStateException("Không đọc được ảnh từ Downloads: " + error.getMessage(), error));
            return;
        }

        imageRef.putStream(imageStream, metadata)
                .addOnSuccessListener(snapshot -> imageRef.getDownloadUrl()
                        .addOnSuccessListener(uri -> {
                            closeQuietly(imageStream);
                            success.accept(uri.toString());
                        })
                        .addOnFailureListener(error -> {
                            closeQuietly(imageStream);
                            Log.e("FirebasePlaceRepository", "Upload succeeded but retrieving download URL failed. bucket="
                                    + FirebaseStorage.getInstance().getReference().getBucket()
                                    + ", path=" + imageRef.getPath(), error);
                            fallbackToFirestoreImage(context, imageUri, success, failure, error);
                        }))
                .addOnFailureListener(error -> {
                    closeQuietly(imageStream);
                    Log.e("FirebasePlaceRepository", "Image upload failed. bucket="
                            + FirebaseStorage.getInstance().getReference().getBucket()
                            + ", path=" + imageRef.getPath() + ", uri=" + imageUri, error);
                    fallbackToFirestoreImage(context, imageUri, success, failure, error);
                });
    }

    /**
     * Storage is optional for place photos. If the project's bucket is unavailable,
     * put a small JPEG data URL in the existing Firestore place document instead.
     * This keeps photos synced without requiring any Firebase Console changes.
     */
    private static void fallbackToFirestoreImage(Context context, Uri imageUri, UploadSuccess success,
                                                 java.util.function.Consumer<Exception> failure,
                                                 Exception storageError) {
        Log.w("FirebasePlaceRepository", "Falling back to compressed Firestore image data", storageError);
        Handler mainHandler = new Handler(Looper.getMainLooper());
        new Thread(() -> {
            try {
                String dataUrl = compressAsDataUrl(context, imageUri);
                mainHandler.post(() -> success.accept(dataUrl));
            } catch (Exception imageError) {
                imageError.addSuppressed(storageError);
                Exception combinedError = new IllegalStateException(
                        "Không thể tải ảnh lên Storage hoặc lưu ảnh nén vào Firestore: "
                                + imageError.getMessage(), imageError);
                mainHandler.post(() -> failure.accept(combinedError));
            }
        }, "place-image-firestore-fallback").start();
    }

    private static String compressAsDataUrl(Context context, Uri imageUri) throws Exception {
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        try (InputStream stream = context.getContentResolver().openInputStream(imageUri)) {
            if (stream == null) throw new IllegalStateException("Không mở được ảnh từ Downloads.");
            BitmapFactory.decodeStream(stream, null, bounds);
        }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            throw new IllegalArgumentException("Tệp đã chọn không phải ảnh hợp lệ.");
        }
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = 1;
        while (Math.max(bounds.outWidth / options.inSampleSize, bounds.outHeight / options.inSampleSize) > 1280) {
            options.inSampleSize *= 2;
        }
        final Bitmap decoded;
        try (InputStream stream = context.getContentResolver().openInputStream(imageUri)) {
            if (stream == null) throw new IllegalStateException("Không mở lại được ảnh từ Downloads.");
            decoded = BitmapFactory.decodeStream(stream, null, options);
        }
        if (decoded == null) throw new IllegalArgumentException("Không giải mã được ảnh đã chọn.");

        Bitmap bitmap = decoded;
        try {
            byte[] jpeg = compressJpeg(bitmap, 72);
            // Firestore limits a document to 1 MiB; keep the encoded image below
            // 450 KiB so normal place fields and metadata also fit safely.
            for (int pass = 0; jpeg.length > 450 * 1024 && pass < 3; pass++) {
                int width = Math.max(1, (int) (bitmap.getWidth() * 0.78f));
                int height = Math.max(1, (int) (bitmap.getHeight() * 0.78f));
                Bitmap smaller = Bitmap.createScaledBitmap(bitmap, width, height, true);
                if (bitmap != decoded) bitmap.recycle();
                bitmap = smaller;
                jpeg = compressJpeg(bitmap, Math.max(48, 66 - pass * 8));
            }
            if (jpeg.length > 450 * 1024) {
                throw new IllegalArgumentException("Ảnh quá lớn sau khi nén. Hãy chọn ảnh nhỏ hơn.");
            }
            return "data:image/jpeg;base64," + Base64.encodeToString(jpeg, Base64.NO_WRAP);
        } finally {
            if (!bitmap.isRecycled()) bitmap.recycle();
            if (bitmap != decoded && !decoded.isRecycled()) decoded.recycle();
        }
    }

    private static byte[] compressJpeg(Bitmap bitmap, int quality) {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        if (!bitmap.compress(Bitmap.CompressFormat.JPEG, quality, output)) {
            throw new IllegalStateException("Không thể nén ảnh để lưu vào Firestore.");
        }
        return output.toByteArray();
    }

    private static void closeQuietly(InputStream stream) {
        try { stream.close(); } catch (Exception ignored) { }
    }

    private static Map<String, Object> toDocument(Place place) {
        Map<String, Object> data = new HashMap<>();
        data.put("name", place.name);
        data.put("category", place.category);
        data.put("address", place.address);
        data.put("distance", place.distance);
        data.put("imageUrl", place.imageUrl);
        data.put("description", place.description);
        data.put("tags", place.tags);
        data.put("ownerUid", place.ownerId);
        data.put("updatedAt", com.google.firebase.firestore.FieldValue.serverTimestamp());
        if (place.hasCoordinates()) {
            data.put("latitude", place.latitude);
            data.put("longitude", place.longitude);
        }
        return data;
    }

    private static Place fromDocument(DocumentSnapshot document) {
        Double latitude = number(document.get("latitude"));
        Double longitude = number(document.get("longitude"));
        return new Place(document.getId(), value(document, "ownerUid"), value(document, "name"), value(document, "category"),
                value(document, "address"), value(document, "distance"), value(document, "imageUrl"),
                value(document, "description"), value(document, "tags"),
                latitude == null ? Double.NaN : latitude,
                longitude == null ? Double.NaN : longitude);
    }

    private static String value(DocumentSnapshot document, String key) {
        Object raw = document.get(key);
        if (raw == null) return "";
        if (raw instanceof String) return (String) raw;
        if (raw instanceof Collection<?>) {
            List<String> values = new ArrayList<>();
            for (Object item : (Collection<?>) raw) {
                if (item != null && !String.valueOf(item).trim().isEmpty()) values.add(String.valueOf(item));
            }
            return String.join(", ", values);
        }
        return String.valueOf(raw);
    }

    private static Double number(Object raw) {
        if (raw instanceof Number) return ((Number) raw).doubleValue();
        if (raw instanceof String) {
            try { return Double.parseDouble((String) raw); }
            catch (NumberFormatException ignored) { return null; }
        }
        return null;
    }

}
