package com.example.ecowattchtechdemo;

import android.animation.ObjectAnimator;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.google.android.material.textfield.TextInputEditText;
import com.example.ecowattchtechdemo.gamification.DormPointsManager;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class LoginFragment extends Fragment {
    Button loginButton;
    TextView signupLink;
    TextInputEditText loginUser, loginPass;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        // Link XML layout to this Fragment
        View view = inflater.inflate(R.layout.fragment_login, container, false);

        loginButton = view.findViewById(R.id.login_button);
        signupLink = view.findViewById(R.id.signup_link);
        loginUser = view.findViewById(R.id.login_user);
        loginPass = view.findViewById(R.id.login_pass);

        loginButton.setOnClickListener(v -> {
            animateClickFeedback(v);

            String username = loginUser.getText().toString().trim();
            String password = loginPass.getText().toString().trim();

            LoginRequest request = new LoginRequest(username, password);

            ApiService apiService = ApiClient.getClient().create(ApiService.class);
            apiService.login(request).enqueue(new Callback<LoginResponse>() {
                @Override
                public void onResponse(Call<LoginResponse> call, Response<LoginResponse> response) {
                    if (response.isSuccessful() && response.body() != null) {
                        if ("success".equals(response.body().getStatus())) {
                            Toast.makeText(requireContext(), "Login Successful", Toast.LENGTH_SHORT).show();

                            //get the stuff from the response and put it into basically a global variable
                            SharedPreferences prefs = requireActivity().getSharedPreferences("MyAppPrefs", Context.MODE_PRIVATE);
                            SharedPreferences.Editor editor = prefs.edit();
                            LoginResponse body = response.body();
                            editor.putString("Username", body.getUser().getUsername());
                            editor.putString("Dormitory", body.getUser().getDormName());
                            editor.apply();

                            //calculate each of if the user has done each of the daily tasks
                            Integer completedTasks = body.getUser().getIsDone();

                            //the way this works is the number in it's binary representation corresponds to which tasks have been done
                            // e.g. 5 = 101 in binary means tasks 1 and 3
                            SharedPreferences tasks = requireActivity().getSharedPreferences("DailyTasks", Context.MODE_PRIVATE);
                            SharedPreferences.Editor tasks_editor = tasks.edit();
                            tasks_editor.putBoolean("checklist_item_1", (completedTasks & 0b001) != 0);
                            tasks_editor.putBoolean("checklist_item_2", (completedTasks & 0b010) != 0);
                            tasks_editor.putBoolean("checklist_item_3", (completedTasks & 0b100) != 0);

                            if(completedTasks >= 7)
                            {
                                //set everything complete if the number is 7 or more
                                tasks_editor.putBoolean("all_tasks", true);
                            }

                            tasks_editor.apply();

                            // Initialize user points from backend
                            DormPointsManager pointsManager = new DormPointsManager(requireContext());
                            pointsManager.initializePointsFromLogin(body.getUser().getSpendablePoints());

                            // go to dashboard
                            Intent intent = new Intent(requireContext(), DashboardActivity.class);
                            startActivity(intent);
                        } else {
                            Toast.makeText(requireContext(), "Login Failed: " + response.body().getMessage(), Toast.LENGTH_SHORT).show();
                            shakeLoginForm();
                        }
                    } else {
                        Toast.makeText(requireContext(), "API Error", Toast.LENGTH_SHORT).show();
                        shakeLoginForm();
                    }
                }

                @Override
                public void onFailure(Call<LoginResponse> call, Throwable t) {
                    Toast.makeText(requireContext(), "Network Error: " + t.getMessage(), Toast.LENGTH_LONG).show();
                    shakeLoginForm();
                }

            // go to dashboard

                /*
            Intent intent = new Intent(requireContext(), DashboardActivity.class);
            startActivity(intent);

                 */
            });
        });

        signupLink.setOnClickListener(v -> {
            animateClickFeedback(v);

            requireActivity().getSupportFragmentManager()
                    .beginTransaction()
                    .setCustomAnimations(
                            R.anim.slide_in_right,  // enter animation
                            R.anim.slide_out_left,  // exit animation
                            R.anim.slide_in_left,   // pop enter animation
                            R.anim.slide_out_right  // pop exit animation
                    )
                    .replace(R.id.login_signup_fragment_container, new SignupFragment())
                    .addToBackStack(null)
                    .commit();
        });

        // Add subtle focus animations to input fields
        addInputFocusAnimations();

        return view;
    }

    /**
     * Adds subtle scale animation when input fields gain focus
     */
    private void addInputFocusAnimations() {
        View.OnFocusChangeListener focusListener = (v, hasFocus) -> {
            float scale = hasFocus ? 1.02f : 1.0f;
            v.animate()
                    .scaleX(scale)
                    .scaleY(scale)
                    .setDuration(200)
                    .setInterpolator(new AccelerateDecelerateInterpolator())
                    .start();
        };

        loginUser.setOnFocusChangeListener(focusListener);
        loginPass.setOnFocusChangeListener(focusListener);
    }

    /**
     * Animates button click with squeeze effect
     */
    private void animateClickFeedback(View view) {
        view.animate()
                .scaleX(0.95f)
                .scaleY(0.95f)
                .setDuration(100)
                .withEndAction(() -> {
                    view.animate()
                            .scaleX(1.0f)
                            .scaleY(1.0f)
                            .setDuration(100)
                            .start();
                })
                .start();
    }

    /**
     * Shakes the login form when authentication fails
     */
    private void shakeLoginForm() {
        View loginForm = getView();
        if (loginForm != null) {
            Animation shake = AnimationUtils.loadAnimation(requireContext(), R.anim.shake);
            loginForm.startAnimation(shake);
        }
    }
}