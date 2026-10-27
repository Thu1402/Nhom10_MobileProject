package com.example.app;

import android.content.Intent;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.annotation.LayoutRes;
import androidx.appcompat.app.AppCompatActivity;
import com.example.app.screens.AddPlaceActivity;
import com.example.app.screens.HomeActivity;
import com.example.app.screens.MapActivity;
import com.example.app.screens.ProfileActivity;
import com.example.app.share.ShareLocationActivity;

/** Shared bottom navigation for the five main app screens. */
public abstract class BaseNavigationActivity extends AppCompatActivity {
    private boolean useNavigationRail;

    @Override protected void onResume() {
        super.onResume();
        com.google.firebase.auth.FirebaseUser user = com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser();
        if (user == null || user.isAnonymous()) return;
        com.google.firebase.firestore.FirebaseFirestore.getInstance().collection("users").document(user.getUid()).get()
                .addOnSuccessListener(document -> {
                    if (!Boolean.TRUE.equals(document.getBoolean("blocked")) || isFinishing()) return;
                    com.google.firebase.auth.FirebaseAuth.getInstance().signOut();
                    android.widget.Toast.makeText(this,
                            "Tài khoản của bạn đã bị khóa. Vui lòng liên hệ quản trị viên.",
                            android.widget.Toast.LENGTH_LONG).show();
                    startActivity(new Intent(this, com.example.app.AuthActivity.class)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
                    finish();
                });
    }

    protected final void setContentViewWithNavigation(@LayoutRes int layoutResId) {
        LinearLayout root = new LinearLayout(this);
        int widthDp = getResources().getConfiguration().screenWidthDp;
        useNavigationRail = widthDp >= 600;
        root.setOrientation(useNavigationRail ? LinearLayout.HORIZONTAL : LinearLayout.VERTICAL);
        root.setBackgroundColor(getColor(R.color.cream));

        FrameLayout content = new FrameLayout(this);
        content.setId(View.generateViewId());
        LinearLayout.LayoutParams contentParams = useNavigationRail
                ? new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f)
                : new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f);
        root.addView(content, contentParams);
        getLayoutInflater().inflate(layoutResId, content, true);

        if (useNavigationRail && !(this instanceof MapActivity)) {
            View page = content.getChildAt(0);
            int availableWidth = Math.max(1, widthDp - 96);
            int maxWidthDp = Math.min(availableWidth, this instanceof HomeActivity ? 1100 : 900);
            FrameLayout.LayoutParams pageParams = new FrameLayout.LayoutParams(
                    dp(maxWidthDp), FrameLayout.LayoutParams.MATCH_PARENT, Gravity.CENTER);
            page.setLayoutParams(pageParams);
        }

        LinearLayout navigation = new LinearLayout(this);
        navigation.setOrientation(useNavigationRail ? LinearLayout.VERTICAL : LinearLayout.HORIZONTAL);
        navigation.setGravity(Gravity.CENTER);
        navigation.setBackgroundColor(getColor(R.color.surface));
        int[] itemIds = {R.id.nav_home, R.id.nav_map, R.id.nav_add, R.id.nav_friends, R.id.nav_profile};
        int[] itemIcons = {R.drawable.ic_home, R.drawable.ic_map, R.drawable.ic_add,
                R.drawable.ic_group, R.drawable.ic_person};
        String[] itemLabels = {getString(R.string.nav_home), getString(R.string.nav_map), "",
                getString(R.string.nav_friends), getString(R.string.nav_profile)};
        int selectedId = selectedItemForCurrentDestination();
        for (int i = 0; i < itemIds.length; i++) {
            final int itemId = itemIds[i];
            LinearLayout item = createNavigationItem(itemIcons[i], itemLabels[i], itemId == selectedId);
            item.setContentDescription(itemLabels[i].isEmpty() ? "Thêm địa điểm" : itemLabels[i]);
            item.setOnClickListener(v -> {
                if (isCurrentDestination(itemId)) return;
                Class<?> target = destinationFor(itemId);
                if (target == null) return;
                Intent intent = new Intent(this, target)
                        .addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
                navigation.postOnAnimation(() -> {
                    startActivity(intent);
                    overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
                });
            });
            if (useNavigationRail) {
                navigation.addView(item, new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
            } else {
                navigation.addView(item, new LinearLayout.LayoutParams(0,
                        LinearLayout.LayoutParams.MATCH_PARENT, 1f));
            }
        }

        if (useNavigationRail) {
            int width = dp(76);
            root.addView(navigation, new LinearLayout.LayoutParams(width,
                    LinearLayout.LayoutParams.MATCH_PARENT));
        } else {
            int height = dp(68);
            root.addView(navigation, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, height));
        }
        setContentView(root);
    }

    private LinearLayout createNavigationItem(int iconRes, String label, boolean selected) {
        LinearLayout item = new LinearLayout(this);
        item.setOrientation(LinearLayout.VERTICAL);
        item.setGravity(Gravity.CENTER);
        item.setClickable(true);
        item.setFocusable(true);

        LinearLayout iconSlot = new LinearLayout(this);
        iconSlot.setGravity(Gravity.CENTER);
        if (selected) {
            GradientDrawable indicator = new GradientDrawable();
            indicator.setColor(getColor(R.color.violet_soft));
            indicator.setCornerRadius(dp(20));
            iconSlot.setBackground(indicator);
        }
        ImageView icon = new ImageView(this);
        icon.setImageResource(iconRes);
        icon.setColorFilter(getColor(selected ? R.color.violet_dark : R.color.muted));
        iconSlot.addView(icon, new LinearLayout.LayoutParams(dp(23), dp(23)));
        item.addView(iconSlot, new LinearLayout.LayoutParams(dp(52), dp(30)));

        if (!label.isEmpty()) {
            TextView caption = new TextView(this);
            caption.setText(label);
            caption.setTextSize(10);
            caption.setTextColor(getColor(selected ? R.color.violet_dark : R.color.muted));
            caption.setGravity(Gravity.CENTER);
            caption.setSingleLine(true);
            caption.setIncludeFontPadding(false);
            LinearLayout.LayoutParams captionParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, dp(16));
            captionParams.topMargin = dp(2);
            item.addView(caption, captionParams);
        }
        return item;
    }

    private int dp(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private int selectedItemForCurrentDestination() {
        if (this instanceof HomeActivity) return R.id.nav_home;
        if (this instanceof MapActivity) return R.id.nav_map;
        if (this instanceof AddPlaceActivity) return R.id.nav_add;
        if (this instanceof ShareLocationActivity) return R.id.nav_friends;
        if (this instanceof ProfileActivity) return R.id.nav_profile;
        return R.id.nav_home;
    }

    private boolean isCurrentDestination(int itemId) {
        return itemId == selectedItemForCurrentDestination();
    }

    private Class<?> destinationFor(int itemId) {
        if (itemId == R.id.nav_home) return HomeActivity.class;
        if (itemId == R.id.nav_map) return MapActivity.class;
        if (itemId == R.id.nav_add) return AddPlaceActivity.class;
        if (itemId == R.id.nav_friends) return ShareLocationActivity.class;
        if (itemId == R.id.nav_profile) return ProfileActivity.class;
        return null;
    }
}
