package J2.giaodien2;

import android.os.Bundle;
import android.widget.Button;
import android.widget.ImageButton;
import androidx.appcompat.app.AppCompatActivity;

public class ShareSuccessActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_share_success);

        ImageButton btnBack = findViewById(R.id.btnBack);
        Button btnBackToLocation = findViewById(R.id.btnBackToLocation);

        btnBack.setOnClickListener(v -> finish());
        btnBackToLocation.setOnClickListener(v -> finish());
    }
}