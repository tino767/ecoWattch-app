package com.example.ecowattchtechdemo;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.util.Log;
import android.widget.TextView;
import android.view.View;
import android.widget.Toast;
import android.animation.ObjectAnimator;
import android.view.animation.AccelerateDecelerateInterpolator;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import com.example.ecowattchtechdemo.gamification.DormPointsManager;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import com.example.ecowattchtechdemo.ApiResponse;
import com.example.ecowattchtechdemo.willow.models.Palette;
import com.google.gson.Gson;

public class ShopActivity extends AppCompatActivity {
    private static final String TAG = "ShopActivity";
    TextView backButton;
    TextView tabPallets, tabOwned;
    RecyclerView palletsRecycler, ownedRecycler;

    TextView usernameText, dormitoryText, energyPointsText;

    private ShopAdapter palletsAdapter;
    private ShopAdapter ownedAdapter;

    private List<ShopItem> palettesList;
    private List<ShopItem> ownedList;

    // theme manager
    private ThemeManager tm;


    // TEST PALETTE VALUES
    // Format: [primary, secondary, accent, background_main, background_light, gradient_start, gradient_end]
    // Indices 5 & 6 are the gradient colors that match the big UI circle gradients
    private Map<String, String[]> paletteColors = new HashMap<String, String[]>() {{
        put("PEACH", new String[]{"#FFFFFF", "#AAAAAA", "#CD232E", "#1B1B1B", "#262626", "#FFFFFF", "#CD232E"});
        put("BLUE", new String[]{"#060606", "#1B1B1B", "#1956DB", "#BCBCBC", "#7D7D7D", "#A5C9FF", "#1956DB"});
        put("GREEN", new String[]{"#FFFFFF", "#AAAAAA", "#19BD53", "#38916A", "#262626", "#C8F5D8", "#19BD53"});
        put("MAGENTA", new String[]{"#0F0F0F", "#AAAAAA", "#D719DB", "#EFEFEF", "#707070", "#F5C8F5", "#D719DB"});
        put("CYAN", new String[]{"#FFFFFF", "#AAAAAA", "#19DBD1", "#313131", "#262626", "#C8F5F0", "#19DBD1"});
    }};

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_shop);

        // Initialize views
        backButton = findViewById(R.id.back_button);
        tabPallets = findViewById(R.id.tab_pallets);
        tabOwned = findViewById(R.id.tab_owned);
        palletsRecycler = findViewById(R.id.pallets_recycler);
        ownedRecycler = findViewById(R.id.owned_recycler);

        usernameText = findViewById(R.id.username_text);
        dormitoryText = findViewById(R.id.dormitory_text);
        energyPointsText = findViewById(R.id.energy_points_text);

        // load and display stored username/dormitory
        SharedPreferences prefs = getSharedPreferences("MyAppPrefs", Context.MODE_PRIVATE);
        String username = prefs.getString("Username", "");
        String dorm = prefs.getString("Dormitory", "");

        usernameText.setText(username);
        dormitoryText.setText(dorm);

        // Update energy points display with user's actual individual spendable points
        DormPointsManager pointsManager = new DormPointsManager(this);
        int spendablePoints = pointsManager.getIndividualSpendablePoints();
        energyPointsText.setText(spendablePoints + " Energy");

        // Back button functionality with animation
        backButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                animateClickFeedback(view);
                view.postDelayed(() -> {
                    finish();
                    // Reverse animation: slide out to right, dashboard slides in from left
                    overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right);
                }, 150);
            }
        });

        // initialize ThemeManager
        tm = new ThemeManager(this);

        // Initialize empty lists first
        palettesList = new ArrayList<>();
        ownedList = new ArrayList<>();

        // Fetch palettes from API and initialize UI when response comes
        fetchPalettesFromApiAndInit();

        // Setup UI components (with empty data initially)
        setupRecyclerViews();
        setupTabs();
    }

    protected void onStart() {
        super.onStart();
        tm.applyTheme();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Update energy points when returning to shop
        updateEnergyPointsDisplay();
        tm.applyTheme();
    }

    private void updateEnergyPointsDisplay() {
        DormPointsManager pointsManager = new DormPointsManager(this);
        int spendablePoints = pointsManager.getIndividualSpendablePoints();
        energyPointsText.setText(spendablePoints + " Energy");
    }

    private void initializeSampleData() {
        // initialize offerings and owned lists with hardcoded defaults
            // defaults are peach and blue for now, can be changed.
        palettesList = new ArrayList<>();
        palettesList.add(new ShopItem("PEACH", 500,
                "#FFFFFF", "#AAAAAA", "#CD232E", "#1B1B1B", "#262626",
                true, false));
        palettesList.add(new ShopItem("MAGENTA", 500,
                "#0F0F0F", "#AAAAAA", "#D719DB", "#EFEFEF", "#707070",
                true, false));
        palettesList.add(new ShopItem("CYAN", 500,
                "#FFFFFF", "#AAAAAA", "#19DBD1", "#313131", "#262626",
                true, false));
        // demo palettes
        palettesList.add(new ShopItem("BLUE", 500,
                "#060606", "#1B1B1B", "#1956DB", "#BCBCBC", "#7D7D7D",
                false, false));
        palettesList.add(new ShopItem("GREEN", 500,
                "#FFFFFF", "#AAAAAA", "#19BD53", "#38916A", "#262626",
                false, false));

        ownedList = new ArrayList<>();
        ownedList.add(new ShopItem("PEACH", 500,
                "#FFFFFF", "#AAAAAA", "#CD232E", "#1B1B1B", "#262626",
                true, false));
        ownedList.add(new ShopItem("MAGENTA", 500,
                "#0F0F0F", "#AAAAAA", "#D719DB", "#EFEFEF", "#707070",
                true, false));
        ownedList.add(new ShopItem("CYAN", 500,
                "#FFFFFF", "#AAAAAA", "#19DBD1", "#313131", "#262626",
                true, false));

        // BACKEND: add API calls to get owned and offered palettes
            // use this format: (String name, int price,
            //            String colorPrimary, String colorSecondary, String colorAccent,
            //            String colorBackgroundPrimary, String colorBackgroundSecondary)
    }

    private void setupRecyclerViews() {
        // Setup Pallets RecyclerView with horizontal layout
        LinearLayoutManager palletsLayoutManager = new LinearLayoutManager(
                this, LinearLayoutManager.HORIZONTAL, false);
        palletsRecycler.setLayoutManager(palletsLayoutManager);

        palletsAdapter = new ShopAdapter(palettesList, new ShopAdapter.OnItemClickListener() {
            @Override
            public void onItemClick(ShopItem item, int position) {
                // Handle item click - check if owned or attempt purchase

                // get colors for clicked palette from paletteColors map
                String[] colors = paletteColors.get(item.getName());
                if (colors == null || colors.length < 5) return;

                // check for owned - only select if palette is owned
                if (item.isOwned()) {
                    // Apply the owned palette (user-specific)
                    applyPaletteColors(colors);

                    Toast.makeText(ShopActivity.this, "Applied " + item.getName() + " palette", Toast.LENGTH_SHORT).show();
                } else {
                    // preview the palette
                    previewPalette(colors);
                    // Attempt to purchase the palette
                    attemptPurchase(item, position);
                }
            }
        });
        palletsRecycler.setAdapter(palletsAdapter);

        // Setup Owned RecyclerView with horizontal layout
        LinearLayoutManager ownedLayoutManager = new LinearLayoutManager(
                this, LinearLayoutManager.HORIZONTAL, false);
        ownedRecycler.setLayoutManager(ownedLayoutManager);

        ownedAdapter = new ShopAdapter(ownedList, new ShopAdapter.OnItemClickListener() {
            @Override
            public void onItemClick(ShopItem item, int position) {
                // Handle owned item click

                // get colors for clicked palette from paletteColors map
                String[] colors = paletteColors.get(item.getName());
                if (colors == null || colors.length < 5) return;

                // Apply the owned palette (user-specific)
                applyPaletteColors(colors);

                Toast.makeText(ShopActivity.this, "Applied " + item.getName() + " palette", Toast.LENGTH_SHORT).show();
            }
        });
        ownedRecycler.setAdapter(ownedAdapter);
    }

    private void previewPalette(String[] colors) {
        // temporary color map
        Map<String, Integer> previewColors = new HashMap<>();
        previewColors.put("primary_color", Color.parseColor(colors[0]));
        previewColors.put("secondary_color", Color.parseColor(colors[1]));
        previewColors.put("accent_color", Color.parseColor(colors[2]));
        previewColors.put("primary_text", Color.parseColor(colors[0]));
        previewColors.put("secondary_text", Color.parseColor(colors[1]));
        previewColors.put("accent_text", Color.parseColor(colors[2]));
        previewColors.put("background_main", Color.parseColor(colors[3]));
        previewColors.put("background_light", Color.parseColor(colors[4]));

        // apply preview colors
        ThemeManager themeManager = new ThemeManager(this);
        themeManager.applyTheme(previewColors);
    }

    private void setupTabs() {
        // Pallets tab click listener with animation
        tabPallets.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                animateClickFeedback(view);
                view.postDelayed(() -> {
                    switchToTab(tabPallets);

                    // Sort palettes: unowned first, owned last
                    sortPalettesByOwnership();

                    // Fade in pallets recycler
                    palletsRecycler.setAlpha(0f);
                    palletsRecycler.setVisibility(View.VISIBLE);
                    palletsRecycler.animate()
                        .alpha(1f)
                        .setDuration(300)
                        .start();
                    ownedRecycler.setVisibility(View.GONE);
                }, 100);
            }
        });

        // Owned tab click listener with animation
        tabOwned.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                animateClickFeedback(view);
                view.postDelayed(() -> {
                    switchToTab(tabOwned);

                    // Fade in owned recycler
                    ownedRecycler.setAlpha(0f);
                    ownedRecycler.setVisibility(View.VISIBLE);
                    ownedRecycler.animate()
                        .alpha(1f)
                        .setDuration(300)
                        .start();
                    palletsRecycler.setVisibility(View.GONE);
                }, 100);
            }
        });

        // Set initial tab state and sort
        sortPalettesByOwnership();
        switchToTab(tabPallets);
    }

    /**
     * Sort palettes list: unowned items first, owned items last
     */
    private void sortPalettesByOwnership() {
        java.util.Collections.sort(palettesList, new java.util.Comparator<ShopItem>() {
            @Override
            public int compare(ShopItem item1, ShopItem item2) {
                // Unowned (false) should come before owned (true)
                // false < true, so we compare the boolean values
                return Boolean.compare(item1.isOwned(), item2.isOwned());
            }
        });

        // Notify adapter that data has changed
        if (palletsAdapter != null) {
            palletsAdapter.notifyDataSetChanged();
        }

        Log.d(TAG, "Palettes sorted: unowned first, owned last");
    }

    private void switchToTab(TextView selectedTab) {
        // Get the custom font using backwards compatible method
        Typeface matrixFont = null;
        try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                matrixFont = getResources().getFont(R.font.matrixtype_display);
            } else {
                matrixFont = androidx.core.content.res.ResourcesCompat.getFont(this, R.font.matrixtype_display);
            }
        } catch (Exception e) {
            // Fallback to default font if custom font fails
            matrixFont = Typeface.DEFAULT;
        }

        // Reset all tabs to normal style (preserving custom font)
        tabPallets.setTypeface(matrixFont, Typeface.NORMAL);
        tabOwned.setTypeface(matrixFont, Typeface.NORMAL);

        // Set selected tab to bold (preserving custom font)
        selectedTab.setTypeface(matrixFont, Typeface.BOLD);
    }

    // ---fetch palettes from API and then initialize UI ---
    // BACKEND - have this use palettesList and ownedList instead
    private void fetchPalettesFromApiAndInit() {
        ApiService api = ApiClient.getClient().create(ApiService.class);
        Call<ApiResponse> call = api.getPalettes();
        call.enqueue(new Callback<ApiResponse>() {
            @Override
            public void onResponse(Call<ApiResponse> call, Response<ApiResponse> response) {
                Log.d(TAG, "Palettes request URL: " + call.request().url());
                if (response.isSuccessful() && response.body() != null) {
                    Log.d(TAG, "Palettes response body: " + new Gson().toJson(response.body()));
                } else {
                    Log.w(TAG, "Palettes response unsuccessful. code=" + response.code()
                            + " error=" + (response.errorBody() != null ? response.errorBody().toString() : "null"));
                }

                if (response.isSuccessful() && response.body() != null && "success".equalsIgnoreCase(response.body().status)) {
                    if (response.body().palettes != null) {
                        for (Palette p : response.body().palettes) {
                            if (p == null || p.offeringName == null) continue;

                            // Trim and normalize the offering name so it matches your keys
                            String key = p.offeringName.trim().toUpperCase();
                            if (key.isEmpty()) continue;

                            String[] arr = new String[]{
                                    safeHex(p.colorHex1),
                                    safeHex(p.colorHex2),
                                    safeHex(p.colorHex3),
                                    safeHex(p.colorHex4),
                                    safeHex(p.colorHex5),
                                    safeHex(p.colorHex6),
                                    safeHex(p.colorHex7)
                            };

                            paletteColors.put(key, arr);
                            Log.d(TAG, "Updated paletteColors[" + key + "] = " + java.util.Arrays.toString(arr));
                        }
                        
                        // IMPORTANT: Update UI with new palette data
                        runOnUiThread(() -> {
                            updatePalettesWithApiData();
                        });
                    }
                } else {
                    Log.w(TAG, "Palettes API response unsuccessful or empty");
                }
            }

            @Override
            public void onFailure(Call<ApiResponse> call, Throwable t) {
                Log.w(TAG, "Failed to fetch palettes: " + t.getMessage());
            }
        });
    }

    private String safeHex(String hex) {
        return (hex != null && !hex.isEmpty()) ? hex : "#000000";
    }

    /**
     * Update the palettes list and UI with API data
     */
    private void updatePalettesWithApiData() {
        Log.d(TAG, "Updating palettes with API data");

        // Clear existing palettes list
        palettesList.clear();
        
        // Get owned palettes from persistent storage
        Set<String> ownedPalettes = getOwnedPalettes();

        // Recreate palette items with API data
        for (String paletteName : paletteColors.keySet()) {
            String[] colors = paletteColors.get(paletteName);
            if (colors != null && colors.length >= 7) {
                ShopItem item = new ShopItem(paletteName, 500, colors[5], colors[6]);

                // Set ownership based on saved data (PEACH is owned by default for new users)
                if (ownedPalettes.contains(paletteName) || "PEACH".equals(paletteName)) {
                    item.setOwned(true);
                }

                palettesList.add(item);
                Log.d(TAG, "Added palette: " + paletteName + " (owned=" + item.isOwned() + ") with colors: " + java.util.Arrays.toString(colors));
            }
        }

        // Update owned list with API data
        ownedList.clear();
        for (ShopItem item : palettesList) {
            if (item.isOwned()) {
                ShopItem ownedItem = new ShopItem(item.getName(), item.getPrice(), 
                    paletteColors.get(item.getName())[5], paletteColors.get(item.getName())[6]);
                ownedItem.setOwned(true);
                ownedList.add(ownedItem);
            }
        }
        
        // Sort palettes and notify adapters
        sortPalettesByOwnership();
        
        if (palletsAdapter != null) {
            palletsAdapter.notifyDataSetChanged();
        }
        if (ownedAdapter != null) {
            ownedAdapter.notifyDataSetChanged();
        }

        Log.d(TAG, "UI updated with " + palettesList.size() + " palettes from API");
    }

    /**
     * Attempt to purchase a palette item
     */
    private void attemptPurchase(ShopItem item, int position) {
        DormPointsManager pointsManager = new DormPointsManager(this);
        int currentPoints = pointsManager.getIndividualSpendablePoints();
        int itemCost = item.getPrice();

        if (currentPoints < itemCost) {
            Toast.makeText(this, "Not enough points! Need " + itemCost + ", have " + currentPoints,
                          Toast.LENGTH_LONG).show();
            return;
        }

        // Show confirmation dialog
        new android.app.AlertDialog.Builder(this)
            .setTitle("Purchase " + item.getName())
            .setMessage("This will cost " + itemCost + " energy points. Continue?")
            .setPositiveButton("Purchase", (dialog, which) -> {
                processPurchase(item, position, pointsManager, itemCost);
            })
            .setNegativeButton("Cancel", null)
            .show();
    }

    /**
     * Process the actual purchase
     */
    private void processPurchase(ShopItem item, int position, DormPointsManager pointsManager, int itemCost) {
        // Deduct points locally first
        boolean success = pointsManager.spendIndividualPoints(itemCost);

        if (success) {
            // Update item ownership
            item.setOwned(true);

            // Save ownership to persistent storage
            saveOwnedPalette(item.getName());

            // Re-sort the palettes list so owned item moves to the end
            sortPalettesByOwnership();

            palletsAdapter.notifyDataSetChanged();

            // Add to owned list if not already there
            boolean alreadyInOwned = false;
            for (ShopItem ownedItem : ownedList) {
                if (ownedItem.getName().equals(item.getName())) {
                    alreadyInOwned = true;
                    break;
                }
            }

            if (!alreadyInOwned) {
                // Get the full color array from paletteColors map
                String[] colors = paletteColors.get(item.getName());
                ShopItem ownedItem = new ShopItem(item.getName(), item.getPrice(),
                    colors[5], colors[6]);
                ownedItem.setOwned(true);
                ownedList.add(ownedItem);
                ownedAdapter.notifyDataSetChanged();
            }

            // Update points display
            updateEnergyPointsDisplay();

            // Sync with backend
            syncPurchaseWithBackend(item.getName(), itemCost);

            Toast.makeText(this, "Successfully purchased " + item.getName() + "!", Toast.LENGTH_SHORT).show();

            // Apply the newly purchased palette immediately
            applyPalette(item);
        } else {
            Toast.makeText(this, "Purchase failed. Please try again.", Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * Apply a palette from ShopItem (user-specific)
     */
    private void applyPalette(ShopItem item) {
        // Get the full color array from paletteColors map
        String[] colors = paletteColors.get(item.getName());
        if (colors == null || colors.length < 5) return;

        applyPaletteColors(colors);
    }

    /**
     * Apply palette colors to user-specific theme preferences
     */
    private void applyPaletteColors(String[] colors) {
        if (colors == null || colors.length < 5) return;

        // Get current username for user-specific storage
        SharedPreferences userPrefs = getSharedPreferences("MyAppPrefs", MODE_PRIVATE);
        String username = userPrefs.getString("Username", "");

        if (username.isEmpty()) {
            Log.w(TAG, "No username found - cannot apply palette");
            return;
        }

        SharedPreferences prefs = getSharedPreferences("ThemePrefs", MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();

        // Store colors with user-specific keys
        editor.putString("primary_color_" + username, colors[0]);
        editor.putString("secondary_color_" + username, colors[1]);
        editor.putString("accent_color_" + username, colors[2]);
        editor.putString("background_main_" + username, colors[3]);
        editor.putString("background_light_" + username, colors[4]);

        editor.apply();

        // Apply theme immediately
        tm.applyTheme();

        Log.d(TAG, "Applied palette colors for user: " + username);
    }

    /**
     * Sync purchase with backend
     */
    private void syncPurchaseWithBackend(String paletteName, int pointsDeducted) {
        SharedPreferences prefs = getSharedPreferences("MyAppPrefs", Context.MODE_PRIVATE);
        String username = prefs.getString("Username", "");

        if (username.isEmpty()) {
            Log.w(TAG, "No username found for backend sync");
            return;
        }

        ApiService api = ApiClient.getClient().create(ApiService.class);
        PurchaseRequest request = new PurchaseRequest(username, paletteName, pointsDeducted);

        Call<PurchaseResponse> call = api.purchasePalette(request);
        call.enqueue(new Callback<PurchaseResponse>() {
            @Override
            public void onResponse(Call<PurchaseResponse> call, Response<PurchaseResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    PurchaseResponse purchaseResponse = response.body();
                    if ("success".equals(purchaseResponse.getStatus())) {
                        Log.d(TAG, "Purchase synced with backend successfully");
                        // Update local points to match backend
                        DormPointsManager pointsManager = new DormPointsManager(ShopActivity.this);
                        pointsManager.setIndividualSpendablePoints(purchaseResponse.getNewPointTotal());
                        updateEnergyPointsDisplay();
                    } else {
                        Log.w(TAG, "Backend purchase failed: " + purchaseResponse.getMessage());
                        Toast.makeText(ShopActivity.this, "Purchase saved locally, sync failed", Toast.LENGTH_SHORT).show();
                    }
                } else {
                    Log.w(TAG, "Backend purchase request failed");
                    Toast.makeText(ShopActivity.this, "Purchase saved locally, sync failed", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(Call<PurchaseResponse> call, Throwable t) {
                Log.w(TAG, "Backend purchase request error: " + t.getMessage());
                Toast.makeText(ShopActivity.this, "Purchase saved locally, sync failed", Toast.LENGTH_SHORT).show();
            }
        });
    }

    /**
     * Save an owned palette to persistent storage (user-specific)
     */
    private void saveOwnedPalette(String paletteName) {
        // Get current username for user-specific storage
        SharedPreferences userPrefs = getSharedPreferences("MyAppPrefs", MODE_PRIVATE);
        String username = userPrefs.getString("Username", "");

        if (username.isEmpty()) {
            Log.w(TAG, "No username found - cannot save owned palette");
            return;
        }

        SharedPreferences prefs = getSharedPreferences("ShopPrefs", MODE_PRIVATE);
        String key = "owned_palettes_" + username;
        Set<String> ownedPalettes = prefs.getStringSet(key, new java.util.HashSet<>());

        // Create a new set to avoid modification issues
        Set<String> updatedOwned = new java.util.HashSet<>(ownedPalettes);
        updatedOwned.add(paletteName);

        prefs.edit().putStringSet(key, updatedOwned).apply();
        Log.d(TAG, "Saved owned palette for " + username + ": " + paletteName + " (total owned: " + updatedOwned.size() + ")");
    }

    /**
     * Get all owned palettes from persistent storage (user-specific)
     */
    private Set<String> getOwnedPalettes() {
        // Get current username for user-specific storage
        SharedPreferences userPrefs = getSharedPreferences("MyAppPrefs", MODE_PRIVATE);
        String username = userPrefs.getString("Username", "");

        if (username.isEmpty()) {
            Log.w(TAG, "No username found - returning default palette only");
            Set<String> defaultSet = new java.util.HashSet<>();
            defaultSet.add("PEACH");
            return defaultSet;
        }

        SharedPreferences prefs = getSharedPreferences("ShopPrefs", MODE_PRIVATE);
        String key = "owned_palettes_" + username;
        Set<String> ownedPalettes = prefs.getStringSet(key, new java.util.HashSet<>());

        // Always include PEACH as owned by default
        Set<String> result = new java.util.HashSet<>(ownedPalettes);
        result.add("PEACH");

        Log.d(TAG, "Loaded owned palettes for " + username + ": " + result);
        return result;
    }

    /**
     * Animate click feedback for interactive elements
     * Creates a scale-down and scale-up effect for button press
     */
    private void animateClickFeedback(View view) {
        ObjectAnimator scaleX = ObjectAnimator.ofFloat(view, "scaleX", 1.0f, 0.95f, 1.0f);
        ObjectAnimator scaleY = ObjectAnimator.ofFloat(view, "scaleY", 1.0f, 0.95f, 1.0f);

        scaleX.setDuration(200);
        scaleY.setDuration(200);
        scaleX.setInterpolator(new android.view.animation.AccelerateDecelerateInterpolator());
        scaleY.setInterpolator(new android.view.animation.AccelerateDecelerateInterpolator());

        scaleX.start();
        scaleY.start();
    }

}
