package com.example.ecowattchtechdemo;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
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

        //variables for the isDone call, with default values
        Boolean[] isDoneBools = {false, false, false};



        loginButton.setOnClickListener(v -> {
            String username = loginUser.getText().toString().trim();
            String password = loginPass.getText().toString().trim();

            //add the code for the isDone call

            isDoneRequest doneRequest = new isDoneRequest(username);

            ApiService apiServiceIsDone = ApiClient.getClient().create(ApiService.class);
            apiServiceIsDone.isDone(doneRequest).enqueue(new Callback<isDoneResponse>() {
                @Override
                public void onResponse(Call<isDoneResponse> call, Response<isDoneResponse> response) {
                    if (response.isSuccessful() && response.body() != null) {
                        if ("success".equals(response.body().getStatus())) {
                            //uncomment this if you wanna test it
                            //Toast.makeText(requireContext(), "isDone gotten: " + response.body().getNumber().toString(), Toast.LENGTH_SHORT).show();

                            //conceptually how I've done this is storing the isDone stuff as a number
                            //and that number's binary representation tells us which tasks are done
                            //e.g., 0 = 000 = none done, 1 = 001 = task 3 done, 2 = 010 = task 2 done,
                            //3 = 011 = tasks 2 and 3 done, 4 = 100 = task 1 done, etc.


                            //calculate whether each check is done or not
                            int isDoneOne = response.body().getNumber() / 4;
                            int isDoneTwo = (response.body().getNumber() % 4) / 2;
                            int isDoneThree = response.body().getNumber() % 2;

                            //now do some if statements to assign the boolean values
                            if (isDoneOne == 1) {
                                isDoneBools[0] = true;
                            }
                            if (isDoneTwo == 1) {
                                isDoneBools[1] = true;
                            }
                            if (isDoneThree == 1) {
                                isDoneBools[2] = true;
                            }

                            //store the isDone values in SharedPreferences
                            SharedPreferences prefs = requireActivity().getSharedPreferences("MyAppPrefs", Context.MODE_PRIVATE);
                            SharedPreferences.Editor editor = prefs.edit();
                            editor.putBoolean("taskOneBool", isDoneBools[0]);
                            editor.putBoolean("taskTwoBool", isDoneBools[1]);
                            editor.putBoolean("taskThreeBool", isDoneBools[2]);
                            editor.apply();
                        } else {
                            Toast.makeText(requireContext(), "Login Failed: " + response.body().getStatus(), Toast.LENGTH_SHORT).show();
                        }
                    } else {
                        Toast.makeText(requireContext(), "API Error", Toast.LENGTH_SHORT).show();
                    }
                }

                @Override
                public void onFailure(Call<isDoneResponse> call, Throwable t) {
                    Toast.makeText(requireContext(), "Network Error: " + t.getMessage(), Toast.LENGTH_LONG).show();
                }
            });

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

                            // Initialize user points from backend
                            DormPointsManager pointsManager = new DormPointsManager(requireContext());
                            pointsManager.initializePointsFromLogin(body.getUser().getSpendablePoints());

                            // go to dashboard
                            Intent intent = new Intent(requireContext(), DashboardActivity.class);
                            startActivity(intent);
                        } else {
                            Toast.makeText(requireContext(), "Login Failed: " + response.body().getMessage(), Toast.LENGTH_SHORT).show();
                        }
                    } else {
                        Toast.makeText(requireContext(), "API Error", Toast.LENGTH_SHORT).show();
                    }
                }

                @Override
                public void onFailure(Call<LoginResponse> call, Throwable t) {
                    Toast.makeText(requireContext(), "Network Error: " + t.getMessage(), Toast.LENGTH_LONG).show();
                }

            // go to dashboard

                /*
            Intent intent = new Intent(requireContext(), DashboardActivity.class);
            startActivity(intent);

                 */
            });
        });

        signupLink.setOnClickListener(v -> {
            requireActivity().getSupportFragmentManager()
                    .beginTransaction()
                    .replace(R.id.login_signup_fragment_container, new SignupFragment())
                    .addToBackStack(null)
                    .commit();
        });

        return view;
    }
}