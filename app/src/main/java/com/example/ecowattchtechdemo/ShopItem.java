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

    // palette colors
    private String colorPrimary;
    private String colorSecondary;
    private String colorAccent;
    private String colorBackgroundPrimary;
    private String colorBackgroundSecondary;

    // Constructor with palette colors (preferred)
    public ShopItem(String name, int price,
                    String colorPrimary, String colorSecondary, String colorAccent,
                    String colorBackgroundPrimary, String colorBackgroundSecondary) {
        this.name = name;
        this.price = price;
        this.isOwned = false;
        this.isSelected = false;
        this.colorPrimary = colorPrimary;
        this.colorSecondary = colorSecondary;
        this.colorAccent = colorAccent;
        this.colorBackgroundPrimary = colorBackgroundPrimary;
        this.colorBackgroundSecondary = colorBackgroundSecondary;
        this.gradientStartColor = colorPrimary;
        this.gradientEndColor = colorAccent;
    }

    // Constructor with palette colors as a list
    public ShopItem(String name, int Price, String[] colors) {
        this.name = name;
        this.price = price;
        this.isOwned = false;
        this.isSelected = false;
        this.colorPrimary = colors[0];
        this.colorSecondary = colors[1];
        this.colorAccent = colors[2];
        this.colorBackgroundPrimary = colors[3];
        this.colorBackgroundSecondary = colors[4];
        this.gradientStartColor = colors[0];
        this.gradientEndColor = colors[2];
    }

    // Constructor with palette colors and booleans
    public ShopItem(String name, int price,
                    String colorPrimary, String colorSecondary, String colorAccent,
                    String colorBackgroundPrimary, String colorBackgroundSecondary,
                    boolean isOwned, boolean isSelected) {
        this.name = name;
        this.price = price;
        this.isOwned = isOwned;
        this.isSelected = isSelected;
        this.colorPrimary = colorPrimary;
        this.colorSecondary = colorSecondary;
        this.colorAccent = colorAccent;
        this.colorBackgroundPrimary = colorBackgroundPrimary;
        this.colorBackgroundSecondary = colorBackgroundSecondary;
        this.gradientStartColor = colorPrimary;
        this.gradientEndColor = colorAccent;
    }

    // Constructor with gradient colors for backwards compatibility
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

    public String[] getColors() {
        return new String[]{colorPrimary, colorSecondary, colorAccent, colorBackgroundPrimary, colorBackgroundSecondary};
    }
}
