package com.example.app;

import android.app.Application;
import android.content.Context;
import com.example.app.model.Place;
import com.google.android.gms.tasks.Task;
import com.google.firebase.FirebaseApp;
import com.google.firebase.auth.AuthResult;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import java.util.ArrayList;
import java.util.List;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

public class App extends Application {
    public interface SessionCallback {
        void onSuccess();
        void onError(Exception error);
    }

    public static final List<Place> PLACES = new ArrayList<>();
    public static final List<Place> SHARED_PLACES = new ArrayList<>();
    private static Task<AuthResult> pendingSignIn;

    @Override public void onCreate() {
        super.onCreate();
        FirebaseApp.initializeApp(this);
        FirebaseFirestore.getInstance();
    }

    public static void ensureSignedIn(Context context, SessionCallback callback) {
        if (FirebaseApp.getApps(context.getApplicationContext()).isEmpty()) {
            callback.onError(new IllegalStateException("Firebase chưa được khởi tạo."));
            return;
        }
        FirebaseAuth auth = FirebaseAuth.getInstance();
        if (auth.getCurrentUser() != null) { callback.onSuccess(); return; }
        Task<AuthResult> task;
        synchronized (App.class) {
            if (auth.getCurrentUser() != null) { callback.onSuccess(); return; }
        if (auth.getCurrentUser() != null) {
            callback.onSuccess();
            return;
        }
        Task<AuthResult> task;
        synchronized (App.class) {
            if (auth.getCurrentUser() != null) {
                callback.onSuccess();
                return;
            }
            if (pendingSignIn == null || (pendingSignIn.isComplete() && !pendingSignIn.isSuccessful())) {
                pendingSignIn = auth.signInAnonymously();
            }
            task = pendingSignIn;
        }
        task.addOnSuccessListener(result -> callback.onSuccess())
                .addOnFailureListener(error -> {
                    synchronized (App.class) { if (pendingSignIn == task) pendingSignIn = null; }
                    synchronized (App.class) {
                        if (pendingSignIn == task) pendingSignIn = null;
                    }
                    callback.onError(error);
                });
    }

    public static boolean isAdmin() {
        if (FirebaseAuth.getInstance().getCurrentUser() == null) return false;
        String email = FirebaseAuth.getInstance().getCurrentUser().getEmail();
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest("username:admin".getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte value : digest) hex.append(String.format(java.util.Locale.ROOT, "%02x", value & 0xff));
            return ("u" + hex.substring(0, 48) + "@tapmap.invalid").equalsIgnoreCase(email);
        } catch (Exception error) { return false; }
    }
}
