package com.example.ecowattchtechdemo;

/**
 * Model class representing a shop item (palette, icon, etc.)
 * Backend can extend this with additional fields as needed
 */
public class ShopItem {
    private String name;
    private int price;
    private int colorResource; // Deprecated - kept for backwards compatibility
    private String gradientStartColor;
    private String gradientEndColor;
    private boolean isOwned;
    private boolean isSelected;

    // Constructor with gradient colors (preferred)
    public ShopItem(String name, int price, String gradientStartColor, String gradientEndColor) {
        this.name = name;
        this.price = price;
        this.gradientStartColor = gradientStartColor;
        this.gradientEndColor = gradientEndColor;
        this.isOwned = false;
        this.isSelected = false;
    }

    // Legacy constructor for backwards compatibility
    public ShopItem(String name, int price, int colorResource) {
        this.name = name;
        this.price = price;
        this.colorResource = colorResource;
        this.isOwned = false;
        this.isSelected = false;
    }

    public String getName() {
        return name;
    }

    public int getPrice() {
        return price;
    }

    public String getPriceText() {
        return price + " Energy";
    }

    public int getColorResource() {
        return colorResource;
    }

    public String getGradientStartColor() {
        return gradientStartColor;
    }

    public String getGradientEndColor() {
        return gradientEndColor;
    }

    public boolean hasGradientColors() {
        return gradientStartColor != null && gradientEndColor != null;
    }

    public boolean isOwned() {
        return isOwned;
    }

    public void setOwned(boolean owned) {
        isOwned = owned;
    }

    public boolean isSelected() {
        return isSelected;
    }

    public void setSelected(boolean selected) {
        isSelected = selected;
    }
}
