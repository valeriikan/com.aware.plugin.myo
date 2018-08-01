package com.aware.plugin.myo;

import android.app.Activity;
import android.content.Intent;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

public class DialogActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_dialog);

        final EditText editText = findViewById(R.id.et);

        Button button = findViewById(R.id.button);
        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                //finish();

                String mac = editText.getText().toString();
                if (mac.equals("")) {
                    Toast.makeText(DialogActivity.this, "MAC cannot be empty", Toast.LENGTH_SHORT).show();

                } else {
                    Intent intent = new Intent(Plugin.INTENT_CONNECT_MAC);
                    intent.putExtra(Plugin.MYO_MAC_ADDRESS, mac);
                    sendBroadcast(intent);
                    finish();
                }
            }
        });
    }
}
