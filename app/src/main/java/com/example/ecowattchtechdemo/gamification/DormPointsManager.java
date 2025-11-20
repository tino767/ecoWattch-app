package com.example.ecowattchtechdemo.gamification;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Manages the gamification points system for dormitories
 * Handles daily energy comparison, potential energy calculation, and point tracking
 */
public class DormPointsManager {
    
    private static final String TAG = "DormPointsManager";
    private static final String PREFS_NAME = "DormPointsPrefs";
    
    // Point values as per requirements
    private static final int POINTS_PENALTY_INCREASE = -25;  // When using more than yesterday
    private static final int POINTS_REWARD_DECREASE = 50;    // When using less than yesterday
    private static final int POINTS_DAILY_CHECKIN = 25;      // Daily check-in reward (+25 points)
    
    // Dorm names exactly as specified by Collin for database variables
    public static final String TINSLEY_TOTAL_POINTS = "Tinsley_total_points";
    public static final String GABALDON_TOTAL_POINTS = "Gabaldon_total_points";
    public static final String SECHRIST_TOTAL_POINTS = "Sechrist_total_points";
    
    // Individual spendable points (earned from daily check-ins)
    private static final String INDIVIDUAL_SPENDABLE_POINTS = "individual_spendable_points";
    
    // Energy tracking keys
    private static final String TODAY_ENERGY_PREFIX = "today_energy_";
    private static final String YESTERDAY_ENERGY_PREFIX = "yesterday_energy_";
    private static final String LAST_CHECK_DATE = "last_check_date";
    
    // Daily check-in tracking keys
    private static final String DAILY_CHECKIN_PREFIX = "daily_checkin_";
    private static final String CHECKIN_STREAK_PREFIX = "checkin_streak_";
    private static final String LAST_CHECKIN_DATE_PREFIX = "last_checkin_date_";
    
    // Rally period tracking
    private static final String RALLY_START_DATE = "rally_start_date";
    private static final String RALLY_DURATION_DAYS = "rally_duration_days";
    private static final int DEFAULT_RALLY_DURATION = 14; // 2 weeks
    
    private final SharedPreferences prefs;
    private final SimpleDateFormat dateFormat;
    
    public DormPointsManager(Context context) {
        this.prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        this.dateFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
        
        // Initialize rally period if not set
        initializeRallyPeriod();
    }
    
    /**
     * Initialize rally period if it hasn't been set
     */
    private void initializeRallyPeriod() {
        if (!prefs.contains(RALLY_START_DATE)) {
            String today = dateFormat.format(new Date());
            prefs.edit()
                    .putString(RALLY_START_DATE, today)
                    .putInt(RALLY_DURATION_DAYS, DEFAULT_RALLY_DURATION)
                    .apply();
            Log.d(TAG, "Initialized rally period starting: " + today);
        }
    }
    
    /**
     * Get current potential energy points for a dorm
     */
    public int getDormTotalPoints(String dormName) {
        String key = getDormPointsKey(dormName);
        int points = prefs.getInt(key, 0);
        Log.d(TAG, "Retrieved points for " + dormName + ": " + points);
        return points;
    }
    
    /**
     * Set potential energy points for a dorm
     */
    public void setDormTotalPoints(String dormName, int points) {
        String key = getDormPointsKey(dormName);
        prefs.edit().putInt(key, points).apply();
        Log.d(TAG, "Set points for " + dormName + ": " + points);
    }
    
    /**
     * Add points to a dorm's total
     */
    public void addPointsToDorm(String dormName, int pointsToAdd) {
        int currentPoints = getDormTotalPoints(dormName);
        int newTotal = currentPoints + pointsToAdd;
        setDormTotalPoints(dormName, newTotal);
        
        Log.d(TAG, String.format("Added %d points to %s: %d → %d", 
                pointsToAdd, dormName, currentPoints, newTotal));
    }
    
    /**
     * Get individual spendable points (earned from daily check-ins)
     */
    public int getIndividualSpendablePoints() {
        int points = prefs.getInt(INDIVIDUAL_SPENDABLE_POINTS, 0);
        Log.d(TAG, "Retrieved individual spendable points: " + points);
        return points;
    }
    
    /**
     * Set individual spendable points
     */
    public void setIndividualSpendablePoints(int points) {
        prefs.edit().putInt(INDIVIDUAL_SPENDABLE_POINTS, points).apply();
        Log.d(TAG, "Set individual spendable points: " + points);
    }
    
    /**
     * Initialize user points from login data
     * @param loginSpendablePoints Points from backend login response
     */
    public void initializePointsFromLogin(int loginSpendablePoints) {
        // Only set if we don't already have points stored locally, or if backend has more
        int currentPoints = getIndividualSpendablePoints();
        if (currentPoints == 0 || loginSpendablePoints > currentPoints) {
            setIndividualSpendablePoints(loginSpendablePoints);
            Log.d(TAG, "Initialized spendable points from login: " + loginSpendablePoints);
        }
    }
    
    /**
     * Add to individual spendable points (for daily check-ins)
     */
    public void addIndividualSpendablePoints(int pointsToAdd) {
        int currentPoints = getIndividualSpendablePoints();
        int newTotal = currentPoints + pointsToAdd;
        setIndividualSpendablePoints(newTotal);
        
        Log.d(TAG, String.format("💰 Added %d spendable points: %d → %d", 
                pointsToAdd, currentPoints, newTotal));
    }
    
    /**
     * Spend individual points (for shop purchases)
     */
    public boolean spendIndividualPoints(int pointsToSpend) {
        int currentPoints = getIndividualSpendablePoints();
        
        if (currentPoints >= pointsToSpend) {
            int newTotal = currentPoints - pointsToSpend;
            setIndividualSpendablePoints(newTotal);
            
            Log.d(TAG, String.format("💸 Spent %d spendable points: %d → %d", 
                    pointsToSpend, currentPoints, newTotal));
            return true;
        } else {
            Log.w(TAG, String.format("❌ Insufficient spendable points: Need %d, Have %d", 
                    pointsToSpend, currentPoints));
            return false;
        }
    }
    
    /**
     * Record today's energy usage for a dorm (overwrites previous value)
     */
    public void recordTodayEnergyUsage(String dormName, double energyKWh) {
        String today = dateFormat.format(new Date());
        String key = TODAY_ENERGY_PREFIX + dormName + "_" + today;
        
        prefs.edit()
                .putFloat(key, (float) energyKWh)
                .putString(LAST_CHECK_DATE, today)
                .apply();
        
        Log.d(TAG, String.format("Recorded today's energy for %s: %.2f kWh", dormName, energyKWh));
    }
    
    /**
     * Update today's energy usage only if the new value is higher (proper accumulation)
     */
    public void updateTodayEnergyUsageIfHigher(String dormName, double newEnergyKWh) {
        String today = dateFormat.format(new Date());
        String key = TODAY_ENERGY_PREFIX + dormName + "_" + today;
        
        double currentTotal = prefs.getFloat(key, 0.0f);
        
        // Only update if the new value is higher (proper daily total behavior)
        if (newEnergyKWh > currentTotal) {
            prefs.edit()
                    .putFloat(key, (float) newEnergyKWh)
                    .putString(LAST_CHECK_DATE, today)
                    .apply();
            
            Log.d(TAG, String.format("Updated today's energy for %s: %.2f kWh (was %.2f kWh)", 
                    dormName, newEnergyKWh, currentTotal));
        } else {
            Log.d(TAG, String.format("Kept existing today's energy for %s: %.2f kWh (new value %.2f kWh was not higher)", 
                    dormName, currentTotal, newEnergyKWh));
        }
    }
    
    /**
     * Get today's energy usage for a dorm
     */
    public double getTodayEnergyUsage(String dormName) {
        String today = dateFormat.format(new Date());
        String key = TODAY_ENERGY_PREFIX + dormName + "_" + today;
        return prefs.getFloat(key, 0.0f);
    }
    
    /**
     * Get yesterday's energy usage for a dorm
     * Returns actual stored data or 0 if no data exists
     */
    public double getYesterdayEnergyUsage(String dormName) {
        String yesterday = getYesterdayDate();
        String key = TODAY_ENERGY_PREFIX + dormName + "_" + yesterday;
        return prefs.getFloat(key, 0.0f);
    }
    
    /**
     * Record daily check-in and award +25 points
     * Returns true if check-in was successful (first time today), false if already checked in
     */
    public boolean recordDailyCheckin(String dormName) {
        String today = dateFormat.format(new Date());
        String checkinKey = DAILY_CHECKIN_PREFIX + dormName + "_" + today;
        String lastCheckinKey = LAST_CHECKIN_DATE_PREFIX + dormName;
        String streakKey = CHECKIN_STREAK_PREFIX + dormName;
        
        // Check if already checked in today
        if (prefs.getBoolean(checkinKey, false)) {
            Log.d(TAG, "✋ " + dormName + " already checked in today - no additional points");
            return false; // Already checked in today
        }
        
        // Record today's check-in
        SharedPreferences.Editor editor = prefs.edit();
        editor.putBoolean(checkinKey, true);
        editor.putString(lastCheckinKey, today);
        
        // Calculate and update streak
        int currentStreak = calculateCheckinStreak(dormName, today);
        editor.putInt(streakKey, currentStreak);
        
        // Award individual spendable points for check-in (NOT dorm points)
        addIndividualSpendablePoints(POINTS_DAILY_CHECKIN);
        
        editor.apply();
        
        Log.d(TAG, String.format("🎯 Daily check-in recorded for %s: +%d spendable points (Streak: %d days)", 
                dormName, POINTS_DAILY_CHECKIN, currentStreak));
        
        return true; // Successfully checked in
    }
    
    /**
     * Get current check-in streak for a dorm
     */
    public int getCheckinStreak(String dormName) {
        String streakKey = CHECKIN_STREAK_PREFIX + dormName;
        return prefs.getInt(streakKey, 0);
    }
    
    /**
     * Check if dorm has checked in today
     */
    public boolean hasCheckedInToday(String dormName) {
        String today = dateFormat.format(new Date());
        String checkinKey = DAILY_CHECKIN_PREFIX + dormName + "_" + today;
        return prefs.getBoolean(checkinKey, false);
    }
    
    /**
     * Calculate check-in streak based on consecutive days
     */
    private int calculateCheckinStreak(String dormName, String todayDate) {
        String lastCheckinKey = LAST_CHECKIN_DATE_PREFIX + dormName;
        String lastCheckinDate = prefs.getString(lastCheckinKey, null);
        
        if (lastCheckinDate == null) {
            // First ever check-in
            return 1;
        }
        
        try {
            Date lastDate = dateFormat.parse(lastCheckinDate);
            Date today = dateFormat.parse(todayDate);
            
            long daysDiff = (today.getTime() - lastDate.getTime()) / (1000 * 60 * 60 * 24);
            
            if (daysDiff == 1) {
                // Consecutive day - increment streak
                int currentStreak = prefs.getInt(CHECKIN_STREAK_PREFIX + dormName, 0);
                return currentStreak + 1;
            } else if (daysDiff == 0) {
                // Same day (shouldn't happen due to earlier check)
                return prefs.getInt(CHECKIN_STREAK_PREFIX + dormName, 1);
            } else {
                // Gap in days - reset streak
                return 1;
            }
        } catch (Exception e) {
            Log.e(TAG, "Error calculating streak for " + dormName, e);
            return 1;
        }
    }
    
    /**
     * Get total check-ins this month for a dorm
     */
    public int getMonthlyCheckins(String dormName) {
        String currentMonth = new SimpleDateFormat("yyyy-MM", Locale.US).format(new Date());
        int checkinCount = 0;
        
        // Check each day of the current month
        for (int day = 1; day <= 31; day++) {
            String dateStr = currentMonth + "-" + String.format(java.util.Locale.US, "%02d", day);
            String checkinKey = DAILY_CHECKIN_PREFIX + dormName + "_" + dateStr;
            
            if (prefs.getBoolean(checkinKey, false)) {
                checkinCount++;
            }
        }
        
        return checkinCount;
    }
    
    /**
     * Perform daily energy comparison and award/deduct points
     * This should be called daily at 10 PM
     */
    public Map<String, Integer> performDailyEnergyCheck() {
        Log.d(TAG, "🔄 Performing daily energy check at 10 PM...");
        
        Map<String, Integer> pointChanges = new HashMap<>();
        String[] dormNames = {TINSLEY_TOTAL_POINTS, GABALDON_TOTAL_POINTS, SECHRIST_TOTAL_POINTS};
        
        for (String dormKey : dormNames) {
            String dormName = extractDormName(dormKey);
            int pointChange = checkDormEnergyComparison(dormName);
            pointChanges.put(dormName, pointChange);
        }
        
        // Update the last check date
        String today = dateFormat.format(new Date());
        prefs.edit().putString(LAST_CHECK_DATE, today).apply();
        
        Log.d(TAG, "✅ Daily energy check completed. Point changes: " + pointChanges);
        return pointChanges;
    }
    
    /**
     * Check energy comparison for a single dorm and award/deduct points
     */
    private int checkDormEnergyComparison(String dormName) {
        double todayUsage = getTodayEnergyUsage(dormName);
        double yesterdayUsage = getYesterdayEnergyUsage(dormName);
        
        if (yesterdayUsage == 0.0) {
            Log.w(TAG, "No yesterday data for " + dormName + ", skipping comparison");
            return 0;
        }
        
        int pointChange = 0;
        String comparisonResult;
        
        if (todayUsage > yesterdayUsage) {
            // Using more energy than yesterday - penalty
            pointChange = POINTS_PENALTY_INCREASE;
            comparisonResult = "INCREASED";
        } else if (todayUsage < yesterdayUsage) {
            // Using less energy than yesterday - reward
            pointChange = POINTS_REWARD_DECREASE;
            comparisonResult = "DECREASED";
        } else {
            // Same usage - no point change
            comparisonResult = "SAME";
        }
        
        // Apply the point change
        if (pointChange != 0) {
            addPointsToDorm(dormName, pointChange);
        }
        
        Log.d(TAG, String.format("🏠 %s: %.2f → %.2f kWh (%s) = %+d points", 
                dormName, yesterdayUsage, todayUsage, comparisonResult, pointChange));
        
        return pointChange;
    }
    
    /**
     * Get leaderboard ranking based on potential energy points
     */
    public Map<String, Integer> getDormRankings() {
        Map<String, Integer> rankings = new HashMap<>();
        
        rankings.put("TINSLEY", getDormTotalPoints("TINSLEY"));
        rankings.put("GABALDON", getDormTotalPoints("GABALDON"));
        rankings.put("SECHRIST", getDormTotalPoints("SECHRIST"));
        
        Log.d(TAG, "Current dorm rankings: " + rankings);
        return rankings;
    }
    
    /**
     * Get the position (1st, 2nd, 3rd) for a dorm based on points
     */
    public String getDormPosition(String dormName) {
        Map<String, Integer> rankings = getDormRankings();
        
        // Sort dorms by points (highest first)
        java.util.List<Map.Entry<String, Integer>> sortedDorms = new java.util.ArrayList<>(rankings.entrySet());
        java.util.Collections.sort(sortedDorms, new java.util.Comparator<Map.Entry<String, Integer>>() {
            @Override
            public int compare(Map.Entry<String, Integer> a, Map.Entry<String, Integer> b) {
                return b.getValue().compareTo(a.getValue());
            }
        });
        
        // Find position
        for (int i = 0; i < sortedDorms.size(); i++) {
            if (sortedDorms.get(i).getKey().equals(dormName.toUpperCase(java.util.Locale.US))) {
                switch (i) {
                    case 0: return "1ST PLACE";
                    case 1: return "2ND PLACE";
                    case 2: return "3RD PLACE";
                    default: return "UNRANKED";
                }
            }
        }
        
        return "UNRANKED";
    }
    
    /**
     * Check if rally period is still active
     */
    public boolean isRallyPeriodActive() {
        try {
            String startDateStr = prefs.getString(RALLY_START_DATE, null);
            if (startDateStr == null) return false;
            
            Date startDate = dateFormat.parse(startDateStr);
            Date currentDate = new Date();
            int rallyDuration = prefs.getInt(RALLY_DURATION_DAYS, DEFAULT_RALLY_DURATION);
            
            long daysDiff = (currentDate.getTime() - startDate.getTime()) / (1000 * 60 * 60 * 24);
            
            boolean isActive = daysDiff < rallyDuration;
            Log.d(TAG, String.format("Rally period check: Day %d of %d (Active: %s)", 
                    daysDiff + 1, rallyDuration, isActive));
            
            return isActive;
        } catch (Exception e) {
            Log.e(TAG, "Error checking rally period", e);
            return true; // Default to active if error
        }
    }
    
    /**
     * Convert dorm score points to individual spendable points (called when rally ends)
     * User gets their dorm's total score points added to their individual spendable points
     */
    public Map<String, Integer> convertDormScoreToSpendablePoints(String userDormName) {
        Log.d(TAG, "💰 Converting dorm score points to individual spendable points at rally end...");
        
        Map<String, Integer> conversions = new HashMap<>();
        String[] dormNames = {"TINSLEY", "GABALDON", "SECHRIST"};
        
        // Get user's dorm score points
        int userDormScorePoints = getDormTotalPoints(userDormName);
        
        // Add dorm score points to user's individual spendable points
        if (userDormScorePoints > 0) {
            addIndividualSpendablePoints(userDormScorePoints);
            Log.d(TAG, String.format("🏆 Rally ended! %s dorm earned %d score points → Added to individual spendable points", 
                    userDormName, userDormScorePoints));
        }
        
        // Track all dorm conversions for reporting
        for (String dormName : dormNames) {
            int dormScorePoints = getDormTotalPoints(dormName);
            conversions.put(dormName, dormScorePoints);
            
            Log.d(TAG, String.format("📊 %s dorm final score: %d points", 
                    dormName, dormScorePoints));
        }
        
        return conversions;
    }
    
    /**
     * Reset rally period (for testing or new rallies)
     */
    public void resetRallyPeriod() {
        String today = dateFormat.format(new Date());
        prefs.edit()
                .putString(RALLY_START_DATE, today)
                .putInt(TINSLEY_TOTAL_POINTS, 0)
                .putInt(GABALDON_TOTAL_POINTS, 0)
                .putInt(SECHRIST_TOTAL_POINTS, 0)
                .apply();
        
        Log.d(TAG, "🔄 Rally period reset. New start date: " + today);
    }
    
    // Helper methods
    
    private String getDormPointsKey(String dormName) {
        switch (dormName.toUpperCase(java.util.Locale.US)) {
            case "TINSLEY": return TINSLEY_TOTAL_POINTS;
            case "GABALDON": return GABALDON_TOTAL_POINTS;
            case "SECHRIST": return SECHRIST_TOTAL_POINTS;
            default: 
                Log.w(TAG, "Unknown dorm name: " + dormName);
                return dormName + "_total_points";
        }
    }
    
    private String extractDormName(String dormPointsKey) {
        if (dormPointsKey.equals(TINSLEY_TOTAL_POINTS)) return "TINSLEY";
        if (dormPointsKey.equals(GABALDON_TOTAL_POINTS)) return "GABALDON";
        if (dormPointsKey.equals(SECHRIST_TOTAL_POINTS)) return "SECHRIST";
        return dormPointsKey.replace("_total_points", "");
    }
    
    private String getYesterdayDate() {
        Date yesterday = new Date(System.currentTimeMillis() - 24 * 60 * 60 * 1000);
        return dateFormat.format(yesterday);
    }
    
    /**
     * Get debugging information about the current state
     */
    public String getDebugInfo() {
        StringBuilder info = new StringBuilder();
        info.append("🎮 DUAL-POINT SYSTEM DEBUG INFO 🎮\n");
        info.append("Rally Period Active: ").append(isRallyPeriodActive()).append("\n");
        info.append("Rally Start: ").append(prefs.getString(RALLY_START_DATE, "Not Set")).append("\n");
        
        // 💰 Individual Spendable Points (from daily check-ins)
        int spendablePoints = getIndividualSpendablePoints();
        info.append(String.format(java.util.Locale.US, "\n💰 Individual Spendable Points: %d\n", spendablePoints));
        info.append("  Source: Daily check-ins (+25 each)\n");
        info.append("  Usage: Shop purchases (palettes cost 500)\n");
        
        // 🏆 Dorm Score Points (from energy performance)
        Map<String, Integer> rankings = getDormRankings();
        info.append("\n🏆 Dorm Score Points (Potential Energy):\n");
        for (Map.Entry<String, Integer> entry : rankings.entrySet()) {
            String position = getDormPosition(entry.getKey());
            info.append(String.format(java.util.Locale.US, "  %s: %d score points (%s)\n", 
                    entry.getKey(), entry.getValue(), position));
        }
        info.append("  Source: Energy performance (+50 decreased, -25 increased)\n");
        info.append("  Conversion: Rally end → Individual spendable points\n");
        
        // 🎯 Daily Check-in Information
        info.append("\n🎯 Daily Check-ins:\n");
        for (String dorm : new String[]{"TINSLEY", "GABALDON", "SECHRIST"}) {
            boolean checkedInToday = hasCheckedInToday(dorm);
            int streak = getCheckinStreak(dorm);
            int monthlyCheckins = getMonthlyCheckins(dorm);
            
            info.append(String.format(java.util.Locale.US, "  %s: %s | Streak: %d days | Monthly: %d/20\n", 
                    dorm, 
                    checkedInToday ? "✅ Checked In" : "⏳ Pending",
                    streak,
                    monthlyCheckins));
        }
        
        String today = dateFormat.format(new Date());
        info.append("\nToday's Energy (").append(today).append("):\n");
        for (String dorm : new String[]{"TINSLEY", "GABALDON", "SECHRIST"}) {
            double todayEnergy = getTodayEnergyUsage(dorm);
            double yesterdayEnergy = getYesterdayEnergyUsage(dorm);
            info.append(String.format(java.util.Locale.US, "  %s: Today=%.2f, Yesterday=%.2f kWh\n", 
                    dorm, todayEnergy, yesterdayEnergy));
        }
        
        return info.toString();
    }
}
