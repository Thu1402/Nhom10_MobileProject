package com.example.app.ui;

import android.content.Context;
import android.graphics.Color;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.bumptech.glide.Glide;
import com.google.android.material.card.MaterialCardView;
import com.example.app.R;
import com.example.app.App;
import com.example.app.model.Place;
import com.google.firebase.firestore.FirebaseFirestore;

public class PlaceCardView extends MaterialCardView {
    public PlaceCardView(Context context, Place place, boolean compact, boolean showOwner, OnClickListener listener) {
        super(context);
        setRadius(Ui.dp(context, 20));
        setCardBackgroundColor(Color.WHITE);
        setCardElevation(Ui.dp(context, 3));
        setStrokeColor(Ui.color(context, R.color.line));
        setStrokeWidth(compact ? Ui.dp(context, 1) : 0);
        setClickable(true);
        setFocusable(true);
        setOnClickListener(listener);

        LinearLayout root = new LinearLayout(context);
        root.setOrientation(compact ? LinearLayout.HORIZONTAL : LinearLayout.VERTICAL);

        ImageView image = new ImageView(context);
        image.setScaleType(ImageView.ScaleType.CENTER_CROP);
        int imageSize = compact ? 88 : 174;
        root.addView(image, new LinearLayout.LayoutParams(
                compact ? Ui.dp(context, imageSize) : ViewGroup.LayoutParams.MATCH_PARENT,
                Ui.dp(context, imageSize)
        ));
        Glide.with(context).load(place.imageUrl).centerCrop().into(image);

        LinearLayout body = new LinearLayout(context);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setGravity(Gravity.CENTER_VERTICAL);
        Ui.setPadding(body, 14, 12);
        TextView category = Ui.text(context, place.category.toUpperCase(), 9, Ui.color(context, R.color.violet), true);
        TextView title = Ui.text(context, place.name, 14, Ui.color(context, R.color.ink), true);
        String addressText = "⌖  " + place.address
                + (place.distance == null || place.distance.isEmpty() ? "" : "  ·  " + place.distance);
        TextView address = Ui.text(context, addressText, 9, Ui.color(context, R.color.muted), false);
        body.addView(category);
        body.addView(title, Ui.margin(-1, -2, 0, 4, 0, 0, context));
        body.addView(address, Ui.margin(-1, -2, 0, 5, 0, 0, context));
        if (showOwner && App.isAdmin() && place.ownerId != null && !place.ownerId.isEmpty()) {
            TextView owner = Ui.text(context, "Tác giả: Đang tải…", 8, Ui.color(context, R.color.muted), false);
            body.addView(owner, Ui.margin(-1, -2, 0, 3, 0, 0, context));
            FirebaseFirestore.getInstance().collection("publicProfiles").document(place.ownerId).get()
                    .addOnSuccessListener(profile -> {
                        if (owner.getParent() != body) return;
                        String username = profile.getString("username");
                        String name = profile.getString("name");
                        String account = username == null || username.trim().isEmpty()
                                ? name : "@" + username.trim();
                        if (account == null || account.trim().isEmpty()) account = place.ownerId;
                        owner.setText("Tác giả: " + account);
                    })
                    .addOnFailureListener(error -> {
                        if (owner.getParent() == body) owner.setText("Tác giả: " + place.ownerId);
                    });
        }
        root.addView(body, new LinearLayout.LayoutParams(0, compact ? Ui.dp(context, 88) : ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        addView(root);
    }
}

