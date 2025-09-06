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
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.util.Log;

import org.runnerup.common.util.Constants;
import org.runnerup.common.util.Constants.DB;
import org.runnerup.db.DBHelper;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

/**
 * Analyzes user's training history to provide meaningful context for LLM workout generation
 */
public class TrainingHistoryAnalyzer {
    
    private static final String TAG = "TrainingHistoryAnalyzer";
    
    private final SQLiteDatabase database;
    private final Context context;
    
    public TrainingHistoryAnalyzer(Context context) {
        this.context = context;
        this.database = DBHelper.getReadableDatabase(context);
        // Initialize database optimizations for LLM queries
        DatabaseOptimizer.optimizeDatabase(context);
    }
    
    /**
     * Get comprehensive training analysis for the last specified number of weeks
     */
    public TrainingAnalysis getTrainingAnalysis(int weeksBack) {
        long startTime = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(weeksBack * 7);
        
        TrainingAnalysis analysis = new TrainingAnalysis();
        analysis.weeksAnalyzed = weeksBack;
        analysis.recentActivities = getRecentActivities(startTime, 20); // Last 20 activities
        analysis.weeklyStats = getWeeklyStatistics(startTime);
        analysis.performanceTrends = analyzePerformanceTrends(startTime);
        analysis.trainingLoad = calculateTrainingLoad(startTime);
        analysis.sportBreakdown = getSportBreakdown(startTime);
        
        return analysis;
    }
    
    /**
     * Get recent activities with detailed performance metrics (memory-optimized)
     */
    private List<ActivitySummary> getRecentActivities(long startTime, int maxCount) {
        List<ActivitySummary> activities = new ArrayList<>();
        
        // Use streaming iterator to prevent loading all activities into memory at once
        ActivityIterator iterator = getActivityIterator(startTime, maxCount);
        int count = 0;
        
        try {
            while (iterator.hasNext() && count < maxCount) {
                ActivitySummary activity = iterator.next();
                activities.add(activity);
                count++;
                
                // Memory pressure check - limit lap details for large datasets
                if (count > LLMConstants.ACTIVITY_LIMIT_FOR_LAP_DETAILS) {
                    activity.lapDetails.clear(); // Free memory for older activities
                }
            }
        } finally {
            iterator.close();
        }
        
        Log.d(TAG, "Found " + activities.size() + " recent activities (memory-optimized)");
        return activities;
    }
    
    /**
     * Create streaming iterator for activities to prevent memory overload
     */
    private ActivityIterator getActivityIterator(long startTime, int maxCount) {
        String[] columns = {
            DB.PRIMARY_KEY,
            DB.ACTIVITY.START_TIME,
            DB.ACTIVITY.DISTANCE,
            DB.ACTIVITY.TIME,
            DB.ACTIVITY.SPORT,
            DB.ACTIVITY.AVG_HR,
            DB.ACTIVITY.MAX_HR,
            DB.ACTIVITY.AVG_CADENCE,
            DB.ACTIVITY.NAME,
            DB.ACTIVITY.COMMENT
        };
        
        String selection = DB.ACTIVITY.START_TIME + " >= ? AND " + DB.ACTIVITY.DELETED + " = 0";
        String[] selectionArgs = {String.valueOf(startTime / 1000)};
        String orderBy = DB.ACTIVITY.START_TIME + " DESC";
        String limit = String.valueOf(Math.min(maxCount, LLMConstants.MAX_ACTIVITIES_PER_QUERY));
        
        Cursor cursor = database.query(
            DB.ACTIVITY.TABLE, columns, selection, selectionArgs, 
            null, null, orderBy, limit
        );
        
        return new ActivityIterator(cursor);
    }
    
    /**
     * Get lap-by-lap breakdown for detailed analysis (memory-optimized)
     */
    private List<LapDetail> getLapDetails(long activityId) {
        List<LapDetail> laps = new ArrayList<>();
        
        String[] columns = {
            DB.LAP.LAP,
            DB.LAP.TIME,
            DB.LAP.DISTANCE,
            DB.LAP.INTENSITY,
            DB.LAP.AVG_HR,
            DB.LAP.MAX_HR,
            DB.LAP.AVG_CADENCE
        };
        
        String selection = DB.LAP.ACTIVITY + " = ?";
        String[] selectionArgs = {String.valueOf(activityId)};
        String orderBy = DB.LAP.LAP + " ASC";
        
        // Limit lap details to prevent memory issues with very long activities
        String limit = String.valueOf(LLMConstants.MAX_LAPS_PER_ACTIVITY);
        
        Cursor cursor = database.query(
            DB.LAP.TABLE, columns, selection, selectionArgs,
            null, null, orderBy, limit
        );
        
        try {
            while (cursor.moveToNext()) {
                LapDetail lap = new LapDetail();
                lap.lapNumber = cursor.getInt(cursor.getColumnIndexOrThrow(DB.LAP.LAP));
                lap.time = cursor.getLong(cursor.getColumnIndexOrThrow(DB.LAP.TIME));
                lap.distance = cursor.getDouble(cursor.getColumnIndexOrThrow(DB.LAP.DISTANCE));
                lap.intensity = getIntensityName(cursor.getInt(cursor.getColumnIndexOrThrow(DB.LAP.INTENSITY)));
                lap.avgHeartRate = cursor.getInt(cursor.getColumnIndexOrThrow(DB.LAP.AVG_HR));
                lap.maxHeartRate = cursor.getInt(cursor.getColumnIndexOrThrow(DB.LAP.MAX_HR));
                lap.avgCadence = cursor.getDouble(cursor.getColumnIndexOrThrow(DB.LAP.AVG_CADENCE));
                
                // Calculate lap pace
                if (lap.distance > 0 && lap.time > 0) {
                    lap.pace = (lap.time / 1000.0) / (lap.distance / 1000.0);
                }
                
                laps.add(lap);
            }
        } finally {
            cursor.close();
        }
        
        return laps;
    }
    
    /**
     * Calculate weekly training statistics
     */
    private WeeklyStats getWeeklyStatistics(long startTime) {
        WeeklyStats stats = new WeeklyStats();
        
        // Total distance and time
        String query = "SELECT " +
            "COUNT(*) as activity_count, " +
            "SUM(" + DB.ACTIVITY.DISTANCE + ") as total_distance, " +
            "SUM(" + DB.ACTIVITY.TIME + ") as total_time, " +
            "AVG(" + DB.ACTIVITY.AVG_HR + ") as avg_hr, " +
            "MAX(" + DB.ACTIVITY.MAX_HR + ") as max_hr " +
            "FROM " + DB.ACTIVITY.TABLE + " " +
            "WHERE " + DB.ACTIVITY.START_TIME + " >= ? AND " + DB.ACTIVITY.DELETED + " = 0";
        
        String[] args = {String.valueOf(startTime / 1000)};
        
        Cursor cursor = database.rawQuery(query, args);
        try {
            if (cursor.moveToFirst()) {
                stats.activityCount = cursor.getInt(cursor.getColumnIndexOrThrow("activity_count"));
                stats.totalDistance = cursor.getDouble(cursor.getColumnIndexOrThrow("total_distance"));
                stats.totalTime = cursor.getLong(cursor.getColumnIndexOrThrow("total_time"));
                stats.avgHeartRate = cursor.getDouble(cursor.getColumnIndexOrThrow("avg_hr"));
                stats.maxHeartRate = cursor.getInt(cursor.getColumnIndexOrThrow("max_hr"));
            }
        } finally {
            cursor.close();
        }
        
        return stats;
    }
    
    /**
     * Analyze performance trends over time
     */
    private PerformanceTrends analyzePerformanceTrends(long startTime) {
        PerformanceTrends trends = new PerformanceTrends();
        
        // Get activities sorted by date to analyze trends
        String query = "SELECT " +
            DB.ACTIVITY.START_TIME + ", " +
            DB.ACTIVITY.DISTANCE + ", " +
            DB.ACTIVITY.TIME + ", " +
            DB.ACTIVITY.AVG_HR + " " +
            "FROM " + DB.ACTIVITY.TABLE + " " +
            "WHERE " + DB.ACTIVITY.START_TIME + " >= ? AND " +
            DB.ACTIVITY.DELETED + " = 0 AND " +
            DB.ACTIVITY.SPORT + " = " + DB.ACTIVITY.SPORT_RUNNING + " " +
            "ORDER BY " + DB.ACTIVITY.START_TIME + " ASC";
        
        String[] args = {String.valueOf(startTime / 1000)};
        
        Cursor cursor = database.rawQuery(query, args);
        List<Double> paces = new ArrayList<>();
        List<Long> dates = new ArrayList<>();
        
        try {
            while (cursor.moveToNext()) {
                long time = cursor.getLong(0);
                double distance = cursor.getDouble(1);
                long duration = cursor.getLong(2);
                
                if (distance > 0 && duration > 0) {
                    double pace = (duration / 1000.0) / (distance / 1000.0); // seconds per km
                    paces.add(pace);
                    dates.add(time * 1000L);
                }
            }
        } finally {
            cursor.close();
        }
        
        // Calculate trend
        if (paces.size() >= 3) {
            // Simple linear trend analysis
            double firstThird = paces.subList(0, paces.size() / 3).stream().mapToDouble(Double::doubleValue).average().orElse(0);
            double lastThird = paces.subList(2 * paces.size() / 3, paces.size()).stream().mapToDouble(Double::doubleValue).average().orElse(0);
            
            if (lastThird < firstThird) {
                trends.paceImprovement = "Improving"; // Faster times = lower pace numbers
                trends.paceChangePercent = ((firstThird - lastThird) / firstThird) * 100;
            } else if (lastThird > firstThird) {
                trends.paceImprovement = "Declining";
                trends.paceChangePercent = ((lastThird - firstThird) / firstThird) * 100;
            } else {
                trends.paceImprovement = "Stable";
                trends.paceChangePercent = 0;
            }
        }
        
        return trends;
    }
    
    /**
     * Calculate training load metrics
     */
    private TrainingLoad calculateTrainingLoad(long startTime) {
        TrainingLoad load = new TrainingLoad();
        
        // Calculate weekly mileage distribution
        long oneWeek = TimeUnit.DAYS.toMillis(7);
        long currentTime = System.currentTimeMillis();
        
        for (int week = 0; week < 4; week++) {
            long weekStart = currentTime - (week + 1) * oneWeek;
            long weekEnd = currentTime - week * oneWeek;
            
            if (weekStart < startTime) break;
            
            double weeklyDistance = getWeeklyDistance(weekStart, weekEnd);
            load.weeklyDistances.add(weeklyDistance);
        }
        
        // Calculate average and consistency
        if (!load.weeklyDistances.isEmpty()) {
            load.avgWeeklyDistance = load.weeklyDistances.stream().mapToDouble(Double::doubleValue).average().orElse(0);
            
            // Calculate coefficient of variation as consistency measure
            double variance = load.weeklyDistances.stream()
                .mapToDouble(d -> Math.pow(d - load.avgWeeklyDistance, 2))
                .average().orElse(0);
            double stdDev = Math.sqrt(variance);
            load.consistencyScore = load.avgWeeklyDistance > 0 ? 1 - (stdDev / load.avgWeeklyDistance) : 0;
        }
        
        return load;
    }
    
    private double getWeeklyDistance(long weekStart, long weekEnd) {
        String query = "SELECT SUM(" + DB.ACTIVITY.DISTANCE + ") FROM " + DB.ACTIVITY.TABLE + 
            " WHERE " + DB.ACTIVITY.START_TIME + " >= ? AND " +
            DB.ACTIVITY.START_TIME + " < ? AND " + DB.ACTIVITY.DELETED + " = 0";
        
        String[] args = {String.valueOf(weekStart / 1000), String.valueOf(weekEnd / 1000)};
        
        Cursor cursor = database.rawQuery(query, args);
        try {
            if (cursor.moveToFirst()) {
                return cursor.getDouble(0);
            }
        } finally {
            cursor.close();
        }
        return 0;
    }
    
    /**
     * Get breakdown of activities by sport type
     */
    private SportBreakdown getSportBreakdown(long startTime) {
        SportBreakdown breakdown = new SportBreakdown();
        
        String query = "SELECT " + DB.ACTIVITY.SPORT + ", COUNT(*), SUM(" + DB.ACTIVITY.DISTANCE + ") " +
            "FROM " + DB.ACTIVITY.TABLE + " " +
            "WHERE " + DB.ACTIVITY.START_TIME + " >= ? AND " + DB.ACTIVITY.DELETED + " = 0 " +
            "GROUP BY " + DB.ACTIVITY.SPORT;
        
        String[] args = {String.valueOf(startTime / 1000)};
        
        Cursor cursor = database.rawQuery(query, args);
        try {
            while (cursor.moveToNext()) {
                int sport = cursor.getInt(0);
                int count = cursor.getInt(1);
                double distance = cursor.getDouble(2);
                
                String sportName = getSportName(sport);
                breakdown.sportCounts.put(sportName, count);
                breakdown.sportDistances.put(sportName, distance);
            }
        } finally {
            cursor.close();
        }
        
        return breakdown;
    }
    
    private String getSportName(int sportType) {
        switch (sportType) {
            case DB.ACTIVITY.SPORT_RUNNING: return "Running";
            case DB.ACTIVITY.SPORT_BIKING: return "Cycling";
            case DB.ACTIVITY.SPORT_WALKING: return "Walking";
            case DB.ACTIVITY.SPORT_TREADMILL: return "Treadmill";
            case DB.ACTIVITY.SPORT_ORIENTEERING: return "Orienteering";
            case DB.ACTIVITY.SPORT_OTHER:
            default: return "Other";
        }
    }
    
    private String getIntensityName(int intensity) {
        switch (intensity) {
            case DB.INTENSITY.ACTIVE: return "Active";
            case DB.INTENSITY.RESTING: return "Rest";
            case DB.INTENSITY.WARMUP: return "Warmup";
            case DB.INTENSITY.COOLDOWN: return "Cooldown";
            case DB.INTENSITY.REPEAT: return "Interval";
            case DB.INTENSITY.RECOVERY: return "Recovery";
            default: return "Unknown";
        }
    }
    
    // Data classes for structured training analysis
    
    public static class TrainingAnalysis {
        public int weeksAnalyzed;
        public List<ActivitySummary> recentActivities = new ArrayList<>();
        public WeeklyStats weeklyStats = new WeeklyStats();
        public PerformanceTrends performanceTrends = new PerformanceTrends();
        public TrainingLoad trainingLoad = new TrainingLoad();
        public SportBreakdown sportBreakdown = new SportBreakdown();
        
        /**
         * Generate a formatted summary for LLM consumption
         */
        public String generateLLMSummary() {
            StringBuilder summary = new StringBuilder();
            SimpleDateFormat dateFormat = new SimpleDateFormat("MMM dd", Locale.getDefault());
            
            summary.append("=== TRAINING ANALYSIS (Last ").append(weeksAnalyzed).append(" weeks) ===\n\n");
            
            // Overall statistics
            summary.append("OVERALL STATS:\n");
            summary.append("- Total activities: ").append(weeklyStats.activityCount).append("\n");
            summary.append("- Total distance: ").append(String.format("%.1f km", weeklyStats.totalDistance / 1000.0)).append("\n");
            summary.append("- Total time: ").append(formatTime(weeklyStats.totalTime)).append("\n");
            if (weeklyStats.avgHeartRate > 0) {
                summary.append("- Average heart rate: ").append(Math.round(weeklyStats.avgHeartRate)).append(" bpm\n");
            }
            
            // Training load
            summary.append("\nTRAINING LOAD:\n");
            summary.append("- Average weekly distance: ").append(String.format("%.1f km", trainingLoad.avgWeeklyDistance / 1000.0)).append("\n");
            summary.append("- Training consistency: ").append(String.format("%.0f%%", trainingLoad.consistencyScore * 100)).append("\n");
            
            // Performance trends
            if (performanceTrends.paceImprovement != null) {
                summary.append("\nPERFORMACE TRENDS:\n");
                summary.append("- Pace trend: ").append(performanceTrends.paceImprovement).append("\n");
                if (performanceTrends.paceChangePercent > 0) {
                    summary.append("- Pace change: ").append(String.format("%.1f%%", performanceTrends.paceChangePercent)).append("\n");
                }
            }
            
            // Recent activities (last 5)
            summary.append("\nRECENT ACTIVITIES:\n");
            int count = Math.min(5, recentActivities.size());
            for (int i = 0; i < count; i++) {
                ActivitySummary activity = recentActivities.get(i);
                Date date = new Date(activity.startTime);
                
                summary.append("- ").append(dateFormat.format(date))
                    .append(": ").append(activity.sport)
                    .append(", ").append(String.format("%.1f km", activity.distance / 1000.0))
                    .append(", ").append(formatTime(activity.duration));
                
                if (activity.avgPace > 0) {
                    summary.append(", ").append(formatPace(activity.avgPace)).append(" min/km");
                }
                
                if (activity.avgHeartRate > 0) {
                    summary.append(", ").append(activity.avgHeartRate).append(" bpm avg");
                }
                
                summary.append("\n");
                
                // Add lap details for interval workouts
                if (activity.lapDetails.size() > 3) {
                    summary.append("  Workout structure: ");
                    for (int j = 0; j < Math.min(3, activity.lapDetails.size()); j++) {
                        LapDetail lap = activity.lapDetails.get(j);
                        summary.append(lap.intensity).append(" ");
                    }
                    if (activity.lapDetails.size() > 3) {
                        summary.append("...");
                    }
                    summary.append("\n");
                }
            }
            
            return summary.toString();
        }
        
        private String formatTime(long timeMs) {
            long minutes = timeMs / 60000;
            long hours = minutes / 60;
            minutes = minutes % 60;
            
            if (hours > 0) {
                return String.format("%d:%02d h", hours, minutes);
            } else {
                return String.format("%d min", minutes);
            }
        }
        
        private String formatPace(double paceSecondsPerKm) {
            int minutes = (int) (paceSecondsPerKm / 60);
            int seconds = (int) (paceSecondsPerKm % 60);
            return String.format("%d:%02d", minutes, seconds);
        }
    }
    
    public static class ActivitySummary {
        public long id;
        public long startTime;
        public double distance; // meters
        public long duration; // milliseconds
        public String sport;
        public int avgHeartRate;
        public int maxHeartRate;
        public double avgCadence;
        public double avgPace; // seconds per km
        public String name;
        public String comment;
        public List<LapDetail> lapDetails = new ArrayList<>();
    }
    
    public static class LapDetail {
        public int lapNumber;
        public long time; // milliseconds
        public double distance; // meters
        public String intensity;
        public int avgHeartRate;
        public int maxHeartRate;
        public double avgCadence;
        public double pace; // seconds per km
    }
    
    public static class WeeklyStats {
        public int activityCount;
        public double totalDistance; // meters
        public long totalTime; // milliseconds
        public double avgHeartRate;
        public int maxHeartRate;
    }
    
    public static class PerformanceTrends {
        public String paceImprovement; // "Improving", "Declining", "Stable"
        public double paceChangePercent;
    }
    
    public static class TrainingLoad {
        public List<Double> weeklyDistances = new ArrayList<>(); // meters
        public double avgWeeklyDistance; // meters
        public double consistencyScore; // 0-1, higher = more consistent
    }
    
    public static class SportBreakdown {
        public java.util.HashMap<String, Integer> sportCounts = new java.util.HashMap<>();
        public java.util.HashMap<String, Double> sportDistances = new java.util.HashMap<>();
    }
    
    /**
     * Streaming iterator for activities to prevent loading all data into memory
     */
    private class ActivityIterator implements Iterator<ActivitySummary> {
        private final Cursor cursor;
        private boolean hasNextCalled = false;
        private boolean hasNextResult = false;
        
        public ActivityIterator(Cursor cursor) {
            this.cursor = cursor;
        }
        
        @Override
        public boolean hasNext() {
            if (!hasNextCalled) {
                hasNextResult = cursor.moveToNext();
                hasNextCalled = true;
            }
            return hasNextResult;
        }
        
        @Override
        public ActivitySummary next() {
            if (!hasNext()) {
                throw new IllegalStateException("No more activities available");
            }
            
            hasNextCalled = false;
            
            ActivitySummary activity = new ActivitySummary();
            
            long activityId = cursor.getLong(cursor.getColumnIndexOrThrow(DB.PRIMARY_KEY));
            activity.id = activityId;
            activity.startTime = cursor.getLong(cursor.getColumnIndexOrThrow(DB.ACTIVITY.START_TIME)) * 1000L;
            activity.distance = cursor.getDouble(cursor.getColumnIndexOrThrow(DB.ACTIVITY.DISTANCE));
            activity.duration = cursor.getLong(cursor.getColumnIndexOrThrow(DB.ACTIVITY.TIME));
            activity.sport = getSportName(cursor.getInt(cursor.getColumnIndexOrThrow(DB.ACTIVITY.SPORT)));
            activity.avgHeartRate = cursor.getInt(cursor.getColumnIndexOrThrow(DB.ACTIVITY.AVG_HR));
            activity.maxHeartRate = cursor.getInt(cursor.getColumnIndexOrThrow(DB.ACTIVITY.MAX_HR));
            activity.avgCadence = cursor.getDouble(cursor.getColumnIndexOrThrow(DB.ACTIVITY.AVG_CADENCE));
            activity.name = cursor.getString(cursor.getColumnIndexOrThrow(DB.ACTIVITY.NAME));
            activity.comment = cursor.getString(cursor.getColumnIndexOrThrow(DB.ACTIVITY.COMMENT));
            
            // Calculate pace (seconds per km)
            if (activity.distance > 0 && activity.duration > 0) {
                activity.avgPace = (activity.duration / 1000.0) / (activity.distance / 1000.0);
            }
            
            // Get lap details for this activity (with memory limits)
            activity.lapDetails = getLapDetails(activityId);
            
            return activity;
        }
        
        public void close() {
            if (cursor != null && !cursor.isClosed()) {
                cursor.close();
            }
        }
    }
}