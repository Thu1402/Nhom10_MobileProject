package com.example.app.screens;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.ImageView;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AlertDialog;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputLayout;
import com.example.app.R;
import com.example.app.App;
import com.example.app.data.FirebasePlaceRepository;
import com.example.app.model.Place;
import com.example.app.ui.Ui;

import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.storage.StorageException;
import com.google.firebase.firestore.FirebaseFirestoreException;

public class EditPlaceActivity extends AppCompatActivity {
    private int index;
    private Uri selectedImageUri;
    private MaterialButton saveButton;
    private ImageView imagePicker;
    private TextInputLayout nameInput, categoryInput, addressInput, descriptionInput, tagsInput;
    private String placeId = "";
    private ListenerRegistration placeListener;
    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_edit_place);
        index = getIntent().getIntExtra(PlaceDetailActivity.PLACE_INDEX, 0);
        if (App.PLACES.isEmpty()) { finish(); return; }
        index = Math.max(0, Math.min(index, App.PLACES.size() - 1));
        Place place = App.PLACES.get(index);
        placeId = place.id;
        LinearLayout page = findViewById(R.id.edit_place_content);
        page.addView(Ui.text(this, "Chỉnh sửa địa điểm", 27, Ui.color(this, R.color.ink), true));
        imagePicker = new ImageView(this);
        imagePicker.setScaleType(ImageView.ScaleType.CENTER_CROP);
        imagePicker.setContentDescription("Chọn ảnh mới cho địa điểm");
        if (place.imageUrl.isEmpty()) imagePicker.setImageResource(R.drawable.bg_empty_place_photo);
        else com.bumptech.glide.Glide.with(this).load(place.imageUrl).centerCrop().into(imagePicker);
        imagePicker.setBackground(Ui.borderedBackground(Ui.color(this, R.color.surface), Ui.color(this, R.color.line), 22, this));
        imagePicker.setOnClickListener(view -> {
            Intent picker = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            picker.addCategory(Intent.CATEGORY_OPENABLE);
            picker.setType("image/*");
            picker.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
            startActivityForResult(picker, 402);
        });
        page.addView(imagePicker, Ui.margin(-1, 160, 0, 0, 0, 20, this));
        nameInput = Ui.input(this, "Tên địa điểm", false);
        categoryInput = Ui.input(this, "Danh mục", false);
        Ui.setupCategoryDropdown(this, categoryInput, place.category);
        addressInput = Ui.input(this, "Địa chỉ", false);
        descriptionInput = Ui.input(this, "Mô tả địa điểm", true);
        tagsInput = Ui.input(this, "Tag", false);
        setValue(nameInput, place.name); setValue(categoryInput, place.category);
        setValue(addressInput, place.address); setValue(descriptionInput, place.description);
        setValue(tagsInput, Ui.normalizeTags(place.tags));
        Ui.bindTagPrefixing(tagsInput.getEditText());
        page.addView(nameInput, Ui.margin(-1, -2, 0, 24, 0, 14, this));
        page.addView(categoryInput, Ui.margin(-1, -2, 0, 0, 0, 14, this));
        page.addView(addressInput, Ui.margin(-1, -2, 0, 0, 0, 14, this));
        page.addView(descriptionInput, Ui.margin(-1, -2, 0, 0, 0, 22, this));
        page.addView(tagsInput, Ui.margin(-1, -2, 0, 0, 0, 18, this));
        saveButton = Ui.primaryButton(this, "Lưu thay đổi");
        saveButton.setOnClickListener(view -> saveChanges());
        page.addView(saveButton, Ui.margin(-1, 54, 0, 8, 0, 0, this));
        MaterialButton cancel = Ui.softButton(this, "Hủy");
        cancel.setOnClickListener(view -> finish());
        page.addView(cancel, Ui.margin(-1, 50, 0, 10, 0, 0, this));
        if (!placeId.isEmpty()) listenForPlaceChanges();
    }

    private void listenForPlaceChanges() {
        placeListener = FirebasePlaceRepository.listenToPlace(this, placeId,
                new FirebasePlaceRepository.PlaceListenerCallback() {
                    @Override public void onPlace(Place updated) {
                        for (int i = 0; i < App.PLACES.size(); i++) {
                            if (placeId.equals(App.PLACES.get(i).id)) {
                                App.PLACES.set(i, updated);
                                index = i;
                                break;
                            }
                        }
                        syncField(nameInput, updated.name);
                        syncField(categoryInput, updated.category);
                        syncField(addressInput, updated.address);
                        syncField(descriptionInput, updated.description);
                        syncField(tagsInput, updated.tags);
                        if (selectedImageUri == null && imagePicker != null) {
                            if (updated.imageUrl.isEmpty()) imagePicker.setImageResource(R.drawable.bg_empty_place_photo);
                            else com.bumptech.glide.Glide.with(EditPlaceActivity.this)
                                    .load(updated.imageUrl).centerCrop().into(imagePicker);
                        }
                    }
                    @Override public void onMissing() {
                        Toast.makeText(EditPlaceActivity.this,
                                "Địa điểm này đã bị xóa khỏi Firebase.", Toast.LENGTH_SHORT).show();
                        finish();
                    }
                    @Override public void onError(Exception error) {
                        android.util.Log.w("EditPlaceActivity", "Realtime place listener failed", error);
                    }
                });
    }

    private void syncField(TextInputLayout layout, String value) {
        EditText edit = layout.getEditText();
        if (edit != null) setValue(layout, value);
    }
    private void saveChanges() {
        String name = value(nameInput), category = value(categoryInput), address = value(addressInput), description = value(descriptionInput);
        if (name.isEmpty() || category.isEmpty() || address.isEmpty()) {
            Toast.makeText(this, "Nhập tên, danh mục và địa chỉ.", Toast.LENGTH_SHORT).show(); return;
        }
        Place old = App.PLACES.get(index);
        String tags = Ui.normalizeTags(value(tagsInput));
        Place updated = new Place(old.id, old.ownerId, name, category, address, old.distance, old.imageUrl,
                description, tags.isEmpty() ? category : tags, old.latitude, old.longitude);
        saveButton.setEnabled(false);
        saveButton.setText(selectedImageUri == null ? "Đang lưu..." : "Đang lưu thay đổi và ảnh...");
        FirebasePlaceRepository.update(this, updated, selectedImageUri, new FirebasePlaceRepository.PlaceCallback() {
            @Override public void onSuccess(Place savedPlace) {
                App.PLACES.set(index, savedPlace);
                Toast.makeText(EditPlaceActivity.this, "Đã cập nhật địa điểm", Toast.LENGTH_SHORT).show();
                Intent intent = new Intent(EditPlaceActivity.this, PlaceDetailActivity.class);
                intent.putExtra(PlaceDetailActivity.PLACE_INDEX, index);
                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                startActivity(intent);
                finish();
            }
            @Override public void onError(Exception error) {
                saveButton.setEnabled(true);
                saveButton.setText("Lưu thay đổi");
                android.util.Log.e("EditPlaceActivity", "Could not save place or upload its image", error);
                new AlertDialog.Builder(EditPlaceActivity.this)
                        .setTitle("Không tải được ảnh")
                        .setMessage(saveErrorMessage(error))
                        .setPositiveButton("Đóng", null)
                        .show();
            }
        });
    }

    private String saveErrorMessage(Exception error) {
        if (error instanceof StorageException) {
            StorageException storageError = (StorageException) error;
            switch (storageError.getErrorCode()) {
                case StorageException.ERROR_NOT_AUTHORIZED:
                    return "Firebase Storage từ chối tải ảnh. Hãy Publish Storage Rules cho đường dẫn users/{uid}/places/{placeId}/cover.";
                case StorageException.ERROR_NOT_AUTHENTICATED:
                    return "Phiên Firebase đã hết hạn. Hãy đăng nhập lại rồi lưu ảnh.";
                case StorageException.ERROR_BUCKET_NOT_FOUND:
                    return "Firebase Storage không khả dụng. Ứng dụng đang thử lưu ảnh nén trực tiếp vào Firestore.";
                case StorageException.ERROR_OBJECT_NOT_FOUND:
                    return "Firebase Storage trả HTTP 404. Ứng dụng sẽ dùng ảnh nén trong Firestore thay thế.";
                case StorageException.ERROR_QUOTA_EXCEEDED:
                    return "Firebase Storage đã vượt hạn mức lưu trữ.";
                case StorageException.ERROR_RETRY_LIMIT_EXCEEDED:
                    return "Tải ảnh bị gián đoạn do kết nối. Hãy thử lại.";
                default:
                    return "Không tải được ảnh lên Firebase Storage (mã "
                            + storageError.getErrorCode() + "). Kiểm tra Storage Rules và kết nối.";
            }
        }
        if (error instanceof FirebaseFirestoreException
                && ((FirebaseFirestoreException) error).getCode() == FirebaseFirestoreException.Code.PERMISSION_DENIED) {
            return "Firestore từ chối lưu địa điểm. Kiểm tra Firestore Rules.";
        }
        String detail = error.getLocalizedMessage();
        return detail == null || detail.trim().isEmpty()
                ? "Không thể lưu địa điểm. Hãy kiểm tra Firebase và kết nối rồi thử lại."
                : "Không thể lưu địa điểm: " + detail;
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 402 && resultCode == RESULT_OK && data != null && data.getData() != null) {
            selectedImageUri = data.getData();
            if ((data.getFlags() & Intent.FLAG_GRANT_READ_URI_PERMISSION) != 0) {
                try {
                    getContentResolver().takePersistableUriPermission(selectedImageUri,
                            Intent.FLAG_GRANT_READ_URI_PERMISSION);
                } catch (SecurityException | IllegalArgumentException ignored) { }
            }
            imagePicker.setImageURI(selectedImageUri);
        }
    }
    private void setValue(TextInputLayout layout, String value) {
        EditText edit = layout.getEditText();
        if (edit != null) edit.setText(value);
    }
    private String value(TextInputLayout layout) {
        EditText edit = layout.getEditText();
        return edit == null || edit.getText() == null ? "" : edit.getText().toString().trim();
    }

    @Override protected void onDestroy() {
        if (placeListener != null) placeListener.remove();
        super.onDestroy();
    }
}

