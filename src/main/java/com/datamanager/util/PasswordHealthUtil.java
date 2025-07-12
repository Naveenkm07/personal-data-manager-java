package com.datamanager.util;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import javax.mail.Authenticator;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.PasswordAuthentication;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;

/**
 * Utility class for password health report generation and scheduling.
 */
public class PasswordHealthUtil {
    
    // Health report constants
    private static final int MIN_PASSWORD_LENGTH = 12;
    private static final long PASSWORD_AGE_WARNING_DAYS = 90; // 3 months
    private static final int MIN_STRENGTH_SCORE = 70;
    
    // Email settings
    private static final String SMTP_HOST = "smtp.example.com";
    private static final int SMTP_PORT = 587;
    private static final String SMTP_USERNAME = "reports@nhce.example.com";
    private static final String SMTP_PASSWORD = "your-email-password";
    private static final String FROM_EMAIL = "reports@nhce.example.com";
    
    /**
     * Generates a password health report for a user
     * @param userId The user ID
     * @return true if report was generated successfully
     */
    public static boolean generateHealthReport(int userId) {
        try (Connection conn = DatabaseUtil.getConnection()) {
            // Get user info
            String userEmail = getUserEmail(userId, conn);
            if (userEmail == null || userEmail.isEmpty()) {
                System.err.println("User has no email address configured");
                return false;
            }
            
            // Analyze password health
            List<PasswordEntry> passwords = getPasswordsForUser(userId, conn);
            
            int totalPasswords = passwords.size();
            if (totalPasswords == 0) {
                System.out.println("No passwords to analyze for user " + userId);
                return false;
            }
            
            int weakPasswords = 0;
            int oldPasswords = 0;
            int reusedPasswords = 0;
            int totalStrength = 0;
            Map<String, Integer> passwordCounts = new HashMap<>();
            
            // Analyze each password
            for (PasswordEntry entry : passwords) {
                // Check strength
                if (entry.strengthScore < MIN_STRENGTH_SCORE) {
                    weakPasswords++;
                }
                
                // Check age
                if (isPasswordOld(entry.lastUsed)) {
                    oldPasswords++;
                }
                
                // Count password reuse
                String decryptedPassword = SecurityUtil.decryptPassword(entry.encryptedPassword, "your-encryption-key");
                passwordCounts.put(decryptedPassword, passwordCounts.getOrDefault(decryptedPassword, 0) + 1);
                
                // Add to total strength
                totalStrength += entry.strengthScore;
            }
            
            // Count reused passwords
            for (PasswordEntry entry : passwords) {
                String decryptedPassword = SecurityUtil.decryptPassword(entry.encryptedPassword, "your-encryption-key");
                if (passwordCounts.get(decryptedPassword) > 1) {
                    reusedPasswords++;
                }
            }
            
            // Calculate overall score
            int averageStrength = totalStrength / totalPasswords;
            int reusedScore = 100 - ((reusedPasswords * 100) / totalPasswords);
            int weakScore = 100 - ((weakPasswords * 100) / totalPasswords);
            int ageScore = 100 - ((oldPasswords * 100) / totalPasswords);
            
            int overallScore = (averageStrength + reusedScore + weakScore + ageScore) / 4;
            
            // Generate report data
            StringBuilder reportData = new StringBuilder();
            reportData.append("Password Health Report for ").append(new Date()).append("\n\n");
            reportData.append("Overall Security Score: ").append(overallScore).append("%\n\n");
            reportData.append("Total passwords: ").append(totalPasswords).append("\n");
            reportData.append("Weak passwords: ").append(weakPasswords).append("\n");
            reportData.append("Reused passwords: ").append(reusedPasswords).append("\n");
            reportData.append("Passwords not changed in 90+ days: ").append(oldPasswords).append("\n\n");
            
            reportData.append("Password Strength Breakdown:\n");
            reportData.append("- Very Strong: ").append(countPasswordsByStrength(passwords, 90, 100)).append("\n");
            reportData.append("- Strong: ").append(countPasswordsByStrength(passwords, 70, 89)).append("\n");
            reportData.append("- Medium: ").append(countPasswordsByStrength(passwords, 50, 69)).append("\n");
            reportData.append("- Weak: ").append(countPasswordsByStrength(passwords, 25, 49)).append("\n");
            reportData.append("- Very Weak: ").append(countPasswordsByStrength(passwords, 0, 24)).append("\n\n");
            
            reportData.append("Recommendations:\n");
            if (weakPasswords > 0) {
                reportData.append("- Update ").append(weakPasswords).append(" weak passwords to improve security\n");
            }
            if (reusedPasswords > 0) {
                reportData.append("- You have ").append(reusedPasswords).append(" accounts using shared passwords. Create unique passwords for each account.\n");
            }
            if (oldPasswords > 0) {
                reportData.append("- ").append(oldPasswords).append(" passwords haven't been updated in over 90 days. Consider updating them.\n");
            }
            
            // Save report to database
            saveReportToDatabase(userId, overallScore, weakPasswords, reusedPasswords, oldPasswords, reportData.toString(), conn);
            
            // Send email report
            return sendReportEmail(userId, userEmail, overallScore, weakPasswords, reusedPasswords, oldPasswords, reportData.toString());
            
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }
    
    /**
     * Schedule health report generation for all users
     */
    public static void scheduleHealthReports() {
        try (Connection conn = DatabaseUtil.getConnection()) {
            String query = "SELECT id, username, email, report_frequency FROM users";
            try (PreparedStatement stmt = conn.prepareStatement(query)) {
                ResultSet rs = stmt.executeQuery();
                
                while (rs.next()) {
                    int userId = rs.getInt("id");
                    String frequency = rs.getString("report_frequency");
                    
                    // Check if it's time to send a report based on frequency
                    if (shouldGenerateReport(userId, frequency)) {
                        generateHealthReport(userId);
                    }
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
    
    /**
     * Sets the report frequency for a user
     */
    public static void setReportFrequency(int userId, String frequency) {
        try (Connection conn = DatabaseUtil.getConnection()) {
            String query = "UPDATE users SET report_frequency = ? WHERE id = ?";
            try (PreparedStatement stmt = conn.prepareStatement(query)) {
                stmt.setString(1, frequency);
                stmt.setInt(2, userId);
                stmt.executeUpdate();
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
    
    /**
     * Sets the email address for a user
     */
    public static void setUserEmail(int userId, String email) {
        try (Connection conn = DatabaseUtil.getConnection()) {
            String query = "UPDATE users SET email = ? WHERE id = ?";
            try (PreparedStatement stmt = conn.prepareStatement(query)) {
                stmt.setString(1, email);
                stmt.setInt(2, userId);
                stmt.executeUpdate();
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
    
    /**
     * Gets the email address for a user
     */
    private static String getUserEmail(int userId, Connection conn) throws SQLException {
        String query = "SELECT email FROM users WHERE id = ?";
        try (PreparedStatement stmt = conn.prepareStatement(query)) {
            stmt.setInt(1, userId);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return rs.getString("email");
            }
        }
        return null;
    }
    
    /**
     * Gets all passwords for a user
     */
    private static List<PasswordEntry> getPasswordsForUser(int userId, Connection conn) throws SQLException {
        List<PasswordEntry> passwords = new ArrayList<>();
        
        String query = "SELECT id, website, username, encrypted_password, last_used, strength_score FROM passwords WHERE user_id = ?";
        try (PreparedStatement stmt = conn.prepareStatement(query)) {
            stmt.setInt(1, userId);
            ResultSet rs = stmt.executeQuery();
            
            while (rs.next()) {
                PasswordEntry entry = new PasswordEntry();
                entry.id = rs.getInt("id");
                entry.website = rs.getString("website");
                entry.username = rs.getString("username");
                entry.encryptedPassword = rs.getString("encrypted_password");
                entry.lastUsed = rs.getTimestamp("last_used");
                entry.strengthScore = rs.getInt("strength_score");
                
                // If strength score isn't set, analyze the password
                if (entry.strengthScore <= 0) {
                    String decryptedPassword = SecurityUtil.decryptPassword(entry.encryptedPassword, "your-encryption-key");
                    entry.strengthScore = analyzePasswordStrength(decryptedPassword);
                    
                    // Update the database with the analyzed score
                    updatePasswordStrength(entry.id, entry.strengthScore, conn);
                }
                
                passwords.add(entry);
            }
        }
        
        return passwords;
    }
    
    /**
     * Updates the strength score for a password
     */
    private static void updatePasswordStrength(int passwordId, int strength, Connection conn) throws SQLException {
        String query = "UPDATE passwords SET strength_score = ? WHERE id = ?";
        try (PreparedStatement stmt = conn.prepareStatement(query)) {
            stmt.setInt(1, strength);
            stmt.setInt(2, passwordId);
            stmt.executeUpdate();
        }
    }
    
    /**
     * Checks if a password is considered "old"
     */
    private static boolean isPasswordOld(Timestamp lastUsed) {
        if (lastUsed == null) {
            return true; // No timestamp means we consider it old
        }
        
        Calendar calendar = Calendar.getInstance();
        calendar.add(Calendar.DAY_OF_YEAR, -(int)PASSWORD_AGE_WARNING_DAYS);
        Date warningDate = calendar.getTime();
        
        return lastUsed.before(warningDate);
    }
    
    /**
     * Counts passwords within a strength range
     */
    private static int countPasswordsByStrength(List<PasswordEntry> passwords, int minStrength, int maxStrength) {
        int count = 0;
        for (PasswordEntry entry : passwords) {
            if (entry.strengthScore >= minStrength && entry.strengthScore <= maxStrength) {
                count++;
            }
        }
        return count;
    }
    
    /**
     * Analyzes the strength of a password
     */
    private static int analyzePasswordStrength(String password) {
        if (password == null || password.isEmpty()) {
            return 0;
        }
        
        // Calculate a strength score based on multiple factors
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
        // Check for mixed character types (not all sequential)
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
     * Saves a report to the database
     */
    private static void saveReportToDatabase(int userId, int overallScore, int weakPasswords, 
            int reusedPasswords, int oldPasswords, String reportData, Connection conn) throws SQLException {
        
        String query = "INSERT INTO password_health_reports " +
                      "(user_id, report_date, overall_score, weak_passwords, reused_passwords, old_passwords, report_data) " +
                      "VALUES (?, ?, ?, ?, ?, ?, ?)";
        
        try (PreparedStatement stmt = conn.prepareStatement(query)) {
            stmt.setInt(1, userId);
            stmt.setTimestamp(2, new Timestamp(new Date().getTime()));
            stmt.setInt(3, overallScore);
            stmt.setInt(4, weakPasswords);
            stmt.setInt(5, reusedPasswords);
            stmt.setInt(6, oldPasswords);
            stmt.setString(7, reportData);
            stmt.executeUpdate();
        }
    }
    
    /**
     * Checks if it's time to generate a report based on frequency
     */
    private static boolean shouldGenerateReport(int userId, String frequency) {
        if (frequency == null) {
            return false;
        }
        
        try (Connection conn = DatabaseUtil.getConnection()) {
            // Find the last report date
            String query = "SELECT report_date FROM password_health_reports " +
                         "WHERE user_id = ? ORDER BY report_date DESC LIMIT 1";
            
            try (PreparedStatement stmt = conn.prepareStatement(query)) {
                stmt.setInt(1, userId);
                ResultSet rs = stmt.executeQuery();
                
                if (rs.next()) {
                    Timestamp lastReportDate = rs.getTimestamp("report_date");
                    Calendar cal = Calendar.getInstance();
                    cal.setTime(new Date());
                    
                    // Calculate next report date based on frequency
                    Calendar nextReportDate = Calendar.getInstance();
                    nextReportDate.setTime(lastReportDate);
                    
                    switch (frequency.toUpperCase()) {
                        case "WEEKLY":
                            nextReportDate.add(Calendar.WEEK_OF_YEAR, 1);
                            break;
                        case "MONTHLY":
                            nextReportDate.add(Calendar.MONTH, 1);
                            break;
                        case "QUARTERLY":
                            nextReportDate.add(Calendar.MONTH, 3);
                            break;
                        default:
                            return false;
                    }
                    
                    return cal.getTime().after(nextReportDate.getTime());
                }
                
                // No previous report, so generate one
                return true;
            }
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }
    
    /**
     * Sends an email with the password health report
     */
    private static boolean sendReportEmail(int userId, String toEmail, int overallScore, 
            int weakPasswords, int reusedPasswords, int oldPasswords, String reportData) {
        
        try {
            // Set up mail properties
            Properties properties = new Properties();
            properties.put("mail.smtp.host", SMTP_HOST);
            properties.put("mail.smtp.port", SMTP_PORT);
            properties.put("mail.smtp.auth", "true");
            properties.put("mail.smtp.starttls.enable", "true");
            
            // Create session
            Session session = Session.getInstance(properties, new Authenticator() {
                @Override
                protected PasswordAuthentication getPasswordAuthentication() {
                    return new PasswordAuthentication(SMTP_USERNAME, SMTP_PASSWORD);
                }
            });
            
            // Create message
            Message message = new MimeMessage(session);
            message.setFrom(new InternetAddress(FROM_EMAIL));
            message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(toEmail));
            
            // Set subject based on overall score
            String securityLevel = "Good";
            if (overallScore < 50) {
                securityLevel = "Critical";
            } else if (overallScore < 75) {
                securityLevel = "Needs Improvement";
            }
            
            message.setSubject("NHCE Password Security Report - " + securityLevel);
            
            // Create HTML content
            StringBuilder htmlContent = new StringBuilder();
            htmlContent.append("<html><body>");
            htmlContent.append("<h1>Password Health Report</h1>");
            htmlContent.append("<p>Generated on: ").append(new SimpleDateFormat("yyyy-MM-dd HH:mm").format(new Date())).append("</p>");
            
            // Security score with color
            String scoreColor = "#00cc00"; // Green
            if (overallScore < 50) {
                scoreColor = "#cc0000"; // Red
            } else if (overallScore < 75) {
                scoreColor = "#cccc00"; // Yellow
            }
            
            htmlContent.append("<h2>Overall Security Score: <span style='color:").append(scoreColor)
                      .append("'>").append(overallScore).append("%</span></h2>");
            
            // Summary stats
            htmlContent.append("<table border='1' cellpadding='5' style='border-collapse: collapse;'>");
            htmlContent.append("<tr><th>Total Passwords</th><th>Weak Passwords</th><th>Reused Passwords</th><th>Old Passwords</th></tr>");
            htmlContent.append("<tr><td align='center'>").append(reportData.split("Total passwords: ")[1].split("\n")[0])
                      .append("</td><td align='center'>").append(weakPasswords)
                      .append("</td><td align='center'>").append(reusedPasswords)
                      .append("</td><td align='center'>").append(oldPasswords)
                      .append("</td></tr>");
            htmlContent.append("</table>");
            
            // Recommendations
            htmlContent.append("<h2>Recommendations</h2>");
            htmlContent.append("<ul>");
            String[] recommendations = reportData.split("Recommendations:\n")[1].split("\n");
            for (String recommendation : recommendations) {
                if (!recommendation.trim().isEmpty()) {
                    htmlContent.append("<li>").append(recommendation.substring(2)).append("</li>");
                }
            }
            htmlContent.append("</ul>");
            
            htmlContent.append("<p>Log in to the NHCE Personal Data Manager to view more details and improve your security.</p>");
            
            htmlContent.append("</body></html>");
            
            // Set content
            message.setContent(htmlContent.toString(), "text/html");
            
            // Send message
            Transport.send(message);
            
            // Update database to mark report as sent
            try (Connection conn = DatabaseUtil.getConnection()) {
                String query = "UPDATE password_health_reports SET email_sent = 1 " +
                             "WHERE user_id = ? ORDER BY report_date DESC LIMIT 1";
                try (PreparedStatement stmt = conn.prepareStatement(query)) {
                    stmt.setInt(1, userId);
                    stmt.executeUpdate();
                }
            }
            
            return true;
        } catch (MessagingException | SQLException e) {
            e.printStackTrace();
            return false;
        }
    }
    
    /**
     * Helper class to store password data for analysis
     */
    private static class PasswordEntry {
        int id;
        String website;
        String username;
        String encryptedPassword;
        Timestamp lastUsed;
        int strengthScore;
    }
} 