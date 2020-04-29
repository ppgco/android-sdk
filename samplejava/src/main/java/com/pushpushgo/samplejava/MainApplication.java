package com.pushpushgo.samplejava;

import android.app.Application;
import com.pushpushgo.sdk.PushPushGo;

public class MainApplication extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        PushPushGo.getInstance(getApplicationContext());
    }
}
