package com.example.ecowattchtechdemo;

public class UpdatePointsRequest {
    private String username;
    private int spendablePoints;

    public UpdatePointsRequest(String username, int spendablePoints) {
        this.username = username;
        this.spendablePoints = spendablePoints;
    }

    // Getters and setters
    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public int getSpendablePoints() {
        return spendablePoints;
    }

    public void setSpendablePoints(int spendablePoints) {
        this.spendablePoints = spendablePoints;
    }
}
