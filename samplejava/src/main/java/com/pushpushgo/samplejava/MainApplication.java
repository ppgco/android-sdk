package com.pushpushgo.samplejava;

import androidx.multidex.MultiDexApplication;

import com.pushpushgo.sdk.PushPushGo;

import timber.log.Timber;

public class MainApplication extends MultiDexApplication {
    @Override
    public void onCreate() {
        super.onCreate();
        PushPushGo.getInstance(getApplicationContext());
        Timber.plant(new Timber.DebugTree());
    }
}
