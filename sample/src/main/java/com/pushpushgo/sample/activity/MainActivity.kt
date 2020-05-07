package com.pushpushgo.sample.activity

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Menu
import android.view.MenuItem
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import com.pushpushgo.sample.R
import com.pushpushgo.sdk.PushPushGo
import kotlinx.android.synthetic.main.activity_main.*

class MainActivity : AppCompatActivity() {

    private val ppg by lazy { PushPushGo.getInstance() }

    @SuppressLint("SetTextI18n")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        ppg.handleBackgroundNotificationClick(intent)

        register.setOnClickListener {
            ppg.registerSubscriber()
        }
        unregister.setOnClickListener {
            ppg.unregisterSubscriber()
        }
        check.setOnClickListener {
            content.text = "Status: " + (if (PushPushGo.getInstance().isSubscribed()) "subscribed" else "unsubscribed")
        }
        beacon1.setOnClickListener {
            ppg.createBeacon()
                .appendTag("jacek")
                .appendTag("sabina", "dziewczyna")
                .set("see_invoice", true)
                .setCustomId("CRMCI")
                .removeTag("marek", "janek")
                .send()
        }
        beacon2.setOnClickListener {
            ppg.createBeacon()
                .set("basket_price", 299)
                .setCustomId("CRMCI")
                .send()
        }
        beacon3.setOnClickListener {
            ppg.createBeacon()
                .set("basket_price", 301)
                .setCustomId("CRMCI")
                .send()
        }
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        PushPushGo.getInstance().handleBackgroundNotificationClick(intent)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        // Inflate the menu; this adds items to the action bar if it is present.
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        return when (item.itemId) {
            R.id.action_settings -> openNotificationsSettings()
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun openNotificationsSettings(): Boolean {
        val intent = Intent()
        when {
            Build.VERSION.SDK_INT > Build.VERSION_CODES.O -> intent.setOpenSettingsForApiLarger25()
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP -> intent.setOpenSettingsForApiBetween21And25()
            else -> intent.setOpenSettingsForApiLess21()
        }
        startActivity(intent)
        return true
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun Intent.setOpenSettingsForApiLarger25() {
        action = Settings.ACTION_APP_NOTIFICATION_SETTINGS
        putExtra("android.provider.extra.APP_PACKAGE", packageName)
    }

    private fun Intent.setOpenSettingsForApiBetween21And25() {
        action = Settings.ACTION_APPLICATION_DETAILS_SETTINGS
        putExtra("app_package", packageName)
        putExtra("app_uid", applicationInfo?.uid)
    }

    private fun Intent.setOpenSettingsForApiLess21() {
        action = Settings.ACTION_APPLICATION_DETAILS_SETTINGS
        addCategory(Intent.CATEGORY_DEFAULT)
        data = Uri.parse("package:$packageName")
    }
}
