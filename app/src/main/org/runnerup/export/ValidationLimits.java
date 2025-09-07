/*
 * Copyright (C) 2024
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 */

package org.runnerup.export;

/**
 * Consolidated validation limits for LLM functionality
 * Centralizes all validation constants to avoid duplication
 */
public final class ValidationLimits {
    
    // Input length validation limits
    public static final int MAX_PROMPT_LENGTH = 10000;
    public static final int MAX_WORKOUT_NAME_LENGTH = 100;
    public static final int MAX_COMMENT_LENGTH = 500;
    public static final int MAX_RACE_NAME_LENGTH = 100;
    public static final int MAX_JSON_RESPONSE_LENGTH = 50000;
    public static final int PROMPT_INJECTION_LOG_LENGTH = 100;
    public static final int DEFAULT_INPUT_LIMIT = 1000;
    
    // Workout validation limits
    public static final int MAX_WORKOUT_STEPS = 50;
    public static final int MAX_NESTING_DEPTH = 20;
    
    // Time validation (milliseconds)
    public static final long MIN_STEP_TIME_MS = 1000L; // 1 second
    public static final long MAX_STEP_TIME_MS = 24L * 60L * 60L * 1000L; // 24 hours
    public static final long MIN_WORKOUT_TIME = 10000L; // 10 seconds
    public static final long MAX_WORKOUT_TIME = 4L * 60L * 60L * 1000L; // 4 hours
    
    // Distance validation (meters)  
    public static final double MIN_STEP_DISTANCE_SECURITY = 10.0; // SecurityValidator
    public static final double MAX_STEP_DISTANCE_SECURITY = 200000.0; // SecurityValidator - 200km
    public static final double MIN_STEP_DISTANCE_WORKOUT = 50.0; // WorkoutJsonProcessor  
    public static final double MAX_STEP_DISTANCE_WORKOUT = 100000.0; // WorkoutJsonProcessor - 100km
    
    // Iterations validation
    public static final int MIN_ITERATIONS = 1;
    public static final int MAX_ITERATIONS_SECURITY = 100; // SecurityValidator
    public static final int MAX_ITERATIONS_WORKOUT = 50; // WorkoutJsonProcessor
    
    // Generic validation limits
    public static final double MIN_GENERIC_VALUE = 0.0;
    public static final double MAX_GENERIC_VALUE = 1000000.0;
    
    // Rate limiting
    public static final int MAX_VALIDATION_CALLS_PER_SECOND = 50;
    public static final long VALIDATION_RATE_LIMIT_WINDOW_MS = 1000L;
    
    // Memory optimization constants
    public static final int MAX_ACTIVITIES_PER_QUERY = 100; // Limit per database query
    public static final int ACTIVITY_LIMIT_FOR_LAP_DETAILS = 10; // Only keep lap details for recent activities
    public static final int MAX_LAPS_PER_ACTIVITY = 200; // Limit laps to prevent memory issues
    
    // Cache management
    public static final int MAX_CACHE_SIZE_BEFORE_CLEANUP = 100; // Trigger cleanup when cache exceeds this size
    
    // Private constructor to prevent instantiation
    private ValidationLimits() {
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
    }
}