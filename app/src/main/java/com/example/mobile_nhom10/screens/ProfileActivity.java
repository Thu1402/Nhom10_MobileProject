package com.example.mobile_nhom10.screens;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageButton;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.mobile_nhom10.R;
import com.google.android.material.bottomsheet.BottomSheetDialog;

public class ProfileActivity extends AppCompatActivity {

    private BottomSheetDialog settingsDialog;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_profile);

        ImageButton btnSettings = findViewById(R.id.btnSettings);

        btnSettings.setOnClickListener(v -> showSettings());

        findViewById(R.id.btnEditProfile).setOnClickListener(v ->
                Toast.makeText(this,
                        "Chỉnh sửa hồ sơ",
                        Toast.LENGTH_SHORT).show());
        findViewById(R.id.txtSeeAll).setOnClickListener(v ->
                Toast.makeText(this,
                        "Xem tất cả bộ sưu tập",
                        Toast.LENGTH_SHORT).show());

        findViewById(R.id.collectionCard).setOnClickListener(v ->
                Toast.makeText(this,
                        "Những nơi muốn trở lại",
                        Toast.LENGTH_SHORT).show());
    }

    private void showSettings() {
        if (settingsDialog != null && settingsDialog.isShowing()) {
            return;
        }

        View sheet = LayoutInflater.from(this)
                .inflate(R.layout.dialog_setting, null, false);

        settingsDialog = new BottomSheetDialog(this);
        settingsDialog.setContentView(sheet);
        settingsDialog.setDismissWithAnimation(true);

        sheet.findViewById(R.id.btnCloseSettings)
                .setOnClickListener(v -> settingsDialog.dismiss());

        setSettingAction(sheet, R.id.rowPersonal,
                "Thông tin cá nhân");

        setSettingAction(sheet, R.id.rowNotifications,
                "Thông báo");

        setSettingAction(sheet, R.id.rowPrivacy,
                "Quyền riêng tư");

        setSettingAction(sheet, R.id.rowLogout,
                "Đăng xuất");

        setSettingAction(sheet, R.id.rowDelete,
                "Xóa tài khoản");

        settingsDialog.setOnShowListener(dialog -> {
            View bottomSheet = settingsDialog.findViewById(
                    com.google.android.material.R.id.design_bottom_sheet);

            if (bottomSheet != null) {
                bottomSheet.setBackgroundResource(
                        android.R.color.transparent);
            }
        });

        settingsDialog.show();
    }

    private void setSettingAction(
            View root, int viewId, String label) {

        View row = root.findViewById(viewId);

        if (row != null) {
            row.setOnClickListener(v ->
                    Toast.makeText(
                            ProfileActivity.this,
                            label,
                            Toast.LENGTH_SHORT
                    ).show());
        }
    }
}
