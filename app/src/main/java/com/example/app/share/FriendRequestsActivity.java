package com.example.app.share;

import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.bumptech.glide.Glide;
import com.example.app.R;
import com.example.app.data.FriendRequestRepository;
import com.example.app.data.FirebaseUserRepository;
import com.example.app.model.UserProfile;
import com.example.app.ui.Ui;
import com.google.firebase.firestore.ListenerRegistration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class FriendRequestsActivity extends AppCompatActivity {
    private LinearLayout requestsContainer;
    private TextView emptyMessage;
    private final List<Map.Entry<String, String>> incomingRequests = new ArrayList<>();
    private final Map<String, UserProfile> profilesByUid = new HashMap<>();
    private ListenerRegistration requestsListener;
    private ListenerRegistration profilesListener;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_friend_requests);
        requestsContainer = findViewById(R.id.pendingRequestsContainer);
        emptyMessage = findViewById(R.id.tvNoFriendRequests);
        TextView back = findViewById(R.id.btnBackRequests);
        back.setOnClickListener(v -> finish());
        emptyMessage.setText("Đang tải…");
        emptyMessage.setVisibility(View.VISIBLE);

        requestsListener = FriendRequestRepository.listen(this,
                new FriendRequestRepository.Listener() {
                    @Override public void onChanged(List<Map.Entry<String, String>> incoming,
                                                    Map<String, String> relationships) {
                        incomingRequests.clear();
                        incomingRequests.addAll(incoming);
                        renderRequests();
                    }
                    @Override public void onError(Exception error) {
                        emptyMessage.setText("Không tải được lời mời.");
                        emptyMessage.setVisibility(View.VISIBLE);
                    }
                });

        profilesListener = FirebaseUserRepository.listOtherUsers(this,
                new FirebaseUserRepository.UsersCallback() {
                    @Override public void onSuccess(List<UserProfile> users) {
                        profilesByUid.clear();
                        for (UserProfile user : users) profilesByUid.put(user.uid, user);
                        renderRequests();
                    }
                    @Override public void onError(Exception error) {
                        renderRequests();
                    }
                });
    }

    private void renderRequests() {
        if (requestsContainer == null || isFinishing()) return;
        requestsContainer.removeAllViews();
        if (incomingRequests.isEmpty()) {
            emptyMessage.setText("Chưa có lời mời.");
            emptyMessage.setVisibility(View.VISIBLE);
            return;
        }
        emptyMessage.setVisibility(View.GONE);
        for (Map.Entry<String, String> request : incomingRequests) {
            addRequestRow(request, profilesByUid.get(request.getValue()));
        }
    }

    private void addRequestRow(Map.Entry<String, String> request, UserProfile profile) {
        int horizontal = dp(12);
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(horizontal, dp(10), horizontal, dp(10));
        row.setBackgroundResource(R.drawable.bg_edit_button);
        LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        rowParams.bottomMargin = dp(8);
        requestsContainer.addView(row, rowParams);

        ImageView avatar = new ImageView(this);
        avatar.setScaleType(ImageView.ScaleType.CENTER_CROP);
        avatar.setBackgroundResource(R.drawable.bg_avatar);
        row.addView(avatar, new LinearLayout.LayoutParams(dp(48), dp(48)));
        if (profile != null && !profile.imageUrl.isEmpty()) {
            Glide.with(this).load(Ui.imageModel(profile.imageUrl)).circleCrop().into(avatar);
        } else avatar.setImageResource(R.drawable.ic_launcher_foreground);

        LinearLayout details = new LinearLayout(this);
        details.setOrientation(LinearLayout.VERTICAL);
        details.setPadding(dp(10), 0, dp(8), 0);
        TextView name = new TextView(this);
        name.setText(profile == null || profile.name.isEmpty() ? "Người dùng TapMap" : profile.name);
        name.setTextColor(getColor(R.color.ink));
        name.setTextSize(14);
        name.setTypeface(null, android.graphics.Typeface.BOLD);
        details.addView(name);
        TextView username = new TextView(this);
        String usernameValue = profile == null ? "" : profile.username.replaceFirst("^@+", "");
        username.setText(usernameValue.isEmpty() ? "Đã gửi lời mời"
                : "@" + usernameValue);
        username.setTextColor(getColor(R.color.muted));
        username.setTextSize(12);
        details.addView(username);
        row.addView(details, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        TextView accept = actionButton("Chấp nhận", true);
        accept.setOnClickListener(v -> respond(request, accept, true));
        row.addView(accept);
        TextView reject = actionButton("Từ chối", false);
        LinearLayout.LayoutParams rejectParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, dp(36));
        rejectParams.leftMargin = dp(6);
        row.addView(reject, rejectParams);
        reject.setOnClickListener(v -> respond(request, reject, false));
    }

    private TextView actionButton(String title, boolean primary) {
        TextView button = new TextView(this);
        button.setText(title);
        button.setGravity(Gravity.CENTER);
        button.setPadding(dp(9), 0, dp(9), 0);
        button.setTextSize(12);
        button.setTypeface(null, android.graphics.Typeface.BOLD);
        button.setTextColor(getColor(primary ? R.color.violet_dark : R.color.muted));
        button.setBackgroundResource(R.drawable.bg_close_button);
        button.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, dp(36)));
        return button;
    }

    private void respond(Map.Entry<String, String> request, TextView button, boolean accept) {
        button.setEnabled(false);
        FriendRequestRepository.respond(this, request.getKey(), accept,
                new FriendRequestRepository.Callback() {
                    @Override public void onSuccess() {
                        Toast.makeText(FriendRequestsActivity.this,
                                accept ? "Đã chấp nhận." : "Đã từ chối.",
                                Toast.LENGTH_SHORT).show();
                    }
                    @Override public void onError(Exception error) {
                        button.setEnabled(true);
                        Toast.makeText(FriendRequestsActivity.this,
                                "Không cập nhật được lời mời.",
                                Toast.LENGTH_LONG).show();
                    }
                });
    }

    private int dp(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    @Override protected void onDestroy() {
        if (requestsListener != null) requestsListener.remove();
        if (profilesListener != null) profilesListener.remove();
        super.onDestroy();
    }
}
