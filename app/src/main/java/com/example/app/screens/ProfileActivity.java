package com.example.app.screens;

import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.LinearLayout;
import android.text.InputType;
import androidx.appcompat.app.AlertDialog;
import com.example.app.AuthActivity;
import com.example.app.BaseNavigationActivity;
import com.bumptech.glide.Glide;
import com.example.app.R;
import com.example.app.data.FirebasePlaceRepository;
import com.example.app.data.FirebaseShareRepository;
import com.example.app.data.FirebaseUserRepository;
import com.example.app.App;
import com.example.app.model.Place;
import com.example.app.model.UserProfile;
import com.example.app.share.ShareLocationActivity;
import com.example.app.ui.Ui;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.EmailAuthProvider;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.QuerySnapshot;
import com.google.firebase.firestore.WriteBatch;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import java.util.List;
import java.util.ArrayList;
import java.util.Locale;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class ProfileActivity extends BaseNavigationActivity {
    private static final int PICK_PROFILE_PHOTO = 510;
    private BottomSheetDialog settingsDialog;
    private BottomSheetDialog accountDialog;
    private UserProfile profile = new UserProfile("", "", "", "");
    private Uri selectedPhoto;
    private TextView profileName;
    private TextView profileUsername;
    private TextView profileAvatar;
    private TextView profileBio;
    private ImageView profilePhoto;
    private ImageView accountPhoto;
    private EditText nameInput;
    private EditText usernameInput;
    private EditText bioInput;
    private TextView saveButton;
    private TextView savedCount;
    private TextView friendsCount;
    private TextView sharesCount;
    private ListenerRegistration placesListener;
    private ListenerRegistration profileListener;
    private ListenerRegistration friendsListener;
    private ListenerRegistration sharesCountListener;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentViewWithNavigation(R.layout.activity_profile);
        profileName = findViewById(R.id.txtProfileName);
        profileUsername = findViewById(R.id.txtProfileUsername);
        profileAvatar = findViewById(R.id.txtProfileAvatar);
        profileBio = findViewById(R.id.txtProfileBio);
        profilePhoto = findViewById(R.id.imgProfilePhoto);
        savedCount = findViewById(R.id.txtSavedCount);
        friendsCount = findViewById(R.id.txtFriendsCount);
        sharesCount = findViewById(R.id.txtSharesCount);
        findViewById(R.id.btnSettings).setOnClickListener(v -> showSettings());
        findViewById(R.id.btnEditProfile).setOnClickListener(v -> showAccountEditor());
        findViewById(R.id.txtSeeAll).setOnClickListener(v ->
                Toast.makeText(this, "Danh sách địa điểm đã lưu ở trang chủ", Toast.LENGTH_SHORT).show());
        findViewById(R.id.collectionCard).setOnClickListener(v ->
                startActivity(new Intent(this, HomeActivity.class)));
        findViewById(R.id.btnProfileAddFriend).setOnClickListener(v ->
                startActivity(new Intent(this, ShareLocationActivity.class)));
        findViewById(R.id.btnQuickPlaces).setOnClickListener(v -> scrollTo(R.id.collectionSection));
        findViewById(R.id.btnQuickShares).setOnClickListener(v ->
                startActivity(new Intent(this, ShareLocationActivity.class)));
        findViewById(R.id.btnCreatePlace).setOnClickListener(v ->
                startActivity(new Intent(this, AddPlaceActivity.class)));
        loadProfile();
        loadPlaces();
        loadFriendCount();
        loadShareCount();
    }

    private void scrollTo(int viewId) {
        View target = findViewById(viewId);
        android.widget.ScrollView scroll = findViewById(R.id.profileScrollView);
        if (target != null && scroll != null) {
            scroll.post(() -> {
                int y = target.getTop();
                View parent = (View) target.getParent();
                while (parent != null && parent != scroll) {
                    y += parent.getTop();
                    if (!(parent.getParent() instanceof View)) break;
                    parent = (View) parent.getParent();
                }
                scroll.smoothScrollTo(0, y);
            });
        }
    }

    private void loadPlaces() {
        placesListener = FirebasePlaceRepository.listen(this, new FirebasePlaceRepository.PlacesCallback() {
            @Override public void onPlaces(List<Place> places) {
                App.PLACES.clear();
                App.PLACES.addAll(places);
                String uid = FirebaseAuth.getInstance().getCurrentUser() == null
                        ? "" : FirebaseAuth.getInstance().getCurrentUser().getUid();
                List<Place> ownPlaces = new java.util.ArrayList<>();
                for (Place place : places) if (uid.equals(place.ownerId)) ownPlaces.add(place);
                findViewById(R.id.collectionCard).setVisibility(ownPlaces.isEmpty() ? View.GONE : View.VISIBLE);
                savedCount.setText(String.valueOf(ownPlaces.size()));
                bindCollectionPlace(R.id.imgCollectionPlaceOne, R.id.txtCollectionPlaceOne, ownPlaces, 0);
                bindCollectionPlace(R.id.imgCollectionPlaceTwo, R.id.txtCollectionPlaceTwo, ownPlaces, 1);
                bindCollectionPlace(R.id.imgCollectionPlaceThree, R.id.txtCollectionPlaceThree, ownPlaces, 2);
                TextView activityPlace = findViewById(R.id.txtActivityPlace);
                activityPlace.setText(places.isEmpty()
                        ? "Chưa có địa điểm"
                        : "Đã lưu · " + places.get(0).name);
                TextView addedActivity = findViewById(R.id.txtActivityAdded);
                Place lastAdded = null;
                for (Place place : places) if (uid.equals(place.ownerId)) lastAdded = place;
                addedActivity.setText(lastAdded == null
                        ? "Chưa thêm địa điểm"
                        : "Đã thêm · " + lastAdded.name);
            }
            @Override public void onError(Exception error) {
                Toast.makeText(ProfileActivity.this, "Không tải được địa điểm.", Toast.LENGTH_LONG).show();
            }
        });
    }

    private void bindCollectionPlace(int imageId, int nameId, List<Place> places, int index) {
        ImageView image = findViewById(imageId);
        TextView name = findViewById(nameId);
        if (index >= places.size()) {
            image.setVisibility(View.INVISIBLE);
            name.setVisibility(View.INVISIBLE);
            image.setOnClickListener(null);
            name.setOnClickListener(null);
            return;
        }
        image.setVisibility(View.VISIBLE);
        name.setVisibility(View.VISIBLE);
        Place place = places.get(index);
        name.setText(place.name);
        image.setOnClickListener(v -> editPlace(place));
        name.setOnClickListener(v -> openPlaceDetails(place));
        String url = place.imageUrl;
        if (url.isEmpty()) image.setImageResource(R.drawable.bg_empty_place_photo);
        else Glide.with(this).load(url).centerCrop().into(image);
    }

    private int appDataIndex(Place place) {
        for (int i = 0; i < App.PLACES.size(); i++) {
            if (place.id.equals(App.PLACES.get(i).id)) return i;
        }
        return -1;
    }

    private void editPlace(Place place) {
        int index = appDataIndex(place);
        if (index >= 0) startActivity(new Intent(this, EditPlaceActivity.class)
                .putExtra(PlaceDetailActivity.PLACE_INDEX, index));
    }

    private void openPlaceDetails(Place place) {
        int index = appDataIndex(place);
        if (index >= 0) startActivity(new Intent(this, PlaceDetailActivity.class)
                .putExtra(PlaceDetailActivity.PLACE_INDEX, index));
    }

    private void loadFriendCount() {
        friendsListener = FirebaseUserRepository.listOtherUsers(this, new FirebaseUserRepository.UsersCallback() {
            @Override public void onSuccess(List<UserProfile> users) { friendsCount.setText(String.valueOf(users.size())); }
            @Override public void onError(Exception error) { friendsCount.setText("0"); }
        });
    }

    private void loadShareCount() {
        sharesCountListener = FirebaseShareRepository.listenToCount(this,
                new FirebaseShareRepository.CountCallback() {
                    @Override public void onResult(int count) {
                        sharesCount.setText(String.valueOf(count));
                    }
                    @Override public void onError(Exception error) {
                        sharesCount.setText("0");
                    }
                });
    }

    private void loadProfile() {
        profileListener = FirebaseUserRepository.load(this, new FirebaseUserRepository.ProfileCallback() {
            @Override public void onSuccess(UserProfile loaded) {
                profile = loaded;
                renderProfile();
                syncAccountEditor();
            }
            @Override public void onError(Exception error) {
                Toast.makeText(ProfileActivity.this, "Không tải được hồ sơ.", Toast.LENGTH_LONG).show();
            }
        });
    }

    private void renderProfile() {
        profileName.setText(profile.name.isEmpty() ? "Tên của bạn" : profile.name);
        profileUsername.setText(profile.username.isEmpty() ? "@tài_khoản" : "@" + profile.username.replaceFirst("^@", ""));
        profileBio.setText(profile.bio.isEmpty() ? "Thêm tiểu sử của bạn" : profile.bio);
        profileAvatar.setText(initials(profile.name));
        if (!profile.imageUrl.isEmpty()) {
            profileAvatar.setVisibility(View.GONE);
            profilePhoto.setVisibility(View.VISIBLE);
            Glide.with(this).load(Ui.imageModel(profile.imageUrl)).circleCrop().into(profilePhoto);
        } else {
            profilePhoto.setVisibility(View.GONE);
            profileAvatar.setVisibility(View.VISIBLE);
        }
    }

    private void syncAccountEditor() {
        if (accountDialog == null || !accountDialog.isShowing()) return;
        if (nameInput != null) nameInput.setText(profile.name);
        if (usernameInput != null) usernameInput.setText(profile.username);
        if (bioInput != null) bioInput.setText(profile.bio);
        if (selectedPhoto == null && accountPhoto != null) {
            if (profile.imageUrl.isEmpty()) accountPhoto.setImageResource(R.drawable.bg_icon_tile);
            else Glide.with(this).load(Ui.imageModel(profile.imageUrl)).circleCrop().into(accountPhoto);
        }
    }

    private String initials(String name) {
        if (name == null || name.trim().isEmpty()) return "";
        String[] parts = name.trim().split("\\s+");
        if (parts.length == 1) return parts[0].substring(0, 1).toUpperCase();
        return (parts[0].substring(0, 1) + parts[parts.length - 1].substring(0, 1)).toUpperCase();
    }

    private void showAccountEditor() {
        if (accountDialog != null && accountDialog.isShowing()) return;
        View sheet = LayoutInflater.from(this).inflate(R.layout.dialog_account_settings, null, false);
        accountDialog = new BottomSheetDialog(this);
        accountDialog.setContentView(sheet);
        accountDialog.setDismissWithAnimation(true);
        accountDialog.setOnDismissListener(dialog -> selectedPhoto = null);
        nameInput = sheet.findViewById(R.id.edtAccountName);
        usernameInput = sheet.findViewById(R.id.edtAccountUsername);
        bioInput = sheet.findViewById(R.id.edtAccountBio);
        accountPhoto = sheet.findViewById(R.id.imgAccountPhoto);
        saveButton = sheet.findViewById(R.id.btnSaveAccountSettings);
        nameInput.setText(profile.name);
        usernameInput.setText(profile.username);
        bioInput.setText(profile.bio);
        if (!profile.imageUrl.isEmpty()) Glide.with(this).load(Ui.imageModel(profile.imageUrl)).circleCrop().into(accountPhoto);
        sheet.findViewById(R.id.btnCloseAccountSettings).setOnClickListener(v -> accountDialog.dismiss());
        sheet.findViewById(R.id.btnChoosePhoto).setOnClickListener(v -> {
            Intent picker = new Intent(Intent.ACTION_GET_CONTENT);
            picker.setType("image/*");
            startActivityForResult(Intent.createChooser(picker, "Chọn ảnh đại diện"), PICK_PROFILE_PHOTO);
        });
        saveButton.setOnClickListener(v -> saveProfile());
        accountDialog.show();
    }

    private void saveProfile() {
        String name = nameInput.getText().toString().trim();
        String username = usernameInput.getText().toString().trim().replaceFirst("^@", "");
        String bio = bioInput.getText().toString().trim();
        if (name.isEmpty()) {
            nameInput.setError("Nhập tên hiển thị");
            return;
        }
        saveButton.setEnabled(false);
        saveButton.setText("Đang lưu...");
        FirebaseUserRepository.save(this, new UserProfile(name, username, bio, profile.imageUrl), selectedPhoto,
                new FirebaseUserRepository.ProfileCallback() {
                    @Override public void onSuccess(UserProfile saved) {
                        profile = saved;
                        selectedPhoto = null;
                        renderProfile();
                        accountDialog.dismiss();
                        Toast.makeText(ProfileActivity.this, "Đã lưu hồ sơ", Toast.LENGTH_SHORT).show();
                    }
                    @Override public void onError(Exception error) {
                        saveButton.setEnabled(true);
                        saveButton.setText("Lưu thay đổi");
                        android.util.Log.e("ProfileActivity", "Could not save profile", error);
                        String reason = error.getLocalizedMessage();
                        Toast.makeText(ProfileActivity.this,
                                reason == null || reason.trim().isEmpty()
                                        ? "Chưa thể lưu hồ sơ. Hãy thử lại sau."
                                        : "Không thể lưu hồ sơ: " + reason,
                                Toast.LENGTH_LONG).show();
                    }
                });
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == PICK_PROFILE_PHOTO && resultCode == RESULT_OK && data != null && data.getData() != null) {
            selectedPhoto = data.getData();
            Glide.with(this).load(selectedPhoto).circleCrop().into(accountPhoto);
        }
    }

    private void showSettings() {
        if (settingsDialog != null && settingsDialog.isShowing()) return;
        View sheet = LayoutInflater.from(this).inflate(R.layout.dialog_setting, null, false);
        settingsDialog = new BottomSheetDialog(this);
        settingsDialog.setContentView(sheet);
        settingsDialog.setDismissWithAnimation(true);
        sheet.findViewById(R.id.btnCloseSettings).setOnClickListener(v -> settingsDialog.dismiss());
        View personal = sheet.findViewById(R.id.rowPersonal);
        if (personal != null) personal.setOnClickListener(v -> {
            settingsDialog.dismiss();
            showAccountEditor();
        });
        setSettingAction(sheet, R.id.rowNotifications, "Thông báo");
        setSettingAction(sheet, R.id.rowPrivacy, "Quyền riêng tư");
        View switchAccount = sheet.findViewById(R.id.rowSwitchAccount);
        if (switchAccount != null) switchAccount.setOnClickListener(v -> {
            settingsDialog.dismiss();
            showSwitchAccount();
        });
        View logout = sheet.findViewById(R.id.rowLogout);
        if (logout != null) logout.setOnClickListener(v -> {
            settingsDialog.dismiss();
            FirebaseAuth.getInstance().signOut();
            Intent intent = new Intent(this, AuthActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
        });
        View delete = sheet.findViewById(R.id.rowDelete);
        if (delete != null) delete.setOnClickListener(v -> {
            settingsDialog.dismiss();
            showDeleteAccountConfirmation();
        });
        settingsDialog.setOnShowListener(dialog -> {
            View bottomSheet = settingsDialog.findViewById(com.google.android.material.R.id.design_bottom_sheet);
            if (bottomSheet != null) bottomSheet.setBackgroundResource(android.R.color.transparent);
        });
        settingsDialog.show();
    }

    private void setSettingAction(View root, int viewId, String label) {
        View row = root.findViewById(viewId);
        if (row != null) row.setOnClickListener(v -> Toast.makeText(this, label, Toast.LENGTH_SHORT).show());
    }

    private void showSwitchAccount() {
        LinearLayout fields = new LinearLayout(this);
        fields.setOrientation(LinearLayout.VERTICAL);
        int pad = Ui.dp(this, 24);
        fields.setPadding(pad, Ui.dp(this, 8), pad, 0);
        EditText identifier = new EditText(this);
        identifier.setSingleLine(true);
        identifier.setHint("Tên đăng nhập hoặc số điện thoại");
        identifier.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_NORMAL);
        styleSwitchAccountInput(identifier);
        fields.addView(identifier, new LinearLayout.LayoutParams(-1, Ui.dp(this, 52)));
        EditText password = new EditText(this);
        password.setSingleLine(true);
        password.setHint("Mật khẩu");
        password.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        styleSwitchAccountInput(password);
        LinearLayout.LayoutParams passwordParams = new LinearLayout.LayoutParams(-1, Ui.dp(this, 52));
        passwordParams.topMargin = Ui.dp(this, 8);
        fields.addView(password, passwordParams);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Chuyển đổi tài khoản")
                .setMessage("Đăng nhập tài khoản khác ngay trên thiết bị này.")
                .setView(fields)
                .setNegativeButton("Hủy", null)
                .setPositiveButton("Chuyển", null)
                .create();
        dialog.setOnShowListener(ignored -> {
            TextView title = dialog.findViewById(androidx.appcompat.R.id.alertTitle);
            TextView message = dialog.findViewById(android.R.id.message);
            if (title != null) title.setTextColor(Color.BLACK);
            if (message != null) message.setTextColor(Color.BLACK);
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(Color.BLACK);
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(Color.BLACK);
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                String login = identifier.getText().toString().trim();
                String secret = password.getText().toString();
                if (login.isEmpty()) {
                    identifier.setError("Nhập tên đăng nhập hoặc số điện thoại");
                    return;
                }
                if (secret.isEmpty()) {
                    password.setError("Nhập mật khẩu");
                    return;
                }
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false);
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setText("Đang chuyển…");
                signInForSwitch(login, secret, dialog);
            });
        });
        dialog.show();
    }

    private void styleSwitchAccountInput(EditText input) {
        input.setTextColor(Color.BLACK);
        input.setHintTextColor(Color.BLACK);
        input.setTextSize(15);
        input.setPadding(Ui.dp(this, 14), 0, Ui.dp(this, 14), 0);
        input.setBackground(accountInputBackground(false));
        input.setOnFocusChangeListener((view, focused) -> input.setBackground(accountInputBackground(focused)));
    }

    private GradientDrawable accountInputBackground(boolean focused) {
        GradientDrawable background = new GradientDrawable();
        background.setShape(GradientDrawable.RECTANGLE);
        background.setCornerRadius(Ui.dp(this, 10));
        background.setColor(Color.WHITE);
        int strokeColor = focused ? Color.rgb(90, 108, 54) : Color.rgb(174, 184, 145);
        background.setStroke(Ui.dp(this, focused ? 2 : 1), strokeColor);
        return background;
    }

    private void signInForSwitch(String identifier, String password, AlertDialog dialog) {
        FirebaseAuth auth = FirebaseAuth.getInstance();
        if (looksLikePhone(identifier)) {
            String digits = canonicalPhone(identifier);
            FirebaseFirestore.getInstance().collection("authIdentifiers")
                    .document(hash("phone:" + digits)).get()
                    .addOnSuccessListener(document -> {
                        String alias = document.getString("authAlias");
                        if (alias == null || alias.isEmpty()) {
                            showSwitchError(dialog, "Không tìm thấy tài khoản với số điện thoại này.");
                            return;
                        }
                        signInWithAlias(auth, alias, password, dialog);
                    })
                    .addOnFailureListener(error -> showSwitchError(dialog, "Không tra được số điện thoại. Kiểm tra kết nối rồi thử lại."));
            return;
        }
        String username = identifier.trim().replaceFirst("^@+", "").toLowerCase(Locale.ROOT);
        if (!username.matches("[a-z0-9_.]{3,24}")) {
            showSwitchError(dialog, "Tên đăng nhập không hợp lệ.");
            return;
        }
        String alias = "u" + hash("username:" + username).substring(0, 48) + "@tapmap.invalid";
        signInWithAlias(auth, alias, password, dialog);
    }

    private void signInWithAlias(FirebaseAuth auth, String alias, String password, AlertDialog dialog) {
        auth.signInWithEmailAndPassword(alias, password)
                .addOnSuccessListener(result -> {
                    dialog.dismiss();
                    App.PLACES.clear();
                    App.SHARED_PLACES.clear();
                    startActivity(new Intent(this, HomeActivity.class)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
                    finish();
                })
                .addOnFailureListener(error -> showSwitchError(dialog, "Thông tin đăng nhập không đúng hoặc không thể kết nối."));
    }

    private void showSwitchError(AlertDialog dialog, String message) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
        if (dialog.isShowing()) {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(true);
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setText("Chuyển");
        }
    }

    private void showDeleteAccountConfirmation() {
        View content = LayoutInflater.from(this).inflate(R.layout.dialog_delete_account, null, false);
        EditText phrase = content.findViewById(R.id.edtDeleteConfirmation);
        EditText password = content.findViewById(R.id.edtDeletePassword);
        TextView confirm = content.findViewById(R.id.btnConfirmDelete);
        AlertDialog dialog = new AlertDialog.Builder(this).setView(content).create();
        content.findViewById(R.id.btnCancelDelete).setOnClickListener(v -> dialog.dismiss());
        confirm.setOnClickListener(v -> {
            if (!"TOIMUONXOATAIKHOAN".equals(phrase.getText().toString().trim())) {
                phrase.setError("Nhập chính xác TOIMUONXOATAIKHOAN");
                phrase.requestFocus();
                return;
            }
            if (password.getText().toString().isEmpty()) {
                password.setError("Nhập mật khẩu để xác thực tài khoản");
                password.requestFocus();
                return;
            }
            confirm.setEnabled(false);
            confirm.setText("Đang xóa…");
            reauthenticateThenDelete(password.getText().toString(), dialog, confirm);
        });
        dialog.show();
        if (dialog.getWindow() != null) dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
    }

    private void reauthenticateThenDelete(String password, AlertDialog dialog, TextView confirm) {
        com.google.firebase.auth.FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null || user.getEmail() == null) {
            restoreDeleteButton(dialog, confirm, "Không xác thực được tài khoản hiện tại.");
            return;
        }
        user.reauthenticate(EmailAuthProvider.getCredential(user.getEmail(), password))
                .addOnSuccessListener(unused -> deleteAccountDataAndAuth(dialog, confirm))
                .addOnFailureListener(error -> restoreDeleteButton(dialog, confirm,
                        "Mật khẩu không đúng. Tài khoản chưa bị xóa."));
    }

    private void deleteAccountDataAndAuth(AlertDialog dialog, TextView confirm) {
        com.google.firebase.auth.FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            finishDelete(dialog);
            return;
        }
        String uid = user.getUid();
        deleteStorageTree(FirebaseStorage.getInstance().getReference().child("users/" + uid))
                .addOnSuccessListener(unused -> deleteFirestoreDataAndAuth(user, uid, dialog, confirm))
                .addOnFailureListener(error -> restoreDeleteButton(dialog, confirm,
                        "Không xóa được ảnh tài khoản. Hãy kiểm tra Storage Rules rồi thử lại."));
    }

    private Task<Void> deleteStorageTree(StorageReference folder) {
        return folder.listAll().continueWithTask(task -> {
            if (!task.isSuccessful()) throw task.getException() == null
                    ? new IllegalStateException("Không đọc được thư mục ảnh") : task.getException();
            List<Task<Void>> deletions = new ArrayList<>();
            for (StorageReference item : task.getResult().getItems()) deletions.add(item.delete());
            for (StorageReference child : task.getResult().getPrefixes()) deletions.add(deleteStorageTree(child));
            return Tasks.whenAll(deletions);
        });
    }

    private void deleteFirestoreDataAndAuth(com.google.firebase.auth.FirebaseUser user, String uid,
                                            AlertDialog dialog, TextView confirm) {
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        List<Task<QuerySnapshot>> queries = new ArrayList<>();
        queries.add(db.collection("places").whereEqualTo("ownerUid", uid).get());
        queries.add(db.collection("shares").whereEqualTo("senderUid", uid).get());
        queries.add(db.collection("shares").whereEqualTo("recipientUid", uid).get());
        queries.add(db.collection("friendRequests").whereEqualTo("senderUid", uid).get());
        queries.add(db.collection("friendRequests").whereEqualTo("recipientUid", uid).get());
        Tasks.whenAllComplete(queries).addOnSuccessListener(results -> {
            List<DocumentReference> references = new ArrayList<>();
            for (Task<?> result : results) {
                if (!result.isSuccessful()) {
                    restoreDeleteButton(dialog, confirm, "Không tải được dữ liệu tài khoản để xóa.");
                    return;
                }
                QuerySnapshot snapshot = (QuerySnapshot) result.getResult();
                if (snapshot != null) for (com.google.firebase.firestore.DocumentSnapshot document : snapshot.getDocuments()) {
                    if (!references.contains(document.getReference())) references.add(document.getReference());
                }
            }
            references.add(db.collection("users").document(uid));
            references.add(db.collection("publicProfiles").document(uid));
            FirebaseFirestore.getInstance().collection("users").document(uid).get()
                    .addOnSuccessListener(profileDocument -> {
                        String phone = profileDocument.getString("phone");
                        if (phone != null && !phone.isEmpty()) {
                            references.add(db.collection("authIdentifiers").document(hash("phone:" + canonicalPhone(phone))));
                        }
                        deleteInBatches(references, 0, () -> user.delete()
                                .addOnSuccessListener(unused -> finishDelete(dialog))
                                .addOnFailureListener(error -> restoreDeleteButton(dialog, confirm,
                                        "Dữ liệu đã được xóa, nhưng Firebase chưa xóa phiên đăng nhập. Hãy đăng nhập lại rồi thử xóa."))
                        , error -> restoreDeleteButton(dialog, confirm, "Không xóa được dữ liệu. Hãy thử lại."));
                    })
                    .addOnFailureListener(error -> restoreDeleteButton(dialog, confirm, "Không đọc được hồ sơ tài khoản."));
        }).addOnFailureListener(error -> restoreDeleteButton(dialog, confirm, "Không thể xóa dữ liệu tài khoản."));
    }

    private void deleteInBatches(List<DocumentReference> refs, int offset, Runnable success,
                                 DeleteFailure failure) {
        if (offset >= refs.size()) {
            success.run();
            return;
        }
        WriteBatch batch = FirebaseFirestore.getInstance().batch();
        int end = Math.min(offset + 450, refs.size());
        for (int i = offset; i < end; i++) batch.delete(refs.get(i));
        batch.commit().addOnSuccessListener(unused -> deleteInBatches(refs, end, success, failure))
                .addOnFailureListener(failure::onError);
    }

    private interface DeleteFailure { void onError(Exception error); }

    private void restoreDeleteButton(AlertDialog dialog, TextView confirm, String message) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
        if (dialog.isShowing()) {
            confirm.setEnabled(true);
            confirm.setText("Xóa vĩnh viễn");
        }
    }

    private void finishDelete(AlertDialog dialog) {
        FirebaseAuth.getInstance().signOut();
        dialog.dismiss();
        startActivity(new Intent(this, AuthActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
        finish();
    }

    private boolean looksLikePhone(String value) {
        String digits = value == null ? "" : value.replaceAll("\\D", "");
        return digits.length() >= 9 && digits.length() <= 15 && value.matches("[+0-9() .-]+");
    }

    private String canonicalPhone(String value) {
        String digits = value == null ? "" : value.replaceAll("\\D", "");
        if (digits.startsWith("0084")) digits = digits.substring(2);
        if (digits.startsWith("84") && digits.length() == 11) return "0" + digits.substring(2);
        if (digits.length() == 9) return "0" + digits;
        return digits;
    }

    private String hash(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(digest.length * 2);
            for (byte item : digest) result.append(String.format(Locale.ROOT, "%02x", item & 0xff));
            return result.toString();
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 không khả dụng", impossible);
        }
    }

    @Override protected void onDestroy() {
        if (placesListener != null) placesListener.remove();
        if (profileListener != null) profileListener.remove();
        if (friendsListener != null) friendsListener.remove();
        if (sharesCountListener != null) sharesCountListener.remove();
        super.onDestroy();
    }
}
