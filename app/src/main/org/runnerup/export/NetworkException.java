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
 * Exception thrown when network operations fail
 */
public class NetworkException extends Exception {
    
    private final int responseCode;
    private final String endpoint;
    
    public NetworkException(String message) {
        super(message);
        this.responseCode = -1;
        this.endpoint = null;
    }
    
    public NetworkException(String message, int responseCode) {
        super(message);
        this.responseCode = responseCode;
        this.endpoint = null;
    }
    
    public NetworkException(String message, String endpoint) {
        super(message);
        this.responseCode = -1;
        this.endpoint = endpoint;
    }
    
    public NetworkException(String message, int responseCode, String endpoint) {
        super(message);
        this.responseCode = responseCode;
        this.endpoint = endpoint;
    }
    
    public NetworkException(String message, Throwable cause) {
        super(message, cause);
        this.responseCode = -1;
        this.endpoint = null;
    }
    
    public NetworkException(String message, int responseCode, String endpoint, Throwable cause) {
        super(message, cause);
        this.responseCode = responseCode;
        this.endpoint = endpoint;
    }
    
    public int getResponseCode() {
        return responseCode;
    }
    
    public String getEndpoint() {
        return endpoint;
    }
    
    public boolean isRetryable() {
        return responseCode >= 500 || responseCode == 429; // Server errors and rate limiting
    }
}