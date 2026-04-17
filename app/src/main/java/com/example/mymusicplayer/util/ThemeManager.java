package com.example.mymusicplayer.util;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.appcompat.app.AppCompatDelegate;

public final class ThemeManager {

    private static final String PREFS_NAME = "theme_prefs";
    private static final String KEY_NIGHT_MODE = "night_mode";

    private ThemeManager() {
    }

    public static int getSavedNightMode(Context context) {
        SharedPreferences preferences = context.getApplicationContext()
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return preferences.getInt(KEY_NIGHT_MODE, AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
    }

    public static boolean isDarkTheme(Context context) {
        return getSavedNightMode(context) == AppCompatDelegate.MODE_NIGHT_YES;
    }

    public static void toggleTheme(Context context) {
        int nextMode = isDarkTheme(context)
                ? AppCompatDelegate.MODE_NIGHT_NO
                : AppCompatDelegate.MODE_NIGHT_YES;
        context.getApplicationContext()
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putInt(KEY_NIGHT_MODE, nextMode)
                .apply();
        AppCompatDelegate.setDefaultNightMode(nextMode);
    }
}
