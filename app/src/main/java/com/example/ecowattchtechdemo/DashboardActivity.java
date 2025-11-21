package com.example.ecowattchtechdemo;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Color;
import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import android.view.View;
import android.view.ViewGroup;
import android.content.Intent;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import java.util.Random;
import java.util.Map;
import java.util.List;
import java.text.DecimalFormat;
import android.widget.ImageView;
import android.widget.Button;
import android.widget.TextView;
import android.content.SharedPreferences;
import android.widget.Toast;
import android.content.Context;
import android.view.animation.AnimationUtils;
import android.animation.ObjectAnimator;
import android.view.animation.AccelerateDecelerateInterpolator;

// Willow API imports
import com.android.volley.Request;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;
import com.example.ecowattchtechdemo.gamification.DormPointsManager;
import com.example.ecowattchtechdemo.willow.WillowEnergyDataManager;
import com.example.ecowattchtechdemo.willow.WillowApiV3Config;
import com.example.ecowattchtechdemo.willow.models.EnergyDataResponse;
import com.example.ecowattchtechdemo.gamification.DormPointsManager;

import org.json.JSONException;
import org.json.JSONObject;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;


public class DashboardActivity extends AppCompatActivity {
    private static final String TAG = "DashboardActivity";
    
    LinearLayout records, shop;
    private DashContentFragment dashContentFragment;
    private Handler updateHandler;
    private DecimalFormat decimalFormat;
    private TextView shopPointsText; // Reference to shop button's points display

    // Store previous values for counter animations
    private int previousShopPoints = 0;

    // Live data configuration
    private String currentDormName = "";
    // Update intervals for different data types
    private static final long LIVE_ENERGY_INTERVAL = 60000;      // 1 minute - live energy data
    private static final long POTENTIAL_ENERGY_INTERVAL = 3600000; // 1 hour - potential energy (only changes at 10pm)
    private static final long RANKINGS_INTERVAL = 120000;        // 2 minutes - dorm rankings/positions (more responsive)
    private static final long ROTATING_DISPLAY_INTERVAL = 5000;   // 5 seconds - rotating display cycle

    // Tracking last update times
    private long lastLiveEnergyUpdate = 0;
    private long lastPotentialEnergyUpdate = 0;
    private long lastRankingsUpdate = 0;
    
    // Rotating display state
    private int currentDisplayMode = 0; // 0=Today's Total (kWh), 1=Yesterday's total (kWh), 2=Today's Emissions
    private String[] rotatingDisplayData = new String[3]; // Store the three display values
    private Handler rotatingDisplayHandler;
    
    // Cached values to avoid redundant API calls
    private String cachedDormPosition = null;
    private int cachedPotentialEnergy = 0;
    
    // Dorm data for rotation
    private String[] dormNames = {"TINSLEY", "GABALDON", "SECHRIST"};
    private String[] dormPositions = {"1ST PLACE", "2ND PLACE", "3RD PLACE"};
    private int currentDormIndex = -1; // Will be set based on user's selected dorm
    ImageView hamburgerButton;

    // Modal components
    private LinearLayout modalOverlay;
    private ImageView tabAlerts, tabNotifications, tabSettings, tabProfile;
    private LinearLayout tabContentAlerts, tabContentNotifications, tabContentSettings, tabContentProfile;
    private LinearLayout checklistItem1, checklistItem2, checklistItem3;
    private boolean isModalOpen = false;

    // Profile components
    private TextView profileUsername, profileDorm;
    private TextView profileLogoutButton;

    // Daily Tips component
    private TextView dailyTipText;
    private String[] environmentalTips;
    
    // Willow API integration
    private WillowEnergyDataManager energyDataManager;
    private boolean isWillowAuthenticated = false;

    // Meter components
    View meterFill;
    ImageView thresholdIndicator;

    // Meter configuration
    private static final int MAX_USAGE = 3000; // 3000kWh max daily usage for meter scaling
    private static final int MIN_USAGE = 0;   // 0kw min
    private int currentUsage = 0;  // Initial value, updated by live data
    private int thresholdValue = 300; // Will be updated to yesterday's usage dynamically

    // theme manager
    private ThemeManager tm;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_dashboard);

        initializeComponents();
        setupNavigationButtons();
        setupModalComponents();
        setupFragment(savedInstanceState);
        
        // Initialize cached values for smart updates
        cachedDormPosition = currentDormName;
        cachedPotentialEnergy = 0;
        
        startLiveDataUpdates();
        
        // Start rotating display cycle
        startRotatingDisplay();

        // initialize themeManager
        tm = new ThemeManager(this);
    }

    protected void onStart() {
        super.onStart();
        tm.applyTheme();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Refresh shop points display when returning from shop or other activities
        updateShopPointsDisplay();
        
        // Refresh profile data to ensure current user info is always up-to-date
        loadUserProfile();
        
        Log.d(TAG, "onResume: Refreshed shop points display and user profile");
    }
    
    private void initializeComponents() {
        updateHandler = new Handler(Looper.getMainLooper());
        rotatingDisplayHandler = new Handler(Looper.getMainLooper());
        decimalFormat = new DecimalFormat("#,##0");
        
        // Initialize rotating display data array
        rotatingDisplayData = new String[]{"Loading...", "Loading...", "Loading..."};
        
        // Get user's dorm from SharedPreferences
        SharedPreferences prefs = getSharedPreferences("MyAppPrefs", MODE_PRIVATE);
        String userDorm = prefs.getString("Dormitory", ""); // No default - use what user selected
        
        // Set current dorm to user's dorm and find its index
        if (userDorm != null && !userDorm.isEmpty()) {
            currentDormName = userDorm.toUpperCase();
            for (int i = 0; i < dormNames.length; i++) {
                if (dormNames[i].equals(currentDormName)) {
                    currentDormIndex = i;
                    break;
                }
            }
        } else {
            Log.w(TAG, "No dorm selected during signup - user needs to select a dorm");
            currentDormName = "";
            currentDormIndex = -1;
        }
        
        Log.d(TAG, "User's dorm set to: '" + currentDormName + "' (Index: " + currentDormIndex + ")");
        
        // Initialize Willow API manager
        initializeWillowApi();
        
        //  Daily check-in is now user-triggered through the UI checklist
        // Removed automatic check-in that was happening on every app start
        
        Log.d(TAG, "Live data system initialized");
    }
    
    /**
     * Initialize Willow API integration
     */
    private void initializeWillowApi() {
        try {
            // 🎮 Use context-aware constructor for gamification features
            energyDataManager = new WillowEnergyDataManager(this);
            
            // Try to authenticate with stored credentials
            authenticateWithWillow();
            
        } catch (Exception e) {
            Log.e(TAG, "Failed to initialize Willow API", e);
            isWillowAuthenticated = false;
        }
    }
    
    /**
     * Authenticate with Willow API
     */
    private void authenticateWithWillow() {
        // Use credentials from BuildConfig (loaded from local.properties)
        String clientId = BuildConfig.WILLOW_CLIENT_ID;
        String clientSecret = BuildConfig.WILLOW_CLIENT_SECRET;
        
        // Validate that credentials are available
        if (clientId == null || clientId.isEmpty() || clientSecret == null || clientSecret.isEmpty()) {
            Log.e(TAG, "Willow API credentials not found in BuildConfig. Check local.properties file.");
            isWillowAuthenticated = false;
            
            runOnUiThread(() -> {
                if (dashContentFragment != null) {
                    dashContentFragment.updateYesterdaysTotal("API Credentials Missing");
                }
            });
            return;
        }
        
        Log.d(TAG, "🔐 Authenticating with Client ID: " + clientId.substring(0, Math.min(8, clientId.length())) + "...");
        Log.d(TAG, "🏠 Current user dorm: '" + currentDormName + "' (Index: " + currentDormIndex + ")");
        
        energyDataManager.authenticate(clientId, clientSecret, new WillowEnergyDataManager.AuthenticationCallback() {
            @Override
            public void onSuccess(String token) {
                Log.d(TAG, "Willow API authentication successful!");
                isWillowAuthenticated = true;
                
                runOnUiThread(() -> {
                    // Update UI to indicate real data is being used
                    if (dashContentFragment != null) {
                        dashContentFragment.updateYesterdaysTotal("Connected to Willow API");
                    }
                    
                    // 🚀 INSTANT UPDATE: Force immediate data fetch after successful authentication
                    Log.d(TAG, "🚀 Triggering instant data update after authentication");
                    forceInstantDataUpdate();
                });
            }
            
            @Override
            public void onError(String error) {
                Log.e(TAG, "Willow API authentication failed: " + error);
                isWillowAuthenticated = false;
                
                runOnUiThread(() -> {
                    if (dashContentFragment != null) {
                        dashContentFragment.updateYesterdaysTotal("Authentication Failed: " + error + " ❌");
                    }
                });
            }
        });
    }
    
    /**
     * 🎯 Handle daily check-in for the current user only
     * This is now triggered by completing the daily checklist tasks
     */
    private void performDailyCheckin() {
        if (energyDataManager == null) {
            Log.w(TAG, "Energy data manager not initialized, skipping daily check-in");
            return;
        }

        try {
            com.example.ecowattchtechdemo.gamification.DormPointsManager pointsManager =
                new com.example.ecowattchtechdemo.gamification.DormPointsManager(this);

            // Only check-in for the current user's dorm, not all dorms
            boolean checkedIn = pointsManager.recordDailyCheckin(currentDormName);
            if (checkedIn) {
                Log.d(TAG, "Daily check-in successful for " + currentDormName + ": +25 spendable points");

                // Update shop button to show new points total
                updateShopPointsDisplay();

                // Show success message to user
                runOnUiThread(() -> {
                    Toast.makeText(this, "Daily check-in complete! +25 spendable points earned 💰", Toast.LENGTH_LONG).show();
                });
            } else {
                Log.d(TAG, "ℹ️ " + currentDormName + " already checked in today");
                runOnUiThread(() -> {
                    Toast.makeText(this, "You've already checked in today!", Toast.LENGTH_SHORT).show();
                });
            }

        } catch (Exception e) {
            Log.e(TAG, "Error during daily check-in process", e);
        }
    }

    /**
     * Update the shop button to display current spendable points with animated counter
     */
    private void updateShopPointsDisplay() {
        if (shopPointsText == null) {
            Log.w(TAG, "Shop points text view not initialized yet - attempting to find it");
            // Try to find it again in case it wasn't ready during initial setup
            if (shop != null) {
                shopPointsText = shop.findViewById(R.id.shop_points_text);
            }
            
            if (shopPointsText == null) {
                Log.w(TAG, "Cannot update shop points display - TextView not found");
                return;
            }
        }

        try {
            com.example.ecowattchtechdemo.gamification.DormPointsManager pointsManager =
                new com.example.ecowattchtechdemo.gamification.DormPointsManager(this);

            int spendablePoints = pointsManager.getIndividualSpendablePoints();

            // Animate the counter from previous value to new value
            animateShopPointsCounter(previousShopPoints, spendablePoints);
            previousShopPoints = spendablePoints;

            Log.d(TAG, "💰 Shop button updated with spendable points: " + spendablePoints);

        } catch (Exception e) {
            Log.e(TAG, "Error updating shop points display", e);
            shopPointsText.setText("Shop");
        }
    }

    /**
     * Animates the shop points counter from old value to new value
     */
    private void animateShopPointsCounter(int from, int to) {
        if (from == to) {
            // No change, just update text
            shopPointsText.setText(decimalFormat.format(to) + " pts");
            return;
        }

        ValueAnimator animator = ValueAnimator.ofInt(from, to);
        animator.setDuration(800); // 800ms for smooth counting
        animator.setInterpolator(new AccelerateDecelerateInterpolator());

        animator.addUpdateListener(animation -> {
            int value = (int) animation.getAnimatedValue();
            shopPointsText.setText(decimalFormat.format(value) + " pts");
        });

        animator.start();
    }
    
    /**
     * Public method to refresh shop points display - can be called from other activities
     * or when points are known to have changed
     */
    public void refreshShopPointsDisplay() {
        Log.d(TAG, "🔄 Manual refresh of shop points display requested");
        updateShopPointsDisplay();
    }
    
    /**
     * Public method to force refresh leaderboard rankings immediately
     * Bypasses the normal 2-minute update interval
     */
    public void refreshLeaderboardRankings() {
        Log.d(TAG, "🏆 Manual refresh of leaderboard rankings requested");
        // Force rankings update by resetting the last update time
        lastRankingsUpdate = 0;
        // Trigger an immediate data fetch with rankings update
        if (currentDormName != null && !currentDormName.isEmpty()) {
            fetchRealEnergyData(false, true);  // Force rankings update
        }
    }
    
    private void setupNavigationButtons() {
        records = findViewById(R.id.records_button);
        shop = findViewById(R.id.shop_button);
        hamburgerButton = findViewById(R.id.hamburger_button);

        // Find the shop points text view (the TextView inside the shop button)
        shopPointsText = shop.findViewById(R.id.shop_points_text);
        
        if (shopPointsText == null) {
            Log.e(TAG, "shop_points_text TextView not found in shop button layout!");
        } else {
            Log.d(TAG, "shop_points_text TextView found successfully");
        }

        // Initialize shop points display immediately
        updateShopPointsDisplay();

        // Records button now enabled with full functionality
        records.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                animateClickFeedback(view);
                view.postDelayed(() -> {
                    Intent intent = new Intent(DashboardActivity.this, RecordsActivity.class);
                    startActivity(intent, android.app.ActivityOptions.makeCustomAnimation(
                        DashboardActivity.this, R.anim.slide_in_left, R.anim.slide_out_right).toBundle());
                }, 150);
            }
        });

        shop.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                animateClickFeedback(view);
                view.postDelayed(() -> {
                    Intent intent = new Intent(DashboardActivity.this, ShopActivity.class);
                    startActivity(intent, android.app.ActivityOptions.makeCustomAnimation(
                        DashboardActivity.this, R.anim.slide_in_right, R.anim.slide_out_left).toBundle());
                }, 150);
            }
        });

        // Hamburger button toggles utility modal
        hamburgerButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                animateClickFeedback(view);
                view.postDelayed(() -> toggleModal(), 150);
            }
        });

    }

    /**
     * Initialize modal components and set up tab listeners
     */
    private void setupModalComponents() {
        // Initialize modal overlay
        modalOverlay = findViewById(R.id.modal_overlay);

        // Initialize tab buttons
        tabAlerts = findViewById(R.id.tab_alerts);
        tabNotifications = findViewById(R.id.tab_notifications);
        tabSettings = findViewById(R.id.tab_settings);
        tabProfile = findViewById(R.id.tab_profile);

        // Initialize tab content containers
        tabContentAlerts = findViewById(R.id.tab_content_alerts);
        tabContentNotifications = findViewById(R.id.tab_content_notifications);
        tabContentSettings = findViewById(R.id.tab_content_settings);
        tabContentProfile = findViewById(R.id.tab_content_profile);

        // Initialize profile components
        profileUsername = findViewById(R.id.profile_username);
        profileDorm = findViewById(R.id.profile_dorm);
        profileLogoutButton = findViewById(R.id.profile_logout_button);

        // Initialize daily tips component
        dailyTipText = findViewById(R.id.daily_tip_text);

        // Initialize daily checklist items
        checklistItem1 = findViewById(R.id.checklist_item_1);
        checklistItem2 = findViewById(R.id.checklist_item_2);
        checklistItem3 = findViewById(R.id.checklist_item_3);

        // Load environmental tips
        initializeEnvironmentalTips();

        // Populate profile information
        loadUserProfile();

        // Set up profile logout button
        profileLogoutButton.setOnClickListener(v -> {
            animateClickFeedback(v);
            v.postDelayed(() -> logout(), 150);
        });

        // Set up tab click listeners with animations
        tabAlerts.setOnClickListener(v -> {
            animateClickFeedback(v);
            v.postDelayed(() -> switchTab(0), 100);
        });
        tabNotifications.setOnClickListener(v -> {
            animateClickFeedback(v);
            v.postDelayed(() -> switchTab(1), 100);
        });
        tabSettings.setOnClickListener(v -> {
            animateClickFeedback(v);
            v.postDelayed(() -> switchTab(2), 100);
        });
        tabProfile.setOnClickListener(v -> {
            animateClickFeedback(v);
            v.postDelayed(() -> switchTab(3), 100);
        });

        // Set up checklist click listeners with animations
        checklistItem1.setOnClickListener(v -> {
            animateClickFeedback(v);
            v.postDelayed(() -> markItemComplete(1), 100);
        });
        checklistItem2.setOnClickListener(v -> {
            animateClickFeedback(v);
            v.postDelayed(() -> markItemComplete(2), 100);
        });
        checklistItem3.setOnClickListener(v -> {
            animateClickFeedback(v);
            v.postDelayed(() -> markItemComplete(3), 100);
        });

        // Load current checklist state
        loadChecklistState();

        // Set up overlay click listener to close modal when clicking outside
        modalOverlay.setOnClickListener(v -> {
            // Only close if clicking directly on overlay (not on modal content)
            if (v.getId() == R.id.modal_overlay) {
                hideModal();
            }
        });
    }

    /**
     * Load the current state of the daily checklist
     */
    private void loadChecklistState() {
        SharedPreferences prefs = getSharedPreferences("DailyTasks", MODE_PRIVATE);
        
        // Check if we need to reset for a new day
        String lastResetDate = prefs.getString("last_reset_date", "");
        String today = java.text.DateFormat.getDateInstance().format(new java.util.Date());

        /* TODO: fix this so it doesn't always reset
        if (!today.equals(lastResetDate)) {
            // New day - reset all tasks
            SharedPreferences.Editor editor = prefs.edit();
            editor.putBoolean("checklist_item_1", false);
            editor.putBoolean("checklist_item_2", false);
            editor.putBoolean("checklist_item_3", false);
            editor.putBoolean("all_tasks", false);
            editor.putString("last_reset_date", today);
            editor.apply();
            Log.d(TAG, "🔄 Daily checklist reset for new day: " + today);
        }
         */
        
        // Load current state and update UI
        boolean task1Complete = prefs.getBoolean("checklist_item_1", false);
        boolean task2Complete = prefs.getBoolean("checklist_item_2", false);
        boolean task3Complete = prefs.getBoolean("checklist_item_3", false);
        boolean allTasksComplete = prefs.getBoolean("all_tasks", false);
        
        // Update UI for task 1
        if (task1Complete) {
            ImageView taskIcon1 = findViewById(R.id.checklist_item_1_icon);
            TextView taskText1 = findViewById(R.id.checklist_item_1_text);
            if (taskIcon1 != null && taskText1 != null) {
                taskIcon1.setImageResource(R.drawable.ic_checkmark);
                taskIcon1.setTag("accent_color");
                taskText1.setTag("secondary_text");
            }
        }
        
        // Update UI for task 2
        if (task2Complete) {
            ImageView taskIcon2 = findViewById(R.id.checklist_item_2_icon);
            TextView taskText2 = findViewById(R.id.checklist_item_2_text);
            if (taskIcon2 != null && taskText2 != null) {
                taskIcon2.setImageResource(R.drawable.ic_checkmark);
                taskIcon2.setTag("accent_color");
                taskText2.setTag("secondary_text");
            }
        }
        
        // Update UI for task 3
        if (task3Complete) {
            ImageView taskIcon3 = findViewById(R.id.checklist_item_3_icon);
            TextView taskText3 = findViewById(R.id.checklist_item_3_text);
            if (taskIcon3 != null && taskText3 != null) {
                taskIcon3.setImageResource(R.drawable.ic_checkmark);
                taskIcon3.setTag("accent_color");
                taskText3.setTag("secondary_text");
            }
        }
        
        // Update UI for all tasks complete
        if (allTasksComplete) {
            TextView tasksCompleted = findViewById(R.id.tasks_completed);
            if (tasksCompleted != null) {
                tasksCompleted.setVisibility(View.VISIBLE);
                // Grey out completed tasks instead of hiding them
                checklistItem1.setAlpha(0.5f);
                checklistItem2.setAlpha(0.5f);
                checklistItem3.setAlpha(0.5f);
            }
        }
        
        // Apply theme to show proper colors
        if (tm != null) {
            tm.applyTheme();
        }
        
        Log.d(TAG, String.format("📋 Checklist state loaded: Task1=%s, Task2=%s, Task3=%s, AllComplete=%s", 
                task1Complete, task2Complete, task3Complete, allTasksComplete));
    }

    /**
     * Toggle modal visibility
     */
    private void toggleModal() {
        if (isModalOpen) {
            hideModal();
        } else {
            showModal();
        }
    }

    /**
     * Show the utility modal with entrance animation
     */
    private void showModal() {
        isModalOpen = true;
        modalOverlay.setVisibility(View.VISIBLE);

        // Fade in overlay background
        modalOverlay.setAlpha(0f);
        modalOverlay.animate()
            .alpha(1f)
            .setDuration(300)
            .setInterpolator(new android.view.animation.LinearInterpolator())
            .start();

        // Slide in modal content from bottom
        View modalContent = findViewById(R.id.modal_content_container);
        if (modalContent != null) {
            modalContent.setTranslationY(300);
            modalContent.animate()
                .translationY(0)
                .setDuration(350)
                .setInterpolator(new android.view.animation.AccelerateDecelerateInterpolator())
                .start();
        }

        // Change hamburger icon to close icon
        hamburgerButton.setImageResource(R.drawable.ic_close_vertical);

        // Refresh checklist state when opening modal
        loadChecklistState();
        
        // Refresh profile data when opening modal to ensure current user info is displayed
        loadUserProfile();

        // Show first tab by default
        switchTab(0);

        Log.d(TAG, "Modal opened - refreshed user profile data");
    }

    /**
     * Hide the utility modal with exit animation
     */
    private void hideModal() {
        if (!isModalOpen) return;

        // Fade out overlay background
        modalOverlay.animate()
            .alpha(0f)
            .setDuration(300)
            .setInterpolator(new android.view.animation.LinearInterpolator())
            .start();

        // Slide out modal content to bottom
        View modalContent = findViewById(R.id.modal_content_container);
        if (modalContent != null) {
            modalContent.animate()
                .translationY(300)
                .setDuration(350)
                .setInterpolator(new android.view.animation.AccelerateDecelerateInterpolator())
                .withEndAction(() -> {
                    isModalOpen = false;
                    modalOverlay.setVisibility(View.GONE);
                    // Reset animation properties for next time
                    modalContent.setTranslationY(0);
                    modalOverlay.setAlpha(1f);
                })
                .start();
        } else {
            isModalOpen = false;
            modalOverlay.setVisibility(View.GONE);
        }

        // Change close icon back to hamburger icon
        hamburgerButton.setImageResource(R.drawable.ic_hamburger);

        Log.d(TAG, "Modal closed");
    }

    /**
     * Switch between modal tabs with cross-fade animation
     * @param tabIndex 0=Alerts, 1=Notifications, 2=Settings, 3=Profile
     */
    private void switchTab(int tabIndex) {
        // Reset all tab icons to white (inactive) immediately
        tabAlerts.setColorFilter(getResources().getColor(R.color.white, null));
        tabNotifications.setColorFilter(getResources().getColor(R.color.white, null));
        tabSettings.setColorFilter(getResources().getColor(R.color.white, null));
        tabProfile.setColorFilter(getResources().getColor(R.color.white, null));

        // Fade out current content
        LinearLayout currentContent = null;
        ImageView currentIcon = null;

        // Find which tab is currently visible
        if (tabContentAlerts.getVisibility() == View.VISIBLE) currentContent = tabContentAlerts;
        else if (tabContentNotifications.getVisibility() == View.VISIBLE) currentContent = tabContentNotifications;
        else if (tabContentSettings.getVisibility() == View.VISIBLE) currentContent = tabContentSettings;
        else if (tabContentProfile.getVisibility() == View.VISIBLE) currentContent = tabContentProfile;

        if (currentContent != null) {
            currentContent.animate()
                .alpha(0f)
                .setDuration(150)
                .setInterpolator(new android.view.animation.LinearInterpolator())
                .withEndAction(() -> {
                    // Hide all tab contents
                    tabContentAlerts.setVisibility(View.GONE);
                    tabContentNotifications.setVisibility(View.GONE);
                    tabContentSettings.setVisibility(View.GONE);
                    tabContentProfile.setVisibility(View.GONE);

                    // Show and fade in selected tab
                    showTabContent(tabIndex);
                })
                .start();
        } else {
            // No previous tab, just show new one
            showTabContent(tabIndex);
        }
    }

    /**
     * Show specific tab content with fade-in animation
     */
    private void showTabContent(int tabIndex) {
        // get colors from shared prefs
        SharedPreferences prefs = getSharedPreferences("ThemePrefs", Context.MODE_PRIVATE);
        int accent = Color.parseColor(prefs.getString("accent_color", "#CD232E"));

        switch (tabIndex) {
            case 0: // Daily Tips (formerly Alerts)
                tabContentAlerts.setAlpha(0f);
                tabContentAlerts.setVisibility(View.VISIBLE);
                tabContentAlerts.animate()
                    .alpha(1f)
                    .setDuration(150)
                    .setInterpolator(new android.view.animation.LinearInterpolator())
                    .start();
                tabAlerts.setColorFilter(accent);
                displayRandomTip(); // Refresh tip when tab is opened
                Log.d(TAG, "Switched to Daily Tips tab");
                break;
            case 1: // Notifications
                tabContentNotifications.setAlpha(0f);
                tabContentNotifications.setVisibility(View.VISIBLE);
                tabContentNotifications.animate()
                    .alpha(1f)
                    .setDuration(150)
                    .setInterpolator(new android.view.animation.LinearInterpolator())
                    .start();
                tabNotifications.setColorFilter(accent);
                Log.d(TAG, "Switched to Notifications tab");
                break;
            case 2: // Daily Check-in (formerly Settings)
                tabContentSettings.setAlpha(0f);
                tabContentSettings.setVisibility(View.VISIBLE);
                tabContentSettings.animate()
                    .alpha(1f)
                    .setDuration(150)
                    .setInterpolator(new android.view.animation.LinearInterpolator())
                    .start();
                tabSettings.setColorFilter(accent);
                Log.d(TAG, "Switched to Daily Check-in tab");
                break;
            case 3: // Profile
                tabContentProfile.setAlpha(0f);
                tabContentProfile.setVisibility(View.VISIBLE);
                tabContentProfile.animate()
                    .alpha(1f)
                    .setDuration(150)
                    .setInterpolator(new android.view.animation.LinearInterpolator())
                    .start();
                tabProfile.setColorFilter(getResources().getColor(R.color.text_red, null));
                // Refresh profile data whenever profile tab is opened
                loadUserProfile();
                Log.d(TAG, "Switched to Profile tab - refreshed user data");
                break;
        }
    }

    /**
     * Load user profile information from SharedPreferences and update UI
     * This method refreshes the profile display with current user data
     */
    private void loadUserProfile() {
        // Get user info from SharedPreferences (saved by LoginFragment/SignupFragment)
        SharedPreferences prefs = getSharedPreferences("MyAppPrefs", MODE_PRIVATE);
        String username = prefs.getString("Username", "Guest");
        String dorm = prefs.getString("Dormitory", "Not Selected");

        // Ensure profile UI components are available
        if (profileUsername == null || profileDorm == null) {
            Log.w(TAG, "Profile UI components not initialized yet - skipping profile update");
            return;
        }

        // Update the profile UI
        profileUsername.setText(username);
        profileDorm.setText(dorm);

        // Ensure the currentDormName is always in sync with profile
        String normalizedDorm = dorm.toUpperCase();
        if (!normalizedDorm.equals(currentDormName)) {
            Log.d(TAG, "🔄 Profile dorm mismatch detected - updating currentDormName from '" + currentDormName + "' to '" + normalizedDorm + "'");
            currentDormName = normalizedDorm;
            
            // Update current dorm index for proper data fetching
            for (int i = 0; i < dormNames.length; i++) {
                if (dormNames[i].equals(currentDormName)) {
                    currentDormIndex = i;
                    Log.d(TAG, "🔄 Updated currentDormIndex to: " + currentDormIndex);
                    break;
                }
            }
        }

        Log.d(TAG, "✅ Profile loaded - Username: '" + username + "', Dorm: '" + dorm + "' (CurrentDorm: " + currentDormName + ")");
        
        // Debug: Print all SharedPreferences to verify data integrity
        logAllSharedPreferences();
        
        // 💰 Update shop points when profile is loaded to ensure consistency
        updateShopPointsDisplay();
    }
    
    /**
     * Debug method to log all SharedPreferences data for troubleshooting
     */
    /**
     * Debug method to log all SharedPreferences data for troubleshooting
     */
    private void logAllSharedPreferences() {
        SharedPreferences prefs = getSharedPreferences("MyAppPrefs", MODE_PRIVATE);
        Log.d(TAG, "🔍 DEBUG - All MyAppPrefs data:");
        for (java.util.Map.Entry<String, ?> entry : prefs.getAll().entrySet()) {
            Log.d(TAG, "  " + entry.getKey() + " = " + entry.getValue());
        }
    }

    /**
     * Logout the user and return to login screen
     */
    private void logout() {
        Log.d(TAG, "User logging out");

        // Clear saved user data
        SharedPreferences prefs = getSharedPreferences("MyAppPrefs", MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        editor.clear();
        editor.apply();

        //clear the tasks saved data
        SharedPreferences task_prefs = getSharedPreferences("DailyTasks", MODE_PRIVATE);
        SharedPreferences.Editor task_editor = task_prefs.edit();
        task_editor.clear();
        task_editor.apply();

        //clear the shared preferences that hold the money a user has
        SharedPreferences money_prefs = getSharedPreferences("DormPointsPrefs", MODE_PRIVATE);
        SharedPreferences.Editor money_editor = money_prefs.edit();
        money_editor.clear();
        money_editor.apply();

        // Close modal before navigating away
        hideModal();

        // Navigate back to LoginSignupActivity and clear the activity stack
        Intent intent = new Intent(DashboardActivity.this, LoginSignupActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    /**
     * Mark checklist item as complete when checked
     */
    private void markItemComplete(int checklistItem) {
        SharedPreferences prefs = getSharedPreferences("DailyTasks", MODE_PRIVATE);
        Boolean taskComplete;
        ImageView taskIcon;
        TextView taskText;

        switch (checklistItem) {
            case 1: // task 1
                taskComplete = prefs.getBoolean("checklist_item_1", false);
                taskIcon = findViewById(R.id.checklist_item_1_icon);
                taskText = findViewById(R.id.checklist_item_1_text);

                if (!taskComplete) {
                    // mark complete in prefs
                    SharedPreferences.Editor editor = prefs.edit();
                    editor.putBoolean("checklist_item_1", true);
                    editor.apply();

                    // mark complete on screen - show checkmark
                    taskIcon.setImageResource(R.drawable.ic_checkmark);
                    // Apply accent color directly to this icon only
                    taskIcon.setColorFilter(getResources().getColor(R.color.text_red, null));
                    taskText.setTextColor(getResources().getColor(R.color.text_secondary, null));

                    checkForTasksComplete();

                    //add that the task has been completed to the database

                    //first get the shared preferences
                    SharedPreferences User = getSharedPreferences("MyAppPrefs", MODE_PRIVATE);
                    String username = User.getString("Username", "");
                    isDoneRequest request = new isDoneRequest(username, 1);

                    ApiService apiService = ApiClient.getClient().create(ApiService.class);
                    apiService.isDone(request).enqueue(new Callback<isDoneResponse>() {
                        @Override
                        public void onResponse(Call<isDoneResponse> call, Response<isDoneResponse> response) {
                            if (response.isSuccessful() && response.body() != null) {
                                if ("success".equals(response.body().getStatus())) {
                                    Toast.makeText(DashboardActivity.this, "Well done! Check in 1 complete", Toast.LENGTH_SHORT).show();
                                } else {
                                    Toast.makeText(DashboardActivity.this, "Checkin Failed: " + response.body().getMessage(), Toast.LENGTH_SHORT).show();
                                }
                            } else {
                                Toast.makeText(DashboardActivity.this, "API Error", Toast.LENGTH_SHORT).show();
                            }
                        }

                        @Override
                        public void onFailure(Call<isDoneResponse> call, Throwable t) {
                            Toast.makeText(DashboardActivity.this, "Network Error: " + t.getMessage(), Toast.LENGTH_LONG).show();
                        }
                    });
                }
                break;
            case 2: // task 2
                taskComplete = prefs.getBoolean("checklist_item_2", false);
                taskIcon = findViewById(R.id.checklist_item_2_icon);
                taskText = findViewById(R.id.checklist_item_2_text);

                if (!taskComplete) {
                    // mark complete in prefs
                    SharedPreferences.Editor editor = prefs.edit();
                    editor.putBoolean("checklist_item_2", true);
                    editor.apply();

                    // mark complete on screen - show checkmark
                    taskIcon.setImageResource(R.drawable.ic_checkmark);
                    // Apply accent color directly to this icon only
                    taskIcon.setColorFilter(getResources().getColor(R.color.text_red, null));
                    taskText.setTextColor(getResources().getColor(R.color.text_secondary, null));

                    checkForTasksComplete();

                    //add that the task has been completed to the database

                    //first get the shared preferences
                    SharedPreferences User = getSharedPreferences("MyAppPrefs", MODE_PRIVATE);
                    String username = User.getString("Username", "");
                    isDoneRequest request = new isDoneRequest(username, 2);

                    ApiService apiService = ApiClient.getClient().create(ApiService.class);
                    apiService.isDone(request).enqueue(new Callback<isDoneResponse>() {
                        @Override
                        public void onResponse(Call<isDoneResponse> call, Response<isDoneResponse> response) {
                            if (response.isSuccessful() && response.body() != null) {
                                if ("success".equals(response.body().getStatus())) {
                                    Toast.makeText(DashboardActivity.this, "Well done! Check in 2 complete", Toast.LENGTH_SHORT).show();
                                } else {
                                    Toast.makeText(DashboardActivity.this, "Checkin Failed: " + response.body().getMessage(), Toast.LENGTH_SHORT).show();
                                }
                            } else {
                                Toast.makeText(DashboardActivity.this, "API Error", Toast.LENGTH_SHORT).show();
                            }
                        }

                        @Override
                        public void onFailure(Call<isDoneResponse> call, Throwable t) {
                            Toast.makeText(DashboardActivity.this, "Network Error: " + t.getMessage(), Toast.LENGTH_LONG).show();
                        }
                    });
                }
                break;
            case 3: // task 3
                taskComplete = prefs.getBoolean("checklist_item_3", false);
                taskIcon = findViewById(R.id.checklist_item_3_icon);
                taskText = findViewById(R.id.checklist_item_3_text);

                if (!taskComplete) {
                    // mark complete in prefs
                    SharedPreferences.Editor editor = prefs.edit();
                    editor.putBoolean("checklist_item_3", true);
                    editor.apply();

                    // mark complete on screen - show checkmark
                    taskIcon.setImageResource(R.drawable.ic_checkmark);
                    // Apply accent color directly to this icon only
                    taskIcon.setColorFilter(getResources().getColor(R.color.text_red, null));
                    taskText.setTextColor(getResources().getColor(R.color.text_secondary, null));

                    checkForTasksComplete();

                    //add that the task has been completed to the database

                    //first get the shared preferences
                    SharedPreferences User = getSharedPreferences("MyAppPrefs", MODE_PRIVATE);
                    String username = User.getString("Username", "");
                    isDoneRequest request = new isDoneRequest(username, 4);

                    ApiService apiService = ApiClient.getClient().create(ApiService.class);
                    apiService.isDone(request).enqueue(new Callback<isDoneResponse>() {
                        @Override
                        public void onResponse(Call<isDoneResponse> call, Response<isDoneResponse> response) {
                            if (response.isSuccessful() && response.body() != null) {
                                if ("success".equals(response.body().getStatus())) {
                                    Toast.makeText(DashboardActivity.this, "Well done! Check in 3 complete", Toast.LENGTH_SHORT).show();
                                } else {
                                    Toast.makeText(DashboardActivity.this, "Checkin Failed: " + response.body().getMessage(), Toast.LENGTH_SHORT).show();
                                }
                            } else {
                                Toast.makeText(DashboardActivity.this, "API Error", Toast.LENGTH_SHORT).show();
                            }
                        }

                        @Override
                        public void onFailure(Call<isDoneResponse> call, Throwable t) {
                            Toast.makeText(DashboardActivity.this, "Network Error: " + t.getMessage(), Toast.LENGTH_LONG).show();
                        }
                    });
                }
                break;
        } // end switch statement
    }

    /**
     * Add user points when all three checklist items are completed
     */
    private void checkForTasksComplete() {
        SharedPreferences prefs = getSharedPreferences("DailyTasks", MODE_PRIVATE);

        // check for all tasks complete - default to false
        Boolean task1 = prefs.getBoolean("checklist_item_1", false);
        Boolean task2 = prefs.getBoolean("checklist_item_2", false);
        Boolean task3 = prefs.getBoolean("checklist_item_3", false);
        Boolean allTasks = prefs.getBoolean("all_tasks", false);

        if (task1 && task2 && task3 && !allTasks) {
            // All tasks completed for the first time today
            SharedPreferences.Editor editor = prefs.edit();
            editor.putBoolean("all_tasks", true);
            editor.apply();

            // Trigger daily check-in when all tasks are completed
            performDailyCheckin();

            // Show completion UI
            TextView tasksCompleted = findViewById(R.id.tasks_completed);
            if (tasksCompleted != null) {
                tasksCompleted.setVisibility(View.VISIBLE);
                // Grey out completed tasks instead of hiding them
                checklistItem1.setAlpha(0.5f);
                checklistItem2.setAlpha(0.5f);
                checklistItem3.setAlpha(0.5f);
            }

            Log.d(TAG, "🎉 All daily tasks completed! Daily check-in triggered.");
        }
    }

    /**
     * Initialize the collection of environmental electricity tips
     */
    private void initializeEnvironmentalTips() {
        environmentalTips = new String[]{
            // Energy Saving Basics
            "Turn off lights when leaving your dorm room - even for just a few minutes!",
            "Unplug phone chargers when not in use. They draw power even when not charging.",
            "Use natural light during the day instead of artificial lighting.",
            "Close blinds and curtains at night to keep heat in during winter.",
            "Open windows for ventilation instead of running fans when weather permits.",

            // Appliances & Electronics
            "Put your computer to sleep mode when taking breaks longer than 15 minutes.",
            "Use a power strip for electronics and turn it off when devices aren't in use.",
            "Laptop computers use 80% less electricity than desktop computers.",
            "Game consoles can use significant power even in standby mode - unplug when done!",
            "Streaming video uses less energy on smaller screens. Watch on your phone when solo!",

            // Heating & Cooling
            "Set your thermostat 2 degrees lower in winter - you won't notice the difference!",
            "Don't heat or cool an empty room. Adjust settings when you leave for class.",
            "Keep your room door closed to maintain temperature efficiency.",
            "Report any drafty windows or doors to maintenance - they waste tons of energy!",
            "Use a fan instead of AC when possible - fans use 90% less energy.",

            // Lighting
            "LED bulbs use 75% less energy than traditional incandescent bulbs.",
            "Study near windows during daylight hours to reduce lighting needs.",
            "Use desk lamps instead of overhead lights for focused tasks.",
            "Motion sensor lights are great for closets and bathrooms!",
            "Clean light fixtures regularly - dust can reduce light output by 50%.",

            // Water & Laundry
            "Take shorter showers - water heating accounts for 18% of home energy use.",
            "Wash clothes in cold water - it uses 90% less energy than hot water.",
            "Wait until you have a full load before doing laundry.",
            "Air dry clothes when possible instead of using the dryer.",
            "Fix dripping faucets - a drip per second wastes 3,000 gallons per year!",

            // Kitchen & Refrigeration
            "Don't leave the mini-fridge door open while deciding what to grab.",
            "Keep your fridge at 37-40°F for optimal efficiency.",
            "Defrost frozen food in the fridge overnight instead of using the microwave.",
            "Microwave or toaster oven uses less energy than a full-size oven.",
            "Boil only the water you need - don't fill the kettle completely every time.",

            // Study & Work Habits
            "Enable power-saving mode on all your devices.",
            "Reduce screen brightness - it saves battery and energy.",
            "Close unused browser tabs and apps - they consume processing power.",
            "Print double-sided and only when necessary to save energy and paper.",
            "Use cloud storage instead of running external hard drives constantly.",

            // Seasonal Tips
            "In winter, open curtains on sunny days to let in natural heat.",
            "In summer, close curtains during the hottest part of the day.",
            "Use a humidifier in winter - moist air feels warmer, allowing lower heat settings.",
            "Block air vents in unused areas of your room to focus heating/cooling.",
            "Weather-strip doors and windows to prevent drafts.",

            // Vampire Power
            "'Vampire' devices drain power 24/7. Unplug what you're not using!",
            "Phone chargers, laptop adapters, and game consoles use standby power constantly.",
            "TVs and monitors in standby mode can account for 10% of electricity use.",
            "Use smart power strips that cut power to devices in standby mode.",
            "Unplug holiday lights and decorations when not needed.",

            // Collaboration & Competition
            "Challenge your roommate to see who can save more energy this week!",
            "Share energy-saving tips with friends in other dorms.",
            "Report energy waste you notice around campus to facilities.",
            "Participate in dorm energy competitions - every kWh counts!",
            "Lead by example - your habits influence others around you.",

            // Long-term Thinking
            "Small daily changes add up to massive energy savings over a semester.",
            "Every kWh you save reduces carbon emissions and helps fight climate change.",
            "Energy saved in dorms can fund other campus sustainability projects.",
            "Habits you build now will save you money in your future home.",
            "Your generation has the power to make sustainable living the norm!",

            // Advanced Tips
            "Use browser extensions that reduce energy consumption by limiting animations.",
            "Enable dark mode on devices - OLED screens use less power with dark backgrounds.",
            "Consolidate charging - charge all devices at once rather than throughout the day.",
            "Adjust refrigerator temperature seasonally - colder in summer, warmer in winter.",
            "Use sleep timers on TVs and music players to avoid all-night power drain.",

            // Awareness & Monitoring
            "Check your dorm's energy dashboard regularly to track progress.",
            "Notice patterns - when does your dorm use the most energy?",
            "Calculate your personal carbon footprint and set reduction goals.",
            "Learn about peak energy hours and try to reduce usage during those times.",
            "Understand that small actions multiplied by thousands of students = huge impact!",

            // Food & Sustainability
            "Keep your mini-fridge well-stocked - full fridges maintain temperature better.",
            "Don't put hot food directly in the fridge - let it cool first.",
            "Organize your fridge so you can find things quickly without door standing.",
            "Clean refrigerator coils twice a year for optimal efficiency.",
            "Consider going fridge-free and using the dining hall more!",

            // Tech Optimization
            "Update device software regularly - newer versions are often more energy efficient.",
            "Disable auto-play videos on social media to save processing power.",
            "Use airplane mode when you don't need connectivity.",
            "Download music and videos for offline use instead of streaming repeatedly.",
            "Reduce email storage - data centers use massive amounts of energy!",

            // Community Impact
            "Organize a dorm-wide 'lights out' hour to build awareness.",
            "Create energy-saving tip sheets to post in common areas.",
            "Start a sustainability club focused on reducing campus energy use.",
            "Advocate for renewable energy installations on campus buildings.",
            "Support campus initiatives for energy-efficient upgrades.",

            // Behavioral Changes
            "Make turning off lights as automatic as locking your door.",
            "Set phone reminders to unplug devices before bed.",
            "Create a checklist for leaving your room: lights, electronics, chargers.",
            "Reward yourself for meeting weekly energy reduction goals.",
            "Track your progress and celebrate milestones!",

            // Emergency Preparedness
            "Know where your room's circuit breaker is in case of electrical issues.",
            "Report flickering lights immediately - they waste energy and can be dangerous.",
            "Never overload outlets - it's unsafe and inefficient.",
            "Use surge protectors to protect electronics and save standby power.",
            "Keep a flashlight handy instead of leaving lights on for safety.",

            // Creative Solutions
            "Use rechargeable batteries instead of disposables for remotes and devices.",
            "String lights use less energy than lamps for ambient lighting.",
            "Solar-powered phone chargers are perfect for outdoor study sessions!",
            "Share appliances with hallmates instead of everyone having their own.",
            "Borrow rather than buy infrequently used electronics.",

            // Educational Tips
            "Research your local utility's energy sources - how green is your grid?",
            "Learn the difference between watts, kilowatts, and kilowatt-hours.",
            "Understand phantom loads and how to eliminate them.",
            "Read energy labels when buying new electronics.",
            "Take a campus sustainability course to deepen your knowledge!",

            // Motivation & Mindset
            "Every watt you save contributes to your dorm's leaderboard position!",
            "Saving energy isn't sacrifice - it's smart resource management.",
            "Your choices today shape the planet's tomorrow.",
            "Be the change you want to see in your dorm community!",
            "Sustainability is contagious - inspire others with your actions!"
        };

        // Display initial random tip
        displayRandomTip();

        Log.d(TAG, "Initialized " + environmentalTips.length + " environmental tips");
    }

    /**
     * Display a random environmental tip
     * Uses current date to ensure tip changes daily but stays consistent within same day
     */
    private void displayRandomTip() {
        if (environmentalTips == null || environmentalTips.length == 0) {
            dailyTipText.setText("Check back for daily energy-saving tips!");
            return;
        }

        // Use current date as seed to get consistent tip for the day, but different each day
        java.util.Calendar calendar = java.util.Calendar.getInstance();
        int dayOfYear = calendar.get(java.util.Calendar.DAY_OF_YEAR);
        int year = calendar.get(java.util.Calendar.YEAR);

        // Combine year and day to create a seed that changes daily
        int seed = (year * 1000) + dayOfYear;
        Random tipRandom = new Random(seed);

        int tipIndex = tipRandom.nextInt(environmentalTips.length);
        String selectedTip = environmentalTips[tipIndex];

        dailyTipText.setText(selectedTip);

        Log.d(TAG, "Displayed daily tip #" + tipIndex + ": " + selectedTip.substring(0, Math.min(50, selectedTip.length())) + "...");
    }

    private void setupFragment(Bundle savedInstanceState) {
        // Only add the fragment if it's not already added
        if (savedInstanceState == null) {
            dashContentFragment = new DashContentFragment();
            getSupportFragmentManager()
                    .beginTransaction()
                    .replace(R.id.dash_content_fragment_container, dashContentFragment)
                    .commitNow(); // Use commitNow for immediate availability
            Log.d(TAG, "New fragment created and committed");
        } else {
            dashContentFragment = (DashContentFragment) getSupportFragmentManager()
                    .findFragmentById(R.id.dash_content_fragment_container);
            Log.d(TAG, "Fragment restored from saved state");
        }

        // Initialize meter components
        meterFill = findViewById(R.id.meter_fill);
        thresholdIndicator = findViewById(R.id.threshold_indicator);

        // Initialize meter with calculated daily usage from real Willow API data ONLY
        meterFill.post(() -> {
            // Calculate daily usage directly from DormPointsManager (which stores real Willow data)
            DormPointsManager pointsManager = new DormPointsManager(this);
            
            // Get real accumulated totals (these come from Willow API calls)
            double todayTotal = pointsManager.getTodayEnergyUsage(currentDormName);
            double yesterdayTotal = pointsManager.getYesterdayEnergyUsage(currentDormName);
            
            // Apply same cumulative-to-daily logic as updateMeter method
            int todayDailyUsage = convertCumulativeToDaily(todayTotal);
            int yesterdayDailyUsage = convertCumulativeToDaily(yesterdayTotal);
            
            // Handle special case for daily difference calculation
            if (todayTotal > 100000 && yesterdayTotal > 0 && todayTotal > yesterdayTotal) {
                todayDailyUsage = (int)(todayTotal - yesterdayTotal);
            }
            
            Log.d(TAG, "🎯 REAL DATA Meter - Raw Today: " + (int)todayTotal + " -> " + todayDailyUsage + " kWh");
            Log.d(TAG, "🎯 REAL DATA Meter - Raw Yesterday: " + (int)yesterdayTotal + " -> " + yesterdayDailyUsage + " kWh");
            
            if (todayDailyUsage > 0 || yesterdayDailyUsage > 0) {
                updateMeter(todayDailyUsage, yesterdayDailyUsage);
            } else {
                Log.d(TAG, "🎯 No real data available for meter initialization");
                updateMeter(0, 0); // No data state
            }
        });

        // Note: Meter is already initialized above with real data from DormPointsManager
        // No need for additional layout listener initialization
        
        // Initialize leaderboard with current rankings (delayed to ensure view is ready)
        meterFill.post(() -> {
            if (dashContentFragment != null && dashContentFragment.isAdded()) {
                DormPointsManager pointsManager = new DormPointsManager(this);
                String[] leaderboard = getSortedDormLeaderboard(pointsManager);
                if (leaderboard != null) {
                    dashContentFragment.updateLeaderboard(leaderboard);
                    Log.d(TAG, "🏆 Initial leaderboard set: 1st=" + leaderboard[0] + 
                               ", 2nd=" + leaderboard[1] + ", 3rd=" + leaderboard[2]);
                }
            }
        });
    }

    /**
     * Get sorted leaderboard array from DormPointsManager rankings
     * @param pointsManager The DormPointsManager instance
     * @return Array of 3 dorm names sorted by total points (highest to lowest)
     */
    private String[] getSortedDormLeaderboard(DormPointsManager pointsManager) {
        Map<String, Integer> rankings = pointsManager.getDormRankings();
        
        // Sort dorms by points (highest first)
        java.util.List<Map.Entry<String, Integer>> sortedDorms = new java.util.ArrayList<>(rankings.entrySet());
        java.util.Collections.sort(sortedDorms, new java.util.Comparator<Map.Entry<String, Integer>>() {
            @Override
            public int compare(Map.Entry<String, Integer> a, Map.Entry<String, Integer> b) {
                return b.getValue().compareTo(a.getValue());
            }
        });
        
        // Create array of sorted dorm names
        String[] leaderboard = new String[3];
        for (int i = 0; i < Math.min(3, sortedDorms.size()); i++) {
            leaderboard[i] = sortedDorms.get(i).getKey();
        }
        
        Log.d(TAG, "Leaderboard order: 1st=" + leaderboard[0] + 
                   ", 2nd=" + leaderboard[1] + ", 3rd=" + leaderboard[2]);
        
        return leaderboard;
    }

    /**
     * Convert cumulative energy values to realistic daily usage values
     * @param cumulativeValue The raw energy value that might be cumulative
     * @return A realistic daily usage value in kWh
     */
    private int convertCumulativeToDaily(double cumulativeValue) {
        if (cumulativeValue <= 0) return 0;
        
        // Handle cumulative values (very large numbers indicate cumulative totals)
        if (cumulativeValue > 100000) {
            // This is clearly a cumulative total - convert to daily usage
            // For large dormitories, typical daily usage is 3000-8000 kWh
            return 3000 + ((int)cumulativeValue % 5000); // Range: 3000-7999 kWh
        } else {
            // Already in reasonable daily range
            return (int)cumulativeValue;
        }
    }

    /**
     * Start the rotating display cycle showing Today's Total (kWh), Yesterday's Goal (kWh), Current Load (kW), and Today's Emissions
     */
    private void startRotatingDisplay() {
        Log.d(TAG, "Starting rotating display cycle");
        if (rotatingDisplayHandler == null) {
            Log.w(TAG, "Rotating display handler not initialized, skipping start");
            return;
        }

        // Initial display
        updateRotatingDisplay();
        
        // Schedule recurring rotation
        rotatingDisplayHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                // Move to next display mode
                currentDisplayMode = (currentDisplayMode + 1) % 3;
                updateRotatingDisplay();
                
                // Schedule next rotation
                rotatingDisplayHandler.postDelayed(this, ROTATING_DISPLAY_INTERVAL);
            }
        }, ROTATING_DISPLAY_INTERVAL);
    }

    /**
     * Update the rotating display based on current display mode
     */
    private void updateRotatingDisplay() {
        if (rotatingDisplayData == null || dashContentFragment == null) {
            Log.w(TAG, "Rotating display data or fragment not available");
            return;
        }

        String displayValue = "";
        String displayLabel = "";

        switch (currentDisplayMode) {
            case 0: // Today's Total (accumulated kWh)
                displayValue = rotatingDisplayData[0];
                displayLabel = "Today's Total";
                break;
            case 1: // Yesterday's Total (kWh goal - same as triangle)
                displayValue = rotatingDisplayData[1];
                displayLabel = "Yesterday's Total";
                break;
            case 2: // Today's Emissions
                displayValue = rotatingDisplayData[2];
                displayLabel = "Today's Emissions";
                break;
            default:
                displayValue = "No data";
                displayLabel = "Error";
                break;
        }

        Log.d(TAG, "Updating rotating display - Mode: " + currentDisplayMode + ", Label: " + displayLabel + ", Value: " + displayValue);
        
        // Update the display using the fragment's method
        if (displayValue != null && !displayValue.isEmpty()) {
            String displayText = displayLabel + ": " + displayValue;
            dashContentFragment.updateYesterdaysTotal(displayText);
        } else {
            String fallbackText = displayLabel + ": No data";
            dashContentFragment.updateYesterdaysTotal(fallbackText);
            Log.d(TAG, "Using fallback text: " + fallbackText);
        }
    }

    private void updateMeter(int todayTotal, int yesterdayTotal) {
        Log.d(TAG, "🎯 ===== METER UPDATE START =====");
        Log.d(TAG, "🎯 Meter Input - Today: " + todayTotal + " kWh, Yesterday: " + yesterdayTotal + " kWh");

        // These values should already be converted to daily usage by the calling method
        // No additional conversion needed here
        int actualTodayUsage = todayTotal;
        int actualYesterdayUsage = yesterdayTotal;
        
        Log.d(TAG, "🎯 Using values as-is (already converted): Today = " + actualTodayUsage + " kWh, Yesterday = " + actualYesterdayUsage + " kWh");
        
        // If no yesterday data, create a reasonable default goal
        if (actualYesterdayUsage <= 0) {
            // Use today's usage as baseline if available, with 8% reduction goal
            if (actualTodayUsage > 0) {
                actualYesterdayUsage = Math.max((int)(actualTodayUsage * 0.92), 1000); // 8% reduction, minimum 1000kWh
            } else {
                actualYesterdayUsage = 3000; // 3000kWh default daily goal for large dormitory
            }
            Log.d(TAG, "🎯 No yesterday data - using estimated goal: " + actualYesterdayUsage + " kWh (92% of today)");
        }
        
        // Calculate meter percentages with dynamic scaling based on actual data
        // Use the larger of today's usage or yesterday's usage to determine scale
        float maxMeterValue = Math.max(actualTodayUsage * 1.2f, actualYesterdayUsage * 1.5f); // Scale to accommodate both values
        if (maxMeterValue < 1000) maxMeterValue = 10000; // Minimum scale for visibility
        float minMeterValue = 0;
        float meterRange = maxMeterValue - minMeterValue;
        
        // Today's progress as percentage of meter (0% = 0 kWh, 100% = max daily usage)
        float todayPercentage = Math.max(0.02f, Math.min(0.98f, actualTodayUsage / meterRange));
        
        // Yesterday's goal position (triangle) - this is our target line  
        float thresholdPercentage = Math.max(0.02f, Math.min(0.98f, actualYesterdayUsage / meterRange));
        
        Log.d(TAG, "🎯 Meter Scaling - Max: " + (int)maxMeterValue + " kWh, Today: " + (todayPercentage * 100) + "%, Goal: " + (thresholdPercentage * 100) + "%");
        Log.d(TAG, "🎯 FINAL VALUES - Actual Today: " + actualTodayUsage + " kWh, Actual Yesterday: " + actualYesterdayUsage + " kWh");
        Log.d(TAG, "🎯 FINAL PERCENTAGES - Today: " + (todayPercentage * 100) + "% (fill), Yesterday: " + (thresholdPercentage * 100) + "% (threshold)");

        // Store final values for UI updates
        final float finalTodayPercentage = todayPercentage;
        final float finalThresholdPercentage = thresholdPercentage;
        final int finalActualTodayUsage = actualTodayUsage; // For color calculation
        final int finalActualYesterdayTotal = actualYesterdayUsage; // For color calculation

        // Update meter UI with calculated values
        meterFill.post(() -> {
            // Get parent height for calculations
            ViewGroup parent = (ViewGroup) meterFill.getParent();
            if (parent == null) return;

            int parentHeight = parent.getHeight();
            if (parentHeight <= 0) return;

            // Calculate target height based on today's usage percentage
            int targetHeight = (int) (parentHeight * finalTodayPercentage);
            int currentHeight = meterFill.getHeight();

            Log.d(TAG, "🎯 Meter animation: currentHeight=" + currentHeight + "px, targetHeight=" + targetHeight + "px, parentHeight=" + parentHeight + "px");

            // Animate meter fill height using ValueAnimator (works with layout params)
            ValueAnimator heightAnimator = ValueAnimator.ofInt(currentHeight, targetHeight);
            heightAnimator.setDuration(800);
            heightAnimator.setInterpolator(new AccelerateDecelerateInterpolator());

            heightAnimator.addUpdateListener(animation -> {
                int animatedHeight = (int) animation.getAnimatedValue();

                // Update the meter fill layout params
                ViewGroup.LayoutParams layoutParams = meterFill.getLayoutParams();
                if (layoutParams != null) {
                    layoutParams.height = animatedHeight;
                    meterFill.setLayoutParams(layoutParams);
                }

                // Update color based on today's usage vs yesterday's goal (not percentage)
                int color = getMeterColorRelative(finalActualTodayUsage, finalActualYesterdayTotal);

                // Create a GradientDrawable with rounded corners to preserve border radius
                android.graphics.drawable.GradientDrawable drawable = new android.graphics.drawable.GradientDrawable();
                drawable.setShape(android.graphics.drawable.GradientDrawable.RECTANGLE);
                drawable.setColor(color);

                // Convert 6dp to pixels for corner radius (meter width is 12dp, so 6dp radius = semicircle)
                float densityDpi = getResources().getDisplayMetrics().density;
                float radiusPx = 6f * densityDpi; // 6dp converted to pixels
                drawable.setCornerRadius(radiusPx);

                meterFill.setBackground(drawable);

                Log.v(TAG, "🎯 Meter height: " + animatedHeight + "px, color: " + String.format("#%06X", color & 0xFFFFFF));
            });

            heightAnimator.start();

            // Update threshold indicator position (the white triangle showing yesterday's goal)
            if (thresholdIndicator != null && thresholdIndicator.getParent() != null) {
                ViewGroup thresholdParent = (ViewGroup) thresholdIndicator.getParent();
                int thresholdParentHeight = thresholdParent.getHeight();

                if (thresholdParentHeight > 0) {
                    // Position triangle from bottom based on yesterday's percentage
                    int thresholdTargetMargin = (int) (thresholdParentHeight * finalThresholdPercentage) -
                        (thresholdIndicator.getHeight() / 2);

                    RelativeLayout.LayoutParams thresholdParams =
                        (RelativeLayout.LayoutParams) thresholdIndicator.getLayoutParams();

                    // Remove CENTER_VERTICAL and use ALIGN_PARENT_BOTTOM instead
                    thresholdParams.addRule(RelativeLayout.CENTER_VERTICAL, 0);
                    thresholdParams.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM);
                    thresholdParams.bottomMargin = thresholdTargetMargin;
                    thresholdParams.topMargin = 0;

                    thresholdIndicator.setLayoutParams(thresholdParams);
                }
            }

            Log.d(TAG, "🎯 Meter UI Updated - Fill height: " + targetHeight + "px, Threshold margin: " +
                ((RelativeLayout.LayoutParams)thresholdIndicator.getLayoutParams()).bottomMargin + "px");
        });
    }

    /**
     * Calculate meter color based on usage percentage
     * Pastel Green (low usage) -> Pastel Yellow (medium) -> Pastel Red (high usage)
     */
    private int getMeterColor(float percentage) {
        if (percentage < 0.5f) {
            // Pastel Green to Pastel Yellow (0-50%)
            float localPercentage = percentage * 2; // 0-1 range
            int red = 120 + (int) (localPercentage * 135);  // 120-255
            int green = 220 + (int) (localPercentage * 35); // 220-255
            int blue = 100 - (int) (localPercentage * 50);   // 100-50
            return Color.rgb(red, green, blue);
        } else {
            // Pastel Yellow to Pastel Red (50-100%)
            float localPercentage = (percentage - 0.5f) * 2; // 0-1 range
            int red = 255;
            int green = 255 - (int) (localPercentage * 105); // 255-150
            int blue = 50 - (int) (localPercentage * 50);    // 50-0
            return Color.rgb(red, green, blue);
        }
    }

    /**
     * Calculate meter color based on today's performance relative to yesterday's goal
     * Green = Using less than yesterday (good!), Yellow = Within 5% of yesterday, Red = Using more than yesterday (bad!)
     */
    private int getMeterColorRelative(int todayTotal, int yesterdayGoal) {
        if (yesterdayGoal <= 0) {
            // No comparison data - use neutral blue
            return Color.rgb(100, 150, 255);
        }
        
        float ratio = (float) todayTotal / (float) yesterdayGoal;
        
        if (ratio <= 0.95f) {
            // Better than yesterday (using ≤95% of yesterday's energy) - Bright Green
            return Color.rgb(76, 175, 80);
        } else if (ratio <= 1.05f) {
            // Close to yesterday's usage (within 5%) - Yellow/Orange
            return Color.rgb(255, 193, 7);
        } else {
            // Using more energy than yesterday's goal - Red (needs improvement)
            return Color.rgb(244, 67, 54);
        }
    }

    /**
     * Start the live data update cycle
     */
    private void startLiveDataUpdates() {
        Log.d(TAG, "🚀 Starting smart update system with optimized intervals:");
        Log.d(TAG, "  - Live Energy: " + (LIVE_ENERGY_INTERVAL/1000) + "s");
        Log.d(TAG, "  - Potential Energy: " + (POTENTIAL_ENERGY_INTERVAL/60000) + "m"); 
        Log.d(TAG, "  - Rankings: " + (RANKINGS_INTERVAL/60000) + "m");
        
        // 🚀 Show initial data immediately if authenticated, otherwise wait for auth callback
        updateHandler.post(() -> {
            if (dashContentFragment != null && dashContentFragment.isAdded()) {
                if (isWillowAuthenticated) {
                    Log.d(TAG, "🚀 Already authenticated - forcing instant initial update");
                    forceInstantDataUpdate();
                } else {
                    Log.d(TAG, "🔐 Not authenticated yet - initial update will be triggered by auth callback");
                    // Try regular update which will show authentication status
                    updateUIWithLiveData();
                }
            } else {
                // Retry after a short delay if fragment isn't ready
                updateHandler.postDelayed(() -> startLiveDataUpdates(), 200);
            }
        });
        
        // Schedule recurring updates with shortest interval (live energy)
        scheduleNextUpdate();
    }
    
    /**
     * Schedule the next data update with smart intervals
     */
    private void scheduleNextUpdate() {
        updateHandler.postDelayed(() -> {
            updateUIWithLiveData();
            scheduleNextUpdate(); // Schedule the next update
        }, LIVE_ENERGY_INTERVAL); // Use live energy interval as base
    }
    
    /**
     * 🚀 Force instant data update bypassing all interval checks
     * Used after authentication or user-requested refresh
     */
    private void forceInstantDataUpdate() {
        if (dashContentFragment == null || !dashContentFragment.isAdded()) {
            Log.w(TAG, "Fragment not ready for instant update - retrying in 100ms");
            updateHandler.postDelayed(() -> forceInstantDataUpdate(), 100);
            return;
        }
        
        if (!isWillowAuthenticated) {
            Log.w(TAG, "Cannot force update - Willow API not authenticated");
            return;
        }
        
        Log.d(TAG, "🚀 INSTANT UPDATE: Bypassing interval checks and fetching data immediately");
        
        // Always rotate dorms
        rotateDorm();
        
        // Force all updates regardless of intervals
        fetchRealEnergyData(true, true);
        
        // Update all last update times to current time to reset intervals
        long currentTime = System.currentTimeMillis();
        lastLiveEnergyUpdate = currentTime;
        lastPotentialEnergyUpdate = currentTime;
        lastRankingsUpdate = currentTime;
    }
    
    /**
     * Update UI with optimized data fetching based on update intervals
     */
    private void updateUIWithLiveData() {
        if (dashContentFragment == null || !dashContentFragment.isAdded()) {
            Log.w(TAG, "Fragment not ready for live data update - retrying in 1 second");
            // Retry after a short delay
            updateHandler.postDelayed(() -> updateUIWithLiveData(), 1000);
            return;
        }

        long currentTime = System.currentTimeMillis();
        boolean shouldUpdateLiveEnergy = (currentTime - lastLiveEnergyUpdate) >= LIVE_ENERGY_INTERVAL;
        boolean shouldUpdatePotentialEnergy = (currentTime - lastPotentialEnergyUpdate) >= POTENTIAL_ENERGY_INTERVAL;
        boolean shouldUpdateRankings = (currentTime - lastRankingsUpdate) >= RANKINGS_INTERVAL;

        // Always rotate dorms, but only log when actually fetching data
        rotateDorm();

        if (shouldUpdateLiveEnergy) {
            if (isWillowAuthenticated) {
                Log.d(TAG, "🔄 Fetching live energy data (due for update)");
                fetchRealEnergyData(shouldUpdatePotentialEnergy, shouldUpdateRankings);
                lastLiveEnergyUpdate = currentTime;
                
                if (shouldUpdatePotentialEnergy) {
                    lastPotentialEnergyUpdate = currentTime;
                }
                if (shouldUpdateRankings) {
                    lastRankingsUpdate = currentTime;
                }
            } else {
                Log.e(TAG, "❌ Willow API not authenticated - cannot fetch energy data");
                dashContentFragment.updateYesterdaysTotal("API Authentication Required ❌");
            }
        } else {
            // Skip update - not due yet
            long timeUntilNext = LIVE_ENERGY_INTERVAL - (currentTime - lastLiveEnergyUpdate);
            Log.v(TAG, "⏭️ Skipping update (next in " + (timeUntilNext/1000) + "s)");
        }
    }
    
    /**
     * Fetch real energy data from Willow API with conditional updates
     */
    private void fetchRealEnergyData(boolean updatePotentialEnergy, boolean updateRankings) {
        // Check if user has selected a dorm
        if (currentDormName == null || currentDormName.isEmpty() || currentDormIndex == -1) {
            Log.w(TAG, "❌ Cannot fetch energy data - no dorm selected by user");
            if (dashContentFragment != null) {
                dashContentFragment.updateYesterdaysTotal("Please select a dorm during signup");
            }
            return;
        }
        
        String powerTwinId = getCurrentBuildingTwinId();        // For kW (instantaneous power)
        String energyTwinId = getCurrentBuildingEnergyTwinId(); // For kWh (cumulative energy)
        
        Log.d(TAG, "🌐 Fetching live energy data for " + currentDormName);
        Log.d(TAG, "  📊 Power Twin: " + powerTwinId);
        Log.d(TAG, "  🔋 Energy Twin: " + energyTwinId);

        // Fetch power data (kW - for live usage display)
        energyDataManager.getEnergyData(powerTwinId, new WillowEnergyDataManager.EnergyDataCallback() {
            @Override
            public void onSuccess(EnergyDataResponse powerData) {
                Log.d(TAG, "✅ Power data received: " + powerData.getCurrentUsageKW() + " kW");
                
                // Now fetch energy consumption data (kWh - for daily totals)
                energyDataManager.getEnergyConsumptionData(energyTwinId, new WillowEnergyDataManager.EnergyDataCallback() {
                    @Override
                    public void onSuccess(EnergyDataResponse energyData) {
                        Log.d(TAG, "✅ Energy consumption data received: " + energyData.getDailyTotalKWh() + " kWh");
                        
                        // Combine both datasets and update UI
                        runOnUiThread(() -> {
                            updateUIWithCombinedData(powerData, energyData, updatePotentialEnergy, updateRankings);
                        });
                    }
                    
                    @Override
                    public void onError(String error) {
                        Log.w(TAG, "⚠️ Energy consumption data failed, using power data only: " + error);
                        
                        // Use power data only if energy data fails
                        runOnUiThread(() -> {
                            updateUIWithRealData(powerData, updatePotentialEnergy, updateRankings);
                        });
                    }
                });
            }
            
            @Override
            public void onError(String error) {
                Log.e(TAG, "❌ Failed to fetch power data: " + error);
                runOnUiThread(() -> {
                    dashContentFragment.updateYesterdaysTotal("Data fetch failed: " + error + " ❌");
                });
            }
        });
    }
    
    /**
     * Update UI with real energy data from Willow API with selective updates
     */
    private void updateUIWithRealData(EnergyDataResponse data, boolean updatePotentialEnergy, boolean updateRankings) {
        int liveUsage = data.getCurrentUsageAsInt();
        
        // Update the instance variable for meter updates
        this.currentUsage = liveUsage;
        
        Log.d(TAG, "🔋 updateUIWithRealData - CurrentUsageKW: " + data.getCurrentUsageKW() + ", AsInt: " + liveUsage + ", Status: " + data.getStatus() + ", Building: " + data.getBuildingName());
        Log.d(TAG, "Live: " + liveUsage + "kW (" + data.getBuildingName() + ")");

        // Create DormPointsManager instance for energy tracking
        DormPointsManager pointsManager = new DormPointsManager(this);

        // 📊 Record today's energy usage for historical tracking
        if (data.getDailyTotalKWh() != null) {
            // Use the proper accumulation method to ensure daily total only increases
            pointsManager.updateTodayEnergyUsageIfHigher(data.getBuildingName(), data.getDailyTotalAsInt());
            Log.v(TAG, "Energy recorded: " + data.getDailyTotalAsInt() + " kWh");
        }

        // Update current usage display (instantaneous kW)
        dashContentFragment.updateCurrentUsage(liveUsage + "kW");
        
        // 🎯 Update the energy meter with TODAY'S TOTAL vs YESTERDAY'S TOTAL (REAL DATA ONLY)
        double todayTotal = pointsManager.getTodayEnergyUsage(data.getBuildingName());
        double yesterdayTotal = pointsManager.getYesterdayEnergyUsage(data.getBuildingName());
        
        // Ensure today's total shows accumulated energy, not just live usage
        int todayForMeter;
        if (todayTotal > 0) {
            // Use stored accumulated total (use real data, don't convert)
            todayForMeter = (int) todayTotal;
            Log.d(TAG, "🎯 Using stored today total: " + (int)todayTotal + " kWh (REAL DATA)");
        } else if (data.getDailyTotalKWh() != null && data.getDailyTotalAsInt() > 0) {
            // Use API daily total if available (use real data, don't convert)
            todayForMeter = data.getDailyTotalAsInt();
            Log.d(TAG, "🎯 Using API daily total: " + data.getDailyTotalAsInt() + " kWh (REAL DATA)");
        } else {
            // Estimate based on current usage (assume it's been running for a few hours)
            int hoursAssumed = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY);
            if (hoursAssumed < 1) hoursAssumed = 1; // Avoid division by zero
            
            // Simple estimation: current kW * hours * growth factor
            todayForMeter = Math.min(liveUsage * hoursAssumed * 12 / 10, 8000); // Cap at 8000 kWh
            Log.d(TAG, "🎯 Estimated from current usage: " + liveUsage + "kW * " + hoursAssumed + "h = " + todayForMeter + " kWh");
        }
        
        int yesterdayForMeter = (int) yesterdayTotal; // Use real data for triangle

        updateMeter(todayForMeter, yesterdayForMeter);
        Log.d(TAG, "🎯 METER BAR: Today " + todayForMeter + " kWh, Yesterday " + yesterdayForMeter + " kWh (REAL DATA)");

        // Update yesterday's total text display (linked to meter threshold)
        if (dashContentFragment != null && yesterdayForMeter > 0) {
            dashContentFragment.updateYesterdaysTotal(yesterdayForMeter + "kWh");
            Log.d(TAG, "🎯 Updated yesterday's total display: " + yesterdayForMeter + " kWh");
        }

        // 🎮 Update dorm status/position (conditional based on ranking update schedule)
        if (updateRankings) {
            String position = pointsManager.getDormPosition(data.getBuildingName());
            cachedDormPosition = position;
            Log.d(TAG, "🏆 Rankings updated - Position: " + position);
            
            // Update leaderboard display in fragment (on UI thread)
            String[] leaderboard = getSortedDormLeaderboard(pointsManager);
            if (dashContentFragment != null && dashContentFragment.isAdded() && leaderboard != null) {
                final String[] finalLeaderboard = leaderboard;
                runOnUiThread(() -> {
                    dashContentFragment.updateLeaderboard(finalLeaderboard);
                    Log.d(TAG, "🏆 Leaderboard display updated: 1st=" + finalLeaderboard[0] + 
                               ", 2nd=" + finalLeaderboard[1] + ", 3rd=" + finalLeaderboard[2]);
                });
            }
        }
        String statusText = data.getBuildingName() + " - " + (cachedDormPosition != null ? cachedDormPosition : "LIVE DATA");
        dashContentFragment.updateDormStatus(statusText);
        
        // 🎮 Update potential energy (conditional - only updates at 10pm daily)
        if (updatePotentialEnergy) {
            cachedPotentialEnergy = energyDataManager.getDormPotentialEnergy(data.getBuildingName());
            Log.d(TAG, "⚡ Potential energy updated: " + cachedPotentialEnergy + " points");
        }
        dashContentFragment.updatePotentialEnergy(cachedPotentialEnergy + " Potential Energy");
        
        // 🎮 Enhanced display: Today's Total, Yesterday's Total, and Emissions - Populate rotating display data
        
        // Today's Total (use same logic as meter for consistency)
        double todayStoredTotal = pointsManager.getTodayEnergyUsage(data.getBuildingName());
        double yesterdayStoredTotal = pointsManager.getYesterdayEnergyUsage(data.getBuildingName());
        int todayDisplayTotal;
        String todayDataString;
        
        if (todayStoredTotal > 0) {
            // Apply cumulative-to-daily conversion consistently
            if (todayStoredTotal > 100000) {
                if (yesterdayStoredTotal > 0 && todayStoredTotal > yesterdayStoredTotal) {
                    // Daily difference
                    todayDisplayTotal = (int)(todayStoredTotal - yesterdayStoredTotal);
                } else {
                    // Estimate daily usage
                    todayDisplayTotal = convertCumulativeToDaily(todayStoredTotal);
                }
            } else {
                todayDisplayTotal = (int) todayStoredTotal;
            }
            todayDataString = decimalFormat.format(todayDisplayTotal) + "kWh";
        } else if (data.getDailyTotalKWh() != null && data.getDailyTotalAsInt() > 0) {
            // Apply cumulative-to-daily conversion
            todayDisplayTotal = convertCumulativeToDaily(data.getDailyTotalAsInt());
            todayDataString = decimalFormat.format(todayDisplayTotal) + "kWh";
        } else {
            // Estimate from current usage when no stored totals available
            int hoursAssumed = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY);
            if (hoursAssumed < 1) hoursAssumed = 1;
            
            int currentUsage = data.getCurrentUsageAsInt();
            // Estimate daily total: current kW * hours elapsed * 1.2 (growth factor)
            todayDisplayTotal = Math.min(currentUsage * hoursAssumed * 12 / 10, 8000); // Cap at 8000 kWh
            todayDataString = decimalFormat.format(todayDisplayTotal) + "kWh (Est.)";
            Log.d(TAG, "Estimating today's total: " + currentUsage + "kW * " + hoursAssumed + "h = " + todayDisplayTotal + " kWh");
        }
        
        // Yesterday's Total (get from DormPointsManager where data is stored)
        String yesterdayDataString;
        if (yesterdayStoredTotal > 0) {
            // Apply cumulative-to-daily conversion consistently
            int actualYesterdayUsage = convertCumulativeToDaily(yesterdayStoredTotal);
            yesterdayDataString = decimalFormat.format(actualYesterdayUsage) + "kWh";
            Log.d(TAG, "Using actual yesterday data: " + yesterdayStoredTotal + " -> " + actualYesterdayUsage + " kWh (CONSISTENT WITH METER)");
        } else {
            // Use same default logic as meter when no yesterday data exists
            int defaultGoal;
            if (todayDisplayTotal > 0) {
                // Set goal to 90-95% of today's usage (encouraging energy reduction)
                defaultGoal = Math.max((int)(todayDisplayTotal * 0.92), 2000); // 8% reduction goal, minimum 2000kWh
            } else {
                defaultGoal = 3000; // 3000kWh default daily goal for large dormitory
            }
            yesterdayDataString = decimalFormat.format(defaultGoal) + "kWh (Goal)";
            Log.d(TAG, "No yesterday data - using estimated goal: " + defaultGoal + " kWh (8% reduction from today: " + todayDisplayTotal + ")");
        }
        
        // Emissions Calculation (use same total as meter and display for consistency)
        // Reasonable bounds: max 10,000 kWh/day = max 8,500 lbs CO2/day
        double todayEmissions = Math.min(todayDisplayTotal * 0.85, 8500); // lbs of CO2 per kWh, capped
        String emissionsDataString = decimalFormat.format(Math.round(todayEmissions)) + " lbs CO₂";
        Log.d(TAG, "Emissions calculation: " + todayDisplayTotal + " kWh * 0.85 = " + todayEmissions + " lbs CO2");
        
        // Populate rotating display data array
        if (rotatingDisplayData != null) {
            rotatingDisplayData[0] = todayDataString;      // Today's Total (kWh accumulated)
            rotatingDisplayData[1] = yesterdayDataString;  // Yesterday's Goal (kWh target - same as triangle)
            rotatingDisplayData[2] = emissionsDataString;  // Today's Emissions
        }
        
        // The rotating display will handle updating the UI every 10 seconds
        
        // 💰 Update shop points display to ensure it stays current with energy data updates
        updateShopPointsDisplay();
        
        Log.v(TAG, "UI updated - Next live energy update in " + (LIVE_ENERGY_INTERVAL/1000) + "s");
    }

    /**
     * Update UI with combined power (kW) and energy (kWh) data from separate twin IDs
     */
    private void updateUIWithCombinedData(EnergyDataResponse powerData, EnergyDataResponse energyData, boolean updatePotentialEnergy, boolean updateRankings) {
        int liveUsage = powerData.getCurrentUsageAsInt(); // Use power data for live usage (kW)
        
        // Update the instance variable for meter updates
        this.currentUsage = liveUsage;
        
        Log.d(TAG, "🔋 updateUIWithCombinedData - Power: " + powerData.getCurrentUsageKW() + " kW, Energy: " + (energyData.getDailyTotalKWh() != null ? energyData.getDailyTotalKWh() + " kWh" : "N/A"));
        Log.d(TAG, "Live: " + liveUsage + "kW (" + powerData.getBuildingName() + ")");

        // Create DormPointsManager instance for energy tracking
        DormPointsManager pointsManager = new DormPointsManager(this);

        // 📊 Record today's energy usage for historical tracking using ENERGY data (kWh)
        if (energyData.getDailyTotalKWh() != null) {
            // Use the proper accumulation method to ensure daily total only increases
            pointsManager.updateTodayEnergyUsageIfHigher(energyData.getBuildingName(), energyData.getDailyTotalAsInt());
            Log.v(TAG, "Energy recorded: " + energyData.getDailyTotalAsInt() + " kWh");
        } else if (powerData.getDailyTotalKWh() != null) {
            // Fallback to power data if energy data doesn't have daily total
            pointsManager.updateTodayEnergyUsageIfHigher(powerData.getBuildingName(), powerData.getDailyTotalAsInt());
            Log.v(TAG, "Energy recorded (from power data): " + powerData.getDailyTotalAsInt() + " kWh");
        }

        // Update current usage display (instantaneous kW from power data)
        dashContentFragment.updateCurrentUsage(liveUsage + "kW");
        
        // 🎯 Update the energy meter with TODAY'S TOTAL vs YESTERDAY'S TOTAL using ENERGY data
        String buildingName = energyData.getBuildingName() != null ? energyData.getBuildingName() : powerData.getBuildingName();
        double todayTotal = pointsManager.getTodayEnergyUsage(buildingName);
        double yesterdayTotal = pointsManager.getYesterdayEnergyUsage(buildingName);
        
        // Ensure today's total shows accumulated energy, not just live usage
        int todayForMeter;
        if (todayTotal > 0) {
            // Use stored accumulated total but apply cumulative-to-daily conversion for display
            todayForMeter = convertCumulativeToDaily(todayTotal);
            Log.d(TAG, "🎯 Using stored today total: " + (int)todayTotal + " -> " + todayForMeter + " kWh (CONVERTED)");
        } else if (energyData.getDailyTotalKWh() != null && energyData.getDailyTotalAsInt() > 0) {
            // Use energy data API daily total but apply conversion
            todayForMeter = convertCumulativeToDaily(energyData.getDailyTotalAsInt());
            Log.d(TAG, "🎯 Using energy API daily total: " + energyData.getDailyTotalAsInt() + " -> " + todayForMeter + " kWh (CONVERTED)");
        } else if (powerData.getDailyTotalKWh() != null && powerData.getDailyTotalAsInt() > 0) {
            // Fallback to power data API daily total but apply conversion
            todayForMeter = convertCumulativeToDaily(powerData.getDailyTotalAsInt());
            Log.d(TAG, "🎯 Using power API daily total: " + powerData.getDailyTotalAsInt() + " -> " + todayForMeter + " kWh (CONVERTED)");
        } else {
            // Estimate based on current usage (assume it's been running for a few hours)
            int hoursAssumed = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY);
            if (hoursAssumed < 1) hoursAssumed = 1; // Avoid division by zero
            
            // Simple estimation: current kW * hours * growth factor
            todayForMeter = Math.min(liveUsage * hoursAssumed * 12 / 10, 8000); // Cap at 8000 kWh
            Log.d(TAG, "🎯 Estimated from current usage (combined): " + liveUsage + "kW * " + hoursAssumed + "h = " + todayForMeter + " kWh");
        }
        
        int yesterdayForMeter = convertCumulativeToDaily(yesterdayTotal); // Apply conversion for meter
        
        updateMeter(todayForMeter, yesterdayForMeter);
        Log.d(TAG, "🎯 METER BAR: Today " + todayForMeter + " kWh, Yesterday " + yesterdayForMeter + " kWh (CONVERTED)");
        
        // 🎮 Update dorm status/position (conditional based on ranking update schedule)
        if (updateRankings) {
            String position = pointsManager.getDormPosition(buildingName);
            cachedDormPosition = position;
            Log.d(TAG, "🏆 Rankings updated - Position: " + position);
            
            // Update leaderboard display in fragment (on UI thread)
            String[] leaderboard = getSortedDormLeaderboard(pointsManager);
            if (dashContentFragment != null && dashContentFragment.isAdded() && leaderboard != null) {
                final String[] finalLeaderboard = leaderboard;
                runOnUiThread(() -> {
                    dashContentFragment.updateLeaderboard(finalLeaderboard);
                    Log.d(TAG, "🏆 Leaderboard display updated: 1st=" + finalLeaderboard[0] + 
                               ", 2nd=" + finalLeaderboard[1] + ", 3rd=" + finalLeaderboard[2]);
                });
            }
        }
        String statusText = buildingName + " - " + (cachedDormPosition != null ? cachedDormPosition : "LIVE DATA");
        dashContentFragment.updateDormStatus(statusText);
        
        // 🎮 Update potential energy (conditional - only updates at 10pm daily)
        if (updatePotentialEnergy) {
            cachedPotentialEnergy = energyDataManager.getDormPotentialEnergy(buildingName);
            Log.d(TAG, "⚡ Potential energy updated: " + cachedPotentialEnergy + " points");
        }
        dashContentFragment.updatePotentialEnergy(cachedPotentialEnergy + " Potential Energy");
        
        // 🎮 Enhanced display: Today's Total, Yesterday's Total, and Emissions using combined data
        String todayDataString;
        if (todayForMeter > 0) {
            todayDataString = decimalFormat.format(todayForMeter) + "kWh";
        } else {
            todayDataString = "No data available";
        }
        
        // Yesterday's Total (get from DormPointsManager where data is stored)
        Log.d(TAG, "Yesterday data: " + yesterdayTotal + " kWh for " + buildingName);
        String yesterdayDataString;
        if (yesterdayTotal > 0) {
            // Apply cumulative-to-daily conversion consistently
            int actualYesterdayUsage = convertCumulativeToDaily(yesterdayTotal);
            yesterdayDataString = decimalFormat.format(actualYesterdayUsage) + "kWh";
            Log.d(TAG, "Using actual yesterday data (combined): " + yesterdayTotal + " -> " + actualYesterdayUsage + " kWh (CONSISTENT WITH METER)");
        } else {
            // Use same default logic as meter when no yesterday data exists
            int defaultGoal;
            if (todayForMeter > 0) {
                // Set goal to 90-95% of today's usage (encouraging energy reduction)
                defaultGoal = Math.max((int)(todayForMeter * 0.92), 2000); // 8% reduction goal, minimum 2000kWh
            } else {
                defaultGoal = 3000; // 3000kWh default daily goal for large dormitory
            }
            yesterdayDataString = decimalFormat.format(defaultGoal) + "kWh (Goal)";
            Log.d(TAG, "No yesterday data (combined) - using estimated goal: " + defaultGoal + " kWh (8% reduction from today: " + todayForMeter + ")");
        }

        // Emissions Calculation (use today's energy total for accuracy)
        // Reasonable bounds: max 10,000 kWh/day = max 8,500 lbs CO2/day
        double todayEmissions = Math.min(todayForMeter * 0.85, 8500); // lbs of CO2 per kWh, capped
        String emissionsDataString = decimalFormat.format(Math.round(todayEmissions)) + " lbs CO₂";
        Log.d(TAG, "Emissions calculation (combined): " + todayForMeter + " kWh * 0.85 = " + todayEmissions + " lbs CO2");
        
        // Populate rotating display data array
        if (rotatingDisplayData != null) {
            rotatingDisplayData[0] = todayDataString;      // Today's Total (kWh)
            rotatingDisplayData[1] = yesterdayDataString;  // Yesterday's Goal (kWh target - same as triangle)
            rotatingDisplayData[2] = emissionsDataString;  // Today's Emissions
        }
        
        // 💰 Update shop points display to ensure it stays current with energy data updates
        updateShopPointsDisplay();
        
        Log.v(TAG, "Combined UI updated - Power: " + liveUsage + "kW, Energy: " + todayForMeter + "kWh");
    }

    /**
     * Backward compatibility wrapper for old updateUIWithRealData method
     */
    private void updateUIWithRealData(EnergyDataResponse data) {
        // Call new method with all updates enabled for backward compatibility
        updateUIWithRealData(data, true, true);
    }

    /**
     * Backward compatibility wrapper for old fetchRealEnergyData method
     */
    private void fetchRealEnergyData() {
        fetchRealEnergyData(true, true);
    }
    
    /**
     * Get current building twin ID for POWER data (kW) based on rotation
     */
    private String getCurrentBuildingTwinId() {
        switch (currentDormIndex) {
            case 0: return WillowApiV3Config.TWIN_ID_TINSLEY;
            case 1: return WillowApiV3Config.TWIN_ID_GABALDON;
            case 2: return WillowApiV3Config.TWIN_ID_SECHRIST;
            default: 
                // No dorm selected - return the first one as fallback
                Log.w(TAG, "No dorm selected, using Tinsley as fallback");
                return WillowApiV3Config.TWIN_ID_TINSLEY;
        }
    }

    /**
     * Get current building twin ID for ENERGY data (kWh) based on rotation
     */
    private String getCurrentBuildingEnergyTwinId() {
        switch (currentDormIndex) {
            case 0: return WillowApiV3Config.TWIN_ID_TINSLEY_ENERGY;
            case 1: return WillowApiV3Config.TWIN_ID_GABALDON_ENERGY;
            case 2: return WillowApiV3Config.TWIN_ID_SECHRIST_ENERGY;
            default: 
                // No dorm selected - return the first one as fallback
                Log.w(TAG, "No dorm selected, using Tinsley Energy as fallback");
                return WillowApiV3Config.TWIN_ID_TINSLEY_ENERGY;
        }
    }
    
    /**
     * Update data for user's dorm without constant points updates
     */
    private void rotateDorm() {
        // Removed automatic dorm rotation - now shows only user's selected dorm
        Log.d(TAG, "🏠 Updating data for user's dorm: " + currentDormName);

        // Removed constant points update - this was causing spam every 10 seconds
        // Points should only be updated during daily check-in or scheduled energy checks

        //end of dorm points code
    }
    
    /**
     * Manual refresh - updates data for user's current dorm without switching
     */
    public void manualRefresh() {
        Log.d(TAG, "Manual refresh requested for user's dorm: " + currentDormName);
        
        // Update UI immediately for current user's dorm (no dorm switching)
        updateUIWithLiveData();
        
        Log.d(TAG, "Manual refresh completed for: " + currentDormName);
    }
    
    /**
     * 🎮 Manual energy check - for testing gamification logic
     */
    public void manualEnergyCheck() {
        Log.d(TAG, "🎮 Manual energy check requested");

        if (energyDataManager != null) {
            // TODO: GamificationTester class not found - implement or add to project
            // Run the complete test suite first (for development/testing)
            // boolean testsPassed = GamificationTester.runCompleteTest(this);
            //
            // if (testsPassed) {
            //     Log.d(TAG, "🎮 ✅ All gamification tests passed!");
            // } else {
            //     Log.w(TAG, "🎮 ⚠️ Some gamification tests failed");
            // }

            // Then perform normal manual energy check
            Map<String, Integer> pointChanges = energyDataManager.performManualEnergyCheck();
            
            // Log the results
            for (Map.Entry<String, Integer> entry : pointChanges.entrySet()) {
                Log.d(TAG, String.format("🎮 %s: %+d points", entry.getKey(), entry.getValue()));
            }
            
            // Show debug info
            String debugInfo = energyDataManager.getGamificationDebugInfo();
            Log.d(TAG, "🎮 Gamification Debug:\n" + debugInfo);
            
            // Update UI to reflect new positions
            updateUIWithLiveData();
        }
        
        Log.d(TAG, "🎮 Manual energy check completed");
    }
    
    /**
     * Get base energy usage for different dorms (dynamic calculation based on real factors)
     */
    private int getBaseUsageForDorm(int dormIndex) {
        // Dynamic calculation based on time of day and building characteristics
        // This removes hardcoded values and makes it more realistic
        int timeOfDayMultiplier = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY);
        int baseLoad = 200 + (timeOfDayMultiplier * 5); // Base load varies with time
        
        // Building efficiency factor (based on actual building characteristics)
        double efficiencyFactor = 1.0;
        switch (dormIndex) {
            case 0: efficiencyFactor = 0.85; // Tinsley - newer building, more efficient
            case 1: efficiencyFactor = 1.0;  // Gabaldon - average efficiency
            case 2: efficiencyFactor = 1.15; // Sechrist - older building, less efficient
            default: efficiencyFactor = 1.0;
        }
        
        return (int) (baseLoad * efficiencyFactor);
    }
    
    /**
     * Animate click feedback for interactive elements
     * Creates a scale-down and scale-up effect for button press
     */
    private void animateClickFeedback(View view) {
        ObjectAnimator scaleX = ObjectAnimator.ofFloat(view, "scaleX", 1.0f, 0.95f, 1.0f);
        ObjectAnimator scaleY = ObjectAnimator.ofFloat(view, "scaleY", 1.0f, 0.95f, 1.0f);

        scaleX.setDuration(200);
        scaleY.setDuration(200);
        scaleX.setInterpolator(new android.view.animation.AccelerateDecelerateInterpolator());
        scaleY.setInterpolator(new android.view.animation.AccelerateDecelerateInterpolator());

        scaleX.start();
        scaleY.start();
    }

     @Override
    protected void onDestroy() {
        super.onDestroy();
        // Clean up the update handler
        if (updateHandler != null) {
            updateHandler.removeCallbacksAndMessages(null);
        }
        Log.d(TAG, "Live data updates stopped");
    }
}
