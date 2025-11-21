package com.example.ecowattchtechdemo;

import com.google.gson.annotations.SerializedName;
public class User {
    @SerializedName("Username")
    private String Username;

    @SerializedName("DormName")
    private String DormName;

    @SerializedName("SpendablePoints")
    private Integer SpendablePoints;

    @SerializedName("Email")
    private String Email;

    @SerializedName("PasswordHash")
    private String PasswordHash;

    @SerializedName("Token")
    private Integer Token;

    @SerializedName("isDone")
    private Integer isDone;

    @SerializedName("EquppiedItem")
    private String EquppiedItem;

    public String getUsername() {
        return Username;
    }

    public String getDormName() {
        return DormName;
    }

    public Integer getIsDone() { return isDone; }

    public Integer getSpendablePoints() {
        return SpendablePoints != null ? SpendablePoints : 0;
    }
}
