package com.example.ecowattchtechdemo;

import com.example.ecowattchtechdemo.willow.models.Palette;
import com.google.gson.annotations.SerializedName;
import java.util.List;
public class ApiResponse {
    @SerializedName("status")
    public String status;

    @SerializedName("palettes")
    public List<Palette> palettes;
}
