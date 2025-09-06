/*
 * Copyright (C) 2024
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.runnerup.export;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.util.Log;
import android.util.Pair;
import androidx.annotation.NonNull;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.runnerup.R;
import org.runnerup.common.util.Constants;
import org.runnerup.db.DBHelper;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

public class LLMSynchronizer extends DefaultSynchronizer {
    
    public static final String NAME = "LLM";
    private static final String TAG = "LLMSynchronizer";
    
    // External API endpoints
    private static final String OPENAI_API_URL = "https://api.openai.com/v1/chat/completions";
    private static final String ANTHROPIC_API_URL = "https://api.anthropic.com/v1/messages";
    
    // Configuration keys
    private static final String CONFIG_API_KEY = "api_key";
    private static final String CONFIG_API_PROVIDER = "api_provider";
    private static final String CONFIG_USE_LOCAL = "use_local";
    
    // API configuration
    private String apiKey;
    private String apiProvider = "openai"; // default to OpenAI
    private boolean useLocalLLM = false;
    
    // Local LLM support
    private LocalLLMHelper localLLMHelper;
    private android.content.Context context;
    
    // Training goals management
    private TrainingGoalsManager goalsManager;
    
    @Override
    public long getId() {
        return LLMConstants.SYNCHRONIZER_ID;
    }
    
    @NonNull
    @Override
    public String getName() {
        return NAME;
    }
    
    @Override
    public int getIconId() {
        return R.drawable.service_strava; // Placeholder - will need LLM icon
    }
    
    public LLMSynchronizer() {
        super();
    }
    
    public LLMSynchronizer(android.content.Context context) {
        super();
        this.context = context;
    }
    
    @Override
    public void init(ContentValues config) {
        super.init(config);
        if (config != null) {
            apiKey = config.getAsString(CONFIG_API_KEY);
            
            String provider = config.getAsString(CONFIG_API_PROVIDER);
            if (provider != null) {
                apiProvider = provider;
            }
            
            Boolean useLocal = config.getAsBoolean(CONFIG_USE_LOCAL);
            if (useLocal != null) {
                useLocalLLM = useLocal;
            }
        }
        
        // Initialize local LLM helper if we have context
        if (context != null && localLLMHelper == null) {
            localLLMHelper = new LocalLLMHelper(context);
        }
        
        // Initialize training goals manager
        if (context != null && goalsManager == null) {
            goalsManager = new TrainingGoalsManager(context);
        }
    }
    
    @NonNull
    @Override
    public String getAuthConfig() {
        JSONObject config = new JSONObject();
        try {
            config.put(CONFIG_API_KEY, "");
            config.put(CONFIG_API_PROVIDER, "openai");
            config.put(CONFIG_USE_LOCAL, false);
        } catch (JSONException e) {
            Log.e(TAG, "Error creating auth config", e);
        }
        return config.toString();
    }
    
    @Override
    public boolean isConfigured() {
        return (apiKey != null && !apiKey.isEmpty()) || useLocalLLM;
    }
    
    @Override
    public boolean checkSupport(Feature f) {
        switch (f) {
            case WORKOUT_LIST:
            case GET_WORKOUT:
                return true;
            default:
                return false;
        }
    }
    
    @NonNull
    @Override
    public Status listWorkouts(List<Pair<String, String>> list) {
        // Generate dynamic workout suggestions based on user's recent activities and goals
        try {
            List<LLMPromptBuilder.TrainingData> recentActivities = getRecentActivities();
            
            // First, add goal-based suggestions if we have training goals
            if (goalsManager != null && goalsManager.isGoalCurrent()) {
                List<String> goalSuggestions = goalsManager.getSuggestedWorkoutTypes();
                for (String workoutType : goalSuggestions) {
                    String displayName = workoutType;
                    list.add(new Pair<>(workoutType, displayName));
                }
            }
            
            if (recentActivities.isEmpty() && (goalsManager == null || !goalsManager.isGoalCurrent())) {
                // Add some default workout options
                list.add(new Pair<>("ai_5k_training", "AI 5K Training Plan"));
                list.add(new Pair<>("ai_interval_workout", "AI Interval Workout"));
                list.add(new Pair<>("ai_easy_run", "AI Easy Recovery Run"));
            } else if (!recentActivities.isEmpty()) {
                // Generate additional suggestions based on training history
                String workoutSuggestions = generateWorkoutSuggestions(recentActivities);
                parseWorkoutSuggestions(workoutSuggestions, list);
            }
            return Status.OK;
        } catch (Exception e) {
            Log.e(TAG, "Error listing workouts", e);
            Status status = Status.ERROR;
            status.ex = e;
            return status;
        }
    }
    
    @Override
    public void downloadWorkout(java.io.File dst, String key) throws Exception {
        Log.d(TAG, "Downloading workout: " + key);
        
        // Generate workout based on key and user data
        List<LLMPromptBuilder.TrainingData> userData = getRecentActivities();
        String workoutJson = generateWorkoutJson(key, userData);
        
        // Write to destination file
        java.io.FileWriter writer = new java.io.FileWriter(dst);
        writer.write(workoutJson);
        writer.close();
        
        Log.d(TAG, "Workout downloaded successfully to: " + dst.getAbsolutePath());
    }
    
    /**
     * Generate workout JSON using LLM based on user's training data
     */
    public String generateWorkoutJson(String workoutType, List<LLMPromptBuilder.TrainingData> userData) throws Exception {
        TrainingHistoryAnalyzer.TrainingAnalysis analysis = getTrainingAnalysis();
        String prompt = LLMPromptBuilder.buildAdvancedWorkoutPrompt(workoutType, analysis);
        String llmResponse;
        
        if (useLocalLLM || !hasNetworkConnection()) {
            llmResponse = generateWithLocalLLM(prompt);
            return WorkoutJsonProcessor.parseWorkoutFromLLMResponse(llmResponse); // No retry for local LLM
        } else {
            // Use specific context for workout JSON generation
            LLMRequestHandler.RequestContext context = LLMRequestHandler.RequestContext.forWorkoutGeneration(workoutType);
            llmResponse = generateWithExternalAPI(prompt, context);
            
            // Use smart retry logic for external API
            return WorkoutJsonProcessor.parseWorkoutFromLLMResponse(llmResponse, workoutType, 0);
        }
    }
    
    /**
     * Generate workout suggestions using LLM
     */
    private String generateWorkoutSuggestions(List<LLMPromptBuilder.TrainingData> recentActivities) throws Exception {
        TrainingHistoryAnalyzer.TrainingAnalysis analysis = getTrainingAnalysis();
        String prompt = LLMPromptBuilder.buildAdvancedSuggestionsPrompt(analysis);
        
        if (useLocalLLM || !hasNetworkConnection()) {
            return generateWithLocalLLM(prompt);
        } else {
            // Use specific context for workout suggestions
            LLMRequestHandler.RequestContext context = LLMRequestHandler.RequestContext.forWorkoutSuggestions();
            return generateWithExternalAPI(prompt, context);
        }
    }
    
    
    /**
     * Generate using external API with robust error handling
     */
    private String generateWithExternalAPI(String prompt) throws Exception {
        return generateWithExternalAPI(prompt, LLMRequestHandler.RequestContext.forWorkoutGeneration("general"));
    }
    
    /**
     * Generate using external API with specific context
     */
    private String generateWithExternalAPI(String prompt, LLMRequestHandler.RequestContext context) throws Exception {
        if (apiKey == null || apiKey.isEmpty()) {
            throw new Exception("API key not configured");
        }
        
        try {
            LLMRequestHandler requestHandler = new LLMRequestHandler(apiProvider, apiKey);
            LLMRequestHandler.LLMResponse response = requestHandler.makeRequest(prompt, context);
            
            Log.d(TAG, "LLM request successful via " + response.apiProvider);
            return response.content;
            
        } catch (LLMRequestHandler.LLMException e) {
            Log.e(TAG, "LLM request failed: " + e.getMessage(), e);
            
            // Convert to generic exception for now (could be enhanced)
            if (e.getErrorType() == LLMRequestHandler.LLMException.ErrorType.AUTHENTICATION_ERROR) {
                throw new Exception("Invalid API key - please check your configuration");
            } else if (e.getErrorType() == LLMRequestHandler.LLMException.ErrorType.RATE_LIMITED) {
                throw new Exception("API rate limit exceeded - please try again later");
            } else if (e.getErrorType() == LLMRequestHandler.LLMException.ErrorType.INVALID_WORKOUT_FORMAT) {
                throw new Exception("Generated workout format was invalid - please try again");
            } else {
                throw new Exception("LLM request failed: " + e.getMessage());
            }
        }
    }
    
    /**
     * Generate using local LLM with MediaPipe
     */
    private String generateWithLocalLLM(String prompt) throws Exception {
        if (localLLMHelper == null) {
            Log.w(TAG, "Local LLM helper not initialized");
            return generateFallbackWorkout(prompt);
        }
        
        if (!localLLMHelper.isAvailable()) {
            Log.w(TAG, "Local LLM not available, using fallback");
            return generateFallbackWorkout(prompt);
        }
        
        try {
            String response = localLLMHelper.generateResponse(prompt);
            if (response != null && !response.trim().isEmpty()) {
                return response;
            } else {
                Log.w(TAG, "Empty response from local LLM, using fallback");
                return generateFallbackWorkout(prompt);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error with local LLM generation", e);
            return generateFallbackWorkout(prompt);
        }
    }
    
    /**
     * Generate a simple fallback workout when LLM is unavailable
     */
    private String generateFallbackWorkout(String prompt) {
        // Create a basic 30-minute easy run workout
        try {
            JSONObject workout = new JSONObject();
            JSONObject userWorkout = new JSONObject();
            
            userWorkout.put("workoutName", "AI Easy Run (Fallback)");
            userWorkout.put("sportTypeKey", "running");
            
            JSONArray steps = new JSONArray();
            
            // Warmup
            JSONObject warmup = new JSONObject();
            warmup.put("stepTypeKey", "warmup");
            warmup.put("endConditionTypeKey", "time");
            warmup.put("endConditionValue", LLMConstants.WARMUP_TIME_MS);
            warmup.put("endConditionUnitKey", "ms");
            warmup.put("targetTypeKey", "no.target");
            warmup.put("stepOrder", 1);
            steps.put(warmup);
            
            // Main run
            JSONObject mainRun = new JSONObject();
            mainRun.put("stepTypeKey", "interval");
            mainRun.put("endConditionTypeKey", "time");
            mainRun.put("endConditionValue", LLMConstants.MAIN_WORKOUT_TIME_MS);
            mainRun.put("endConditionUnitKey", "ms");
            mainRun.put("targetTypeKey", "no.target");
            mainRun.put("stepOrder", 2);
            steps.put(mainRun);
            
            // Cooldown
            JSONObject cooldown = new JSONObject();
            cooldown.put("stepTypeKey", "cooldown");
            cooldown.put("endConditionTypeKey", "time");
            cooldown.put("endConditionValue", LLMConstants.COOLDOWN_TIME_MS);
            cooldown.put("endConditionUnitKey", "ms");
            cooldown.put("targetTypeKey", "no.target");
            cooldown.put("stepOrder", 3);
            steps.put(cooldown);
            
            userWorkout.put("workoutSteps", steps);
            workout.put("com.garmin.connect.workout.json.UserWorkoutJson", userWorkout);
            
            return workout.toString();
        } catch (JSONException e) {
            Log.e(TAG, "Error creating fallback workout", e);
            return "{}";
        }
    }
    
    private String getApiUrl() {
        switch (apiProvider.toLowerCase()) {
            case "anthropic":
                return ANTHROPIC_API_URL;
            case "openai":
            default:
                return OPENAI_API_URL;
        }
    }
    
    private String getAuthHeader() {
        switch (apiProvider.toLowerCase()) {
            case "anthropic":
                return "x-api-key " + apiKey;
            case "openai":
            default:
                return "Bearer " + apiKey;
        }
    }
    
    private JSONObject buildApiRequest(String prompt) throws JSONException {
        JSONObject request = new JSONObject();
        
        switch (apiProvider.toLowerCase()) {
            case "openai":
                request.put("model", "gpt-3.5-turbo");
                JSONArray messages = new JSONArray();
                JSONObject message = new JSONObject();
                message.put("role", "user");
                message.put("content", prompt);
                messages.put(message);
                request.put("messages", messages);
                request.put("max_tokens", LLMConstants.DEFAULT_MAX_TOKENS);
                request.put("temperature", 0.7);
                break;
                
            case "anthropic":
                request.put("model", LLMConstants.DEFAULT_ANTHROPIC_MODEL);
                request.put("max_tokens", LLMConstants.DEFAULT_MAX_TOKENS);
                request.put("messages", new JSONArray().put(
                    new JSONObject().put("role", "user").put("content", prompt)
                ));
                break;
        }
        
        return request;
    }
    
    private String parseApiResponse(String response) throws JSONException {
        JSONObject jsonResponse = new JSONObject(response);
        
        switch (apiProvider.toLowerCase()) {
            case "openai":
                JSONArray choices = jsonResponse.getJSONArray("choices");
                if (choices.length() > 0) {
                    return choices.getJSONObject(0)
                        .getJSONObject("message")
                        .getString("content");
                }
                break;
                
            case "anthropic":
                JSONArray content = jsonResponse.getJSONArray("content");
                if (content.length() > 0) {
                    return content.getJSONObject(0).getString("text");
                }
                break;
        }
        
        throw new JSONException("Unable to parse API response");
    }
    
    
    
    private void parseWorkoutSuggestions(String suggestions, List<Pair<String, String>> list) {
        String[] lines = suggestions.split("\n");
        for (String line : lines) {
            if (line.contains("|")) {
                String[] parts = line.split("\\|", 2);
                if (parts.length == 2) {
                    list.add(new Pair<>(parts[0].trim(), parts[1].trim()));
                }
            }
        }
        
        // Ensure we have at least some suggestions
        if (list.isEmpty()) {
            list.add(new Pair<>("ai_easy_run", "AI Easy Run"));
            list.add(new Pair<>("ai_interval_training", "AI Interval Training"));
            list.add(new Pair<>("ai_tempo_run", "AI Tempo Run"));
        }
    }
    
    private boolean hasNetworkConnection() {
        if (context == null) {
            return true; // Assume network available if no context
        }
        
        try {
            android.net.ConnectivityManager connectivityManager = 
                (android.net.ConnectivityManager) context.getSystemService(android.content.Context.CONNECTIVITY_SERVICE);
            android.net.NetworkInfo activeNetworkInfo = connectivityManager.getActiveNetworkInfo();
            return activeNetworkInfo != null && activeNetworkInfo.isConnected();
        } catch (Exception e) {
            Log.w(TAG, "Could not check network connectivity", e);
            return true; // Assume network available on error
        }
    }
    
    /**
     * Get comprehensive training analysis for context
     */
    private TrainingHistoryAnalyzer.TrainingAnalysis getTrainingAnalysis() {
        if (context == null) {
            Log.w(TAG, "No context available for training analysis");
            return new TrainingHistoryAnalyzer.TrainingAnalysis();
        }
        
        TrainingHistoryAnalyzer analyzer = new TrainingHistoryAnalyzer(context);
        return analyzer.getTrainingAnalysis(4); // Analyze last 4 weeks
    }
    
    /**
     * Get recent training activities for context (legacy method for compatibility)
     */
    private List<LLMPromptBuilder.TrainingData> getRecentActivities() {
        TrainingHistoryAnalyzer.TrainingAnalysis analysis = getTrainingAnalysis();
        List<LLMPromptBuilder.TrainingData> activities = new ArrayList<>();
        
        // Convert to prompt builder format
        for (TrainingHistoryAnalyzer.ActivitySummary activity : analysis.recentActivities) {
            java.text.SimpleDateFormat dateFormat = new java.text.SimpleDateFormat("MMM dd", java.util.Locale.getDefault());
            String dateStr = dateFormat.format(new java.util.Date(activity.startTime));
            
            LLMPromptBuilder.TrainingData data = new LLMPromptBuilder.TrainingData(
                activity.sport,
                activity.distance / LLMConstants.METERS_TO_KM, // Convert to km
                activity.avgPace, // Already in seconds per km
                dateStr
            );
            activities.add(data);
        }
        
        return activities;
    }
    
}