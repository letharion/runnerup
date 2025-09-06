/*
 * Copyright (C) 2024
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 */

package org.runnerup.export;

import java.util.List;

/**
 * Builds LLM prompts for workout generation and suggestions
 */
public class LLMPromptBuilder {
    
    /**
     * Build advanced workout prompt with training analysis
     */
    public static String buildAdvancedWorkoutPrompt(String workoutType, TrainingHistoryAnalyzer.TrainingAnalysis analysis) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("Generate a detailed workout plan in Garmin Connect JSON format.\n\n");
        
        // Add training context
        if (analysis != null && analysis.recentActivities.size() > 0) {
            prompt.append(analysis.generateLLMSummary());
        }
        
        // Goals context would be added here if passed as parameter
        
        prompt.append("WORKOUT REQUEST: ").append(formatWorkoutDisplayName(workoutType)).append("\n\n");
        
        addWorkoutSpecificGuidance(prompt, workoutType, analysis);
        
        prompt.append("IMPORTANT REQUIREMENTS:\n");
        prompt.append("- Return ONLY valid JSON in Garmin Connect workout format\n");
        prompt.append("- Use realistic times and distances based on training analysis\n");
        prompt.append("- Include proper warmup and cooldown phases\n");
        prompt.append("- Ensure all stepTypeKey values are: warmup, interval, recovery, rest, cooldown, or repeat\n");
        prompt.append("- All endConditionTypeKey values must be: time, distance, iterations, or lap.button\n");
        prompt.append("- Time values in milliseconds, distance in meters\n");
        prompt.append("- Include targetTypeKey as: no.target, pace, speed, heart.rate, power, or cadence\n\n");
        
        return prompt.toString();
    }
    
    /**
     * Add workout-specific guidance to prompt
     */
    private static void addWorkoutSpecificGuidance(StringBuilder prompt, String workoutType, TrainingHistoryAnalyzer.TrainingAnalysis analysis) {
        double recentPace = calculateRecentAveragePace(analysis);
        
        switch (workoutType) {
            case "ai_intervals":
                prompt.append("Create interval training with:\n");
                if (recentPace > 0) {
                    double intervalPace = recentPace * 0.9; // 10% faster than average
                    int paceMin = (int) (intervalPace / 60);
                    int paceSec = (int) (intervalPace % 60);
                    prompt.append("- Target interval pace: around ").append(paceMin).append(":").append(String.format("%02d", paceSec)).append(" min/km\n");
                }
                prompt.append("- 4-8 intervals of 2-5 minutes each\n");
                prompt.append("- Recovery between intervals\n");
                break;
                
            case "ai_tempo_run":
                prompt.append("Create tempo run with:\n");
                if (recentPace > 0) {
                    double tempoPace = recentPace * 0.95; // 5% faster than average
                    int paceMin = (int) (tempoPace / 60);
                    int paceSec = (int) (tempoPace % 60);
                    prompt.append("- Target tempo pace: around ").append(paceMin).append(":").append(String.format("%02d", paceSec)).append(" min/km\n");
                }
                prompt.append("- 20-40 minute sustained effort\n");
                prompt.append("- Comfortably hard intensity\n");
                break;
                
            case "ai_long_run":
                prompt.append("Create long run with:\n");
                if (recentPace > 0) {
                    double longPace = recentPace * 1.1; // 10% slower than average
                    int paceMin = (int) (longPace / 60);
                    int paceSec = (int) (longPace % 60);
                    prompt.append("- Target pace: around ").append(paceMin).append(":").append(String.format("%02d", paceSec)).append(" min/km\n");
                }
                prompt.append("- Duration: ").append(LLMConstants.MIN_LONG_RUN_DURATION).append("-").append(LLMConstants.MAX_LONG_RUN_DURATION).append(" minutes\n");
                prompt.append("- Easy, conversational pace\n");
                break;
                
            case "ai_speed_work":
                prompt.append("Create speed work with:\n");
                if (recentPace > 0) {
                    double speedPace = recentPace * 0.85; // 15% faster than average
                    int paceMin = (int) (speedPace / 60);
                    int paceSec = (int) (speedPace % 60);
                    prompt.append("- Target pace: around ").append(paceMin).append(":").append(String.format("%02d", paceSec)).append(" min/km\n");
                }
                prompt.append("- Short, fast intervals (30s-2min)\n");
                prompt.append("- Full recovery between reps\n");
                break;
                
            default:
                prompt.append("Create ").append(formatWorkoutDisplayName(workoutType)).append(" with appropriate pacing and structure.\n");
                break;
        }
        prompt.append("\n");
    }
    
    /**
     * Calculate recent average pace from training analysis
     */
    private static double calculateRecentAveragePace(TrainingHistoryAnalyzer.TrainingAnalysis analysis) {
        if (analysis == null || analysis.recentActivities.isEmpty()) {
            return LLMConstants.DEFAULT_PACE; // Default 5:00 min/km if no data
        }
        
        double totalPace = 0;
        int count = 0;
        
        for (TrainingHistoryAnalyzer.ActivitySummary activity : analysis.recentActivities) {
            if (activity.avgPace > 0 && "Running".equals(activity.sport)) {
                totalPace += activity.avgPace;
                count++;
            }
        }
        
        return count > 0 ? totalPace / count : LLMConstants.DEFAULT_PACE;
    }
    
    /**
     * Build advanced workout suggestions prompt
     */
    public static String buildAdvancedSuggestionsPrompt(TrainingHistoryAnalyzer.TrainingAnalysis analysis) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("Based on the following training analysis, suggest 3-5 appropriate workout types:\n\n");
        
        if (analysis != null && !analysis.recentActivities.isEmpty()) {
            prompt.append(analysis.generateLLMSummary());
            prompt.append("\n");
        }
        
        prompt.append("Please suggest workout types from this list:\n");
        prompt.append("- ai_intervals: High-intensity interval training\n");
        prompt.append("- ai_tempo_run: Sustained comfortably hard effort\n");
        prompt.append("- ai_long_run: Easy-paced endurance building\n");
        prompt.append("- ai_speed_work: Short, fast intervals\n");
        prompt.append("- ai_recovery_run: Active recovery at easy pace\n");
        prompt.append("- ai_fartlek: Unstructured speed play\n");
        prompt.append("- ai_hill_repeats: Uphill interval training\n");
        prompt.append("\nProvide brief explanations for each suggestion.\n");
        
        return prompt.toString();
    }
    
    /**
     * Build basic workout prompt for simple requests
     */
    public static String buildWorkoutPrompt(String workoutType, List<TrainingData> userData) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("Generate a ").append(formatWorkoutDisplayName(workoutType));
        prompt.append(" workout in Garmin Connect JSON format.\n\n");
        
        if (userData != null && !userData.isEmpty()) {
            prompt.append("Recent training data:\n");
            for (TrainingData data : userData) {
                prompt.append("- ").append(data.toString()).append("\n");
            }
            prompt.append("\n");
        }
        
        prompt.append("Requirements:\n");
        prompt.append("- Return valid JSON only\n");
        prompt.append("- Include warmup and cooldown\n");
        prompt.append("- Use realistic paces and durations\n");
        prompt.append("- Follow Garmin Connect workout schema\n");
        
        return prompt.toString();
    }
    
    /**
     * Build workout suggestions prompt for simple requests
     */
    public static String buildSuggestionsPrompt(List<TrainingData> recentActivities) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("Based on recent training activities, suggest appropriate workout types:\n\n");
        
        if (recentActivities != null && !recentActivities.isEmpty()) {
            prompt.append("Recent activities:\n");
            for (TrainingData activity : recentActivities) {
                prompt.append("- ").append(activity.toString()).append("\n");
            }
            prompt.append("\n");
        }
        
        prompt.append("Suggest 3-4 workout types with brief explanations.\n");
        return prompt.toString();
    }
    
    /**
     * Format workout type for display
     */
    private static String formatWorkoutDisplayName(String workoutType) {
        switch (workoutType) {
            case "ai_intervals":
                return "AI Interval Training";
            case "ai_tempo_run":
                return "AI Tempo Run";
            case "ai_long_run":
                return "AI Long Run";
            case "ai_speed_work":
                return "AI Speed Work";
            case "ai_recovery_run":
                return "AI Recovery Run";
            case "ai_fartlek":
                return "AI Fartlek";
            case "ai_hill_repeats":
                return "AI Hill Repeats";
            case "ai_easy_shakeout":
                return "AI Easy Shakeout";
            case "ai_race_pace_practice":
                return "AI Race Pace Practice";
            default:
                // Convert underscores to spaces and capitalize
                String result = workoutType.replace("_", " ").replace("ai ", "AI ");
                // Capitalize first letter of each word
                StringBuilder capitalized = new StringBuilder();
                boolean capitalizeNext = true;
                for (char c : result.toCharArray()) {
                    if (Character.isWhitespace(c)) {
                        capitalizeNext = true;
                        capitalized.append(c);
                    } else if (capitalizeNext) {
                        capitalized.append(Character.toUpperCase(c));
                        capitalizeNext = false;
                    } else {
                        capitalized.append(c);
                    }
                }
                return capitalized.toString();
        }
    }
    
    /**
     * Format time in minutes to HH:MM format
     */
    private static String formatTime(int totalMinutes) {
        int hours = totalMinutes / 60;
        int minutes = totalMinutes % 60;
        if (hours > 0) {
            return String.format("%d:%02d", hours, minutes);
        } else {
            return String.format("%d:00", minutes);
        }
    }
    
    /**
     * Training data class for prompt context
     */
    public static class TrainingData {
        public String activity;
        public double distance;
        public double pace;
        public String date;
        
        public TrainingData(String activity, double distance, double pace, String date) {
            this.activity = activity;
            this.distance = distance;
            this.pace = pace;
            this.date = date;
        }
        
        @Override
        public String toString() {
            int paceMin = (int) (pace / 60);
            int paceSec = (int) (pace % 60);
            return String.format("%s: %.1fkm at %d:%02d pace (%s)", 
                activity, distance, paceMin, paceSec, date);
        }
    }
}