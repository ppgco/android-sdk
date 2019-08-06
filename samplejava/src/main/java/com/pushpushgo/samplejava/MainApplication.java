package com.pushpushgo.samplejava;

import android.app.Application;
import com.pushpushgo.sdk.facade.PushPushGoFacade;

public class MainApplication extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        PushPushGoFacade.getInstance(getApplicationContext());
    }
}
