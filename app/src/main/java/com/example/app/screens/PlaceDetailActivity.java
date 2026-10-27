package com.example.app.screens;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.FrameLayout;
import android.view.Gravity;
import android.view.ViewGroup;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AlertDialog;
import com.bumptech.glide.Glide;
import com.google.android.material.button.MaterialButton;
import com.example.app.R;
import com.example.app.App;
import com.example.app.data.FirebasePlaceRepository;
import com.example.app.model.Place;
import com.example.app.ui.Ui;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.ListenerRegistration;

public class PlaceDetailActivity extends AppCompatActivity {
    public static final String PLACE_INDEX = "place_index";
    public static final String SHARED_PLACE_INDEX = "shared_place_index";
    public static final String SHARED_PLACE_ID = "shared_place_id";
    public static final String DEMO_PLACE = "demo_place";
    public static final String DEMO_NAME = "demo_name";
    public static final String DEMO_CATEGORY = "demo_category";
    public static final String DEMO_ADDRESS = "demo_address";
    public static final String DEMO_IMAGE = "demo_image";
    public static final String DEMO_DESCRIPTION = "demo_description";
    public static final String DEMO_TAGS = "demo_tags";
    private String placeId = "";
    private int selectedIndex;
    private Place demoPlace;
    private ListenerRegistration placeListener;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_place_detail);
        int widthDp = getResources().getConfiguration().screenWidthDp;
        if (widthDp >= 600) {
            View detailPage = ((ViewGroup) findViewById(android.R.id.content)).getChildAt(0);
            int pageWidth = Math.min(980, widthDp - 96);
            detailPage.setLayoutParams(new FrameLayout.LayoutParams(
                    Math.round(pageWidth * getResources().getDisplayMetrics().density),
                    FrameLayout.LayoutParams.MATCH_PARENT, Gravity.CENTER_HORIZONTAL));
        }
        if (getIntent().getBooleanExtra(DEMO_PLACE, false)) {
            demoPlace = new Place("", "", getIntent().getStringExtra(DEMO_NAME),
                    getIntent().getStringExtra(DEMO_CATEGORY), getIntent().getStringExtra(DEMO_ADDRESS), "",
                    getIntent().getStringExtra(DEMO_IMAGE), getIntent().getStringExtra(DEMO_DESCRIPTION),
                    getIntent().getStringExtra(DEMO_TAGS));
        }
        int sharedIndex = getIntent().getIntExtra(SHARED_PLACE_INDEX, -1);
        if (demoPlace == null && sharedIndex >= 0 && sharedIndex < App.SHARED_PLACES.size()) {
            demoPlace = App.SHARED_PLACES.get(sharedIndex);
        }
        String sharedPlaceId = getIntent().getStringExtra(SHARED_PLACE_ID);
        if (demoPlace == null && sharedPlaceId != null && !sharedPlaceId.isEmpty()) {
            for (Place sharedPlace : App.SHARED_PLACES) {
                if (sharedPlaceId.equals(sharedPlace.id)) {
                    demoPlace = sharedPlace;
                    break;
                }
            }
            if (demoPlace == null) {
                Toast.makeText(this, "Địa điểm chia sẻ không còn khả dụng.", Toast.LENGTH_SHORT).show();
                finish();
                return;
            }
        }
        selectedIndex = getIntent().getIntExtra(PLACE_INDEX, 0);
        if (demoPlace == null && selectedIndex >= 0 && selectedIndex < App.PLACES.size()) {
            placeId = App.PLACES.get(selectedIndex).id;
        }
        showPlace();
        if (!placeId.isEmpty()) listenForPlaceChanges();
    }

    private void listenForPlaceChanges() {
        placeListener = FirebasePlaceRepository.listenToPlace(this, placeId,
                new FirebasePlaceRepository.PlaceListenerCallback() {
                    @Override public void onPlace(Place updated) {
                        for (int i = 0; i < App.PLACES.size(); i++) {
                            if (placeId.equals(App.PLACES.get(i).id)) {
                                App.PLACES.set(i, updated);
                                selectedIndex = i;
                                break;
                            }
                        }
                        showPlace();
                    }

                    @Override public void onMissing() {
                        if (!isFinishing()) {
                            Toast.makeText(PlaceDetailActivity.this,
                                    "Địa điểm này đã bị xóa khỏi Firebase.", Toast.LENGTH_SHORT).show();
                            finish();
                        }
                    }

                    @Override public void onError(Exception error) {
                        android.util.Log.w("PlaceDetailActivity", "Realtime place listener failed", error);
                    }
                });
    }

    private void showPlace() {
        if (demoPlace != null) {
            renderPlace(demoPlace);
            return;
        }
        int index = selectedIndex;
        if (App.PLACES.isEmpty()) { finish(); return; }
        if (!placeId.isEmpty()) {
            for (int i = 0; i < App.PLACES.size(); i++) {
                if (placeId.equals(App.PLACES.get(i).id)) {
                    index = i;
                    break;
                }
            }
        }
        index = Math.max(0, Math.min(index, App.PLACES.size() - 1));
        selectedIndex = index;
        final int selectedIndex = index;
        Place place = App.PLACES.get(index);
        renderPlace(place);
    }

    private void renderPlace(Place place) {
        final int currentIndex = selectedIndex;
        LinearLayout page = findViewById(R.id.detail_content);
        page.removeAllViews();

        FrameLayout heroFrame = new FrameLayout(this);
        ImageView hero = new ImageView(this);
        hero.setScaleType(ImageView.ScaleType.CENTER_CROP);
        Glide.with(this).load(place.imageUrl).centerCrop().into(hero);
        heroFrame.addView(hero, new FrameLayout.LayoutParams(-1, -1));
        TextView backButton = Ui.text(this, "<", 22, Ui.color(this, R.color.ink), true);
        backButton.setGravity(Gravity.CENTER);
        backButton.setContentDescription("Quay lại");
        backButton.setFocusable(true);
        backButton.setClickable(true);
        GradientDrawable backCircle = new GradientDrawable();
        backCircle.setShape(GradientDrawable.OVAL);
        backCircle.setColor(Color.WHITE);
        backButton.setBackground(backCircle);
        backButton.setElevation(Ui.dp(this, 4));
        backButton.setOnClickListener(view -> finish());
        FrameLayout.LayoutParams backParams = new FrameLayout.LayoutParams(
                Ui.dp(this, 44), Ui.dp(this, 44), Gravity.TOP | Gravity.LEFT);
        backParams.leftMargin = Ui.dp(this, 16);
        backParams.topMargin = Ui.dp(this, 16);
        heroFrame.addView(backButton, backParams);
        page.addView(heroFrame, new LinearLayout.LayoutParams(-1, Ui.dp(this, 280)));

        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(Ui.dp(this, 20), Ui.dp(this, 22), Ui.dp(this, 20), Ui.dp(this, 24));
        body.addView(Ui.text(this, place.category.toUpperCase(), 10, Ui.color(this, R.color.violet), true), Ui.margin(-1, -2, 0, 0, 0, 0, this));
        body.addView(Ui.text(this, place.name, 28, Ui.color(this, R.color.ink), true), Ui.margin(-1, -2, 0, 5, 0, 0, this));
        String addressLine = "⌖  " + place.address + (place.distance.isEmpty() ? "" : "  ·  " + place.distance);
        body.addView(Ui.text(this, addressLine, 11, Ui.color(this, R.color.muted), false), Ui.margin(-1, -2, 0, 8, 0, 18, this));
        body.addView(Ui.text(this, Ui.tagsForDisplay(place.tags), 12, Ui.color(this, R.color.violet), true), Ui.margin(-1, -2, 0, 0, 0, 18, this));
        body.addView(Ui.text(this, "Về địa điểm này", 17, Ui.color(this, R.color.ink), true));
        body.addView(Ui.text(this, place.description, 13, Ui.color(this, R.color.muted), false), Ui.margin(-1, -2, 0, 8, 0, 24, this));
        MaterialButton edit = Ui.primaryButton(this, "Chỉnh sửa địa điểm");
        if (demoPlace != null || FirebaseAuth.getInstance().getCurrentUser() == null
                || !place.ownerId.equals(FirebaseAuth.getInstance().getCurrentUser().getUid())) {
            edit.setVisibility(View.GONE);
        }
        edit.setOnClickListener(view -> startActivity(new Intent(this, EditPlaceActivity.class).putExtra(PLACE_INDEX, currentIndex)));
        body.addView(edit, Ui.margin(-1, 54, 0, 10, 0, 0, this));
        MaterialButton delete = Ui.softButton(this, "Xóa bài đăng");
        delete.setTextColor(Color.rgb(180, 45, 45));
        if (demoPlace != null || !App.isAdmin()) delete.setVisibility(View.GONE);
        delete.setOnClickListener(view -> new AlertDialog.Builder(this)
                .setTitle("Xóa bài đăng?")
                .setMessage("Bài đăng ‘" + place.name + "’ sẽ bị xóa khỏi danh sách chung.")
                .setNegativeButton("Hủy", null)
                .setPositiveButton("Xóa", (dialog, which) -> FirebasePlaceRepository.delete(
                        this, place.id, new FirebasePlaceRepository.ResultCallback() {
                            @Override public void onSuccess() {
                                for (int i = App.PLACES.size() - 1; i >= 0; i--) {
                                    if (place.id.equals(App.PLACES.get(i).id)) App.PLACES.remove(i);
                                }
                                Toast.makeText(PlaceDetailActivity.this, "Đã xóa bài đăng.", Toast.LENGTH_SHORT).show();
                                finish();
                            }
                            @Override public void onError(Exception error) {
                                Toast.makeText(PlaceDetailActivity.this,
                                        "Không xóa được bài đăng. Kiểm tra kết nối và Firestore Rules.", Toast.LENGTH_LONG).show();
                            }
                        }))
                .show());
        body.addView(delete, Ui.margin(-1, 52, 0, 10, 0, 0, this));
        MaterialButton share = Ui.softButton(this, "Chia sẻ địa điểm");
        if (demoPlace != null) share.setVisibility(View.GONE);
        share.setOnClickListener(view -> startActivity(new Intent(this, com.example.app.share.ShareLocationActivity.class).putExtra("PLACE_NAME", place.name).putExtra(PLACE_INDEX, currentIndex)));
        body.addView(share, Ui.margin(-1, 52, 0, 10, 0, 0, this));
        page.addView(body);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (findViewById(R.id.detail_content) != null && (demoPlace != null || !App.PLACES.isEmpty())) showPlace();
    }

    @Override protected void onDestroy() {
        if (placeListener != null) placeListener.remove();
        super.onDestroy();
    }
}

