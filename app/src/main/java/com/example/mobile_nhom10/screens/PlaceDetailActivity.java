package com.example.mobile_nhom10.screens;

import android.content.Intent;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import com.bumptech.glide.Glide;
import com.google.android.material.button.MaterialButton;
import com.example.mobile_nhom10.R;
import com.example.mobile_nhom10.data.AppData;
import com.example.mobile_nhom10.model.Place;
import com.example.mobile_nhom10.ui.Ui;

public class PlaceDetailActivity extends AppCompatActivity {
    public static final String PLACE_INDEX = "place_index";

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_place_detail);
        showPlace();
    }

    private void showPlace() {
        int index = getIntent().getIntExtra(PLACE_INDEX, 0);
        if (AppData.PLACES.isEmpty()) { finish(); return; }
        index = Math.max(0, Math.min(index, AppData.PLACES.size() - 1));
        final int selectedIndex = index;
        Place place = AppData.PLACES.get(index);
        LinearLayout page = findViewById(R.id.detail_content);
        page.removeAllViews();

        ImageView hero = new ImageView(this);
        hero.setScaleType(ImageView.ScaleType.CENTER_CROP);
        Glide.with(this).load(place.imageUrl).centerCrop().into(hero);
        page.addView(hero, new LinearLayout.LayoutParams(-1, Ui.dp(this, 280)));

        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(Ui.dp(this, 20), Ui.dp(this, 22), Ui.dp(this, 20), Ui.dp(this, 24));
        TextView back = Ui.text(this, "‹  Quay lại", 12, Ui.color(this, R.color.violet), true);
        back.setOnClickListener(view -> finish());
        body.addView(back);
        body.addView(Ui.text(this, place.category.toUpperCase(), 10, Ui.color(this, R.color.violet), true), Ui.margin(-1, -2, 0, 20, 0, 0, this));
        body.addView(Ui.text(this, place.name, 28, Ui.color(this, R.color.ink), true), Ui.margin(-1, -2, 0, 5, 0, 0, this));
        body.addView(Ui.text(this, "⌖  " + place.address + "  ·  " + place.distance, 11, Ui.color(this, R.color.muted), false), Ui.margin(-1, -2, 0, 8, 0, 18, this));
        body.addView(Ui.text(this, "Về địa điểm này", 17, Ui.color(this, R.color.ink), true));
        body.addView(Ui.text(this, place.description, 13, Ui.color(this, R.color.muted), false), Ui.margin(-1, -2, 0, 8, 0, 24, this));
        MaterialButton edit = Ui.primaryButton(this, "Chỉnh sửa địa điểm");
        edit.setOnClickListener(view -> startActivity(new Intent(this, EditPlaceActivity.class).putExtra(PLACE_INDEX, selectedIndex)));
        body.addView(edit, Ui.margin(-1, 54, 0, 10, 0, 0, this));
        page.addView(body);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (findViewById(R.id.detail_content) != null && !AppData.PLACES.isEmpty()) showPlace();
    }
}
