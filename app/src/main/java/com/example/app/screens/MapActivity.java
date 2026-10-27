package com.example.app.screens;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Build;
import android.os.Looper;
import android.os.SystemClock;
import android.os.Handler;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.content.Context;
import android.app.AlertDialog;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Button;
import android.widget.Toast;
import android.widget.ScrollView;
import android.text.Editable;
import android.text.TextWatcher;
import androidx.annotation.NonNull;
import com.bumptech.glide.Glide;
import com.example.app.BaseNavigationActivity;
import com.example.app.R;
import com.example.app.App;
import com.example.app.data.FirebasePlaceRepository;
import com.example.app.data.FirebaseShareRepository;
import com.example.app.model.Place;
import com.example.app.ui.Ui;

import com.google.firebase.firestore.ListenerRegistration;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.net.URLEncoder;
import android.widget.LinearLayout.LayoutParams;
import org.json.JSONObject;
import org.json.JSONArray;
import android.view.VelocityTracker;

public class MapActivity extends BaseNavigationActivity {
    private static final int LOCATION_PERMISSION_REQUEST = 702;

    private final ExecutorService geocoderExecutor = Executors.newFixedThreadPool(3);
    private final ExecutorService nearbyExecutor = Executors.newSingleThreadExecutor();
    private final ExecutorService routeExecutor = Executors.newSingleThreadExecutor();
    private final Handler searchHandler = new Handler(Looper.getMainLooper());
    private WebView mapView;
    private TextView placeCount;
    private TextView placeName;
    private TextView placeAddress;
    private TextView placeDistance;
    private TextView nearbyStatus;
    private ImageView placePhoto;
    private LinearLayout nearbyList;
    private LinearLayout searchSuggestionList;
    private ScrollView searchSuggestions;
    private int searchRequestId;
    private Runnable pendingSearch;
    private int selectedIndex;
    private int selectedNearbyIndex = -1;
    private Place selectedSharedPlace;
    private boolean mapReady;
    private boolean centerOnNextLocation;
    private boolean locationTrackingRequested;
    private boolean locationUpdatesActive;
    private String selectedSavedPlaceId = "";
    private int locationSessionToken;
    private volatile boolean nearbyRequestInFlight;
    private double nearbyCenterLatitude = Double.NaN;
    private double nearbyCenterLongitude = Double.NaN;
    private long nearbyLoadedAt;
    private long nearbyRetryAfter;
    private final List<Place> nearbyPlaces = new ArrayList<>();
    private final List<TextView> nearbyCards = new ArrayList<>();
    private final List<Place> receivedShares = new ArrayList<>();
    private final List<Place> sharedMapPlaces = new ArrayList<>();
    private ListenerRegistration placesListener;
    private ListenerRegistration receivedSharesListener;
    private LocationListener pendingLocationListener;
    private LocationManager locationManager;
    private Location currentLocation;
    private boolean pendingRoute;
    private double pendingRouteLatitude;
    private double pendingRouteLongitude;
    private String pendingRouteLabel = "";
    private int routeRequestId;
    private float sheetTouchStartY;
    private float sheetStartTranslationY;
    private VelocityTracker sheetVelocityTracker;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentViewWithNavigation(R.layout.activity_map);
        mapView = findViewById(R.id.mapView);
        WebSettings settings = mapView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setUserAgentString(settings.getUserAgentString() + " TapMap/1.0");
        mapView.addJavascriptInterface(this, "TapMap");
        mapView.setWebViewClient(new WebViewClient() {
            @Override public void onPageFinished(WebView view, String url) {
                mapReady = true;
                addSavedPlaceMarkers();
                if (!App.PLACES.isEmpty()) showSelectedPlace(true);
                renderSharedPlaceMarkers();
                if (locationTrackingRequested) startLocationUpdates(false);
            }
        });
        loadInteractiveMap();

        placeCount = findViewById(R.id.tvPlaceCount);
        placeName = findViewById(R.id.tvPlaceName);
        placeAddress = findViewById(R.id.tvPlaceAddress);
        placeDistance = findViewById(R.id.tvPlaceDistance);
        placePhoto = findViewById(R.id.ivPlacePhoto);
        nearbyStatus = findViewById(R.id.tvNearbyStatus);
        nearbyList = findViewById(R.id.nearbyList);
        searchSuggestionList = findViewById(R.id.searchSuggestionList);
        searchSuggestions = findViewById(R.id.searchSuggestions);
        setupDraggableSheet();
        findViewById(R.id.placeCard).setOnClickListener(v -> openPlace());
        findViewById(R.id.btnAddPlaceAtLocation).setOnClickListener(v ->
                startActivity(new Intent(this, AddPlaceActivity.class)));
        findViewById(R.id.routeButton).setOnClickListener(v -> openDirections());
        findViewById(R.id.categoryButton).setOnClickListener(v -> showMyLocation());
        findViewById(R.id.btnMapBack).setOnClickListener(v -> finish());
        findViewById(R.id.btnZoomIn).setOnClickListener(v -> animateMapControl(v, "zoomIn()"));
        findViewById(R.id.btnZoomOut).setOnClickListener(v -> animateMapControl(v, "zoomOut()"));

        EditText search = findViewById(R.id.etMapSearch);
        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence text, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence text, int start, int before, int count) {
                showSearchSuggestions(text.toString().trim());
            }
            @Override public void afterTextChanged(Editable text) { }
        });
        search.setOnEditorActionListener((view, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH || actionId == EditorInfo.IME_NULL) {
                dismissSearchSuggestions();
                searchLocation(view.getText().toString().trim());
                hideSearchKeyboard(view);
                return true;
            }
            return false;
        });
        listenForPlaces();
        listenForReceivedShares();
    }

    private void showSearchSuggestions(String query) {
        searchSuggestions.animate().cancel();
        searchRequestId++;
        if (pendingSearch != null) searchHandler.removeCallbacks(pendingSearch);
        searchSuggestionList.removeAllViews();
        if (query.isEmpty()) {
            searchSuggestions.setVisibility(View.GONE);
            searchSuggestions.setAlpha(1f);
            return;
        }

        String normalized = query.toLowerCase(Locale.ROOT);
        List<Place> savedMatches = new ArrayList<>();
        for (int index = 0; index < App.PLACES.size(); index++) {
            Place place = App.PLACES.get(index);
            String searchable = (place.name + " " + place.address + " " + place.tags).toLowerCase(Locale.ROOT);
            if (searchable.contains(normalized)) {
                savedMatches.add(Place.savedSearchResult(index, place));
                if (savedMatches.size() == 5) break;
            }
        }
        renderSearchSuggestions(savedMatches);
        searchSuggestions.setVisibility(View.VISIBLE);
        searchSuggestions.setAlpha(0f);
        searchSuggestions.animate().alpha(1f).setDuration(150).start();

        final int requestId = searchRequestId;
        pendingSearch = () -> geocoderExecutor.execute(() -> fetchAddressSuggestions(query, requestId));
        searchHandler.postDelayed(pendingSearch, 350);
    }

    private void fetchAddressSuggestions(String query, int requestId) {
        if (!Geocoder.isPresent()) return;
        try {
            @SuppressWarnings("deprecation")
            List<Address> addresses = new Geocoder(this, new Locale("vi", "VN"))
                    .getFromLocationName(query + ", Việt Nam", 6,
                            8.18, 102.14, 23.39, 109.47);
            List<Place> results = new ArrayList<>();
            if (addresses != null) {
                for (Address address : addresses) {
                    String title = address.getFeatureName();
                    if (title == null || title.trim().isEmpty()) title = address.getAddressLine(0);
                    if (title == null || title.trim().isEmpty()) title = query;
                    String subtitle = address.getAddressLine(0);
                    if (subtitle == null) subtitle = "Địa chỉ phù hợp";
                    results.add(Place.addressSearchResult(title, subtitle,
                            address.getLatitude(), address.getLongitude()));
                }
            }
            runOnUiThread(() -> {
                if (isFinishing() || requestId != searchRequestId) return;
                List<Place> combined = new ArrayList<>();
                for (int i = 0; i < searchSuggestionList.getChildCount(); i++) {
                    Object tag = searchSuggestionList.getChildAt(i).getTag();
                    if (tag instanceof Place) combined.add((Place) tag);
                }
                for (Place result : results) {
                    if (combined.size() >= 10) break;
                    boolean duplicate = false;
                    for (Place existing : combined) {
                        if (existing.title.equalsIgnoreCase(result.title)
                                || (existing.hasCoordinates()
                                && Math.abs(existing.latitude - result.latitude) < 0.0005
                                && Math.abs(existing.longitude - result.longitude) < 0.0005)) {
                            duplicate = true;
                            break;
                        }
                    }
                    if (!duplicate) combined.add(result);
                }
                renderSearchSuggestions(combined);
            });
        } catch (IOException | IllegalArgumentException ignored) { }
    }

    private void renderSearchSuggestions(List<Place> results) {
        searchSuggestionList.removeAllViews();
        if (results.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText("Không tìm thấy địa điểm phù hợp");
            empty.setTextColor(getColor(R.color.ink));
            empty.setTextSize(14);
            empty.setPadding(dp(16), dp(15), dp(16), dp(15));
            searchSuggestionList.addView(empty);
        } else {
            for (Place suggestion : results) {
                TextView row = new TextView(this);
                row.setText(suggestion.title + "\n" + suggestion.subtitle);
                row.setTextColor(getColor(R.color.ink));
                row.setTextSize(14);
                row.setMaxLines(2);
                row.setPadding(dp(16), dp(12), dp(16), dp(12));
                row.setTag(suggestion);
                row.setBackgroundResource(android.R.drawable.list_selector_background);
                row.setOnClickListener(v -> selectSearchSuggestion(suggestion));
                searchSuggestionList.addView(row, new LinearLayout.LayoutParams(
                        LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
            }
        }
        ViewGroup.LayoutParams params = searchSuggestions.getLayoutParams();
        params.height = results.size() > 5 ? dp(280) : LayoutParams.WRAP_CONTENT;
        searchSuggestions.setLayoutParams(params);
    }

    private void selectSearchSuggestion(Place suggestion) {
        dismissSearchSuggestions();
        hideSearchKeyboard(findViewById(R.id.etMapSearch));
        clearRoute();
        if (suggestion.savedPlaceIndex >= 0) {
            selectedIndex = suggestion.savedPlaceIndex;
            showSelectedPlace(true);
            return;
        }
        selectedSharedPlace = null;
        selectedNearbyIndex = -1;
        placeCount.setText("Kết quả");
        placeName.setText(suggestion.title);
        placeAddress.setText(suggestion.subtitle);
        placeDistance.setText("");
        placePhoto.setImageResource(R.drawable.bg_empty_place_photo);
        pulsePlaceCard();
        moveMap(suggestion.latitude, suggestion.longitude, suggestion.title);
    }

    private void dismissSearchSuggestions() {
        searchRequestId++;
        if (pendingSearch != null) searchHandler.removeCallbacks(pendingSearch);
        searchSuggestions.setVisibility(View.GONE);
    }

    private void hideSearchKeyboard(View view) {
        if (view == null) return;
        InputMethodManager manager = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (manager != null) manager.hideSoftInputFromWindow(view.getWindowToken(), 0);
        view.clearFocus();
    }

    private void setupDraggableSheet() {
        View sheet = findViewById(R.id.placeBottomSheet);
        View handle = findViewById(R.id.sheetDragHandle);
        syncMapControlsWithSheet(0f);
        handle.setOnTouchListener((view, event) -> {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    sheetTouchStartY = event.getRawY();
                    sheetStartTranslationY = sheet.getTranslationY();
                    sheetVelocityTracker = VelocityTracker.obtain();
                    sheetVelocityTracker.addMovement(event);
                    return true;
                case MotionEvent.ACTION_MOVE:
                    if (sheetVelocityTracker != null) sheetVelocityTracker.addMovement(event);
                    float maxCollapse = Math.max(0, sheet.getHeight() - dp(180));
                    float translation = sheetStartTranslationY + event.getRawY() - sheetTouchStartY;
                    sheet.setTranslationY(Math.max(0, Math.min(maxCollapse, translation)));
                    syncMapControlsWithSheet(sheet.getTranslationY());
                    return true;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    if (sheetVelocityTracker != null) {
                        sheetVelocityTracker.addMovement(event);
                        sheetVelocityTracker.computeCurrentVelocity(1000);
                        float velocityY = sheetVelocityTracker.getYVelocity();
                        float max = Math.max(0, sheet.getHeight() - dp(180));
                        boolean wasTap = Math.abs(event.getRawY() - sheetTouchStartY) < dp(8);
                        boolean collapse = velocityY > 500
                                || (velocityY > -500 && sheet.getTranslationY() > max / 2f);
                        if (wasTap) collapse = sheet.getTranslationY() < max / 2f;
                        sheet.animate().translationY(collapse ? max : 0).setDuration(280)
                                .setInterpolator(new android.view.animation.DecelerateInterpolator())
                                .setUpdateListener(animation -> syncMapControlsWithSheet(sheet.getTranslationY()))
                                .withEndAction(() -> syncMapControlsWithSheet(sheet.getTranslationY()))
                                .start();
                        sheetVelocityTracker.recycle();
                        sheetVelocityTracker = null;
                    }
                    return true;
                default:
                    return true;
            }
        });
    }

    private void syncMapControlsWithSheet(float translationY) {
        int[] controlIds = {
                R.id.zoomControls,
                R.id.categoryButton,
                R.id.btnAddPlaceAtLocation
        };
        for (int controlId : controlIds) {
            View control = findViewById(controlId);
            if (control != null) control.setTranslationY(translationY);
        }
    }

    private void animateMapControl(View control, String command) {
        control.animate().scaleX(0.9f).scaleY(0.9f).setDuration(75).withEndAction(() ->
                control.animate().scaleX(1f).scaleY(1f).setDuration(130).start()).start();
        if (mapReady) mapView.evaluateJavascript(command, null);
    }

    private void pulsePlaceCard() {
        View card = findViewById(R.id.placeCard);
        card.setAlpha(0.65f);
        card.setTranslationY(dp(5));
        card.animate().alpha(1f).translationY(0).setDuration(220)
                .setInterpolator(new android.view.animation.DecelerateInterpolator()).start();
    }

    private void loadInteractiveMap() {
        try (InputStream input = getAssets().open("map.html")) {
            byte[] html = new byte[input.available()];
            int read = input.read(html);
            String page = new String(html, 0, Math.max(0, read), StandardCharsets.UTF_8);
            mapView.loadDataWithBaseURL("https://appassets.androidplatform.net/assets/", page,
                    "text/html", "UTF-8", null);
        } catch (IOException error) {
            Toast.makeText(this, "Không thể mở bản đồ. Hãy thử khởi động lại ứng dụng.", Toast.LENGTH_LONG).show();
        }
    }

    private void listenForPlaces() {
        placesListener = FirebasePlaceRepository.listen(this, new FirebasePlaceRepository.PlacesCallback() {
            @Override public void onPlaces(List<Place> places) {
                String previousSavedId = selectedSavedPlaceId;
                App.PLACES.clear();
                App.PLACES.addAll(places);
                if (places.isEmpty()) {
                    if (mapReady) mapView.evaluateJavascript("clearPlaceMarkers()", null);
                    selectedSavedPlaceId = "";
                    if (selectedSharedPlace == null && selectedNearbyIndex < 0) {
                        placeCount.setText("Địa điểm đã lưu");
                        placeName.setText("Chưa có địa điểm");
                        placeAddress.setText("Chạm vào bản đồ để thêm địa điểm tại vị trí đó");
                        placeDistance.setText("");
                        placePhoto.setImageResource(R.drawable.bg_empty_place_photo);
                    }
                } else {
                    int restoredIndex = findPlaceIndex(previousSavedId);
                    if (restoredIndex >= 0) {
                        selectedIndex = restoredIndex;
                        showSelectedPlace(false);
                    } else if (selectedSharedPlace == null && selectedNearbyIndex < 0) {
                        selectedIndex = 0;
                        showSelectedPlace(true);
                    }
                    addSavedPlaceMarkers();
                }
                renderSharedPlaceMarkers();
            }

            @Override public void onError(Exception error) {
                placeCount.setText("Bản đồ của bạn");
                Toast.makeText(MapActivity.this,
                        "Chưa thể tải địa điểm. Bạn vẫn có thể tìm địa chỉ trên bản đồ.", Toast.LENGTH_LONG).show();
            }
        });
    }

    private void listenForReceivedShares() {
        receivedSharesListener = FirebaseShareRepository.listenReceived(this,
                new FirebaseShareRepository.ReceivedCallback() {
                    @Override public void onResult(List<Place> shares) {
                        receivedShares.clear();
                        receivedShares.addAll(shares);
                        List<String> ids = new ArrayList<>();
                        for (Place share : shares) ids.add(share.id);
                        FirebasePlaceRepository.loadByIds(MapActivity.this, ids,
                                new FirebasePlaceRepository.PlacesCallback() {
                                    @Override public void onPlaces(List<Place> places) {
                                        App.SHARED_PLACES.clear();
                                        App.SHARED_PLACES.addAll(places);
                                        renderSharedPlaceMarkers();
                                    }
                                    @Override public void onError(Exception error) {
                                        App.SHARED_PLACES.clear();
                                        renderSharedPlaceMarkers();
                                        android.util.Log.w("MapActivity", "Could not load shared places", error);
                                    }
                                });
                    }

                    @Override public void onError(Exception error) {
                        App.SHARED_PLACES.clear();
                        android.util.Log.w("MapActivity", "Could not listen for received shares", error);
                    }
                });
    }

    private void renderSharedPlaceMarkers() {
        sharedMapPlaces.clear();
        if (!mapReady) return;
        mapView.evaluateJavascript("clearSharedMarkers()", null);
        if (!locationTrackingRequested) return;
        Map<String, Integer> shareCounts = new HashMap<>();
        for (Place share : receivedShares) {
            shareCounts.put(share.id, shareCounts.getOrDefault(share.id, 0) + 1);
        }
        for (Place place : App.SHARED_PLACES) {
            Integer count = shareCounts.get(place.id);
            if (count == null || place.id.isEmpty()) continue;
            place.shareCount = count;
            Place sharedPlace = place;
            int index = sharedMapPlaces.size();
            sharedMapPlaces.add(sharedPlace);
            if (place.hasCoordinates()) {
                addSharedMarker(index, place.latitude, place.longitude, sharedPlace);
            } else if (!place.address.isEmpty() && Geocoder.isPresent()) {
                geocoderExecutor.execute(() -> {
                    try {
                        @SuppressWarnings("deprecation")
                        List<Address> results = new Geocoder(this, Locale.getDefault())
                                .getFromLocationName(place.address, 1);
                        if (results == null || results.isEmpty()) return;
                        Address address = results.get(0);
                        runOnUiThread(() -> {
                            if (locationTrackingRequested && index < sharedMapPlaces.size()
                                    && sharedMapPlaces.get(index) == sharedPlace) {
                                addSharedMarker(index, address.getLatitude(), address.getLongitude(), sharedPlace);
                            }
                        });
                    } catch (IOException | IllegalArgumentException ignored) { }
                });
            }
        }
    }

    private void addSharedMarker(int index, double latitude, double longitude, Place place) {
        if (!mapReady || !locationTrackingRequested || isFinishing()) return;
        String subtitle = "Được bạn bè chia sẻ" + (place.shareCount > 1 ? " · " + place.shareCount + " lần" : "");
        String js = "addSharedMarker(" + index + "," + latitude + "," + longitude + ","
                + JSONObject.quote(place.name) + "," + JSONObject.quote(subtitle) + ")";
        mapView.evaluateJavascript(js, null);
    }

    private void showSharedPlaceDetails(int index) {
        if (index < 0 || index >= sharedMapPlaces.size()) return;
        clearRoute();
        Place place = sharedMapPlaces.get(index);
        selectedSavedPlaceId = "";
        selectedNearbyIndex = -1;
        selectedSharedPlace = place;
        updateNearbyCardSelection();
        placeCount.setText("Bạn bè đã chia sẻ");
        placeName.setText(place.name);
        placeAddress.setText(place.address);
        placeDistance.setText("");
        if (place.imageUrl == null || place.imageUrl.isEmpty()) {
            placePhoto.setImageResource(R.drawable.bg_empty_place_photo);
        } else {
            Glide.with(this).load(place.imageUrl).centerCrop().into(placePhoto);
        }
        pulsePlaceCard();
    }

    private void addSavedPlaceMarkers() {
        if (!mapReady) return;
        mapView.evaluateJavascript("clearPlaceMarkers()", null);
        for (int index = 0; index < App.PLACES.size(); index++) {
            Place place = App.PLACES.get(index);
            if (place.hasCoordinates()) {
                addMarker(place, place.latitude, place.longitude);
            } else if (Geocoder.isPresent()) {
                geocoderExecutor.execute(() -> {
                    try {
                        @SuppressWarnings("deprecation")
                        List<Address> results = new Geocoder(this, Locale.getDefault())
                                .getFromLocationName(place.address, 1);
                        if (results == null || results.isEmpty()) return;
                        Address location = results.get(0);
                        runOnUiThread(() -> addMarker(place, location.getLatitude(),
                                location.getLongitude()));
                    } catch (IOException | IllegalArgumentException ignored) { }
                });
            }
        }
    }

    private void addMarker(Place place, double latitude, double longitude) {
        if (!mapReady || isFinishing() || place == null || place.id.isEmpty()) return;
        JSONObject snapshot = new JSONObject();
        try {
            snapshot.put("id", place.id);
            snapshot.put("ownerId", place.ownerId);
            snapshot.put("name", place.name);
            snapshot.put("category", place.category);
            snapshot.put("address", place.address);
            snapshot.put("distance", place.distance);
            // The marker callback resolves the full Place from the shared cache by ID.
            // Do not serialize an inline Firestore image into the WebView payload.
            snapshot.put("description", place.description);
            snapshot.put("tags", place.tags);
            snapshot.put("latitude", latitude);
            snapshot.put("longitude", longitude);
        } catch (org.json.JSONException ignored) { return; }
        String js = "addPlaceMarker(" + JSONObject.quote(snapshot.toString()) + "," + latitude + "," + longitude + ","
                + JSONObject.quote(place.name) + ")";
        mapView.evaluateJavascript(js, null);
    }

    private void addNearbyMarker(int index, Place place) {
        if (!mapReady || isFinishing()) return;
        String subtitle = place.category + " · " + formatDistance(place.distanceMeters);
        String js = "addNearbyMarker(" + index + "," + place.latitude + "," + place.longitude + ","
                + JSONObject.quote(place.name) + "," + JSONObject.quote(subtitle) + ")";
        mapView.evaluateJavascript(js, null);
    }

    private void searchLocation(String query) {
        if (query.isEmpty()) return;
        String normalizedQuery = query.toLowerCase(Locale.ROOT);
        for (int i = 0; i < App.PLACES.size(); i++) {
            Place place = App.PLACES.get(i);
            String searchable = (place.name + " " + place.address + " " + place.tags).toLowerCase(Locale.ROOT);
            if (searchable.contains(normalizedQuery)) {
                selectedIndex = i;
                selectedNearbyIndex = -1;
                showSelectedPlace(true);
                return;
            }
        }
        if (!Geocoder.isPresent()) {
            Toast.makeText(this, "Không tìm thấy dịch vụ tìm địa chỉ trên thiết bị.", Toast.LENGTH_SHORT).show();
            return;
        }
        geocoderExecutor.execute(() -> {
            try {
                @SuppressWarnings("deprecation")
                List<Address> matches = new Geocoder(this, Locale.getDefault()).getFromLocationName(query, 1);
                if (matches == null || matches.isEmpty()) {
                    runOnUiThread(() -> Toast.makeText(this,
                            "Không tìm thấy vị trí. Hãy thử nhập địa chỉ chi tiết hơn.", Toast.LENGTH_SHORT).show());
                    return;
                }
                Address match = matches.get(0);
                String label = match.getAddressLine(0) == null ? query : match.getAddressLine(0);
                runOnUiThread(() -> moveMap(match.getLatitude(), match.getLongitude(), label));
            } catch (IOException | IllegalArgumentException error) {
                runOnUiThread(() -> Toast.makeText(this,
                        "Chưa tìm được vị trí. Kiểm tra kết nối rồi thử lại.", Toast.LENGTH_SHORT).show());
            }
        });
    }

    private void showSelectedPlace(boolean moveMap) {
        if (App.PLACES.isEmpty() || placeName == null) return;
        clearRoute();
        selectedSharedPlace = null;
        selectedNearbyIndex = -1;
        updateNearbyCardSelection();
        selectedIndex = Math.min(selectedIndex, App.PLACES.size() - 1);
        Place place = App.PLACES.get(selectedIndex);
        selectedSavedPlaceId = place.id;
        displaySavedPlace(place);
        if (!moveMap || !mapReady) return;
        if (place.hasCoordinates()) moveMap(place.latitude, place.longitude, place.name);
        else geocodeForMap(place.address, place.name);
    }

    private void displaySavedPlace(Place place) {
        placeCount.setText(App.PLACES.size() + " địa điểm đã lưu");
        placeName.setText(place.name);
        placeAddress.setText(place.address + (place.tags.isEmpty() ? "" : "  ·  "
                + Ui.tagsForDisplay(place.tags)));
        placeDistance.setText(place.distance);
        if (place.imageUrl.isEmpty()) placePhoto.setImageResource(R.drawable.bg_empty_place_photo);
        else Glide.with(this).load(place.imageUrl).centerCrop().into(placePhoto);
        pulsePlaceCard();
    }

    private void selectSavedPlace(String markerSnapshot) {
        final JSONObject data;
        try {
            data = new JSONObject(markerSnapshot);
        } catch (org.json.JSONException error) {
            android.util.Log.e("MapActivity", "Invalid saved-place marker data", error);
            return;
        }
        double markerLatitude = data.optDouble("latitude", Double.NaN);
        double markerLongitude = data.optDouble("longitude", Double.NaN);
        String title = data.optString("name", "").trim();
        String placeId = data.optString("id", "");
        int index = findPlaceIndex(placeId);
        if (index >= 0 && !App.PLACES.get(index).name.trim().equalsIgnoreCase(title)) index = -1;
        if (index < 0) {
            float closestDistance = Float.MAX_VALUE;
            for (int candidate = 0; candidate < App.PLACES.size(); candidate++) {
                Place place = App.PLACES.get(candidate);
                if (!place.name.trim().equalsIgnoreCase(title)) continue;
                if (!place.hasCoordinates()) {
                    if (index < 0) index = candidate;
                    continue;
                }
                float distance = distanceMeters(markerLatitude, markerLongitude,
                        place.latitude, place.longitude);
                if (distance < closestDistance) {
                    closestDistance = distance;
                    index = candidate;
                }
            }
        }
        Place markerPlace = new Place(placeId, data.optString("ownerId", ""), title,
                data.optString("category", ""), data.optString("address", ""),
                data.optString("distance", ""), "",
                data.optString("description", ""), data.optString("tags", ""),
                markerLatitude, markerLongitude);
        Place selectedPlace = index >= 0 && index < App.PLACES.size()
                ? App.PLACES.get(index) : markerPlace;
        selectedIndex = index;
        selectedNearbyIndex = -1;
        selectedSharedPlace = null;
        selectedSavedPlaceId = index >= 0 ? selectedPlace.id : "";
        clearRoute();
        updateNearbyCardSelection();
        displaySavedPlace(selectedPlace);
    }

    private void geocodeForMap(String query, String label) {
        if (!Geocoder.isPresent()) return;
        geocoderExecutor.execute(() -> {
            try {
                @SuppressWarnings("deprecation")
                List<Address> results = new Geocoder(this, Locale.getDefault()).getFromLocationName(query, 1);
                if (results == null || results.isEmpty()) return;
                Address address = results.get(0);
                runOnUiThread(() -> moveMap(address.getLatitude(), address.getLongitude(), label));
            } catch (IOException | IllegalArgumentException ignored) { }
        });
    }

    private void moveMap(double latitude, double longitude, String label) {
        if (!mapReady || mapView == null) return;
        mapView.evaluateJavascript("moveMapTo(" + latitude + "," + longitude + ","
                + JSONObject.quote(label) + ")", null);
    }

    private void openDirections() {
        double destinationLatitude;
        double destinationLongitude;
        String destinationLabel;
        if (selectedNearbyIndex >= 0 && selectedNearbyIndex < nearbyPlaces.size()) {
            Place nearby = nearbyPlaces.get(selectedNearbyIndex);
            destinationLatitude = nearby.latitude;
            destinationLongitude = nearby.longitude;
            destinationLabel = nearby.name;
        } else if (selectedSharedPlace != null) {
            Place place = selectedSharedPlace;
            destinationLabel = place.name;
            if (place.hasCoordinates()) {
                destinationLatitude = place.latitude;
                destinationLongitude = place.longitude;
            } else {
                geocodeRouteDestination(place.address, place.name);
                return;
            }
        } else if (!App.PLACES.isEmpty()) {
            Place place = App.PLACES.get(Math.min(selectedIndex, App.PLACES.size() - 1));
            destinationLabel = place.name;
            if (place.hasCoordinates()) {
                destinationLatitude = place.latitude;
                destinationLongitude = place.longitude;
            } else {
                geocodeRouteDestination(place.address, place.name);
                return;
            }
        } else {
            Toast.makeText(this, "Chưa có địa điểm để tìm đường.", Toast.LENGTH_SHORT).show();
            return;
        }
        requestRoute(destinationLatitude, destinationLongitude, destinationLabel);
    }

    private void geocodeRouteDestination(String address, String label) {
        if (address == null || address.trim().isEmpty() || !Geocoder.isPresent()) {
            Toast.makeText(this, "Địa điểm chưa có tọa độ ghim.", Toast.LENGTH_SHORT).show();
            return;
        }
        Toast.makeText(this, "Đang xác định vị trí địa điểm…", Toast.LENGTH_SHORT).show();
        geocoderExecutor.execute(() -> {
            try {
                @SuppressWarnings("deprecation")
                List<Address> results = new Geocoder(this, Locale.getDefault())
                        .getFromLocationName(address, 1);
                if (results == null || results.isEmpty()) {
                    runOnUiThread(() -> Toast.makeText(this,
                            "Không xác định được tọa độ địa điểm.", Toast.LENGTH_SHORT).show());
                    return;
                }
                Address result = results.get(0);
                runOnUiThread(() -> requestRoute(result.getLatitude(), result.getLongitude(), label));
            } catch (IOException | IllegalArgumentException error) {
                runOnUiThread(() -> Toast.makeText(this,
                        "Không xác định được tọa độ địa điểm.", Toast.LENGTH_SHORT).show());
            }
        });
    }

    private void requestRoute(double latitude, double longitude, String label) {
        boolean freshLocation = currentLocation != null
                && System.currentTimeMillis() - currentLocation.getTime() <= 2 * 60 * 1000L;
        if (!locationTrackingRequested || !freshLocation) {
            pendingRoute = true;
            pendingRouteLatitude = latitude;
            pendingRouteLongitude = longitude;
            pendingRouteLabel = label;
            if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
                    && checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION}, LOCATION_PERMISSION_REQUEST);
                return;
            }
            locationTrackingRequested = true;
            centerOnNextLocation = true;
            renderSharedPlaceMarkers();
            startLocationUpdates(true);
            return;
        }
        fetchRoute(currentLocation.getLatitude(), currentLocation.getLongitude(), latitude, longitude, label);
    }

    private void fetchRoute(double fromLatitude, double fromLongitude,
                            double toLatitude, double toLongitude, String label) {
        if (!mapReady || mapView == null) {
            Toast.makeText(this, "Bản đồ đang tải, hãy thử lại sau.", Toast.LENGTH_SHORT).show();
            return;
        }
        clearRoute();
        int requestId = ++routeRequestId;
        Toast.makeText(this, "Đang tìm đường…", Toast.LENGTH_SHORT).show();
        routeExecutor.execute(() -> {
            HttpURLConnection connection = null;
            try {
                String coordinates = String.format(Locale.US, "%.6f,%.6f;%.6f,%.6f",
                        fromLongitude, fromLatitude, toLongitude, toLatitude);
                URL url = new URL("https://router.project-osrm.org/route/v1/driving/" + coordinates
                        + "?alternatives=false&steps=false&overview=simplified&geometries=geojson");
                connection = (HttpURLConnection) url.openConnection();
                connection.setConnectTimeout(12000);
                connection.setReadTimeout(20000);
                connection.setRequestProperty("User-Agent", "TapMap/1.0 (Android app)");
                connection.setRequestProperty("Accept", "application/json");
                int status = connection.getResponseCode();
                if (status < 200 || status >= 300) throw new IOException("Routing HTTP " + status);
                StringBuilder response = new StringBuilder();
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                        connection.getInputStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null) response.append(line);
                }
                JSONObject root = new JSONObject(response.toString());
                JSONArray routes = root.optJSONArray("routes");
                if (!"Ok".equals(root.optString("code")) || routes == null || routes.length() == 0) {
                    throw new IOException("No route found");
                }
                JSONObject geometry = routes.getJSONObject(0).optJSONObject("geometry");
                JSONArray points = geometry == null ? null : geometry.optJSONArray("coordinates");
                if (points == null || points.length() < 2) throw new IOException("Route has no geometry");
                StringBuilder jsPoints = new StringBuilder("[");
                for (int i = 0; i < points.length(); i++) {
                    JSONArray point = points.getJSONArray(i);
                    if (i > 0) jsPoints.append(',');
                    jsPoints.append('[').append(point.getDouble(1)).append(',')
                            .append(point.getDouble(0)).append(']');
                }
                jsPoints.append(']');
                String script = "showRoute(" + jsPoints + "," + JSONObject.quote(label) + ")";
                runOnUiThread(() -> {
                    if (requestId == routeRequestId && mapReady && mapView != null) {
                        mapView.evaluateJavascript(script, null);
                    }
                });
            } catch (Exception error) {
                runOnUiThread(() -> {
                    if (requestId == routeRequestId) {
                        Toast.makeText(this, "Không tìm được tuyến đường. Hãy thử lại.",
                                Toast.LENGTH_LONG).show();
                    }
                });
            } finally {
                if (connection != null) connection.disconnect();
            }
        });
    }

    private void clearRoute() {
        routeRequestId++;
        if (mapReady && mapView != null) mapView.evaluateJavascript("clearRoute()", null);
    }

    private void selectNearbyPlace(int index) {
        if (index < 0 || index >= nearbyPlaces.size()) return;
        clearRoute();
        selectedSharedPlace = null;
        selectedSavedPlaceId = "";
        selectedNearbyIndex = index;
        Place place = nearbyPlaces.get(index);
        placeName.setText(place.name);
        placeAddress.setText(place.category);
        placeDistance.setText(formatDistance(place.distanceMeters));
        placePhoto.setImageResource(R.drawable.bg_empty_place_photo);
        pulsePlaceCard();
        updateNearbyCardSelection();
        moveMap(place.latitude, place.longitude, place.name);
        showNearbyPlaceDetails(place);
    }

    private void showNearbyPlaceDetails(Place place) {
        List<String> details = new ArrayList<>();
        details.add("Loại địa điểm: " + place.category);
        details.add("Khoảng cách: " + formatDistance(place.distanceMeters));
        details.add("Tọa độ: " + String.format(Locale.getDefault(), "%.6f, %.6f", place.latitude, place.longitude));
        if (!place.address.isEmpty()) details.add("Địa chỉ: " + place.address);
        if (!place.openingHours.isEmpty()) details.add("Giờ mở cửa: " + place.openingHours);
        if (!place.phone.isEmpty()) details.add("Điện thoại: " + place.phone);
        if (!place.website.isEmpty()) details.add("Website: " + place.website);
        if (!place.email.isEmpty()) details.add("Email: " + place.email);
        if (!place.extraInfo.isEmpty()) details.add("Thông tin thêm: " + place.extraInfo);
        String message = String.join("\n\n", details);
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(place.name)
                .setMessage(message)
                .setNegativeButton("Đóng", null)
                .setPositiveButton("Ghim mới", (selectedDialog, which) -> {
                    String address = place.address.isEmpty()
                            ? String.format(Locale.getDefault(), "%.6f, %.6f", place.latitude, place.longitude)
                            : place.address;
                    List<String> description = new ArrayList<>();
                    if (!place.extraInfo.isEmpty()) description.add(place.extraInfo);
                    if (!place.openingHours.isEmpty()) description.add("Giờ mở cửa: " + place.openingHours);
                    if (!place.phone.isEmpty()) description.add("Điện thoại: " + place.phone);
                    if (!place.website.isEmpty()) description.add("Website: " + place.website);
                    if (!place.email.isEmpty()) description.add("Email: " + place.email);
                    Intent intent = new Intent(this, AddPlaceActivity.class)
                            .putExtra("PREFILL_NAME", place.name)
                            .putExtra("PREFILL_CATEGORY", place.category)
                            .putExtra("PREFILL_ADDRESS", address)
                            .putExtra("MAP_ADDRESS", address)
                            .putExtra("PREFILL_DESCRIPTION", String.join("\n", description))
                            .putExtra("PREFILL_TAGS", place.category)
                            .putExtra("MAP_LATITUDE", place.latitude)
                            .putExtra("MAP_LONGITUDE", place.longitude);
                    startActivity(intent);
                })
                .create();
        dialog.setOnShowListener(ignored -> {
            TextView title = dialog.findViewById(androidx.appcompat.R.id.alertTitle);
            TextView messageView = dialog.findViewById(android.R.id.message);
            if (title != null) {
                title.setTextColor(Color.BLACK);
                title.setTextSize(20);
            }
            if (messageView != null) {
                messageView.setTextColor(Color.BLACK);
                messageView.setTextSize(17);
                messageView.setLineSpacing(dp(2), 1f);
            }
            styleConfirmationButton(dialog.getButton(AlertDialog.BUTTON_NEGATIVE));
            styleConfirmationButton(dialog.getButton(AlertDialog.BUTTON_POSITIVE));
        });
        dialog.show();
    }

    private void updateNearbyCardSelection() {
        for (int i = 0; i < nearbyCards.size(); i++) {
            TextView card = nearbyCards.get(i);
            card.setBackgroundResource(i == selectedNearbyIndex
                    ? R.drawable.bg_close_button : R.drawable.bg_edit_button);
            card.animate().scaleX(i == selectedNearbyIndex ? 1.02f : 1f)
                    .scaleY(i == selectedNearbyIndex ? 1.02f : 1f).setDuration(140).start();
        }
    }

    private void renderNearbyPlaces(List<Place> places, double latitude, double longitude) {
        nearbyPlaces.clear();
        nearbyPlaces.addAll(places);
        nearbyCenterLatitude = latitude;
        nearbyCenterLongitude = longitude;
        nearbyLoadedAt = SystemClock.elapsedRealtime();
        nearbyRetryAfter = 0;
        nearbyRequestInFlight = false;
        nearbyList.removeAllViews();
        nearbyCards.clear();
        renderNearbyMarkers();
        if (places.isEmpty()) {
            nearbyStatus.setText("Chưa có gợi ý gần bạn");
            selectedNearbyIndex = -1;
            return;
        }
        nearbyStatus.setText(places.size() + " địa điểm · chạm để xem");
        for (int i = 0; i < places.size(); i++) {
            final int index = i;
            Place place = places.get(i);
            TextView card = new TextView(this);
            card.setBackgroundResource(R.drawable.bg_edit_button);
            int pad = dp(10);
            card.setPadding(pad, pad, pad, pad);
            card.setGravity(Gravity.CENTER_VERTICAL);
            card.setText(place.name + "\n" + place.category + " · " + formatDistance(place.distanceMeters));
            card.setTextColor(getColor(R.color.ink));
            card.setTextSize(12);
            card.setMaxLines(3);
            card.setOnClickListener(v -> selectNearbyPlace(index));
            LayoutParams params = new LayoutParams(dp(166), LayoutParams.MATCH_PARENT);
            params.setMarginEnd(dp(8));
            nearbyList.addView(card, params);
            nearbyCards.add(card);
        }
    }

    private void renderNearbyMarkers() {
        if (!mapReady) return;
        mapView.evaluateJavascript("clearNearbyMarkers()", null);
        if (!locationTrackingRequested) return;
        for (int i = 0; i < nearbyPlaces.size(); i++) addNearbyMarker(i, nearbyPlaces.get(i));
    }

    private int findPlaceIndex(String id) {
        if (id == null || id.isEmpty()) return -1;
        for (int i = 0; i < App.PLACES.size(); i++) {
            if (id.equals(App.PLACES.get(i).id)) return i;
        }
        return -1;
    }

    private int dp(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private static String formatDistance(float meters) {
        if (meters < 1000) return Math.round(meters) + " m";
        return String.format(Locale.getDefault(), "%.1f km", meters / 1000f);
    }

    private void showMyLocation() {
        if (locationTrackingRequested) {
            locationTrackingRequested = false;
            centerOnNextLocation = false;
            locationSessionToken++;
            stopLocationUpdates();
            if (mapReady) {
                mapView.evaluateJavascript("clearCurrentLocation()", null);
                mapView.evaluateJavascript("clearNearbyMarkers()", null);
                mapView.evaluateJavascript("clearSharedMarkers()", null);
            }
            nearbyRequestInFlight = false;
            nearbyLoadedAt = 0;
            nearbyRetryAfter = 0;
            nearbyCenterLatitude = Double.NaN;
            nearbyCenterLongitude = Double.NaN;
            nearbyPlaces.clear();
            sharedMapPlaces.clear();
            nearbyCards.clear();
            nearbyList.removeAllViews();
            selectedNearbyIndex = -1;
            nearbyStatus.setText("Định vị đã tắt");
            return;
        }
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
                && checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION}, LOCATION_PERMISSION_REQUEST);
            return;
        }
        locationTrackingRequested = true;
        centerOnNextLocation = true;
        renderSharedPlaceMarkers();
        startLocationUpdates(true);
    }

    @SuppressWarnings("MissingPermission")
    private void startLocationUpdates(boolean centerImmediately) {
        locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);
        if (locationManager == null) return;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && !locationManager.isLocationEnabled()) {
            nearbyStatus.setText("Bật định vị để xem địa điểm gần bạn");
            new AlertDialog.Builder(this)
                    .setTitle("Vị trí đang tắt")
                    .setMessage("Bật dịch vụ vị trí của thiết bị để ứng dụng xác định vị trí hiện tại.")
                    .setNegativeButton("Để sau", null)
                    .setPositiveButton("Mở cài đặt", (dialog, which) ->
                            startActivity(new Intent(android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS)))
                    .show();
            return;
        }
        Location best = null;
        for (String provider : locationManager.getProviders(true)) {
            try {
                if (LocationManager.GPS_PROVIDER.equals(provider)
                        && checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)
                        != PackageManager.PERMISSION_GRANTED) continue;
                Location location = locationManager.getLastKnownLocation(provider);
                if (location == null || System.currentTimeMillis() - location.getTime() > 2 * 60 * 1000L) continue;
                if (best == null || location.getTime() > best.getTime()
                        || (Math.abs(location.getTime() - best.getTime()) < 10000
                        && location.hasAccuracy() && (!best.hasAccuracy()
                        || location.getAccuracy() < best.getAccuracy()))) best = location;
            } catch (SecurityException ignored) { }
        }
        if (best != null) handleLocation(best, centerImmediately);
        try {
            if (pendingLocationListener == null) {
                pendingLocationListener = new LocationListener() {
                    @Override public void onLocationChanged(@NonNull Location location) {
                        handleLocation(location, centerOnNextLocation);
                    }
                };
            }
            if (!locationUpdatesActive) {
                boolean requested = false;
                boolean fineAllowed = checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)
                        == PackageManager.PERMISSION_GRANTED;
                String[] providers = fineAllowed
                        ? new String[]{LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER}
                        : new String[]{LocationManager.NETWORK_PROVIDER};
                for (String provider : providers) {
                    if (locationManager.isProviderEnabled(provider)) {
                        locationManager.requestLocationUpdates(provider, 4000, 8f,
                                pendingLocationListener, Looper.getMainLooper());
                        requested = true;
                    }
                }
                locationUpdatesActive = requested;
            }
        } catch (SecurityException | IllegalArgumentException error) {
            Toast.makeText(this, "Bật dịch vụ vị trí rồi thử lại.", Toast.LENGTH_SHORT).show();
        }
    }

    private void handleLocation(Location location, boolean centerMap) {
        if (!locationTrackingRequested) return;
        currentLocation = new Location(location);
        if (mapReady) {
            String js = "updateCurrentLocation(" + location.getLatitude() + "," + location.getLongitude()
                    + "," + location.getAccuracy() + "," + centerMap + ")";
            mapView.evaluateJavascript(js, null);
        }
        if (centerMap) centerOnNextLocation = false;
        maybeLoadNearby(location.getLatitude(), location.getLongitude());
        if (pendingRoute) {
            pendingRoute = false;
            fetchRoute(location.getLatitude(), location.getLongitude(), pendingRouteLatitude,
                    pendingRouteLongitude, pendingRouteLabel);
        }
    }

    private void maybeLoadNearby(double latitude, double longitude) {
        if (nearbyRequestInFlight) return;
        long age = SystemClock.elapsedRealtime() - nearbyLoadedAt;
        boolean sameArea = !Double.isNaN(nearbyCenterLatitude)
                && distanceMeters(latitude, longitude, nearbyCenterLatitude, nearbyCenterLongitude) < 500;
        if (nearbyLoadedAt > 0 && sameArea && age < 5 * 60 * 1000) {
            if (nearbyPlaces.isEmpty()) nearbyStatus.setText("Chưa có gợi ý gần bạn");
            else nearbyStatus.setText(nearbyPlaces.size() + " địa điểm · chạm để xem");
            return;
        }
        if (SystemClock.elapsedRealtime() < nearbyRetryAfter) return;
        nearbyRequestInFlight = true;
        nearbyStatus.setText("Đang tìm địa điểm gần bạn…");
        int requestToken = locationSessionToken;
        nearbyExecutor.execute(() -> fetchNearbyPlaces(latitude, longitude, requestToken));
    }

    private void fetchNearbyPlaces(double latitude, double longitude, int requestToken) {
        HttpURLConnection connection = null;
        try {
            String query = String.format(Locale.US,
                    "[out:json][timeout:25];(nwr(around:2000,%.6f,%.6f)[\"name\"][\"tourism\"];"
                            + "nwr(around:2000,%.6f,%.6f)[\"name\"][\"historic\"];"
                            + "nwr(around:2000,%.6f,%.6f)[\"name\"][\"amenity\"~\"restaurant|cafe|fast_food|bar|pub|marketplace|cinema|museum|theatre|place_of_worship\"];"
                            + "nwr(around:2000,%.6f,%.6f)[\"name\"][\"leisure\"~\"park|nature_reserve|garden\"];"
                            + "nwr(around:2000,%.6f,%.6f)[\"name\"][\"wikidata\"];);out center body 120;",
                    latitude, longitude, latitude, longitude, latitude, longitude,
                    latitude, longitude, latitude, longitude);
            byte[] body = ("data=" + URLEncoder.encode(query, "UTF-8")).getBytes(StandardCharsets.UTF_8);
            connection = (HttpURLConnection) new URL("https://overpass-api.de/api/interpreter").openConnection();
            connection.setRequestMethod("POST");
            connection.setConnectTimeout(15000);
            connection.setReadTimeout(30000);
            connection.setRequestProperty("User-Agent", "TapMap/1.0 (Android app)");
            connection.setRequestProperty("Accept", "application/json");
            connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8");
            connection.setDoOutput(true);
            try (OutputStream output = connection.getOutputStream()) { output.write(body); }
            int status = connection.getResponseCode();
            if (status < 200 || status >= 300) throw new IOException("Nearby search HTTP " + status);
            StringBuilder jsonText = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                    connection.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) jsonText.append(line);
            }
            List<Place> results = parseNearbyPlaces(jsonText.toString(), latitude, longitude);
            runOnUiThread(() -> {
                if (locationTrackingRequested && requestToken == locationSessionToken) {
                    renderNearbyPlaces(results, latitude, longitude);
                }
            });
        } catch (Exception error) {
            runOnUiThread(() -> {
                if (!locationTrackingRequested || requestToken != locationSessionToken) return;
                nearbyRequestInFlight = false;
                nearbyCenterLatitude = Double.NaN;
                nearbyCenterLongitude = Double.NaN;
                nearbyLoadedAt = 0;
                nearbyRetryAfter = SystemClock.elapsedRealtime() + 30 * 1000;
                nearbyStatus.setText("Không tải được gợi ý quanh bạn");
            });
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    private List<Place> parseNearbyPlaces(String json, double fromLatitude, double fromLongitude)
            throws org.json.JSONException {
        org.json.JSONArray elements = new JSONObject(json).optJSONArray("elements");
        List<Place> results = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        if (elements == null) return results;
        for (int i = 0; i < elements.length(); i++) {
            JSONObject element = elements.optJSONObject(i);
            if (element == null) continue;
            JSONObject tags = element.optJSONObject("tags");
            if (tags == null) continue;
            String name = tags.optString("name", "").trim();
            if (name.isEmpty()) continue;
            double latitude = element.optDouble("lat", Double.NaN);
            double longitude = element.optDouble("lon", Double.NaN);
            if (Double.isNaN(latitude) || Double.isNaN(longitude)) {
                JSONObject center = element.optJSONObject("center");
                if (center == null) continue;
                latitude = center.optDouble("lat", Double.NaN);
                longitude = center.optDouble("lon", Double.NaN);
            }
            if (Double.isNaN(latitude) || Double.isNaN(longitude)) continue;
            float distance = distanceMeters(fromLatitude, fromLongitude, latitude, longitude);
            if (distance > 2000f) continue;
            String key = name.toLowerCase(Locale.ROOT) + Math.round(latitude * 10000) + Math.round(longitude * 10000);
            if (!seen.add(key)) continue;
            results.add(Place.nearby(name, nearbyCategory(tags), latitude, longitude, distance,
                    nearbyAddress(tags), firstTag(tags, "opening_hours", "contact:opening_hours"),
                    firstTag(tags, "phone", "contact:phone"), firstTag(tags, "website", "contact:website", "url"),
                    firstTag(tags, "email", "contact:email"),
                    firstTag(tags, "cuisine", "description", "wikipedia"), nearbyFameScore(tags)));
        }
        Collections.sort(results, Comparator
                .comparingInt((Place place) -> place.fameScore).reversed()
                .thenComparingDouble(place -> place.distanceMeters));
        if (results.size() > 12) return new ArrayList<>(results.subList(0, 12));
        return results;
    }

    private String nearbyCategory(JSONObject tags) {
        String amenity = tags.optString("amenity", "");
        if ("cafe".equals(amenity)) return "Quán cà phê";
        if ("restaurant".equals(amenity)) return "Nhà hàng";
        if ("fast_food".equals(amenity)) return "Đồ ăn nhanh";
        if ("hospital".equals(amenity) || "clinic".equals(amenity)) return "Y tế";
        if ("school".equals(amenity) || "university".equals(amenity)) return "Giáo dục";
        String tourism = tags.optString("tourism", "");
        if (!tourism.isEmpty()) return "Điểm tham quan";
        String leisure = tags.optString("leisure", "");
        if ("park".equals(leisure)) return "Công viên";
        if (!leisure.isEmpty()) return "Giải trí";
        String shop = tags.optString("shop", "");
        if (!shop.isEmpty()) return "Cửa hàng";
        return "Địa điểm gần đây";
    }

    private static String firstTag(JSONObject tags, String... keys) {
        for (String key : keys) {
            String value = tags.optString(key, "").trim();
            if (!value.isEmpty()) return value;
        }
        return "";
    }

    private static String nearbyAddress(JSONObject tags) {
        String fullAddress = firstTag(tags, "addr:full");
        if (!fullAddress.isEmpty()) return fullAddress;
        List<String> parts = new ArrayList<>();
        String number = firstTag(tags, "addr:housenumber");
        String road = firstTag(tags, "addr:street");
        if (!number.isEmpty()) parts.add(number);
        if (!road.isEmpty()) parts.add(road);
        String suburb = firstTag(tags, "addr:suburb");
        String district = firstTag(tags, "addr:district");
        String city = firstTag(tags, "addr:city");
        if (!suburb.isEmpty()) parts.add(suburb);
        if (!district.isEmpty() && !district.equals(suburb)) parts.add(district);
        if (!city.isEmpty() && !city.equals(district)) parts.add(city);
        return String.join(", ", parts);
    }

    private static int nearbyFameScore(JSONObject tags) {
        int score = 0;
        if (!firstTag(tags, "tourism").isEmpty()) score += 4;
        if (!firstTag(tags, "historic").isEmpty()) score += 4;
        if (!firstTag(tags, "wikidata", "wikipedia", "heritage").isEmpty()) score += 5;
        String amenity = tags.optString("amenity", "");
        if ("museum".equals(amenity) || "theatre".equals(amenity)
                || "place_of_worship".equals(amenity)) score += 3;
        else if ("restaurant".equals(amenity) || "cafe".equals(amenity)) score += 1;
        return score;
    }

    private static float distanceMeters(double lat1, double lon1, double lat2, double lon2) {
        float[] distance = new float[1];
        Location.distanceBetween(lat1, lon1, lat2, lon2, distance);
        return distance[0];
    }

    private void stopLocationUpdates() {
        if (locationManager != null && pendingLocationListener != null && locationUpdatesActive) {
            locationManager.removeUpdates(pendingLocationListener);
        }
        locationUpdatesActive = false;
    }

    @Override public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                                      @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == LOCATION_PERMISSION_REQUEST) {
            boolean granted = false;
            for (int result : grantResults) if (result == PackageManager.PERMISSION_GRANTED) granted = true;
            if (granted) {
                locationTrackingRequested = true;
                centerOnNextLocation = true;
                renderSharedPlaceMarkers();
                startLocationUpdates(true);
            }
            else {
                pendingRoute = false;
                Toast.makeText(this, "Cần quyền vị trí để hiển thị vị trí hiện tại.", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void openPlace() {
        if (selectedSharedPlace != null) {
            startActivity(new Intent(this, PlaceDetailActivity.class)
                    .putExtra(PlaceDetailActivity.SHARED_PLACE_ID, selectedSharedPlace.id));
            return;
        }
        if (selectedNearbyIndex >= 0) {
            openDirections();
            return;
        }
        if (selectedIndex < 0 || selectedIndex >= App.PLACES.size()) return;
        startActivity(new Intent(this, PlaceDetailActivity.class)
                .putExtra(PlaceDetailActivity.PLACE_INDEX, selectedIndex));
    }

    @JavascriptInterface public void onMapTapped(double latitude, double longitude) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            runOnUiThread(() -> onMapTapped(latitude, longitude));
            return;
        }
        if (isFinishing() || isDestroyed()) return;

        clearRoute();
        dismissSearchSuggestions();

        String coordinateAddress = String.format(Locale.getDefault(), "%.6f, %.6f", latitude, longitude);
        String[] selectedAddress = {coordinateAddress};
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Thêm địa điểm tại đây?")
                .setMessage(coordinateAddress)
                .setNegativeButton("Xem lại bản đồ", (selectedDialog, which) -> selectedDialog.dismiss())
                .setPositiveButton("Tiếp tục ghim", (selectedDialog, which) -> {
                    Intent intent = new Intent(this, AddPlaceActivity.class)
                            .putExtra("MAP_LATITUDE", latitude)
                            .putExtra("MAP_LONGITUDE", longitude)
                            .putExtra("MAP_ADDRESS", selectedAddress[0]);
                    startActivity(intent);
                })
                .create();
        dialog.setOnShowListener(ignored -> {
            styleConfirmationButton(dialog.getButton(AlertDialog.BUTTON_NEGATIVE));
            styleConfirmationButton(dialog.getButton(AlertDialog.BUTTON_POSITIVE));
        });
        dialog.show();

        // Resolve the address after the confirmation appears so a slow geocoder
        // cannot make the map tap seem unresponsive.
        geocoderExecutor.execute(() -> {
            if (!Geocoder.isPresent()) return;
            try {
                @SuppressWarnings("deprecation")
                List<Address> results = new Geocoder(this, Locale.getDefault())
                        .getFromLocation(latitude, longitude, 1);
                if (results == null || results.isEmpty() || results.get(0).getAddressLine(0) == null) return;
                String resolvedAddress = results.get(0).getAddressLine(0);
                runOnUiThread(() -> {
                    if (dialog.isShowing() && !isFinishing() && !isDestroyed()) {
                        selectedAddress[0] = resolvedAddress;
                        dialog.setMessage(resolvedAddress);
                    }
                });
            } catch (IOException | IllegalArgumentException ignored) { }
        });
    }

    private void styleConfirmationButton(Button button) {
        if (button == null) return;
        GradientDrawable background = new GradientDrawable();
        background.setColor(Color.parseColor("#E6F1BC"));
        background.setCornerRadius(12 * getResources().getDisplayMetrics().density);
        button.setBackground(background);
        button.setTextColor(Color.BLACK);
        button.setAllCaps(false);
        button.setPadding(
                (int) (12 * getResources().getDisplayMetrics().density),
                button.getPaddingTop(),
                (int) (12 * getResources().getDisplayMetrics().density),
                button.getPaddingBottom());
    }

    @JavascriptInterface public void onSavedPlaceTapped(String markerSnapshot) {
        runOnUiThread(() -> selectSavedPlace(markerSnapshot));
    }

    @JavascriptInterface public void onNearbyPlaceTapped(int index) {
        runOnUiThread(() -> selectNearbyPlace(index));
    }

    @JavascriptInterface public void onSharedPlaceTapped(int index) {
        runOnUiThread(() -> showSharedPlaceDetails(index));
    }

    @Override protected void onResume() {
        super.onResume();
        if (locationTrackingRequested
                && (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                || checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED)) {
            startLocationUpdates(false);
        }
    }

    @Override protected void onPause() {
        stopLocationUpdates();
        super.onPause();
    }

    @Override protected void onDestroy() {
        if (placesListener != null) placesListener.remove();
        if (receivedSharesListener != null) receivedSharesListener.remove();
        stopLocationUpdates();
        geocoderExecutor.shutdownNow();
        nearbyExecutor.shutdownNow();
        routeExecutor.shutdownNow();
        if (mapView != null) {
            mapReady = false;
            mapView.removeJavascriptInterface("TapMap");
            mapView.stopLoading();
            mapView.destroy();
        }
        super.onDestroy();
    }

}
