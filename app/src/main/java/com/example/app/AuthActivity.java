package com.example.app;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.FrameLayout;
import android.view.Gravity;
import androidx.appcompat.app.AppCompatActivity;
import com.example.app.data.AppData;
import com.example.app.data.FirebaseSession;
import com.example.app.screens.HomeActivity;
import com.google.firebase.FirebaseApp;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.EmailAuthProvider;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;
import com.google.firebase.firestore.WriteBatch;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class AuthActivity extends AppCompatActivity {
    private EditText nameInput;
    private EditText phoneInput;
    private EditText usernameInput;
    private EditText identifierInput;
    private EditText passwordInput;
    private LinearLayout registerFields;
    private TextView title;
    private TextView subtitle;
    private TextView submitButton;
    private TextView togglePrompt;
    private TextView toggleButton;
    private boolean registerMode;
    private boolean submitting;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (FirebaseApp.getApps(this).isEmpty()) FirebaseApp.initializeApp(this);
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser != null && !currentUser.isAnonymous()) {
            openHome();
            return;
        }

        setContentView(R.layout.activity_auth);
        int widthDp = getResources().getConfiguration().screenWidthDp;
        if (widthDp >= 600) {
            View authPage = ((ViewGroup) findViewById(android.R.id.content)).getChildAt(0);
            int pageWidth = Math.min(560, widthDp - 48);
            authPage.setLayoutParams(new FrameLayout.LayoutParams(
                    Math.round(pageWidth * getResources().getDisplayMetrics().density),
                    FrameLayout.LayoutParams.MATCH_PARENT, Gravity.CENTER_HORIZONTAL));
        }
        nameInput = findViewById(R.id.edtRegisterName);
        phoneInput = findViewById(R.id.edtRegisterPhone);
        usernameInput = findViewById(R.id.edtRegisterUsername);
        identifierInput = findViewById(R.id.edtAuthIdentifier);
        passwordInput = findViewById(R.id.edtAuthPassword);
        registerFields = findViewById(R.id.registerFields);
        title = findViewById(R.id.txtAuthTitle);
        subtitle = findViewById(R.id.txtAuthSubtitle);
        submitButton = findViewById(R.id.btnAuthSubmit);
        togglePrompt = findViewById(R.id.txtAuthTogglePrompt);
        toggleButton = findViewById(R.id.btnAuthToggle);

        submitButton.setOnClickListener(v -> submit());
        toggleButton.setOnClickListener(v -> setRegisterMode(!registerMode));
        setRegisterMode(false);
    }

    private void setRegisterMode(boolean enabled) {
        registerMode = enabled;
        registerFields.setVisibility(enabled ? View.VISIBLE : View.GONE);
        identifierInput.setVisibility(enabled ? View.GONE : View.VISIBLE);
        title.setText(enabled ? "Tạo tài khoản" : "Đăng nhập");
        subtitle.setText(enabled
                ? "Thông tin tài khoản"
                : "Tên đăng nhập hoặc số điện thoại.");
        submitButton.setText(enabled ? "Đăng ký" : "Đăng nhập");
        togglePrompt.setText(enabled ? "Đã có tài khoản?" : "Chưa có tài khoản?");
        toggleButton.setText(enabled ? "Đăng nhập" : "Đăng ký");
    }

    private void submit() {
        if (submitting) return;
        String password = passwordInput.getText().toString();
        if (password.length() < 6) {
            passwordInput.setError("Mật khẩu cần có ít nhất 6 ký tự");
            passwordInput.requestFocus();
            return;
        }
        if (registerMode) register(password);
        else login(password);
    }

    private void login(String password) {
        String identifier = identifierInput.getText().toString().trim();
        if (identifier.isEmpty()) {
            identifierInput.setError("Nhập tên đăng nhập hoặc số điện thoại");
            identifierInput.requestFocus();
            return;
        }

        if (looksLikePhone(identifier)) {
            String digits = canonicalPhone(identifier);
            setSubmitting(true, "Đang tìm tài khoản...");
            FirebaseSession.ensureSignedIn(this, new FirebaseSession.Callback() {
                @Override public void onSuccess() {
                    FirebaseFirestore.getInstance().collection("authIdentifiers")
                            .document(hash("phone:" + digits)).get()
                            .addOnSuccessListener(document -> signInUsingLookup(document, password))
                            .addOnFailureListener(error -> showLoginError(
                                    "Không tra được số điện thoại. Kiểm tra quyền Firestore rồi thử lại."));
                }
                @Override public void onError(Exception error) {
                    showLoginError("Không thể kết nối Firebase Authentication. Hãy thử lại.");
                }
            });
            return;
        }

        String username = normalizeUsername(identifier);
        if (!validUsername(username)) {
            identifierInput.setError("Tên đăng nhập không hợp lệ");
            identifierInput.requestFocus();
            return;
        }
        setSubmitting(true, "Đang đăng nhập...");
        signInWithAlias(usernameAlias(username), password);
    }

    private void signInUsingLookup(DocumentSnapshot document, String password) {
        if (!document.exists()) {
            showLoginError("Không tìm thấy tài khoản với số điện thoại này.");
            return;
        }
        String authAlias = document.getString("authAlias");
        if (authAlias == null || authAlias.isEmpty()) {
            showLoginError("Thông tin đăng nhập chưa đầy đủ. Hãy đăng nhập bằng tên đăng nhập.");
            return;
        }
        signInWithAlias(authAlias, password);
    }

    private void signInWithAlias(String authAlias, String password) {
        FirebaseAuth.getInstance().signInWithEmailAndPassword(authAlias, password)
                .addOnSuccessListener(result -> openHome())
                .addOnFailureListener(error -> showLoginError(authError(error)));
    }

    private void register(String password) {
        String name = nameInput.getText().toString().trim();
        String phone = phoneInput.getText().toString().trim();
        String digits = canonicalPhone(phone);
        String username = normalizeUsername(usernameInput.getText().toString());
        if (name.isEmpty()) {
            nameInput.setError("Nhập tên hiển thị");
            nameInput.requestFocus();
            return;
        }
        if (digits.length() < 9 || digits.length() > 15) {
            phoneInput.setError("Nhập số điện thoại hợp lệ (9–15 chữ số)");
            phoneInput.requestFocus();
            return;
        }
        if (!validUsername(username)) {
            usernameInput.setError("Tên đăng nhập gồm 3–24 chữ, số, dấu chấm hoặc gạch dưới");
            usernameInput.requestFocus();
            return;
        }

        setSubmitting(true, "Đang kiểm tra thông tin...");
        // Tạo tài khoản trực tiếp để đăng ký không phụ thuộc provider Anonymous.
        createOrLinkAccount(name, phone, digits, username, password);
    }

    private void createOrLinkAccount(String name, String phone, String digits,
                                     String username, String password) {
        FirebaseAuth auth = FirebaseAuth.getInstance();
        String authAlias = usernameAlias(username);
        AuthCredential credential = EmailAuthProvider.getCredential(authAlias, password);
        FirebaseUser current = auth.getCurrentUser();
        if (current != null && current.isAnonymous()) {
            current.linkWithCredential(credential)
                    .addOnSuccessListener(result -> checkPhoneAndSaveProfile(result.getUser(), name,
                            phone, digits, username, authAlias))
                    .addOnFailureListener(error -> showRegisterError(authError(error)));
        } else {
            auth.createUserWithEmailAndPassword(authAlias, password)
                    .addOnSuccessListener(result -> checkPhoneAndSaveProfile(result.getUser(), name,
                            phone, digits, username, authAlias))
                    .addOnFailureListener(error -> showRegisterError(authError(error)));
        }
    }

    private void checkPhoneAndSaveProfile(FirebaseUser user, String name, String phone, String digits,
                                          String username, String authAlias) {
        if (user == null) {
            showRegisterError("Không lấy được tài khoản vừa tạo.");
            return;
        }
        FirebaseFirestore.getInstance().collection("authIdentifiers")
                .document(hash("phone:" + digits)).get()
                .addOnSuccessListener(document -> {
                    String existingUid = document.getString("uid");
                    if (document.exists() && !user.getUid().equals(existingUid)) {
                        user.delete().addOnCompleteListener(task -> {
                            FirebaseAuth.getInstance().signOut();
                            phoneInput.setError("Số điện thoại đã được đăng ký");
                            setSubmitting(false, "Đăng ký");
                        });
                        return;
                    }
                    saveNewProfile(user, name, phone, digits, username, authAlias);
                })
                .addOnFailureListener(error -> user.delete().addOnCompleteListener(task -> {
                    FirebaseAuth.getInstance().signOut();
                    showRegisterError("Không kiểm tra được số điện thoại trong Firebase. Kiểm tra quyền Firestore rồi thử lại.");
                }));
    }

    private void saveNewProfile(FirebaseUser user, String name, String phone, String digits,
                                String username, String authAlias) {
        if (user == null) {
            showRegisterError("Không lấy được tài khoản vừa tạo.");
            return;
        }
        String uid = user.getUid();
        Map<String, Object> userData = new HashMap<>();
        userData.put("uid", uid);
        userData.put("name", name);
        userData.put("phone", phone);
        userData.put("username", username);
        userData.put("bio", "");
        userData.put("imageUrl", "");
        userData.put("createdAt", FieldValue.serverTimestamp());
        userData.put("updatedAt", FieldValue.serverTimestamp());

        Map<String, Object> publicData = new HashMap<>();
        publicData.put("uid", uid);
        publicData.put("name", name);
        publicData.put("username", username);
        publicData.put("imageUrl", "");
        publicData.put("updatedAt", FieldValue.serverTimestamp());

        Map<String, Object> phoneLookup = new HashMap<>();
        phoneLookup.put("uid", uid);
        phoneLookup.put("authAlias", authAlias);

        FirebaseFirestore db = FirebaseFirestore.getInstance();
        WriteBatch batch = db.batch();
        batch.set(db.collection("users").document(uid), userData, SetOptions.merge());
        batch.set(db.collection("publicProfiles").document(uid), publicData, SetOptions.merge());
        batch.set(db.collection("authIdentifiers").document(hash("phone:" + digits)), phoneLookup);
        batch.commit().addOnSuccessListener(unused -> openHome())
                .addOnFailureListener(error -> {
                    setRegisterMode(false);
                    identifierInput.setText("@" + username);
                    showLoginError("Tài khoản đã tạo nhưng chưa lưu được hồ sơ. Kiểm tra quyền Firestore rồi đăng nhập.");
                });
    }

    private boolean looksLikePhone(String value) {
        String digits = phoneDigits(value);
        return digits.length() >= 9 && digits.length() <= 15
                && value.matches("[+0-9() .-]+");
    }

    private String phoneDigits(String value) {
        return value == null ? "" : value.replaceAll("\\D", "");
    }

    private String canonicalPhone(String value) {
        String digits = phoneDigits(value);
        if (digits.startsWith("0084")) digits = digits.substring(2);
        if (digits.startsWith("84") && digits.length() == 11) return "0" + digits.substring(2);
        if (digits.length() == 9) return "0" + digits;
        return digits;
    }

    private String normalizeUsername(String value) {
        return value == null ? "" : value.trim().replaceFirst("^@+", "").toLowerCase(Locale.ROOT);
    }

    private boolean validUsername(String username) {
        return username.matches("[a-z0-9_.]{3,24}")
                && !(username.length() >= 9 && username.matches("[0-9]+"));
    }

    private String usernameAlias(String username) {
        return "u" + hash("username:" + username).substring(0, 48) + "@tapmap.invalid";
    }

    private String hash(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(digest.length * 2);
            for (byte item : digest) result.append(String.format(Locale.ROOT, "%02x", item & 0xff));
            return result.toString();
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 không khả dụng", impossible);
        }
    }

    private void showLoginError(String message) {
        setSubmitting(false, "Đăng nhập");
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }

    private void showRegisterError(String message) {
        setSubmitting(false, "Đăng ký");
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }

    private void setSubmitting(boolean value, String label) {
        submitting = value;
        submitButton.setEnabled(!value);
        submitButton.setAlpha(value ? 0.65f : 1f);
        submitButton.setText(label);
    }

    private String authError(Exception error) {
        String message = error.getMessage();
        if (message == null) return "Không thể xác thực. Kiểm tra kết nối rồi thử lại.";
        String lower = message.toLowerCase(Locale.ROOT);
        if (lower.contains("email address is already in use") || lower.contains("credential is already in use")) {
            return "Tên đăng nhập đã được đăng ký.";
        }
        if (lower.contains("password is invalid") || lower.contains("no user record")
                || lower.contains("invalid login credentials") || lower.contains("invalid-credential")
                || lower.contains("supplied auth credential is incorrect")) {
            return "Tên đăng nhập/số điện thoại hoặc mật khẩu không đúng.";
        }
        if (lower.contains("network error")) return "Không có kết nối mạng. Hãy thử lại.";
        if (lower.contains("operation is not allowed") || lower.contains("operation-not-allowed")) {
            return "Firebase chưa bật phương thức Email/Password. Hãy bật trong Authentication > Sign-in method rồi thử lại.";
        }
        return message;
    }

    private void openHome() {
        AppData.PLACES.clear();
        AppData.SHARED_PLACES.clear();
        Intent intent = new Intent(this, HomeActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
}
