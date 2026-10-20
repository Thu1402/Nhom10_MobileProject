package J2.giaodien2;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.giaodien2.Friend;
import com.example.giaodien2.R;
import com.example.giaodien2.SelectFriendAdapter;

import java.util.ArrayList;
import java.util.List;

public class ShareLocationActivity extends AppCompatActivity {

    private RecyclerView rvFriends;
    private SelectFriendAdapter adapter;
    private List<Friend> friendList;
    private TextView tvSelectedCount;
    private Button btnShare;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_share_location);

        tvSelectedCount = findViewById(R.id.tvSelectedCount);
        btnShare = findViewById(R.id.btnShare);
        rvFriends = findViewById(R.id.rvFriends);

        initData();

        adapter = new SelectFriendAdapter(this, friendList, this::onFriendSelectionChanged);
        rvFriends.setLayoutManager(new LinearLayoutManager(this));
        rvFriends.setAdapter(adapter);

        btnShare.setOnClickListener(v -> {
            int selectedCount = adapter.getSelectedFriends().size();
            if (selectedCount == 0) {
                Toast.makeText(this, "Vui lòng chọn ít nhất 1 người bạn!", Toast.LENGTH_SHORT).show();
                return;
            }
            // Chuyển sang Màn hình 3 (Đã chia sẻ)
            Intent intent = new Intent(ShareLocationActivity.this, ShareSuccessActivity.class);
            intent.putExtra("SELECTED_COUNT", selectedCount);
            startActivity(intent);
        });
    }

    private void initData() {
        friendList = new ArrayList<>();
        // Dùng android.R.drawable.ic_menu_gallery hoặc R.drawable.ic_launcher_foreground để test
        friendList.add(new Friend("1", "Mai An", "Bạn bè · 8 địa điểm chung", R.drawable.ic_launcher_foreground));
        friendList.add(new Friend("2", "Hoàng Minh", "Bạn bè · 3 địa điểm chung", R.drawable.ic_launcher_foreground));
        friendList.add(new Friend("3", "Thảo Nguyễn", "Bạn bè · Thích khám phá Đà Lạt", R.drawable.ic_launcher_foreground));
    }

    private void onFriendSelectionChanged(int count) {
        tvSelectedCount.setText("Đã chọn " + count + " người");
        btnShare.setText("Chia sẻ với " + count + " người");
    }
}