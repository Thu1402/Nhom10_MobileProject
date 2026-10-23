package com.example.app.data;

import android.content.Context;
import com.google.android.gms.tasks.Task;
import com.google.firebase.FirebaseApp;
import com.google.firebase.auth.AuthResult;
import com.google.firebase.auth.FirebaseAuth;

public final class FirebaseSession {
    public interface Callback {
        void onSuccess();
        void onError(Exception error);
    }

    private static Task<AuthResult> pendingSignIn;

    private FirebaseSession() {}

    public static void ensureSignedIn(Context context, Callback callback) {
        if (FirebaseApp.getApps(context.getApplicationContext()).isEmpty()) {
            callback.onError(new IllegalStateException("Firebase chưa được khởi tạo."));
            return;
        }
        FirebaseAuth auth = FirebaseAuth.getInstance();
        if (auth.getCurrentUser() != null) {
            callback.onSuccess();
            return;
        }
        Task<AuthResult> task;
        synchronized (FirebaseSession.class) {
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
                    synchronized (FirebaseSession.class) {
                        if (pendingSignIn == task) pendingSignIn = null;
                    }
                    callback.onError(error);
                });
    }
}
