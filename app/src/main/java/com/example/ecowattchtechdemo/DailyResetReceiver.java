package com.example.ecowattchtechdemo;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.util.Log;

public class DailyResetReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        // Reset all daily tasks
        SharedPreferences prefs = context.getSharedPreferences("DailyTasks", Context.MODE_PRIVATE);
        prefs.edit()
                .putBoolean("checklist_item_1", false)
                .putBoolean("checklist_item_2", false)
                .putBoolean("checklist_item_3", false)
                .putBoolean("all_tasks", false)
                .apply();

        Log.d("DailyReset", "Daily tasks reset at 10PM");
    }
}

