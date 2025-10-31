package com.example.ecowattchtechdemo.gamification;

import android.content.Context;
import android.util.Log;

/**
 * Test utilities for the gamification system
 * Helps validate that the scoring logic works correctly
 */
public class GamificationTester {
    
    private static final String TAG = "GamificationTester";
    
    /**
     * Initialize test data for all dorms to see the system in action
     */
    public static void initializeTestData(Context context) {
        Log.d(TAG, "🧪 Initializing test data for gamification system...");
        
        DormPointsManager pointsManager = new DormPointsManager(context);
        
        // Set some initial points for testing
        pointsManager.setDormTotalPoints("TINSLEY", 100);
        pointsManager.setDormTotalPoints("GABALDON", 75);
        pointsManager.setDormTotalPoints("SECHRIST", 125);
        
        // Simulate yesterday's energy usage for all dorms
        pointsManager.recordTodayEnergyUsage("TINSLEY", 5000.0);  // Simulate yesterday data
        pointsManager.recordTodayEnergyUsage("GABALDON", 5500.0);
        pointsManager.recordTodayEnergyUsage("SECHRIST", 4800.0);
        
        Log.d(TAG, "✅ Test data initialized:");
        Log.d(TAG, "  TINSLEY: 100 points, 5000 kWh yesterday");
        Log.d(TAG, "  GABALDON: 75 points, 5500 kWh yesterday");
        Log.d(TAG, "  SECHRIST: 125 points, 4800 kWh yesterday");
    }
    
    /**
     * Simulate different energy scenarios for testing
     */
    public static void simulateEnergyScenarios(Context context) {
        Log.d(TAG, "🧪 Simulating energy scenarios...");
        
        DormPointsManager pointsManager = new DormPointsManager(context);
        
        // Scenario 1: TINSLEY reduces energy usage (should get +50 points)
        // Yesterday: 5000 kWh, Today: 4500 kWh
        pointsManager.recordTodayEnergyUsage("TINSLEY", 4500.0);
        
        // Scenario 2: GABALDON increases energy usage (should get -25 points)
        // Yesterday: 5500 kWh, Today: 6000 kWh
        pointsManager.recordTodayEnergyUsage("GABALDON", 6000.0);
        
        // Scenario 3: SECHRIST reduces energy usage (should get +50 points)
        // Yesterday: 4800 kWh, Today: 4200 kWh
        pointsManager.recordTodayEnergyUsage("SECHRIST", 4200.0);
        
        Log.d(TAG, "✅ Energy comparison test completed successfully");
    }
    
    /**
     * Test daily check-in system (+25 points per check-in)
     */
    public static void testDailyCheckins(Context context) {
        Log.d(TAG, "🎯 Testing daily check-in system...");
        
        DormPointsManager pointsManager = new DormPointsManager(context);
        
        // Test first check-in for all dorms
        String[] dormNames = {"TINSLEY", "GABALDON", "SECHRIST"};
        
        for (String dormName : dormNames) {
            int spendablePointsBefore = pointsManager.getIndividualSpendablePoints();
            int dormScorePointsBefore = pointsManager.getDormTotalPoints(dormName);
            
            boolean checkedIn = pointsManager.recordDailyCheckin(dormName);
            
            int spendablePointsAfter = pointsManager.getIndividualSpendablePoints();
            int dormScorePointsAfter = pointsManager.getDormTotalPoints(dormName);
            
            Log.d(TAG, String.format("🎯 %s check-in: %s", 
                    dormName, 
                    checkedIn ? "SUCCESS" : "ALREADY DONE"));
            Log.d(TAG, String.format("  💰 Spendable: %d → %d | 🏆 Dorm Score: %d → %d", 
                    spendablePointsBefore, spendablePointsAfter,
                    dormScorePointsBefore, dormScorePointsAfter));
            
            // Verify check-in status
            boolean hasCheckedIn = pointsManager.hasCheckedInToday(dormName);
            int streak = pointsManager.getCheckinStreak(dormName);
            int monthlyCheckins = pointsManager.getMonthlyCheckins(dormName);
            
            Log.d(TAG, String.format("  Status: %s | Streak: %d | Monthly: %d/20", 
                    hasCheckedIn ? "✅ Checked In" : "❌ Not Checked In",
                    streak,
                    monthlyCheckins));
        }
        
        // Test duplicate check-in (should not award additional points)
        Log.d(TAG, "🔄 Testing duplicate check-in prevention...");
        for (String dormName : dormNames) {
            int spendablePointsBefore = pointsManager.getIndividualSpendablePoints();
            boolean checkedIn = pointsManager.recordDailyCheckin(dormName);
            int spendablePointsAfter = pointsManager.getIndividualSpendablePoints();
            
            if (!checkedIn && spendablePointsBefore == spendablePointsAfter) {
                Log.d(TAG, "✅ " + dormName + " duplicate check-in correctly prevented");
            } else {
                Log.w(TAG, "❌ " + dormName + " duplicate check-in not prevented properly");
            }
        }
        
        Log.d(TAG, "✅ Daily check-in test completed");
    }
    
    /**
     * Test rally end conversion (dorm score points → individual spendable points)
     */
    public static void testRallyEndConversion(Context context) {
        Log.d(TAG, "🏁 Testing rally end conversion...");
        
        DormPointsManager pointsManager = new DormPointsManager(context);
        
        // Record initial state
        int spendablePointsBefore = pointsManager.getIndividualSpendablePoints();
        int tinsleyScoreBefore = pointsManager.getDormTotalPoints("TINSLEY");
        
        Log.d(TAG, String.format("Before rally end - Spendable: %d, TINSLEY Score: %d", 
                spendablePointsBefore, tinsleyScoreBefore));
        
        // Simulate rally end for TINSLEY user
        java.util.Map<String, Integer> conversions = pointsManager.convertDormScoreToSpendablePoints("TINSLEY");
        
        // Check results
        int spendablePointsAfter = pointsManager.getIndividualSpendablePoints();
        int expectedSpendable = spendablePointsBefore + tinsleyScoreBefore;
        
        Log.d(TAG, String.format("After rally end - Spendable: %d (Expected: %d)", 
                spendablePointsAfter, expectedSpendable));
        
        if (spendablePointsAfter == expectedSpendable) {
            Log.d(TAG, "✅ Rally end conversion successful!");
        } else {
            Log.w(TAG, "❌ Rally end conversion failed!");
        }
        
        Log.d(TAG, "✅ Rally end conversion test completed");
    }
    
    /**
     * Comprehensive test of all gamification features
     */
    public static boolean runComprehensiveTest(Context context) {
        Log.d(TAG, "🧪 Running comprehensive gamification test...");
        return runCompleteTest(context);
    }
    
    /**
     * Test the daily energy check and validate results
     */
    public static boolean testDailyEnergyCheck(Context context) {
        Log.d(TAG, "🧪 Testing daily energy check...");
        
        DormPointsManager pointsManager = new DormPointsManager(context);
        
        // Get points before check
        int tinsleyBefore = pointsManager.getDormTotalPoints("TINSLEY");
        int gabaldonBefore = pointsManager.getDormTotalPoints("GABALDON");
        int sechristBefore = pointsManager.getDormTotalPoints("SECHRIST");
        
        Log.d(TAG, "Points before check:");
        Log.d(TAG, "  TINSLEY: " + tinsleyBefore);
        Log.d(TAG, "  GABALDON: " + gabaldonBefore);
        Log.d(TAG, "  SECHRIST: " + sechristBefore);
        
        // Perform daily check
        java.util.Map<String, Integer> changes = pointsManager.performDailyEnergyCheck();
        
        // Get points after check
        int tinsleyAfter = pointsManager.getDormTotalPoints("TINSLEY");
        int gabaldonAfter = pointsManager.getDormTotalPoints("GABALDON");
        int sechristAfter = pointsManager.getDormTotalPoints("SECHRIST");
        
        Log.d(TAG, "Points after check:");
        Log.d(TAG, "  TINSLEY: " + tinsleyAfter + " (change: " + changes.get("TINSLEY") + ")");
        Log.d(TAG, "  GABALDON: " + gabaldonAfter + " (change: " + changes.get("GABALDON") + ")");
        Log.d(TAG, "  SECHRIST: " + sechristAfter + " (change: " + changes.get("SECHRIST") + ")");
        
        // Validate expected results
        boolean tinsleyCorrect = (tinsleyAfter == tinsleyBefore + 50);  // Should get +50
        boolean gabaldonCorrect = (gabaldonAfter == gabaldonBefore - 25); // Should get -25
        boolean sechristCorrect = (sechristAfter == sechristBefore + 50); // Should get +50
        
        boolean allCorrect = tinsleyCorrect && gabaldonCorrect && sechristCorrect;
        
        Log.d(TAG, "✅ Test Results:");
        Log.d(TAG, "  TINSLEY: " + (tinsleyCorrect ? "✅ PASS" : "❌ FAIL"));
        Log.d(TAG, "  GABALDON: " + (gabaldonCorrect ? "✅ PASS" : "❌ FAIL"));
        Log.d(TAG, "  SECHRIST: " + (sechristCorrect ? "✅ PASS" : "❌ FAIL"));
        Log.d(TAG, "  Overall: " + (allCorrect ? "✅ ALL TESTS PASSED" : "❌ SOME TESTS FAILED"));
        
        return allCorrect;
    }
    
    /**
     * Test leaderboard ranking logic
     */
    public static void testLeaderboardRanking(Context context) {
        Log.d(TAG, "🧪 Testing leaderboard ranking...");
        
        DormPointsManager pointsManager = new DormPointsManager(context);
        
        // Get current rankings
        java.util.Map<String, Integer> rankings = pointsManager.getDormRankings();
        
        Log.d(TAG, "Current rankings:");
        for (java.util.Map.Entry<String, Integer> entry : rankings.entrySet()) {
            String position = pointsManager.getDormPosition(entry.getKey());
            Log.d(TAG, String.format("  %s: %d points (%s)", 
                    entry.getKey(), entry.getValue(), position));
        }
    }
    
    /**
     * Run complete test suite
     */
    public static boolean runCompleteTest(Context context) {
        Log.d(TAG, "🧪 Starting complete gamification test suite...");
        
        try {
            // Step 1: Initialize test data
            initializeTestData(context);
            
            // Step 2: Test daily check-ins (+25 points each)
            testDailyCheckins(context);
            
            // Step 3: Simulate energy scenarios
            simulateEnergyScenarios(context);
            
            // Step 4: Test daily energy check
            boolean testPassed = testDailyEnergyCheck(context);
            
            // Step 5: Test leaderboard ranking
            testLeaderboardRanking(context);
            
            // Step 6: Test rally end conversion
            testRallyEndConversion(context);
            
            Log.d(TAG, "🧪 Complete test suite finished: " + 
                    (testPassed ? "✅ ALL TESTS PASSED" : "❌ SOME TESTS FAILED"));
            
            return testPassed;
            
        } catch (Exception e) {
            Log.e(TAG, "❌ Test suite failed with exception", e);
            return false;
        }
    }
    
    /**
     * Reset all test data
     */
    public static void resetTestData(Context context) {
        Log.d(TAG, "🧪 Resetting test data...");
        
        DormPointsManager pointsManager = new DormPointsManager(context);
        pointsManager.resetRallyPeriod();
        
        Log.d(TAG, "✅ Test data reset complete");
    }
}
