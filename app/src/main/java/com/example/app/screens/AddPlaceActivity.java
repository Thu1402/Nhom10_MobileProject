package com.example.app.screens;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ImageView;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import android.util.Log;
import androidx.appcompat.app.AppCompatActivity;
import com.example.app.BaseNavigationActivity;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.firebase.storage.StorageException;
import com.example.app.R;
import com.example.app.data.AppData;
import com.example.app.data.FirebasePlaceRepository;
import com.example.app.model.Place;
import com.example.app.ui.Ui;
import com.example.app.ui.PlaceFormUtils;
import com.bumptech.glide.Glide;

public class AddPlaceActivity extends BaseNavigationActivity {
    private TextInputLayout nameInput, categoryInput, addressInput, descriptionInput, tagsInput;
    private ImageView imagePicker;
    private TextView imageHint;
    private MaterialButton saveButton;
    private Uri selectedImageUri;
    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentViewWithNavigation(R.layout.activity_add_place);
        LinearLayout page = findViewById(R.id.add_place_content);
        page.addView(Ui.text(this, "Thêm địa điểm", 27, Ui.color(this, R.color.ink), true));
        FrameLayout imageCard = new FrameLayout(this);
        imageCard.setBackground(Ui.borderedBackground(Ui.color(this, R.color.surface), Ui.color(this, R.color.line), 22, this));
        imagePicker = new ImageView(this);
        imagePicker.setScaleType(ImageView.ScaleType.CENTER_CROP);
        imagePicker.setContentDescription("Ảnh địa điểm. Chạm để chọn hoặc đổi ảnh.");
        imageCard.addView(imagePicker, new FrameLayout.LayoutParams(-1, -1));
        imageHint = Ui.text(this, "＋\nChạm để thêm ảnh", 15, Ui.color(this, R.color.violet), true);
        imageHint.setGravity(Gravity.CENTER);
        imageHint.setClickable(false);
        imageCard.addView(imageHint, new FrameLayout.LayoutParams(-1, -1));
        imageCard.setOnClickListener(view -> {
            Intent picker = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            picker.addCategory(Intent.CATEGORY_OPENABLE);
            picker.setType("image/*");
            startActivityForResult(picker, 401);
        });
        page.addView(imageCard, Ui.margin(-1, 180, 0, 22, 0, 24, this));
        nameInput = Ui.input(this, "Tên địa điểm", false);
        categoryInput = Ui.input(this, "Danh mục", false);
        PlaceFormUtils.setupCategoryDropdown(this, categoryInput, "");
        addressInput = Ui.input(this, "Địa chỉ", false);
        descriptionInput = Ui.input(this, "Mô tả địa điểm", true);
        tagsInput = Ui.input(this, "Tag", false);
        PlaceFormUtils.bindTagPrefixing(tagsInput.getEditText());
        String mapAddress = getIntent().getStringExtra("MAP_ADDRESS");
        if (mapAddress != null && !mapAddress.trim().isEmpty()) {
            TextInputEditText addressField = (TextInputEditText) addressInput.getEditText();
            if (addressField != null) addressField.setText(mapAddress);
        }
        setInputValue(nameInput, getIntent().getStringExtra("PREFILL_NAME"));
        setInputValue(categoryInput, getIntent().getStringExtra("PREFILL_CATEGORY"));
        setInputValue(addressInput, getIntent().getStringExtra("PREFILL_ADDRESS"));
        setInputValue(descriptionInput, getIntent().getStringExtra("PREFILL_DESCRIPTION"));
        setInputValue(tagsInput, getIntent().getStringExtra("PREFILL_TAGS"));
        page.addView(nameInput, Ui.margin(-1, -2, 0, 0, 0, 14, this));
        page.addView(categoryInput, Ui.margin(-1, -2, 0, 0, 0, 14, this));
        page.addView(addressInput, Ui.margin(-1, -2, 0, 0, 0, 14, this));
        page.addView(descriptionInput, Ui.margin(-1, -2, 0, 0, 0, 18, this));
        page.addView(tagsInput, Ui.margin(-1, -2, 0, 0, 0, 18, this));
        saveButton = Ui.primaryButton(this, "Lưu địa điểm");
        saveButton.setOnClickListener(view -> savePlace());
        page.addView(saveButton, Ui.margin(-1, 54, 0, 8, 0, 0, this));
    }
    private void setInputValue(TextInputLayout input, String value) {
        if (value == null || value.isEmpty() || input.getEditText() == null) return;
        input.getEditText().setText(value);
    }
    private void savePlace() {
        String name = value(nameInput);
        String category = value(categoryInput);
        String address = value(addressInput);
        String description = value(descriptionInput);
        String tags = PlaceFormUtils.normalizeTags(value(tagsInput));
        if (name.isEmpty() || category.isEmpty() || address.isEmpty()) {
            Toast.makeText(this, "Nhập tên, danh mục và địa chỉ.", Toast.LENGTH_SHORT).show();
            return;
        }
        saveButton.setEnabled(false);
        saveButton.setText(selectedImageUri == null ? "Đang lưu..." : "Đang lưu địa điểm và ảnh...");
        double latitude = getIntent().getDoubleExtra("MAP_LATITUDE", Double.NaN);
        double longitude = getIntent().getDoubleExtra("MAP_LONGITUDE", Double.NaN);
        Place place = new Place("", "", name, category, address, "", "", description,
                tags.isEmpty() ? category : tags, latitude, longitude);
        FirebasePlaceRepository.create(this, place, selectedImageUri, new FirebasePlaceRepository.ResultCallback() {
            @Override public void onSuccess() {
                Toast.makeText(AddPlaceActivity.this, "Đã lưu địa điểm", Toast.LENGTH_SHORT).show();
                Intent intent = new Intent(AddPlaceActivity.this, HomeActivity.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                startActivity(intent);
                finish();
            }
            @Override public void onError(Exception error) {
                saveButton.setEnabled(true);
                saveButton.setText("Lưu địa điểm");
                Log.e("AddPlaceActivity", "Firebase could not save place", error);
                Toast.makeText(AddPlaceActivity.this, saveErrorMessage(error), Toast.LENGTH_LONG).show();
            }
        });
    }

    private String saveErrorMessage(Exception error) {
        if (error instanceof FirebaseAuthException
                && "ERROR_OPERATION_NOT_ALLOWED".equals(((FirebaseAuthException) error).getErrorCode())) {
            return "Firebase chưa bật đăng nhập ẩn danh. Bật Anonymous trong Authentication → Sign-in method.";
        }
        if (error instanceof FirebaseFirestoreException
                && ((FirebaseFirestoreException) error).getCode() == FirebaseFirestoreException.Code.PERMISSION_DENIED) {
            return "Firestore từ chối ghi dữ liệu. Kiểm tra rules của collection places đã được Publish chưa.";
        }
        if (error instanceof StorageException) {
            return "Không tải được ảnh lên Firebase Storage. Kiểm tra Storage đã tạo và rules cho phép người dùng ghi ảnh.";
        }
        String detail = error.getLocalizedMessage();
        if (detail == null || detail.trim().isEmpty()) detail = error.getClass().getSimpleName();
        return "Không lưu được lên Firebase: " + detail;
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 401 && resultCode == RESULT_OK && data != null && data.getData() != null) {
            selectedImageUri = data.getData();
            if (data.getFlags() != 0) {
                try {
                    int permission = data.getFlags() & Intent.FLAG_GRANT_READ_URI_PERMISSION;
                    if (permission != 0) getContentResolver().takePersistableUriPermission(selectedImageUri, permission);
                } catch (SecurityException | IllegalArgumentException ignored) { }
            }
            imagePicker.setImageURI(selectedImageUri);
            imageHint.setVisibility(View.GONE);
        }
    }
    private String value(TextInputLayout layout) {
        EditText edit = layout.getEditText();
        return edit == null || edit.getText() == null ? "" : edit.getText().toString().trim();
    }
}

