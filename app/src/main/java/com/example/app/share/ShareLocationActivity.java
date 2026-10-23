package com.example.app.share;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.LayoutInflater;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.widget.ListPopupWindow;
import com.example.app.BaseNavigationActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.bumptech.glide.Glide;
import com.example.app.R;
import com.example.app.data.AppData;
import com.example.app.data.FirebasePlaceRepository;
import com.example.app.data.FirebaseShareRepository;
import com.example.app.data.FriendRequestRepository;
import com.example.app.data.FirebaseUserRepository;
import com.example.app.model.Place;
import com.example.app.model.UserProfile;
import com.example.app.ui.PlaceFormUtils;
import com.example.app.screens.PlaceDetailActivity;
import com.example.app.screens.EditPlaceActivity;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.auth.FirebaseAuth;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class ShareLocationActivity extends BaseNavigationActivity {
    private RecyclerView rvFriends;
    private SelectFriendAdapter adapter;
    private final List<Friend> allFriends = new ArrayList<>();
    private TextView tvSelectedCount;
    private TextView tvFriendsEmpty;
    private TextView tvFriendsListTitle;
    private TextView btnFriendRequests;
    private Button btnShare;
    private EditText edtSearch;
    private String placeId = "";
    private String placeName;
    private String placeImageUrl = "";
    private ListenerRegistration friendsListener;
    private ListenerRegistration friendRequestsListener;
    private ListenerRegistration placeListener;
    private ListenerRegistration placesListener;
    private final List<Place> favoritePlaces = new ArrayList<>();
    private Place selectedPlace;
    private String listenedPlaceId = "";
    private final Map<String, String> relationshipStatuses = new HashMap<>();

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentViewWithNavigation(R.layout.activity_share_location);
        tvSelectedCount = findViewById(R.id.tvSelectedCount);
        tvFriendsEmpty = findViewById(R.id.tvFriendsEmpty);
        tvFriendsListTitle = findViewById(R.id.tvFriendsListTitle);
        btnFriendRequests = findViewById(R.id.btnFriendRequests);
        btnShare = findViewById(R.id.btnShare);
        rvFriends = findViewById(R.id.rvFriends);
        edtSearch = findViewById(R.id.edtSearch);
        rvFriends.setLayoutManager(new LinearLayoutManager(this));
        adapter = new SelectFriendAdapter(this, new ArrayList<>(), this::onFriendSelectionChanged,
                this::onFriendAction);
        rvFriends.setAdapter(adapter);
        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
        btnFriendRequests.setOnClickListener(v ->
                startActivity(new Intent(this, FriendRequestsActivity.class)));

        int placeIndex = getIntent().getIntExtra(PlaceDetailActivity.PLACE_INDEX, -1);
        placeName = getIntent().getStringExtra("PLACE_NAME");
        Place place = placeIndex >= 0 && placeIndex < AppData.PLACES.size()
                ? AppData.PLACES.get(placeIndex) : null;
        if (place != null) {
            setSelectedPlace(place);
        } else {
            placeName = "";
            ((TextView) findViewById(R.id.tvPlaceName)).setText("Chọn địa điểm yêu thích");
            ((TextView) findViewById(R.id.tvPlaceAddress)).setText("");
            ((TextView) findViewById(R.id.tvPlaceCategory)).setText("");
        }
        findViewById(R.id.cardSelectPlace).setOnClickListener(v -> showFavoritePlacePicker());
        findViewById(R.id.imgPlaceDropdown).setOnClickListener(v -> showFavoritePlacePicker());
        findViewById(R.id.tvPlaceAddImage).setOnClickListener(v -> openPlaceEditor(selectedPlace));
        setTitle("Chia sẻ: " + placeName);
        if (place != null && !place.id.isEmpty()) listenForPlaceChanges(place.id);

        edtSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { showFilteredFriends(); }
            @Override public void afterTextChanged(Editable s) { }
        });
        btnShare.setEnabled(selectedPlace != null);
        btnShare.setOnClickListener(v -> shareSelectedFriends());
        loadFavoritePlaces();
        loadFriends();
        listenForFriendRequests();
    }

    private void loadFavoritePlaces() {
        placesListener = FirebasePlaceRepository.listen(this, new FirebasePlaceRepository.PlacesCallback() {
            @Override public void onPlaces(List<Place> places) {
                favoritePlaces.clear();
                for (Place place : places) if (!place.id.isEmpty()) favoritePlaces.add(place);
                AppData.PLACES.clear();
                AppData.PLACES.addAll(favoritePlaces);
                if (selectedPlace != null) {
                    for (Place place : favoritePlaces) {
                        if (place.id.equals(selectedPlace.id)) {
                            setSelectedPlace(place);
                            break;
                        }
                    }
                }
            }
            @Override public void onError(Exception error) {
                if (selectedPlace == null) Toast.makeText(ShareLocationActivity.this,
                        "Không tải được địa điểm yêu thích.", Toast.LENGTH_LONG).show();
            }
        });
    }

    private void showFavoritePlacePicker() {
        View anchor = findViewById(R.id.cardSelectPlace);
        ListPopupWindow popup = new ListPopupWindow(this);
        popup.setAnchorView(anchor);
        popup.setWidth(anchor.getWidth() > 0 ? anchor.getWidth() : ListView.LayoutParams.MATCH_PARENT);
        popup.setHeight(favoritePlaces.isEmpty() ? dp(64) : Math.min(dp(320), dp(76) * favoritePlaces.size()));
        popup.setModal(true);
        popup.setInputMethodMode(ListPopupWindow.INPUT_METHOD_NOT_NEEDED);
        popup.setAdapter(new FavoritePlaceAdapter());
        popup.setOnItemClickListener((parent, view, position, id) -> {
            setSelectedPlace(favoritePlaces.get(position));
            popup.dismiss();
        });
        popup.show();
    }

    private int dp(int value) { return (int) (value * getResources().getDisplayMetrics().density + 0.5f); }

    private void openPlaceEditor(Place place) {
        if (place == null) return;
        String uid = FirebaseAuth.getInstance().getCurrentUser() == null ? ""
                : FirebaseAuth.getInstance().getCurrentUser().getUid();
        if (!uid.isEmpty() && uid.equals(place.ownerId)) {
            int index = favoritePlaces.indexOf(place);
            if (index < 0) {
                for (int i = 0; i < AppData.PLACES.size(); i++) {
                    if (place.id.equals(AppData.PLACES.get(i).id)) { index = i; break; }
                }
            }
            if (index >= 0) startActivity(new Intent(this, EditPlaceActivity.class)
                    .putExtra(PlaceDetailActivity.PLACE_INDEX, index));
            return;
        }
        Intent addPlace = new Intent(this, com.example.app.screens.AddPlaceActivity.class)
                .putExtra("PREFILL_NAME", place.name)
                .putExtra("PREFILL_CATEGORY", place.category)
                .putExtra("PREFILL_ADDRESS", place.address)
                .putExtra("PREFILL_DESCRIPTION", place.description)
                .putExtra("PREFILL_TAGS", PlaceFormUtils.normalizeTags(place.tags));
        startActivity(addPlace);
    }

    private final class FavoritePlaceAdapter extends BaseAdapter {
        @Override public int getCount() { return favoritePlaces.size(); }
        @Override public Object getItem(int position) { return favoritePlaces.get(position); }
        @Override public long getItemId(int position) { return position; }
        @Override public View getView(int position, View convertView, ViewGroup parent) {
            View row = LayoutInflater.from(ShareLocationActivity.this)
                    .inflate(R.layout.item_saved_place_dropdown, parent, false);
            Place place = favoritePlaces.get(position);
            ImageView photo = row.findViewById(R.id.imgSavedPlace);
            TextView addPhoto = row.findViewById(R.id.tvAddSavedPlacePhoto);
            TextView name = row.findViewById(R.id.tvSavedPlaceName);
            TextView address = row.findViewById(R.id.tvSavedPlaceAddress);
            if (place.imageUrl == null || place.imageUrl.trim().isEmpty()) {
                photo.setImageResource(R.drawable.bg_icon_tile);
                addPhoto.setVisibility(View.VISIBLE);
                addPhoto.setOnClickListener(v -> openPlaceEditor(place));
            } else {
                Glide.with(ShareLocationActivity.this).load(place.imageUrl).centerCrop().into(photo);
                addPhoto.setVisibility(View.GONE);
            }
            name.setText(place.name);
            address.setText(place.address == null || place.address.isEmpty() ? place.category : place.address);
            return row;
        }
    }

    private void setSelectedPlace(Place place) {
        selectedPlace = place;
        placeId = place.id;
        placeName = place.name;
        placeImageUrl = place.imageUrl;
        ((TextView) findViewById(R.id.tvPlaceName)).setText(place.name);
        ((TextView) findViewById(R.id.tvPlaceAddress)).setText(place.address);
        ((TextView) findViewById(R.id.tvPlaceCategory)).setText(
                place.category + (place.tags.isEmpty() ? "" : "  ·  " + PlaceFormUtils.tagsForDisplay(place.tags)));
        ImageView image = findViewById(R.id.imgPlace);
        TextView addImage = findViewById(R.id.tvPlaceAddImage);
        if (place.imageUrl == null || place.imageUrl.trim().isEmpty()) {
            image.setImageResource(R.drawable.ic_launcher_foreground);
            addImage.setVisibility(View.VISIBLE);
        } else {
            addImage.setVisibility(View.GONE);
            Glide.with(this).load(place.imageUrl).centerCrop().into(image);
        }
        setTitle("Chia sẻ: " + placeName);
        btnShare.setEnabled(!selectedFriends().isEmpty());
        if (!place.id.isEmpty()) listenForPlaceChanges(place.id);
    }

    private void loadFriends() {
        tvFriendsEmpty.setText("Đang tải…");
        tvFriendsEmpty.setVisibility(View.VISIBLE);
        friendsListener = FirebaseUserRepository.listOtherUsers(this, new FirebaseUserRepository.UsersCallback() {
            @Override public void onSuccess(List<UserProfile> users) {
                Set<String> selectedIds = new HashSet<>();
                for (Friend friend : allFriends) if (friend.isSelected()) selectedIds.add(friend.getId());
                allFriends.clear();
                for (UserProfile user : users) {
                    String label = user.username.isEmpty() ? "Người dùng TapMap" : "@" + user.username;
                    Friend friend = new Friend(user.uid, user.name, label, user.imageUrl);
                    friend.setSelected(selectedIds.contains(user.uid));
                    allFriends.add(friend);
                }
                applyRelationshipStatuses();
                showFilteredFriends();
            }
            @Override public void onError(Exception error) {
                tvFriendsEmpty.setText("Không tải được bạn bè.");
                tvFriendsEmpty.setVisibility(View.VISIBLE);
            }
        });
    }

    private void listenForFriendRequests() {
        friendRequestsListener = FriendRequestRepository.listen(this,
                new FriendRequestRepository.Listener() {
                    @Override public void onChanged(List<FriendRequestRepository.Request> requests,
                                                    Map<String, String> relationships) {
                        relationshipStatuses.clear();
                        relationshipStatuses.putAll(relationships);
                        btnFriendRequests.setText(requests.isEmpty() ? "Lời mời"
                                : "Lời mời · " + requests.size());
                        applyRelationshipStatuses();
                        showFilteredFriends();
                    }

                    @Override public void onError(Exception error) {
                        Toast.makeText(ShareLocationActivity.this,
                                "Không tải được lời mời.",
                                Toast.LENGTH_LONG).show();
                    }
                });
    }

    private void applyRelationshipStatuses() {
        for (Friend friend : allFriends) {
            String status = relationshipStatuses.getOrDefault(friend.getId(), FriendRequestRepository.NONE);
            friend.setRelationshipStatus(status);
            if (!FriendRequestRepository.ACCEPTED.equals(status)) friend.setSelected(false);
        }
    }

    private void onFriendAction(Friend friend) {
        String status = friend.getRelationshipStatus();
        if (FriendRequestRepository.INCOMING.equals(status)) {
            Toast.makeText(this, "Xem lời mời để xác nhận.", Toast.LENGTH_SHORT).show();
            return;
        }
        if (FriendRequestRepository.ACCEPTED.equals(status)) {
            Toast.makeText(this, "Hai bạn đã là bạn bè.", Toast.LENGTH_SHORT).show();
            return;
        }
        if (FriendRequestRepository.OUTGOING.equals(status)) {
            Toast.makeText(this, "Đang chờ xác nhận.", Toast.LENGTH_SHORT).show();
            return;
        }
        FriendRequestRepository.send(this, friend.getId(), new FriendRequestRepository.Callback() {
            @Override public void onSuccess() {
                Toast.makeText(ShareLocationActivity.this, "Đã gửi lời mời kết bạn.", Toast.LENGTH_SHORT).show();
            }
            @Override public void onError(Exception error) {
                Toast.makeText(ShareLocationActivity.this,
                        "Không gửi được lời mời. Kiểm tra quyền Firestore rồi thử lại.", Toast.LENGTH_LONG).show();
            }
        });
    }

    private void listenForPlaceChanges(String id) {
        if (id.equals(listenedPlaceId)) return;
        if (placeListener != null) placeListener.remove();
        listenedPlaceId = id;
        placeListener = FirebasePlaceRepository.listenToPlace(this, id,
                new FirebasePlaceRepository.PlaceListenerCallback() {
                    @Override public void onPlace(Place updated) {
                        placeName = updated.name;
                        placeImageUrl = updated.imageUrl;
                        setTitle("Chia sẻ: " + placeName);
                        ((TextView) findViewById(R.id.tvPlaceName)).setText(updated.name);
                        ((TextView) findViewById(R.id.tvPlaceAddress)).setText(updated.address);
                        ((TextView) findViewById(R.id.tvPlaceCategory)).setText(
                                updated.category + (updated.tags.isEmpty() ? "" : "  ·  "
                                        + PlaceFormUtils.tagsForDisplay(updated.tags)));
                        ImageView image = findViewById(R.id.imgPlace);
                        TextView addImage = findViewById(R.id.tvPlaceAddImage);
                        if (updated.imageUrl == null || updated.imageUrl.trim().isEmpty()) {
                            image.setImageResource(R.drawable.ic_launcher_foreground);
                            addImage.setVisibility(View.VISIBLE);
                        } else {
                            addImage.setVisibility(View.GONE);
                            Glide.with(ShareLocationActivity.this).load(updated.imageUrl).centerCrop().into(image);
                        }
                        for (int i = 0; i < AppData.PLACES.size(); i++) {
                            if (id.equals(AppData.PLACES.get(i).id)) {
                                AppData.PLACES.set(i, updated);
                                break;
                            }
                        }
                    }
                    @Override public void onMissing() {
                        Toast.makeText(ShareLocationActivity.this,
                                "Địa điểm đã bị xóa.", Toast.LENGTH_SHORT).show();
                        finish();
                    }
                    @Override public void onError(Exception error) {
                        android.util.Log.w("ShareLocationActivity", "Realtime place listener failed", error);
                    }
                });
    }

    private void showFilteredFriends() {
        String query = edtSearch.getText().toString().trim().toLowerCase(Locale.ROOT);
        String normalizedQuery = query.replaceFirst("^@+", "");
        boolean searching = !normalizedQuery.isEmpty();
        List<Friend> filtered = new ArrayList<>();
        for (Friend friend : allFriends) {
            boolean accepted = FriendRequestRepository.ACCEPTED.equals(friend.getRelationshipStatus());
            if (!searching) {
                if (accepted) filtered.add(friend);
                continue;
            }
            String name = friend.getName().toLowerCase(Locale.ROOT);
            String username = friend.getSubtitle().replaceFirst("^@+", "").toLowerCase(Locale.ROOT);
            if (name.contains(normalizedQuery) || username.contains(normalizedQuery)) filtered.add(friend);
        }
        tvFriendsListTitle.setText(searching ? "Kết quả" : "Bạn bè");
        adapter.replaceFriends(filtered);
        if (filtered.isEmpty()) {
            tvFriendsEmpty.setText(searching
                    ? "Không tìm thấy tài khoản."
                    : "Chưa có bạn bè. Tìm tài khoản để kết bạn.");
            tvFriendsEmpty.setVisibility(View.VISIBLE);
        } else tvFriendsEmpty.setVisibility(View.GONE);
        updateSelectedCount();
    }

    private List<Friend> selectedFriends() {
        List<Friend> selected = new ArrayList<>();
        for (Friend friend : allFriends) if (friend.isSelected()) selected.add(friend);
        return selected;
    }

    private void onFriendSelectionChanged(int ignoredVisibleCount) { updateSelectedCount(); }

    private void updateSelectedCount() {
        int count = selectedFriends().size();
        tvSelectedCount.setText(count + " đã chọn");
        btnShare.setText(count == 0 ? "Chia sẻ" : "Chia sẻ · " + count);
        btnShare.setEnabled(count > 0 && selectedPlace != null);
    }

    private void shareSelectedFriends() {
        List<Friend> selected = selectedFriends();
        if (selectedPlace == null) {
            showFavoritePlacePicker();
            return;
        }
        btnShare.setEnabled(false);
        FirebaseShareRepository.share(this, placeId, placeName, selected,
                new FirebaseShareRepository.Callback() {
                    @Override public void onSuccess() {
                        Intent intent = new Intent(ShareLocationActivity.this, ShareSuccessActivity.class);
                        intent.putExtra("SELECTED_COUNT", selected.size());
                        intent.putExtra("PLACE_NAME", placeName);
                        intent.putExtra("IMAGE_URL", placeImageUrl);
                        startActivity(intent);
                    }
                    @Override public void onError(Exception error) {
                        updateSelectedCount();
                        Toast.makeText(ShareLocationActivity.this,
                                "Chia sẻ thất bại. Thử lại.", Toast.LENGTH_LONG).show();
                    }
                });
    }

    @Override protected void onDestroy() {
        if (friendsListener != null) friendsListener.remove();
        if (friendRequestsListener != null) friendRequestsListener.remove();
        if (placeListener != null) placeListener.remove();
        if (placesListener != null) placesListener.remove();
        super.onDestroy();
    }
}
