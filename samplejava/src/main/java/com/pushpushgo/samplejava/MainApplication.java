package com.pushpushgo.samplejava;

import android.app.Application;

import com.pushpushgo.sdk.PushPushGo;

import timber.log.Timber;

public class MainApplication extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        PushPushGo.getInstance(this);
        Timber.plant(new Timber.DebugTree());
    }
}
