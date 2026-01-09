package com.pushpushgo.sdk.sample.push.java;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.pushpushgo.sdk.push.PushNotifications;

import timber.log.Timber;

public class MainApplication extends Application {
    @Override
    public void onCreate() {
        super.onCreate();

        Timber.plant(new DebugTree());

        PushNotifications.initialize(this);
    }

    static class DebugTree extends Timber.Tree {
        @Override
        protected void log(int i, @Nullable String s, @NonNull String s1, @Nullable Throwable throwable) {
            System.out.println(s1);
        }
    }
}
