package com.example.ecowattchtechdemo;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.POST;
import retrofit2.http.GET;
import retrofit2.http.Path;
import com.example.ecowattchtechdemo.willow.models.EnergyDataResponse;
import com.example.ecowattchtechdemo.ApiResponse;

public interface ApiService {
    @POST("/login")
    Call<LoginResponse> login(@Body LoginRequest request);
    
    // Willow API integration endpoints (for future use)
    @GET("/energy/{buildingId}")
    Call<EnergyDataResponse> getEnergyData(@Path("buildingId") String buildingId);

    @GET("/palettes")
    Call<ApiResponse> getPalettes();

    @POST("/update_user_points")
    Call<ApiResponse> updateUserPoints(@Body UpdatePointsRequest request);

    @POST("/purchase_palette")
    Call<PurchaseResponse> purchasePalette(@Body PurchaseRequest request);
}
