package com.ath.fuel;

import android.app.Activity;
import android.app.Application;
import android.view.View;

import androidx.annotation.NonNull;

public class FuelSubmodule extends FuelModule {

    public FuelSubmodule(@NonNull Application app) {
        super(app, app);
    }

    public FuelSubmodule(@NonNull Activity activity) {
        //noinspection ConstantConditions
        super(activity.getApplication(), activity);
    }

    public FuelSubmodule(@NonNull View view) {
        //noinspection ConstantConditions
        super((Application) view.getContext().getApplicationContext(), view.getContext());
    }

}
