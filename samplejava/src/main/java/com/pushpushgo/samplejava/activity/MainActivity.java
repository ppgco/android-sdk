package com.pushpushgo.samplejava.activity;

import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;

import androidx.appcompat.app.AppCompatActivity;

import com.pushpushgo.samplejava.R;
import com.pushpushgo.sdk.PushPushGo;

public class MainActivity extends AppCompatActivity {

    private final PushPushGo ppg = PushPushGo.getInstance();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        ppg.createBeacon()
                .appendTag("jacek")
                .appendTag("sabina", "dziewczyna")
                .set("see_invoice", true)
                .setCustomId("CRMID200")
                .removeTag("marek", "janek")
                .send();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }
}
