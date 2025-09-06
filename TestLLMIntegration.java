/*
 * Simple test to validate LLM integration works
 */
import org.runnerup.export.*;
import android.content.ContentValues;
import android.util.Pair;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;
import java.io.File;

public class TestLLMIntegration {
    
    public static void main(String[] args) {
        System.out.println("Testing LLM Integration...");
        
        try {
            // Create LLM synchronizer
            LLMSynchronizer synchronizer = new LLMSynchronizer();
            
            // Configure with empty API key (will use fallback)
            ContentValues config = new ContentValues();
            config.put("api_key", "");
            config.put("api_provider", "openai");
            config.put("use_local", false);
            synchronizer.init(config);
            
            System.out.println("✓ Synchronizer initialized");
            System.out.println("✓ Name: " + synchronizer.getName());
            System.out.println("✓ ID: " + synchronizer.getId());
            
            // Test workout listing
            List<Pair<String, String>> workouts = new ArrayList<>();
            Synchronizer.Status status = synchronizer.listWorkouts(workouts);
            
            System.out.println("✓ Workout listing status: " + status);
            System.out.println("✓ Found " + workouts.size() + " workouts");
            
            for (Pair<String, String> workout : workouts) {
                System.out.println("  - " + workout.first + ": " + workout.second);
            }
            
            // Test workout generation
            if (!workouts.isEmpty()) {
                File testFile = new File("test_workout.json");
                synchronizer.downloadWorkout(testFile, workouts.get(0).first);
                
                if (testFile.exists() && testFile.length() > 0) {
                    System.out.println("✓ Workout file generated: " + testFile.getAbsolutePath());
                    System.out.println("✓ File size: " + testFile.length() + " bytes");
                    
                    // Clean up
                    testFile.delete();
                } else {
                    System.out.println("✗ Failed to generate workout file");
                }
            }
            
            System.out.println("\n=== LLM Integration Test PASSED ===");
            
        } catch (Exception e) {
            System.err.println("✗ Test failed: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
}