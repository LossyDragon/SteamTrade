package com.aegamesi.steamtrade.steam;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

import java.util.Map;

import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Query;

public interface StoreFront {

    @GET("appdetails")
    Call<Map<Integer, AppDetailsResponse>> getAppDetails(@Query("appids") int appId);

    class AppDetailsResponse {

        @SerializedName("success")
        @Expose
        private Boolean success;
        @SerializedName("data")
        @Expose
        private Data data;

        public Data getData() {
            return data;
        }
    }
    class Data {

        @SerializedName("name")
        @Expose
        private String name;

        public String getName() {
            return name;
        }
    }
}