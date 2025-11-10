package com.example.ecowattchtechdemo;

public class PurchaseRequest {
    private String username;
    private String paletteName;
    private int pointsToDeduct;

    public PurchaseRequest(String username, String paletteName, int pointsToDeduct) {
        this.username = username;
        this.paletteName = paletteName;
        this.pointsToDeduct = pointsToDeduct;
    }

    // Getters and setters
    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPaletteName() {
        return paletteName;
    }

    public void setPaletteName(String paletteName) {
        this.paletteName = paletteName;
    }

    public int getPointsToDeduct() {
        return pointsToDeduct;
    }

    public void setPointsToDeduct(int pointsToDeduct) {
        this.pointsToDeduct = pointsToDeduct;
    }
}
