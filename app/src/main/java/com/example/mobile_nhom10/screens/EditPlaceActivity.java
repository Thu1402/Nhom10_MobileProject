package com.example.mobile_nhom10.screens;

import android.content.Intent;
import android.os.Bundle;
import android.widget.LinearLayout;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.example.mobile_nhom10.R;
import com.example.mobile_nhom10.data.AppData;
import com.example.mobile_nhom10.model.Place;
import com.example.mobile_nhom10.ui.Ui;

public class EditPlaceActivity extends AppCompatActivity {
    private int index;
    private TextInputLayout nameInput, categoryInput, addressInput, descriptionInput;
    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_edit_place);
        index = getIntent().getIntExtra(PlaceDetailActivity.PLACE_INDEX, 0);
        if (AppData.PLACES.isEmpty()) { finish(); return; }
        index = Math.max(0, Math.min(index, AppData.PLACES.size() - 1));
        Place place = AppData.PLACES.get(index);
        LinearLayout page = findViewById(R.id.edit_place_content);
        page.addView(Ui.header(this, "CHỈNH SỬA", "Cập nhật địa điểm"));
        nameInput = Ui.input(this, "Tên địa điểm", false);
        categoryInput = Ui.input(this, "Danh mục", false);
        addressInput = Ui.input(this, "Địa chỉ", false);
        descriptionInput = Ui.input(this, "Mô tả địa điểm", true);
        setValue(nameInput, place.name); setValue(categoryInput, place.category);
        setValue(addressInput, place.address); setValue(descriptionInput, place.description);
        page.addView(nameInput, Ui.margin(-1, -2, 0, 24, 0, 14, this));
        page.addView(categoryInput, Ui.margin(-1, -2, 0, 0, 0, 14, this));
        page.addView(addressInput, Ui.margin(-1, -2, 0, 0, 0, 14, this));
        page.addView(descriptionInput, Ui.margin(-1, -2, 0, 0, 0, 22, this));
        MaterialButton save = Ui.primaryButton(this, "Lưu thay đổi");
        save.setOnClickListener(view -> saveChanges());
        page.addView(save, Ui.margin(-1, 54, 0, 8, 0, 0, this));
        MaterialButton cancel = Ui.softButton(this, "Hủy");
        cancel.setOnClickListener(view -> finish());
        page.addView(cancel, Ui.margin(-1, 50, 0, 10, 0, 0, this));
    }
    private void saveChanges() {
        String name = value(nameInput), category = value(categoryInput), address = value(addressInput), description = value(descriptionInput);
        if (name.isEmpty() || category.isEmpty() || address.isEmpty()) {
            Toast.makeText(this, "Vui lòng nhập tên, danh mục và địa chỉ", Toast.LENGTH_SHORT).show(); return;
        }
        Place old = AppData.PLACES.get(index);
        AppData.PLACES.set(index, new Place(name, category, address, old.distance, old.imageUrl, description));
        Toast.makeText(this, "Đã cập nhật địa điểm", Toast.LENGTH_SHORT).show();
        Intent intent = new Intent(this, PlaceDetailActivity.class);
        intent.putExtra(PlaceDetailActivity.PLACE_INDEX, index);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        startActivity(intent);
        finish();
    }
    private void setValue(TextInputLayout layout, String value) {
        TextInputEditText edit = (TextInputEditText) layout.getEditText();
        if (edit != null) edit.setText(value);
    }
    private String value(TextInputLayout layout) {
        TextInputEditText edit = (TextInputEditText) layout.getEditText();
        return edit == null || edit.getText() == null ? "" : edit.getText().toString().trim();
    }
}
