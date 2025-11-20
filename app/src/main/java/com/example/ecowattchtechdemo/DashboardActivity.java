package com.example.ecowattchtechdemo;

import android.graphics.Color;
import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import android.view.View;
import android.view.ViewGroup;
import android.content.Intent;
import android.widget.LinearLayout;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import java.util.Random;
import java.util.Map;
import java.text.DecimalFormat;
import android.widget.ImageView;
import android.widget.Button;
import android.widget.TextView;
import android.content.SharedPreferences;
import android.widget.Toast;

// Willow API imports
import com.android.volley.Request;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;
import com.example.ecowattchtechdemo.willow.WillowEnergyDataManager;
import com.example.ecowattchtechdemo.willow.WillowApiV3Config;
import com.example.ecowattchtechdemo.willow.models.EnergyDataResponse;
import com.example.ecowattchtechdemo.gamification.DormPointsManager;
import com.example.ecowattchtechdemo.gamification.EnergyCheckScheduler;

import org.json.JSONException;
import org.json.JSONObject;


public class DashboardActivity extends AppCompatActivity {
    private static final String TAG = "DashboardActivity";
    
    LinearLayout records, shop;
    private DashContentFragment dashContentFragment;
    private Handler updateHandler;
    private DecimalFormat decimalFormat;
    
    // Live data configuration
    private String currentDormName = "TINSLEY";
    // Update intervals for different data types
    private static final long LIVE_ENERGY_INTERVAL = 60000;      // 1 minute - live energy data
    private static final long POTENTIAL_ENERGY_INTERVAL = 3600000; // 1 hour - potential energy (only changes at 10pm)
    private static final long RANKINGS_INTERVAL = 300000;        // 5 minutes - dorm rankings/positions
    
    // Tracking last update times
    private long lastLiveEnergyUpdate = 0;
    private long lastPotentialEnergyUpdate = 0;
    private long lastRankingsUpdate = 0;
    
    // Cached values to avoid redundant API calls
    private String cachedDormPosition = null;
    private int cachedPotentialEnergy = 0;
    
    // Dorm data for rotation
    private String[] dormNames = {"TINSLEY", "GABALDON", "SECHRIST"};
    private String[] dormPositions = {"1ST PLACE", "2ND PLACE", "3RD PLACE"};
    private int currentDormIndex = 0;
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

        // initialize themeManager
        tm = new ThemeManager(this);
    }

    protected void onStart() {
        super.onStart();
        tm.applyTheme();
    }
    
    private void initializeComponents() {
        updateHandler = new Handler(Looper.getMainLooper());
        decimalFormat = new DecimalFormat("#,##0");
        
        // Get user's dorm from SharedPreferences
        SharedPreferences prefs = getSharedPreferences("MyAppPrefs", MODE_PRIVATE);
        String userDorm = prefs.getString("Dormitory", "Tinsley"); // Default to Tinsley
        
        // Set current dorm to user's dorm and find its index
        currentDormName = userDorm.toUpperCase();
        for (int i = 0; i < dormNames.length; i++) {
            if (dormNames[i].equals(currentDormName)) {
                currentDormIndex = i;
                break;
            }
        }
        
        Log.d(TAG, "🏠 User's dorm set to: " + currentDormName + " (Index: " + currentDormIndex + ")");
        
        // Validate BuildConfig environment variables
        Log.d(TAG, "🔐 Environment Variables Status:");
        Log.d(TAG, "  - Willow Base URL: " + (BuildConfig.WILLOW_BASE_URL.isEmpty() ? "❌ Missing" : "✅ Loaded"));
        Log.d(TAG, "  - Client ID: " + (BuildConfig.WILLOW_CLIENT_ID.isEmpty() ? "❌ Missing" : "✅ Loaded (" + BuildConfig.WILLOW_CLIENT_ID.substring(0, 8) + "...)"));
        Log.d(TAG, "  - Client Secret: " + (BuildConfig.WILLOW_CLIENT_SECRET.isEmpty() ? "❌ Missing" : "✅ Loaded"));
        Log.d(TAG, "  - Twin IDs: " + 
              (BuildConfig.TWIN_ID_TINSLEY.isEmpty() ? "❌" : "✅") + " Tinsley, " +
              (BuildConfig.TWIN_ID_GABALDON.isEmpty() ? "❌" : "✅") + " Gabaldon, " +
              (BuildConfig.TWIN_ID_SECHRIST.isEmpty() ? "❌" : "✅") + " Sechrist");
        
        // Initialize Willow API manager
        initializeWillowApi();
        
        // 🎮 Schedule daily energy check at 10 PM
        EnergyCheckScheduler.scheduleDailyEnergyCheck(this);
        
        // 🎯 Daily check-in is now user-triggered through the UI checklist
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
            Log.e(TAG, "❌ Failed to initialize Willow API", e);
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
            Log.e(TAG, "❌ Willow API credentials not found in BuildConfig. Check local.properties file.");
            isWillowAuthenticated = false;
            
            runOnUiThread(() -> {
                if (dashContentFragment != null) {
                    dashContentFragment.updateYesterdaysTotal("API Credentials Missing ❌");
                }
            });
            return;
        }
        
        Log.d(TAG, "🔐 Authenticating with Client ID: " + clientId.substring(0, 8) + "...");
        
        energyDataManager.authenticate(clientId, clientSecret, new WillowEnergyDataManager.AuthenticationCallback() {
            @Override
            public void onSuccess(String token) {
                Log.d(TAG, "✅ Willow API authentication successful!");
                isWillowAuthenticated = true;
                
                runOnUiThread(() -> {
                    // Update UI to indicate real data is being used
                    if (dashContentFragment != null) {
                        dashContentFragment.updateYesterdaysTotal("Connected to Willow API ✅");
                    }
                    
                    // 🚀 INSTANT UPDATE: Force immediate data fetch after successful authentication
                    Log.d(TAG, "🚀 Triggering instant data update after authentication");
                    forceInstantDataUpdate();
                });
            }
            
            @Override
            public void onError(String error) {
                Log.e(TAG, "❌ Willow API authentication failed: " + error);
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
                Log.d(TAG, "🎯 Daily check-in successful for " + currentDormName + ": +25 spendable points");
                
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
    
    private void setupNavigationButtons() {
        records = findViewById(R.id.records_button);
        shop = findViewById(R.id.shop_button);
        hamburgerButton = findViewById(R.id.hamburger_button);

        // Disable Records button (Coming Soon)
        records.setEnabled(false);
        records.setAlpha(0.5f); // Visual indicator that it's disabled

        records.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                // Disabled - Coming Soon
                // Intent intent = new Intent(DashboardActivity.this, RecordsActivity.class);
                // startActivity(intent);
            }
        });

        shop.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent(DashboardActivity.this, ShopActivity.class);
                startActivity(intent);
            }
        });

        // Hamburger button toggles utility modal
        hamburgerButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                toggleModal();
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
        profileLogoutButton.setOnClickListener(v -> logout());

        // Set up tab click listeners
        tabAlerts.setOnClickListener(v -> switchTab(0));
        tabNotifications.setOnClickListener(v -> switchTab(1));
        tabSettings.setOnClickListener(v -> switchTab(2));
        tabProfile.setOnClickListener(v -> switchTab(3));

        // Set up checklist click listeners
        checklistItem1.setOnClickListener(v -> markItemComplete(1));
        checklistItem2.setOnClickListener(v -> markItemComplete(2));
        checklistItem3.setOnClickListener(v -> markItemComplete(3));

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
                checklistItem1.setVisibility(View.INVISIBLE);
                checklistItem2.setVisibility(View.INVISIBLE);
                checklistItem3.setVisibility(View.INVISIBLE);
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
     * Show the utility modal
     */
    private void showModal() {
        isModalOpen = true;
        modalOverlay.setVisibility(View.VISIBLE);

        // Change hamburger icon to close icon
        hamburgerButton.setImageResource(R.drawable.ic_close_vertical);

        // Refresh checklist state when opening modal
        loadChecklistState();

        // Show first tab by default
        switchTab(0);

        Log.d(TAG, "Modal opened");
    }

    /**
     * Hide the utility modal
     */
    private void hideModal() {
        isModalOpen = false;
        modalOverlay.setVisibility(View.GONE);

        // Change close icon back to hamburger icon
        hamburgerButton.setImageResource(R.drawable.ic_hamburger);

        Log.d(TAG, "Modal closed");
    }

    /**
     * Switch between modal tabs
     * @param tabIndex 0=Alerts, 1=Notifications, 2=Settings, 3=Profile
     */
    private void switchTab(int tabIndex) {
        // Hide all tab contents
        tabContentAlerts.setVisibility(View.GONE);
        tabContentNotifications.setVisibility(View.GONE);
        tabContentSettings.setVisibility(View.GONE);
        tabContentProfile.setVisibility(View.GONE);

        // Reset all tab icons to white (inactive)
        tabAlerts.setColorFilter(getResources().getColor(R.color.white, null));
        tabNotifications.setColorFilter(getResources().getColor(R.color.white, null));
        tabSettings.setColorFilter(getResources().getColor(R.color.white, null));
        tabProfile.setColorFilter(getResources().getColor(R.color.white, null));

        // Show selected tab and highlight its icon in red
        switch (tabIndex) {
            case 0: // Daily Tips (formerly Alerts)
                tabContentAlerts.setVisibility(View.VISIBLE);
                tabAlerts.setColorFilter(getResources().getColor(R.color.text_red, null));
                displayRandomTip(); // Refresh tip when tab is opened
                Log.d(TAG, "Switched to Daily Tips tab");
                break;
            case 1: // Notifications
                tabContentNotifications.setVisibility(View.VISIBLE);
                tabNotifications.setColorFilter(getResources().getColor(R.color.text_red, null));
                Log.d(TAG, "Switched to Notifications tab");
                break;
            case 2: // Daily Check-in (formerly Settings)
                tabContentSettings.setVisibility(View.VISIBLE);
                tabSettings.setColorFilter(getResources().getColor(R.color.text_red, null));
                Log.d(TAG, "Switched to Daily Check-in tab");
                break;
            case 3: // Profile
                tabContentProfile.setVisibility(View.VISIBLE);
                tabProfile.setColorFilter(getResources().getColor(R.color.text_red, null));
                Log.d(TAG, "Switched to Profile tab");
                break;
        }
    }

    /**
     * Load user profile information from SharedPreferences or Intent
     */
    private void loadUserProfile() {
        // Get user info from SharedPreferences (saved by LoginFragment/SignupFragment)
        SharedPreferences prefs = getSharedPreferences("MyAppPrefs", MODE_PRIVATE);
        String username = prefs.getString("Username", "Guest");
        String dorm = prefs.getString("Dormitory", "Not Selected");

        // Update the profile UI
        profileUsername.setText(username);
        profileDorm.setText(dorm);

        Log.d(TAG, "Profile loaded - Username: " + username + ", Dorm: " + dorm);
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
                    taskIcon.setTag("accent_color");
                    taskText.setTag("secondary_text");

                    tm.applyTheme();

                    checkForTasksComplete();
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
                    taskIcon.setTag("accent_color");
                    taskText.setTag("secondary_text");

                    tm.applyTheme();

                    checkForTasksComplete();
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
                    taskIcon.setTag("accent_color");
                    taskText.setTag("secondary_text");

                    tm.applyTheme();

                    checkForTasksComplete();
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
                checklistItem1.setVisibility(View.INVISIBLE);
                checklistItem2.setVisibility(View.INVISIBLE);
                checklistItem3.setVisibility(View.INVISIBLE);
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

        // Initialize meter with today's total vs yesterday's total (REAL DATA ONLY)
        meterFill.post(() -> {
            // Use proper daily totals for meter initialization - no fallbacks
            if (energyDataManager != null) {
                DormPointsManager pointsManager = new DormPointsManager(this);
                double todayTotal = pointsManager.getTodayEnergyUsage(currentDormName);
                double yesterdayTotal = pointsManager.getYesterdayEnergyUsage(currentDormName);
                
                int todayForMeter = (int) todayTotal; // Real data only
                int yesterdayForMeter = (int) yesterdayTotal; // Real data only
                
                updateMeter(todayForMeter, yesterdayForMeter);
                Log.d(TAG, "🎯 REAL DATA Meter initialized - Today: " + todayForMeter + " kWh, Yesterday: " + yesterdayForMeter + " kWh");
            } else {
                updateMeter(0, 0); // No data state
            }
        });
    }

    /**
     * Updates the energy meter display with relative scaling
     * Triangle shows yesterday's total as today's goal
     * @param todayTotal Today's accumulated energy total in kWh  
     * @param yesterdayTotal Yesterday's total energy in kWh (triangle position = goal)
     */
    private void updateMeter(int todayTotal, int yesterdayTotal) {
        Log.d(TAG, "🎯 Meter Input - Today: " + todayTotal + " kWh, Yesterday: " + yesterdayTotal + " kWh");
        
        // If no yesterday data, create a reasonable default goal based on today's progress
        int actualYesterdayTotal = yesterdayTotal;
        if (yesterdayTotal <= 0) {
            // Estimate yesterday's total based on current usage pattern
            // Use current hour to estimate what a full day might be
            int currentHour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY);
            if (currentHour < 1) currentHour = 1; // Avoid division by zero
            
            if (todayTotal > 0) {
                // Extrapolate today's total to estimate what yesterday might have been
                actualYesterdayTotal = Math.max(todayTotal, 100); // Minimum 100kWh goal
            } else {
                // Default reasonable goal for a dorm
                actualYesterdayTotal = 200; // 200kWh default goal
            }
            
            Log.d(TAG, "🎯 No yesterday data - using estimated goal: " + actualYesterdayTotal + " kWh");
        }
        
        // Calculate meter percentages - use simple linear scaling with yesterday as reference point
        float maxMeterValue = actualYesterdayTotal * 1.5f; // Meter goes up to 150% of yesterday's total
        float minMeterValue = 0;
        float meterRange = maxMeterValue - minMeterValue;
        
        // Today's progress as percentage of meter (0% = 0 kWh, 100% = 150% of yesterday)
        float todayPercentage = Math.max(0.02f, Math.min(0.98f, todayTotal / meterRange));
        
        // Yesterday's goal position (triangle) - this is our target line
        float thresholdPercentage = Math.max(0.02f, Math.min(0.98f, actualYesterdayTotal / meterRange));
        
        Log.d(TAG, "🎯 Meter Scaling - Max: " + (int)maxMeterValue + " kWh, Today: " + (todayPercentage * 100) + "%, Goal: " + (thresholdPercentage * 100) + "%");

        // Store final values for UI updates
        final float finalTodayPercentage = todayPercentage;
        final float finalThresholdPercentage = thresholdPercentage;
        final int finalActualYesterdayTotal = actualYesterdayTotal; // For color calculation

        // Update meter fill height
        meterFill.post(() -> {
            ViewGroup.LayoutParams params = meterFill.getLayoutParams();
            int meterHeight = meterFill.getParent() != null ?
                ((View) meterFill.getParent()).getHeight() : 0;
            params.height = (int) (meterHeight * finalTodayPercentage);
            meterFill.setLayoutParams(params);

            // Update meter color based on performance vs yesterday's goal
            int color = getMeterColorRelative(todayTotal, finalActualYesterdayTotal);
            android.graphics.drawable.GradientDrawable drawable =
                (android.graphics.drawable.GradientDrawable)
                getResources().getDrawable(R.drawable.meter_fill_shape, null).mutate();
            drawable.setColor(color);
            meterFill.setBackground(drawable);
        });

        // Update threshold indicator position (triangle)
        thresholdIndicator.post(() -> {
            ViewGroup.MarginLayoutParams params =
                (ViewGroup.MarginLayoutParams) thresholdIndicator.getLayoutParams();
            int meterHeight = thresholdIndicator.getParent() != null ?
                ((View) thresholdIndicator.getParent()).getHeight() : 0;

            // Position from bottom (inverse of percentage)
            int marginBottom = (int) (meterHeight * finalThresholdPercentage) -
                (thresholdIndicator.getHeight() / 2);
            params.bottomMargin = marginBottom;
            params.topMargin = 0;
            thresholdIndicator.setLayoutParams(params);
            
            Log.d(TAG, "🎯 Triangle positioned at " + (finalThresholdPercentage * 100) + "% (Goal: " + finalActualYesterdayTotal + " kWh, Margin: " + marginBottom + "px)");
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
     * Green = Meeting or beating yesterday's goal, Yellow = Close to goal, Red = Exceeding goal (using more energy)
     */
    private int getMeterColorRelative(int todayTotal, int yesterdayGoal) {
        if (yesterdayGoal <= 0) {
            // No comparison data - use neutral blue
            return Color.rgb(100, 150, 255);
        }
        
        float ratio = (float) todayTotal / (float) yesterdayGoal;
        
        if (ratio <= 0.9f) {
            // Much better than yesterday (using less energy) - Bright Green
            return Color.rgb(76, 175, 80);
        } else if (ratio <= 1.1f) {
            // Close to yesterday's usage (within 10%) - Yellow/Orange
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
        String twinId = getCurrentBuildingTwinId();
        
        Log.d(TAG, "🌐 Fetching live energy data for " + currentDormName + " (Twin: " + twinId + ")");
        
        energyDataManager.getEnergyData(twinId, new WillowEnergyDataManager.EnergyDataCallback() {
            @Override
            public void onSuccess(EnergyDataResponse data) {
                runOnUiThread(() -> {
                    updateUIWithRealData(data, updatePotentialEnergy, updateRankings);
                });
            }
            
            @Override
            public void onError(String error) {
                Log.e(TAG, "❌ Failed to fetch real data: " + error);
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
        
        Log.d(TAG, "🌐 Live: " + liveUsage + "kW (" + data.getBuildingName() + ")");

        // Create DormPointsManager instance for energy tracking
        DormPointsManager pointsManager = new DormPointsManager(this);

        // 📊 Record today's energy usage for historical tracking
        if (data.getDailyTotalKWh() != null) {
            // Use the proper accumulation method to ensure daily total only increases
            pointsManager.updateTodayEnergyUsageIfHigher(data.getBuildingName(), data.getDailyTotalAsInt());
            Log.v(TAG, "📊 Energy recorded: " + data.getDailyTotalAsInt() + " kWh");
        }

        // Update current usage display (instantaneous kW)
        dashContentFragment.updateCurrentUsage(liveUsage + "kW");
        
        // 🎯 Update the energy meter with TODAY'S TOTAL vs YESTERDAY'S TOTAL (REAL DATA ONLY)
        double todayTotal = pointsManager.getTodayEnergyUsage(data.getBuildingName());
        double yesterdayTotal = pointsManager.getYesterdayEnergyUsage(data.getBuildingName());
        
        // Ensure today's total shows accumulated energy, not just live usage
        int todayForMeter;
        if (todayTotal > 0) {
            // Use stored accumulated total
            todayForMeter = (int) todayTotal;
            Log.d(TAG, "🎯 Using stored today total: " + todayForMeter + " kWh");
        } else if (data.getDailyTotalKWh() != null && data.getDailyTotalAsInt() > 0) {
            // Fallback to API daily total if available
            todayForMeter = data.getDailyTotalAsInt();
            Log.d(TAG, "🎯 Using API daily total: " + todayForMeter + " kWh");
        } else {
            // Estimate based on current usage (assume it's been running for a few hours)
            int hoursAssumed = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY);
            if (hoursAssumed < 1) hoursAssumed = 1; // Avoid division by zero
            todayForMeter = liveUsage * hoursAssumed; // Rough estimate
            Log.d(TAG, "🎯 Using estimated total: " + todayForMeter + " kWh (based on " + liveUsage + "kW for " + hoursAssumed + "h)");
        }
        
        int yesterdayForMeter = (int) yesterdayTotal; // Use actual value, 0 if no data
        
        updateMeter(todayForMeter, yesterdayForMeter);
        Log.d(TAG, "🎯 METER BAR: Today " + todayForMeter + " kWh, Yesterday " + yesterdayForMeter + " kWh");
        
        // 🎮 Update dorm status/position (conditional based on ranking update schedule)
        if (updateRankings) {
            String position = energyDataManager.getBuildingPosition(data.getBuildingName());
            cachedDormPosition = position;
            Log.d(TAG, "🏆 Rankings updated - Position: " + position);
        }
        String statusText = data.getBuildingName() + " - " + (cachedDormPosition != null ? cachedDormPosition : "LIVE DATA");
        dashContentFragment.updateDormStatus(statusText);
        
        // 🎮 Update potential energy (conditional - only updates at 10pm daily)
        if (updatePotentialEnergy) {
            cachedPotentialEnergy = energyDataManager.getDormPotentialEnergy(data.getBuildingName());
            Log.d(TAG, "⚡ Potential energy updated: " + cachedPotentialEnergy + " points");
        }
        dashContentFragment.updatePotentialEnergy(cachedPotentialEnergy + " Potential Energy");
        
        // 🎮 Enhanced display: Today's Total, Yesterday's Total, and Emissions
        StringBuilder enhancedDisplayText = new StringBuilder();
        
        // Today's Total (use same logic as meter for consistency)
        double todayStoredTotal = pointsManager.getTodayEnergyUsage(data.getBuildingName());
        int todayDisplayTotal;
        String todayLabel;
        
        if (todayStoredTotal > 0) {
            todayDisplayTotal = (int) todayStoredTotal;
            todayLabel = "Today's Total: " + decimalFormat.format(todayDisplayTotal) + "kWh";
        } else if (data.getDailyTotalKWh() != null && data.getDailyTotalAsInt() > 0) {
            todayDisplayTotal = data.getDailyTotalAsInt();
            todayLabel = "Today's Total: " + decimalFormat.format(todayDisplayTotal) + "kWh";
        } else {
            // Use same estimation logic as meter
            int hoursAssumed = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY);
            if (hoursAssumed < 1) hoursAssumed = 1;
            todayDisplayTotal = liveUsage * hoursAssumed;
            todayLabel = "Today's Total: " + decimalFormat.format(todayDisplayTotal) + "kWh (Est. " + hoursAssumed + "h)";
        }
        
        enhancedDisplayText.append(todayLabel);
        
        // Yesterday's Total (get from DormPointsManager where data is stored)
        double yesterdayUsage = pointsManager.getYesterdayEnergyUsage(data.getBuildingName());
        Log.d(TAG, "🔍 DEBUG Yesterday data: " + yesterdayUsage + " kWh for " + data.getBuildingName());
        
        if (yesterdayUsage > 0) {
            enhancedDisplayText.append("\nYesterday's Total: ")
                             .append(decimalFormat.format((int)yesterdayUsage))
                             .append("kWh");
        } else {
            enhancedDisplayText.append("\nYesterday's Total: No data available");
            Log.w(TAG, "🔍 Yesterday data missing for " + data.getBuildingName() + " - checking storage...");
            
            // Debug: Check what data exists in SharedPreferences
            SharedPreferences prefs = getSharedPreferences("points_data", MODE_PRIVATE);
            java.text.SimpleDateFormat dateFormat = new java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US);
            String yesterday = dateFormat.format(new java.util.Date(System.currentTimeMillis() - 24 * 60 * 60 * 1000));
            String key = "today_energy_" + data.getBuildingName() + "_" + yesterday;
            float storedValue = prefs.getFloat(key, -1f);
            
            Log.w(TAG, "🔍 Searched key: " + key + " = " + storedValue);
            
            // Show all keys containing this building name
            java.util.Map<String, ?> allPrefs = prefs.getAll();
            for (java.util.Map.Entry<String, ?> entry : allPrefs.entrySet()) {
                if (entry.getKey().contains(data.getBuildingName())) {
                    Log.d(TAG, "🔍 Found data: " + entry.getKey() + " = " + entry.getValue());
                }
            }
        }
        
        // Emissions Calculation (use same total as meter and display for consistency)
        double todayEmissions = todayDisplayTotal * 0.85; // lbs of CO2 per kWh
        
        enhancedDisplayText.append("\nToday's Emissions: ")
                         .append(decimalFormat.format(todayEmissions))
                         .append(" lbs CO₂");
        
        dashContentFragment.updateYesterdaysTotal(enhancedDisplayText.toString());
        
        Log.v(TAG, "✅ UI updated - Next live energy update in " + (LIVE_ENERGY_INTERVAL/1000) + "s");
    }

    /**
     * Backward compatibility wrapper for old method signature
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
     * Get current building twin ID based on rotation
     */
    private String getCurrentBuildingTwinId() {
        switch (currentDormIndex) {
            case 0: return WillowApiV3Config.TWIN_ID_TINSLEY;
            case 1: return WillowApiV3Config.TWIN_ID_GABALDON;
            case 2: return WillowApiV3Config.TWIN_ID_SECHRIST;
            default: return WillowApiV3Config.TWIN_ID_TINSLEY;
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
