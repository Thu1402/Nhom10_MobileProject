package com.example.mobile_nhom10.screens;

import android.content.Intent;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.example.mobile_nhom10.R;
import com.example.mobile_nhom10.data.AppData;
import com.example.mobile_nhom10.model.Place;
import com.example.mobile_nhom10.ui.Ui;

public class AddPlaceActivity extends AppCompatActivity {
    private TextInputLayout nameInput, categoryInput, addressInput, descriptionInput;
    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_add_place);
        LinearLayout page = findViewById(R.id.add_place_content);
        page.addView(Ui.header(this, "ĐỊA ĐIỂM MỚI", "Ghim một nơi hay"));
        TextView imagePicker = Ui.text(this, "▧\n\nThêm hình ảnh\nChọn ảnh cho địa điểm", 12, Ui.color(this, R.color.violet), true);
        imagePicker.setGravity(Gravity.CENTER);
        imagePicker.setBackground(Ui.borderedBackground(Ui.color(this, R.color.surface), Ui.color(this, R.color.line), 22, this));
        imagePicker.setOnClickListener(view -> Toast.makeText(this, "Chức năng chọn ảnh sẽ được kết nối sau", Toast.LENGTH_SHORT).show());
        page.addView(imagePicker, Ui.margin(-1, 160, 0, 22, 0, 24, this));
        nameInput = Ui.input(this, "Tên địa điểm", false);
        categoryInput = Ui.input(this, "Danh mục", false);
        addressInput = Ui.input(this, "Địa chỉ", false);
        descriptionInput = Ui.input(this, "Mô tả địa điểm", true);
        page.addView(nameInput, Ui.margin(-1, -2, 0, 0, 0, 14, this));
        page.addView(categoryInput, Ui.margin(-1, -2, 0, 0, 0, 14, this));
        page.addView(addressInput, Ui.margin(-1, -2, 0, 0, 0, 14, this));
        page.addView(descriptionInput, Ui.margin(-1, -2, 0, 0, 0, 18, this));
        MaterialButton location = Ui.softButton(this, "⌖  Vị trí địa điểm");
        location.setOnClickListener(view -> Toast.makeText(this, "Có thể nhập địa chỉ ở trường phía trên", Toast.LENGTH_SHORT).show());
        page.addView(location, Ui.margin(-1, 52, 0, 0, 0, 22, this));
        MaterialButton save = Ui.primaryButton(this, "Lưu địa điểm");
        save.setOnClickListener(view -> savePlace());
        page.addView(save, Ui.margin(-1, 54, 0, 8, 0, 0, this));
    }
    private void savePlace() {
        String name = value(nameInput);
        String category = value(categoryInput);
        String address = value(addressInput);
        String description = value(descriptionInput);
        if (name.isEmpty() || category.isEmpty() || address.isEmpty()) {
            Toast.makeText(this, "Vui lòng nhập tên, danh mục và địa chỉ", Toast.LENGTH_SHORT).show();
            return;
        }
        String image = AppData.PLACES.isEmpty() ? "" : AppData.PLACES.get(0).imageUrl;
        AppData.PLACES.add(new Place(name, category, address, "Chưa xác định", image, description));
        Toast.makeText(this, "Đã thêm địa điểm", Toast.LENGTH_SHORT).show();
        Intent intent = new Intent(this, HomeActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        startActivity(intent);
        finish();
    }
    private String value(TextInputLayout layout) {
        TextInputEditText edit = (TextInputEditText) layout.getEditText();
        return edit == null || edit.getText() == null ? "" : edit.getText().toString().trim();
    }
}
