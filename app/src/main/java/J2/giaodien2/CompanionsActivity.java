package J2.giaodien2;

import android.os.Bundle;
import android.widget.Button;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

import com.example.giaodien2.R;

public class CompanionsActivity extends AppCompatActivity {

    private Button btnAccept;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_companions);

        btnAccept = findViewById(R.id.btnAccept);

        btnAccept.setOnClickListener(v -> {
            Toast.makeText(CompanionsActivity.this, "Đã chấp nhận lời mời kết bạn!", Toast.LENGTH_SHORT).show();
        });
    }
}