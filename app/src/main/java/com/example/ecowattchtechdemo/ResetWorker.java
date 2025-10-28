package com.example.ecowattchtechdemo;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

public class ResetWorker extends Worker {
    public ResetWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull
    @Override
    public Result doWork() {
        SharedPreferences prefs = getApplicationContext().getSharedPreferences("ChecklistPrefs", Context.MODE_PRIVATE);
        prefs.edit().clear().apply(); // or reset only specific keys
        Log.d("DailyReset", "Daily checklist reset executed by WorkManager");
        return Result.success();
    }
}

