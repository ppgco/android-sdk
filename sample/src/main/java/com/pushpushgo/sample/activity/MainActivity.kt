package com.pushpushgo.sample.activity

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Menu
import android.view.MenuItem
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.common.util.concurrent.FutureCallback
import com.google.common.util.concurrent.Futures
import com.pushpushgo.sample.R
import com.pushpushgo.sdk.PushPushGo

class MainActivity : AppCompatActivity() {

    private val ppg by lazy { PushPushGo.getInstance() }

    @SuppressLint("SetTextI18n")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        ppg.handleBackgroundNotificationClick(intent)

        findViewById<TextView>(R.id.version).text = PushPushGo.VERSION

        findViewById<Button>(R.id.register).setOnClickListener {
            Futures.addCallback(ppg.createSubscriber(), object : FutureCallback<String> {
                override fun onSuccess(result: String) {
                    Toast.makeText(this@MainActivity, "Subscribed! $result", Toast.LENGTH_SHORT).show()
                }

                override fun onFailure(t: Throwable) {
                    Toast.makeText(this@MainActivity, "Can't subscribe! ${t.message}", Toast.LENGTH_SHORT).show()
                }
            }, ContextCompat.getMainExecutor(this))
        }
        findViewById<Button>(R.id.unregister).setOnClickListener {
            ppg.unregisterSubscriber()
        }
        findViewById<Button>(R.id.check).setOnClickListener {
            findViewById<TextView>(R.id.content).text = "Status: " + (if (PushPushGo.getInstance().isSubscribed()) "subscribed" else "unsubscribed")

            Futures.addCallback(ppg.getPushToken(), object : FutureCallback<String> {
                override fun onSuccess(result: String) {
                    findViewById<TextView>(R.id.token).text = result
                }

                override fun onFailure(t: Throwable) {
                    findViewById<TextView>(R.id.token).text = t.message
                }
            }, ContextCompat.getMainExecutor(this))
        }
        findViewById<Button>(R.id.beacons).setOnClickListener {
            startActivity(Intent(baseContext, BeaconActivity::class.java))
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
