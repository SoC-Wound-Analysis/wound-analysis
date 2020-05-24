package sg.edu.woundanalysis;

import android.util.Log;
import androidx.appcompat.app.AppCompatActivity;
import android.os.Bundle;
import androidx.appcompat.widget.Toolbar;

/**
 * Represents the Activity for capturing images.
 */
public class MainActivity extends AppCompatActivity {

    private boolean safeOpenCamera(int id) {
        boolean cameraIsOpened = false;

        try {

        } catch (Exception e) {
            Log.e(getString(R.string.app_name),
                    String.format(": failed to open camera of id  %d", String.valueOf(id))
            );
        }

        return cameraIsOpened;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Set the tile of the Toolbar.
        Toolbar appbar = (Toolbar) findViewById(R.id.appbar);
        appbar.setTitle(R.string.app_name);

        //Request camera permission

    }

}
