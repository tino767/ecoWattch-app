package com.example.ecowattchtechdemo;

public class PurchaseResponse {
    private String status;
    private String message;
    private int newPointTotal;

    // Constructors
    public PurchaseResponse() {}

    public PurchaseResponse(String status, String message, int newPointTotal) {
        this.status = status;
        this.message = message;
        this.newPointTotal = newPointTotal;
    }

    // Getters and setters
    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public int getNewPointTotal() {
        return newPointTotal;
    }

    public void setNewPointTotal(int newPointTotal) {
        this.newPointTotal = newPointTotal;
    }
}
