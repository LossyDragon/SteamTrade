package com.aegamesi.steamtrade.steam

import com.google.gson.annotations.SerializedName

import retrofit2.Call
import retrofit2.http.GET
import retrofit2.http.Query

interface StoreFront {

    @GET("appdetails")
    fun getAppDetails(@Query("appids") appId: Int): Call<Map<Int, AppDetailsResponse>>

    class AppDetailsResponse {
        @SerializedName("success")
        private val success: Boolean? = null
        @SerializedName("data")
        val data: Data? = null
    }

    class Data {
        @SerializedName("name")
        val name: String? = null
    }
}