package com.example.ecowattchtechdemo;

import android.graphics.Typeface;
import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import android.widget.Button;
import android.widget.TextView;
import android.view.View;
import java.util.ArrayList;
import java.util.List;

public class ShopActivity extends AppCompatActivity {
    Button backButton;
    TextView tabPallets, tabOwned, tabMore;
    RecyclerView palletsRecycler, ownedRecycler;

    private ShopAdapter palletsAdapter;
    private ShopAdapter ownedAdapter;

    private List<ShopItem> palletsList;
    private List<ShopItem> ownedList;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_shop);

        // Initialize views
        backButton = findViewById(R.id.back_button);
        tabPallets = findViewById(R.id.tab_pallets);
        tabOwned = findViewById(R.id.tab_owned);
        tabMore = findViewById(R.id.tab_more);
        palletsRecycler = findViewById(R.id.pallets_recycler);
        ownedRecycler = findViewById(R.id.owned_recycler);

        // Back button functionality
        backButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                finish();
            }
        });

        // Initialize sample data (BACKEND YOU REPLACE DATA HERE)
        initializeSampleData();

        // Setup RecyclerViews with horizontal scrolling
        setupRecyclerViews();

        // Setup tab click listeners
        setupTabs();
    }

    private void initializeSampleData() {
        // Sample palette data - AND HERE
        palletsList = new ArrayList<>();
        palletsList.add(new ShopItem("PEACH", 400, R.drawable.gradient_circle_extended));
        palletsList.add(new ShopItem("OCEAN", 500, R.drawable.gradient_circle_extended));
        palletsList.add(new ShopItem("SUNSET", 600, R.drawable.gradient_circle_extended));
        palletsList.add(new ShopItem("FOREST", 450, R.drawable.gradient_circle_extended));
        palletsList.add(new ShopItem("LAVENDER", 550, R.drawable.gradient_circle_extended));

        // Sample owned items - backend will replace with user's owned items
        ownedList = new ArrayList<>();
        ownedList.add(new ShopItem("PEACH", 400, R.drawable.gradient_circle_extended));
        ownedList.get(0).setOwned(true);
    }

    private void setupRecyclerViews() {
        // Setup Pallets RecyclerView with horizontal layout
        LinearLayoutManager palletsLayoutManager = new LinearLayoutManager(
                this, LinearLayoutManager.HORIZONTAL, false);
        palletsRecycler.setLayoutManager(palletsLayoutManager);

        palletsAdapter = new ShopAdapter(palletsList, new ShopAdapter.OnItemClickListener() {
            @Override
            public void onItemClick(ShopItem item, int position) {
                // Handle item click - backend can add purchase logic here - Risa you use palette handler here)
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
                // Handle owned item click - MORE BACKEND HERE
            }
        });
        ownedRecycler.setAdapter(ownedAdapter);
    }

    private void setupTabs() {
        // Pallets tab click listener
        tabPallets.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                switchToTab(tabPallets);
                palletsRecycler.setVisibility(View.VISIBLE);
                ownedRecycler.setVisibility(View.GONE);
            }
        });

        // Owned tab click listener
        tabOwned.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                switchToTab(tabOwned);
                palletsRecycler.setVisibility(View.GONE);
                ownedRecycler.setVisibility(View.VISIBLE);
            }
        });

        // More tab click listener (placeholder for alpha)
        tabMore.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                // Placeholder for future tabs like icons
            }
        });

        // Set initial tab state
        switchToTab(tabPallets);
    }

    private void switchToTab(TextView selectedTab) {
        // Reset all tabs to normal style
        tabPallets.setTypeface(null, Typeface.NORMAL);
        tabOwned.setTypeface(null, Typeface.NORMAL);
        tabMore.setTypeface(null, Typeface.NORMAL);

        // Set selected tab to bold
        selectedTab.setTypeface(null, Typeface.BOLD);
    }
}
