package com.datamanager;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.sql.*;
import com.datamanager.util.DatabaseUtil;
import com.datamanager.util.SecurityUtil;

public class LoginFrame extends JFrame {
    private JTextField usernameField;
    private JPasswordField passwordField;
    private JButton loginButton;
    private JButton registerButton;

    public LoginFrame() {
        setTitle("NHCE Personal Data Manager - Login");
        setSize(400, 300);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);
        
        // Create main panel
        JPanel mainPanel = new JPanel();
        mainPanel.setLayout(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);

        // Username field
        gbc.gridx = 0;
        gbc.gridy = 0;
        mainPanel.add(new JLabel("Username:"), gbc);

        gbc.gridx = 1;
        usernameField = new JTextField(20);
        mainPanel.add(usernameField, gbc);

        // Password field
        gbc.gridx = 0;
        gbc.gridy = 1;
        mainPanel.add(new JLabel("Password:"), gbc);

        gbc.gridx = 1;
        passwordField = new JPasswordField(20);
        mainPanel.add(passwordField, gbc);

        // Button panel
        JPanel buttonPanel = new JPanel(new FlowLayout());
        loginButton = new JButton("Login");
        registerButton = new JButton("Register");
        buttonPanel.add(loginButton);
        buttonPanel.add(registerButton);

        gbc.gridx = 0;
        gbc.gridy = 2;
        gbc.gridwidth = 2;
        mainPanel.add(buttonPanel, gbc);

        // Login button action
        loginButton.addActionListener(e -> handleLogin());

        // Register button action
        registerButton.addActionListener(e -> handleRegistration());

        add(mainPanel);
        setVisible(true);
    }

    private void handleLogin() {
        String username = usernameField.getText();
        String password = new String(passwordField.getPassword());

        if (!validateInput(username, password)) {
            return;
        }

        try (Connection conn = DatabaseUtil.getConnection()) {
            String query = "SELECT id, password_hash, salt FROM users WHERE username = ?";
            try (PreparedStatement pstmt = conn.prepareStatement(query)) {
                pstmt.setString(1, username);
                ResultSet rs = pstmt.executeQuery();

                if (rs.next()) {
                    String storedHash = rs.getString("password_hash");
                    String salt = rs.getString("salt");
                    int userId = rs.getInt("id");

                    if (SecurityUtil.verifyPassword(password, salt, storedHash)) {
                        new DashboardFrame(userId);
                        dispose();
                    } else {
                        showError("Invalid username or password!");
                    }
                } else {
                    showError("User not found!");
                }
            }
        } catch (SQLException ex) {
            showError("Database error: " + ex.getMessage());
        }
    }

    private void handleRegistration() {
        String username = usernameField.getText();
        String password = new String(passwordField.getPassword());

        if (!validateInput(username, password)) {
            return;
        }

        try (Connection conn = DatabaseUtil.getConnection()) {
            // Check if username already exists
            String checkQuery = "SELECT id FROM users WHERE username = ?";
            try (PreparedStatement checkStmt = conn.prepareStatement(checkQuery)) {
                checkStmt.setString(1, username);
                if (checkStmt.executeQuery().next()) {
                    showError("Username already exists!");
                    return;
                }
            }

            // Create new user
            String salt = SecurityUtil.generateSalt();
            String passwordHash = SecurityUtil.hashPassword(password, salt);

            String insertQuery = "INSERT INTO users (username, password_hash, salt) VALUES (?, ?, ?)";
            try (PreparedStatement pstmt = conn.prepareStatement(insertQuery)) {
                pstmt.setString(1, username);
                pstmt.setString(2, passwordHash);
                pstmt.setString(3, salt);
                pstmt.executeUpdate();
                
                JOptionPane.showMessageDialog(this,
                    "Registration successful! Please login.",
                    "Success",
                    JOptionPane.INFORMATION_MESSAGE);
            }
        } catch (SQLException ex) {
            showError("Database error: " + ex.getMessage());
        }
    }

    private boolean validateInput(String username, String password) {
        if (username.trim().isEmpty() || password.trim().isEmpty()) {
            showError("Username and password cannot be empty!");
            return false;
        }
        
        if (username.length() < 3 || username.length() > 50) {
            showError("Username must be between 3 and 50 characters!");
            return false;
        }
        
        if (password.length() < 6) {
            showError("Password must be at least 6 characters long!");
            return false;
        }
        
        return true;
    }

    private void showError(String message) {
        JOptionPane.showMessageDialog(this,
            message,
            "Error",
            JOptionPane.ERROR_MESSAGE);
    }
} 