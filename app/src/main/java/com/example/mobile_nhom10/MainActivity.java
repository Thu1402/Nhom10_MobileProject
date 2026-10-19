package com.example.mobile_nhom10;

import android.content.Intent;
import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.example.mobile_nhom10.screens.AddPlaceActivity;
import com.example.mobile_nhom10.screens.HomeActivity;

public class MainActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        BottomNavigationView navigation = findViewById(R.id.bottom_navigation);
        navigation.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.nav_home) {
                startActivity(new Intent(this, HomeActivity.class));
                return true;
            }
            if (id == R.id.nav_add) {
                startActivity(new Intent(this, AddPlaceActivity.class));
                return true;
            }
            // Các mục còn lại thuộc phạm vi của thành viên khác.
            return false;
        });
        navigation.setSelectedItemId(R.id.nav_home);
    }
}
