package com.example.app.ui;

import android.content.Context;
import android.text.Editable;
import android.text.TextWatcher;
import android.widget.EditText;

import androidx.appcompat.app.AlertDialog;

import com.google.android.material.textfield.TextInputLayout;

import java.util.ArrayList;
import java.util.List;

public final class PlaceFormUtils {
    private static final String[] CATEGORIES = {
            "Nhà hàng", "Quán cà phê", "Quán ăn", "Khách sạn", "Điểm tham quan",
            "Công viên", "Mua sắm", "Giải trí", "Trường học", "Bệnh viện", "Khác"
    };

    private PlaceFormUtils() { }

    public static void setupCategoryDropdown(Context context, TextInputLayout layout, String value) {
        EditText field = layout.getEditText();
        if (field == null) return;
        if (value != null) field.setText(value);
        layout.setEndIconMode(TextInputLayout.END_ICON_CUSTOM);
        layout.setEndIconDrawable(android.R.drawable.arrow_down_float);
        layout.setEndIconContentDescription("Chọn danh mục");
        layout.setEndIconOnClickListener(view -> new AlertDialog.Builder(context)
                .setTitle("Chọn danh mục")
                .setItems(CATEGORIES, (dialog, which) -> {
                    if (which == CATEGORIES.length - 1) showCustomCategoryDialog(context, field);
                    else field.setText(CATEGORIES[which]);
                })
                .show());
    }

    private static void showCustomCategoryDialog(Context context, EditText categoryField) {
        EditText customCategory = new EditText(context);
        customCategory.setSingleLine(true);
        customCategory.setHint("Nhập tên danh mục");
        new AlertDialog.Builder(context)
                .setTitle("Danh mục khác")
                .setView(customCategory)
                .setNegativeButton("Hủy", null)
                .setPositiveButton("Chọn", (dialog, which) -> {
                    String value = customCategory.getText() == null
                            ? "" : customCategory.getText().toString().trim();
                    if (!value.isEmpty()) categoryField.setText(value);
                })
                .show();
    }

    public static void bindTagPrefixing(EditText editText) {
        editText.addTextChangedListener(new TextWatcher() {
            private boolean formatting;

            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { }

            @Override public void afterTextChanged(Editable editable) {
                if (formatting) return;
                String formatted = normalizeTags(editable.toString());
                if (!formatted.equals(editable.toString())) {
                    formatting = true;
                    editText.setText(formatted);
                    editText.setSelection(formatted.length());
                    formatting = false;
                }
            }
        });
    }

    public static String normalizeTags(String value) {
        if (value == null || value.trim().isEmpty()) return "";
        String[] parts = value.split(",");
        List<String> tags = new ArrayList<>();
        for (String part : parts) {
            String tag = part.trim().replaceFirst("^#+", "").trim();
            if (!tag.isEmpty()) tags.add("#" + tag);
        }
        return String.join(", ", tags);
    }

    public static String tagsForDisplay(String value) {
        return normalizeTags(value).replace(", ", "   ");
    }
}
