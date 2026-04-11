package com.example.mymusicplayer.data.remote;

import com.example.mymusicplayer.BuildConfig;

public final class ApiConfig {

    public static final String BASE_URL = "https://api.jamendo.com/v3.0/";
    public static final String CLIENT_ID = BuildConfig.JAMENDO_CLIENT_ID;

    private ApiConfig() {
    }
}
