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
 * Exception thrown when workout validation fails
 */
public class WorkoutValidationException extends Exception {
    
    private final String validationError;
    private final String workoutType;
    
    public WorkoutValidationException(String message) {
        super(message);
        this.validationError = message;
        this.workoutType = null;
    }
    
    public WorkoutValidationException(String message, String workoutType) {
        super(message);
        this.validationError = message;
        this.workoutType = workoutType;
    }
    
    public WorkoutValidationException(String message, Throwable cause) {
        super(message, cause);
        this.validationError = message;
        this.workoutType = null;
    }
    
    public WorkoutValidationException(String message, String workoutType, Throwable cause) {
        super(message, cause);
        this.validationError = message;
        this.workoutType = workoutType;
    }
    
    public String getValidationError() {
        return validationError;
    }
    
    public String getWorkoutType() {
        return workoutType;
    }
}