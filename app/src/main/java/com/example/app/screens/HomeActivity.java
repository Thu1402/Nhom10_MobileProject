package com.example.app.screens;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.HorizontalScrollView;
import android.widget.TextView;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.content.res.ColorStateList;
import com.example.app.BaseNavigationActivity;
import com.example.app.R;
import com.example.app.App;
import com.example.app.data.FirebasePlaceRepository;
import com.example.app.data.FirebaseShareRepository;
import com.example.app.model.Place;
import com.example.app.ui.PlaceCardView;
import com.example.app.ui.Ui;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.firestore.ListenerRegistration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.List;
import java.util.Set;

public class HomeActivity extends BaseNavigationActivity {
    private static final long SEARCH_RENDER_DELAY_MS = 140L;
    private LinearLayout results;
    private TextInputEditText searchField;
    private ListenerRegistration placesListener;
    private ListenerRegistration sharesListener;
    private final List<Place> receivedShares = new ArrayList<>();
    private final List<Place> fallbackPlaces = createFallbackPlaces();
    private String selectedCategory = "Tất cả";
    private boolean placesLoaded;
    private boolean placesLoadFailed;
    private boolean sharesLoaded;
    private boolean sharesLoadFailed;
    private final Handler searchRenderHandler = new Handler(Looper.getMainLooper());
    private final Runnable pendingSearchRender = this::renderResults;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentViewWithNavigation(R.layout.activity_home);
        LinearLayout page = findViewById(R.id.home_content);
        page.addView(Ui.header(this, "KHÁM PHÁ", "Địa điểm chia sẻ"));
        TextInputLayout search = Ui.input(this, "Tìm địa điểm hoặc tag...", false);
        searchField = (TextInputEditText) search.getEditText();
        search.setHintTextColor(ColorStateList.valueOf(Color.BLACK));
        searchField.setHintTextColor(Color.BLACK);
        searchField.setTextColor(Color.BLACK);
        page.addView(search, Ui.margin(-1, -2, 0, 18, 0, 16, this));
        results = new LinearLayout(this);
        results.setOrientation(LinearLayout.VERTICAL);
        page.addView(results);

        searchField.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                searchRenderHandler.removeCallbacks(pendingSearchRender);
                searchRenderHandler.postDelayed(pendingSearchRender, SEARCH_RENDER_DELAY_MS);
            }
            @Override public void afterTextChanged(Editable s) { }
        });

        placesListener = FirebasePlaceRepository.listen(this, new FirebasePlaceRepository.PlacesCallback() {
            @Override public void onPlaces(List<Place> places) {
                App.PLACES.clear();
                App.PLACES.addAll(places);
                placesLoaded = true;
                placesLoadFailed = false;
                renderResults();
            }
            @Override public void onError(Exception error) {
                placesLoaded = true;
                placesLoadFailed = true;
                results.removeAllViews();
                results.addView(Ui.text(HomeActivity.this,
                        "Không tải được địa điểm.",
                        13, Ui.color(HomeActivity.this, R.color.muted), false));
            }
        });
    }

    private void renderResults() {
        if (results == null) return;
        results.removeAllViews();
        if (!placesLoaded) return;
        if (placesLoadFailed) {
            results.addView(Ui.text(this, "Không tải được địa điểm.",
                    13, Ui.color(this, R.color.muted), false));
            return;
        }
        String query = searchField == null || searchField.getText() == null
                ? "" : searchField.getText().toString().trim().toLowerCase(java.util.Locale.ROOT);
        Set<String> filterCategories = new LinkedHashSet<>();
        for (Place place : App.PLACES) filterCategories.add(place.category == null || place.category.trim().isEmpty()
                ? "Khác" : place.category.trim());
        for (Place place : fallbackPlaces) {
            String category = place.category == null || place.category.trim().isEmpty()
                    ? "Khác" : place.category.trim();
            filterCategories.add(category);
        }
        renderCategoryFilters(filterCategories);
        List<Place> matches = new ArrayList<>();
        for (Place place : App.PLACES) {
            String category = place.category == null || place.category.trim().isEmpty() ? "Khác" : place.category.trim();
            String searchable = (place.name + " " + category + " " + place.address + " " + place.tags)
                    .toLowerCase(java.util.Locale.ROOT);
            if (("Tất cả".equals(selectedCategory) || selectedCategory.equals(category))
                    && (query.isEmpty() || searchable.contains(query))) matches.add(place);
        }
        if (!matches.isEmpty()) {
            results.addView(sectionTitle("Địa điểm cộng đồng", matches.size() + " nơi"),
                    Ui.margin(-1, -2, 0, 0, 0, 10, this));
            for (Place place : matches) {
                int position = App.PLACES.indexOf(place);
                results.addView(new PlaceCardView(this, place, true, view -> open(position)),
                        Ui.margin(-1, 104, 0, 0, 0, 10, this));
            }
        }
        List<Place> recommendations = matchingRecommendations(query);
        if (!recommendations.isEmpty()) {
            renderRecommendations(recommendations);
        } else if (matches.isEmpty()) {
            results.addView(Ui.text(this, query.isEmpty()
                    ? "Chưa có gợi ý địa điểm."
                    : "Không tìm thấy địa điểm phù hợp.",
                    14, Ui.color(this, R.color.muted), false), Ui.margin(-1, -2, 0, 8, 0, 0, this));
        }
    }

    private Map<String, List<Integer>> groupSharedPlaces(Set<String> sharedPlaceIds) {
        Map<String, List<Integer>> groups = new LinkedHashMap<>();
        Set<String> addedIds = new HashSet<>();
        for (int i = 0; i < App.SHARED_PLACES.size(); i++) {
            Place place = App.SHARED_PLACES.get(i);
            if (!sharedPlaceIds.contains(place.id) || !addedIds.add(place.id)) continue;
            String category = place.category == null || place.category.trim().isEmpty()
                    ? "Khác" : place.category.trim();
            if (!groups.containsKey(category)) groups.put(category, new ArrayList<>());
            groups.get(category).add(i);
        }
        return groups;
    }

    private void renderCategoryFilters(Set<String> categories) {
        HorizontalScrollView scroll = new HorizontalScrollView(this);
        scroll.setHorizontalScrollBarEnabled(false);
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        addCategoryChip(row, "Tất cả");
        for (String category : categories) addCategoryChip(row, category);
        scroll.addView(row);
        results.addView(scroll, Ui.margin(-1, 40, 0, 0, 0, 10, this));
    }

    private void addCategoryChip(LinearLayout row, String category) {
        TextView chip = Ui.text(this, category, 12, Ui.color(this,
                selectedCategory.equals(category) ? R.color.ink : R.color.muted),
                selectedCategory.equals(category));
        chip.setGravity(android.view.Gravity.CENTER);
        chip.setPadding(Ui.dp(this, 14), 0, Ui.dp(this, 14), 0);
        GradientDrawable background = new GradientDrawable();
        background.setCornerRadius(Ui.dp(this, 24));
        if (selectedCategory.equals(category)) {
            background.setColor(Color.parseColor("#E6F1BC"));
        } else {
            background.setColor(Color.WHITE);
            background.setStroke(Ui.dp(this, 1), Color.parseColor("#E7E7DF"));
        }
        chip.setBackground(background);
        chip.setClickable(true);
        chip.setFocusable(true);
        chip.setOnClickListener(view -> {
            selectedCategory = category;
            renderResults();
        });
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-2, Ui.dp(this, 32));
        params.setMarginEnd(Ui.dp(this, 8));
        row.addView(chip, params);
    }

    private List<Place> matchingRecommendations(String query) {
        List<Place> matches = new ArrayList<>();
        for (Place place : fallbackPlaces) {
            String searchable = (place.name + " " + place.category + " " + place.address + " " + place.tags)
                    .toLowerCase(java.util.Locale.ROOT);
            boolean categoryMatches = "Tất cả".equals(selectedCategory)
                    || selectedCategory.equals(place.category);
            if (categoryMatches && (query.isEmpty() || searchable.contains(query))) matches.add(place);
        }
        return matches;
    }

    private void renderRecommendations(List<Place> recommendations) {
        results.addView(sectionTitle("Gợi ý địa điểm", recommendations.size() + " nơi"),
                Ui.margin(-1, -2, 0, 20, 0, 10, this));
        HorizontalScrollView scroll = new HorizontalScrollView(this);
        scroll.setHorizontalScrollBarEnabled(false);
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        for (Place place : recommendations) {
            PlaceCardView card = new PlaceCardView(this, place, true,
                    view -> openDemo(place));
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    Ui.dp(this, 286), Ui.dp(this, 104));
            params.setMarginEnd(Ui.dp(this, 12));
            row.addView(card, params);
        }
        scroll.addView(row);
        results.addView(scroll, Ui.margin(-1, 104, 0, 8, 0, 0, this));
    }

    private LinearLayout sectionTitle(String title, String action) {
        LinearLayout row = Ui.row(this);
        row.addView(Ui.text(this, title, 17, Ui.color(this, R.color.ink), true));
        row.addView(Ui.spacer(this));
        row.addView(Ui.text(this, action, 10, Ui.color(this, R.color.violet), true));
        return row;
    }

    private void open(int position) {
        startActivity(new android.content.Intent(this, PlaceDetailActivity.class)
                .putExtra(PlaceDetailActivity.PLACE_INDEX, position));
    }

    private void openShared(int position) {
        startActivity(new android.content.Intent(this, PlaceDetailActivity.class)
                .putExtra(PlaceDetailActivity.SHARED_PLACE_INDEX, position));
    }

    private void openDemo(Place place) {
        startActivity(new android.content.Intent(this, PlaceDetailActivity.class)
                .putExtra(PlaceDetailActivity.DEMO_PLACE, true)
                .putExtra(PlaceDetailActivity.DEMO_NAME, place.name)
                .putExtra(PlaceDetailActivity.DEMO_CATEGORY, place.category)
                .putExtra(PlaceDetailActivity.DEMO_ADDRESS, place.address)
                .putExtra(PlaceDetailActivity.DEMO_IMAGE, place.imageUrl)
                .putExtra(PlaceDetailActivity.DEMO_DESCRIPTION, place.description)
                .putExtra(PlaceDetailActivity.DEMO_TAGS, place.tags));
    }

    private List<Place> createFallbackPlaces() {
        List<Place> places = new ArrayList<>();
        places.add(new Place("", "", "Radar Coffee", "Quán cà phê",
                "235 Đ. Đặng Thuỳ Trâm, Bình Lợi Trung, Thành phố Hồ Chí Minh", "",
                "https://bizweb.dktcdn.net/thumb/1024x1024/100/441/818/products/zve03959-large-jpeg-05677f83-d9e2-4c7c-9606-51a9f2b00698.jpg",
                "Không gian rộng rãi, thoáng mát, thích hợp để làm việc hoặc trò chuyện cùng bạn bè.",
                "#coffee #workspace #binhthanh"));
        places.add(new Place("", "", "LAM COFFEE", "Quán cà phê",
                "294 Bình Lợi, Bình Lợi Trung, Thành phố Hồ Chí Minh", "",
                "https://dynamic-media-cdn.tripadvisor.com/media/photo-o/2d/75/af/f7/caption.jpg",
                "Quán có thiết kế ấm cúng, đồ uống đa dạng và nhân viên phục vụ thân thiện.",
                "#lamcoffee #chill #cafe"));
        places.add(new Place("", "", "Sunny farm Coffee and BBQ", "Cà phê & Ăn uống",
                "173/1a Bình Lợi, Bình Lợi Trung, Thành phố Hồ Chí Minh", "",
                "https://1phutsaigon.vn/wp-content/uploads/2022/10/sunny-farm-10.jpg",
                "Mô hình kết hợp độc đáo giữa quán cà phê và không gian nướng ngoài trời với nhiều góc sống ảo đẹp.",
                "#sunnyfarm #bbq #checkin"));
        return places;
    }

    @Override protected void onDestroy() {
        searchRenderHandler.removeCallbacks(pendingSearchRender);
        if (placesListener != null) placesListener.remove();
        super.onDestroy();
    }
}
