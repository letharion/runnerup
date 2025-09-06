/*
 * Copyright (C) 2024
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 */

package org.runnerup.export;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * Handles asynchronous execution of LLM operations to prevent UI thread blocking
 */
public class LLMAsyncExecutor {
    
    private static final String TAG = "LLMAsyncExecutor";
    
    // Thread pool for LLM operations
    private static final ExecutorService executor = Executors.newFixedThreadPool(
        Math.max(2, Runtime.getRuntime().availableProcessors() / 2)
    );
    
    // Handler for UI thread callbacks
    private static final Handler mainHandler = new Handler(Looper.getMainLooper());
    
    /**
     * Execute LLM request asynchronously with callbacks
     */
    public static <T> Future<T> executeAsync(
            LLMAsyncTask<T> task,
            LLMCallback<T> callback) {
        
        return executor.submit(() -> {
            long startTime = System.currentTimeMillis();
            Log.d(TAG, "Starting async LLM task: " + task.getTaskName());
            
            try {
                // Execute the task on background thread
                T result = task.execute();
                long duration = System.currentTimeMillis() - startTime;
                
                Log.d(TAG, "LLM task completed in " + duration + "ms: " + task.getTaskName());
                
                // Post success callback to UI thread
                if (callback != null) {
                    mainHandler.post(() -> callback.onSuccess(result));
                }
                
                return result;
                
            } catch (Exception e) {
                long duration = System.currentTimeMillis() - startTime;
                Log.e(TAG, "LLM task failed after " + duration + "ms: " + task.getTaskName(), e);
                
                // Post error callback to UI thread
                if (callback != null) {
                    mainHandler.post(() -> callback.onError(e));
                }
                
                throw new RuntimeException(e);
            }
        });
    }
    
    /**
     * Execute LLM request with timeout
     */
    public static <T> Future<T> executeAsyncWithTimeout(
            LLMAsyncTask<T> task,
            LLMCallback<T> callback,
            long timeoutSeconds) {
        
        Future<T> future = executeAsync(task, callback);
        
        // Schedule timeout handling
        executor.schedule(() -> {
            if (!future.isDone() && !future.isCancelled()) {
                Log.w(TAG, "LLM task timeout after " + timeoutSeconds + "s: " + task.getTaskName());
                future.cancel(true);
                
                if (callback != null) {
                    mainHandler.post(() -> 
                        callback.onError(new LLMTimeoutException("Request timed out after " + timeoutSeconds + " seconds"))
                    );
                }
            }
        }, timeoutSeconds, TimeUnit.SECONDS);
        
        return future;
    }
    
    /**
     * Execute multiple LLM requests concurrently
     */
    public static <T> Future<T[]> executeAllAsync(
            LLMAsyncTask<T>[] tasks,
            LLMBatchCallback<T> callback) {
        
        return executor.submit(() -> {
            long startTime = System.currentTimeMillis();
            Log.d(TAG, "Starting batch LLM tasks: " + tasks.length + " tasks");
            
            try {
                @SuppressWarnings("unchecked")
                T[] results = (T[]) new Object[tasks.length];
                Exception firstException = null;
                
                // Execute all tasks and collect results
                for (int i = 0; i < tasks.length; i++) {
                    try {
                        results[i] = tasks[i].execute();
                    } catch (Exception e) {
                        if (firstException == null) {
                            firstException = e;
                        }
                        Log.e(TAG, "Batch task " + i + " failed: " + tasks[i].getTaskName(), e);
                    }
                }
                
                long duration = System.currentTimeMillis() - startTime;
                Log.d(TAG, "Batch LLM tasks completed in " + duration + "ms");
                
                // Post callback to UI thread
                if (callback != null) {
                    final Exception finalException = firstException;
                    mainHandler.post(() -> callback.onBatchComplete(results, finalException));
                }
                
                return results;
                
            } catch (Exception e) {
                long duration = System.currentTimeMillis() - startTime;
                Log.e(TAG, "Batch LLM tasks failed after " + duration + "ms", e);
                
                if (callback != null) {
                    mainHandler.post(() -> callback.onBatchComplete(null, e));
                }
                
                throw new RuntimeException(e);
            }
        });
    }
    
    /**
     * Check if current thread is UI thread
     */
    public static boolean isMainThread() {
        return Looper.myLooper() == Looper.getMainLooper();
    }
    
    /**
     * Execute on UI thread
     */
    public static void runOnUIThread(Runnable action) {
        if (isMainThread()) {
            action.run();
        } else {
            mainHandler.post(action);
        }
    }
    
    /**
     * Shutdown executor (call on app termination)
     */
    public static void shutdown() {
        Log.d(TAG, "Shutting down LLM async executor");
        executor.shutdown();
        try {
            if (!executor.awaitTermination(10, TimeUnit.SECONDS)) {
                Log.w(TAG, "Executor did not terminate gracefully, forcing shutdown");
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            Log.w(TAG, "Interrupted while waiting for executor shutdown", e);
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
    
    /**
     * Interface for async LLM tasks
     */
    public interface LLMAsyncTask<T> {
        T execute() throws Exception;
        String getTaskName();
    }
    
    /**
     * Callback interface for async results
     */
    public interface LLMCallback<T> {
        void onSuccess(T result);
        void onError(Exception error);
        
        default void onProgress(int percentComplete) {
            // Optional progress callback
        }
    }
    
    /**
     * Callback interface for batch operations
     */
    public interface LLMBatchCallback<T> {
        void onBatchComplete(T[] results, Exception error);
    }
    
    /**
     * Custom exception for timeout scenarios
     */
    public static class LLMTimeoutException extends Exception {
        public LLMTimeoutException(String message) {
            super(message);
        }
    }
}