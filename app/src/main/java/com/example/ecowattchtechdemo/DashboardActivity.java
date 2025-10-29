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
import java.text.DecimalFormat;
import android.widget.ImageView;
import android.widget.Button;
import android.widget.TextView;
import android.content.SharedPreferences;

// Willow API imports
import com.example.ecowattchtechdemo.willow.WillowEnergyDataManager;
import com.example.ecowattchtechdemo.willow.WillowApiV3Config;
import com.example.ecowattchtechdemo.willow.models.EnergyDataResponse;

public class DashboardActivity extends AppCompatActivity {
    private static final String TAG = "DashboardActivity";
    
    LinearLayout records, shop;
    private DashContentFragment dashContentFragment;
    private Handler updateHandler;
    private Random random;
    private DecimalFormat decimalFormat;
    
    // Live data configuration
    private String currentDormName = "TINSLEY";
    private static final long UPDATE_INTERVAL = 10000; // 10 seconds
    
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
    private Button profileLogoutButton;

    // Daily Tips component
    private TextView dailyTipText;
    private String[] environmentalTips;
    
    // Willow API integration
    private WillowEnergyDataManager energyDataManager;
    private boolean isWillowAuthenticated = false;
    private boolean useRealData = false; // Toggle between real and simulated data

    // Meter components
    View meterFill;
    ImageView thresholdIndicator;

    // Meter configuration
    private static final int MAX_USAGE = 600; // 600kw max
    private static final int MIN_USAGE = 0;   // 0kw min
    private int currentUsage = 0;  // Initial value, updated by live data
    private int thresholdValue = 300; // Threshold at 300kw

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
        random = new Random();
        decimalFormat = new DecimalFormat("#,##0");
        
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
        
        Log.d(TAG, "Live data system initialized");
    }
    
    /**
     * Initialize Willow API integration
     */
    private void initializeWillowApi() {
        try {
            energyDataManager = new WillowEnergyDataManager();
            
            // Try to authenticate with stored credentials
            authenticateWithWillow();
            
        } catch (Exception e) {
            Log.w(TAG, "Failed to initialize Willow API, falling back to simulated data", e);
            useRealData = false;
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
            useRealData = false;
            
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
                useRealData = true;
                
                runOnUiThread(() -> {
                    // Update UI to indicate real data is being used
                    if (dashContentFragment != null) {
                        dashContentFragment.updateYesterdaysTotal("Connected to Willow API ✅");
                    }
                });
            }
            
            @Override
            public void onError(String error) {
                Log.w(TAG, "❌ Willow API authentication failed: " + error);
                isWillowAuthenticated = false;
                useRealData = false;
                
                runOnUiThread(() -> {
                    // Update UI to indicate simulated data
                    if (dashContentFragment != null) {
                        dashContentFragment.updateYesterdaysTotal("Using Simulated Data 🔄");
                    }
                });
            }
        });
    }
    
    private void setupNavigationButtons() {
        records = findViewById(R.id.records_button);
        shop = findViewById(R.id.shop_button);
        hamburgerButton = findViewById(R.id.hamburger_button);

        records.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent(DashboardActivity.this, RecordsActivity.class);
                startActivity(intent);
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

        // Add long press listener to access Willow API test interface
        hamburgerButton.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                Intent intent = new Intent(DashboardActivity.this, WillowApiV3TestActivity.class);
                startActivity(intent);
                return true;
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

        // Set up overlay click listener to close modal when clicking outside
        modalOverlay.setOnClickListener(v -> {
            // Only close if clicking directly on overlay (not on modal content)
            if (v.getId() == R.id.modal_overlay) {
                hideModal();
            }
        });
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

                    // mark complete on screen
                    taskIcon.setImageResource(android.R.drawable.checkbox_on_background);
                    taskIcon.setTag("primary_color");
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

                    // mark complete on screen
                    taskIcon.setImageResource(android.R.drawable.checkbox_on_background);
                    taskIcon.setTag("primary_color");
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

                    // mark complete on screen
                    taskIcon.setImageResource(android.R.drawable.checkbox_on_background);
                    taskIcon.setTag("primary_color");
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

        // backend - get allTasks from db

        if (task1 && task2 && task3) {
            allTasks = true;
            prefs.edit()
                    .putBoolean("all_tasks", true)
                    .apply();

            // backend - update db to say user completed all tasks for the day
        }
        if (allTasks) {
            TextView tasksCompleted = findViewById(R.id.tasks_completed);
            tasksCompleted.setVisibility(View.VISIBLE);

            checklistItem1.setVisibility(View.INVISIBLE);
            checklistItem2.setVisibility(View.INVISIBLE);
            checklistItem3.setVisibility(View.INVISIBLE);

            // backend - add user points
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

        // Initialize meter with default value
        meterFill.post(() -> {
            updateMeter(currentUsage, thresholdValue);
        });
    }

    /**
     * Updates the energy meter display
     * @param usage Current energy usage in kw (0-400)
     * @param threshold Threshold value in kw
     */
    private void updateMeter(int usage, int threshold) {
        // Calculate meter fill height as percentage
        float usagePercentage = ((float) usage / MAX_USAGE);
        float thresholdPercentage = ((float) threshold / MAX_USAGE);

        // Update meter fill height
        meterFill.post(() -> {
            ViewGroup.LayoutParams params = meterFill.getLayoutParams();
            int meterHeight = meterFill.getParent() != null ?
                ((View) meterFill.getParent()).getHeight() : 0;
            params.height = (int) (meterHeight * usagePercentage);
            meterFill.setLayoutParams(params);

            // Update meter color based on usage (green to red gradient)
            // Use drawable background to maintain rounded corners
            int color = getMeterColor(usagePercentage);
            android.graphics.drawable.GradientDrawable drawable =
                (android.graphics.drawable.GradientDrawable)
                getResources().getDrawable(R.drawable.meter_fill_shape, null).mutate();
            drawable.setColor(color);
            meterFill.setBackground(drawable);
        });

        // Update threshold indicator position
        thresholdIndicator.post(() -> {
            ViewGroup.MarginLayoutParams params =
                (ViewGroup.MarginLayoutParams) thresholdIndicator.getLayoutParams();
            int meterHeight = thresholdIndicator.getParent() != null ?
                ((View) thresholdIndicator.getParent()).getHeight() : 0;

            // Position from bottom (inverse of percentage)
            int marginBottom = (int) (meterHeight * thresholdPercentage) -
                (thresholdIndicator.getHeight() / 2);
            params.bottomMargin = marginBottom;
            params.topMargin = 0;
            thresholdIndicator.setLayoutParams(params);
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
     * Start the live data update cycle
     */
    private void startLiveDataUpdates() {
        Log.d(TAG, "Starting live data updates with " + UPDATE_INTERVAL + "ms interval");
        
        // Show initial data immediately
        updateHandler.post(() -> {
            if (dashContentFragment != null && dashContentFragment.isAdded()) {
                updateUIWithLiveData();
            } else {
                // Retry after a short delay if fragment isn't ready
                updateHandler.postDelayed(() -> updateUIWithLiveData(), 200);
            }
        });
        
        // Schedule recurring updates
        scheduleNextUpdate();
    }
    
    /**
     * Schedule the next data update
     */
    private void scheduleNextUpdate() {
        updateHandler.postDelayed(() -> {
            updateUIWithLiveData();
            scheduleNextUpdate(); // Schedule the next update
        }, UPDATE_INTERVAL);
    }
    
    /**
     * Update UI with fresh live data (real or simulated)
     */
    private void updateUIWithLiveData() {
        if (dashContentFragment == null || !dashContentFragment.isAdded()) {
            Log.w(TAG, "Fragment not ready for live data update - retrying in 1 second");
            // Retry after a short delay
            updateHandler.postDelayed(() -> updateUIWithLiveData(), 1000);
            return;
        }

        // Rotate through dorms every few updates
        rotateDorm();

        if (useRealData && isWillowAuthenticated) {
            // Fetch real data from Willow API
            fetchRealEnergyData();
        } else {
            // Use simulated data
            updateWithSimulatedData();
        }
    }
    
    /**
     * Fetch real energy data from Willow API
     */
    private void fetchRealEnergyData() {
        String twinId = getCurrentBuildingTwinId();
        
        Log.d(TAG, "🌐 Fetching REAL energy data for " + currentDormName + " (Twin ID: " + twinId + ")");
        
        energyDataManager.getEnergyData(twinId, new WillowEnergyDataManager.EnergyDataCallback() {
            @Override
            public void onSuccess(EnergyDataResponse data) {
                runOnUiThread(() -> {
                    updateUIWithRealData(data);
                });
            }
            
            @Override
            public void onError(String error) {
                Log.w(TAG, "Failed to fetch real data, falling back to simulated: " + error);
                runOnUiThread(() -> {
                    // Fallback to simulated data
                    updateWithSimulatedData();
                });
            }
        });
    }
    
    /**
     * Update UI with real energy data from Willow API
     */
    private void updateUIWithRealData(EnergyDataResponse data) {
        int liveUsage = data.getCurrentUsageAsInt();
        
        // Update the instance variable for meter updates
        this.currentUsage = liveUsage;
        
        Log.d(TAG, "🌐 REAL DATA UPDATE: " + liveUsage + "kW for " + data.getBuildingName() + 
              " (Status: " + data.getStatus() + ")");
        
        // Update current usage (main display)
        dashContentFragment.updateCurrentUsage(liveUsage + "kW");
        
        // Update the energy meter with real usage
        updateMeter(liveUsage, thresholdValue);
        
        // Update dorm status with position
        String position = energyDataManager.getBuildingPosition(data.getBuildingName());
        String statusText = data.getBuildingName() + " - " + (position != null ? position : "LIVE DATA");
        dashContentFragment.updateDormStatus(statusText);
        
        // Use real potential energy if available
        if (data.getPotentialEnergy() != null) {
            dashContentFragment.updatePotentialEnergy(data.getPotentialEnergy().intValue() + " Potential Energy");
        } else {
            // Calculate potential energy based on real usage
            int potentialEnergy = Math.max(0, 300 - (liveUsage - 200));
            dashContentFragment.updatePotentialEnergy(potentialEnergy + " Potential Energy");
        }
        
        // Display real daily total or calculated value
        String dailyTotalText;
        if (data.getDailyTotalKWh() != null) {
            dailyTotalText = "Today's Total: " + decimalFormat.format(data.getDailyTotalAsInt()) + "kWh (Real Data ✅)";
        } else {
            int calculatedTotal = liveUsage * 24;
            dailyTotalText = "Estimated Daily: " + decimalFormat.format(calculatedTotal) + "kWh (Calculated)";
        }
        dashContentFragment.updateYesterdaysTotal(dailyTotalText);
        
        Log.d(TAG, "✅ Real data update completed successfully - Next update in " + (UPDATE_INTERVAL/1000) + " seconds");
    }
    
    /**
     * Update UI with simulated data (fallback)
     */
    private void updateWithSimulatedData() {
        // Generate realistic energy usage data for current dorm
        int baseUsage = getBaseUsageForDorm(currentDormIndex);
        int liveUsage = baseUsage + random.nextInt(50) - 25; // ±25kW variation

        // Update the instance variable for meter updates
        this.currentUsage = liveUsage;

        Log.d(TAG, "🔄 SIMULATED UPDATE: " + liveUsage + "kW for " + currentDormName + " (Position: " + dormPositions[currentDormIndex] + ")");

        // Update current usage (main display)
        dashContentFragment.updateCurrentUsage(liveUsage + "kW");

        // Update the energy meter with new usage
        updateMeter(liveUsage, thresholdValue);

        // Update dorm status with position
        String statusText = currentDormName + " - " + dormPositions[currentDormIndex];
        dashContentFragment.updateDormStatus(statusText);

        // Calculate potential energy based on efficiency (dynamic calculation)
        int optimalUsage = (int) (baseUsage * 0.8); // 80% of base usage is optimal
        int potentialEnergy = Math.max(0, (liveUsage > optimalUsage) ? 
            (int)((double)(liveUsage - optimalUsage) / liveUsage * 1000) : 1000);
        dashContentFragment.updatePotentialEnergy(potentialEnergy + " Potential Energy");

        // Simulate yesterday's total
        int yesterdayTotal = liveUsage * 24 + random.nextInt(1000);
        dashContentFragment.updateYesterdaysTotal("Yesterday's Total: " + decimalFormat.format(yesterdayTotal) + "kWh (Simulated 🔄)");

        Log.d(TAG, "✅ Simulated data update completed successfully - Next update in " + (UPDATE_INTERVAL/1000) + " seconds");
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
     * Rotate through different dorms every few updates
     */
    private void rotateDorm() {
        // Change dorm every 2 updates (approximately every 20 seconds) - more frequent for testing
        if (random.nextInt(2) == 0) {
            currentDormIndex = (currentDormIndex + 1) % dormNames.length;
            currentDormName = dormNames[currentDormIndex];
            Log.d(TAG, "🏠 Rotated to dorm: " + currentDormName + " (Index: " + currentDormIndex + ")");
        }
    }
    
    /**
     * Manual refresh - switches to next dorm and updates data immediately
     */
    public void manualRefresh() {
        Log.d(TAG, "Manual refresh requested");
        
        // Switch to next dorm
        currentDormIndex = (currentDormIndex + 1) % dormNames.length;
        currentDormName = dormNames[currentDormIndex];
        
        // Update UI immediately
        updateUIWithLiveData();
        
        Log.d(TAG, "Manual refresh completed - switched to: " + currentDormName);
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
