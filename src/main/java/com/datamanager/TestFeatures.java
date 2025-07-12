package com.datamanager;

import java.sql.Connection;
import java.sql.SQLException;
import javax.swing.JOptionPane;
import com.datamanager.util.DatabaseUtil;
import com.datamanager.util.BrowserExtensionUtil;
import com.datamanager.util.PasswordHealthUtil;

/**
 * Test class for new features in the Personal Data Manager.
 * This class provides methods to test the password health report and browser extension features.
 */
public class TestFeatures {
    
    /**
     * Test method for generating a password health report.
     * @param userId The user ID to generate a report for
     */
    public static void testPasswordHealthReport(int userId) {
        try {
            System.out.println("Testing password health report generation for user ID: " + userId);
            
            // Generate a health report
            boolean success = PasswordHealthUtil.generateHealthReport(userId);
            
            if (success) {
                System.out.println("Password health report generated successfully!");
                JOptionPane.showMessageDialog(null, 
                    "Password health report generated successfully! Check your email.",
                    "Report Generated", 
                    JOptionPane.INFORMATION_MESSAGE);
            } else {
                System.err.println("Failed to generate password health report.");
                JOptionPane.showMessageDialog(null, 
                    "Failed to generate password health report. Check the console for details.",
                    "Report Generation Failed", 
                    JOptionPane.ERROR_MESSAGE);
            }
        } catch (Exception e) {
            System.err.println("Error testing password health report: " + e.getMessage());
            e.printStackTrace();
            
            JOptionPane.showMessageDialog(null, 
                "Error: " + e.getMessage(),
                "Test Error", 
                JOptionPane.ERROR_MESSAGE);
        }
    }
    
    /**
     * Test method for the browser extension integration.
     */
    public static void testBrowserExtension() {
        try {
            System.out.println("Testing browser extension integration");
            
            // Stop any existing server
            BrowserExtensionUtil.stopExtensionServer();
            
            // Start the extension server
            BrowserExtensionUtil.startExtensionServer();
            
            // Display the instructions
            String instructions = BrowserExtensionUtil.getExtensionInstructions();
            
            JOptionPane.showMessageDialog(null, 
                "Browser extension server started successfully!\n\n" +
                "Connection information:\n" +
                "- The server is now running on port 45678\n" +
                "- Make sure to keep the application running while using the extension",
                "Extension Server Started", 
                JOptionPane.INFORMATION_MESSAGE);
            
            // Show instructions in a scrollable text area
            javax.swing.JTextArea textArea = new javax.swing.JTextArea(20, 50);
            textArea.setText(instructions);
            textArea.setEditable(false);
            textArea.setCaretPosition(0);
            
            javax.swing.JScrollPane scrollPane = new javax.swing.JScrollPane(textArea);
            
            JOptionPane.showMessageDialog(null, 
                scrollPane,
                "Browser Extension Setup Instructions", 
                JOptionPane.INFORMATION_MESSAGE);
            
        } catch (Exception e) {
            System.err.println("Error testing browser extension: " + e.getMessage());
            e.printStackTrace();
            
            JOptionPane.showMessageDialog(null, 
                "Error: " + e.getMessage(),
                "Test Error", 
                JOptionPane.ERROR_MESSAGE);
        }
    }
    
    /**
     * Update email and report frequency for a user
     * @param userId The user ID to update
     * @param email The email address to set
     * @param frequency The report frequency (WEEKLY, MONTHLY, QUARTERLY)
     */
    public static void updateUserReportSettings(int userId, String email, String frequency) {
        try {
            System.out.println("Updating report settings for user ID: " + userId);
            System.out.println("Email: " + email);
            System.out.println("Frequency: " + frequency);
            
            // Update user email
            PasswordHealthUtil.setUserEmail(userId, email);
            
            // Update report frequency
            PasswordHealthUtil.setReportFrequency(userId, frequency);
            
            System.out.println("User report settings updated successfully!");
            JOptionPane.showMessageDialog(null, 
                "User report settings updated successfully!",
                "Settings Updated", 
                JOptionPane.INFORMATION_MESSAGE);
        } catch (Exception e) {
            System.err.println("Error updating user report settings: " + e.getMessage());
            e.printStackTrace();
            
            JOptionPane.showMessageDialog(null, 
                "Error: " + e.getMessage(),
                "Update Error", 
                JOptionPane.ERROR_MESSAGE);
        }
    }
    
    /**
     * Test method to modify sample URL patterns for auto-fill
     * @param userId The user ID
     */
    public static void setupSampleUrlPatterns(int userId) {
        try {
            System.out.println("Setting up sample URL patterns for user ID: " + userId);
            Connection conn = DatabaseUtil.getConnection();
            
            // You would add code here to set up URL patterns for existing passwords
            // This is just a placeholder
            JOptionPane.showMessageDialog(null, 
                "In a real implementation, this would set up sample URL patterns for your passwords.\n" +
                "You can use the UI to set URL patterns for each password entry.",
                "Sample Setup", 
                JOptionPane.INFORMATION_MESSAGE);
            
        } catch (SQLException e) {
            System.err.println("Error setting up sample URL patterns: " + e.getMessage());
            e.printStackTrace();
            
            JOptionPane.showMessageDialog(null, 
                "Error: " + e.getMessage(),
                "Setup Error", 
                JOptionPane.ERROR_MESSAGE);
        }
    }
    
    public static void main(String[] args) {
        // Quick test from command line
        try {
            // Initialize database connection
            DatabaseUtil.getConnection();
            
            // Create GUI for testing
            javax.swing.JFrame frame = new javax.swing.JFrame("Feature Test");
            frame.setSize(400, 300);
            frame.setDefaultCloseOperation(javax.swing.JFrame.EXIT_ON_CLOSE);
            
            javax.swing.JPanel panel = new javax.swing.JPanel();
            panel.setLayout(new java.awt.GridLayout(0, 1, 10, 10));
            
            javax.swing.JLabel userIdLabel = new javax.swing.JLabel("User ID:");
            javax.swing.JTextField userIdField = new javax.swing.JTextField("1");
            
            javax.swing.JButton healthReportButton = new javax.swing.JButton("Test Password Health Report");
            healthReportButton.addActionListener(e -> {
                try {
                    int userId = Integer.parseInt(userIdField.getText());
                    testPasswordHealthReport(userId);
                } catch (NumberFormatException ex) {
                    JOptionPane.showMessageDialog(frame, "Please enter a valid user ID", "Error", JOptionPane.ERROR_MESSAGE);
                }
            });
            
            javax.swing.JButton browserExtensionButton = new javax.swing.JButton("Test Browser Extension");
            browserExtensionButton.addActionListener(e -> testBrowserExtension());
            
            javax.swing.JButton updateSettingsButton = new javax.swing.JButton("Update Report Settings");
            updateSettingsButton.addActionListener(e -> {
                try {
                    int userId = Integer.parseInt(userIdField.getText());
                    
                    javax.swing.JPanel settingsPanel = new javax.swing.JPanel(new java.awt.GridLayout(0, 2, 5, 5));
                    settingsPanel.add(new javax.swing.JLabel("Email:"));
                    javax.swing.JTextField emailField = new javax.swing.JTextField("user@example.com");
                    settingsPanel.add(emailField);
                    
                    settingsPanel.add(new javax.swing.JLabel("Frequency:"));
                    javax.swing.JComboBox<String> frequencyCombo = new javax.swing.JComboBox<>(
                        new String[]{"WEEKLY", "MONTHLY", "QUARTERLY"});
                    settingsPanel.add(frequencyCombo);
                    
                    int result = JOptionPane.showConfirmDialog(frame, settingsPanel, 
                        "Update Report Settings", JOptionPane.OK_CANCEL_OPTION);
                        
                    if (result == JOptionPane.OK_OPTION) {
                        String email = emailField.getText();
                        String frequency = (String) frequencyCombo.getSelectedItem();
                        updateUserReportSettings(userId, email, frequency);
                    }
                } catch (NumberFormatException ex) {
                    JOptionPane.showMessageDialog(frame, "Please enter a valid user ID", "Error", JOptionPane.ERROR_MESSAGE);
                }
            });
            
            javax.swing.JButton setupPatternsButton = new javax.swing.JButton("Setup URL Patterns");
            setupPatternsButton.addActionListener(e -> {
                try {
                    int userId = Integer.parseInt(userIdField.getText());
                    setupSampleUrlPatterns(userId);
                } catch (NumberFormatException ex) {
                    JOptionPane.showMessageDialog(frame, "Please enter a valid user ID", "Error", JOptionPane.ERROR_MESSAGE);
                }
            });
            
            panel.add(userIdLabel);
            panel.add(userIdField);
            panel.add(healthReportButton);
            panel.add(browserExtensionButton);
            panel.add(updateSettingsButton);
            panel.add(setupPatternsButton);
            
            frame.add(panel);
            frame.setVisible(true);
            
        } catch (Exception e) {
            System.err.println("Error in test application: " + e.getMessage());
            e.printStackTrace();
        }
    }
} 