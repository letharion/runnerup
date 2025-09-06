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
 * Central constants for LLM functionality
 */
public final class LLMConstants {
    
    // Synchronizer configuration
    public static final long SYNCHRONIZER_ID = 1000L;
    
    // Request and timeout configuration
    public static final int MAX_RETRIES = 3;
    public static final long BASE_RETRY_DELAY_MS = 1000L; // 1 second
    public static final int REQUEST_TIMEOUT_MS = 60000; // 60 seconds
    public static final long MIN_REQUEST_INTERVAL_MS = 1000L; // 1 second between requests
    
    // Enhanced rate limiting
    public static final int OPENAI_REQUESTS_PER_MINUTE = 60; // OpenAI standard limit
    public static final int ANTHROPIC_REQUESTS_PER_MINUTE = 50; // Anthropic standard limit  
    public static final int DEFAULT_REQUESTS_PER_MINUTE = 30; // Conservative default
    public static final long MAX_RATE_LIMIT_WAIT_MS = 30000L; // 30 seconds max wait
    
    // Token and response limits
    public static final int DEFAULT_MAX_TOKENS = 2000;
    public static final int WORKOUT_SUGGESTIONS_MAX_TOKENS = 1000;
    public static final double DEFAULT_TEMPERATURE = 0.7;
    public static final double LOW_TEMPERATURE = 0.1; // For corrections
    public static final double WORKOUT_TEMPERATURE = 0.3; // For consistent JSON
    
    // Model names
    public static final String DEFAULT_OPENAI_MODEL = "gpt-3.5-turbo";
    public static final String DEFAULT_ANTHROPIC_MODEL = "claude-3-haiku-20240307";
    
    // HTTP response codes
    public static final int HTTP_OK = 200;
    public static final int HTTP_BAD_REQUEST = 400;
    public static final int HTTP_UNAUTHORIZED = 401;
    public static final int HTTP_TOO_MANY_REQUESTS = 429;
    public static final int HTTP_SERVER_ERROR = 500;
    
    // Workout time constants (in milliseconds)
    public static final long WARMUP_TIME_MS = 600000L; // 10 minutes
    public static final long MAIN_WORKOUT_TIME_MS = 1200000L; // 20 minutes  
    public static final long COOLDOWN_TIME_MS = 300000L; // 5 minutes
    
    // Training pace constants (seconds per km)
    public static final double DEFAULT_PACE = 300.0; // 5:00 min/km
    public static final double INTERVAL_PACE_FACTOR = 0.9; // 10% faster than average
    public static final double TEMPO_PACE_FACTOR = 0.95; // 5% faster than average
    public static final double LONG_RUN_PACE_FACTOR = 1.1; // 10% slower than average
    public static final double SPEED_PACE_FACTOR = 0.85; // 15% faster than average
    
    // Long run duration constants (minutes)
    public static final int MIN_LONG_RUN_DURATION = 60;
    public static final int MAX_LONG_RUN_DURATION = 120;
    
    // Memory requirements for local LLM
    public static final long MIN_HEAP_SIZE_FOR_LLM = 1024L * 1024L * 1024L; // 1GB
    
    // Input validation limits  
    public static final int MAX_PROMPT_LENGTH = 10000;
    public static final int MAX_WORKOUT_NAME_LENGTH = 100;
    public static final int MAX_COMMENT_LENGTH = 500;
    public static final int MAX_RACE_NAME_LENGTH = 100;
    public static final int MAX_JSON_RESPONSE_LENGTH = 50000;
    public static final int PROMPT_INJECTION_LOG_LENGTH = 100;
    
    // Workout validation limits
    public static final int MAX_WORKOUT_STEPS = 50;
    public static final int MAX_NESTING_DEPTH = 20;
    public static final int DEFAULT_INPUT_LIMIT = 1000;
    
    // Time validation (milliseconds)
    public static final long MIN_STEP_TIME_MS = 1000L; // 1 second
    public static final long MAX_STEP_TIME_MS = 24L * 60L * 60L * 1000L; // 24 hours
    
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
    
    // Workout step time validation (milliseconds)
    public static final long MIN_WORKOUT_TIME = 10000L; // 10 seconds
    public static final long MAX_WORKOUT_TIME = 4L * 60L * 60L * 1000L; // 4 hours
    
    // Unit conversion
    public static final double METERS_TO_KM = 1000.0;
    public static final int SECONDS_PER_MINUTE = 60;
    public static final int MS_PER_SECOND = 1000;
    public static final int MINUTES_PER_HOUR = 60;
    public static final int HOURS_PER_DAY = 24;
    
    // Memory optimization constants
    public static final int MAX_ACTIVITIES_PER_QUERY = 100; // Limit per database query
    public static final int ACTIVITY_LIMIT_FOR_LAP_DETAILS = 10; // Only keep lap details for recent activities
    public static final int MAX_LAPS_PER_ACTIVITY = 200; // Limit laps to prevent memory issues
    
    // Obfuscation constants (for older Android versions)
    public static final int OBFUSCATION_OFFSET = 42;
    
    // Private constructor to prevent instantiation
    private LLMConstants() {
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
    }
}