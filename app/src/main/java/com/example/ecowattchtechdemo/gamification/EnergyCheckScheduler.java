package com.example.ecowattchtechdemo.gamification;

import android.content.Context;
import android.util.Log;

import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import java.util.Calendar;
import java.util.Date;
import java.util.concurrent.TimeUnit;

/**
 * Utility class to schedule the daily energy check at 10 PM
 */
public class EnergyCheckScheduler {
    
    private static final String TAG = "EnergyCheckScheduler";
    private static final String WORK_NAME = "DailyEnergyCheckWork";
    private static final String WORK_TAG = "daily_energy_check";
    
    /**
     * Schedule daily energy check at 10 PM
     */
    public static void scheduleDailyEnergyCheck(Context context) {
        try {
            // Calculate time until next 10 PM
            Calendar now = Calendar.getInstance();
            Calendar next10PM = Calendar.getInstance();
            next10PM.set(Calendar.HOUR_OF_DAY, 22); // 10 PM
            next10PM.set(Calendar.MINUTE, 0);
            next10PM.set(Calendar.SECOND, 0);
            next10PM.set(Calendar.MILLISECOND, 0);
            
            // If it's already past 10 PM today, schedule for tomorrow
            if (next10PM.before(now)) {
                next10PM.add(Calendar.DAY_OF_MONTH, 1);
            }
            
            long initialDelay = next10PM.getTimeInMillis() - now.getTimeInMillis();
            
            // Create periodic work request for every 24 hours
            PeriodicWorkRequest dailyEnergyCheck = new PeriodicWorkRequest.Builder(
                    EnergyCheckWorker.class, 
                    24, 
                    TimeUnit.HOURS
            )
                    .setInitialDelay(initialDelay, TimeUnit.MILLISECONDS)
                    .addTag(WORK_TAG)
                    .build();
            
            // Schedule the work (replace any existing schedule)
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                    WORK_NAME,
                    ExistingPeriodicWorkPolicy.UPDATE,
                    dailyEnergyCheck
            );
            
            // Log scheduling info
            Date nextRun = new Date(System.currentTimeMillis() + initialDelay);
            Log.d(TAG, "🎮 Scheduled daily energy check at 10 PM");
            Log.d(TAG, "   Next run: " + nextRun);
            Log.d(TAG, "   Initial delay: " + (initialDelay / 1000 / 60) + " minutes");
            
        } catch (Exception e) {
            Log.e(TAG, "❌ Failed to schedule daily energy check", e);
        }
    }
    
    /**
     * Cancel the daily energy check
     */
    public static void cancelDailyEnergyCheck(Context context) {
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME);
        Log.d(TAG, "🎮 Cancelled daily energy check");
    }
    
    /**
     * Check if daily energy check is scheduled
     */
    public static void getScheduleStatus(Context context) {
        // This would require querying WorkManager state
        // For now, just log that we're checking
        Log.d(TAG, "🎮 Checking daily energy check schedule status...");
    }
}
