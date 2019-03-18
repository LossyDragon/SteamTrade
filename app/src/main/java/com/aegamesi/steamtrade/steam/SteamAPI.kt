package com.aegamesi.steamtrade.steam

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object SteamAPI {

    private var retrofit: Retrofit? = null
    private const val BASE_URL = "https://store.steampowered.com/api/"

    val apiInstance: Retrofit
        get() {
            if (retrofit == null) {

                retrofit = Retrofit.Builder()
                        .baseUrl(BASE_URL)
                        .addConverterFactory(GsonConverterFactory.create())
                        .build()
            }
            return retrofit!!
        }
}

