package com.datamanager.util;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;
import java.util.Base64;
import java.nio.ByteBuffer;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.time.Instant;
import java.security.MessageDigest;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

public class SecurityUtil {
    private static final int ITERATIONS = 65536;
    private static final int KEY_LENGTH = 256;
    private static final String ALGORITHM = "PBKDF2WithHmacSHA256";
    
    // TOTP constants
    private static final int TIME_STEP = 30; // 30 seconds
    private static final int CODE_DIGITS = 6; // 6-digit codes
    private static final String TOTP_ALGORITHM = "HmacSHA1";
    
    private static final int GCM_TAG_LENGTH = 16;
    
    public static String generateSalt() {
        SecureRandom random = new SecureRandom();
        byte[] salt = new byte[16];
        random.nextBytes(salt);
        return Base64.getEncoder().encodeToString(salt);
    }
    
    public static String hashPassword(String password, String salt) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            md.update(Base64.getDecoder().decode(salt));
            byte[] hashedPassword = md.digest(password.getBytes());
            return Base64.getEncoder().encodeToString(hashedPassword);
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
            return null;
        }
    }
    
    public static boolean verifyPassword(String password, String salt, String storedHash) {
        String computedHash = hashPassword(password, salt);
        return computedHash.equals(storedHash);
    }
    
    // Generate a random secret key for TOTP
    public static String generateTotpSecret() {
        SecureRandom random = new SecureRandom();
        byte[] secret = new byte[20]; // 20 bytes is recommended for TOTP
        random.nextBytes(secret);
        return Base64.getEncoder().encodeToString(secret);
    }
    
    // Generate a QR code URL for TOTP setup
    public static String generateTotpQrCodeUrl(String username, String appName, String secret) {
        String encodedAppName = encodeUrl(appName);
        String encodedUsername = encodeUrl(username);
        String encodedSecret = encodeUrl(secret);
        
        return String.format(
            "otpauth://totp/%s:%s?secret=%s&issuer=%s",
            encodedAppName, encodedUsername, encodedSecret, encodedAppName
        );
    }
    
    // Helper method to URL encode strings
    private static String encodeUrl(String input) {
        try {
            return java.net.URLEncoder.encode(input, "UTF-8")
                    .replace("+", "%20")
                    .replace("%21", "!")
                    .replace("%27", "'")
                    .replace("%28", "(")
                    .replace("%29", ")")
                    .replace("%7E", "~");
        } catch (java.io.UnsupportedEncodingException e) {
            return input; // Fallback if encoding fails
        }
    }
    
    // Generate a TOTP code using the secret key
    public static String generateTotpCode(String secretKey) {
        try {
            String normalizedBase32Key = secretKey
                    .replace(" ", "")
                    .toUpperCase();
            
            // Get current interval
            long time = Instant.now().getEpochSecond() / TIME_STEP;
            byte[] timeBytes = longToBytes(time);
            
            // Calculate HMAC-SHA1 hash
            byte[] hash = calculateHmacSha1(Base64.getDecoder().decode(secretKey), timeBytes);
            
            // Truncate hash and extract OTP
            int offset = hash[hash.length - 1] & 0xF;
            int code = ((hash[offset] & 0x7F) << 24) |
                       ((hash[offset + 1] & 0xFF) << 16) |
                       ((hash[offset + 2] & 0xFF) << 8) |
                       (hash[offset + 3] & 0xFF);
            
            code = code % (int) Math.pow(10, CODE_DIGITS);
            
            // Format code with leading zeros
            return String.format("%0" + CODE_DIGITS + "d", code);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }
    
    // Validate a TOTP code
    public static boolean validateTotpCode(String secret, String code) {
        // Check current time and one step before/after to allow for clock skew
        long currentTime = Instant.now().getEpochSecond();
        
        // Verify the code for a window of time
        for (int i = -1; i <= 1; i++) {
            long timeCounter = (currentTime / TIME_STEP) + i;
            try {
                // Decode the secret key
                byte[] secretBytes = Base64.getDecoder().decode(secret);
                
                // Generate HMAC-SHA1 hash of the time counter
                byte[] hash = generateHmac(timeCounter, secretBytes);
                
                // Extract a 4-byte integer from the hash and convert to digits
                int offset = hash[hash.length - 1] & 0x0F;
                int binary = ((hash[offset] & 0x7F) << 24) |
                             ((hash[offset + 1] & 0xFF) << 16) |
                             ((hash[offset + 2] & 0xFF) << 8) |
                             (hash[offset + 3] & 0xFF);
                
                // Limit to CODE_DIGITS
                int otp = binary % (int) Math.pow(10, CODE_DIGITS);
                
                // Format with leading zeros if necessary
                String generatedCode = String.format("%0" + CODE_DIGITS + "d", otp);
                
                // Check if the code matches
                if (code.equals(generatedCode)) {
                    return true;
                }
            } catch (Exception e) {
                // Continue checking other time windows
            }
        }
        
        return false;
    }
    
    private static byte[] generateHmac(long counter, byte[] secret) throws Exception {
        // Convert counter to byte array
        byte[] counterBytes = ByteBuffer.allocate(8).putLong(counter).array();
        
        // Create HMAC-SHA1 instance
        Mac mac = Mac.getInstance(TOTP_ALGORITHM);
        mac.init(new SecretKeySpec(secret, TOTP_ALGORITHM));
        
        // Generate HMAC
        return mac.doFinal(counterBytes);
    }
    
    // Generate backup codes
    public static String[] generateBackupCodes(int count) {
        String[] codes = new String[count];
        SecureRandom random = new SecureRandom();
        
        for (int i = 0; i < count; i++) {
            int code = random.nextInt(1000000000);
            codes[i] = String.format("%09d", code);
        }
        
        return codes;
    }
    
    // Verify TOTP code
    public static boolean verifyTotpCode(String secretKey, String code) {
        try {
            // Get the current code
            String currentCode = generateTotpCode(secretKey);
            
            // Allow for time drift by also checking previous and next codes
            String previousCode = generateTotpCodeWithOffset(secretKey, -1);
            String nextCode = generateTotpCodeWithOffset(secretKey, 1);
            
            return code.equals(currentCode) || code.equals(previousCode) || code.equals(nextCode);
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }
    
    // Helper to generate TOTP code with time offset (for handling clock skew)
    private static String generateTotpCodeWithOffset(String secretKey, int timeOffset) {
        try {
            String normalizedBase32Key = secretKey
                    .replace(" ", "")
                    .toUpperCase();
            
            // Get interval with offset
            long time = (Instant.now().getEpochSecond() / TIME_STEP) + timeOffset;
            byte[] timeBytes = longToBytes(time);
            
            // Calculate HMAC-SHA1 hash
            byte[] hash = calculateHmacSha1(Base64.getDecoder().decode(secretKey), timeBytes);
            
            // Truncate hash and extract OTP
            int offset = hash[hash.length - 1] & 0xF;
            int truncatedHash = ((hash[offset] & 0x7F) << 24) |
                              ((hash[offset + 1] & 0xFF) << 16) |
                              ((hash[offset + 2] & 0xFF) << 8) |
                              (hash[offset + 3] & 0xFF);
            
            int code = truncatedHash % (int) Math.pow(10, CODE_DIGITS);
            
            // Format code with leading zeros
            return String.format("%0" + CODE_DIGITS + "d", code);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }
    
    // Convert long to byte array
    private static byte[] longToBytes(long value) {
        byte[] result = new byte[8];
        for (int i = 7; i >= 0; i--) {
            result[i] = (byte) (value & 0xFF);
            value >>= 8;
        }
        return result;
    }
    
    // Calculate HMAC-SHA1 hash
    private static byte[] calculateHmacSha1(byte[] key, byte[] data) throws Exception {
        javax.crypto.Mac mac = javax.crypto.Mac.getInstance(TOTP_ALGORITHM);
        SecretKeySpec keySpec = new SecretKeySpec(key, TOTP_ALGORITHM);
        mac.init(keySpec);
        return mac.doFinal(data);
    }
    
    /**
     * Encrypt a password for storage in the database
     * @param plainTextPassword The password to encrypt
     * @param encryptionKey The encryption key to use
     * @return The encrypted password
     */
    public static String encryptPassword(String plainTextPassword, String encryptionKey) {
        return encryptData(plainTextPassword, encryptionKey);
    }
    
    /**
     * Decrypt a password from the database
     * @param encryptedPassword The encrypted password
     * @param encryptionKey The encryption key used for encryption
     * @return The decrypted password
     */
    public static String decryptPassword(String encryptedPassword, String encryptionKey) {
        return decryptData(encryptedPassword, encryptionKey);
    }
    
    /**
     * Encrypt general data (can be used for notes, documents, etc.)
     * 
     * @param data The data to encrypt
     * @param encryptionKey The encryption key to use
     * @return The encrypted data as Base64 string
     */
    public static String encryptData(String data, String encryptionKey) {
        try {
            // Generate a random IV
            SecureRandom random = new SecureRandom();
            byte[] iv = new byte[12]; // 96 bits IV for GCM
            random.nextBytes(iv);
            
            // Create a secret key from the encryption key
            byte[] keyBytes = encryptionKey.getBytes();
            MessageDigest sha = MessageDigest.getInstance("SHA-256");
            keyBytes = sha.digest(keyBytes);
            keyBytes = java.util.Arrays.copyOf(keyBytes, 16); // AES key length: 16 bytes
            SecretKey secretKey = new SecretKeySpec(keyBytes, "AES");
            
            // Create cipher instance and initialize
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH * 8, iv);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, parameterSpec);
            
            // Encrypt
            byte[] encryptedText = cipher.doFinal(data.getBytes());
            
            // Combine IV and encrypted text
            byte[] combined = new byte[iv.length + encryptedText.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(encryptedText, 0, combined, iv.length, encryptedText.length);
            
            // Encode to Base64
            return Base64.getEncoder().encodeToString(combined);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }
    
    /**
     * Decrypt general data
     * 
     * @param encryptedData The encrypted data as Base64 string
     * @param encryptionKey The encryption key used for encryption
     * @return The decrypted data
     */
    public static String decryptData(String encryptedData, String encryptionKey) {
        try {
            // Decode from Base64
            byte[] combined = Base64.getDecoder().decode(encryptedData);
            
            // Extract IV
            byte[] iv = new byte[12];
            System.arraycopy(combined, 0, iv, 0, iv.length);
            
            // Extract encrypted text
            byte[] encryptedText = new byte[combined.length - iv.length];
            System.arraycopy(combined, iv.length, encryptedText, 0, encryptedText.length);
            
            // Create a secret key from the encryption key
            byte[] keyBytes = encryptionKey.getBytes();
            MessageDigest sha = MessageDigest.getInstance("SHA-256");
            keyBytes = sha.digest(keyBytes);
            keyBytes = java.util.Arrays.copyOf(keyBytes, 16); // AES key length: 16 bytes
            SecretKey secretKey = new SecretKeySpec(keyBytes, "AES");
            
            // Create cipher instance and initialize
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH * 8, iv);
            cipher.init(Cipher.DECRYPT_MODE, secretKey, parameterSpec);
            
            // Decrypt
            byte[] decryptedText = cipher.doFinal(encryptedText);
            
            return new String(decryptedText);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }
} 