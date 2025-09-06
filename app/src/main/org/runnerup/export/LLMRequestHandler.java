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

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.util.concurrent.TimeUnit;

/**
 * Robust LLM request handler with retries, validation, and error recovery
 */
public class LLMRequestHandler {
    
    private static final String TAG = "LLMRequestHandler";
    
    // Retry configuration
    private static final int MAX_RETRIES = LLMConstants.MAX_RETRIES;
    private static final long BASE_RETRY_DELAY_MS = LLMConstants.BASE_RETRY_DELAY_MS;
    private static final int REQUEST_TIMEOUT_MS = LLMConstants.REQUEST_TIMEOUT_MS;
    
    // Enhanced rate limiting with token bucket algorithm
    private long lastRequestTime = 0;
    private static final long MIN_REQUEST_INTERVAL_MS = LLMConstants.MIN_REQUEST_INTERVAL_MS;
    private TokenBucket rateLimiter;
    private RequestPriorityQueue priorityQueue;
    
    private final String apiProvider;
    private final String apiKey;
    
    public LLMRequestHandler(String apiProvider, String apiKey) {
        this.apiProvider = apiProvider;
        this.apiKey = apiKey;
        this.rateLimiter = new TokenBucket(getProviderRateLimit(apiProvider));
        this.priorityQueue = new RequestPriorityQueue();
    }
    
    private int getProviderRateLimit(String provider) {
        switch (provider.toLowerCase()) {
            case "openai":
                return LLMConstants.OPENAI_REQUESTS_PER_MINUTE;
            case "anthropic":
                return LLMConstants.ANTHROPIC_REQUESTS_PER_MINUTE;
            default:
                return LLMConstants.DEFAULT_REQUESTS_PER_MINUTE;
        }
    }
    
    /**
     * Make LLM request with comprehensive error handling and retries
     */
    public LLMResponse makeRequest(String prompt) throws LLMException {
        return makeRequest(prompt, null);
    }
    
    /**
     * Make LLM request with context and retry logic
     */
    public LLMResponse makeRequest(String prompt, RequestContext context) throws LLMException {
        LLMException lastException = null;
        
        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try {
                Log.d(TAG, "LLM request attempt " + attempt + "/" + MAX_RETRIES);
                
                // Rate limiting
                enforceRateLimit();
                
                // Make the request
                String response = executeRequest(prompt, context);
                
                // Validate response
                LLMResponse llmResponse = validateAndParseResponse(response, context);
                
                Log.d(TAG, "LLM request successful on attempt " + attempt);
                return llmResponse;
                
            } catch (LLMException e) {
                lastException = e;
                Log.w(TAG, "LLM request attempt " + attempt + " failed: " + e.getMessage());
                
                // Don't retry for certain error types
                if (!shouldRetry(e, attempt)) {
                    break;
                }
                
                // Wait before retry with exponential backoff
                if (attempt < MAX_RETRIES) {
                    long delay = calculateRetryDelay(attempt);
                    Log.d(TAG, "Waiting " + delay + "ms before retry");
                    try {
                        Thread.sleep(delay);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new LLMException("Request interrupted", ie);
                    }
                }
            }
        }
        
        // All retries failed
        Log.e(TAG, "All LLM request attempts failed");
        if (lastException != null) {
            throw lastException;
        } else {
            throw new LLMException("Unknown error after " + MAX_RETRIES + " attempts");
        }
    }
    
    /**
     * Execute the actual HTTP request
     */
    private String executeRequest(String prompt, RequestContext context) throws LLMException {
        try {
            String apiUrl = getApiUrl();
            JSONObject requestBody = buildRequestBody(prompt, context);
            
            HttpURLConnection connection = (HttpURLConnection) new URL(apiUrl).openConnection();
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setRequestProperty("Authorization", getAuthHeader());
            connection.setConnectTimeout(REQUEST_TIMEOUT_MS);
            connection.setReadTimeout(REQUEST_TIMEOUT_MS);
            connection.setDoOutput(true);
            
            // Send request
            try (DataOutputStream wr = new DataOutputStream(connection.getOutputStream())) {
                wr.writeBytes(requestBody.toString());
                wr.flush();
            }
            
            int responseCode = connection.getResponseCode();
            Log.d(TAG, "HTTP response code: " + responseCode);
            
            // Handle different response codes
            if (responseCode == LLMConstants.HTTP_OK) {
                // Success
                return readResponse(connection);
            } else if (responseCode == LLMConstants.HTTP_TOO_MANY_REQUESTS) {
                // Rate limited
                throw new LLMException("Rate limited by API", LLMException.ErrorType.RATE_LIMITED);
            } else if (responseCode == LLMConstants.HTTP_UNAUTHORIZED) {
                // Unauthorized
                throw new LLMException("Invalid API key", LLMException.ErrorType.AUTHENTICATION_ERROR);
            } else if (responseCode == LLMConstants.HTTP_BAD_REQUEST) {
                // Bad request
                String errorResponse = readErrorResponse(connection);
                throw new LLMException("Invalid request: " + errorResponse, LLMException.ErrorType.INVALID_REQUEST);
            } else if (responseCode >= LLMConstants.HTTP_SERVER_ERROR) {
                // Server error
                String errorResponse = readErrorResponse(connection);
                throw new LLMException("Server error: " + errorResponse, LLMException.ErrorType.SERVER_ERROR);
            } else {
                // Other error
                String errorResponse = readErrorResponse(connection);
                throw new LLMException("Unexpected response code " + responseCode + ": " + errorResponse, 
                    LLMException.ErrorType.UNKNOWN_ERROR);
            }
            
        } catch (SocketTimeoutException e) {
            throw new LLMException("Request timed out", LLMException.ErrorType.TIMEOUT, e);
        } catch (IOException e) {
            throw new LLMException("Network error: " + e.getMessage(), LLMException.ErrorType.NETWORK_ERROR, e);
        } catch (JSONException e) {
            throw new LLMException("JSON parsing error: " + e.getMessage(), LLMException.ErrorType.PARSING_ERROR, e);
        }
    }
    
    /**
     * Validate and parse the LLM response
     */
    private LLMResponse validateAndParseResponse(String rawResponse, RequestContext context) throws LLMException {
        if (rawResponse == null || rawResponse.trim().isEmpty()) {
            throw new LLMException("Empty response from API", LLMException.ErrorType.INVALID_RESPONSE);
        }
        
        try {
            JSONObject responseJson = new JSONObject(rawResponse);
            String content = extractContent(responseJson);
            
            if (content == null || content.trim().isEmpty()) {
                throw new LLMException("No content in API response", LLMException.ErrorType.INVALID_RESPONSE);
            }
            
            LLMResponse response = new LLMResponse();
            response.rawResponse = rawResponse;
            response.content = content.trim();
            response.apiProvider = apiProvider;
            response.requestContext = context;
            
            // Additional validation based on context
            if (context != null && context.expectedResponseType == RequestContext.ResponseType.WORKOUT_JSON) {
                validateWorkoutJson(response.content);
            }
            
            return response;
            
        } catch (JSONException e) {
            throw new LLMException("Invalid JSON response: " + e.getMessage(), LLMException.ErrorType.PARSING_ERROR, e);
        }
    }
    
    /**
     * Validate workout JSON structure
     */
    private void validateWorkoutJson(String content) throws LLMException {
        // Extract JSON from response (might have markdown formatting)
        String jsonContent = extractJsonFromResponse(content);
        
        try {
            JSONObject workout = new JSONObject(jsonContent);
            
            // Check for required Garmin Connect structure
            if (!workout.has("com.garmin.connect.workout.json.UserWorkoutJson")) {
                throw new LLMException("Invalid workout JSON: missing UserWorkoutJson", 
                    LLMException.ErrorType.INVALID_WORKOUT_FORMAT);
            }
            
            JSONObject userWorkout = workout.getJSONObject("com.garmin.connect.workout.json.UserWorkoutJson");
            
            // Check required fields
            if (!userWorkout.has("workoutSteps")) {
                throw new LLMException("Invalid workout JSON: missing workoutSteps", 
                    LLMException.ErrorType.INVALID_WORKOUT_FORMAT);
            }
            
            JSONArray steps = userWorkout.getJSONArray("workoutSteps");
            if (steps.length() == 0) {
                throw new LLMException("Invalid workout JSON: no workout steps", 
                    LLMException.ErrorType.INVALID_WORKOUT_FORMAT);
            }
            
            // Validate each step has required fields
            for (int i = 0; i < steps.length(); i++) {
                JSONObject step = steps.getJSONObject(i);
                
                if (!step.has("stepTypeKey")) {
                    throw new LLMException("Invalid workout JSON: step missing stepTypeKey", 
                        LLMException.ErrorType.INVALID_WORKOUT_FORMAT);
                }
                
                if (!step.has("endConditionTypeKey")) {
                    throw new LLMException("Invalid workout JSON: step missing endConditionTypeKey", 
                        LLMException.ErrorType.INVALID_WORKOUT_FORMAT);
                }
            }
            
            Log.d(TAG, "Workout JSON validation successful");
            
        } catch (JSONException e) {
            throw new LLMException("Invalid workout JSON format: " + e.getMessage(), 
                LLMException.ErrorType.INVALID_WORKOUT_FORMAT, e);
        }
    }
    
    /**
     * Extract JSON content from LLM response (handles markdown code blocks)
     */
    private String extractJsonFromResponse(String response) {
        // Try to find JSON within markdown code blocks
        String[] codeBlockMarkers = {"```json", "```", "`"};
        
        for (String marker : codeBlockMarkers) {
            int startIndex = response.indexOf(marker);
            if (startIndex >= 0) {
                startIndex += marker.length();
                int endIndex = response.indexOf(marker, startIndex);
                if (endIndex > startIndex) {
                    return response.substring(startIndex, endIndex).trim();
                }
            }
        }
        
        // Try to find JSON by looking for opening and closing braces
        int jsonStart = response.indexOf('{');
        int jsonEnd = response.lastIndexOf('}');
        
        if (jsonStart >= 0 && jsonEnd > jsonStart) {
            return response.substring(jsonStart, jsonEnd + 1);
        }
        
        // Return original response if no JSON markers found
        return response;
    }
    
    /**
     * Extract content from API response based on provider
     */
    private String extractContent(JSONObject responseJson) throws JSONException {
        switch (apiProvider.toLowerCase()) {
            case "openai":
                JSONArray choices = responseJson.getJSONArray("choices");
                if (choices.length() > 0) {
                    return choices.getJSONObject(0)
                        .getJSONObject("message")
                        .getString("content");
                }
                break;
                
            case "anthropic":
                JSONArray content = responseJson.getJSONArray("content");
                if (content.length() > 0) {
                    return content.getJSONObject(0).getString("text");
                }
                break;
        }
        
        throw new JSONException("Unable to extract content from " + apiProvider + " response");
    }
    
    /**
     * Build request body based on API provider
     */
    private JSONObject buildRequestBody(String prompt, RequestContext context) throws JSONException {
        JSONObject request = new JSONObject();
        
        switch (apiProvider.toLowerCase()) {
            case "openai":
                request.put("model", context != null && context.model != null ? context.model : LLMConstants.DEFAULT_OPENAI_MODEL);
                JSONArray messages = new JSONArray();
                JSONObject message = new JSONObject();
                message.put("role", "user");
                message.put("content", prompt);
                messages.put(message);
                request.put("messages", messages);
                request.put("max_tokens", context != null ? context.maxTokens : LLMConstants.DEFAULT_MAX_TOKENS);
                request.put("temperature", context != null ? context.temperature : LLMConstants.DEFAULT_TEMPERATURE);
                break;
                
            case "anthropic":
                request.put("model", context != null && context.model != null ? context.model : LLMConstants.DEFAULT_ANTHROPIC_MODEL);
                request.put("max_tokens", context != null ? context.maxTokens : LLMConstants.DEFAULT_MAX_TOKENS);
                request.put("messages", new JSONArray().put(
                    new JSONObject().put("role", "user").put("content", prompt)
                ));
                break;
        }
        
        return request;
    }
    
    private String getApiUrl() {
        switch (apiProvider.toLowerCase()) {
            case "anthropic":
                return "https://api.anthropic.com/v1/messages";
            case "openai":
            default:
                return "https://api.openai.com/v1/chat/completions";
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
    
    private String readResponse(HttpURLConnection connection) throws IOException {
        BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream()));
        StringBuilder response = new StringBuilder();
        String inputLine;
        while ((inputLine = in.readLine()) != null) {
            response.append(inputLine);
        }
        in.close();
        return response.toString();
    }
    
    private String readErrorResponse(HttpURLConnection connection) {
        try {
            BufferedReader in = new BufferedReader(new InputStreamReader(connection.getErrorStream()));
            StringBuilder response = new StringBuilder();
            String inputLine;
            while ((inputLine = in.readLine()) != null) {
                response.append(inputLine);
            }
            in.close();
            return response.toString();
        } catch (Exception e) {
            return "Unknown error";
        }
    }
    
    private void enforceRateLimit() throws LLMException {
        // Use token bucket for more sophisticated rate limiting
        if (!rateLimiter.tryConsume()) {
            long waitTime = rateLimiter.getRefillTimeMs();
            Log.d(TAG, "Rate limiting: waiting " + waitTime + "ms for token bucket refill");
            
            if (waitTime > LLMConstants.MAX_RATE_LIMIT_WAIT_MS) {
                throw new LLMException("Rate limit exceeded, wait time too long: " + waitTime + "ms", 
                    LLMException.ErrorType.RATE_LIMITED);
            }
            
            try {
                Thread.sleep(waitTime);
                if (!rateLimiter.tryConsume()) {
                    throw new LLMException("Rate limit still exceeded after waiting", 
                        LLMException.ErrorType.RATE_LIMITED);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new LLMException("Rate limiting interrupted", LLMException.ErrorType.UNKNOWN_ERROR, e);
            }
        }
        
        lastRequestTime = System.currentTimeMillis();
    }
    
    private boolean shouldRetry(LLMException e, int attemptNumber) {
        switch (e.getErrorType()) {
            case RATE_LIMITED:
            case SERVER_ERROR:
            case TIMEOUT:
            case NETWORK_ERROR:
                return true; // Retry these errors
                
            case AUTHENTICATION_ERROR:
            case INVALID_REQUEST:
            case PARSING_ERROR:
                return false; // Don't retry these errors
                
            case INVALID_RESPONSE:
            case INVALID_WORKOUT_FORMAT:
                return attemptNumber <= 2; // Retry once for response format issues
                
            default:
                return true; // Retry unknown errors
        }
    }
    
    private long calculateRetryDelay(int attemptNumber) {
        // Exponential backoff: 1s, 2s, 4s
        return BASE_RETRY_DELAY_MS * (1L << (attemptNumber - 1));
    }
    
    /**
     * Request context for additional parameters and validation
     */
    public static class RequestContext {
        public enum ResponseType {
            WORKOUT_JSON,
            WORKOUT_SUGGESTIONS,
            GENERAL
        }
        
        public ResponseType expectedResponseType = ResponseType.GENERAL;
        public String model;
        public int maxTokens = LLMConstants.DEFAULT_MAX_TOKENS;
        public double temperature = LLMConstants.DEFAULT_TEMPERATURE;
        public String workoutType;
        
        public static RequestContext forWorkoutGeneration(String workoutType) {
            RequestContext context = new RequestContext();
            context.expectedResponseType = ResponseType.WORKOUT_JSON;
            context.workoutType = workoutType;
            context.temperature = LLMConstants.WORKOUT_TEMPERATURE; // Lower temperature for more consistent JSON
            return context;
        }
        
        public static RequestContext forWorkoutSuggestions() {
            RequestContext context = new RequestContext();
            context.expectedResponseType = ResponseType.WORKOUT_SUGGESTIONS;
            context.maxTokens = LLMConstants.WORKOUT_SUGGESTIONS_MAX_TOKENS;
            return context;
        }
    }
    
    /**
     * LLM response wrapper
     */
    public static class LLMResponse {
        public String rawResponse;
        public String content;
        public String apiProvider;
        public RequestContext requestContext;
    }
    
    /**
     * Custom exception for LLM-related errors
     */
    public static class LLMException extends Exception {
        public enum ErrorType {
            NETWORK_ERROR,
            AUTHENTICATION_ERROR,
            RATE_LIMITED,
            SERVER_ERROR,
            INVALID_REQUEST,
            INVALID_RESPONSE,
            INVALID_WORKOUT_FORMAT,
            PARSING_ERROR,
            TIMEOUT,
            UNKNOWN_ERROR
        }
        
        private final ErrorType errorType;
        
        public LLMException(String message, ErrorType errorType) {
            super(message);
            this.errorType = errorType;
        }
        
        public LLMException(String message, ErrorType errorType, Throwable cause) {
            super(message, cause);
            this.errorType = errorType;
        }
        
        public LLMException(String message) {
            super(message);
            this.errorType = ErrorType.UNKNOWN_ERROR;
        }
        
        public LLMException(String message, Throwable cause) {
            super(message, cause);
            this.errorType = ErrorType.UNKNOWN_ERROR;
        }
        
        public ErrorType getErrorType() {
            return errorType;
        }
        
        public boolean isRetryable() {
            switch (errorType) {
                case RATE_LIMITED:
                case SERVER_ERROR:
                case TIMEOUT:
                case NETWORK_ERROR:
                    return true;
                default:
                    return false;
            }
        }
    }
    
    /**
     * Token bucket implementation for sophisticated rate limiting
     */
    private static class TokenBucket {
        private final int capacity;
        private final long refillIntervalMs;
        private int tokens;
        private long lastRefillTime;
        
        public TokenBucket(int requestsPerMinute) {
            this.capacity = requestsPerMinute;
            this.refillIntervalMs = 60000L / requestsPerMinute; // milliseconds between token refills
            this.tokens = capacity;
            this.lastRefillTime = System.currentTimeMillis();
        }
        
        public synchronized boolean tryConsume() {
            refill();
            if (tokens > 0) {
                tokens--;
                return true;
            }
            return false;
        }
        
        public synchronized long getRefillTimeMs() {
            refill();
            if (tokens > 0) {
                return 0;
            }
            return refillIntervalMs - (System.currentTimeMillis() - lastRefillTime);
        }
        
        private void refill() {
            long currentTime = System.currentTimeMillis();
            long timePassed = currentTime - lastRefillTime;
            int tokensToAdd = (int) (timePassed / refillIntervalMs);
            
            if (tokensToAdd > 0) {
                tokens = Math.min(capacity, tokens + tokensToAdd);
                lastRefillTime = currentTime;
            }
        }
    }
    
    /**
     * Priority queue for managing request ordering
     */
    private static class RequestPriorityQueue {
        // Simple FIFO for now - could be enhanced with actual priority logic
        private final java.util.concurrent.ConcurrentLinkedQueue<RequestInfo> queue;
        
        public RequestPriorityQueue() {
            this.queue = new java.util.concurrent.ConcurrentLinkedQueue<>();
        }
        
        public void addRequest(RequestInfo requestInfo) {
            queue.offer(requestInfo);
        }
        
        public RequestInfo getNextRequest() {
            return queue.poll();
        }
        
        public boolean isEmpty() {
            return queue.isEmpty();
        }
    }
    
    /**
     * Request information for priority queue
     */
    private static class RequestInfo {
        public final String prompt;
        public final RequestContext context;
        public final long timestamp;
        public final int priority;
        
        public RequestInfo(String prompt, RequestContext context, int priority) {
            this.prompt = prompt;
            this.context = context;
            this.timestamp = System.currentTimeMillis();
            this.priority = priority;
        }
    }
}