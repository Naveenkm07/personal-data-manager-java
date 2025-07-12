package com.datamanager.util;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import org.json.simple.JSONObject;
import org.json.simple.JSONArray;

/**
 * Utility class for browser extension integration.
 * Handles communication between the application and browser extensions for password auto-fill.
 */
public class BrowserExtensionUtil {
    
    private static final int SERVER_PORT = 45678; // Local port for extension communication
    private static boolean serverRunning = false;
    private static SimpleHttpServer server;
    
    /**
     * Starts the local server for browser extension communication
     */
    public static void startExtensionServer() {
        if (serverRunning) {
            return;
        }
        
        try {
            server = new SimpleHttpServer(SERVER_PORT, (request, response) -> {
                // Handle requests from browser extension
                if (request.getPath().equals("/api/auth")) {
                    // Authenticate extension
                    String authToken = request.getParameter("token");
                    if (isValidAuthToken(authToken)) {
                        response.setStatus(200);
                        response.setBody("{\"status\":\"success\",\"message\":\"Authenticated\"}");
                    } else {
                        response.setStatus(401);
                        response.setBody("{\"status\":\"error\",\"message\":\"Invalid authentication\"}");
                    }
                } else if (request.getPath().equals("/api/credentials")) {
                    // Return credentials for a specific URL pattern
                    String url = request.getParameter("url");
                    String token = request.getParameter("token");
                    if (isValidAuthToken(token) && url != null) {
                        response.setStatus(200);
                        response.setBody(getCredentialsForUrl(url));
                    } else {
                        response.setStatus(400);
                        response.setBody("{\"status\":\"error\",\"message\":\"Missing or invalid parameters\"}");
                    }
                } else if (request.getPath().equals("/api/update-usage")) {
                    // Update last used timestamp for a credential
                    String token = request.getParameter("token");
                    int credentialId = Integer.parseInt(request.getParameter("id"));
                    if (isValidAuthToken(token)) {
                        updateCredentialUsage(credentialId);
                        response.setStatus(200);
                        response.setBody("{\"status\":\"success\",\"message\":\"Usage updated\"}");
                    } else {
                        response.setStatus(401);
                        response.setBody("{\"status\":\"error\",\"message\":\"Invalid authentication\"}");
                    }
                } else {
                    response.setStatus(404);
                    response.setBody("{\"status\":\"error\",\"message\":\"Endpoint not found\"}");
                }
            });
            
            server.start();
            serverRunning = true;
            System.out.println("Browser extension server started on port " + SERVER_PORT);
        } catch (Exception e) {
            System.err.println("Failed to start browser extension server: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * Stops the local server for browser extension communication
     */
    public static void stopExtensionServer() {
        if (serverRunning && server != null) {
            server.stop();
            serverRunning = false;
            System.out.println("Browser extension server stopped");
        }
    }
    
    /**
     * Validates the authentication token from the extension
     */
    private static boolean isValidAuthToken(String token) {
        // In a real application, implement proper token validation
        // For demonstration, just check if it's not null/empty
        return token != null && !token.isEmpty();
    }
    
    /**
     * Gets credentials for a specific URL
     */
    private static String getCredentialsForUrl(String url) {
        JSONArray credentialsArray = new JSONArray();
        
        try (Connection conn = DatabaseUtil.getConnection()) {
            // Find credentials that match URL patterns
            String query = "SELECT id, website, username, encrypted_password, url_pattern " +
                         "FROM passwords " +
                         "WHERE auto_fill_enabled = 1 AND " +
                         "(website = ? OR ? LIKE url_pattern OR url_pattern IS NULL)";
            
            try (PreparedStatement stmt = conn.prepareStatement(query)) {
                stmt.setString(1, url);
                stmt.setString(2, url);
                
                ResultSet rs = stmt.executeQuery();
                while (rs.next()) {
                    JSONObject credential = new JSONObject();
                    credential.put("id", rs.getInt("id"));
                    credential.put("website", rs.getString("website"));
                    credential.put("username", rs.getString("username"));
                    
                    // Decrypt password
                    String encryptedPassword = rs.getString("encrypted_password");
                    String decryptedPassword = SecurityUtil.decryptPassword(encryptedPassword, "your-encryption-key");
                    credential.put("password", decryptedPassword);
                    
                    credentialsArray.add(credential);
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
            return "{\"status\":\"error\",\"message\":\"Database error\"}";
        }
        
        JSONObject response = new JSONObject();
        response.put("status", "success");
        response.put("credentials", credentialsArray);
        
        return response.toJSONString();
    }
    
    /**
     * Updates the last used timestamp for a credential
     */
    private static void updateCredentialUsage(int credentialId) {
        try (Connection conn = DatabaseUtil.getConnection()) {
            String query = "UPDATE passwords SET last_used = ? WHERE id = ?";
            try (PreparedStatement stmt = conn.prepareStatement(query)) {
                stmt.setTimestamp(1, new Timestamp(new Date().getTime()));
                stmt.setInt(2, credentialId);
                stmt.executeUpdate();
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
    
    /**
     * Generates the extension installation instructions.
     */
    public static String getExtensionInstructions() {
        return "To integrate password auto-fill with your browser:\n\n" +
               "1. Install the NHCE Password Manager Extension:\n" +
               "   - Chrome: Visit Chrome Web Store and search for NHCE Password Manager\n" +
               "   - Firefox: Visit Firefox Add-ons and search for NHCE Password Manager\n\n" +
               "2. After installation, click on the extension icon and enter your credentials\n\n" +
               "3. Make sure this application is running for the extension to access your passwords\n\n" +
               "4. The extension will automatically suggest logins when you visit saved websites\n\n" +
               "Connection Information:\n" +
               "Local API URL: http://localhost:" + SERVER_PORT + "/api/";
    }
    
    /**
     * Updates the URL pattern for a password entry
     */
    public static void updateUrlPattern(int passwordId, String urlPattern) {
        try (Connection conn = DatabaseUtil.getConnection()) {
            String query = "UPDATE passwords SET url_pattern = ? WHERE id = ?";
            try (PreparedStatement stmt = conn.prepareStatement(query)) {
                stmt.setString(1, urlPattern);
                stmt.setInt(2, passwordId);
                stmt.executeUpdate();
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
    
    /**
     * Toggles auto-fill for a password entry
     */
    public static void toggleAutoFill(int passwordId, boolean enabled) {
        try (Connection conn = DatabaseUtil.getConnection()) {
            String query = "UPDATE passwords SET auto_fill_enabled = ? WHERE id = ?";
            try (PreparedStatement stmt = conn.prepareStatement(query)) {
                stmt.setInt(1, enabled ? 1 : 0);
                stmt.setInt(2, passwordId);
                stmt.executeUpdate();
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
    
    /**
     * A very simple HTTP server implementation.
     * In a real application, use a more robust server implementation.
     */
    private static class SimpleHttpServer {
        // Simple placeholder for HTTP server
        // In a real implementation, use a proper HTTP server like NanoHTTPD or Jetty
        
        private final int port;
        private final RequestHandler handler;
        private boolean running;
        
        public SimpleHttpServer(int port, RequestHandler handler) {
            this.port = port;
            this.handler = handler;
        }
        
        public void start() {
            // In a real implementation, start the HTTP server
            running = true;
        }
        
        public void stop() {
            // In a real implementation, stop the HTTP server
            running = false;
        }
        
        public interface RequestHandler {
            void handle(Request request, Response response);
        }
        
        public static class Request {
            private String path;
            private Map<String, String> parameters = new HashMap<>();
            
            public String getPath() {
                return path;
            }
            
            public String getParameter(String name) {
                return parameters.get(name);
            }
        }
        
        public static class Response {
            private int status;
            private String body;
            
            public void setStatus(int status) {
                this.status = status;
            }
            
            public void setBody(String body) {
                this.body = body;
            }
        }
    }
} 