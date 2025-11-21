package com.example.ecowattchtechdemo;

import android.animation.ValueAnimator;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import java.text.DecimalFormat;

public class DashContentFragment extends Fragment {

    private TextView usernameText;
    private TextView currentUsageText;
    private TextView yesterdaysTotalText;
    private TextView potentialEnergyText;
    // theme manager
    private ThemeManager tm;

    // Store previous values for counter animations
    private int previousUsage = 0;
    private int previousPotentialEnergy = 0;
    private int previousYesterdaysTotal = 0;

    private DecimalFormat decimalFormat = new DecimalFormat("#,##0");
    
    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_dash_content, container, false);
    }
    
    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        // Initialize all TextViews after view is created
        initializeViews(view);

        tm = new ThemeManager(requireContext());
        tm.applyTheme();
    }
    
    private void initializeViews(View view) {
        usernameText = view.findViewById(R.id.username_text);
        currentUsageText = view.findViewById(R.id.current_usage_text);
        yesterdaysTotalText = view.findViewById(R.id.yesterdays_total_text);
        potentialEnergyText = view.findViewById(R.id.potential_energy_text);
        
        // Manual refresh features removed - app now updates automatically
        // Users can no longer manually trigger data updates
        
        // Refresh button removed from layout for production
        /*
        // Set up refresh button (if it exists)
        View refreshButton = view.findViewById(R.id.refresh_button);
        if (refreshButton != null) {
            refreshButton.setOnClickListener(v -> {
                if (getActivity() instanceof DashboardActivity) {
                    ((DashboardActivity) getActivity()).manualRefresh();
                }
            });
        }
        */
    }
    
    /**
     * Update the current energy usage display with animated counter
     * @param usage String in format "280kW" or just the number
     */
    public void updateCurrentUsage(String usage) {
        if (currentUsageText != null) {
            // Remove "kW" suffix if present, we display it separately
            String numericValue = usage.replace("kW", "").replace("kw", "").replace(",", "").trim();
            try {
                int newValue = Integer.parseInt(numericValue);
                animateCounter(currentUsageText, previousUsage, newValue, false);
                previousUsage = newValue;
            } catch (NumberFormatException e) {
                // Fallback to direct text update if parsing fails
                currentUsageText.setText(numericValue);
            }
        }
    }

    /**
     * Update the dorm status and leaderboard position
     * @param dormInfo String in format "TINSLEY - 1ST PLACE"
     */
    public void updateDormStatus(String dormInfo) {
        if (usernameText != null) {
            usernameText.setText(dormInfo);
            
            // Set up double-tap gesture to refresh rankings
            usernameText.setOnClickListener(new View.OnClickListener() {
                private long lastClickTime = 0;
                
                @Override
                public void onClick(View v) {
                    long currentTime = System.currentTimeMillis();
                    if (currentTime - lastClickTime < 500) { // Double tap detected (within 500ms)
                        // Refresh leaderboard rankings
                        if (getActivity() instanceof DashboardActivity) {
                            ((DashboardActivity) getActivity()).refreshLeaderboardRankings();
                            // Show feedback to user
                            android.widget.Toast.makeText(getContext(), "🏆 Refreshing leaderboard...", android.widget.Toast.LENGTH_SHORT).show();
                        }
                        lastClickTime = 0; // Reset to prevent triple-tap
                    } else {
                        lastClickTime = currentTime;
                    }
                }
            });
        }
    }
    
    /**
     * Update yesterday's total energy usage with animated counter
     */
    public void updateYesterdaysTotal(String total) {
        if (yesterdaysTotalText != null) {
            String numericValue = total.replace("kWh", "").replace("kW", "").replace(",", "").trim();
            try {
                int newValue = Integer.parseInt(numericValue);
                animateCounter(yesterdaysTotalText, previousYesterdaysTotal, newValue, true);
                previousYesterdaysTotal = newValue;
            } catch (NumberFormatException e) {
                // Fallback to direct text update if parsing fails
                yesterdaysTotalText.setText(total);
            }
        }
    }

    /**
     * Update the potential energy display with animated counter
     * @param potentialEnergy String in format "237 Potential Energy" or just the number
     */
    public void updatePotentialEnergy(String potentialEnergy) {
        if (potentialEnergyText != null) {
            // Extract just the number if full string is provided
            String numericValue = potentialEnergy.replace(" Potential Energy", "").replace(",", "").trim();
            try {
                int newValue = Integer.parseInt(numericValue);
                animateCounter(potentialEnergyText, previousPotentialEnergy, newValue, true);
                previousPotentialEnergy = newValue;
            } catch (NumberFormatException e) {
                // Fallback to direct text update if parsing fails
                potentialEnergyText.setText(numericValue);
            }
        }
    }

    /**
     * Animates a number counter from old value to new value
     * @param textView The TextView to animate
     * @param from Starting value
     * @param to Ending value
     * @param useCommas Whether to format with comma separators
     */
    private void animateCounter(TextView textView, int from, int to, boolean useCommas) {
        if (from == to) {
            // No change, just update text
            textView.setText(useCommas ? decimalFormat.format(to) : String.valueOf(to));
            return;
        }

        ValueAnimator animator = ValueAnimator.ofInt(from, to);
        animator.setDuration(800); // 800ms for smooth counting
        animator.setInterpolator(new AccelerateDecelerateInterpolator());

        animator.addUpdateListener(animation -> {
            int value = (int) animation.getAnimatedValue();
            if (useCommas) {
                textView.setText(decimalFormat.format(value));
            } else {
                textView.setText(String.valueOf(value));
            }
        });

        animator.start();
    }
}