package J2.giaodien2;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Button btnScreen1 = findViewById(R.id.btnScreen1);
        Button btnScreen2 = findViewById(R.id.btnScreen2);
        Button btnScreen3 = findViewById(R.id.btnScreen3);

        if (btnScreen1 != null) {
            btnScreen1.setOnClickListener(v -> {
                Intent intent = new Intent(MainActivity.this, ShareLocationActivity.class);
                startActivity(intent);
            });
        }

        if (btnScreen2 != null) {
            btnScreen2.setOnClickListener(v -> {
                Intent intent = new Intent(MainActivity.this, CompanionsActivity.class);
                startActivity(intent);
            });
        }

        if (btnScreen3 != null) {
            btnScreen3.setOnClickListener(v -> {
                Intent intent = new Intent(MainActivity.this, ShareSuccessActivity.class);
                startActivity(intent);
            });
        }
    }
}