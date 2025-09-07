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
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteStatement;
import android.util.Log;

import org.runnerup.common.util.Constants.DB;
import org.runnerup.db.DBHelper;

/**
 * Database optimization for LLM-related queries
 * Creates indexes and prepared statements for better performance
 */
public class DatabaseOptimizer {
    
    private static final String TAG = "DatabaseOptimizer";
    
    // Database connection pooling with proper synchronization
    private static final Object DB_LOCK = new Object();
    
    // Prepared statement cache for common queries
    private static SQLiteStatement sRecentActivitiesStmt;
    private static SQLiteStatement sLapDetailsStmt;
    private static SQLiteStatement sWeeklyStatsStmt;
    private static SQLiteStatement sPerformanceTrendsStmt;
    private static SQLiteStatement sWeeklyDistanceStmt;
    
    /**
     * Initialize database optimizations for LLM queries
     */
    public static void optimizeDatabase(Context context) {
        SQLiteDatabase db = DBHelper.getWritableDatabase(context);
        
        try {
            // Create indices for frequently accessed columns in LLM queries
            createLLMIndices(db);
            
            // Prepare common statements
            prepareStatements(db);
            
            Log.d(TAG, "Database optimization completed successfully");
            
        } catch (Exception e) {
            Log.e(TAG, "Failed to optimize database for LLM queries", e);
        }
    }
    
    /**
     * Create database indices for LLM-optimized queries
     */
    private static void createLLMIndices(SQLiteDatabase db) {
        // Index for activity queries by start time (most common LLM query pattern)
        String activityStartTimeIndex = "CREATE INDEX IF NOT EXISTS idx_activity_start_time_llm " +
            "ON " + DB.ACTIVITY.TABLE + " (" + DB.ACTIVITY.START_TIME + " DESC, " + 
            DB.ACTIVITY.DELETED + ")";
        db.execSQL(activityStartTimeIndex);
        
        // Index for activity sport filtering (used in performance trends)
        String activitySportIndex = "CREATE INDEX IF NOT EXISTS idx_activity_sport_llm " +
            "ON " + DB.ACTIVITY.TABLE + " (" + DB.ACTIVITY.SPORT + ", " + 
            DB.ACTIVITY.START_TIME + " DESC)";
        db.execSQL(activitySportIndex);
        
        // Composite index for activity analysis queries
        String activityAnalysisIndex = "CREATE INDEX IF NOT EXISTS idx_activity_analysis_llm " +
            "ON " + DB.ACTIVITY.TABLE + " (" + DB.ACTIVITY.START_TIME + " DESC, " +
            DB.ACTIVITY.SPORT + ", " + DB.ACTIVITY.DISTANCE + ", " + DB.ACTIVITY.TIME + ")";
        db.execSQL(activityAnalysisIndex);
        
        // Index for lap queries by activity
        String lapActivityIndex = "CREATE INDEX IF NOT EXISTS idx_lap_activity_llm " +
            "ON " + DB.LAP.TABLE + " (" + DB.LAP.ACTIVITY + ", " + DB.LAP.LAP + " ASC)";
        db.execSQL(lapActivityIndex);
        
        Log.d(TAG, "Created LLM-optimized database indices");
    }
    
    /**
     * Prepare commonly used SQL statements for better performance
     */
    private static void prepareStatements(SQLiteDatabase db) {
        try {
            // Recent activities query (most frequently used)
            String recentActivitiesSQL = "SELECT " +
                DB.PRIMARY_KEY + ", " +
                DB.ACTIVITY.START_TIME + ", " +
                DB.ACTIVITY.DISTANCE + ", " +
                DB.ACTIVITY.TIME + ", " +
                DB.ACTIVITY.SPORT + ", " +
                DB.ACTIVITY.AVG_HR + ", " +
                DB.ACTIVITY.MAX_HR + ", " +
                DB.ACTIVITY.AVG_CADENCE + ", " +
                DB.ACTIVITY.NAME + ", " +
                DB.ACTIVITY.COMMENT + " " +
                "FROM " + DB.ACTIVITY.TABLE + " " +
                "WHERE " + DB.ACTIVITY.START_TIME + " >= ? AND " + DB.ACTIVITY.DELETED + " = 0 " +
                "ORDER BY " + DB.ACTIVITY.START_TIME + " DESC LIMIT ?";
            sRecentActivitiesStmt = db.compileStatement(recentActivitiesSQL);
            
            // Lap details query  
            String lapDetailsSQL = "SELECT " +
                DB.LAP.LAP + ", " +
                DB.LAP.TIME + ", " +
                DB.LAP.DISTANCE + ", " +
                DB.LAP.INTENSITY + ", " +
                DB.LAP.AVG_HR + ", " +
                DB.LAP.MAX_HR + ", " +
                DB.LAP.AVG_CADENCE + " " +
                "FROM " + DB.LAP.TABLE + " " +
                "WHERE " + DB.LAP.ACTIVITY + " = ? " +
                "ORDER BY " + DB.LAP.LAP + " ASC LIMIT ?";
            sLapDetailsStmt = db.compileStatement(lapDetailsSQL);
            
            // Weekly statistics query
            String weeklyStatsSQL = "SELECT " +
                "COUNT(*) as activity_count, " +
                "SUM(" + DB.ACTIVITY.DISTANCE + ") as total_distance, " +
                "SUM(" + DB.ACTIVITY.TIME + ") as total_time, " +
                "AVG(" + DB.ACTIVITY.AVG_HR + ") as avg_hr, " +
                "MAX(" + DB.ACTIVITY.MAX_HR + ") as max_hr " +
                "FROM " + DB.ACTIVITY.TABLE + " " +
                "WHERE " + DB.ACTIVITY.START_TIME + " >= ? AND " + DB.ACTIVITY.DELETED + " = 0";
            sWeeklyStatsStmt = db.compileStatement(weeklyStatsSQL);
            
            // Performance trends query (running activities only)
            String performanceTrendsSQL = "SELECT " +
                DB.ACTIVITY.START_TIME + ", " +
                DB.ACTIVITY.DISTANCE + ", " +
                DB.ACTIVITY.TIME + ", " +
                DB.ACTIVITY.AVG_HR + " " +
                "FROM " + DB.ACTIVITY.TABLE + " " +
                "WHERE " + DB.ACTIVITY.START_TIME + " >= ? AND " +
                DB.ACTIVITY.DELETED + " = 0 AND " +
                DB.ACTIVITY.SPORT + " = " + DB.ACTIVITY.SPORT_RUNNING + " " +
                "ORDER BY " + DB.ACTIVITY.START_TIME + " ASC";
            sPerformanceTrendsStmt = db.compileStatement(performanceTrendsSQL);
            
            // Weekly distance query
            String weeklyDistanceSQL = "SELECT SUM(" + DB.ACTIVITY.DISTANCE + ") " +
                "FROM " + DB.ACTIVITY.TABLE + " " + 
                "WHERE " + DB.ACTIVITY.START_TIME + " >= ? AND " +
                DB.ACTIVITY.START_TIME + " < ? AND " + DB.ACTIVITY.DELETED + " = 0";
            sWeeklyDistanceStmt = db.compileStatement(weeklyDistanceSQL);
            
            Log.d(TAG, "Prepared SQL statements for LLM queries");
            
        } catch (Exception e) {
            Log.e(TAG, "Failed to prepare SQL statements", e);
        }
    }
    
    /**
     * Get prepared statement for recent activities query
     */
    public static SQLiteStatement getRecentActivitiesStatement(Context context) {
        synchronized (DB_LOCK) {
            if (sRecentActivitiesStmt == null) {
                optimizeDatabase(context);
            }
            return sRecentActivitiesStmt;
        }
    }
    
    /**
     * Get prepared statement for lap details query
     */
    public static SQLiteStatement getLapDetailsStatement(Context context) {
        synchronized (DB_LOCK) {
            if (sLapDetailsStmt == null) {
                optimizeDatabase(context);
            }
            return sLapDetailsStmt;
        }
    }
    
    /**
     * Get prepared statement for weekly stats query
     */
    public static SQLiteStatement getWeeklyStatsStatement(Context context) {
        synchronized (DB_LOCK) {
            if (sWeeklyStatsStmt == null) {
                optimizeDatabase(context);
            }
            return sWeeklyStatsStmt;
        }
    }
    
    /**
     * Get prepared statement for performance trends query
     */
    public static SQLiteStatement getPerformanceTrendsStatement(Context context) {
        synchronized (DB_LOCK) {
            if (sPerformanceTrendsStmt == null) {
                optimizeDatabase(context);
            }
            return sPerformanceTrendsStmt;
        }
    }
    
    /**
     * Get prepared statement for weekly distance query
     */
    public static SQLiteStatement getWeeklyDistanceStatement(Context context) {
        synchronized (DB_LOCK) {
            if (sWeeklyDistanceStmt == null) {
                optimizeDatabase(context);
            }
            return sWeeklyDistanceStmt;
        }
    }
    
    /**
     * Analyze query performance (for debugging)
     */
    public static void analyzeQueryPerformance(Context context, String query) {
        SQLiteDatabase db = DBHelper.getReadableDatabase(context);
        
        try {
            // Use EXPLAIN QUERY PLAN to analyze performance
            String explainQuery = "EXPLAIN QUERY PLAN " + query;
            android.database.Cursor cursor = db.rawQuery(explainQuery, null);
            
            Log.d(TAG, "Query plan for: " + query);
            
            while (cursor.moveToNext()) {
                StringBuilder plan = new StringBuilder();
                for (int i = 0; i < cursor.getColumnCount(); i++) {
                    plan.append(cursor.getColumnName(i)).append("=")
                        .append(cursor.getString(i)).append(" ");
                }
                Log.d(TAG, "  " + plan.toString());
            }
            
            cursor.close();
            
        } catch (Exception e) {
            Log.w(TAG, "Failed to analyze query performance: " + query, e);
        }
    }
    
    /**
     * Cleanup prepared statements
     */
    public static void cleanup() {
        try {
            if (sRecentActivitiesStmt != null) {
                sRecentActivitiesStmt.close();
                sRecentActivitiesStmt = null;
            }
            if (sLapDetailsStmt != null) {
                sLapDetailsStmt.close();
                sLapDetailsStmt = null;
            }
            if (sWeeklyStatsStmt != null) {
                sWeeklyStatsStmt.close();
                sWeeklyStatsStmt = null;
            }
            if (sPerformanceTrendsStmt != null) {
                sPerformanceTrendsStmt.close();
                sPerformanceTrendsStmt = null;
            }
            if (sWeeklyDistanceStmt != null) {
                sWeeklyDistanceStmt.close();
                sWeeklyDistanceStmt = null;
            }
            
            Log.d(TAG, "Cleaned up prepared statements");
            
        } catch (Exception e) {
            Log.w(TAG, "Error during cleanup", e);
        }
    }
}