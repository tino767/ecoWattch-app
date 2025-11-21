package com.example.ecowattchtechdemo;

import android.animation.ObjectAnimator;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import java.util.List;

/**
 * RecyclerView adapter for displaying shop items in a horizontal scrolling list
 * Backend can modify the click listener to integrate with purchase/selection logic
 */
public class ShopAdapter extends RecyclerView.Adapter<ShopAdapter.ShopViewHolder> {

    private List<ShopItem> shopItems;
    private OnItemClickListener clickListener;

    public interface OnItemClickListener {
        void onItemClick(ShopItem item, int position);
    }

    public ShopAdapter(List<ShopItem> shopItems, OnItemClickListener clickListener) {
        this.shopItems = shopItems;
        this.clickListener = clickListener;
    }

    @NonNull
    @Override
    public ShopViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_shop, parent, false);
        return new ShopViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ShopViewHolder holder, int position) {
        ShopItem item = shopItems.get(position);
        holder.bind(item, position);

        // Add staggered entrance animation
        holder.itemView.setAlpha(0f);
        holder.itemView.setTranslationY(20f);

        holder.itemView.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(300)
                .setStartDelay(position * 100L) // Stagger by 100ms per item
                .setInterpolator(new AccelerateDecelerateInterpolator())
                .start();
    }

    @Override
    public int getItemCount() {
        return shopItems.size();
    }

    class ShopViewHolder extends RecyclerView.ViewHolder {
        TextView itemName;
        TextView itemPrice;
        View itemColorPreview;
        ImageView itemCheckmark;

        public ShopViewHolder(@NonNull View itemView) {
            super(itemView);
            itemName = itemView.findViewById(R.id.item_name);
            itemPrice = itemView.findViewById(R.id.item_price);
            itemColorPreview = itemView.findViewById(R.id.item_color_preview);
            itemCheckmark = itemView.findViewById(R.id.item_checkmark);
        }

        public void bind(ShopItem item, int position) {
            itemName.setText(item.getName());

            // Show "Owned" for owned items, otherwise show price
            if (item.isOwned()) {
                itemPrice.setText("Owned");
            } else {
                itemPrice.setText(item.getPriceText());
            }

            // Set the color preview background - programmatically create gradient
            if (item.hasGradientColors()) {
                // Create gradient drawable programmatically
                GradientDrawable gradientDrawable = new GradientDrawable(
                    GradientDrawable.Orientation.TL_BR, // Top-left to bottom-right (135 degrees)
                    new int[]{
                        Color.parseColor(item.getGradientStartColor()),
                        Color.parseColor(item.getGradientEndColor())
                    }
                );
                gradientDrawable.setShape(GradientDrawable.OVAL);
                itemColorPreview.setBackground(gradientDrawable);
            } else if (item.getColorResource() != 0) {
                // Fallback to resource-based background for backwards compatibility
                itemColorPreview.setBackgroundResource(item.getColorResource());
            }

            // Show checkmark if item is owned or selected
            if (item.isOwned() || item.isSelected()) {
                itemCheckmark.setVisibility(View.VISIBLE);
            } else {
                itemCheckmark.setVisibility(View.GONE);
            }

            // Set click listener with press animation
            itemView.setOnClickListener(v -> {
                animateClickFeedback(v);
                if (clickListener != null) {
                    clickListener.onItemClick(item, position);
                }
            });
        }

        /**
         * Animates a squeeze effect when item is clicked
         */
        private void animateClickFeedback(View view) {
            view.animate()
                    .scaleX(0.95f)
                    .scaleY(0.95f)
                    .setDuration(100)
                    .withEndAction(() -> {
                        view.animate()
                                .scaleX(1.0f)
                                .scaleY(1.0f)
                                .setDuration(100)
                                .start();
                    })
                    .start();
        }
    }

    // Method to update the list (useful for tab switching)
    public void updateItems(List<ShopItem> newItems) {
        this.shopItems = newItems;
        notifyDataSetChanged();
    }
}
