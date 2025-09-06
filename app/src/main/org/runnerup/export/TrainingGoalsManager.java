/*
 * Copyright (C) 2024
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 */

package org.runnerup.export;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import androidx.preference.PreferenceManager;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

/**
 * Manages training goals and race targets for LLM workout generation
 */
public class TrainingGoalsManager {
    
    private static final String TAG = "TrainingGoalsManager";
    private static final String PREF_KEY_GOALS = "llm_training_goals";
    
    private final Context context;
    private final SharedPreferences preferences;
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
    
    public TrainingGoalsManager(Context context) {
        this.context = context;
        this.preferences = PreferenceManager.getDefaultSharedPreferences(context);
    }
    
    /**
     * Set primary race goal with date and target
     */
    public void setPrimaryRaceGoal(RaceGoal goal) {
        try {
            JSONObject goalJson = goal.toJson();
            preferences.edit()
                .putString(PREF_KEY_GOALS, goalJson.toString())
                .apply();
            
            Log.d(TAG, "Primary race goal set: " + goal.raceName + " on " + dateFormat.format(goal.raceDate));
        } catch (JSONException e) {
            Log.e(TAG, "Error saving primary race goal", e);
        }
    }
    
    /**
     * Get current primary race goal
     */
    public RaceGoal getPrimaryRaceGoal() {
        String goalJson = preferences.getString(PREF_KEY_GOALS, null);
        if (goalJson != null) {
            try {
                JSONObject json = new JSONObject(goalJson);
                return RaceGoal.fromJson(json);
            } catch (JSONException e) {
                Log.e(TAG, "Error parsing race goal", e);
            }
        }
        return null;
    }
    
    /**
     * Clear current race goal
     */
    public void clearRaceGoal() {
        preferences.edit().remove(PREF_KEY_GOALS).apply();
        Log.d(TAG, "Race goal cleared");
    }
    
    /**
     * Get training context string for LLM prompt
     */
    public String getTrainingContext() {
        RaceGoal goal = getPrimaryRaceGoal();
        if (goal == null) {
            return "TRAINING GOALS: No specific race goal set - focus on general fitness improvement\n";
        }
        
        StringBuilder context = new StringBuilder();
        context.append("TRAINING GOALS:\n");
        context.append("- Race: ").append(goal.raceName).append("\n");
        context.append("- Date: ").append(dateFormat.format(goal.raceDate)).append("\n");
        context.append("- Distance: ").append(String.format("%.1f km", goal.raceDistance / 1000.0)).append("\n");
        
        if (goal.targetTime > 0) {
            context.append("- Target Time: ").append(formatTime(goal.targetTime)).append("\n");
            double targetPace = (goal.targetTime / 1000.0) / (goal.raceDistance / 1000.0);
            context.append("- Target Pace: ").append(formatPace(targetPace)).append(" min/km\n");
        }
        
        // Calculate days until race
        long daysUntilRace = (goal.raceDate.getTime() - System.currentTimeMillis()) / TimeUnit.DAYS.toMillis(1);
        context.append("- Days until race: ").append(daysUntilRace).append("\n");
        
        // Add training phase guidance
        if (daysUntilRace > 84) { // More than 12 weeks
            context.append("- Training Phase: Base building phase - focus on aerobic development\n");
        } else if (daysUntilRace > 28) { // 4-12 weeks
            context.append("- Training Phase: Build phase - increase intensity and race-specific work\n");
        } else if (daysUntilRace > 7) { // 1-4 weeks
            context.append("- Training Phase: Peak/Sharpening phase - race pace work and tapering\n");
        } else if (daysUntilRace >= 0) { // Race week
            context.append("- Training Phase: Taper week - easy runs and race preparation\n");
        } else {
            context.append("- Training Phase: Post-race recovery\n");
        }
        
        if (goal.experienceLevel != null) {
            context.append("- Experience Level: ").append(goal.experienceLevel).append("\n");
        }
        
        if (goal.weeklyVolumeGoal > 0) {
            context.append("- Target Weekly Volume: ").append(String.format("%.1f km", goal.weeklyVolumeGoal / 1000.0)).append("\n");
        }
        
        if (goal.priorityAreas != null && !goal.priorityAreas.isEmpty()) {
            context.append("- Priority Training Areas: ").append(String.join(", ", goal.priorityAreas)).append("\n");
        }
        
        context.append("\n");
        return context.toString();
    }
    
    /**
     * Check if current goal is still relevant (not past race date + buffer)
     */
    public boolean isGoalCurrent() {
        RaceGoal goal = getPrimaryRaceGoal();
        if (goal == null) return false;
        
        // Consider goal current until 2 weeks after race date
        long bufferTime = TimeUnit.DAYS.toMillis(14);
        return (goal.raceDate.getTime() + bufferTime) > System.currentTimeMillis();
    }
    
    /**
     * Get suggested workout types based on current goal and timeline
     */
    public List<String> getSuggestedWorkoutTypes() {
        List<String> suggestions = new ArrayList<>();
        RaceGoal goal = getPrimaryRaceGoal();
        
        if (goal == null) {
            // Default suggestions for general fitness
            suggestions.add("ai_easy_run");
            suggestions.add("ai_interval_training");
            suggestions.add("ai_tempo_run");
            suggestions.add("ai_long_run");
            return suggestions;
        }
        
        long daysUntilRace = (goal.raceDate.getTime() - System.currentTimeMillis()) / TimeUnit.DAYS.toMillis(1);
        
        if (goal.raceDistance <= 5000) { // 5K and shorter
            suggestions.add("ai_5k_intervals");
            suggestions.add("ai_speed_work");
            suggestions.add("ai_tempo_run");
        } else if (goal.raceDistance <= 10000) { // 10K
            suggestions.add("ai_10k_intervals");
            suggestions.add("ai_tempo_run");
            suggestions.add("ai_threshold_run");
        } else if (goal.raceDistance <= 21097) { // Half marathon
            suggestions.add("ai_half_marathon_pace");
            suggestions.add("ai_long_run");
            suggestions.add("ai_tempo_run");
        } else { // Marathon and longer
            suggestions.add("ai_marathon_pace");
            suggestions.add("ai_long_run");
            suggestions.add("ai_progression_run");
        }
        
        // Phase-specific additions
        if (daysUntilRace <= 7) { // Race week
            suggestions.clear();
            suggestions.add("ai_race_prep");
            suggestions.add("ai_easy_shakeout");
        } else if (daysUntilRace <= 28) { // Peak phase
            suggestions.add("ai_race_pace_practice");
        }
        
        // Always include recovery options
        suggestions.add("ai_easy_run");
        suggestions.add("ai_recovery_run");
        
        return suggestions;
    }
    
    private String formatTime(long timeMs) {
        long totalSeconds = timeMs / 1000;
        long hours = totalSeconds / 3600;
        long minutes = (totalSeconds % 3600) / 60;
        long seconds = totalSeconds % 60;
        
        if (hours > 0) {
            return String.format("%d:%02d:%02d", hours, minutes, seconds);
        } else {
            return String.format("%d:%02d", minutes, seconds);
        }
    }
    
    private String formatPace(double paceSecondsPerKm) {
        int minutes = (int) (paceSecondsPerKm / 60);
        int seconds = (int) (paceSecondsPerKm % 60);
        return String.format("%d:%02d", minutes, seconds);
    }
    
    /**
     * Data class representing a race goal
     */
    public static class RaceGoal {
        public String raceName;
        public Date raceDate;
        public double raceDistance; // meters
        public long targetTime; // milliseconds, 0 if no specific target
        public String experienceLevel; // "Beginner", "Intermediate", "Advanced"
        public double weeklyVolumeGoal; // meters per week
        public List<String> priorityAreas; // e.g., "Speed", "Endurance", "Recovery"
        public String additionalNotes;
        
        public RaceGoal() {
            priorityAreas = new ArrayList<>();
        }
        
        public JSONObject toJson() throws JSONException {
            JSONObject json = new JSONObject();
            json.put("raceName", raceName);
            json.put("raceDate", raceDate.getTime());
            json.put("raceDistance", raceDistance);
            json.put("targetTime", targetTime);
            json.put("experienceLevel", experienceLevel);
            json.put("weeklyVolumeGoal", weeklyVolumeGoal);
            
            if (priorityAreas != null) {
                JSONArray areasArray = new JSONArray();
                for (String area : priorityAreas) {
                    areasArray.put(area);
                }
                json.put("priorityAreas", areasArray);
            }
            
            json.put("additionalNotes", additionalNotes);
            return json;
        }
        
        public static RaceGoal fromJson(JSONObject json) throws JSONException {
            RaceGoal goal = new RaceGoal();
            goal.raceName = json.optString("raceName");
            goal.raceDate = new Date(json.getLong("raceDate"));
            goal.raceDistance = json.optDouble("raceDistance");
            goal.targetTime = json.optLong("targetTime");
            goal.experienceLevel = json.optString("experienceLevel");
            goal.weeklyVolumeGoal = json.optDouble("weeklyVolumeGoal");
            goal.additionalNotes = json.optString("additionalNotes");
            
            JSONArray areasArray = json.optJSONArray("priorityAreas");
            if (areasArray != null) {
                for (int i = 0; i < areasArray.length(); i++) {
                    goal.priorityAreas.add(areasArray.getString(i));
                }
            }
            
            return goal;
        }
        
        /**
         * Create common race distance goals
         */
        public static RaceGoal create5K(String raceName, Date raceDate) {
            RaceGoal goal = new RaceGoal();
            goal.raceName = raceName;
            goal.raceDate = raceDate;
            goal.raceDistance = 5000; // 5K in meters
            return goal;
        }
        
        public static RaceGoal create10K(String raceName, Date raceDate) {
            RaceGoal goal = new RaceGoal();
            goal.raceName = raceName;
            goal.raceDate = raceDate;
            goal.raceDistance = 10000; // 10K in meters
            return goal;
        }
        
        public static RaceGoal createHalfMarathon(String raceName, Date raceDate) {
            RaceGoal goal = new RaceGoal();
            goal.raceName = raceName;
            goal.raceDate = raceDate;
            goal.raceDistance = 21097; // Half marathon in meters
            return goal;
        }
        
        public static RaceGoal createMarathon(String raceName, Date raceDate) {
            RaceGoal goal = new RaceGoal();
            goal.raceName = raceName;
            goal.raceDate = raceDate;
            goal.raceDistance = 42195; // Marathon in meters
            return goal;
        }
    }
}