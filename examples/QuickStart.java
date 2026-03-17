/**
 * Cortex Community Edition - Quick Start Example
 * 
 * This example demonstrates how to use the Cortex CE API.
 */
public class QuickStartExample {
    
    public static void main(String[] args) {
        // Initialize the Cortex client
        String baseUrl = "http://localhost:37777";
        String apiKey = System.getenv("CORTEX_API_KEY");
        
        // Create a session
        // Session session = new Session(baseUrl, apiKey);
        
        // Send a message
        // Response response = session.sendMessage("Hello, Cortex!");
        
        System.out.println("Cortex Community Edition Quick Start");
        System.out.println("=====================================");
        System.out.println("See documentation for full API reference.");
    }
}
