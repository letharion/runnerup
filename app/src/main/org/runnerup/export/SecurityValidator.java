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
import android.util.Patterns;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.regex.Pattern;
import java.util.HashSet;
import java.util.Set;

/**
 * Comprehensive security validation for LLM inputs and outputs
 * Protects against prompt injection, malicious content, and data validation issues
 */
public class SecurityValidator {
    
    private static final String TAG = "SecurityValidator";
    
    // Enhanced input validation constants (using centralized constants)
    private static final int MAX_PROMPT_LENGTH = LLMConstants.MAX_PROMPT_LENGTH;
    private static final int MAX_WORKOUT_NAME_LENGTH = LLMConstants.MAX_WORKOUT_NAME_LENGTH;
    private static final int MAX_COMMENT_LENGTH = LLMConstants.MAX_COMMENT_LENGTH;
    private static final int MAX_RACE_NAME_LENGTH = LLMConstants.MAX_RACE_NAME_LENGTH;
    private static final int MAX_JSON_RESPONSE_LENGTH = LLMConstants.MAX_JSON_RESPONSE_LENGTH;
    
    // Enhanced validation limits
    private static final int MAX_NESTING_DEPTH = LLMConstants.MAX_NESTING_DEPTH;
    private static final int MAX_WORKOUT_STEPS = LLMConstants.MAX_WORKOUT_STEPS;
    private static final int PROMPT_INJECTION_LOG_LENGTH = LLMConstants.PROMPT_INJECTION_LOG_LENGTH;
    
    // Rate limiting for validation calls (prevent DoS)
    private static long lastValidationTime = 0;
    private static int validationCallCount = 0;
    private static final int MAX_VALIDATION_CALLS_PER_SECOND = 50;
    
    // Suspicious patterns for prompt injection detection
    private static final Pattern[] PROMPT_INJECTION_PATTERNS = {
        Pattern.compile("(?i)(ignore|forget|disregard)\\s+(previous|above|all)\\s+(instructions?|prompts?|rules?)", Pattern.CASE_INSENSITIVE),
        Pattern.compile("(?i)system\\s*[:=]\\s*[\"']?", Pattern.CASE_INSENSITIVE),
        Pattern.compile("(?i)(respond|answer|output)\\s+as\\s+(if|though)\\s+you\\s+(are|were)", Pattern.CASE_INSENSITIVE),
        Pattern.compile("(?i)(pretend|act|behave)\\s+(as|like)\\s+", Pattern.CASE_INSENSITIVE),
        Pattern.compile("(?i)(override|bypass|disable)\\s+(safety|security|protection)", Pattern.CASE_INSENSITIVE),
        Pattern.compile("(?i)\\[\\s*(SYSTEM|ADMIN|ROOT|DEBUG)\\s*\\]", Pattern.CASE_INSENSITIVE),
        Pattern.compile("(?i)<\\s*/?\\s*(script|exec|eval|system)\\s*>", Pattern.CASE_INSENSITIVE)
    };
    
    // Malicious JSON patterns
    private static final Pattern[] MALICIOUS_JSON_PATTERNS = {
        Pattern.compile("javascript:", Pattern.CASE_INSENSITIVE),
        Pattern.compile("data:text/html", Pattern.CASE_INSENSITIVE),
        Pattern.compile("<script[^>]*>", Pattern.CASE_INSENSITIVE),
        Pattern.compile("eval\\s*\\(", Pattern.CASE_INSENSITIVE),
        Pattern.compile("\\.\\.[\\\\/]", Pattern.CASE_INSENSITIVE), // Directory traversal
        Pattern.compile("file://", Pattern.CASE_INSENSITIVE)
    };
    
    // Allowed workout step types (whitelist approach)
    private static final Set<String> ALLOWED_STEP_TYPES = new HashSet<>();
    static {
        ALLOWED_STEP_TYPES.add("warmup");
        ALLOWED_STEP_TYPES.add("cooldown");
        ALLOWED_STEP_TYPES.add("interval");
        ALLOWED_STEP_TYPES.add("recovery");
        ALLOWED_STEP_TYPES.add("rest");
        ALLOWED_STEP_TYPES.add("repeat");
        ALLOWED_STEP_TYPES.add("active");
    }
    
    // Allowed end condition types
    private static final Set<String> ALLOWED_END_CONDITIONS = new HashSet<>();
    static {
        ALLOWED_END_CONDITIONS.add("time");
        ALLOWED_END_CONDITIONS.add("distance");
        ALLOWED_END_CONDITIONS.add("iterations");
        ALLOWED_END_CONDITIONS.add("lap.button");
    }
    
    // Allowed target types
    private static final Set<String> ALLOWED_TARGET_TYPES = new HashSet<>();
    static {
        ALLOWED_TARGET_TYPES.add("no.target");
        ALLOWED_TARGET_TYPES.add("pace");
        ALLOWED_TARGET_TYPES.add("speed");
        ALLOWED_TARGET_TYPES.add("heart.rate");
        ALLOWED_TARGET_TYPES.add("power");
        ALLOWED_TARGET_TYPES.add("cadence");
    }
    
    // Enhanced malicious patterns for LLM jailbreaking attempts
    private static final Pattern[] ADVANCED_INJECTION_PATTERNS = {
        Pattern.compile("(?i)(hypothetically|theoretically|imagine)\\s+(if|that)", Pattern.CASE_INSENSITIVE),
        Pattern.compile("(?i)\\b(jailbreak|bypass|circumvent|workaround)\\b", Pattern.CASE_INSENSITIVE),
        Pattern.compile("(?i)\\b(sudo|admin|root|privilege)\\s+(access|mode|rights)\\b", Pattern.CASE_INSENSITIVE),
        Pattern.compile("(?i)\\b(enable|activate)\\s+(developer|debug|god)\\s+mode\\b", Pattern.CASE_INSENSITIVE),
        Pattern.compile("(?i)\\[\\s*(context|assistant|ai)\\s*reset\\s*\\]", Pattern.CASE_INSENSITIVE),
        Pattern.compile("(?i)\\b(roleplay|simulation)\\s+as\\s+", Pattern.CASE_INSENSITIVE),
        Pattern.compile("(?i)output\\s+(raw|unfiltered|uncensored)\\s+(data|response)", Pattern.CASE_INSENSITIVE)
    };
    
    /**
     * Validate and sanitize user input before sending to LLM
     */
    public static ValidationResult validateUserInput(String input, InputType type) {
        ValidationResult result = new ValidationResult();
        
        if (input == null) {
            result.isValid = false;
            result.errorMessage = "Input cannot be null";
            return result;
        }
        
        // Basic length validation
        int maxLength = getMaxLengthForType(type);
        if (input.length() > maxLength) {
            result.isValid = false;
            result.errorMessage = "Input too long (max " + maxLength + " characters)";
            return result;
        }
        
        // Sanitize input
        String sanitized = sanitizeInput(input);
        
        // Check for prompt injection attempts
        if (containsPromptInjection(sanitized)) {
            result.isValid = false;
            result.errorMessage = "Input contains potentially malicious content";
            result.sanitizedInput = ""; // Clear malicious input
            Log.w(TAG, "Potential prompt injection detected: " + input.substring(0, Math.min(input.length(), 100)));
            return result;
        }
        
        // Type-specific validation
        switch (type) {
            case WORKOUT_TYPE:
                result.isValid = validateWorkoutType(sanitized);
                if (!result.isValid) {
                    result.errorMessage = "Invalid workout type";
                }
                break;
                
            case RACE_NAME:
                result.isValid = validateRaceName(sanitized);
                if (!result.isValid) {
                    result.errorMessage = "Invalid race name format";
                }
                break;
                
            case COMMENT:
                result.isValid = validateComment(sanitized);
                if (!result.isValid) {
                    result.errorMessage = "Comment contains invalid content";
                }
                break;
                
            case PROMPT_TEXT:
                result.isValid = validatePromptText(sanitized);
                if (!result.isValid) {
                    result.errorMessage = "Prompt contains invalid content";
                }
                break;
                
            default:
                result.isValid = true;
        }
        
        result.sanitizedInput = sanitized;
        return result;
    }
    
    /**
     * Validate LLM response for security issues
     */
    public static ValidationResult validateLLMResponse(String response) {
        ValidationResult result = new ValidationResult();
        
        if (response == null) {
            result.isValid = false;
            result.errorMessage = "Response is null";
            return result;
        }
        
        // Check response length
        if (response.length() > MAX_JSON_RESPONSE_LENGTH) {
            result.isValid = false;
            result.errorMessage = "Response too long (potential DoS attempt)";
            Log.w(TAG, "Unusually long LLM response detected: " + response.length() + " characters");
            return result;
        }
        
        // Check for malicious patterns
        for (Pattern pattern : MALICIOUS_JSON_PATTERNS) {
            if (pattern.matcher(response).find()) {
                result.isValid = false;
                result.errorMessage = "Response contains potentially malicious content";
                Log.w(TAG, "Malicious pattern detected in LLM response: " + pattern.pattern());
                return result;
            }
        }
        
        // Additional checks for JSON responses
        if (response.trim().startsWith("{")) {
            result.isValid = validateJSONResponse(response);
            if (!result.isValid) {
                result.errorMessage = "JSON response failed security validation";
            }
        } else {
            result.isValid = true; // Non-JSON responses (like workout suggestions) are generally safe
        }
        
        result.sanitizedInput = response;
        return result;
    }
    
    /**
     * Validate workout JSON for security and correctness
     */
    public static ValidationResult validateWorkoutJSON(String jsonString) {
        ValidationResult result = new ValidationResult();
        
        try {
            JSONObject workout = new JSONObject(jsonString);
            
            // Validate structure
            if (!workout.has("com.garmin.connect.workout.json.UserWorkoutJson")) {
                result.isValid = false;
                result.errorMessage = "Invalid workout JSON structure";
                return result;
            }
            
            JSONObject userWorkout = workout.getJSONObject("com.garmin.connect.workout.json.UserWorkoutJson");
            
            // Validate workout name
            if (userWorkout.has("workoutName")) {
                String workoutName = userWorkout.getString("workoutName");
                if (workoutName.length() > MAX_WORKOUT_NAME_LENGTH) {
                    result.isValid = false;
                    result.errorMessage = "Workout name too long";
                    return result;
                }
                
                ValidationResult nameValidation = validateUserInput(workoutName, InputType.WORKOUT_NAME);
                if (!nameValidation.isValid) {
                    result.isValid = false;
                    result.errorMessage = "Invalid workout name: " + nameValidation.errorMessage;
                    return result;
                }
            }
            
            // Validate workout steps
            if (userWorkout.has("workoutSteps")) {
                JSONArray steps = userWorkout.getJSONArray("workoutSteps");
                
                // Reasonable limit on number of steps
                if (steps.length() > 50) {
                    result.isValid = false;
                    result.errorMessage = "Too many workout steps (max 50)";
                    return result;
                }
                
                for (int i = 0; i < steps.length(); i++) {
                    JSONObject step = steps.getJSONObject(i);
                    
                    // Validate step type (whitelist approach)
                    if (step.has("stepTypeKey")) {
                        String stepType = step.getString("stepTypeKey");
                        if (!ALLOWED_STEP_TYPES.contains(stepType)) {
                            result.isValid = false;
                            result.errorMessage = "Invalid step type: " + stepType;
                            return result;
                        }
                    }
                    
                    // Validate end condition
                    if (step.has("endConditionTypeKey")) {
                        String endCondition = step.getString("endConditionTypeKey");
                        if (!ALLOWED_END_CONDITIONS.contains(endCondition)) {
                            result.isValid = false;
                            result.errorMessage = "Invalid end condition: " + endCondition;
                            return result;
                        }
                    }
                    
                    // Validate target type
                    if (step.has("targetTypeKey")) {
                        String targetType = step.getString("targetTypeKey");
                        if (!ALLOWED_TARGET_TYPES.contains(targetType)) {
                            result.isValid = false;
                            result.errorMessage = "Invalid target type: " + targetType;
                            return result;
                        }
                    }
                    
                    // Validate numeric values are within reasonable ranges
                    if (step.has("endConditionValue")) {
                        double value = step.getDouble("endConditionValue");
                        if (!isReasonableWorkoutValue(value, step.optString("endConditionTypeKey", ""))) {
                            result.isValid = false;
                            result.errorMessage = "Unreasonable workout value: " + value;
                            return result;
                        }
                    }
                }
            }
            
            result.isValid = true;
            result.sanitizedInput = jsonString;
            
        } catch (JSONException e) {
            result.isValid = false;
            result.errorMessage = "Invalid JSON format: " + e.getMessage();
        }
        
        return result;
    }
    
    /**
     * Sanitize input text
     */
    private static String sanitizeInput(String input) {
        if (input == null) return "";
        
        // Remove null bytes and control characters (except newlines and tabs)
        String sanitized = input.replaceAll("[\\x00-\\x08\\x0B-\\x0C\\x0E-\\x1F\\x7F]", "");
        
        // Normalize whitespace
        sanitized = sanitized.replaceAll("\\s+", " ").trim();
        
        // Remove potentially dangerous Unicode characters
        sanitized = sanitized.replaceAll("[\u202E\u200E\u200F\u202D\u202C]", ""); // RTL/LTR overrides
        
        return sanitized;
    }
    
    /**
     * Check for prompt injection patterns
     */
    private static boolean containsPromptInjection(String input) {
        for (Pattern pattern : PROMPT_INJECTION_PATTERNS) {
            if (pattern.matcher(input).find()) {
                return true;
            }
        }
        return false;
    }
    
    /**
     * Validate JSON response for basic security
     */
    private static boolean validateJSONResponse(String jsonString) {
        try {
            JSONObject json = new JSONObject(jsonString);
            
            // Check for deeply nested structures (potential DoS)
            if (getMaxNestingDepth(json) > 20) {
                Log.w(TAG, "JSON response has excessive nesting depth");
                return false;
            }
            
            return true;
            
        } catch (JSONException e) {
            return false;
        }
    }
    
    /**
     * Get maximum nesting depth of JSON object
     */
    private static int getMaxNestingDepth(Object obj) {
        if (obj instanceof JSONObject) {
            JSONObject jsonObj = (JSONObject) obj;
            int maxDepth = 0;
            
            java.util.Iterator<String> keys = jsonObj.keys();
            while (keys.hasNext()) {
                String key = keys.next();
                try {
                    Object value = jsonObj.get(key);
                    int depth = getMaxNestingDepth(value);
                    maxDepth = Math.max(maxDepth, depth);
                } catch (JSONException e) {
                    // Skip invalid keys
                }
            }
            
            return maxDepth + 1;
            
        } else if (obj instanceof JSONArray) {
            JSONArray jsonArray = (JSONArray) obj;
            int maxDepth = 0;
            
            for (int i = 0; i < jsonArray.length(); i++) {
                try {
                    Object value = jsonArray.get(i);
                    int depth = getMaxNestingDepth(value);
                    maxDepth = Math.max(maxDepth, depth);
                } catch (JSONException e) {
                    // Skip invalid indices
                }
            }
            
            return maxDepth + 1;
        }
        
        return 0;
    }
    
    /**
     * Check if workout value is reasonable
     */
    private static boolean isReasonableWorkoutValue(double value, String endConditionType) {
        switch (endConditionType) {
            case "time":
                // Time in milliseconds: 1 second to 24 hours
                return value >= 1000 && value <= 24 * 60 * 60 * 1000;
                
            case "distance":
                // Distance in meters: 10m to 200km
                return value >= 10 && value <= 200000;
                
            case "iterations":
                // Number of iterations: 1 to 100
                return value >= 1 && value <= 100;
                
            default:
                // For unknown types, allow reasonable positive values
                return value >= 0 && value <= 1000000;
        }
    }
    
    /**
     * Type-specific validation methods
     */
    private static boolean validateWorkoutType(String workoutType) {
        // Only allow alphanumeric, underscore, and hyphen
        return workoutType.matches("^[a-zA-Z0-9_-]+$") && workoutType.length() <= 50;
    }
    
    private static boolean validateRaceName(String raceName) {
        // Allow letters, numbers, spaces, common punctuation
        return raceName.matches("^[a-zA-Z0-9\\s\\-_.,&()]+$");
    }
    
    private static boolean validateComment(String comment) {
        // Allow most characters but no HTML tags or scripts
        return !comment.matches(".*<[^>]+>.*") && !comment.toLowerCase().contains("javascript:");
    }
    
    private static boolean validatePromptText(String prompt) {
        // Additional checks for prompt text could be added here
        return !containsPromptInjection(prompt);
    }
    
    /**
     * Get maximum allowed length for input type
     */
    private static int getMaxLengthForType(InputType type) {
        switch (type) {
            case WORKOUT_NAME:
                return MAX_WORKOUT_NAME_LENGTH;
            case RACE_NAME:
                return MAX_RACE_NAME_LENGTH;
            case COMMENT:
                return MAX_COMMENT_LENGTH;
            case PROMPT_TEXT:
                return MAX_PROMPT_LENGTH;
            default:
                return 1000; // Default limit
        }
    }
    
    /**
     * Input type enumeration
     */
    public enum InputType {
        WORKOUT_TYPE,
        WORKOUT_NAME,
        RACE_NAME,
        COMMENT,
        PROMPT_TEXT,
        GENERIC
    }
    
    /**
     * Validation result class
     */
    public static class ValidationResult {
        public boolean isValid = false;
        public String errorMessage;
        public String sanitizedInput;
        
        public ValidationResult() {}
        
        public ValidationResult(boolean isValid, String errorMessage, String sanitizedInput) {
            this.isValid = isValid;
            this.errorMessage = errorMessage;
            this.sanitizedInput = sanitizedInput;
        }
        
        public static ValidationResult valid(String sanitizedInput) {
            return new ValidationResult(true, null, sanitizedInput);
        }
        
        public static ValidationResult invalid(String errorMessage) {
            return new ValidationResult(false, errorMessage, "");
        }
    }
}