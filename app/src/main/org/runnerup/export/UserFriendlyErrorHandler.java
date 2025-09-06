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
import android.util.Log;

import org.runnerup.R;

/**
 * Converts technical LLM errors into user-friendly messages with actionable suggestions
 */
public class UserFriendlyErrorHandler {
    
    private static final String TAG = "UserFriendlyErrorHandler";
    
    /**
     * Convert technical LLM exception to user-friendly message
     */
    public static String getActionableErrorMessage(Context context, LLMRequestHandler.LLMException exception) {
        if (context == null || exception == null) {
            return "An unexpected error occurred. Please try again.";
        }
        
        switch (exception.getErrorType()) {
            case NETWORK_ERROR:
                return context.getString(R.string.llm_error_network_title) + "\n\n" +
                       context.getString(R.string.llm_error_network_message) + "\n\n" +
                       "• Check your internet connection\n" +
                       "• Try again in a few moments\n" +
                       "• Switch between WiFi and mobile data";
                       
            case AUTHENTICATION_ERROR:
                return context.getString(R.string.llm_error_auth_title) + "\n\n" +
                       context.getString(R.string.llm_error_auth_message) + "\n\n" +
                       "• Go to Settings → LLM Configuration\n" +
                       "• Check your API key is entered correctly\n" +
                       "• Verify your account has remaining credits";
                       
            case RATE_LIMITED:
                return context.getString(R.string.llm_error_rate_title) + "\n\n" +
                       context.getString(R.string.llm_error_rate_message) + "\n\n" +
                       "• Wait a few minutes before trying again\n" +
                       "• Consider upgrading your API plan\n" +
                       "• Try using local LLM mode if available";
                       
            case SERVER_ERROR:
                return context.getString(R.string.llm_error_server_title) + "\n\n" +
                       context.getString(R.string.llm_error_server_message) + "\n\n" +
                       "• The service is temporarily unavailable\n" +
                       "• Try again in a few minutes\n" +
                       "• Check the service status page";
                       
            case TIMEOUT:
                return context.getString(R.string.llm_error_timeout_title) + "\n\n" +
                       context.getString(R.string.llm_error_timeout_message) + "\n\n" +
                       "• Try generating a simpler workout\n" +
                       "• Check your internet connection speed\n" +
                       "• Consider using a faster LLM model";
                       
            case INVALID_WORKOUT_FORMAT:
                return context.getString(R.string.llm_error_format_title) + "\n\n" +
                       context.getString(R.string.llm_error_format_message) + "\n\n" +
                       "• This is usually a temporary issue\n" +
                       "• Try generating the workout again\n" +
                       "• Try a different workout type";
                       
            case INVALID_REQUEST:
                return context.getString(R.string.llm_error_request_title) + "\n\n" +
                       context.getString(R.string.llm_error_request_message) + "\n\n" +
                       "• Simplify your workout request\n" +
                       "• Avoid special characters in names\n" +
                       "• Try a standard workout type";
                       
            case PARSING_ERROR:
                return context.getString(R.string.llm_error_parsing_title) + "\n\n" +
                       context.getString(R.string.llm_error_parsing_message) + "\n\n" +
                       "• Try generating the workout again\n" +
                       "• Switch to a different LLM model\n" +
                       "• Use a simpler workout type";
                       
            default:
                return context.getString(R.string.llm_error_unknown_title) + "\n\n" +
                       context.getString(R.string.llm_error_unknown_message) + "\n\n" +
                       "• Try again in a few moments\n" +
                       "• Restart the app if the problem persists\n" +
                       "• Check your internet connection";
        }
    }
    
    /**
     * Get user-friendly message for general errors
     */
    public static String getGeneralErrorMessage(Context context, Exception exception, String operation) {
        if (context == null) {
            return "Unable to " + operation + ". Please try again.";
        }
        
        String baseMessage = String.format(context.getString(R.string.llm_error_general_format), operation);
        
        if (exception == null) {
            return baseMessage + "\n\n• Try again in a few moments\n• Restart the app if needed";
        }
        
        // Common error patterns
        String errorMessage = exception.getMessage() != null ? exception.getMessage().toLowerCase() : "";
        
        if (errorMessage.contains("network") || errorMessage.contains("connection")) {
            return baseMessage + "\n\n• Check your internet connection\n• Try switching networks\n• Wait and try again";
        } else if (errorMessage.contains("timeout")) {
            return baseMessage + "\n\n• Your connection may be slow\n• Try again with a simpler request\n• Check your network speed";
        } else if (errorMessage.contains("memory") || errorMessage.contains("outofmemory")) {
            return baseMessage + "\n\n• Close other apps to free memory\n• Try a simpler workout type\n• Restart the app";
        } else if (errorMessage.contains("database") || errorMessage.contains("sql")) {
            return baseMessage + "\n\n• Your training data may be corrupted\n• Try restarting the app\n• Consider backing up and resetting data";
        } else {
            return baseMessage + "\n\n• Try again in a few moments\n• Restart the app if the problem persists\n• Check the app logs for details";
        }
    }
    
    /**
     * Get progress-friendly status messages for long operations
     */
    public static String getProgressMessage(String operation, int attemptNumber, int maxAttempts) {
        if (attemptNumber <= 1) {
            switch (operation.toLowerCase()) {
                case "workout_generation":
                    return "Analyzing your training data...";
                case "suggestions":
                    return "Reviewing your recent activities...";
                case "llm_request":
                    return "Sending request to AI service...";
                default:
                    return "Processing your request...";
            }
        } else {
            String retry = attemptNumber == 2 ? "Retrying" : "Retrying again";
            return retry + " (" + attemptNumber + "/" + maxAttempts + ")...";
        }
    }
    
    /**
     * Get success messages with helpful next steps
     */
    public static String getSuccessMessage(Context context, String operation) {
        switch (operation.toLowerCase()) {
            case "workout_generation":
                return context.getString(R.string.llm_success_workout) + "\n\n" +
                       "• Review the workout plan\n" +
                       "• Sync with your device when ready\n" +
                       "• Edit if needed before syncing";
                       
            case "suggestions":
                return context.getString(R.string.llm_success_suggestions) + "\n\n" +
                       "• Select a workout type that interests you\n" +
                       "• Consider your current fitness level\n" +
                       "• Generate a specific workout plan";
                       
            case "api_key_validation":
                return context.getString(R.string.llm_success_api_key) + "\n\n" +
                       "• Your API key is working correctly\n" +
                       "• You can now generate AI workouts\n" +
                       "• Check your usage limits regularly";
                       
            default:
                return "Operation completed successfully!";
        }
    }
    
    /**
     * Get helpful configuration messages
     */
    public static String getConfigurationMessage(Context context, String configType, boolean isFirstTime) {
        String prefix = isFirstTime ? "Welcome to AI-powered workouts!\n\n" : "";
        
        switch (configType.toLowerCase()) {
            case "api_key_needed":
                return prefix + context.getString(R.string.llm_config_api_key_needed) + "\n\n" +
                       "1. Get an API key from OpenAI or Anthropic\n" +
                       "2. Go to Settings → LLM Configuration\n" +
                       "3. Enter your API key securely\n" +
                       "4. Start generating personalized workouts!";
                       
            case "local_llm_available":
                return prefix + context.getString(R.string.llm_config_local_available) + "\n\n" +
                       "• Works without internet connection\n" +
                       "• Protects your privacy completely\n" +
                       "• May be slower than cloud services\n" +
                       "• Perfect for basic workout generation";
                       
            case "no_training_data":
                return context.getString(R.string.llm_config_no_data) + "\n\n" +
                       "• Record a few workouts to get started\n" +
                       "• AI will learn your fitness patterns\n" +
                       "• More data = better personalization\n" +
                       "• You can still generate generic workouts";
                       
            default:
                return "Configuration completed successfully!";
        }
    }
    
    /**
     * Log user-friendly error for debugging while keeping technical details
     */
    public static void logUserFriendlyError(String operation, Exception exception, String userMessage) {
        String logMessage = String.format(
            "User-friendly error for operation '%s': %s (Technical: %s)",
            operation,
            userMessage,
            exception != null ? exception.getMessage() : "unknown"
        );
        
        if (exception instanceof LLMRequestHandler.LLMException) {
            LLMRequestHandler.LLMException llmEx = (LLMRequestHandler.LLMException) exception;
            logMessage += " [Type: " + llmEx.getErrorType() + "]";
        }
        
        Log.w(TAG, logMessage, exception);
    }
}