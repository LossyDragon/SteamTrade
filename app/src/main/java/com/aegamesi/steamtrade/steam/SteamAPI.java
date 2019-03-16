package com.aegamesi.steamtrade.steam;

import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class SteamAPI {

    private static Retrofit retrofit;
    private static final String BASE_URL = "https://store.steampowered.com/api/";

    public static Retrofit getApiInstance() {
        if (retrofit == null) {

            retrofit = new Retrofit.Builder()
                    .baseUrl(BASE_URL)
                    .addConverterFactory(GsonConverterFactory.create())
                    .build();
        }
        return retrofit;
    }
}

