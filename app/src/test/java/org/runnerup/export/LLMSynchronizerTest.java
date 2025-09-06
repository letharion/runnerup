/*
 * Copyright (C) 2024
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 */

package org.runnerup.export;

import android.content.ContentValues;
import android.util.Pair;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

/**
 * Test class for LLMSynchronizer functionality
 */
@RunWith(MockitoJUnitRunner.class)
public class LLMSynchronizerTest {
    
    private LLMSynchronizer synchronizer;
    
    @Before
    public void setUp() {
        synchronizer = new LLMSynchronizer();
        
        // Configure with test values (no real API key needed for fallback testing)
        ContentValues config = new ContentValues();
        config.put("api_key", "");  // Empty to trigger fallback mode
        config.put("api_provider", "openai");
        config.put("use_local", false);
        synchronizer.init(config);
    }
    
    @Test
    public void testSynchronizerProperties() {
        assertEquals("LLM", synchronizer.getName());
        assertEquals(1000, synchronizer.getId());
        assertTrue(synchronizer.getIconId() > 0);
    }
    
    @Test
    public void testAuthConfig() {
        String authConfig = synchronizer.getAuthConfig();
        assertNotNull(authConfig);
        
        try {
            JSONObject config = new JSONObject(authConfig);
            assertTrue(config.has("api_key"));
            assertTrue(config.has("api_provider"));
            assertTrue(config.has("use_local"));
        } catch (Exception e) {
            fail("Auth config should be valid JSON");
        }
    }
    
    @Test
    public void testSupportedFeatures() {
        assertTrue(synchronizer.checkSupport(Synchronizer.Feature.WORKOUT_LIST));
        assertTrue(synchronizer.checkSupport(Synchronizer.Feature.GET_WORKOUT));
        assertFalse(synchronizer.checkSupport(Synchronizer.Feature.UPLOAD));
        assertFalse(synchronizer.checkSupport(Synchronizer.Feature.LIVE));
    }
    
    @Test
    public void testListWorkouts() {
        List<Pair<String, String>> workouts = new ArrayList<>();
        Synchronizer.Status status = synchronizer.listWorkouts(workouts);
        
        assertEquals(Synchronizer.Status.OK, status);
        assertFalse(workouts.isEmpty());
        
        // Should have some default workout suggestions
        boolean foundWorkout = false;
        for (Pair<String, String> workout : workouts) {
            assertNotNull(workout.first);  // workout ID
            assertNotNull(workout.second); // workout name
            if (workout.first.contains("ai_")) {
                foundWorkout = true;
            }
        }
        assertTrue("Should have AI-generated workout suggestions", foundWorkout);
    }
    
    @Test
    public void testFallbackWorkoutGeneration() throws Exception {
        // Create a temporary file for the workout
        File tempFile = File.createTempFile("test_workout", ".json");
        tempFile.deleteOnExit();
        
        try {
            // Test downloading a workout (should generate fallback since no API key)
            synchronizer.downloadWorkout(tempFile, "ai_easy_run");
            
            assertTrue("Workout file should be created", tempFile.exists());
            assertTrue("Workout file should have content", tempFile.length() > 0);
            
            // Read and validate the JSON
            String content = readFileContent(tempFile);
            JSONObject workout = new JSONObject(content);
            
            assertTrue(workout.has("com.garmin.connect.workout.json.UserWorkoutJson"));
            JSONObject userWorkout = workout.getJSONObject("com.garmin.connect.workout.json.UserWorkoutJson");
            
            assertTrue(userWorkout.has("workoutName"));
            assertTrue(userWorkout.has("sportTypeKey"));
            assertTrue(userWorkout.has("workoutSteps"));
            
            assertEquals("running", userWorkout.getString("sportTypeKey"));
            assertTrue(userWorkout.getJSONArray("workoutSteps").length() > 0);
            
        } finally {
            tempFile.delete();
        }
    }
    
    @Test
    public void testFallbackWorkoutStructure() throws Exception {
        File tempFile = File.createTempFile("test_workout", ".json");
        tempFile.deleteOnExit();
        
        try {
            synchronizer.downloadWorkout(tempFile, "ai_interval_training");
            
            String content = readFileContent(tempFile);
            JSONObject workout = new JSONObject(content);
            JSONObject userWorkout = workout.getJSONObject("com.garmin.connect.workout.json.UserWorkoutJson");
            
            // Verify it has the required Garmin Connect structure
            assertTrue("Should have workout steps", userWorkout.has("workoutSteps"));
            
            var steps = userWorkout.getJSONArray("workoutSteps");
            assertTrue("Should have at least 3 steps (warmup, main, cooldown)", steps.length() >= 3);
            
            // Check first step is warmup
            JSONObject firstStep = steps.getJSONObject(0);
            assertEquals("warmup", firstStep.getString("stepTypeKey"));
            assertEquals("time", firstStep.getString("endConditionTypeKey"));
            assertEquals("ms", firstStep.getString("endConditionUnitKey"));
            
        } finally {
            tempFile.delete();
        }
    }
    
    private String readFileContent(File file) throws IOException {
        StringBuilder content = new StringBuilder();
        try (FileReader reader = new FileReader(file)) {
            int ch;
            while ((ch = reader.read()) != -1) {
                content.append((char) ch);
            }
        }
        return content.toString();
    }
}