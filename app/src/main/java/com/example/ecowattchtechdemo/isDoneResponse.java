package com.example.ecowattchtechdemo;

import com.google.gson.annotations.SerializedName;

public class isDoneResponse {
    private String status;
    @SerializedName("isDone")
    private int isDone;

    public String getStatus() {
        return status;
    }

    public Integer getNumber() { return isDone; }
}
