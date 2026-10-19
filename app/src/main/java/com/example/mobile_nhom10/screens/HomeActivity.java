package com.example.mobile_nhom10.screens;

import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.textfield.TextInputLayout;
import com.example.mobile_nhom10.R;
import com.example.mobile_nhom10.data.AppData;
import com.example.mobile_nhom10.ui.PlaceCardView;
import com.example.mobile_nhom10.ui.Ui;

public class HomeActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_home);
        View root = findViewById(android.R.id.content);
        LinearLayout page = root.findViewById(R.id.home_content);

        page.addView(Ui.header(this, "Thứ sáu, 24 tháng 5", "Đi đâu hôm nay?"));
        TextInputLayout search = Ui.input(this, "Tìm địa điểm...", false);
        page.addView(search, Ui.margin(-1, -2, 0, 22, 0, 18, this));

        LinearLayout mood = new LinearLayout(this);
        mood.setOrientation(LinearLayout.VERTICAL);
        mood.setPadding(Ui.dp(this, 22), Ui.dp(this, 22), Ui.dp(this, 22), Ui.dp(this, 22));
        mood.setBackground(Ui.background(Ui.color(this, R.color.lime), 24, this));
        mood.addView(Ui.text(this, "GỢI Ý CHO BẠN", 10, Ui.color(this, R.color.muted), true));
        mood.addView(Ui.text(this, "Trốn phố một chút?", 21, Ui.color(this, R.color.ink), true), Ui.margin(-1, -2, 0, 5, 0, 0, this));
        mood.addView(Ui.text(this, "8 địa điểm xanh mát trong bán kính 5 km.", 11, Color.rgb(91, 106, 68), false), Ui.margin(-1, -2, 0, 7, 0, 0, this));
        TextView discover = Ui.text(this, "Khám phá ngay  →", 11, Ui.color(this, R.color.violet), true);
        mood.addView(discover, Ui.margin(-1, -2, 0, 12, 0, 0, this));
        page.addView(mood, Ui.margin(-1, -2, 0, 0, 0, 28, this));

        page.addView(sectionTitle("Nổi bật gần bạn", "Xem tất cả"));
        HorizontalScrollView horizontal = new HorizontalScrollView(this);
        horizontal.setHorizontalScrollBarEnabled(false);
        LinearLayout featured = new LinearLayout(this);
        featured.setOrientation(LinearLayout.HORIZONTAL);
        for (int index = 0; index < 2; index++) {
            final int position = index;
            PlaceCardView card = new PlaceCardView(this, AppData.PLACES.get(index), false, view -> open(position));
            featured.addView(card, Ui.margin(278, -2, 0, 0, 12, 0, this));
        }
        horizontal.addView(featured);
        page.addView(horizontal, Ui.margin(-1, -2, 0, 14, 0, 28, this));

        page.addView(sectionTitle("Mới được lưu", "Xem tất cả"));
        for (int index = 1; index < AppData.PLACES.size(); index++) {
            final int position = index;
            page.addView(new PlaceCardView(this, AppData.PLACES.get(index), true, view -> open(position)),
                    Ui.margin(-1, -2, 0, 0, 0, 12, this));
        }
    }

    private LinearLayout sectionTitle(String title, String action) {
        LinearLayout row = Ui.row(this);
        row.addView(Ui.text(this, title, 17, Ui.color(this, R.color.ink), true));
        row.addView(Ui.spacer(this));
        row.addView(Ui.text(this, action, 10, Ui.color(this, R.color.violet), true));
        return row;
    }

    private void open(int position) {
        startActivity(new android.content.Intent(this, PlaceDetailActivity.class).putExtra(PlaceDetailActivity.PLACE_INDEX, position));
    }
}
