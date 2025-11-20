package com.example.ecowattchtechdemo;

import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

public class RecordsActivity extends AppCompatActivity {

    // UI Components
    private TextView backButton;
    private LinearLayout barChartContainer;
    private LinearLayout allTimeRankingsContainer;
    private LinearLayout streaksRankingsContainer;
    private TextView selectedDayEnergyLabel;

    private TextView firstPlaceDorm;
    private TextView secondPlaceDorm;
    private TextView thirdPlaceDorm;

    // Day selection
    private int selectedDayIndex = 3; // Default: Wednesday (index 3)
    private int currentDayIndex; // Current day of the week (0 = Sunday, 6 = Saturday)
    private String[] dayNames = {"sun", "mon", "tue", "wed", "thu", "fri", "sat"};
    private String[] dayNamesFull = {"Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday"};

    // Sample data - will be replaced with backend API calls
    private List<DailyEnergyData> weeklyEnergyData;
    private List<LeaderboardEntry> allTimeLeaderboard;
    private List<StreakEntry> streaksLeaderboard;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_records);

        // Initialize UI components
        initializeViews();

        // Get current day of week (0 = Sunday, 6 = Saturday)
        Calendar calendar = Calendar.getInstance();
        currentDayIndex = calendar.get(Calendar.DAY_OF_WEEK) - 1; // Convert to 0-based index

        // Load sample data
        // TODO: BACKEND - Replace with API calls to fetch real data
        loadSampleData();

        // Setup UI
        setupBarChart();
        setupPodium();
        populateAllTimeLeaderboard();
        populateStreaksLeaderboard();

        // Update energy label for initial selected day
        updateEnergyLabel();

        // Setup back button
        backButton.setOnClickListener(view -> finish());
    }

    /**
     * Initialize all view components
     */
    private void initializeViews() {
        backButton = findViewById(R.id.back_button);
        barChartContainer = findViewById(R.id.bar_chart_container);
        allTimeRankingsContainer = findViewById(R.id.all_time_rankings_container);
        streaksRankingsContainer = findViewById(R.id.streaks_rankings_container);
        selectedDayEnergyLabel = findViewById(R.id.selected_day_energy_label);

        firstPlaceDorm = findViewById(R.id.first_place_dorm);
        secondPlaceDorm = findViewById(R.id.second_place_dorm);
        thirdPlaceDorm = findViewById(R.id.third_place_dorm);
    }

    /**
     * Handle day selection with visual feedback
     */
    private void selectDay(int dayIndex) {
        selectedDayIndex = dayIndex;
        // Update bar chart to reflect selection
        updateBarChart();
        // Update energy label for selected day
        updateEnergyLabel();
    }

    /**
     * Update the energy usage label for the selected day
     */
    private void updateEnergyLabel() {
        if (selectedDayIndex >= 0 && selectedDayIndex < weeklyEnergyData.size()) {
            DailyEnergyData selectedData = weeklyEnergyData.get(selectedDayIndex);
            selectedDayEnergyLabel.setText(String.format("%s: %,d kWh",
                dayNamesFull[selectedDayIndex], selectedData.energyUsageKwh));
        }
    }

    /**
     * Create and display the bar chart for weekly energy usage
     */
    private void setupBarChart() {
        updateBarChart();
    }

    /**
     * Update bar chart based on current data
     */
    private void updateBarChart() {
        barChartContainer.removeAllViews();

        // Find max energy value for scaling
        int maxEnergy = 0;
        for (DailyEnergyData data : weeklyEnergyData) {
            if (data.energyUsageKwh > maxEnergy) {
                maxEnergy = data.energyUsageKwh;
            }
        }

        // Create bars for each day
        for (int i = 0; i < weeklyEnergyData.size(); i++) {
            final int dayIndex = i;
            DailyEnergyData data = weeklyEnergyData.get(i);

            // Create bar container (holds indicator, bar, and day label)
            LinearLayout barContainer = new LinearLayout(this);
            LinearLayout.LayoutParams containerParams = new LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.MATCH_PARENT,
                1.0f
            );
            barContainer.setLayoutParams(containerParams);
            barContainer.setOrientation(LinearLayout.VERTICAL);
            barContainer.setGravity(android.view.Gravity.BOTTOM | android.view.Gravity.CENTER_HORIZONTAL);
            barContainer.setPadding(2, 0, 2, 0);

            // Make entire container clickable
            barContainer.setClickable(true);
            barContainer.setOnClickListener(v -> selectDay(dayIndex));

            // Calculate bar height (percentage of max)
            int maxBarHeight = 120; // dp - reduced to fit with day label
            float heightPercentage = (float) data.energyUsageKwh / maxEnergy;
            int barHeight = (int) (maxBarHeight * heightPercentage);

            // Create the bar with rounded background
            LinearLayout barWrapper = new LinearLayout(this);
            LinearLayout.LayoutParams barWrapperParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                (int) (barHeight * getResources().getDisplayMetrics().density)
            );
            barWrapperParams.setMargins(
                (int) (4 * getResources().getDisplayMetrics().density),
                (int) (4 * getResources().getDisplayMetrics().density),
                (int) (4 * getResources().getDisplayMetrics().density),
                (int) (4 * getResources().getDisplayMetrics().density)
            );
            barWrapper.setLayoutParams(barWrapperParams);

            // Create rounded bar background
            GradientDrawable barDrawable = new GradientDrawable();
            barDrawable.setShape(GradientDrawable.RECTANGLE);
            barDrawable.setCornerRadius(12f);

            // Color based on selection
            if (i == selectedDayIndex) {
                barDrawable.setColor(ContextCompat.getColor(this, R.color.text_red));
            } else {
                barDrawable.setColor(ContextCompat.getColor(this, R.color.button_background));
            }

            barWrapper.setBackground(barDrawable);
            barContainer.addView(barWrapper);

            // Add day label below the bar
            TextView dayLabel = new TextView(this);
            dayLabel.setText(dayNames[i]);
            dayLabel.setTextSize(12);
            dayLabel.setGravity(android.view.Gravity.CENTER);
            dayLabel.setTypeface(getResources().getFont(R.font.matrixtype_display));

            LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            );
            labelParams.setMargins(0, (int) (4 * getResources().getDisplayMetrics().density), 0, 0);
            dayLabel.setLayoutParams(labelParams);

            // Color label based on current day (not selection)
            // Current day is always red, other days are dimmed white
            if (i == currentDayIndex) {
                dayLabel.setTextColor(ContextCompat.getColor(this, R.color.text_red));
                dayLabel.setAlpha(1.0f);
            } else {
                dayLabel.setTextColor(ContextCompat.getColor(this, R.color.white));
                dayLabel.setAlpha(0.6f);
            }

            barContainer.addView(dayLabel);
            barChartContainer.addView(barContainer);
        }
    }

    /**
     * Setup the podium with top 3 dorms
     */
    private void setupPodium() {
        // TODO: BACKEND - Replace with API call to fetch current top 3 dorms
        if (allTimeLeaderboard.size() >= 3) {
            LeaderboardEntry first = allTimeLeaderboard.get(0);
            LeaderboardEntry second = allTimeLeaderboard.get(1);
            LeaderboardEntry third = allTimeLeaderboard.get(2);

            firstPlaceDorm.setText(first.dormName);
            secondPlaceDorm.setText(second.dormName);
            thirdPlaceDorm.setText(third.dormName);
        }
    }

    /**
     * Populate the all-time leaderboard rankings
     */
    private void populateAllTimeLeaderboard() {
        allTimeRankingsContainer.removeAllViews();

        for (int i = 0; i < allTimeLeaderboard.size(); i++) {
            LeaderboardEntry entry = allTimeLeaderboard.get(i);

            // Create horizontal layout for each ranking item
            LinearLayout itemLayout = new LinearLayout(this);
            itemLayout.setOrientation(LinearLayout.HORIZONTAL);
            LinearLayout.LayoutParams itemParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            );
            itemParams.setMargins(0, 0, 0, (int) (12 * getResources().getDisplayMetrics().density));
            itemLayout.setLayoutParams(itemParams);

            // Left side: rank and name
            TextView leftText = new TextView(this);
            leftText.setText(String.format("%d. %s", i + 1, entry.dormName));
            leftText.setTextColor(ContextCompat.getColor(this, R.color.white));
            leftText.setTextSize(13);
            leftText.setTypeface(getResources().getFont(R.font.matrixtype_display));
            LinearLayout.LayoutParams leftParams = new LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1.0f
            );
            leftText.setLayoutParams(leftParams);

            // Right side: potential energy
            TextView rightText = new TextView(this);
            rightText.setText(String.format("%d potential energy", entry.potentialEnergy));
            rightText.setTextColor(ContextCompat.getColor(this, R.color.white));
            rightText.setTextSize(13);
            rightText.setTypeface(getResources().getFont(R.font.matrixtype_display));
            rightText.setGravity(android.view.Gravity.END);
            LinearLayout.LayoutParams rightParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            );
            rightText.setLayoutParams(rightParams);

            itemLayout.addView(leftText);
            itemLayout.addView(rightText);
            allTimeRankingsContainer.addView(itemLayout);
        }
    }

    /**
     * Populate the user streaks leaderboard
     */
    private void populateStreaksLeaderboard() {
        streaksRankingsContainer.removeAllViews();

        for (int i = 0; i < streaksLeaderboard.size(); i++) {
            StreakEntry entry = streaksLeaderboard.get(i);

            // Create horizontal layout for each streak item
            LinearLayout itemLayout = new LinearLayout(this);
            itemLayout.setOrientation(LinearLayout.HORIZONTAL);
            LinearLayout.LayoutParams itemParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            );
            itemParams.setMargins(0, 0, 0, (int) (12 * getResources().getDisplayMetrics().density));
            itemLayout.setLayoutParams(itemParams);

            // Left side: rank and username
            TextView leftText = new TextView(this);
            leftText.setText(String.format("%d. %s", i + 1, entry.userName));
            leftText.setTextColor(ContextCompat.getColor(this, R.color.white));
            leftText.setTextSize(13);
            leftText.setTypeface(getResources().getFont(R.font.matrixtype_display));
            LinearLayout.LayoutParams leftParams = new LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1.0f
            );
            leftText.setLayoutParams(leftParams);

            // Right side: streak days
            TextView rightText = new TextView(this);
            rightText.setText(String.format("%d day streak", entry.streakDays));
            rightText.setTextColor(ContextCompat.getColor(this, R.color.white));
            rightText.setTextSize(13);
            rightText.setTypeface(getResources().getFont(R.font.matrixtype_display));
            rightText.setGravity(android.view.Gravity.END);
            LinearLayout.LayoutParams rightParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            );
            rightText.setLayoutParams(rightParams);

            itemLayout.addView(leftText);
            itemLayout.addView(rightText);
            streaksRankingsContainer.addView(itemLayout);
        }
    }

    /**
     * Load sample data for demonstration
     * TODO: BACKEND - Replace all sample data loading with API calls
     */
    private void loadSampleData() {
        // Sample weekly energy data (7 days)
        weeklyEnergyData = new ArrayList<>();
        weeklyEnergyData.add(new DailyEnergyData("Sunday", 1850));
        weeklyEnergyData.add(new DailyEnergyData("Monday", 2100));
        weeklyEnergyData.add(new DailyEnergyData("Tuesday", 2300));
        weeklyEnergyData.add(new DailyEnergyData("Wednesday", 2650)); // Highest usage
        weeklyEnergyData.add(new DailyEnergyData("Thursday", 1950));
        weeklyEnergyData.add(new DailyEnergyData("Friday", 1700));
        weeklyEnergyData.add(new DailyEnergyData("Saturday", 1600));

        // Sample all-time leaderboard data
        allTimeLeaderboard = new ArrayList<>();
        allTimeLeaderboard.add(new LeaderboardEntry(1, "Tinsley", 2400));
        allTimeLeaderboard.add(new LeaderboardEntry(2, "Allen", 2340));
        allTimeLeaderboard.add(new LeaderboardEntry(3, "Sechrist", 1860));
        allTimeLeaderboard.add(new LeaderboardEntry(4, "Taylor", 1600));
        allTimeLeaderboard.add(new LeaderboardEntry(5, "Wilson", 1430));
        allTimeLeaderboard.add(new LeaderboardEntry(6, "Cowden", 1200));

        // Sample streaks leaderboard data (expanded for scrolling test)
        streaksLeaderboard = new ArrayList<>();
        streaksLeaderboard.add(new StreakEntry("Sarah_T", 21));
        streaksLeaderboard.add(new StreakEntry("Mike_A", 18));
        streaksLeaderboard.add(new StreakEntry("Emma_S", 16));
        streaksLeaderboard.add(new StreakEntry("Jake_M", 14));
        streaksLeaderboard.add(new StreakEntry("Lily_W", 12));
        streaksLeaderboard.add(new StreakEntry("Alex_R", 11));
        streaksLeaderboard.add(new StreakEntry("Jordan_K", 10));
        streaksLeaderboard.add(new StreakEntry("Casey_L", 9));
        streaksLeaderboard.add(new StreakEntry("Morgan_B", 8));
        streaksLeaderboard.add(new StreakEntry("Riley_H", 7));
        streaksLeaderboard.add(new StreakEntry("Avery_P", 6));
        streaksLeaderboard.add(new StreakEntry("Quinn_D", 5));
        streaksLeaderboard.add(new StreakEntry("Taylor_M", 5));
        streaksLeaderboard.add(new StreakEntry("Sam_V", 4));
        streaksLeaderboard.add(new StreakEntry("Chris_N", 3));
    }

    // ==================== Data Models ====================
    // TODO: BACKEND - Move these to separate model files when implementing API integration

    /**
     * Data model for daily energy usage
     * Backend endpoint: GET /api/energy/weekly?dormId={dormId}
     * Expected response: List of DailyEnergyData objects with dayName and energyUsageKwh
     */
    public static class DailyEnergyData {
        public String dayName;
        public int energyUsageKwh;

        public DailyEnergyData(String dayName, int energyUsageKwh) {
            this.dayName = dayName;
            this.energyUsageKwh = energyUsageKwh;
        }
    }

    /**
     * Data model for leaderboard entries
     * Backend endpoint: GET /api/leaderboard/alltime
     * Expected response: List of LeaderboardEntry objects sorted by rank
     */
    public static class LeaderboardEntry {
        public int rank;
        public String dormName;
        public int potentialEnergy;

        public LeaderboardEntry(int rank, String dormName, int potentialEnergy) {
            this.rank = rank;
            this.dormName = dormName;
            this.potentialEnergy = potentialEnergy;
        }
    }

    /**
     * Data model for user streak entries
     * Backend endpoint: GET /api/streaks/leaderboard
     * Expected response: List of StreakEntry objects sorted by streak length
     *
     * NOTE: Streak system not yet implemented in backend
     * Streak tracking will require:
     * - Daily check-in mechanism
     * - Consecutive day counter
     * - Streak reset logic (when a day is missed)
     */
    public static class StreakEntry {
        public String userName;
        public int streakDays;

        public StreakEntry(String userName, int streakDays) {
            this.userName = userName;
            this.streakDays = streakDays;
        }
    }

    // ==================== Backend Integration Methods ====================

    /**
     * Fetch weekly energy data from backend
     * TODO: BACKEND - Implement API call
     *
     * Endpoint: GET /api/energy/weekly?dormId={dormId}
     * Response format:
     * [
     *   {"dayName": "Sunday", "energyUsageKwh": 1850},
     *   {"dayName": "Monday", "energyUsageKwh": 2100},
     *   ...
     * ]
     */
    private void fetchWeeklyEnergyData(String dormId) {
        // Example implementation:
        // ApiClient.getApiService().getWeeklyEnergy(dormId).enqueue(new Callback<List<DailyEnergyData>>() {
        //     @Override
        //     public void onResponse(Call<List<DailyEnergyData>> call, Response<List<DailyEnergyData>> response) {
        //         if (response.isSuccessful() && response.body() != null) {
        //             weeklyEnergyData = response.body();
        //             updateBarChart();
        //         }
        //     }
        //
        //     @Override
        //     public void onFailure(Call<List<DailyEnergyData>> call, Throwable t) {
        //         // Handle error
        //     }
        // });
    }

    /**
     * Fetch all-time leaderboard from backend
     * TODO: BACKEND - Implement API call
     *
     * Endpoint: GET /api/leaderboard/alltime
     * Response format:
     * [
     *   {"rank": 1, "dormName": "Tinsley", "potentialEnergy": 2400},
     *   {"rank": 2, "dormName": "Allen", "potentialEnergy": 2340},
     *   ...
     * ]
     */
    private void fetchAllTimeLeaderboard() {
        // Example implementation:
        // ApiClient.getApiService().getAllTimeLeaderboard().enqueue(new Callback<List<LeaderboardEntry>>() {
        //     @Override
        //     public void onResponse(Call<List<LeaderboardEntry>> call, Response<List<LeaderboardEntry>> response) {
        //         if (response.isSuccessful() && response.body() != null) {
        //             allTimeLeaderboard = response.body();
        //             setupPodium();
        //             populateAllTimeLeaderboard();
        //         }
        //     }
        //
        //     @Override
        //     public void onFailure(Call<List<LeaderboardEntry>> call, Throwable t) {
        //         // Handle error
        //     }
        // });
    }

    /**
     * Fetch user streaks leaderboard from backend
     * TODO: BACKEND - Implement API call (requires streak tracking system)
     *
     * Endpoint: GET /api/streaks/leaderboard
     * Response format:
     * [
     *   {"userName": "Sarah_T", "streakDays": 14},
     *   {"userName": "Mike_A", "streakDays": 12},
     *   ...
     * ]
     */
    private void fetchStreaksLeaderboard() {
        // Example implementation:
        // ApiClient.getApiService().getStreaksLeaderboard().enqueue(new Callback<List<StreakEntry>>() {
        //     @Override
        //     public void onResponse(Call<List<StreakEntry>> call, Response<List<StreakEntry>> response) {
        //         if (response.isSuccessful() && response.body() != null) {
        //             streaksLeaderboard = response.body();
        //             populateStreaksLeaderboard();
        //         }
        //     }
        //
        //     @Override
        //     public void onFailure(Call<List<StreakEntry>> call, Throwable t) {
        //         // Handle error
        //     }
        // });
    }
}
