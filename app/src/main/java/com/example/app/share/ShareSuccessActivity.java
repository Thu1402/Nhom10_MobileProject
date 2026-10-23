package com.example.app.share;

import android.os.Bundle;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import com.bumptech.glide.Glide;
import androidx.appcompat.app.AppCompatActivity;
import com.example.app.R;

public class ShareSuccessActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_share_success);

        ImageButton btnBack = findViewById(R.id.btnBack);
        Button btnBackToLocation = findViewById(R.id.btnBackToLocation);
        String placeName = getIntent().getStringExtra("PLACE_NAME");
        int selectedCount = getIntent().getIntExtra("SELECTED_COUNT", 0);
        if (placeName != null && !placeName.isEmpty()) {
            ((TextView) findViewById(R.id.tvSharedPlaceName)).setText(placeName);
        }
        String imageUrl = getIntent().getStringExtra("IMAGE_URL");
        if (imageUrl != null && !imageUrl.isEmpty()) {
            Glide.with(this).load(imageUrl).centerCrop().into((ImageView) findViewById(R.id.ivSharedPlacePhoto));
        }
        ((TextView) findViewById(R.id.tvShareSuccessDescription))
                .setText("Đã gửi cho " + selectedCount + " bạn.");

        btnBack.setOnClickListener(v -> finish());
        btnBackToLocation.setOnClickListener(v -> finish());
    }
}

