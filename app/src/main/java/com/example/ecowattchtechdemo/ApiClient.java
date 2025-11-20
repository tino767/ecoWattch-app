package com.example.ecowattchtechdemo;

import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;
import com.example.ecowattchtechdemo.BuildConfig;
public class ApiClient {
    public static final String BASE_URL = "http://" + BuildConfig.SERVER_IP; // No trailing slash
    private static Retrofit retrofit;

    public static Retrofit getClient() {
        if (retrofit == null) {
            retrofit = new Retrofit.Builder()
                    .baseUrl(BASE_URL)
                    .addConverterFactory(GsonConverterFactory.create())
                    .build();
        }
        return retrofit;
    }
}
