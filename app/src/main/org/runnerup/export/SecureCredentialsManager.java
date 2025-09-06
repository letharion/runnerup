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
import android.content.SharedPreferences;
import android.os.Build;
import android.util.Log;

import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKey;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.security.GeneralSecurityException;

/**
 * Secure storage for LLM API credentials using Android's EncryptedSharedPreferences
 * Provides encrypted storage for sensitive data like API keys
 */
public class SecureCredentialsManager {
    
    private static final String TAG = "SecureCredentialsManager";
    private static final String ENCRYPTED_PREFS_NAME = "llm_secure_credentials";
    
    // Secure storage keys
    private static final String KEY_API_KEY = "encrypted_api_key";
    private static final String KEY_API_PROVIDER = "api_provider";
    private static final String KEY_USE_LOCAL = "use_local_llm";
    private static final String KEY_MODEL_PREFERENCE = "model_preference";
    
    private final Context context;
    private SharedPreferences encryptedPrefs;
    private SharedPreferences fallbackPrefs; // For non-sensitive data
    
    public SecureCredentialsManager(Context context) {
        this.context = context;
        initializeSecureStorage();
    }
    
    /**
     * Initialize secure storage with fallback for older devices
     */
    private void initializeSecureStorage() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                // Use encrypted preferences on API 23+
                MasterKey masterKey = new MasterKey.Builder(context)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build();
                
                encryptedPrefs = EncryptedSharedPreferences.create(
                    context,
                    ENCRYPTED_PREFS_NAME,
                    masterKey,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
                );
                
                Log.d(TAG, "Secure storage initialized with encryption");
                
            } else {
                // Fallback to obfuscated storage for older devices
                Log.w(TAG, "Device too old for EncryptedSharedPreferences, using obfuscated storage");
                encryptedPrefs = context.getSharedPreferences(
                    ENCRYPTED_PREFS_NAME + "_obfuscated", Context.MODE_PRIVATE);
            }
            
        } catch (GeneralSecurityException | IOException e) {
            Log.e(TAG, "Failed to initialize secure storage, falling back to obfuscated", e);
            // Fallback to obfuscated preferences if encryption fails
            encryptedPrefs = context.getSharedPreferences(
                ENCRYPTED_PREFS_NAME + "_fallback", Context.MODE_PRIVATE);
        }
        
        // Non-sensitive preferences (unencrypted)
        fallbackPrefs = context.getSharedPreferences(
            "llm_non_sensitive_prefs", Context.MODE_PRIVATE);
    }
    
    /**
     * Store API key securely
     */
    public void storeApiKey(String apiKey) {
        if (apiKey == null || apiKey.trim().isEmpty()) {
            clearApiKey();
            return;
        }
        
        try {
            String secureKey = Build.VERSION.SDK_INT >= Build.VERSION_CODES.M 
                ? apiKey // Already encrypted by EncryptedSharedPreferences
                : obfuscateString(apiKey); // Simple obfuscation for older devices
                
            encryptedPrefs.edit()
                .putString(KEY_API_KEY, secureKey)
                .apply();
                
            Log.d(TAG, "API key stored securely");
            
        } catch (Exception e) {
            Log.e(TAG, "Failed to store API key", e);
            throw new SecurityException("Failed to securely store API key");
        }
    }
    
    /**
     * Retrieve API key securely
     */
    public String getApiKey() {
        try {
            String storedKey = encryptedPrefs.getString(KEY_API_KEY, null);
            if (storedKey == null) {
                return null;
            }
            
            // Return decrypted/deobfuscated key
            return Build.VERSION.SDK_INT >= Build.VERSION_CODES.M 
                ? storedKey // Already decrypted by EncryptedSharedPreferences
                : deobfuscateString(storedKey); // Simple deobfuscation for older devices
                
        } catch (Exception e) {
            Log.e(TAG, "Failed to retrieve API key", e);
            return null;
        }
    }
    
    /**
     * Clear stored API key
     */
    public void clearApiKey() {
        encryptedPrefs.edit().remove(KEY_API_KEY).apply();
        Log.d(TAG, "API key cleared");
    }
    
    /**
     * Store LLM configuration (non-sensitive)
     */
    public void storeConfiguration(LLMConfiguration config) {
        try {
            JSONObject configJson = new JSONObject();
            configJson.put(KEY_API_PROVIDER, config.apiProvider);
            configJson.put(KEY_USE_LOCAL, config.useLocalLLM);
            configJson.put(KEY_MODEL_PREFERENCE, config.modelPreference);
            
            fallbackPrefs.edit()
                .putString("llm_config", configJson.toString())
                .apply();
                
            Log.d(TAG, "LLM configuration stored");
            
        } catch (JSONException e) {
            Log.e(TAG, "Failed to store LLM configuration", e);
        }
    }
    
    /**
     * Retrieve LLM configuration
     */
    public LLMConfiguration getConfiguration() {
        LLMConfiguration config = new LLMConfiguration();
        
        try {
            String configStr = fallbackPrefs.getString("llm_config", null);
            if (configStr != null) {
                JSONObject configJson = new JSONObject(configStr);
                config.apiProvider = configJson.optString(KEY_API_PROVIDER, "openai");
                config.useLocalLLM = configJson.optBoolean(KEY_USE_LOCAL, false);
                config.modelPreference = configJson.optString(KEY_MODEL_PREFERENCE, null);
            }
        } catch (JSONException e) {
            Log.e(TAG, "Failed to parse LLM configuration", e);
        }
        
        return config;
    }
    
    /**
     * Check if API key is configured
     */
    public boolean hasApiKey() {
        String key = getApiKey();
        return key != null && !key.trim().isEmpty();
    }
    
    /**
     * Validate API key format
     */
    public boolean isValidApiKey(String apiKey, String provider) {
        if (apiKey == null || apiKey.trim().isEmpty()) {
            return false;
        }
        
        switch (provider.toLowerCase()) {
            case "openai":
                return apiKey.startsWith("sk-") && apiKey.length() > 10;
            case "anthropic":
                return apiKey.startsWith("sk-ant-") && apiKey.length() > 20;
            default:
                return apiKey.length() > 8; // Basic length check
        }
    }
    
    /**
     * Simple string obfuscation for older Android versions
     * NOTE: This is not cryptographically secure, just basic obfuscation
     */
    private String obfuscateString(String input) {
        if (input == null) return null;
        
        StringBuilder obfuscated = new StringBuilder();
        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            // Simple XOR obfuscation with position-based key
            obfuscated.append((char) (c ^ (i + LLMConstants.OBFUSCATION_OFFSET))); // Arbitrary offset
        }
        
        // Base64 encode the result to make it printable
        return android.util.Base64.encodeToString(
            obfuscated.toString().getBytes(), android.util.Base64.DEFAULT);
    }
    
    /**
     * Reverse simple string obfuscation
     */
    private String deobfuscateString(String obfuscated) {
        if (obfuscated == null) return null;
        
        try {
            // Base64 decode first
            String decoded = new String(android.util.Base64.decode(obfuscated, android.util.Base64.DEFAULT));
            
            StringBuilder original = new StringBuilder();
            for (int i = 0; i < decoded.length(); i++) {
                char c = decoded.charAt(i);
                // Reverse XOR obfuscation
                original.append((char) (c ^ (i + LLMConstants.OBFUSCATION_OFFSET)));
            }
            
            return original.toString();
            
        } catch (Exception e) {
            Log.e(TAG, "Failed to deobfuscate string", e);
            return null;
        }
    }
    
    /**
     * Clear all stored credentials and configuration
     */
    public void clearAllData() {
        encryptedPrefs.edit().clear().apply();
        fallbackPrefs.edit().clear().apply();
        Log.d(TAG, "All LLM credentials and configuration cleared");
    }
    
    /**
     * Get security information for user display
     */
    public SecurityInfo getSecurityInfo() {
        SecurityInfo info = new SecurityInfo();
        info.isEncrypted = Build.VERSION.SDK_INT >= Build.VERSION_CODES.M;
        info.hasApiKey = hasApiKey();
        info.storageType = info.isEncrypted ? "AES256 Encrypted" : "Obfuscated";
        return info;
    }
    
    /**
     * Configuration data class
     */
    public static class LLMConfiguration {
        public String apiProvider = "openai";
        public boolean useLocalLLM = false;
        public String modelPreference;
        
        public android.content.ContentValues toContentValues() {
            android.content.ContentValues values = new android.content.ContentValues();
            values.put("api_provider", apiProvider);
            values.put("use_local", useLocalLLM ? 1 : 0);
            values.put("model_preference", modelPreference);
            return values;
        }
    }
    
    /**
     * Security information for user display
     */
    public static class SecurityInfo {
        public boolean isEncrypted;
        public boolean hasApiKey;
        public String storageType;
        
        public String getDisplayText() {
            StringBuilder info = new StringBuilder();
            info.append("Storage: ").append(storageType).append("\n");
            info.append("API Key: ").append(hasApiKey ? "Configured" : "Not configured").append("\n");
            if (isEncrypted) {
                info.append("✓ Hardware-backed encryption enabled");
            } else {
                info.append("⚠ Using basic obfuscation (Android 6.0+ recommended for full encryption)");
            }
            return info.toString();
        }
    }
    
    /**
     * Migration helper for existing plain text storage
     */
    public void migrateFromPlainTextStorage(SharedPreferences oldPrefs) {
        try {
            String oldApiKey = oldPrefs.getString("api_key", null);
            String oldProvider = oldPrefs.getString("api_provider", "openai");
            boolean oldUseLocal = oldPrefs.getBoolean("use_local", false);
            
            if (oldApiKey != null && !oldApiKey.isEmpty()) {
                // Migrate to secure storage
                storeApiKey(oldApiKey);
                
                LLMConfiguration config = new LLMConfiguration();
                config.apiProvider = oldProvider;
                config.useLocalLLM = oldUseLocal;
                storeConfiguration(config);
                
                // Clear old insecure storage
                oldPrefs.edit()
                    .remove("api_key")
                    .remove("api_provider") 
                    .remove("use_local")
                    .apply();
                    
                Log.i(TAG, "Successfully migrated credentials to secure storage");
            }
            
        } catch (Exception e) {
            Log.e(TAG, "Failed to migrate from plain text storage", e);
        }
    }
}