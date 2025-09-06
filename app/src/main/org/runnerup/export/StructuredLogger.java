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

import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;

/**
 * Structured logging system for LLM operations with contextual information
 */
public class StructuredLogger {
    
    private static final String TAG = "StructuredLogger";
    private static final String LLM_TAG_PREFIX = "LLM_";
    
    // Log categories for better filtering
    public enum Category {
        REQUEST("REQUEST"),
        RESPONSE("RESPONSE"), 
        ERROR("ERROR"),
        PERFORMANCE("PERFORMANCE"),
        SECURITY("SECURITY"),
        USER_ACTION("USER_ACTION"),
        SYSTEM("SYSTEM");
        
        private final String value;
        Category(String value) { this.value = value; }
        @Override public String toString() { return value; }
    }
    
    // Log levels matching Android Log levels
    public enum Level {
        VERBOSE(Log.VERBOSE),
        DEBUG(Log.DEBUG),
        INFO(Log.INFO),
        WARN(Log.WARN),
        ERROR(Log.ERROR);
        
        private final int androidLevel;
        Level(int level) { this.androidLevel = level; }
        public int getAndroidLevel() { return androidLevel; }
    }
    
    /**
     * Log LLM request with structured context
     */
    public static void logRequest(String operation, String provider, Map<String, Object> context) {
        JSONObject logData = new JSONObject();
        try {
            logData.put("timestamp", System.currentTimeMillis());
            logData.put("category", Category.REQUEST.toString());
            logData.put("operation", operation);
            logData.put("provider", provider);
            logData.put("thread", Thread.currentThread().getName());
            
            if (context != null) {
                for (Map.Entry<String, Object> entry : context.entrySet()) {
                    logData.put(entry.getKey(), entry.getValue());
                }
            }
            
        } catch (JSONException e) {
            Log.w(TAG, "Failed to create request log JSON", e);
        }
        
        String tag = LLM_TAG_PREFIX + Category.REQUEST;
        Log.i(tag, logData.toString());
    }
    
    /**
     * Log LLM response with metrics
     */
    public static void logResponse(String operation, String provider, boolean success, 
                                 long durationMs, Map<String, Object> metrics) {
        JSONObject logData = new JSONObject();
        try {
            logData.put("timestamp", System.currentTimeMillis());
            logData.put("category", Category.RESPONSE.toString());
            logData.put("operation", operation);
            logData.put("provider", provider);
            logData.put("success", success);
            logData.put("duration_ms", durationMs);
            logData.put("thread", Thread.currentThread().getName());
            
            if (metrics != null) {
                for (Map.Entry<String, Object> entry : metrics.entrySet()) {
                    logData.put(entry.getKey(), entry.getValue());
                }
            }
            
        } catch (JSONException e) {
            Log.w(TAG, "Failed to create response log JSON", e);
        }
        
        String tag = LLM_TAG_PREFIX + Category.RESPONSE;
        Level level = success ? Level.INFO : Level.WARN;
        log(level, tag, logData.toString());
    }
    
    /**
     * Log structured error with context
     */
    public static void logError(String operation, Throwable throwable, Map<String, Object> context) {
        JSONObject logData = new JSONObject();
        try {
            logData.put("timestamp", System.currentTimeMillis());
            logData.put("category", Category.ERROR.toString());
            logData.put("operation", operation);
            logData.put("thread", Thread.currentThread().getName());
            
            if (throwable != null) {
                logData.put("exception_class", throwable.getClass().getSimpleName());
                logData.put("exception_message", throwable.getMessage());
                
                if (throwable instanceof LLMRequestHandler.LLMException) {
                    LLMRequestHandler.LLMException llmEx = (LLMRequestHandler.LLMException) throwable;
                    logData.put("error_type", llmEx.getErrorType().toString());
                    logData.put("is_retryable", llmEx.isRetryable());
                }
                
                // Include stack trace for debugging (first few frames)
                StackTraceElement[] stack = throwable.getStackTrace();
                if (stack.length > 0) {
                    logData.put("stack_trace_top", stack[0].toString());
                }
            }
            
            if (context != null) {
                for (Map.Entry<String, Object> entry : context.entrySet()) {
                    logData.put(entry.getKey(), entry.getValue());
                }
            }
            
        } catch (JSONException e) {
            Log.w(TAG, "Failed to create error log JSON", e);
        }
        
        String tag = LLM_TAG_PREFIX + Category.ERROR;
        Log.e(tag, logData.toString(), throwable);
    }
    
    /**
     * Log performance metrics
     */
    public static void logPerformance(String operation, Map<String, Object> metrics) {
        JSONObject logData = new JSONObject();
        try {
            logData.put("timestamp", System.currentTimeMillis());
            logData.put("category", Category.PERFORMANCE.toString());
            logData.put("operation", operation);
            logData.put("thread", Thread.currentThread().getName());
            
            if (metrics != null) {
                for (Map.Entry<String, Object> entry : metrics.entrySet()) {
                    logData.put(entry.getKey(), entry.getValue());
                }
            }
            
            // Add memory information
            Runtime runtime = Runtime.getRuntime();
            logData.put("memory_used_mb", (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024));
            logData.put("memory_free_mb", runtime.freeMemory() / (1024 * 1024));
            logData.put("memory_total_mb", runtime.totalMemory() / (1024 * 1024));
            logData.put("memory_max_mb", runtime.maxMemory() / (1024 * 1024));
            
        } catch (JSONException e) {
            Log.w(TAG, "Failed to create performance log JSON", e);
        }
        
        String tag = LLM_TAG_PREFIX + Category.PERFORMANCE;
        Log.d(tag, logData.toString());
    }
    
    /**
     * Log security events
     */
    public static void logSecurity(String event, String severity, Map<String, Object> details) {
        JSONObject logData = new JSONObject();
        try {
            logData.put("timestamp", System.currentTimeMillis());
            logData.put("category", Category.SECURITY.toString());
            logData.put("event", event);
            logData.put("severity", severity);
            logData.put("thread", Thread.currentThread().getName());
            
            if (details != null) {
                for (Map.Entry<String, Object> entry : details.entrySet()) {
                    // Be careful not to log sensitive data
                    String key = entry.getKey();
                    Object value = entry.getValue();
                    
                    if (key.toLowerCase().contains("password") || 
                        key.toLowerCase().contains("key") ||
                        key.toLowerCase().contains("token")) {
                        logData.put(key, "[REDACTED]");
                    } else {
                        logData.put(key, value);
                    }
                }
            }
            
        } catch (JSONException e) {
            Log.w(TAG, "Failed to create security log JSON", e);
        }
        
        String tag = LLM_TAG_PREFIX + Category.SECURITY;
        Level level = "HIGH".equals(severity) || "CRITICAL".equals(severity) ? Level.ERROR : Level.WARN;
        log(level, tag, logData.toString());
    }
    
    /**
     * Log user actions for analytics
     */
    public static void logUserAction(String action, Map<String, Object> context) {
        JSONObject logData = new JSONObject();
        try {
            logData.put("timestamp", System.currentTimeMillis());
            logData.put("category", Category.USER_ACTION.toString());
            logData.put("action", action);
            logData.put("thread", Thread.currentThread().getName());
            
            if (context != null) {
                for (Map.Entry<String, Object> entry : context.entrySet()) {
                    logData.put(entry.getKey(), entry.getValue());
                }
            }
            
        } catch (JSONException e) {
            Log.w(TAG, "Failed to create user action log JSON", e);
        }
        
        String tag = LLM_TAG_PREFIX + Category.USER_ACTION;
        Log.i(tag, logData.toString());
    }
    
    /**
     * Log system events
     */
    public static void logSystem(String event, Level level, Map<String, Object> details) {
        JSONObject logData = new JSONObject();
        try {
            logData.put("timestamp", System.currentTimeMillis());
            logData.put("category", Category.SYSTEM.toString());
            logData.put("event", event);
            logData.put("level", level.toString());
            logData.put("thread", Thread.currentThread().getName());
            
            if (details != null) {
                for (Map.Entry<String, Object> entry : details.entrySet()) {
                    logData.put(entry.getKey(), entry.getValue());
                }
            }
            
        } catch (JSONException e) {
            Log.w(TAG, "Failed to create system log JSON", e);
        }
        
        String tag = LLM_TAG_PREFIX + Category.SYSTEM;
        log(level, tag, logData.toString());
    }
    
    /**
     * Create context map for logging
     */
    public static Map<String, Object> createContext() {
        return new HashMap<>();
    }
    
    /**
     * Add item to context map
     */
    public static Map<String, Object> addToContext(Map<String, Object> context, String key, Object value) {
        if (context == null) {
            context = new HashMap<>();
        }
        context.put(key, value);
        return context;
    }
    
    /**
     * Log with specific level and tag
     */
    private static void log(Level level, String tag, String message) {
        switch (level) {
            case VERBOSE:
                Log.v(tag, message);
                break;
            case DEBUG:
                Log.d(tag, message);
                break;
            case INFO:
                Log.i(tag, message);
                break;
            case WARN:
                Log.w(tag, message);
                break;
            case ERROR:
                Log.e(tag, message);
                break;
        }
    }
    
    /**
     * Helper methods for common logging patterns
     */
    public static class Helpers {
        
        /**
         * Log LLM request start
         */
        public static void logRequestStart(String operation, String provider, String workoutType) {
            Map<String, Object> context = createContext();
            addToContext(context, "workout_type", workoutType);
            addToContext(context, "request_id", generateRequestId());
            logRequest(operation, provider, context);
        }
        
        /**
         * Log LLM request success
         */
        public static void logRequestSuccess(String operation, String provider, long durationMs, 
                                           int responseLength, int retryCount) {
            Map<String, Object> metrics = createContext();
            addToContext(metrics, "response_length", responseLength);
            addToContext(metrics, "retry_count", retryCount);
            logResponse(operation, provider, true, durationMs, metrics);
        }
        
        /**
         * Log LLM request failure
         */
        public static void logRequestFailure(String operation, String provider, long durationMs, 
                                           Throwable error, int retryCount) {
            Map<String, Object> context = createContext();
            addToContext(context, "provider", provider);
            addToContext(context, "duration_ms", durationMs);
            addToContext(context, "retry_count", retryCount);
            logError(operation, error, context);
        }
        
        /**
         * Log database performance
         */
        public static void logDatabaseQuery(String query, long durationMs, int resultCount) {
            Map<String, Object> metrics = createContext();
            addToContext(metrics, "query_type", extractQueryType(query));
            addToContext(metrics, "duration_ms", durationMs);
            addToContext(metrics, "result_count", resultCount);
            logPerformance("database_query", metrics);
        }
        
        /**
         * Log validation event
         */
        public static void logValidationEvent(String inputType, boolean passed, String reason) {
            Map<String, Object> context = createContext();
            addToContext(context, "input_type", inputType);
            addToContext(context, "passed", passed);
            if (reason != null) {
                addToContext(context, "reason", reason);
            }
            
            if (passed) {
                logUserAction("validation_passed", context);
            } else {
                String severity = reason != null && reason.toLowerCase().contains("injection") ? "HIGH" : "MEDIUM";
                logSecurity("validation_failed", severity, context);
            }
        }
        
        private static String generateRequestId() {
            return "req_" + System.currentTimeMillis() + "_" + (int)(Math.random() * 1000);
        }
        
        private static String extractQueryType(String query) {
            if (query == null) return "unknown";
            String upperQuery = query.trim().toUpperCase();
            if (upperQuery.startsWith("SELECT")) return "select";
            if (upperQuery.startsWith("INSERT")) return "insert";
            if (upperQuery.startsWith("UPDATE")) return "update";
            if (upperQuery.startsWith("DELETE")) return "delete";
            return "other";
        }
    }
}