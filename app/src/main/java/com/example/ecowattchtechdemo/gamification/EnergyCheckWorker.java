package com.example.ecowattchtechdemo.gamification;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import java.util.Map;

/**
 * WorkManager worker that performs daily energy comparison at 10 PM
 * Awards/deducts points based on today's vs yesterday's energy usage
 */
public class EnergyCheckWorker extends Worker {
    
    private static final String TAG = "EnergyCheckWorker";
    
    public EnergyCheckWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull
    @Override
    public Result doWork() {
        try {
            Log.d(TAG, "🎮 Starting daily energy check at 10 PM...");
            
            // Initialize the points manager
            DormPointsManager pointsManager = new DormPointsManager(getApplicationContext());
            
            // Perform the daily energy comparison
            Map<String, Integer> pointChanges = pointsManager.performDailyEnergyCheck();
            
            // Log the results
            Log.d(TAG, "🎮 Daily energy check completed:");
            for (Map.Entry<String, Integer> entry : pointChanges.entrySet()) {
                Log.d(TAG, String.format("  %s: %+d points", entry.getKey(), entry.getValue()));
            }
            
            // Get current standings for logging
            Map<String, Integer> rankings = pointsManager.getDormRankings();
            Log.d(TAG, "🏆 Current standings after daily check:");
            for (Map.Entry<String, Integer> entry : rankings.entrySet()) {
                String position = pointsManager.getDormPosition(entry.getKey());
                Log.d(TAG, String.format("  %s: %d points (%s)", 
                        entry.getKey(), entry.getValue(), position));
            }
            
            // Check if rally period has ended
            if (!pointsManager.isRallyPeriodActive()) {
                Log.d(TAG, "🏁 Rally period has ended!");
                Log.d(TAG, "ℹ️ Rally end conversion must be triggered manually per user's dorm");
                
                // Note: Rally end conversion now requires knowing user's dorm
                // This should be handled in the main app when user opens it
            }
            
            Log.d(TAG, "✅ Daily energy check worker completed successfully");
            return Result.success();
            
        } catch (Exception e) {
            Log.e(TAG, "❌ Error in daily energy check worker", e);
            return Result.failure();
        }
    }
}
