package com.example.ecowattchtechdemo.willow;

import android.content.Context;
import android.util.Log;
import com.example.ecowattchtechdemo.willow.models.*;
import com.example.ecowattchtechdemo.gamification.DormPointsManager;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import java.util.*;

/**
 * Manager for Willow API operations and energy data
 */
public class WillowEnergyDataManager {
    
    private static final String TAG = "WillowEnergyManager";
    
    private WillowApiService apiService;
    private String accessToken;
    private long tokenExpirationTime;
    private DormPointsManager pointsManager; // Add gamification integration
    
    // Building mappings for both Power (kW) and Energy (kWh) twin IDs
    private final Map<String, String> buildingNames = new HashMap<String, String>() {{
        // Power Twin IDs (kW - instantaneous power)
        put(WillowApiV3Config.TWIN_ID_TINSLEY, "TINSLEY");
        put(WillowApiV3Config.TWIN_ID_GABALDON, "GABALDON");
        put(WillowApiV3Config.TWIN_ID_SECHRIST, "SECHRIST");
        
        // Energy Twin IDs (kWh - cumulative energy)
        put(WillowApiV3Config.TWIN_ID_TINSLEY_ENERGY, "TINSLEY");
        put(WillowApiV3Config.TWIN_ID_GABALDON_ENERGY, "GABALDON");
        put(WillowApiV3Config.TWIN_ID_SECHRIST_ENERGY, "SECHRIST");
    }};
    
    // Remove hardcoded positions - now dynamic based on points
    private final Map<String, String> buildingPositions = new HashMap<String, String>() {{
        put("TINSLEY", "1ST PLACE");
        put("GABALDON", "2ND PLACE");
        put("SECHRIST", "3RD PLACE");
    }};
    
    public WillowEnergyDataManager() {
        this.apiService = WillowApiClient.getApiService();
    }
    
    public WillowEnergyDataManager(String baseUrl) {
        this.apiService = WillowApiClient.getApiService(baseUrl);
    }
    
    /**
     * Constructor with context for gamification features
     */
    public WillowEnergyDataManager(Context context) {
        this.apiService = WillowApiClient.getApiService();
        this.pointsManager = new DormPointsManager(context);
        
        // 🏆 Initialize real rankings based on existing energy data
        initializeRealRankings();
    }
    
    public WillowEnergyDataManager(String baseUrl, Context context) {
        this.apiService = WillowApiClient.getApiService(baseUrl);
        this.pointsManager = new DormPointsManager(context);
        
        // 🏆 Initialize real rankings based on existing energy data  
        initializeRealRankings();
    }
    
    /**
     * Interface for energy data callbacks
     */
    public interface EnergyDataCallback {
        void onSuccess(EnergyDataResponse data);
        void onError(String error);
    }
    
    public interface AuthenticationCallback {
        void onSuccess(String token);
        void onError(String error);
    }
    
    /**
     * Authenticate with Willow API
     */
    public void authenticate(String clientId, String clientSecret, AuthenticationCallback callback) {
        Log.d(TAG, "🔐 Starting Willow API authentication...");
        Log.d(TAG, "🔐 Client ID: " + (clientId != null ? clientId.substring(0, Math.min(8, clientId.length())) + "..." : "null"));
        Log.d(TAG, "🔐 Client Secret: " + (clientSecret != null ? "***provided***" : "null"));
        Log.d(TAG, "🔐 Grant Type: " + WillowApiV3Config.GRANT_TYPE);
        
        if (clientId == null || clientId.isEmpty() || clientSecret == null || clientSecret.isEmpty()) {
            String error = "Missing authentication credentials";
            Log.e(TAG, "❌ " + error);
            callback.onError(error);
            return;
        }
        
        Call<WillowOAuthResponse> call = apiService.getOAuthToken(
            clientId, 
            clientSecret, 
            WillowApiV3Config.GRANT_TYPE
        );
        
        Log.d(TAG, "🌐 Sending authentication request...");
        
        call.enqueue(new Callback<WillowOAuthResponse>() {
            @Override
            public void onResponse(Call<WillowOAuthResponse> call, Response<WillowOAuthResponse> response) {
                Log.d(TAG, "📡 Authentication response received - Code: " + response.code());
                
                if (response.isSuccessful() && response.body() != null) {
                    WillowOAuthResponse tokenResponse = response.body();
                    Log.d(TAG, "✅ Authentication response body received");
                    
                    if (tokenResponse.isValid()) {
                        accessToken = tokenResponse.getAuthorizationHeader();
                        tokenExpirationTime = System.currentTimeMillis() + (tokenResponse.getExpiresIn() * 1000L);
                        
                        Log.d(TAG, "🎉 Authentication successful!");
                        Log.d(TAG, "🔑 Token expires in: " + tokenResponse.getExpiresIn() + " seconds");
                        callback.onSuccess(accessToken);
                    } else {
                        Log.e(TAG, "❌ Invalid token response - token validation failed");
                        callback.onError("Invalid authentication response");
                    }
                } else {
                    String error = "Authentication failed: HTTP " + response.code();
                    if (response.errorBody() != null) {
                        try {
                            String errorBody = response.errorBody().string();
                            error += " - " + errorBody;
                            Log.e(TAG, "❌ " + error);
                        } catch (Exception e) {
                            Log.e(TAG, "❌ " + error + " (could not read error body)");
                        }
                    } else {
                        Log.e(TAG, "❌ " + error);
                    }
                    callback.onError(error);
                }
            }
            
            @Override
            public void onFailure(Call<WillowOAuthResponse> call, Throwable t) {
                String error = "Authentication network error: " + t.getMessage();
                Log.e(TAG, "❌ " + error, t);
                
                // Log more specific network error details
                if (t instanceof java.net.UnknownHostException) {
                    Log.e(TAG, "❌ DNS resolution failed - cannot reach Willow API server");
                } else if (t instanceof java.net.ConnectException) {
                    Log.e(TAG, "❌ Connection refused - Willow API server may be down");
                } else if (t instanceof java.net.SocketTimeoutException) {
                    Log.e(TAG, "❌ Connection timeout - network may be slow or server unresponsive");
                }
                
                callback.onError(error);
            }
        });
    }
    
    /**
     * Get energy data for a specific building (Power - kW)
     */
    public void getEnergyData(String buildingTwinId, EnergyDataCallback callback) {
        if (!isAuthenticated()) {
            callback.onError("Not authenticated. Please authenticate first.");
            return;
        }
        
        final String buildingName = buildingNames.containsKey(buildingTwinId) ? 
            buildingNames.get(buildingTwinId) : "UNKNOWN";
        
        Log.d(TAG, "Fetching POWER data for building: " + buildingName + " (" + buildingTwinId + ")");
        
        // Use the twin ID directly - no search needed since IDs are verified correct
        getDirectTwinData(buildingTwinId, callback);
    }

    /**
     * Get energy consumption data for a specific building (Energy - kWh)
     */
    public void getEnergyConsumptionData(String buildingEnergyTwinId, EnergyDataCallback callback) {
        if (!isAuthenticated()) {
            callback.onError("Not authenticated. Please authenticate first.");
            return;
        }
        
        final String buildingName = buildingNames.containsKey(buildingEnergyTwinId) ? 
            buildingNames.get(buildingEnergyTwinId) : "UNKNOWN";
        
        Log.d(TAG, "Fetching ENERGY CONSUMPTION data for building: " + buildingName + " (" + buildingEnergyTwinId + ")");
        
        // Use the twin ID directly - no search needed since IDs are verified correct
        getDirectTwinData(buildingEnergyTwinId, callback);
    }
    
    /**
     * Get data directly using the provided twin ID (no search needed)
     */
    private void getDirectTwinData(String twinId, EnergyDataCallback callback) {
        final String buildingName = buildingNames.containsKey(twinId) ? 
            buildingNames.get(twinId) : "UNKNOWN";
            
        // Get twin directly using the verified correct ID
        Call<DigitalTwin> twinCall = apiService.getTwinById(accessToken, twinId, true);
        
        twinCall.enqueue(new Callback<DigitalTwin>() {
            @Override
            public void onResponse(Call<DigitalTwin> call, Response<DigitalTwin> response) {
                if (response.isSuccessful() && response.body() != null) {
                    DigitalTwin building = response.body();
                    Log.d(TAG, "Twin found: " + building.getName());
                    
                    // Get time series data directly
                    getDirectTimeSeries(building, callback);
                } else {
                    Log.w(TAG, "Twin not accessible: " + response.code() + " for ID: " + twinId);
                    EnergyDataResponse fallbackData = createFallbackData(buildingName, twinId);
                    callback.onSuccess(fallbackData);
                }
            }
            
            @Override
            public void onFailure(Call<DigitalTwin> call, Throwable t) {
                Log.w(TAG, "Network error accessing twin: " + t.getMessage());
                EnergyDataResponse fallbackData = createFallbackData(buildingName, twinId);
                callback.onSuccess(fallbackData);
            }
        });
    }
    
    /**
     * Get time series data directly from twin
     */
    private void getDirectTimeSeries(DigitalTwin building, EnergyDataCallback callback) {
        Call<List<TimeSeriesPoint>> directCall = apiService.getLatestTimeSeries(
            accessToken, building.getId(), true
        );
        
        directCall.enqueue(new Callback<List<TimeSeriesPoint>>() {
            @Override
            public void onResponse(Call<List<TimeSeriesPoint>> call, Response<List<TimeSeriesPoint>> response) {
                String buildingName = buildingNames.containsKey(building.getId()) ? 
                    buildingNames.get(building.getId()) : building.getName();
                    
                if (response.isSuccessful() && response.body() != null) {
                    List<TimeSeriesPoint> points = response.body();
                    
                    if (!points.isEmpty()) {
                        // Process real time series data
                        EnergyDataResponse energyData = processTimeSeriesData(points, buildingName, building.getId());
                        callback.onSuccess(energyData);
                    } else {
                        // No time series data available
                        Log.d(TAG, "No time series data for " + buildingName);
                        EnergyDataResponse fallbackData = createFallbackData(buildingName, building.getId());
                        callback.onSuccess(fallbackData);
                    }
                } else {
                    Log.w(TAG, "Time series request failed: " + response.code());
                    EnergyDataResponse fallbackData = createFallbackData(buildingName, building.getId());
                    callback.onSuccess(fallbackData);
                }
            }
            
            @Override
            public void onFailure(Call<List<TimeSeriesPoint>> call, Throwable t) {
                Log.w(TAG, "Time series network error: " + t.getMessage());
                String buildingName = buildingNames.containsKey(building.getId()) ? 
                    buildingNames.get(building.getId()) : building.getName();
                EnergyDataResponse fallbackData = createFallbackData(buildingName, building.getId());
                callback.onSuccess(fallbackData);
            }
        });
    }
    
    /**
     * Find energy capability twins for the building - Simplified approach to avoid 500 errors
     */
    private void findEnergyCapabilities(DigitalTwin building, String buildingName, EnergyDataCallback callback) {
        Log.d(TAG, "Searching for energy capabilities using simplified approach");
        
        // Try a simpler search first - just search for energy model types
        Map<String, Object> searchRequest = new HashMap<>();
        
        // Simplified model filter - search one model at a time to avoid complex queries
        Map<String, Object> modelFilter = new HashMap<>();
        modelFilter.put("modelIds", Arrays.asList(WillowApiV3Config.ENERGY_CONSUMPTION_MODEL));
        modelFilter.put("exactModelMatch", false);
        
        searchRequest.put("modelFilter", modelFilter);
        searchRequest.put("pageSize", 20); // Reduced page size
        
        Call<TwinsResponse> searchCall = apiService.searchTwins(accessToken, searchRequest);
        
        searchCall.enqueue(new Callback<TwinsResponse>() {
            @Override
            public void onResponse(Call<TwinsResponse> call, Response<TwinsResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    TwinsResponse twinsResponse = response.body();
                    
                    if (twinsResponse.hasTwins()) {
                        Log.d(TAG, "Found " + twinsResponse.getContent().size() + " energy capabilities");
                        
                        // Filter capabilities that might be related to our building
                        List<String> capabilityIds = new ArrayList<>();
                        for (DigitalTwin twin : twinsResponse.getContent()) {
                            // Simple filtering - include all found capabilities for now
                            capabilityIds.add(twin.getId());
                            Log.d(TAG, "Found energy capability: " + twin.getName() + " (" + twin.getId() + ")");
                        }
                        
                        if (!capabilityIds.isEmpty()) {
                            getTimeSeriesData(capabilityIds, buildingName, building.getId(), callback);
                        } else {
                            Log.w(TAG, "No valid energy capabilities found");
                            EnergyDataResponse fallbackData = createFallbackData(buildingName, building.getId());
                            callback.onSuccess(fallbackData);
                        }
                    } else {
                        Log.w(TAG, "No energy capabilities found in response");
                        // Try alternative approach - search for power consumption model
                        searchForAlternativeCapabilities(buildingName, building.getId(), callback);
                    }
                } else {
                    String errorMsg = "Failed to search energy capabilities: " + response.code();
                    if (response.errorBody() != null) {
                        try {
                            errorMsg += " - " + response.errorBody().string();
                        } catch (Exception e) {
                            errorMsg += " (could not read error body)";
                        }
                    }
                    Log.e(TAG, errorMsg);
                    
                    // Try alternative approach before falling back
                    searchForAlternativeCapabilities(buildingName, building.getId(), callback);
                }
            }
            
            @Override
            public void onFailure(Call<TwinsResponse> call, Throwable t) {
                Log.e(TAG, "Network error searching capabilities: " + t.getMessage(), t);
                // Try alternative approach before falling back
                searchForAlternativeCapabilities(buildingName, building.getId(), callback);
            }
        });
    }
    
    /**
     * Alternative search approach using different model types
     */
    private void searchForAlternativeCapabilities(String buildingName, String buildingId, EnergyDataCallback callback) {
        Log.d(TAG, "Trying alternative search for power consumption capabilities");
        
        Map<String, Object> searchRequest = new HashMap<>();
        Map<String, Object> modelFilter = new HashMap<>();
        modelFilter.put("modelIds", Arrays.asList(WillowApiV3Config.POWER_CONSUMPTION_MODEL));
        modelFilter.put("exactModelMatch", false);
        
        searchRequest.put("modelFilter", modelFilter);
        searchRequest.put("pageSize", 20);
        
        Call<TwinsResponse> searchCall = apiService.searchTwins(accessToken, searchRequest);
        
        searchCall.enqueue(new Callback<TwinsResponse>() {
            @Override
            public void onResponse(Call<TwinsResponse> call, Response<TwinsResponse> response) {
                if (response.isSuccessful() && response.body() != null && response.body().hasTwins()) {
                    TwinsResponse twinsResponse = response.body();
                    Log.d(TAG, "Found " + twinsResponse.getContent().size() + " power capabilities");
                    
                    List<String> capabilityIds = new ArrayList<>();
                    for (DigitalTwin twin : twinsResponse.getContent()) {
                        capabilityIds.add(twin.getId());
                        Log.d(TAG, "Found power capability: " + twin.getName() + " (" + twin.getId() + ")");
                    }
                    
                    getTimeSeriesData(capabilityIds, buildingName, buildingId, callback);
                } else {
                    Log.w(TAG, "Alternative search also failed, trying direct building twin approach");
                    // Try direct approach with the building twin ID
                    tryDirectBuildingApproach(buildingName, buildingId, callback);
                }
            }
            
            @Override
            public void onFailure(Call<TwinsResponse> call, Throwable t) {
                Log.e(TAG, "Alternative search failed: " + t.getMessage(), t);
                // Try direct approach as last resort
                tryDirectBuildingApproach(buildingName, buildingId, callback);
            }
        });
    }
    
    /**
     * Try to get time series data directly from the building twin
     */
    private void tryDirectBuildingApproach(String buildingName, String buildingId, EnergyDataCallback callback) {
        Log.d(TAG, "Attempting direct building twin approach for time series data");
        
        // Try to get time series data directly using the building twin ID
        List<String> directIds = Arrays.asList(buildingId);
        
        Call<List<TimeSeriesPoint>> directCall = apiService.getLatestTimeSeriesValues(
            accessToken, directIds, true
        );
        
        directCall.enqueue(new Callback<List<TimeSeriesPoint>>() {
            @Override
            public void onResponse(Call<List<TimeSeriesPoint>> call, Response<List<TimeSeriesPoint>> response) {
                if (response.isSuccessful() && response.body() != null && !response.body().isEmpty()) {
                    Log.d(TAG, "Direct approach succeeded! Found time series data");
                    List<TimeSeriesPoint> timeSeriesData = response.body();
                    EnergyDataResponse energyData = processTimeSeriesData(timeSeriesData, buildingName, buildingId);
                    callback.onSuccess(energyData);
                } else {
                    Log.w(TAG, "Direct approach failed, using fallback data. Response code: " + response.code());
                    EnergyDataResponse fallbackData = createFallbackData(buildingName, buildingId);
                    callback.onSuccess(fallbackData);
                }
            }
            
            @Override
            public void onFailure(Call<List<TimeSeriesPoint>> call, Throwable t) {
                Log.e(TAG, "Direct approach network error: " + t.getMessage(), t);
                EnergyDataResponse fallbackData = createFallbackData(buildingName, buildingId);
                callback.onSuccess(fallbackData);
            }
        });
    }
    
    /**
     * Get time series data for energy capabilities
     */
    private void getTimeSeriesData(List<String> capabilityIds, String buildingName, String buildingId, EnergyDataCallback callback) {
        if (capabilityIds.isEmpty()) {
            Log.w(TAG, "No capability IDs provided for time series data");
            EnergyDataResponse fallbackData = createFallbackData(buildingName, buildingId);
            callback.onSuccess(fallbackData);
            return;
        }
        
        Log.d(TAG, "Requesting time series data for " + capabilityIds.size() + " capabilities");
        
        Call<List<TimeSeriesPoint>> latestCall = apiService.getLatestTimeSeriesValues(
            accessToken, capabilityIds, true
        );
        
        latestCall.enqueue(new Callback<List<TimeSeriesPoint>>() {
            @Override
            public void onResponse(Call<List<TimeSeriesPoint>> call, Response<List<TimeSeriesPoint>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    List<TimeSeriesPoint> timeSeriesData = response.body();
                    
                    if (!timeSeriesData.isEmpty()) {
                        Log.d(TAG, "Retrieved " + timeSeriesData.size() + " time series points");
                        
                        // Log first few points for debugging
                        for (int i = 0; i < Math.min(3, timeSeriesData.size()); i++) {
                            TimeSeriesPoint point = timeSeriesData.get(i);
                            Log.d(TAG, "Time series point " + i + ": " + 
                                 point.getScalarValue() + " at " + point.getSourceTimestamp());
                        }
                        
                        // Process the real data
                        EnergyDataResponse energyData = processTimeSeriesData(
                            timeSeriesData, buildingName, buildingId
                        );
                        callback.onSuccess(energyData);
                    } else {
                        Log.w(TAG, "Time series response was empty");
                        EnergyDataResponse fallbackData = createFallbackData(buildingName, buildingId);
                        callback.onSuccess(fallbackData);
                    }
                } else {
                    String errorMsg = "Failed to get time series data: " + response.code();
                    if (response.errorBody() != null) {
                        try {
                            errorMsg += " - " + response.errorBody().string();
                        } catch (Exception e) {
                            errorMsg += " (could not read error body)";
                        }
                    }
                    Log.e(TAG, errorMsg);
                    EnergyDataResponse fallbackData = createFallbackData(buildingName, buildingId);
                    callback.onSuccess(fallbackData);
                }
            }
            
            @Override
            public void onFailure(Call<List<TimeSeriesPoint>> call, Throwable t) {
                Log.e(TAG, "Network error getting time series: " + t.getMessage(), t);
                EnergyDataResponse fallbackData = createFallbackData(buildingName, buildingId);
                callback.onSuccess(fallbackData);
            }
        });
    }
    
    /**
     * Process time series data into energy response
     */
    private EnergyDataResponse processTimeSeriesData(List<TimeSeriesPoint> timeSeriesData, 
                                                   String buildingName, String buildingId) {
        EnergyDataResponse energyData = new EnergyDataResponse(buildingName, buildingId);
        
        Log.d(TAG, "🔍 Processing " + timeSeriesData.size() + " time series points for " + buildingName);
        
        // Find the most recent valid energy reading
        TimeSeriesPoint latestPoint = null;
        double totalEnergy = 0.0;
        int validPointCount = 0;
        int totalPointCount = 0;
        
        for (TimeSeriesPoint point : timeSeriesData) {
            totalPointCount++;
            Log.v(TAG, "🔍 Point " + totalPointCount + ": ScalarValue=" + point.getScalarValue() + 
                  ", Quality=" + (point.getDataQuality() != null ? point.getDataQuality().toString() : "null") +
                  ", Timestamp=" + point.getSourceTimestamp());
                  
            // Be more lenient - accept points with scalar values even if quality is questionable
            if (point.getScalarValue() != null && point.getScalarValue() >= 0) {
                // Skip data quality check for now since Sechrist might have quality issues
                boolean hasGoodQuality = (point.getDataQuality() == null || point.getDataQuality().isGoodQuality());
                
                if (latestPoint == null || 
                    point.getSourceTimestamp().compareTo(latestPoint.getSourceTimestamp()) > 0) {
                    latestPoint = point;
                    Log.d(TAG, "🔍 New latest point for " + buildingName + ": " + point.getScalarValue() + 
                          " (Quality: " + hasGoodQuality + ")");
                }
                
                totalEnergy += point.getScalarValue();
                validPointCount++;
            } else {
                Log.v(TAG, "🔍 Skipped invalid point: ScalarValue=" + point.getScalarValue());
            }
        }
        
        Log.d(TAG, "🔍 " + buildingName + " processed " + validPointCount + "/" + totalPointCount + " valid points");
        
        if (latestPoint != null) {
            // Set real data
            energyData.setCurrentUsageKW(latestPoint.getValueAsKW());
            energyData.setDailyTotalKWh(totalEnergy);
            energyData.setLastUpdated(latestPoint.getSourceTimestamp());
            energyData.setDataAvailable(true);
            energyData.setStatus("Live Data");
            
            // 🎮 GAMIFICATION: Record today's energy usage for comparison
            if (pointsManager != null) {
                String dormName = mapBuildingNameToDorm(buildingName);
                pointsManager.recordTodayEnergyUsage(dormName, totalEnergy);
                Log.d(TAG, "🎮 Recorded energy usage for " + buildingName + " (mapped to " + dormName + "): " + totalEnergy + " kWh");
                
                // 🏆 AUTO-CALCULATE REAL RANKINGS: Calculate points based on energy efficiency
                updateDormRankingsBasedOnRealData();
            }
            
            // Calculate potential energy
            energyData.calculatePotentialEnergy();
            
            Log.d(TAG, "✅ Processed real energy data for " + buildingName + ": " + energyData.getCurrentUsageKW() + " kW, " + totalEnergy + " kWh total");
        } else {
            Log.w(TAG, "❌ No valid time series data found for " + buildingName + " (processed " + validPointCount + "/" + totalPointCount + " points) - using fallback");
            return createFallbackData(buildingName, buildingId);
        }
        
        return energyData;
    }
    
    /**
     * 🏆 AUTO-CALCULATE REAL RANKINGS based on actual energy efficiency from Willow API
     * This replaces fake test data with real energy-based scoring
     */
    private void updateDormRankingsBasedOnRealData() {
        if (pointsManager == null) return;
        
        String[] dorms = {"TINSLEY", "GABALDON", "SECHRIST"};
        double[] energyUsages = new double[3];
        
        // Get current energy usage for all dorms
        for (int i = 0; i < dorms.length; i++) {
            energyUsages[i] = pointsManager.getTodayEnergyUsage(dorms[i]);
        }
        
        // Calculate efficiency-based points (lower energy = higher points)
        // Scoring system: Most efficient gets 1000 points, least efficient gets 500 points
        
        // Find max usage for scaling
        double maxUsage = Math.max(Math.max(energyUsages[0], energyUsages[1]), energyUsages[2]);
        double minUsage = Math.min(Math.min(energyUsages[0], energyUsages[1]), energyUsages[2]);
        
        Log.d(TAG, "🏆 REAL DATA RANKINGS UPDATE:");
        Log.d(TAG, "  Energy usage - TINSLEY: " + energyUsages[0] + " kWh");
        Log.d(TAG, "  Energy usage - GABALDON: " + energyUsages[1] + " kWh");
        Log.d(TAG, "  Energy usage - SECHRIST: " + energyUsages[2] + " kWh");
        
        for (int i = 0; i < dorms.length; i++) {
            String dorm = dorms[i];
            double usage = energyUsages[i];
            
            if (usage <= 0) {
                // No data - set neutral score
                pointsManager.setDormTotalPoints(dorm, 750);
                Log.d(TAG, "  " + dorm + ": No data → 750 points (neutral)");
            } else {
                // Calculate efficiency score (lower usage = higher score)
                // Scale from 1000 (most efficient) to 500 (least efficient)
                int efficiencyPoints;
                if (maxUsage > minUsage) {
                    // Invert the scale: lower usage gets higher points
                    double efficiency = 1.0 - ((usage - minUsage) / (maxUsage - minUsage));
                    efficiencyPoints = (int)(500 + (efficiency * 500)); // 500-1000 range
                } else {
                    // All dorms have same usage
                    efficiencyPoints = 750;
                }
                
                pointsManager.setDormTotalPoints(dorm, efficiencyPoints);
                Log.d(TAG, "  " + dorm + ": " + usage + " kWh → " + efficiencyPoints + " points");
            }
        }
        
        // Log final rankings
        java.util.Map<String, Integer> rankings = pointsManager.getDormRankings();
        Log.d(TAG, "🏆 UPDATED REAL RANKINGS:");
        
        // Sort and display rankings
        java.util.List<java.util.Map.Entry<String, Integer>> sortedRankings = 
            new java.util.ArrayList<>(rankings.entrySet());
        java.util.Collections.sort(sortedRankings, 
            new java.util.Comparator<java.util.Map.Entry<String, Integer>>() {
                @Override
                public int compare(java.util.Map.Entry<String, Integer> a, 
                                 java.util.Map.Entry<String, Integer> b) {
                    return b.getValue().compareTo(a.getValue()); // Highest first
                }
            });
            
        for (int i = 0; i < sortedRankings.size(); i++) {
            java.util.Map.Entry<String, Integer> entry = sortedRankings.get(i);
            String position = (i == 0) ? "1ST" : (i == 1) ? "2ND" : "3RD";
            Log.d(TAG, "  " + (i+1) + ". " + entry.getKey() + ": " + entry.getValue() + " points (" + position + " PLACE)");
        }
    }
    
    /**
     * 🏆 Initialize real rankings when app starts - avoids starting with zero points
     */
    private void initializeRealRankings() {
        if (pointsManager == null) return;
        
        Log.d(TAG, "🏆 Initializing real rankings from existing energy data...");
        
        // Check if we already have points (avoid overwriting existing rankings)
        String[] dorms = {"TINSLEY", "GABALDON", "SECHRIST"};
        boolean hasExistingPoints = false;
        
        for (String dorm : dorms) {
            if (pointsManager.getDormTotalPoints(dorm) > 0) {
                hasExistingPoints = true;
                break;
            }
        }
        
        if (hasExistingPoints) {
            Log.d(TAG, "  Rankings already exist - skipping initialization");
            return;
        }
        
        // Initialize with real energy data or reasonable defaults
        updateDormRankingsBasedOnRealData();
        
        Log.d(TAG, "🏆 Real rankings initialized successfully");
    }
    
    /**
     * Create fallback data when real data is not available
     */
    private EnergyDataResponse createFallbackData(String buildingName, String buildingId) {
        EnergyDataResponse fallbackData = new EnergyDataResponse(buildingName, buildingId);
        
        // Return minimal real API response - no fake data
        fallbackData.setCurrentUsageKW(0.0);
        fallbackData.setDailyTotalKWh(0.0);
        fallbackData.calculatePotentialEnergy();
        fallbackData.setLastUpdated(new Date().toString());
        fallbackData.setDataAvailable(false);
        fallbackData.setStatus("No Data Available");
        
        return fallbackData;
    }
    
    /**
     * Get base usage for building (dynamic calculation)
     */
    private int getBaseUsageForBuilding(String buildingName) {
        // Dynamic calculation based on time of day and building characteristics
        int timeOfDayMultiplier = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY);
        int baseLoad = 200 + (timeOfDayMultiplier * 5); // Base load varies with time
        
        // Building efficiency factor (based on actual building characteristics)
        double efficiencyFactor = 1.0;
        switch (buildingName.toUpperCase(java.util.Locale.US)) {
            case "TINSLEY": efficiencyFactor = 0.85; break; // Newer building, more efficient
            case "GABALDON": efficiencyFactor = 1.0; break; // Average efficiency
            case "SECHRIST": efficiencyFactor = 1.15; break; // Older building, less efficient
            default: efficiencyFactor = 1.0; break;
        }
        
        return (int) (baseLoad * efficiencyFactor);
    }
    
    /**
     * Get building position for leaderboard (now dynamic based on points!)
     */
    public String getBuildingPosition(String buildingName) {
        if (pointsManager != null) {
            // Use dynamic leaderboard based on potential energy points
            String dormName = mapBuildingNameToDorm(buildingName);
            
            // Log all current rankings for debugging
            java.util.Map<String, Integer> allRankings = pointsManager.getDormRankings();
            Log.d(TAG, "🏆 CURRENT RANKINGS DEBUG:");
            Log.d(TAG, "🏆 TINSLEY: " + allRankings.get("TINSLEY") + " points");
            Log.d(TAG, "🏆 GABALDON: " + allRankings.get("GABALDON") + " points"); 
            Log.d(TAG, "🏆 SECHRIST: " + allRankings.get("SECHRIST") + " points");
            
            String dynamicPosition = pointsManager.getDormPosition(dormName);
            Log.d(TAG, "🏆 Dynamic position for " + buildingName + " (mapped to " + dormName + "): " + dynamicPosition);
            return dynamicPosition;
        } else {
            // Fallback to hardcoded positions if no points manager
            Log.w(TAG, "⚠️ No points manager, using hardcoded positions");
            return buildingPositions.get(buildingName.toUpperCase(java.util.Locale.US));
        }
    }
    
    /**
     * Get dorm's potential energy points (collective dorm score that determines leaderboard placement)
     */
    public int getDormPotentialEnergy(String buildingName) {
        if (pointsManager != null) {
            // Return dorm's total points (potential energy) which determines leaderboard placement
            String dormName = mapBuildingNameToDorm(buildingName);
            return pointsManager.getDormTotalPoints(dormName);
        }
        return 0;
    }
    
    /**
     * Get dorm score points (for leaderboard)
     */
    public int getDormScorePoints(String buildingName) {
        if (pointsManager != null) {
            String dormName = mapBuildingNameToDorm(buildingName);
            return pointsManager.getDormTotalPoints(dormName);
        }
        return 0;
    }
    
    /**
     * Get yesterday's energy usage for display
     */
    public double getYesterdayEnergyUsage(String buildingName) {
        if (pointsManager != null) {
            return pointsManager.getYesterdayEnergyUsage(buildingName);
        }
        return 0.0;
    }
    
    /**
     * Perform manual daily energy check (for testing)
     */
    public Map<String, Integer> performManualEnergyCheck() {
        if (pointsManager != null) {
            // TODO: Fix method call - temporarily disabled for authentication debugging
            // return pointsManager.performDailyEnergyCheck();
        }
        return new HashMap<>();
    }
    
    /**
     * Get gamification debug info
     */
    public String getGamificationDebugInfo() {
        if (pointsManager != null) {
            return pointsManager.getDebugInfo();
        }
        return "No gamification system available";
    }
    
    /**
     * Check if currently authenticated
     */
    public boolean isAuthenticated() {
        return accessToken != null && System.currentTimeMillis() < tokenExpirationTime;
    }
    
    /**
     * Test method to debug API issues
     */
    public void testApiEndpoints(String buildingTwinId, EnergyDataCallback callback) {
        if (!isAuthenticated()) {
            callback.onError("Not authenticated");
            return;
        }
        

        
        // First, let's search for available twins to see what we can access
        searchForAvailableTwins(buildingTwinId, callback);
    }
    
    /**
     * Search for available twins instead of using hardcoded IDs
     */
    private void searchForAvailableTwins(String targetTwinId, EnergyDataCallback callback) {
        Log.d(TAG, "🔍 Searching for available twins...");
        
        // Create a simple search request to find all accessible twins
        Map<String, Object> searchRequest = new HashMap<>();
        searchRequest.put("pageSize", 50); // Get more results
        
        Call<TwinsResponse> searchCall = apiService.searchTwins(accessToken, searchRequest);
        
        searchCall.enqueue(new Callback<TwinsResponse>() {
            @Override
            public void onResponse(Call<TwinsResponse> call, Response<TwinsResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    TwinsResponse twinsResponse = response.body();
                    
                    if (twinsResponse.hasTwins()) {
                        Log.d(TAG, "✅ Found " + twinsResponse.getContent().size() + " accessible twins");
                        
                        // Log all available twins for debugging
                        for (DigitalTwin twin : twinsResponse.getContent()) {
                            Log.d(TAG, "Available twin: " + twin.getName() + " (ID: " + twin.getId() + ", Model: " + twin.getModelId() + ")");
                        }
                        
                        // Try to find a matching twin by name or use any building twin
                        DigitalTwin buildingTwin = findBestMatchingTwin(twinsResponse.getContent(), targetTwinId);
                        
                        if (buildingTwin != null) {
                            Log.d(TAG, "✅ Using twin: " + buildingTwin.getName() + " (ID: " + buildingTwin.getId() + ")");
                            testDirectTimeSeries(buildingTwin, callback);
                        } else {
                            Log.w(TAG, "⚠️ No suitable building twin found, using fallback data");
                            String buildingName = buildingNames.containsKey(targetTwinId) ? 
                                buildingNames.get(targetTwinId) : "UNKNOWN";
                            EnergyDataResponse fallbackData = createFallbackData(buildingName, targetTwinId);
                            callback.onSuccess(fallbackData);
                        }
                    } else {
                        Log.w(TAG, "⚠️ No twins found in search response");
                        String buildingName = buildingNames.containsKey(targetTwinId) ? 
                            buildingNames.get(targetTwinId) : "UNKNOWN";
                        EnergyDataResponse fallbackData = createFallbackData(buildingName, targetTwinId);
                        callback.onSuccess(fallbackData);
                    }
                } else {
                    Log.e(TAG, "❌ Twin search failed: " + response.code());
                    if (response.errorBody() != null) {
                        try {
                            Log.e(TAG, "Error details: " + response.errorBody().string());
                        } catch (Exception e) {
                            Log.e(TAG, "Could not read error body");
                        }
                    }
                    
                    // Fallback to original approach
                    tryOriginalTwinAccess(targetTwinId, callback);
                }
            }
            
            @Override
            public void onFailure(Call<TwinsResponse> call, Throwable t) {
                Log.e(TAG, "❌ Twin search network error: " + t.getMessage(), t);
                // Fallback to original approach
                tryOriginalTwinAccess(targetTwinId, callback);
            }
        });
    }
    
    /**
     * Find the best matching twin from available twins
     */
    private DigitalTwin findBestMatchingTwin(List<DigitalTwin> availableTwins, String targetTwinId) {
        String targetBuildingName = buildingNames.containsKey(targetTwinId) ? 
            buildingNames.get(targetTwinId).toLowerCase(java.util.Locale.US) : "";
        
        Log.d(TAG, "🔍 Looking for twin ID: " + targetTwinId + " (Building: " + targetBuildingName.toUpperCase() + ")");
        
        // PRIORITY 1: Try to find exact twin ID match
        for (DigitalTwin twin : availableTwins) {
            if (twin.getId().equals(targetTwinId)) {
                Log.d(TAG, "✅ Found exact twin ID match: " + twin.getName() + " (ID: " + twin.getId() + ")");
                return twin;
            }
        }
        
        // PRIORITY 2: Try to find a twin with matching building name (specific dorm matching)
        for (DigitalTwin twin : availableTwins) {
            if (twin.getName() != null && !targetBuildingName.isEmpty()) {
                String twinName = twin.getName().toLowerCase();
                // More specific matching for dormitories
                if ((targetBuildingName.equals("sechrist") && twinName.contains("sechrist")) ||
                    (targetBuildingName.equals("gabaldon") && twinName.contains("gabaldon")) ||
                    (targetBuildingName.equals("tinsley") && twinName.contains("tinsley"))) {
                    Log.d(TAG, "✅ Found dorm name match: " + twin.getName() + " for " + targetBuildingName.toUpperCase());
                    return twin;
                }
            }
        }
        
        // PRIORITY 3: Try broader name matching
        for (DigitalTwin twin : availableTwins) {
            if (twin.getName() != null && 
                twin.getName().toLowerCase().contains(targetBuildingName) &&
                !targetBuildingName.isEmpty()) {
                Log.d(TAG, "⚠️ Found partial name match: " + twin.getName() + " for " + targetBuildingName.toUpperCase());
                return twin;
            }
        }
        
        // CRITICAL ERROR: Don't fall back to random buildings for wrong dorms!
        // Instead, log the error and return null to use fallback data
        Log.e(TAG, "❌ CRITICAL: No twin found for " + targetBuildingName.toUpperCase() + " (ID: " + targetTwinId + ")");
        Log.e(TAG, "❌ Available twins do not include the requested dorm. This will show NO DATA instead of wrong dorm data.");
        
        // Log available twins for debugging
        Log.d(TAG, "🔍 Available building twins:");
        for (DigitalTwin twin : availableTwins) {
            if (twin.getName() != null) {
                String name = twin.getName().toLowerCase();
                if (name.contains("hall") || name.contains("building") || 
                    name.contains("dorm") || name.contains("residence")) {
                    Log.d(TAG, "  - " + twin.getName() + " (ID: " + twin.getId() + ")");
                }
            }
        }
        
        return null; // Return null instead of wrong building data
    }
    
    /**
     * Fallback to original twin access method
     */
    private void tryOriginalTwinAccess(String buildingTwinId, EnergyDataCallback callback) {

        
        // Test 1: Try to get the building twin directly
        Call<DigitalTwin> twinCall = apiService.getTwinById(accessToken, buildingTwinId, true);
        
        twinCall.enqueue(new Callback<DigitalTwin>() {
            @Override
            public void onResponse(Call<DigitalTwin> call, Response<DigitalTwin> response) {
                if (response.isSuccessful() && response.body() != null) {
                    DigitalTwin building = response.body();
                    Log.d(TAG, "✅ Building twin found: " + building.getName() + " (Model: " + building.getModelId() + ")");
                    
                    // Test 2: Try direct time series call
                    testDirectTimeSeries(building, callback);
                } else {
                    Log.e(TAG, "❌ Failed to get building twin: " + response.code());
                    String buildingName = buildingNames.containsKey(buildingTwinId) ? 
                        buildingNames.get(buildingTwinId) : "UNKNOWN";
                    EnergyDataResponse fallbackData = createFallbackData(buildingName, buildingTwinId);
                    callback.onSuccess(fallbackData);
                }
            }
            
            @Override
            public void onFailure(Call<DigitalTwin> call, Throwable t) {
                Log.e(TAG, "❌ Network error getting building twin: " + t.getMessage(), t);
                String buildingName = buildingNames.containsKey(buildingTwinId) ? 
                    buildingNames.get(buildingTwinId) : "UNKNOWN";
                EnergyDataResponse fallbackData = createFallbackData(buildingName, buildingTwinId);
                callback.onSuccess(fallbackData);
            }
        });
    }
    
    /**
     * Test direct time series access
     */
    private void testDirectTimeSeries(DigitalTwin building, EnergyDataCallback callback) {

        
        Call<List<TimeSeriesPoint>> directCall = apiService.getLatestTimeSeries(
            accessToken, building.getId(), true
        );
        
        directCall.enqueue(new Callback<List<TimeSeriesPoint>>() {
            @Override
            public void onResponse(Call<List<TimeSeriesPoint>> call, Response<List<TimeSeriesPoint>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    List<TimeSeriesPoint> points = response.body();
                    Log.d(TAG, "✅ Direct time series success: " + points.size() + " points found");
                    
                    if (!points.isEmpty()) {
                        // Process and return real data
                        String buildingName = buildingNames.containsKey(building.getId()) ? 
                            buildingNames.get(building.getId()) : building.getName();
                        EnergyDataResponse energyData = processTimeSeriesData(points, buildingName, building.getId());
                        callback.onSuccess(energyData);
                    } else {
                        Log.w(TAG, "⚠️ No time series points found");
                        String buildingName = buildingNames.containsKey(building.getId()) ? 
                            buildingNames.get(building.getId()) : building.getName();
                        EnergyDataResponse fallbackData = createFallbackData(buildingName, building.getId());
                        callback.onSuccess(fallbackData);
                    }
                } else {
                    Log.e(TAG, "❌ Direct time series failed: " + response.code());
                    // Try the simplified search approach
                    String buildingName = buildingNames.containsKey(building.getId()) ? 
                        buildingNames.get(building.getId()) : building.getName();
                    findEnergyCapabilities(building, buildingName, callback);
                }
            }
            
            @Override
            public void onFailure(Call<List<TimeSeriesPoint>> call, Throwable t) {
                Log.e(TAG, "❌ Direct time series network error: " + t.getMessage(), t);
                // Try the simplified search approach
                String buildingName = buildingNames.containsKey(building.getId()) ? 
                    buildingNames.get(building.getId()) : building.getName();
                findEnergyCapabilities(building, buildingName, callback);
            }
        });
    }
    
    /**
     * Get all building twin IDs
     */
    public List<String> getAllBuildingIds() {
        return new ArrayList<>(buildingNames.keySet());
    }
    
    /**
     * Get building name by ID
     */
    public String getBuildingName(String twinId) {
        return buildingNames.get(twinId);
    }
    
    /**
     * Map API building names to internal dorm names for gamification
     */
    private String mapBuildingNameToDorm(String buildingName) {
        if (buildingName == null) return buildingName;
        
        String normalized = buildingName.toLowerCase(java.util.Locale.US).trim();
        
        if (normalized.contains("tinsley")) {
            return "TINSLEY";
        } else if (normalized.contains("gabaldon")) {
            return "GABALDON";
        } else if (normalized.contains("sechrist")) {
            return "SECHRIST";
        }
        
        // For debugging - return original name if no match
        Log.w(TAG, "No dorm mapping found for building: " + buildingName);
        return buildingName;
    }
}
