package com.example.ecowattchtechdemo;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import com.android.volley.Request;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;
import com.google.android.material.textfield.TextInputEditText;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Calendar;
import java.util.concurrent.TimeUnit;

import com.example.ecowattchtechdemo.ApiClient;

public class SignupFragment extends Fragment {
    Button signupButton;
    TextView loginLink;
    TextInputEditText signupUser, signupPass, confirmPass;
    AutoCompleteTextView dormDropdown;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        // Link XML layout to this Fragment
        View view = inflater.inflate(R.layout.fragment_signup, container, false);

        signupButton = view.findViewById(R.id.signup_button);
        loginLink = view.findViewById(R.id.login_link);
        signupUser = view.findViewById(R.id.signup_user);
        signupPass = view.findViewById(R.id.signup_pass);
        confirmPass = view.findViewById(R.id.confirm_pass);
        dormDropdown = view.findViewById(R.id.dormitory);

        // Setup dormitory dropdown with dorm options
        String[] dormitories = {"Tinsley", "Gabaldon", "Sechrist"};
        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                requireContext(),
                R.layout.dropdown_item,
                dormitories
        );
        dormDropdown.setAdapter(adapter);
        dormDropdown.setDropDownBackgroundResource(R.color.modal_background);

        signupButton.setOnClickListener(v -> {
            String username = signupUser.getText().toString().trim();
            String password = signupPass.getText().toString().trim();
            String confirm = confirmPass.getText().toString().trim();
            String dormitory = dormDropdown.getText().toString().trim();

            JSONObject jsonBody = new JSONObject();
            try {
                jsonBody.put("usernames", username);
                jsonBody.put("passwords", password);
                jsonBody.put("dormitory", dormitory);
            } catch (JSONException e) {
                e.printStackTrace();
            }

            String url = ApiClient.BASE_URL + "/signup";

            JsonObjectRequest request = new JsonObjectRequest(Request.Method.POST, url, jsonBody,
                    response -> {
                        Toast.makeText(requireContext(), "Sign-up successful!", Toast.LENGTH_SHORT).show();

                        //get the stuff from the sign up
                        SharedPreferences prefs = requireActivity().getSharedPreferences("MyAppPrefs", Context.MODE_PRIVATE);
                        SharedPreferences.Editor editor = prefs.edit();
                        editor.putString("Username", username);
                        editor.putString("Dormitory", dormitory);
                        editor.apply();
                    },
                    error -> {
                        String errorMsg = "Sign-up failed";
                        if (error.networkResponse != null && error.networkResponse.data != null) {
                            try {
                                String responseBody = new String(error.networkResponse.data, "utf-8");
                                JSONObject data = new JSONObject(responseBody);
                                errorMsg = data.optString("message", errorMsg);
                            } catch (Exception e) {
                                // fallback to default errorMsg
                            }
                        }
                        Toast.makeText(requireContext(), errorMsg, Toast.LENGTH_LONG).show();
                        //Toast.makeText(getApplicationContext(), "Sign-up failed: " + error.getMessage(), Toast.LENGTH_LONG).show();
                    }
            );

            Volley.newRequestQueue(requireContext()).add(request);

            // TEMP: reset checklist progress on signup
            SharedPreferences prefs = requireContext().getSharedPreferences("DailyTasks", Context.MODE_PRIVATE);
            prefs.edit()
                    .putBoolean("checklist_item_1", false)
                    .putBoolean("checklist_item_2", false)
                    .putBoolean("checklist_item_3", false)
                    .putBoolean("all_tasks", false)
                    .apply();

            scheduleDailyReset();

            // go to dashboard
            Intent intent = new Intent(requireContext(), DashboardActivity.class);
            startActivity(intent);
        });

        loginLink.setOnClickListener(v -> {
            requireActivity().getSupportFragmentManager()
                    .beginTransaction()
                    .replace(R.id.login_signup_fragment_container, new LoginFragment())
                    .addToBackStack(null)
                    .commit();
        });

        return view;
    }

    private void scheduleDailyReset() {
        Context context = requireContext(); // Safe inside Fragment

        // Calculate delay until next 10 PM
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

        // Create a periodic WorkRequest for every 24 hours
        PeriodicWorkRequest dailyResetWork =
                new PeriodicWorkRequest.Builder(ResetWorker.class, 24, TimeUnit.HOURS)
                        .setInitialDelay(initialDelay, TimeUnit.MILLISECONDS)
                        .addTag("daily_reset_work")
                        .build();

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                "DailyResetWork", // unique name so it won't duplicate
                ExistingPeriodicWorkPolicy.UPDATE, // replace any existing schedule
                dailyResetWork
        );

        Log.d("DailyReset", "WorkManager scheduled daily reset at 10 PM");
    }


}
