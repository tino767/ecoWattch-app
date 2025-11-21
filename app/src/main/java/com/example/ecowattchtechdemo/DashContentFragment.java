package com.example.ecowattchtechdemo;

import android.animation.ValueAnimator;
import android.os.Bundle;
import android.util.Log;
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
    
    private static final String TAG = "DashContentFragment";

    private TextView usernameText;
    private TextView currentUsageText;
    private TextView yesterdaysTotalText;
    private TextView potentialEnergyText;
    private TextView leaderboardFirstPlace;
    private TextView leaderboardSecondPlace;
    private TextView leaderboardThirdPlace;
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
        leaderboardFirstPlace = view.findViewById(R.id.leaderboard_first_place);
        leaderboardSecondPlace = view.findViewById(R.id.leaderboard_second_place);
        leaderboardThirdPlace = view.findViewById(R.id.leaderboard_third_place);
        
        Log.d(TAG, "🏆 Leaderboard TextViews initialized: 1st=" + (leaderboardFirstPlace != null) + 
                   ", 2nd=" + (leaderboardSecondPlace != null) + 
                   ", 3rd=" + (leaderboardThirdPlace != null));
        
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
     * Update the rally points display with animated counter
     * @param potentialEnergy String in format "237 Rally Points" or just the number
     */
    public void updatePotentialEnergy(String potentialEnergy) {
        if (potentialEnergyText != null) {
            // Extract just the number if full string is provided
            String numericValue = potentialEnergy.replace(" Rally Points", "").replace(" Potential Energy", "").replace(",", "").trim();
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

    /**
     * Updates the leaderboard display with current rankings
     * @param rankings Array of exactly 3 dorm names in rank order (1st, 2nd, 3rd)
     */
    public void updateLeaderboard(String[] rankings) {
        Log.d(TAG, "🏆 updateLeaderboard called with: " + (rankings != null ? 
            String.format("[%s, %s, %s]", rankings[0], rankings[1], rankings[2]) : "null"));
        
        if (rankings == null || rankings.length < 3) {
            Log.w(TAG, "🏆 Invalid rankings array - skipping update");
            return;
        }

        if (leaderboardFirstPlace != null) {
            String formattedName = toTitleCase(rankings[0]);
            String newText = "1st: " + formattedName;
            leaderboardFirstPlace.setText(newText);
            leaderboardFirstPlace.invalidate();
            Log.d(TAG, "🏆 Updated 1st place to: " + formattedName);
        } else {
            Log.w(TAG, "🏆 leaderboardFirstPlace TextView is null!");
        }
        
        if (leaderboardSecondPlace != null) {
            String formattedName = toTitleCase(rankings[1]);
            String newText = "2nd: " + formattedName;
            leaderboardSecondPlace.setText(newText);
            leaderboardSecondPlace.invalidate();
            Log.d(TAG, "🏆 Updated 2nd place to: " + formattedName);
        } else {
            Log.w(TAG, "🏆 leaderboardSecondPlace TextView is null!");
        }
        
        if (leaderboardThirdPlace != null) {
            String formattedName = toTitleCase(rankings[2]);
            String newText = "3rd: " + formattedName;
            leaderboardThirdPlace.setText(newText);
            leaderboardThirdPlace.invalidate();
            Log.d(TAG, "🏆 Updated 3rd place to: " + formattedName);
        } else {
            Log.w(TAG, "🏆 leaderboardThirdPlace TextView is null!");
        }
        
        // Force the parent view to refresh
        if (getView() != null) {
            getView().invalidate();
        }
    }
    
    /**
     * Convert a string to title case (first letter uppercase, rest lowercase)
     * @param input The input string (e.g., "TINSLEY")
     * @return Title case string (e.g., "Tinsley")
     */
    private String toTitleCase(String input) {
        if (input == null || input.isEmpty()) {
            return input;
        }
        return input.substring(0, 1).toUpperCase() + input.substring(1).toLowerCase();
    }
}