package cz.helicode.hondaescanner;

import android.app.Application;

public class HondaEApplication extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        ThemeHelper.applyTheme(this);
    }
}
