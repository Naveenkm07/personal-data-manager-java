package com.datamanager.util;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.crypto.Cipher;
import javax.crypto.CipherOutputStream;
import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import javax.swing.JOptionPane;
import javax.swing.JPasswordField;

import org.json.simple.JSONObject;
import org.json.simple.JSONArray;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

/**
 * Utility class for importing and exporting data in various formats.
 */
public class DataTransferUtil {
    
    private static final String ENCRYPTION_ALGORITHM = "AES/CBC/PKCS5Padding";
    
    /**
     * Exports password data to CSV format
     * 
     * @param userId The user ID 
     * @param filePath The path to save the exported file
     * @param includePasswords Whether to include actual passwords in export
     * @return true if export was successful
     */
    public static boolean exportToCSV(int userId, String filePath, boolean includePasswords) {
        try (Connection conn = DatabaseUtil.getConnection()) {
            // Create file writer
            try (FileWriter writer = new FileWriter(filePath)) {
                // Write header
                writer.write("Website,Username,Password,URL Pattern,Last Used,Strength Score,Auto Fill Enabled\n");
                
                // Get all passwords for the user
                String query = "SELECT website, username, encrypted_password, url_pattern, " +
                               "last_used, strength_score, auto_fill_enabled FROM passwords WHERE user_id = ?";
                
                try (PreparedStatement stmt = conn.prepareStatement(query)) {
                    stmt.setInt(1, userId);
                    ResultSet rs = stmt.executeQuery();
                    
                    while (rs.next()) {
                        String website = rs.getString("website");
                        String username = rs.getString("username");
                        String password = "********"; // Default masked password
                        
                        // Decrypt password if needed
                        if (includePasswords) {
                            String encryptedPassword = rs.getString("encrypted_password");
                            password = SecurityUtil.decryptPassword(encryptedPassword, "your-encryption-key");
                        }
                        
                        String urlPattern = rs.getString("url_pattern");
                        String lastUsed = rs.getString("last_used");
                        int strengthScore = rs.getInt("strength_score");
                        boolean autoFillEnabled = rs.getInt("auto_fill_enabled") == 1;
                        
                        // Escape fields and write to CSV
                        writer.write(escapeCSV(website) + "," +
                                     escapeCSV(username) + "," +
                                     escapeCSV(password) + "," +
                                     escapeCSV(urlPattern) + "," +
                                     escapeCSV(lastUsed) + "," +
                                     strengthScore + "," +
                                     autoFillEnabled + "\n");
                    }
                }
                
                System.out.println("CSV export successful: " + filePath);
                return true;
            }
        } catch (SQLException | IOException e) {
            System.err.println("Error exporting to CSV: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }
    
    /**
     * Exports password data to JSON format
     * 
     * @param userId The user ID 
     * @param filePath The path to save the exported file
     * @param includePasswords Whether to include actual passwords in export
     * @return true if export was successful
     */
    public static boolean exportToJSON(int userId, String filePath, boolean includePasswords) {
        try (Connection conn = DatabaseUtil.getConnection()) {
            JSONObject root = new JSONObject();
            JSONArray passwordsArray = new JSONArray();
            
            // Get all passwords for the user
            String query = "SELECT website, username, encrypted_password, url_pattern, " +
                           "last_used, strength_score, auto_fill_enabled FROM passwords WHERE user_id = ?";
            
            try (PreparedStatement stmt = conn.prepareStatement(query)) {
                stmt.setInt(1, userId);
                ResultSet rs = stmt.executeQuery();
                
                while (rs.next()) {
                    JSONObject passwordEntry = new JSONObject();
                    passwordEntry.put("website", rs.getString("website"));
                    passwordEntry.put("username", rs.getString("username"));
                    
                    // Add password if needed
                    if (includePasswords) {
                        String encryptedPassword = rs.getString("encrypted_password");
                        String decryptedPassword = SecurityUtil.decryptPassword(encryptedPassword, "your-encryption-key");
                        passwordEntry.put("password", decryptedPassword);
                    } else {
                        passwordEntry.put("password", "********");
                    }
                    
                    passwordEntry.put("url_pattern", rs.getString("url_pattern"));
                    passwordEntry.put("last_used", rs.getString("last_used"));
                    passwordEntry.put("strength_score", rs.getInt("strength_score"));
                    passwordEntry.put("auto_fill_enabled", rs.getInt("auto_fill_enabled") == 1);
                    
                    passwordsArray.add(passwordEntry);
                }
            }
            
            root.put("passwords", passwordsArray);
            root.put("export_date", new java.util.Date().toString());
            root.put("format_version", "1.0");
            
            // Write JSON to file
            try (FileWriter writer = new FileWriter(filePath)) {
                writer.write(root.toJSONString());
                System.out.println("JSON export successful: " + filePath);
                return true;
            }
            
        } catch (SQLException | IOException e) {
            System.err.println("Error exporting to JSON: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }
    
    /**
     * Creates an encrypted export file with customizable format
     * 
     * @param userId The user ID
     * @param filePath The path to save the encrypted file
     * @param password The password to encrypt the file with
     * @param exportFormat The format to export data in (json/csv)
     * @return true if export was successful
     */
    public static boolean createEncryptedExport(int userId, String filePath, char[] password, String exportFormat) {
        try {
            // First create a temporary export file
            String tempFilePath = filePath + ".tmp";
            boolean exportSuccess = false;
            
            if ("json".equalsIgnoreCase(exportFormat)) {
                exportSuccess = exportToJSON(userId, tempFilePath, true);
            } else if ("csv".equalsIgnoreCase(exportFormat)) {
                exportSuccess = exportToCSV(userId, tempFilePath, true);
            } else {
                throw new IllegalArgumentException("Unsupported export format: " + exportFormat);
            }
            
            if (!exportSuccess) {
                return false;
            }
            
            // Read the temporary file
            byte[] fileData = Files.readAllBytes(Paths.get(tempFilePath));
            
            // Generate encryption key from password
            byte[] passwordBytes = new String(password).getBytes(StandardCharsets.UTF_8);
            byte[] keyBytes = new byte[16]; // 128-bit AES key
            
            // Simple key derivation (in a real app, use a proper KDF)
            System.arraycopy(passwordBytes, 0, keyBytes, 0, Math.min(passwordBytes.length, keyBytes.length));
            SecretKey key = new SecretKeySpec(keyBytes, "AES");
            
            // Generate random IV
            byte[] iv = new byte[16];
            new java.security.SecureRandom().nextBytes(iv);
            IvParameterSpec ivSpec = new IvParameterSpec(iv);
            
            // Initialize Cipher for encryption
            Cipher cipher = Cipher.getInstance(ENCRYPTION_ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, key, ivSpec);
            
            // Create output file
            try (FileOutputStream fos = new FileOutputStream(filePath);
                 BufferedOutputStream bos = new BufferedOutputStream(fos)) {
                
                // Write format identifier
                bos.write("NHCEENC".getBytes(StandardCharsets.UTF_8));
                
                // Write IV
                bos.write(iv);
                
                // Write export format
                bos.write(exportFormat.getBytes(StandardCharsets.UTF_8)[0]);
                
                // Encrypt and write the data
                try (CipherOutputStream cos = new CipherOutputStream(bos, cipher)) {
                    cos.write(fileData);
                }
            }
            
            // Delete temporary file
            Files.delete(Paths.get(tempFilePath));
            
            System.out.println("Encrypted export successful: " + filePath);
            return true;
            
        } catch (Exception e) {
            System.err.println("Error creating encrypted export: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }
    
    /**
     * Imports password data from a CSV file
     * 
     * @param userId The user ID to import data for
     * @param filePath The path to the CSV file
     * @param importMode How to handle duplicates (skip/replace/keep_both)
     * @return The number of passwords imported
     */
    public static int importFromCSV(int userId, String filePath, String importMode) {
        int importedCount = 0;
        
        try (BufferedReader reader = new BufferedReader(new FileReader(filePath))) {
            String line;
            boolean isFirstLine = true;
            
            // Map for checking duplicates
            Map<String, String> existingPasswords;
            try {
                existingPasswords = getExistingPasswordMap(userId);
            } catch (SQLException e) {
                System.err.println("Error checking existing passwords: " + e.getMessage());
                e.printStackTrace();
                return -1;
            }
            
            while ((line = reader.readLine()) != null) {
                // Skip header row
                if (isFirstLine) {
                    isFirstLine = false;
                    continue;
                }
                
                // Parse CSV line
                String[] fields = parseCSVLine(line);
                if (fields.length < 3) continue; // Skip incomplete lines
                
                String website = fields[0];
                String username = fields[1];
                String password = fields[2];
                
                // Skip masked passwords
                if (password.equals("********")) continue;
                
                // Optional fields
                String urlPattern = fields.length > 3 ? fields[3] : null;
                String autoFillStr = fields.length > 6 ? fields[6] : "true";
                boolean autoFill = Boolean.parseBoolean(autoFillStr) || "1".equals(autoFillStr);
                
                // Check for duplicates
                String key = website + ":" + username;
                if (existingPasswords.containsKey(key)) {
                    if ("skip".equals(importMode)) {
                        continue; // Skip this entry
                    } else if ("replace".equals(importMode)) {
                        // Delete existing entry
                        try {
                            deleteExistingPassword(userId, website, username);
                        } catch (SQLException e) {
                            System.err.println("Error replacing password: " + e.getMessage());
                            continue; // Skip this entry and continue with next
                        }
                    } else if ("keep_both".equals(importMode)) {
                        // Modify website to avoid collision
                        website = website + " (Imported)";
                    }
                }
                
                // Insert password into database
                if (insertPassword(userId, website, username, password, urlPattern, autoFill)) {
                    importedCount++;
                }
            }
            
            System.out.println("CSV import successful: " + importedCount + " passwords imported");
            return importedCount;
            
        } catch (IOException e) {
            System.err.println("Error importing from CSV: " + e.getMessage());
            e.printStackTrace();
            return -1;
        }
    }
    
    /**
     * Imports password data from a JSON file
     * 
     * @param userId The user ID to import data for
     * @param filePath The path to the JSON file
     * @param importMode How to handle duplicates (skip/replace/keep_both)
     * @return The number of passwords imported
     */
    public static int importFromJSON(int userId, String filePath, String importMode) {
        int importedCount = 0;
        
        try {
            // Parse the JSON file
            JSONParser parser = new JSONParser();
            JSONObject root = (JSONObject) parser.parse(new FileReader(filePath));
            
            // Get passwords array
            JSONArray passwordsArray = (JSONArray) root.get("passwords");
            if (passwordsArray == null) {
                throw new ParseException(0, "Invalid JSON format. No 'passwords' array found.");
            }
            
            // Map for checking duplicates
            Map<String, String> existingPasswords;
            try {
                existingPasswords = getExistingPasswordMap(userId);
            } catch (SQLException e) {
                System.err.println("Error checking existing passwords: " + e.getMessage());
                e.printStackTrace();
                return -1;
            }
            
            // Process each password entry
            for (Object obj : passwordsArray) {
                JSONObject entry = (JSONObject) obj;
                
                String website = (String) entry.get("website");
                String username = (String) entry.get("username");
                String password = (String) entry.get("password");
                
                // Skip masked passwords
                if (password == null || password.equals("********")) continue;
                
                // Optional fields
                String urlPattern = (String) entry.get("url_pattern");
                Boolean autoFill = entry.containsKey("auto_fill_enabled") ? 
                    (Boolean) entry.get("auto_fill_enabled") : true;
                
                // Check for duplicates
                String key = website + ":" + username;
                if (existingPasswords.containsKey(key)) {
                    if ("skip".equals(importMode)) {
                        continue; // Skip this entry
                    } else if ("replace".equals(importMode)) {
                        // Delete existing entry
                        try {
                            deleteExistingPassword(userId, website, username);
                        } catch (SQLException e) {
                            System.err.println("Error replacing password: " + e.getMessage());
                            continue; // Skip this entry and continue with next
                        }
                    } else if ("keep_both".equals(importMode)) {
                        // Modify website to avoid collision
                        website = website + " (Imported)";
                    }
                }
                
                // Insert password into database
                if (insertPassword(userId, website, username, password, urlPattern, autoFill)) {
                    importedCount++;
                }
            }
            
            System.out.println("JSON import successful: " + importedCount + " passwords imported");
            return importedCount;
            
        } catch (IOException | ParseException e) {
            System.err.println("Error importing from JSON: " + e.getMessage());
            e.printStackTrace();
            return -1;
        }
    }
    
    /**
     * Imports passwords from an encrypted export file
     * 
     * @param userId The user ID to import data for
     * @param filePath The path to the encrypted file
     * @param password The password to decrypt the file with
     * @param importMode How to handle duplicates (skip/replace/keep_both)
     * @return The number of passwords imported
     */
    public static int importFromEncryptedFile(int userId, String filePath, char[] password, String importMode) {
        try {
            // Read the encrypted file
            byte[] fileData = Files.readAllBytes(Paths.get(filePath));
            
            // Check file header
            byte[] header = new byte[7];
            System.arraycopy(fileData, 0, header, 0, 7);
            if (!new String(header, StandardCharsets.UTF_8).equals("NHCEENC")) {
                throw new IOException("Invalid file format. Not a NHCE encrypted export.");
            }
            
            // Extract IV
            byte[] iv = new byte[16];
            System.arraycopy(fileData, 7, iv, 0, 16);
            IvParameterSpec ivSpec = new IvParameterSpec(iv);
            
            // Get export format
            char formatChar = (char) fileData[23];
            String exportFormat = formatChar == 'j' ? "json" : "csv";
            
            // Generate decryption key from password
            byte[] passwordBytes = new String(password).getBytes(StandardCharsets.UTF_8);
            byte[] keyBytes = new byte[16]; // 128-bit AES key
            
            // Simple key derivation (in a real app, use a proper KDF)
            System.arraycopy(passwordBytes, 0, keyBytes, 0, Math.min(passwordBytes.length, keyBytes.length));
            SecretKey key = new SecretKeySpec(keyBytes, "AES");
            
            // Initialize Cipher for decryption
            Cipher cipher = Cipher.getInstance(ENCRYPTION_ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, key, ivSpec);
            
            // Extract and decrypt the data
            byte[] encryptedData = new byte[fileData.length - 24];
            System.arraycopy(fileData, 24, encryptedData, 0, encryptedData.length);
            byte[] decryptedData = cipher.doFinal(encryptedData);
            
            // Create temporary file with decrypted data
            String tempFilePath = filePath + ".dec";
            Files.write(Paths.get(tempFilePath), decryptedData);
            
            // Import based on format
            int result;
            if ("json".equals(exportFormat)) {
                result = importFromJSON(userId, tempFilePath, importMode);
            } else {
                result = importFromCSV(userId, tempFilePath, importMode);
            }
            
            // Delete temporary file
            Files.delete(Paths.get(tempFilePath));
            
            return result;
            
        } catch (Exception e) {
            System.err.println("Error importing from encrypted file: " + e.getMessage());
            e.printStackTrace();
            return -1;
        }
    }
    
    /**
     * Imports passwords directly from Chrome/Firefox browser
     * @param userId The user ID to import data for
     * @param browserType The browser to import from ("chrome" or "firefox")
     * @param importMode How to handle duplicates
     * @return The number of passwords imported
     */
    public static int importFromBrowser(int userId, String browserType, String importMode) {
        int importedCount = 0;
        
        try {
            List<Map<String, String>> browserPasswords = new ArrayList<>();
            
            if ("chrome".equalsIgnoreCase(browserType)) {
                browserPasswords = extractChromePasswords();
            } else if ("firefox".equalsIgnoreCase(browserType)) {
                browserPasswords = extractFirefoxPasswords();
            } else {
                throw new IllegalArgumentException("Unsupported browser type: " + browserType);
            }
            
            // Check if any passwords were found
            if (browserPasswords.isEmpty()) {
                return 0;
            }
            
            // Map for checking duplicates
            Map<String, String> existingPasswords = getExistingPasswordMap(userId);
            
            // Import each password
            for (Map<String, String> entry : browserPasswords) {
                String website = entry.get("url");
                String username = entry.get("username");
                String password = entry.get("password");
                
                // Skip entries with missing data
                if (website == null || username == null || password == null || 
                    website.isEmpty() || password.isEmpty()) {
                    continue;
                }
                
                // Extract domain from URL
                try {
                    java.net.URL url = new java.net.URL(website);
                    website = url.getHost();
                } catch (Exception e) {
                    // If URL parsing fails, just use the original value
                }
                
                // Check for duplicates
                String key = website + ":" + username;
                if (existingPasswords.containsKey(key)) {
                    if ("skip".equals(importMode)) {
                        continue; // Skip this entry
                    } else if ("replace".equals(importMode)) {
                        // Delete existing entry
                        deleteExistingPassword(userId, website, username);
                    } else if ("keep_both".equals(importMode)) {
                        // Modify website to avoid collision
                        website = website + " (Imported from " + browserType + ")";
                    }
                }
                
                // Insert password into database
                if (insertPassword(userId, website, username, password, website, true)) {
                    importedCount++;
                }
            }
            
            System.out.println(browserType + " import successful: " + importedCount + " passwords imported");
            return importedCount;
            
        } catch (Exception e) {
            System.err.println("Error importing from " + browserType + ": " + e.getMessage());
            e.printStackTrace();
            return -1;
        }
    }
    
    /**
     * Extract passwords from Chrome
     * @return List of password entries
     */
    private static List<Map<String, String>> extractChromePasswords() {
        List<Map<String, String>> passwords = new ArrayList<>();
        
        // This is a stub implementation as actual Chrome password extraction
        // requires platform-specific native code to access the encrypted storage
        
        // In a real implementation, this would:
        // 1. Find the Chrome password database (Login Data file)
        // 2. Use platform-specific methods to decrypt the passwords
        // 3. Return the extracted passwords
        
        // Display a dialog explaining the limitation
        JOptionPane.showMessageDialog(null,
            "Direct Chrome password extraction requires platform-specific native code.\n\n" +
            "In a production app, this would use:\n" +
            "- Windows: DPAPI and CryptUnprotectData\n" +
            "- macOS: Keychain Access APIs\n" +
            "- Linux: Secret Service API\n\n" +
            "As an alternative, please export your passwords from Chrome to CSV and import that file.",
            "Chrome Import Information",
            JOptionPane.INFORMATION_MESSAGE);
            
        return passwords;
    }
    
    /**
     * Extract passwords from Firefox
     * @return List of password entries
     */
    private static List<Map<String, String>> extractFirefoxPasswords() {
        List<Map<String, String>> passwords = new ArrayList<>();
        
        // This is a stub implementation as actual Firefox password extraction
        // requires access to the encrypted storage
        
        // In a real implementation, this would:
        // 1. Find the Firefox profiles directory
        // 2. Locate the key4.db and logins.json files
        // 3. Use NSS libraries to decrypt the passwords
        // 4. Return the extracted passwords
        
        // Display a dialog explaining the limitation
        JOptionPane.showMessageDialog(null,
            "Direct Firefox password extraction requires Mozilla NSS libraries.\n\n" +
            "In a production app, this would use:\n" +
            "- Firefox's NSS libraries\n" +
            "- Access to key4.db and logins.json\n" +
            "- Master password if set\n\n" +
            "As an alternative, please export your passwords from Firefox and import that file.",
            "Firefox Import Information",
            JOptionPane.INFORMATION_MESSAGE);
            
        return passwords;
    }
    
    /**
     * Helper method to insert a password into the database
     */
    private static boolean insertPassword(int userId, String website, String username, 
                                        String password, String urlPattern, boolean autoFill) {
        try (Connection conn = DatabaseUtil.getConnection()) {
            String encryptedPassword = SecurityUtil.encryptPassword(password, "your-encryption-key");
            
            String query = "INSERT INTO passwords (user_id, website, username, encrypted_password, " +
                         "url_pattern, auto_fill_enabled, strength_score) VALUES (?, ?, ?, ?, ?, ?, ?)";
            
            try (PreparedStatement stmt = conn.prepareStatement(query)) {
                stmt.setInt(1, userId);
                stmt.setString(2, website);
                stmt.setString(3, username);
                stmt.setString(4, encryptedPassword);
                stmt.setString(5, urlPattern);
                stmt.setInt(6, autoFill ? 1 : 0);
                
                // Calculate password strength
                int strength = analyzePasswordStrength(password);
                stmt.setInt(7, strength);
                
                stmt.executeUpdate();
                return true;
            }
        } catch (SQLException e) {
            System.err.println("Error inserting password: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Helper method to delete an existing password
     */
    private static void deleteExistingPassword(int userId, String website, String username) throws SQLException {
        try (Connection conn = DatabaseUtil.getConnection()) {
            String query = "DELETE FROM passwords WHERE user_id = ? AND website = ? AND username = ?";
            
            try (PreparedStatement stmt = conn.prepareStatement(query)) {
                stmt.setInt(1, userId);
                stmt.setString(2, website);
                stmt.setString(3, username);
                stmt.executeUpdate();
            }
        }
    }
    
    /**
     * Helper method to get a map of existing passwords for duplicate checking
     */
    private static Map<String, String> getExistingPasswordMap(int userId) throws SQLException {
        Map<String, String> map = new HashMap<>();
        
        try (Connection conn = DatabaseUtil.getConnection()) {
            String query = "SELECT website, username FROM passwords WHERE user_id = ?";
            
            try (PreparedStatement stmt = conn.prepareStatement(query)) {
                stmt.setInt(1, userId);
                ResultSet rs = stmt.executeQuery();
                
                while (rs.next()) {
                    String website = rs.getString("website");
                    String username = rs.getString("username");
                    map.put(website + ":" + username, "exists");
                }
            }
        }
        
        return map;
    }
    
    /**
     * Helper method to analyze password strength (simplified version)
     */
    private static int analyzePasswordStrength(String password) {
        if (password == null || password.isEmpty()) {
            return 0;
        }
        
        int score = 0;
        
        // Length contributes up to 40 points
        int lengthScore = Math.min(password.length() * 3, 40);
        score += lengthScore;
        
        // Character variety contributes up to 40 points
        int varietyScore = 0;
        if (password.matches(".*[a-z].*")) varietyScore += 10; // Lowercase
        if (password.matches(".*[A-Z].*")) varietyScore += 10; // Uppercase
        if (password.matches(".*\\d.*")) varietyScore += 10;   // Digits
        if (password.matches(".*[^a-zA-Z0-9].*")) varietyScore += 10; // Special chars
        score += varietyScore;
        
        // Complexity contributes up to 20 points
        int complexityScore = 0;
        // Check for not using common patterns
        if (!password.matches(".*123.*") && !password.matches(".*abc.*")) {
            complexityScore += 5;
        }
        // Check for mixed character types
        if (password.matches(".*[a-z].*[A-Z].*") || 
            password.matches(".*[A-Z].*[a-z].*")) {
            complexityScore += 5;
        }
        if (password.matches(".*[a-zA-Z].*\\d.*") || 
            password.matches(".*\\d.*[a-zA-Z].*")) {
            complexityScore += 5;
        }
        if (password.matches(".*[a-zA-Z0-9].*[^a-zA-Z0-9].*") || 
            password.matches(".*[^a-zA-Z0-9].*[a-zA-Z0-9].*")) {
            complexityScore += 5;
        }
        score += complexityScore;
        
        return score;
    }
    
    /**
     * Helper method to escape strings for CSV output
     */
    private static String escapeCSV(String value) {
        if (value == null) return "";
        
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            value = value.replace("\"", "\"\"");
            value = "\"" + value + "\"";
        }
        
        return value;
    }
    
    /**
     * Helper method to parse CSV lines respecting quotes
     */
    private static String[] parseCSVLine(String line) {
        List<String> result = new ArrayList<>();
        StringBuilder currentField = new StringBuilder();
        boolean inQuotes = false;
        
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            
            if (c == '\"') {
                // If we see a quote inside quotes, check if it's an escaped quote
                if (inQuotes && i + 1 < line.length() && line.charAt(i + 1) == '\"') {
                    currentField.append('\"');
                    i++; // Skip the next quote
                } else {
                    inQuotes = !inQuotes;
                }
            } else if (c == ',' && !inQuotes) {
                // End of field
                result.add(currentField.toString());
                currentField.setLength(0);
            } else {
                currentField.append(c);
            }
        }
        
        // Add the last field
        result.add(currentField.toString());
        
        return result.toArray(new String[0]);
    }
} 