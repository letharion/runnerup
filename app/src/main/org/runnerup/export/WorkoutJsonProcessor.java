/*
 * Copyright (C) 2024
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 */

package org.runnerup.export;

import android.util.Log;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * Processes and validates workout JSON from LLM responses
 */
public class WorkoutJsonProcessor {
    
    private static final String TAG = "WorkoutJsonProcessor";
    
    // Valid step types for workout validation
    private static final Set<String> VALID_STEP_TYPES = new HashSet<>(Arrays.asList(
        "warmup", "interval", "recovery", "rest", "cooldown", "repeat", "active"
    ));
    
    /**
     * Parse workout JSON from LLM response with retries
     */
    public static String parseWorkoutFromLLMResponse(String llmResponse) throws Exception {
        return parseWorkoutFromLLMResponse(llmResponse, null, 0);
    }
    
    /**
     * Parse workout JSON from LLM response with retry logic
     */
    public static String parseWorkoutFromLLMResponse(String llmResponse, String originalWorkoutType, int retryCount) throws Exception {
        String jsonString = extractJsonFromLLMResponse(llmResponse);
        
        if (jsonString == null || jsonString.trim().isEmpty()) {
            throw new WorkoutValidationException("No JSON found in LLM response");
        }
        
        try {
            // Attempt to validate and fix the JSON
            String validatedJson = validateAndFixWorkoutJson(jsonString);
            Log.d(TAG, "Successfully validated workout JSON");
            return validatedJson;
            
        } catch (WorkoutValidationException e) {
            Log.w(TAG, "JSON validation failed: " + e.getMessage());
            
            // If we have retry attempts remaining and original workout type, try regenerating
            if (retryCount < 2 && originalWorkoutType != null) {
                Log.d(TAG, "Attempting to regenerate workout with feedback");
                throw new RetryableValidationException(e.getMessage(), jsonString);
            } else {
                throw e;
            }
        }
    }
    
    /**
     * Extract JSON content from LLM response (handles markdown, code blocks, etc.)
     */
    public static String extractJsonFromLLMResponse(String response) {
        if (response == null || response.trim().isEmpty()) {
            return null;
        }
        
        String trimmed = response.trim();
        
        // Try to find JSON within markdown code blocks first
        String[] codeBlockMarkers = {"```json", "```"};
        for (String marker : codeBlockMarkers) {
            int startIndex = trimmed.indexOf(marker);
            if (startIndex >= 0) {
                startIndex += marker.length();
                int endIndex = trimmed.indexOf("```", startIndex);
                if (endIndex > startIndex) {
                    String extracted = trimmed.substring(startIndex, endIndex).trim();
                    if (isLikelyJson(extracted)) {
                        return extracted;
                    }
                }
            }
        }
        
        // Try to find JSON by looking for opening and closing braces
        int jsonStart = trimmed.indexOf('{');
        int jsonEnd = trimmed.lastIndexOf('}');
        
        if (jsonStart >= 0 && jsonEnd > jsonStart) {
            String extracted = trimmed.substring(jsonStart, jsonEnd + 1);
            if (isLikelyJson(extracted)) {
                return extracted;
            }
        }
        
        // If the entire response looks like JSON, return it
        if (isLikelyJson(trimmed)) {
            return trimmed;
        }
        
        return null;
    }
    
    /**
     * Check if a string is likely to be JSON
     */
    private static boolean isLikelyJson(String str) {
        if (str == null || str.trim().isEmpty()) {
            return false;
        }
        String trimmed = str.trim();
        return (trimmed.startsWith("{") && trimmed.endsWith("}")) ||
               (trimmed.startsWith("[") && trimmed.endsWith("]"));
    }
    
    /**
     * Validate and fix workout JSON structure
     */
    public static String validateAndFixWorkoutJson(String jsonString) throws WorkoutValidationException {
        try {
            JSONObject workout = new JSONObject(jsonString);
            
            // Check for main structure
            if (!workout.has("com.garmin.connect.workout.json.UserWorkoutJson")) {
                throw new WorkoutValidationException("Missing required UserWorkoutJson structure");
            }
            
            JSONObject userWorkout = workout.getJSONObject("com.garmin.connect.workout.json.UserWorkoutJson");
            
            // Validate required fields
            validateRequiredWorkoutFields(userWorkout);
            
            // Validate and fix workout steps
            if (userWorkout.has("workoutSteps")) {
                JSONArray steps = userWorkout.getJSONArray("workoutSteps");
                validateWorkoutSteps(steps);
            }
            
            // Apply automatic fixes
            JSONObject fixedWorkout = applyWorkoutFixes(workout);
            
            return fixedWorkout.toString(2); // Pretty print with 2-space indentation
            
        } catch (JSONException e) {
            throw new WorkoutValidationException("Invalid JSON structure: " + e.getMessage());
        }
    }
    
    /**
     * Validate required workout fields
     */
    private static void validateRequiredWorkoutFields(JSONObject userWorkout) throws WorkoutValidationException, JSONException {
        // Check for workout steps
        if (!userWorkout.has("workoutSteps")) {
            throw new WorkoutValidationException("Missing required 'workoutSteps' array");
        }
        
        JSONArray steps = userWorkout.getJSONArray("workoutSteps");
        if (steps.length() == 0) {
            throw new WorkoutValidationException("Workout must have at least one step");
        }
        
        // Validate workout name if present
        if (userWorkout.has("workoutName")) {
            String workoutName = userWorkout.getString("workoutName");
            if (workoutName.length() > 100) {
                throw new WorkoutValidationException("Workout name too long (max 100 characters)");
            }
        }
    }
    
    /**
     * Validate workout steps array
     */
    private static void validateWorkoutSteps(JSONArray steps) throws WorkoutValidationException, JSONException {
        if (steps.length() > 50) {
            throw new WorkoutValidationException("Too many workout steps (max 50)");
        }
        
        for (int i = 0; i < steps.length(); i++) {
            JSONObject step = steps.getJSONObject(i);
            validateWorkoutStep(step, i);
        }
    }
    
    /**
     * Validate individual workout step
     */
    private static void validateWorkoutStep(JSONObject step, int stepIndex) throws WorkoutValidationException, JSONException {
        // Validate step type
        if (!step.has("stepTypeKey")) {
            throw new WorkoutValidationException("Step " + stepIndex + " missing stepTypeKey");
        }
        
        String stepType = step.getString("stepTypeKey");
        if (!isValidStepType(stepType)) {
            throw new WorkoutValidationException("Step " + stepIndex + " has invalid stepTypeKey: " + stepType);
        }
        
        // Validate end condition
        validateEndCondition(step, stepIndex);
        
        // Validate target if present
        if (step.has("targetTypeKey")) {
            String targetType = step.getString("targetTypeKey");
            if (!isValidTargetType(targetType)) {
                Log.w(TAG, "Step " + stepIndex + " has questionable targetTypeKey: " + targetType);
            }
        }
    }
    
    /**
     * Validate end condition for workout step
     */
    private static void validateEndCondition(JSONObject step, int stepIndex) throws WorkoutValidationException, JSONException {
        if (!step.has("endConditionTypeKey")) {
            throw new WorkoutValidationException("Step " + stepIndex + " missing endConditionTypeKey");
        }
        
        String endConditionType = step.getString("endConditionTypeKey");
        Set<String> validEndConditions = new HashSet<>(Arrays.asList(
            "time", "distance", "iterations", "lap.button"
        ));
        
        if (!validEndConditions.contains(endConditionType)) {
            throw new WorkoutValidationException("Step " + stepIndex + " has invalid endConditionTypeKey: " + endConditionType);
        }
        
        // Validate end condition value
        if (step.has("endConditionValue")) {
            double value = step.getDouble("endConditionValue");
            if (!isReasonableEndConditionValue(value, endConditionType)) {
                throw new WorkoutValidationException("Step " + stepIndex + " has unreasonable endConditionValue: " + value);
            }
        }
    }
    
    /**
     * Check if step type is valid
     */
    private static boolean isValidStepType(String stepType) {
        return VALID_STEP_TYPES.contains(stepType);
    }
    
    /**
     * Check if target type is valid
     */
    private static boolean isValidTargetType(String targetType) {
        Set<String> validTargetTypes = new HashSet<>(Arrays.asList(
            "no.target", "pace", "speed", "heart.rate", "power", "cadence"
        ));
        return validTargetTypes.contains(targetType);
    }
    
    /**
     * Check if end condition value is reasonable
     */
    private static boolean isReasonableEndConditionValue(double value, String endConditionType) {
        switch (endConditionType) {
            case "time":
                // Time in milliseconds: 10 seconds to 4 hours
                return value >= 10000 && value <= 4 * 60 * 60 * 1000;
            case "distance":
                // Distance in meters: 50m to 100km
                return value >= 50 && value <= 100000;
            case "iterations":
                // Number of iterations: 1 to 50
                return value >= 1 && value <= 50;
            default:
                return value > 0 && value <= 1000000;
        }
    }
    
    /**
     * Apply automatic fixes to workout JSON
     */
    private static JSONObject applyWorkoutFixes(JSONObject workout) throws JSONException {
        JSONObject userWorkout = workout.getJSONObject("com.garmin.connect.workout.json.UserWorkoutJson");
        
        // Ensure workout has a name
        if (!userWorkout.has("workoutName") || userWorkout.getString("workoutName").trim().isEmpty()) {
            userWorkout.put("workoutName", "AI Generated Workout");
        }
        
        // Ensure sportType is set
        if (!userWorkout.has("sportType")) {
            JSONObject sportType = new JSONObject();
            sportType.put("sportTypeId", 1);
            sportType.put("sportTypeKey", "running");
            userWorkout.put("sportType", sportType);
        }
        
        // Fix workout steps
        if (userWorkout.has("workoutSteps")) {
            JSONArray steps = userWorkout.getJSONArray("workoutSteps");
            for (int i = 0; i < steps.length(); i++) {
                JSONObject step = steps.getJSONObject(i);
                fixWorkoutStep(step);
            }
        }
        
        return workout;
    }
    
    /**
     * Apply fixes to individual workout step
     */
    private static void fixWorkoutStep(JSONObject step) throws JSONException {
        // Ensure step has stepId
        if (!step.has("stepId")) {
            step.put("stepId", null);
        }
        
        // Ensure target type is set
        if (!step.has("targetTypeKey")) {
            step.put("targetTypeKey", "no.target");
        }
        
        // Add step order if missing
        if (!step.has("stepOrder")) {
            step.put("stepOrder", 1);
        }
        
        // Ensure end condition has proper units
        if (step.has("endConditionTypeKey")) {
            String endConditionType = step.getString("endConditionTypeKey");
            if ("time".equals(endConditionType) && !step.has("endConditionUnit")) {
                step.put("endConditionUnit", "ms");
            } else if ("distance".equals(endConditionType) && !step.has("endConditionUnit")) {
                step.put("endConditionUnit", "meter");
            }
        }
    }
    
    /**
     * Exception for workout validation errors
     */
    public static class WorkoutValidationException extends Exception {
        public WorkoutValidationException(String message) {
            super(message);
        }
        
        public WorkoutValidationException(String message, Throwable cause) {
            super(message, cause);
        }
    }
    
    /**
     * Exception that indicates the validation can be retried with feedback
     */
    public static class RetryableValidationException extends WorkoutValidationException {
        private final String failedJson;
        
        public RetryableValidationException(String message, String failedJson) {
            super(message);
            this.failedJson = failedJson;
        }
        
        public String getFailedJson() {
            return failedJson;
        }
    }
}