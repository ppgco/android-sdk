package com.pushpushgo.samplejava;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.pushpushgo.sdk.PushPushGo;

import timber.log.Timber;

public class MainApplication extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        PushPushGo.getInstance(this);
        Timber.plant(new DebugTree());
    }

    static class DebugTree extends Timber.Tree {

        @Override
        protected void log(int i, @Nullable String s, @NonNull String s1, @Nullable Throwable throwable) {
            System.out.println(s1);
        }
    }
}
