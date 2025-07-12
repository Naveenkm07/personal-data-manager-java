package com.datamanager;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import javax.swing.text.*;
import javax.swing.text.rtf.RTFEditorKit;
import java.awt.*;
import java.awt.event.*;
import java.io.File;
import java.io.StringWriter;
import java.lang.reflect.Field;
import java.sql.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.Date;
import java.util.List;
import com.datamanager.util.DatabaseUtil;
import com.datamanager.util.SecurityUtil;
import com.datamanager.util.BackupUtil;
import com.datamanager.util.BrowserExtensionUtil;
import com.datamanager.util.PasswordHealthUtil;
import com.datamanager.util.DataTransferUtil;
import com.datamanager.util.SecureNotesUtil;

public class DashboardFrame extends JFrame {
    private JTabbedPane tabbedPane;
    private final int userId;
    private DefaultTableModel passwordTableModel;
    private DefaultTableModel contactsTableModel;
    private DefaultListModel<String> taskListModel;

    public DashboardFrame(int userId) {
        this.userId = userId;
        setTitle("NHCE Personal Data Manager - Dashboard");
        setSize(800, 600);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);

        // Create tabbed pane
        tabbedPane = new JTabbedPane();
        
        // Add tabs
        tabbedPane.addTab("Dashboard", createDashboardPanel());
        tabbedPane.addTab("Passwords", createPasswordPanel());
        tabbedPane.addTab("Tasks", createTaskPanel());
        tabbedPane.addTab("Contacts", createContactPanel());
        tabbedPane.addTab("Analytics", createAnalyticsPanel());
        tabbedPane.addTab("Browser Integration", createBrowserIntegrationPanel());
        tabbedPane.addTab("Password Health", createPasswordHealthPanel());
        tabbedPane.addTab("Secure Notes", createSecureNotesPanel());
        tabbedPane.addTab("Data Import/Export", createDataTransferPanel());
        tabbedPane.addTab("Backup/Restore", createBackupPanel());

        // Add logout button and theme toggle
        JPanel mainPanel = new JPanel(new BorderLayout());
        mainPanel.add(tabbedPane, BorderLayout.CENTER);
        
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        
        // Add theme toggle button
        JToggleButton themeToggle = new JToggleButton("Dark Mode");
        themeToggle.addActionListener(e -> toggleDarkMode(themeToggle.isSelected()));
        buttonPanel.add(themeToggle);
        
        JButton logoutButton = new JButton("Logout");
        logoutButton.addActionListener(e -> {
            DatabaseUtil.closeConnection();
            dispose();
            new LoginFrame();
        });
        buttonPanel.add(logoutButton);
        
        mainPanel.add(buttonPanel, BorderLayout.SOUTH);

        add(mainPanel);
        loadUserData();
        
        // Load user theme preferences
        loadUserPreferences();
        
        // Start the browser extension server if enabled
        try {
            BrowserExtensionUtil.startExtensionServer();
        } catch (Exception e) {
            showError("Failed to start browser extension server: " + e.getMessage());
        }
        
        setVisible(true);
    }

    private void loadUserData() {
        loadPasswords();
        loadTasks();
        loadContacts();
        loadNotes();
    }

    private JPanel createDashboardPanel() {
        JPanel panel = new JPanel();
        panel.setLayout(new BorderLayout());
        
        JLabel welcomeLabel = new JLabel("Welcome to NHCE Personal Data Manager", SwingConstants.CENTER);
        welcomeLabel.setFont(new Font("Arial", Font.BOLD, 24));
        panel.add(welcomeLabel, BorderLayout.CENTER);
        
        return panel;
    }

    private JPanel createPasswordPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        
        // Password table
        String[] columnNames = {"Website", "Username", "Password", "Auto-Fill"};
        passwordTableModel = new DefaultTableModel(columnNames, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return column == 3; // Only Auto-Fill column is editable
            }
            
            @Override
            public Class<?> getColumnClass(int columnIndex) {
                return columnIndex == 3 ? Boolean.class : String.class;
            }
        };
        JTable table = new JTable(passwordTableModel);
        JScrollPane scrollPane = new JScrollPane(table);
        
        // Add listener for auto-fill toggle
        table.getModel().addTableModelListener(e -> {
            if (e.getColumn() == 3) { // Auto-Fill column
                int row = e.getFirstRow();
                String website = (String) passwordTableModel.getValueAt(row, 0);
                String username = (String) passwordTableModel.getValueAt(row, 1);
                boolean autoFillEnabled = (boolean) passwordTableModel.getValueAt(row, 3);
                
                updateAutoFillSetting(website, username, autoFillEnabled);
            }
        });
        
        // Buttons panel
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton addButton = new JButton("Add Password");
        JButton deleteButton = new JButton("Delete Password");
        JButton viewButton = new JButton("View Password");
        JButton generateButton = new JButton("Generate Password");
        JButton urlPatternButton = new JButton("Edit URL Pattern");
        
        buttonPanel.add(addButton);
        buttonPanel.add(deleteButton);
        buttonPanel.add(viewButton);
        buttonPanel.add(generateButton);
        buttonPanel.add(urlPatternButton);

        addButton.addActionListener(e -> addPassword());
        deleteButton.addActionListener(e -> deletePassword(table.getSelectedRow()));
        viewButton.addActionListener(e -> viewPassword(table.getSelectedRow()));
        generateButton.addActionListener(e -> generatePassword());
        urlPatternButton.addActionListener(e -> editUrlPattern(table.getSelectedRow()));
        
        panel.add(scrollPane, BorderLayout.CENTER);
        panel.add(buttonPanel, BorderLayout.SOUTH);
        
        return panel;
    }

    private void addPassword() {
        JPanel panel = new JPanel(new GridLayout(3, 2));
        JTextField websiteField = new JTextField();
        JTextField usernameField = new JTextField();
        JPasswordField passwordField = new JPasswordField();
        
        panel.add(new JLabel("Website:"));
        panel.add(websiteField);
        panel.add(new JLabel("Username:"));
        panel.add(usernameField);
        panel.add(new JLabel("Password:"));
        panel.add(passwordField);
        
        int result = JOptionPane.showConfirmDialog(this, panel, "Add Password",
            JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
            
        if (result == JOptionPane.OK_OPTION) {
            String website = websiteField.getText();
            String username = usernameField.getText();
            String password = new String(passwordField.getPassword());
            
            if (website.isEmpty() || username.isEmpty() || password.isEmpty()) {
                showError("All fields are required!");
                return;
            }
            
            try (Connection conn = DatabaseUtil.getConnection()) {
                String encryptedPassword = SecurityUtil.encryptPassword(password, "your-encryption-key");
                String query = "INSERT INTO passwords (user_id, website, username, encrypted_password) VALUES (?, ?, ?, ?)";
                
                try (PreparedStatement pstmt = conn.prepareStatement(query)) {
                    pstmt.setInt(1, userId);
                    pstmt.setString(2, website);
                    pstmt.setString(3, username);
                    pstmt.setString(4, encryptedPassword);
                    pstmt.executeUpdate();
                    loadPasswords();
                }
            } catch (SQLException ex) {
                showError("Error saving password: " + ex.getMessage());
            }
        }
    }

    private void deletePassword(int selectedRow) {
        if (selectedRow == -1) {
            showError("Please select a password to delete!");
            return;
        }
        
        int confirm = JOptionPane.showConfirmDialog(this,
            "Are you sure you want to delete this password?",
            "Confirm Delete",
            JOptionPane.YES_NO_OPTION);
            
        if (confirm == JOptionPane.YES_OPTION) {
            String website = (String) passwordTableModel.getValueAt(selectedRow, 0);
            String username = (String) passwordTableModel.getValueAt(selectedRow, 1);
            
            try (Connection conn = DatabaseUtil.getConnection()) {
                String query = "DELETE FROM passwords WHERE user_id = ? AND website = ? AND username = ?";
                try (PreparedStatement pstmt = conn.prepareStatement(query)) {
                    pstmt.setInt(1, userId);
                    pstmt.setString(2, website);
                    pstmt.setString(3, username);
                    pstmt.executeUpdate();
                    loadPasswords();
                }
            } catch (SQLException ex) {
                showError("Error deleting password: " + ex.getMessage());
            }
        }
    }

    private void viewPassword(int selectedRow) {
        if (selectedRow == -1) {
            showError("Please select a password to view!");
            return;
        }
        
        String website = (String) passwordTableModel.getValueAt(selectedRow, 0);
        String username = (String) passwordTableModel.getValueAt(selectedRow, 1);
        
        try (Connection conn = DatabaseUtil.getConnection()) {
            String query = "SELECT encrypted_password FROM passwords WHERE user_id = ? AND website = ? AND username = ?";
            try (PreparedStatement pstmt = conn.prepareStatement(query)) {
                pstmt.setInt(1, userId);
                pstmt.setString(2, website);
                pstmt.setString(3, username);
                
                ResultSet rs = pstmt.executeQuery();
                if (rs.next()) {
                    String encryptedPassword = rs.getString("encrypted_password");
                    String decryptedPassword = SecurityUtil.decryptPassword(encryptedPassword, "your-encryption-key");
                    
                    JOptionPane.showMessageDialog(this,
                        String.format("Password for %s: %s", website, decryptedPassword),
                        "Password",
                        JOptionPane.INFORMATION_MESSAGE);
                }
            }
        } catch (SQLException ex) {
            showError("Error retrieving password: " + ex.getMessage());
        }
    }

    private void loadPasswords() {
        passwordTableModel.setRowCount(0);
        try (Connection conn = DatabaseUtil.getConnection()) {
            String query = "SELECT id, website, username, auto_fill_enabled FROM passwords WHERE user_id = ?";
            try (PreparedStatement pstmt = conn.prepareStatement(query)) {
                pstmt.setInt(1, userId);
                ResultSet rs = pstmt.executeQuery();
                
                while (rs.next()) {
                    boolean autoFillEnabled = rs.getInt("auto_fill_enabled") == 1;
                    passwordTableModel.addRow(new Object[]{
                        rs.getString("website"),
                        rs.getString("username"),
                        "********",
                        autoFillEnabled
                    });
                }
            }
        } catch (SQLException ex) {
            showError("Error loading passwords: " + ex.getMessage());
        }
    }

    private void loadTasks() {
        // TODO: Implement task loading from database
    }

    private void loadContacts() {
        contactsTableModel.setRowCount(0);
        try (Connection conn = DatabaseUtil.getConnection()) {
            String query = "SELECT name, phone, email, company, category, is_favorite FROM contacts WHERE user_id = ?";
            try (PreparedStatement pstmt = conn.prepareStatement(query)) {
                pstmt.setInt(1, userId);
                ResultSet rs = pstmt.executeQuery();
                
                while (rs.next()) {
                    contactsTableModel.addRow(new Object[]{
                        rs.getString("name"),
                        rs.getString("phone"),
                        rs.getString("email"),
                        rs.getString("company"),
                        rs.getString("category"),
                        rs.getInt("is_favorite") == 1 ? "★" : ""
                    });
                }
            }
        } catch (SQLException ex) {
            showError("Error loading contacts: " + ex.getMessage());
        }
    }

    private void showError(String message) {
        JOptionPane.showMessageDialog(this,
            message,
            "Error",
            JOptionPane.ERROR_MESSAGE);
    }

    private JPanel createTaskPanel() {
        return new TaskPanel(this, userId);
    }

    private JPanel createContactPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        
        // Add search panel at the top
        JPanel searchPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JTextField searchField = new JTextField(20);
        JButton searchButton = new JButton("Search");
        searchPanel.add(new JLabel("Search Contacts: "));
        searchPanel.add(searchField);
        searchPanel.add(searchButton);
        JButton resetButton = new JButton("Show All");
        searchPanel.add(resetButton);
        panel.add(searchPanel, BorderLayout.NORTH);
        
        // Contacts table with more columns
        String[] columnNames = {"Name", "Phone", "Email", "Company", "Category", "Favorite"};
        contactsTableModel = new DefaultTableModel(columnNames, 0);
        JTable table = new JTable(contactsTableModel);
        JScrollPane scrollPane = new JScrollPane(table);
        
        // Buttons panel
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton addButton = new JButton("Add Contact");
        JButton editButton = new JButton("Edit Contact");
        JButton deleteButton = new JButton("Delete Contact");
        JButton detailsButton = new JButton("View Details");
        
        buttonPanel.add(addButton);
        buttonPanel.add(editButton);
        buttonPanel.add(deleteButton);
        buttonPanel.add(detailsButton);
        
        addButton.addActionListener(e -> addContact());
        editButton.addActionListener(e -> editContact(table.getSelectedRow()));
        deleteButton.addActionListener(e -> deleteContact(table.getSelectedRow()));
        detailsButton.addActionListener(e -> viewContactDetails(table.getSelectedRow()));
        
        // Add search functionality
        searchButton.addActionListener(e -> searchContacts(searchField.getText()));
        resetButton.addActionListener(e -> loadContacts());
        
        panel.add(scrollPane, BorderLayout.CENTER);
        panel.add(buttonPanel, BorderLayout.SOUTH);
        
        return panel;
    }

    private void addContact() {
        // Create a panel with a grid layout for the form
        JPanel panel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(2, 2, 2, 2);
        gbc.fill = GridBagConstraints.HORIZONTAL;
        
        // Basic fields
        JTextField nameField = new JTextField(20);
        JTextField phoneField = new JTextField(20);
        JTextField emailField = new JTextField(20);
        
        // Additional fields
        JTextField addressField = new JTextField(20);
        JTextField companyField = new JTextField(20);
        JTextField jobTitleField = new JTextField(20);
        JTextField websiteField = new JTextField(20);
        JTextField categoryField = new JTextField(20);
        JTextArea notesArea = new JTextArea(4, 20);
        notesArea.setLineWrap(true);
        JScrollPane notesScrollPane = new JScrollPane(notesArea);
        JCheckBox favoriteCheckBox = new JCheckBox("Mark as Favorite");
        
        // Date picker for birthday
        JTextField birthdayField = new JTextField(10);
        JButton datePickerButton = new JButton("...");
        datePickerButton.addActionListener(e -> {
            JOptionPane.showMessageDialog(this,
                "Enter date in format: YYYY-MM-DD",
                "Date Format",
                JOptionPane.INFORMATION_MESSAGE);
        });
        
        JPanel birthdayPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        birthdayPanel.add(birthdayField);
        birthdayPanel.add(datePickerButton);
        
        // Add components to the panel
        gbc.gridx = 0;
        gbc.gridy = 0;
        panel.add(new JLabel("Name:*"), gbc);
        
        gbc.gridx = 1;
        panel.add(nameField, gbc);
        
        gbc.gridx = 0;
        gbc.gridy = 1;
        panel.add(new JLabel("Phone:"), gbc);
        
        gbc.gridx = 1;
        panel.add(phoneField, gbc);
        
        gbc.gridx = 0;
        gbc.gridy = 2;
        panel.add(new JLabel("Email:"), gbc);
        
        gbc.gridx = 1;
        panel.add(emailField, gbc);
        
        gbc.gridx = 0;
        gbc.gridy = 3;
        panel.add(new JLabel("Address:"), gbc);
        
        gbc.gridx = 1;
        panel.add(addressField, gbc);
        
        gbc.gridx = 0;
        gbc.gridy = 4;
        panel.add(new JLabel("Birthday:"), gbc);
        
        gbc.gridx = 1;
        panel.add(birthdayPanel, gbc);
        
        gbc.gridx = 0;
        gbc.gridy = 5;
        panel.add(new JLabel("Company:"), gbc);
        
        gbc.gridx = 1;
        panel.add(companyField, gbc);
        
        gbc.gridx = 0;
        gbc.gridy = 6;
        panel.add(new JLabel("Job Title:"), gbc);
        
        gbc.gridx = 1;
        panel.add(jobTitleField, gbc);
        
        gbc.gridx = 0;
        gbc.gridy = 7;
        panel.add(new JLabel("Website:"), gbc);
        
        gbc.gridx = 1;
        panel.add(websiteField, gbc);
        
        gbc.gridx = 0;
        gbc.gridy = 8;
        panel.add(new JLabel("Category:"), gbc);
        
        gbc.gridx = 1;
        panel.add(categoryField, gbc);
        
        gbc.gridx = 0;
        gbc.gridy = 9;
        panel.add(new JLabel("Notes:"), gbc);
        
        gbc.gridx = 1;
        panel.add(notesScrollPane, gbc);
        
        gbc.gridx = 1;
        gbc.gridy = 10;
        panel.add(favoriteCheckBox, gbc);
        
        // Create JScrollPane to handle large form
        JScrollPane scrollPane = new JScrollPane(panel);
        scrollPane.setPreferredSize(new Dimension(400, 400));
        
        int result = JOptionPane.showConfirmDialog(this, scrollPane, "Add Contact",
            JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
            
        if (result == JOptionPane.OK_OPTION) {
            String name = nameField.getText();
            String phone = phoneField.getText();
            String email = emailField.getText();
            String address = addressField.getText();
            String birthday = birthdayField.getText();
            String company = companyField.getText();
            String jobTitle = jobTitleField.getText();
            String website = websiteField.getText();
            String category = categoryField.getText();
            String notes = notesArea.getText();
            boolean isFavorite = favoriteCheckBox.isSelected();
            
            if (name.trim().isEmpty()) {
                showError("Contact name cannot be empty!");
                return;
            }
            
            try (Connection conn = DatabaseUtil.getConnection()) {
                String query = "INSERT INTO contacts (user_id, name, phone, email, address, birthday, " +
                               "company, job_title, website, notes, category, is_favorite) " +
                               "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
                
                try (PreparedStatement pstmt = conn.prepareStatement(query)) {
                    pstmt.setInt(1, userId);
                    pstmt.setString(2, name);
                    pstmt.setString(3, phone);
                    pstmt.setString(4, email);
                    pstmt.setString(5, address);
                    
                    // Handle birthday
                    if (birthday != null && !birthday.trim().isEmpty()) {
                        try {
                            java.sql.Date sqlDate = java.sql.Date.valueOf(birthday);
                            pstmt.setDate(6, sqlDate);
                        } catch (IllegalArgumentException e) {
                            pstmt.setNull(6, java.sql.Types.DATE);
                            JOptionPane.showMessageDialog(this,
                                "Birthday format incorrect. It will be ignored.",
                                "Warning",
                                JOptionPane.WARNING_MESSAGE);
                        }
                    } else {
                        pstmt.setNull(6, java.sql.Types.DATE);
                    }
                    
                    pstmt.setString(7, company);
                    pstmt.setString(8, jobTitle);
                    pstmt.setString(9, website);
                    pstmt.setString(10, notes);
                    pstmt.setString(11, category);
                    pstmt.setInt(12, isFavorite ? 1 : 0);
                    
                    pstmt.executeUpdate();
                    loadContacts();
                    
                    JOptionPane.showMessageDialog(this,
                        "Contact added successfully!",
                        "Success",
                        JOptionPane.INFORMATION_MESSAGE);
                }
            } catch (SQLException ex) {
                showError("Error adding contact: " + ex.getMessage());
            }
        }
    }
    
    private void editContact(int selectedRow) {
        if (selectedRow == -1) {
            showError("Please select a contact to edit!");
            return;
        }
        
        String name = (String) contactsTableModel.getValueAt(selectedRow, 0);
        
        try (Connection conn = DatabaseUtil.getConnection()) {
            String query = "SELECT * FROM contacts WHERE user_id = ? AND name = ?";
            try (PreparedStatement pstmt = conn.prepareStatement(query)) {
                pstmt.setInt(1, userId);
                pstmt.setString(2, name);
                
                ResultSet rs = pstmt.executeQuery();
                if (rs.next()) {
                    // Create a panel with a grid layout for the form
                    JPanel panel = new JPanel(new GridBagLayout());
                    GridBagConstraints gbc = new GridBagConstraints();
                    gbc.insets = new Insets(2, 2, 2, 2);
                    gbc.fill = GridBagConstraints.HORIZONTAL;
                    
                    // Basic fields
                    JTextField nameField = new JTextField(rs.getString("name"), 20);
                    JTextField phoneField = new JTextField(rs.getString("phone") != null ? rs.getString("phone") : "", 20);
                    JTextField emailField = new JTextField(rs.getString("email") != null ? rs.getString("email") : "", 20);
                    
                    // Additional fields
                    JTextField addressField = new JTextField(rs.getString("address") != null ? rs.getString("address") : "", 20);
                    JTextField companyField = new JTextField(rs.getString("company") != null ? rs.getString("company") : "", 20);
                    JTextField jobTitleField = new JTextField(rs.getString("job_title") != null ? rs.getString("job_title") : "", 20);
                    JTextField websiteField = new JTextField(rs.getString("website") != null ? rs.getString("website") : "", 20);
                    JTextField categoryField = new JTextField(rs.getString("category") != null ? rs.getString("category") : "", 20);
                    JTextArea notesArea = new JTextArea(rs.getString("notes") != null ? rs.getString("notes") : "", 4, 20);
                    notesArea.setLineWrap(true);
                    JScrollPane notesScrollPane = new JScrollPane(notesArea);
                    JCheckBox favoriteCheckBox = new JCheckBox("Mark as Favorite", rs.getInt("is_favorite") == 1);
                    
                    // Date picker for birthday
                    JTextField birthdayField = new JTextField(10);
                    java.sql.Date birthday = rs.getDate("birthday");
                    if (birthday != null) {
                        birthdayField.setText(birthday.toString());
                    }
                    
                    JButton datePickerButton = new JButton("...");
                    datePickerButton.addActionListener(e -> {
                        JOptionPane.showMessageDialog(this,
                            "Enter date in format: YYYY-MM-DD",
                            "Date Format",
                            JOptionPane.INFORMATION_MESSAGE);
                    });
                    
                    JPanel birthdayPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
                    birthdayPanel.add(birthdayField);
                    birthdayPanel.add(datePickerButton);
                    
                    // Add components to the panel
                    gbc.gridx = 0;
                    gbc.gridy = 0;
                    panel.add(new JLabel("Name:*"), gbc);
                    
                    gbc.gridx = 1;
                    panel.add(nameField, gbc);
                    
                    gbc.gridx = 0;
                    gbc.gridy = 1;
                    panel.add(new JLabel("Phone:"), gbc);
                    
                    gbc.gridx = 1;
                    panel.add(phoneField, gbc);
                    
                    gbc.gridx = 0;
                    gbc.gridy = 2;
                    panel.add(new JLabel("Email:"), gbc);
                    
                    gbc.gridx = 1;
                    panel.add(emailField, gbc);
                    
                    gbc.gridx = 0;
                    gbc.gridy = 3;
                    panel.add(new JLabel("Address:"), gbc);
                    
                    gbc.gridx = 1;
                    panel.add(addressField, gbc);
                    
                    gbc.gridx = 0;
                    gbc.gridy = 4;
                    panel.add(new JLabel("Birthday:"), gbc);
                    
                    gbc.gridx = 1;
                    panel.add(birthdayPanel, gbc);
                    
                    gbc.gridx = 0;
                    gbc.gridy = 5;
                    panel.add(new JLabel("Company:"), gbc);
                    
                    gbc.gridx = 1;
                    panel.add(companyField, gbc);
                    
                    gbc.gridx = 0;
                    gbc.gridy = 6;
                    panel.add(new JLabel("Job Title:"), gbc);
                    
                    gbc.gridx = 1;
                    panel.add(jobTitleField, gbc);
                    
                    gbc.gridx = 0;
                    gbc.gridy = 7;
                    panel.add(new JLabel("Website:"), gbc);
                    
                    gbc.gridx = 1;
                    panel.add(websiteField, gbc);
                    
                    gbc.gridx = 0;
                    gbc.gridy = 8;
                    panel.add(new JLabel("Category:"), gbc);
                    
                    gbc.gridx = 1;
                    panel.add(categoryField, gbc);
                    
                    gbc.gridx = 0;
                    gbc.gridy = 9;
                    panel.add(new JLabel("Notes:"), gbc);
                    
                    gbc.gridx = 1;
                    panel.add(notesScrollPane, gbc);
                    
                    gbc.gridx = 1;
                    gbc.gridy = 10;
                    panel.add(favoriteCheckBox, gbc);
                    
                    // Create JScrollPane to handle large form
                    JScrollPane scrollPane = new JScrollPane(panel);
                    scrollPane.setPreferredSize(new Dimension(400, 400));
                    
                    int result = JOptionPane.showConfirmDialog(this, scrollPane, "Edit Contact",
                        JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
                        
                    if (result == JOptionPane.OK_OPTION) {
                        String newName = nameField.getText();
                        String newPhone = phoneField.getText();
                        String newEmail = emailField.getText();
                        String newAddress = addressField.getText();
                        String newBirthday = birthdayField.getText();
                        String newCompany = companyField.getText();
                        String newJobTitle = jobTitleField.getText();
                        String newWebsite = websiteField.getText();
                        String newCategory = categoryField.getText();
                        String newNotes = notesArea.getText();
                        boolean newIsFavorite = favoriteCheckBox.isSelected();
                        
                        if (newName.trim().isEmpty()) {
                            showError("Contact name cannot be empty!");
                            return;
                        }
                        
                        String updateQuery = "UPDATE contacts SET name = ?, phone = ?, email = ?, " +
                                           "address = ?, birthday = ?, company = ?, job_title = ?, " +
                                           "website = ?, notes = ?, category = ?, is_favorite = ? " +
                                           "WHERE user_id = ? AND name = ?";
                                           
                        try (PreparedStatement updateStmt = conn.prepareStatement(updateQuery)) {
                            updateStmt.setString(1, newName);
                            updateStmt.setString(2, newPhone);
                            updateStmt.setString(3, newEmail);
                            updateStmt.setString(4, newAddress);
                            
                            // Handle birthday
                            if (newBirthday != null && !newBirthday.trim().isEmpty()) {
                                try {
                                    java.sql.Date sqlDate = java.sql.Date.valueOf(newBirthday);
                                    updateStmt.setDate(5, sqlDate);
                                } catch (IllegalArgumentException e) {
                                    updateStmt.setNull(5, java.sql.Types.DATE);
                                    JOptionPane.showMessageDialog(this,
                                        "Birthday format incorrect. It will be ignored.",
                                        "Warning",
                                        JOptionPane.WARNING_MESSAGE);
                                }
                            } else {
                                updateStmt.setNull(5, java.sql.Types.DATE);
                            }
                            
                            updateStmt.setString(6, newCompany);
                            updateStmt.setString(7, newJobTitle);
                            updateStmt.setString(8, newWebsite);
                            updateStmt.setString(9, newNotes);
                            updateStmt.setString(10, newCategory);
                            updateStmt.setInt(11, newIsFavorite ? 1 : 0);
                            updateStmt.setInt(12, userId);
                            updateStmt.setString(13, name); // Original name for WHERE clause
                            
                            int updated = updateStmt.executeUpdate();
                            if (updated > 0) {
                                loadContacts();
                                JOptionPane.showMessageDialog(this,
                                    "Contact updated successfully!",
                                    "Success",
                                    JOptionPane.INFORMATION_MESSAGE);
                            } else {
                                showError("Contact not found or could not be updated.");
                            }
                        }
                    }
                }
            }
        } catch (SQLException ex) {
            showError("Error updating contact: " + ex.getMessage());
        }
    }
    
    private void deleteContact(int selectedRow) {
        if (selectedRow == -1) {
            showError("Please select a contact to delete!");
            return;
        }
        
        String name = (String) contactsTableModel.getValueAt(selectedRow, 0);
        
        int confirm = JOptionPane.showConfirmDialog(this,
            "Are you sure you want to delete contact: " + name + "?",
            "Confirm Delete",
            JOptionPane.YES_NO_OPTION,
            JOptionPane.WARNING_MESSAGE);
            
        if (confirm == JOptionPane.YES_OPTION) {
            try (Connection conn = DatabaseUtil.getConnection()) {
                String query = "DELETE FROM contacts WHERE user_id = ? AND name = ?";
                
                try (PreparedStatement pstmt = conn.prepareStatement(query)) {
                    pstmt.setInt(1, userId);
                    pstmt.setString(2, name);
                    
                    int deleted = pstmt.executeUpdate();
                    if (deleted > 0) {
                        loadContacts();
                        JOptionPane.showMessageDialog(this,
                            "Contact deleted successfully!",
                            "Success",
                            JOptionPane.INFORMATION_MESSAGE);
                    } else {
                        showError("Contact not found or could not be deleted.");
                    }
                }
            } catch (SQLException ex) {
                showError("Error deleting contact: " + ex.getMessage());
            }
        }
    }

    private JPanel createBackupPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        
        JButton backupButton = new JButton("Create Backup");
        backupButton.addActionListener(e -> {
            try {
                String backupFile = BackupUtil.createBackup(userId);
                JOptionPane.showMessageDialog(this,
                    "Backup created successfully: " + backupFile,
                    "Backup Success",
                    JOptionPane.INFORMATION_MESSAGE);
            } catch (Exception ex) {
                showError("Error creating backup: " + ex.getMessage());
            }
        });
        
        JButton restoreButton = new JButton("Restore Data");
        restoreButton.addActionListener(e -> {
            JFileChooser fileChooser = new JFileChooser("backups");
            fileChooser.setFileFilter(new javax.swing.filechooser.FileFilter() {
                public boolean accept(File f) {
                    return f.isDirectory() || f.getName().toLowerCase().endsWith(".zip");
                }
                public String getDescription() {
                    return "Backup Files (*.zip)";
                }
            });
            
            int result = fileChooser.showOpenDialog(this);
            if (result == JFileChooser.APPROVE_OPTION) {
                try {
                    File selectedFile = fileChooser.getSelectedFile();
                    int confirm = JOptionPane.showConfirmDialog(this,
                        "This will replace all your current data. Are you sure you want to continue?",
                        "Confirm Restore",
                        JOptionPane.YES_NO_OPTION,
                        JOptionPane.WARNING_MESSAGE);
                        
                    if (confirm == JOptionPane.YES_OPTION) {
                        BackupUtil.restoreBackup(selectedFile.getPath(), userId);
                        loadUserData();
                        JOptionPane.showMessageDialog(this,
                            "Data restored successfully!",
                            "Restore Success",
                            JOptionPane.INFORMATION_MESSAGE);
                    }
                } catch (Exception ex) {
                    showError("Error restoring backup: " + ex.getMessage());
                }
            }
        });
        
        gbc.gridx = 0;
        gbc.gridy = 0;
        panel.add(backupButton, gbc);
        
        gbc.gridy = 1;
        panel.add(restoreButton, gbc);
        
        // Add description labels
        gbc.gridx = 1;
        gbc.gridy = 0;
        panel.add(new JLabel(" - Save all your data to a backup file"), gbc);
        
        gbc.gridy = 1;
        panel.add(new JLabel(" - Restore your data from a backup file"), gbc);
        
        return panel;
    }

    private void searchContacts(String searchTerm) {
        if (searchTerm == null || searchTerm.trim().isEmpty()) {
            loadContacts();
            return;
        }
        
        contactsTableModel.setRowCount(0);
        try (Connection conn = DatabaseUtil.getConnection()) {
            String query = "SELECT name, phone, email, company, category, is_favorite FROM contacts " +
                           "WHERE user_id = ? AND (name LIKE ? OR phone LIKE ? OR email LIKE ? OR " +
                           "company LIKE ? OR category LIKE ? OR notes LIKE ?)";
            try (PreparedStatement pstmt = conn.prepareStatement(query)) {
                searchTerm = "%" + searchTerm + "%";
                pstmt.setInt(1, userId);
                pstmt.setString(2, searchTerm);
                pstmt.setString(3, searchTerm);
                pstmt.setString(4, searchTerm);
                pstmt.setString(5, searchTerm);
                pstmt.setString(6, searchTerm);
                pstmt.setString(7, searchTerm);
                
                ResultSet rs = pstmt.executeQuery();
                
                while (rs.next()) {
                    contactsTableModel.addRow(new Object[]{
                        rs.getString("name"),
                        rs.getString("phone"),
                        rs.getString("email"),
                        rs.getString("company"),
                        rs.getString("category"),
                        rs.getInt("is_favorite") == 1 ? "★" : ""
                    });
                }
            }
        } catch (SQLException ex) {
            showError("Error searching contacts: " + ex.getMessage());
        }
    }
    
    private void viewContactDetails(int selectedRow) {
        if (selectedRow == -1) {
            showError("Please select a contact to view!");
            return;
        }
        
        String name = (String) contactsTableModel.getValueAt(selectedRow, 0);
        
        try (Connection conn = DatabaseUtil.getConnection()) {
            String query = "SELECT * FROM contacts WHERE user_id = ? AND name = ?";
            try (PreparedStatement pstmt = conn.prepareStatement(query)) {
                pstmt.setInt(1, userId);
                pstmt.setString(2, name);
                
                ResultSet rs = pstmt.executeQuery();
                if (rs.next()) {
                    // Create a formatted message with all contact details
                    StringBuilder details = new StringBuilder();
                    details.append("<html><h2>").append(name).append("</h2>");
                    
                    // Add basic details
                    String phone = rs.getString("phone");
                    if (phone != null && !phone.isEmpty()) {
                        details.append("<p><b>Phone:</b> ").append(phone).append("</p>");
                    }
                    
                    String email = rs.getString("email");
                    if (email != null && !email.isEmpty()) {
                        details.append("<p><b>Email:</b> ").append(email).append("</p>");
                    }
                    
                    // Add extended details
                    String address = rs.getString("address");
                    if (address != null && !address.isEmpty()) {
                        details.append("<p><b>Address:</b> ").append(address).append("</p>");
                    }
                    
                    Date birthday = rs.getDate("birthday");
                    if (birthday != null) {
                        details.append("<p><b>Birthday:</b> ").append(new java.text.SimpleDateFormat("yyyy-MM-dd").format(birthday)).append("</p>");
                    }
                    
                    String company = rs.getString("company");
                    if (company != null && !company.isEmpty()) {
                        details.append("<p><b>Company:</b> ").append(company).append("</p>");
                    }
                    
                    String jobTitle = rs.getString("job_title");
                    if (jobTitle != null && !jobTitle.isEmpty()) {
                        details.append("<p><b>Job Title:</b> ").append(jobTitle).append("</p>");
                    }
                    
                    String website = rs.getString("website");
                    if (website != null && !website.isEmpty()) {
                        details.append("<p><b>Website:</b> ").append(website).append("</p>");
                    }
                    
                    String category = rs.getString("category");
                    if (category != null && !category.isEmpty()) {
                        details.append("<p><b>Category:</b> ").append(category).append("</p>");
                    }
                    
                    String notes = rs.getString("notes");
                    if (notes != null && !notes.isEmpty()) {
                        details.append("<p><b>Notes:</b><br>").append(notes.replace("\n", "<br>")).append("</p>");
                    }
                    
                    details.append("</html>");
                    
                    // Display in a nicely formatted dialog
                    JOptionPane.showMessageDialog(this,
                        new JLabel(details.toString()),
                        "Contact Details",
                        JOptionPane.INFORMATION_MESSAGE);
                }
            }
        } catch (SQLException ex) {
            showError("Error loading contact details: " + ex.getMessage());
        }
    }

    private void generatePassword() {
        // Password options panel
        JPanel optionsPanel = new JPanel(new GridLayout(5, 2, 5, 5));
        
        // Length option
        JSlider lengthSlider = new JSlider(JSlider.HORIZONTAL, 8, 30, 16);
        lengthSlider.setMajorTickSpacing(4);
        lengthSlider.setMinorTickSpacing(1);
        lengthSlider.setPaintTicks(true);
        lengthSlider.setPaintLabels(true);
        
        // Character options
        JCheckBox uppercaseCheckBox = new JCheckBox("Include Uppercase (A-Z)", true);
        JCheckBox lowercaseCheckBox = new JCheckBox("Include Lowercase (a-z)", true);
        JCheckBox numbersCheckBox = new JCheckBox("Include Numbers (0-9)", true);
        JCheckBox specialCharsCheckBox = new JCheckBox("Include Special (!@#$%^&*)", true);
        
        optionsPanel.add(new JLabel("Password Length:"));
        optionsPanel.add(lengthSlider);
        optionsPanel.add(uppercaseCheckBox);
        optionsPanel.add(lowercaseCheckBox);
        optionsPanel.add(numbersCheckBox);
        optionsPanel.add(specialCharsCheckBox);
        
        // Result panel
        JPanel resultPanel = new JPanel(new BorderLayout(5, 5));
        JTextField passwordField = new JTextField(20);
        passwordField.setEditable(false);
        JButton regenerateButton = new JButton("Regenerate");
        JPanel buttonContainer = new JPanel(new FlowLayout(FlowLayout.CENTER));
        buttonContainer.add(regenerateButton);
        
        resultPanel.add(new JLabel("Generated Password:"), BorderLayout.NORTH);
        resultPanel.add(passwordField, BorderLayout.CENTER);
        resultPanel.add(buttonContainer, BorderLayout.SOUTH);
        
        // Function to generate random password
        Runnable generateRandomPassword = () -> {
            StringBuilder characterPool = new StringBuilder();
            
            if (uppercaseCheckBox.isSelected()) {
                characterPool.append("ABCDEFGHIJKLMNOPQRSTUVWXYZ");
            }
            
            if (lowercaseCheckBox.isSelected()) {
                characterPool.append("abcdefghijklmnopqrstuvwxyz");
            }
            
            if (numbersCheckBox.isSelected()) {
                characterPool.append("0123456789");
            }
            
            if (specialCharsCheckBox.isSelected()) {
                characterPool.append("!@#$%^&*()-_=+[]{}|;:,.<>?/");
            }
            
            if (characterPool.length() == 0) {
                passwordField.setText("Please select at least one character type");
                return;
            }
            
            int passwordLength = lengthSlider.getValue();
            StringBuilder password = new StringBuilder(passwordLength);
            java.util.Random random = new java.util.Random();
            
            for (int i = 0; i < passwordLength; i++) {
                int randomIndex = random.nextInt(characterPool.length());
                password.append(characterPool.charAt(randomIndex));
            }
            
            passwordField.setText(password.toString());
        };
        
        // Generate initial password
        generateRandomPassword.run();
        
        // Add listeners
        regenerateButton.addActionListener(e -> generateRandomPassword.run());
        
        // Main panel combining options and result
        JPanel mainPanel = new JPanel(new BorderLayout(10, 10));
        mainPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        mainPanel.add(optionsPanel, BorderLayout.NORTH);
        mainPanel.add(resultPanel, BorderLayout.CENTER);
        
        // Add copy and save buttons
        JPanel actionPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
        JButton copyButton = new JButton("Copy to Clipboard");
        JButton saveButton = new JButton("Save Password");
        
        copyButton.addActionListener(e -> {
            String password = passwordField.getText();
            if (password != null && !password.isEmpty()) {
                java.awt.Toolkit.getDefaultToolkit().getSystemClipboard().setContents(
                    new java.awt.datatransfer.StringSelection(password), null
                );
                JOptionPane.showMessageDialog(this, "Password copied to clipboard!", "Success", JOptionPane.INFORMATION_MESSAGE);
            }
        });
        
        saveButton.addActionListener(e -> {
            String generatedPassword = passwordField.getText();
            if (generatedPassword == null || generatedPassword.isEmpty() || generatedPassword.startsWith("Please select")) {
                showError("No valid password generated!");
                return;
            }
            
            JPanel savePanel = new JPanel(new GridLayout(2, 2, 5, 5));
            JTextField websiteField = new JTextField(20);
            JTextField usernameField = new JTextField(20);
            
            savePanel.add(new JLabel("Website:"));
            savePanel.add(websiteField);
            savePanel.add(new JLabel("Username:"));
            savePanel.add(usernameField);
            
            int result = JOptionPane.showConfirmDialog(this, savePanel, "Save Password",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
                
            if (result == JOptionPane.OK_OPTION) {
                String website = websiteField.getText();
                String username = usernameField.getText();
                
                if (website.isEmpty() || username.isEmpty()) {
                    showError("Website and username are required!");
                    return;
                }
                
                try (Connection conn = DatabaseUtil.getConnection()) {
                    String encryptedPassword = SecurityUtil.encryptPassword(generatedPassword, "your-encryption-key");
                    String query = "INSERT INTO passwords (user_id, website, username, encrypted_password) VALUES (?, ?, ?, ?)";
                    
                    try (PreparedStatement pstmt = conn.prepareStatement(query)) {
                        pstmt.setInt(1, userId);
                        pstmt.setString(2, website);
                        pstmt.setString(3, username);
                        pstmt.setString(4, encryptedPassword);
                        pstmt.executeUpdate();
                        loadPasswords();
                        JOptionPane.showMessageDialog(this, "Password saved successfully!", "Success", JOptionPane.INFORMATION_MESSAGE);
                    }
                } catch (SQLException ex) {
                    showError("Error saving password: " + ex.getMessage());
                }
            }
        });
        
        actionPanel.add(copyButton);
        actionPanel.add(saveButton);
        mainPanel.add(actionPanel, BorderLayout.SOUTH);
        
        // Show dialog with password generator
        JOptionPane.showOptionDialog(
            this,
            mainPanel,
            "Generate Secure Password",
            JOptionPane.DEFAULT_OPTION,
            JOptionPane.PLAIN_MESSAGE,
            null,
            new Object[]{},
            null
        );
    }

    private JPanel createAnalyticsPanel() {
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        
        // Create header
        JLabel headerLabel = new JLabel("NHCE Password Security Analytics", SwingConstants.CENTER);
        headerLabel.setFont(new Font("Arial", Font.BOLD, 20));
        panel.add(headerLabel, BorderLayout.NORTH);
        
        // Create tabs for different analytics
        JTabbedPane analyticsTabs = new JTabbedPane();
        analyticsTabs.addTab("Password Strength", createPasswordStrengthPanel());
        analyticsTabs.addTab("Security Overview", createSecurityOverviewPanel());
        panel.add(analyticsTabs, BorderLayout.CENTER);
        
        return panel;
    }
    
    private JPanel createPasswordStrengthPanel() {
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        
        // Create password input for testing
        JPanel inputPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JTextField passwordField = new JTextField(20);
        JButton analyzeButton = new JButton("Analyze");
        inputPanel.add(new JLabel("Test your password strength: "));
        inputPanel.add(passwordField);
        inputPanel.add(analyzeButton);
        panel.add(inputPanel, BorderLayout.NORTH);
        
        // Create results panel with animated strength meter
        JPanel resultsPanel = new JPanel();
        resultsPanel.setLayout(new BoxLayout(resultsPanel, BoxLayout.Y_AXIS));
        resultsPanel.setBorder(BorderFactory.createTitledBorder("Password Strength Analysis"));
        
        // Strength meter components
        JPanel meterPanel = new JPanel(new BorderLayout(5, 5));
        JProgressBar strengthMeter = new JProgressBar(0, 100);
        strengthMeter.setStringPainted(true);
        strengthMeter.setString("Enter a password to analyze");
        JLabel strengthLabel = new JLabel("Not analyzed", SwingConstants.CENTER);
        meterPanel.add(new JLabel("Strength:"), BorderLayout.WEST);
        meterPanel.add(strengthMeter, BorderLayout.CENTER);
        meterPanel.add(strengthLabel, BorderLayout.SOUTH);
        
        // Detailed analysis components
        JPanel detailsPanel = new JPanel(new GridLayout(5, 2, 5, 5));
        detailsPanel.setBorder(BorderFactory.createTitledBorder("Details"));
        
        JLabel lengthLabel = new JLabel("Length: ");
        JLabel lengthValue = new JLabel("0 characters");
        
        JLabel complexityLabel = new JLabel("Complexity: ");
        JLabel complexityValue = new JLabel("N/A");
        
        JLabel entropyLabel = new JLabel("Entropy: ");
        JLabel entropyValue = new JLabel("0 bits");
        
        JLabel crackTimeLabel = new JLabel("Estimated crack time: ");
        JLabel crackTimeValue = new JLabel("N/A");
        
        JLabel suggestionsLabel = new JLabel("Suggestions: ");
        JTextArea suggestionsValue = new JTextArea(3, 20);
        suggestionsValue.setEditable(false);
        suggestionsValue.setLineWrap(true);
        suggestionsValue.setWrapStyleWord(true);
        suggestionsValue.setBackground(detailsPanel.getBackground());
        
        detailsPanel.add(lengthLabel);
        detailsPanel.add(lengthValue);
        detailsPanel.add(complexityLabel);
        detailsPanel.add(complexityValue);
        detailsPanel.add(entropyLabel);
        detailsPanel.add(entropyValue);
        detailsPanel.add(crackTimeLabel);
        detailsPanel.add(crackTimeValue);
        detailsPanel.add(suggestionsLabel);
        detailsPanel.add(new JScrollPane(suggestionsValue));
        
        resultsPanel.add(meterPanel);
        resultsPanel.add(Box.createVerticalStrut(10));
        resultsPanel.add(detailsPanel);
        
        panel.add(resultsPanel, BorderLayout.CENTER);
        
        // Create animated chart
        JPanel chartPanel = new JPanel(new BorderLayout());
        chartPanel.setBorder(BorderFactory.createTitledBorder("Strength Visualization"));
        
        // Sample chart panel - would be replaced with real chart in production
        PasswordStrengthChart strengthChart = new PasswordStrengthChart();
        chartPanel.add(strengthChart, BorderLayout.CENTER);
        
        panel.add(chartPanel, BorderLayout.SOUTH);
        
        // Set up the analyzer
        analyzeButton.addActionListener(e -> {
            String password = passwordField.getText();
            if (password.isEmpty()) {
                strengthMeter.setValue(0);
                strengthMeter.setString("Enter a password to analyze");
                strengthLabel.setText("Not analyzed");
                lengthValue.setText("0 characters");
                complexityValue.setText("N/A");
                entropyValue.setText("0 bits");
                crackTimeValue.setText("N/A");
                suggestionsValue.setText("");
                strengthChart.updateStrength(0);
                return;
            }
            
            // Analyze password
            int strength = analyzePasswordStrength(password);
            
            // Animate the strength meter
            final int targetStrength = strength;
            final javax.swing.Timer timer = new javax.swing.Timer(15, null);
            final int[] currentValue = {0};
            
            timer.addActionListener(event -> {
                if (currentValue[0] < targetStrength) {
                    currentValue[0] += 2;
                    strengthMeter.setValue(currentValue[0]);
                    
                    // Update color based on strength
                    if (currentValue[0] < 25) {
                        strengthMeter.setForeground(new Color(255, 0, 0)); // Red
                    } else if (currentValue[0] < 50) {
                        strengthMeter.setForeground(new Color(255, 165, 0)); // Orange
                    } else if (currentValue[0] < 75) {
                        strengthMeter.setForeground(new Color(255, 255, 0)); // Yellow
                    } else {
                        strengthMeter.setForeground(new Color(0, 128, 0)); // Green
                    }
                } else {
                    timer.stop();
                }
            });
            
            timer.start();
            
            // Update other analysis components
            strengthMeter.setString(getStrengthText(strength) + " (" + strength + "%)");
            strengthLabel.setText(getStrengthText(strength));
            lengthValue.setText(password.length() + " characters");
            
            boolean hasLowercase = !password.equals(password.toUpperCase());
            boolean hasUppercase = !password.equals(password.toLowerCase());
            boolean hasDigits = password.matches(".*\\d.*");
            boolean hasSpecial = password.matches(".*[^a-zA-Z0-9].*");
            
            int complexityScore = 0;
            if (hasLowercase) complexityScore++;
            if (hasUppercase) complexityScore++;
            if (hasDigits) complexityScore++;
            if (hasSpecial) complexityScore++;
            
            String complexityText;
            switch (complexityScore) {
                case 1: complexityText = "Very Low"; break;
                case 2: complexityText = "Low"; break;
                case 3: complexityText = "Medium"; break;
                case 4: complexityText = "High"; break;
                default: complexityText = "None"; break;
            }
            complexityValue.setText(complexityText);
            
            // Calculate entropy (simplified)
            double entropyBits = calculateEntropy(password);
            entropyValue.setText(String.format("%.1f bits", entropyBits));
            
            // Estimate crack time
            crackTimeValue.setText(estimateCrackTime(entropyBits));
            
            // Generate suggestions
            StringBuilder suggestions = new StringBuilder();
            if (password.length() < 12) {
                suggestions.append("• Use at least 12 characters\n");
            }
            if (!hasLowercase || !hasUppercase) {
                suggestions.append("• Mix uppercase and lowercase letters\n");
            }
            if (!hasDigits) {
                suggestions.append("• Add numbers\n");
            }
            if (!hasSpecial) {
                suggestions.append("• Include special characters\n");
            }
            if (suggestions.length() == 0) {
                suggestions.append("Your password is strong!");
            }
            
            suggestionsValue.setText(suggestions.toString());
            
            // Update chart
            strengthChart.updateStrength(strength);
        });
        
        return panel;
    }
    
    private JPanel createSecurityOverviewPanel() {
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        
        // Create statistics panel
        JPanel statsPanel = new JPanel(new GridLayout(2, 2, 10, 10));
        
        // Create stored passwords stats card
        JPanel passwordsCard = createStatsCard("Stored Passwords", "0");
        
        // Create average strength card
        JPanel strengthCard = createStatsCard("Average Strength", "0%");
        
        // Create weak passwords card
        JPanel weakPasswordsCard = createStatsCard("Weak Passwords", "0");
        
        // Create reused passwords card
        JPanel reusedPasswordsCard = createStatsCard("Reused Passwords", "0");
        
        statsPanel.add(passwordsCard);
        statsPanel.add(strengthCard);
        statsPanel.add(weakPasswordsCard);
        statsPanel.add(reusedPasswordsCard);
        
        panel.add(statsPanel, BorderLayout.NORTH);
        
        // Create analytics charts panel
        JPanel chartsPanel = new JPanel(new GridLayout(1, 2, 10, 10));
        
        // Create password distribution chart
        JPanel distributionPanel = new JPanel(new BorderLayout());
        distributionPanel.setBorder(BorderFactory.createTitledBorder("Password Strength Distribution"));
        JLabel distributionChart = new JLabel("Chart would go here", SwingConstants.CENTER);
        distributionPanel.add(distributionChart, BorderLayout.CENTER);
        
        // Create password activity chart
        JPanel activityPanel = new JPanel(new BorderLayout());
        activityPanel.setBorder(BorderFactory.createTitledBorder("Password Activity"));
        JLabel activityChart = new JLabel("Chart would go here", SwingConstants.CENTER);
        activityPanel.add(activityChart, BorderLayout.CENTER);
        
        chartsPanel.add(distributionPanel);
        chartsPanel.add(activityPanel);
        
        panel.add(chartsPanel, BorderLayout.CENTER);
        
        // Create button to analyze all passwords
        JPanel actionPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
        JButton analyzeAllButton = new JButton("Analyze All Passwords");
        analyzeAllButton.addActionListener(e -> updateSecurityOverview());
        actionPanel.add(analyzeAllButton);
        
        panel.add(actionPanel, BorderLayout.SOUTH);
        
        return panel;
    }
    
    private JPanel createStatsCard(String title, String value) {
        JPanel card = new JPanel(new BorderLayout());
        card.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(Color.LIGHT_GRAY), 
            BorderFactory.createEmptyBorder(10, 10, 10, 10)
        ));
        
        JLabel titleLabel = new JLabel(title, SwingConstants.CENTER);
        titleLabel.setFont(new Font("Arial", Font.PLAIN, 14));
        
        JLabel valueLabel = new JLabel(value, SwingConstants.CENTER);
        valueLabel.setFont(new Font("Arial", Font.BOLD, 24));
        
        card.add(titleLabel, BorderLayout.NORTH);
        card.add(valueLabel, BorderLayout.CENTER);
        
        return card;
    }
    
    private void updateSecurityOverview() {
        // This would query the database and update the security overview
        // For now, we'll just show a message indicating it's a placeholder
        JOptionPane.showMessageDialog(this,
            "This feature would analyze all stored passwords and update the security metrics.\n" +
            "For now, this is just a placeholder to demonstrate the UI.",
            "Placeholder",
            JOptionPane.INFORMATION_MESSAGE);
    }
    
    private int analyzePasswordStrength(String password) {
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
    
    private String getStrengthText(int strength) {
        if (strength < 25) {
            return "Very Weak";
        } else if (strength < 50) {
            return "Weak";
        } else if (strength < 75) {
            return "Medium";
        } else if (strength < 90) {
            return "Strong";
        } else {
            return "Very Strong";
        }
    }
    
    private double calculateEntropy(String password) {
        int charset = 0;
        if (password.matches(".*[a-z].*")) charset += 26;
        if (password.matches(".*[A-Z].*")) charset += 26;
        if (password.matches(".*\\d.*")) charset += 10;
        if (password.matches(".*[^a-zA-Z0-9].*")) charset += 33; // Approximation for special chars
        
        if (charset == 0) return 0;
        
        return password.length() * (Math.log(charset) / Math.log(2));
    }
    
    private String estimateCrackTime(double entropy) {
        // Assume 10 billion guesses per second (high-end hardware)
        double guessesPerSecond = 10_000_000_000.0;
        double possibleCombinations = Math.pow(2, entropy);
        double secondsToCrack = possibleCombinations / guessesPerSecond / 2; // Average case is half of max
        
        if (secondsToCrack < 1) {
            return "Instant";
        } else if (secondsToCrack < 60) {
            return String.format("%.1f seconds", secondsToCrack);
        } else if (secondsToCrack < 3600) {
            return String.format("%.1f minutes", secondsToCrack / 60);
        } else if (secondsToCrack < 86400) {
            return String.format("%.1f hours", secondsToCrack / 3600);
        } else if (secondsToCrack < 2592000) {
            return String.format("%.1f days", secondsToCrack / 86400);
        } else if (secondsToCrack < 31536000) {
            return String.format("%.1f months", secondsToCrack / 2592000);
        } else if (secondsToCrack < 315360000) {
            return String.format("%.1f years", secondsToCrack / 31536000);
        } else {
            return "centuries";
        }
    }
    
    // Inner class for the animated strength chart
    private class PasswordStrengthChart extends JPanel {
        private int strength = 0;
        private final int animationDuration = 30;
        private int animationProgress = 0;
        private javax.swing.Timer animationTimer;
        
        public PasswordStrengthChart() {
            setPreferredSize(new Dimension(400, 100));
            setBackground(Color.WHITE);
        }
        
        public void updateStrength(int newStrength) {
            // Stop any current animation
            if (animationTimer != null && animationTimer.isRunning()) {
                animationTimer.stop();
            }
            
            // Store the target strength
            this.strength = newStrength;
            
            // Reset animation progress
            animationProgress = 0;
            
            // Start animation
            animationTimer = new javax.swing.Timer(16, e -> {
                animationProgress++;
                if (animationProgress >= animationDuration) {
                    animationTimer.stop();
                }
                repaint();
            });
            
            animationTimer.start();
        }
        
        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            
            Graphics2D g2d = (Graphics2D) g.create();
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            
            int width = getWidth();
            int height = getHeight();
            
            // Calculate animation progress (0.0 to 1.0)
            float progress = Math.min(1.0f, (float) animationProgress / animationDuration);
            
            // Calculate current animated strength value
            int currentStrength = (int) (strength * progress);
            
            // Draw background bar
            g2d.setColor(Color.LIGHT_GRAY);
            g2d.fillRect(10, height / 2 - 10, width - 20, 20);
            
            // Get color based on strength
            Color barColor;
            if (currentStrength < 25) {
                barColor = new Color(255, 0, 0); // Red
            } else if (currentStrength < 50) {
                barColor = new Color(255, 165, 0); // Orange
            } else if (currentStrength < 75) {
                barColor = new Color(255, 255, 0); // Yellow
            } else {
                barColor = new Color(0, 128, 0); // Green
            }
            
            // Draw strength bar
            g2d.setColor(barColor);
            int barWidth = (int) ((width - 20) * currentStrength / 100.0);
            g2d.fillRect(10, height / 2 - 10, barWidth, 20);
            
            // Draw segments
            g2d.setColor(Color.DARK_GRAY);
            for (int i = 1; i < 4; i++) {
                int x = 10 + (width - 20) * i / 4;
                g2d.drawLine(x, height / 2 - 15, x, height / 2 + 5);
            }
            
            // Draw strength percentage
            String strengthText = currentStrength + "%";
            FontMetrics fm = g2d.getFontMetrics();
            int textWidth = fm.stringWidth(strengthText);
            int textHeight = fm.getHeight();
            
            g2d.setColor(Color.BLACK);
            g2d.drawString(strengthText, (width - textWidth) / 2, height / 2 + textHeight / 2);
            
            g2d.dispose();
        }
    }

    private void toggleDarkMode(boolean darkMode) {
        try {
            // Reset all UIManager settings first to avoid conflicts
            try {
                // First reset to system defaults to clear previous settings
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception ex) {
                System.err.println("Could not reset look and feel: " + ex.getMessage());
            }
            
            // Switch between light and dark theme
            if (darkMode) {
                applyDarkTheme();
            } else {
                applyLightTheme();
            }
            
            // Update UI for all components
            SwingUtilities.updateComponentTreeUI(this);
            
            // Refresh the content panes
            for (int i = 0; i < tabbedPane.getTabCount(); i++) {
                Component component = tabbedPane.getComponentAt(i);
                if (component instanceof JPanel) {
                    component.setBackground(UIManager.getColor("Panel.background"));
                    updatePanelComponents((JPanel) component);
                }
            }
            
            // Store user preference
            saveDarkModePreference(darkMode);
            
        } catch (Exception ex) {
            showError("Error changing theme: " + ex.getMessage());
        }
    }
    
    private void updatePanelComponents(JPanel panel) {
        // Update background color for the panel and all its components
        panel.setBackground(UIManager.getColor("Panel.background"));
        
        // Update all components in the panel
        for (Component component : panel.getComponents()) {
            if (component instanceof JPanel) {
                updatePanelComponents((JPanel) component);
            } else if (component instanceof JScrollPane) {
                JScrollPane scrollPane = (JScrollPane) component;
                scrollPane.getViewport().setBackground(UIManager.getColor("Panel.background"));
                Component view = scrollPane.getViewport().getView();
                if (view instanceof JPanel) {
                    updatePanelComponents((JPanel) view);
                } else if (view instanceof JTextArea) {
                    view.setBackground(UIManager.getColor("TextArea.background"));
                    view.setForeground(UIManager.getColor("TextArea.foreground"));
                }
            } else if (component instanceof JTextArea) {
                component.setBackground(UIManager.getColor("TextArea.background"));
                component.setForeground(UIManager.getColor("TextArea.foreground"));
            }
        }
    }
    
    private void applyDarkTheme() {
        // Create a dark theme
        Color darkBg = new Color(43, 43, 43);
        Color darkText = new Color(220, 220, 220);
        Color darkHighlight = new Color(75, 110, 175);
        
        // Update UI Manager defaults
        UIManager.put("Panel.background", darkBg);
        UIManager.put("OptionPane.background", darkBg);
        UIManager.put("TabbedPane.background", darkBg);
        UIManager.put("ComboBox.background", darkBg);
        UIManager.put("TextField.background", new Color(60, 60, 60));
        UIManager.put("TextArea.background", new Color(60, 60, 60));
        UIManager.put("Table.background", new Color(50, 50, 50));
        UIManager.put("TabbedPane.selected", darkHighlight);
        UIManager.put("TabbedPane.contentAreaColor", darkBg);
        
        UIManager.put("Panel.foreground", darkText);
        UIManager.put("Label.foreground", darkText);
        UIManager.put("Button.foreground", darkText);
        UIManager.put("TextField.foreground", darkText);
        UIManager.put("TextArea.foreground", darkText);
        UIManager.put("TabbedPane.foreground", darkText);
        UIManager.put("ComboBox.foreground", darkText);
        UIManager.put("Table.foreground", darkText);
        UIManager.put("TableHeader.foreground", darkText);
        
        UIManager.put("Button.background", new Color(80, 80, 80));
        UIManager.put("ToggleButton.background", new Color(80, 80, 80));
        UIManager.put("ToggleButton.select", darkHighlight);
        UIManager.put("ComboBox.selectionBackground", darkHighlight);
        UIManager.put("ComboBox.selectionForeground", darkText);
        
        // Update border colors
        UIManager.put("Table.gridColor", new Color(100, 100, 100));
        UIManager.put("TitledBorder.titleColor", darkText);
        UIManager.put("TabbedPane.borderColor", new Color(70, 70, 70));
        
        // Show a notification
        JOptionPane.showMessageDialog(this, 
            "Dark mode enabled! Enjoy reduced eye strain.", 
            "Theme Changed", 
            JOptionPane.INFORMATION_MESSAGE);
    }
    
    private void applyLightTheme() {
        try {
            // Create a light blue theme
            Color lightBlueBg = new Color(230, 240, 250);  // Light blue background
            Color lightBlueAccent = new Color(70, 130, 180);  // Steel blue accent
            Color darkText = new Color(20, 20, 50);  // Dark text for contrast
            
            // Update UI Manager defaults with light blue theme
            UIManager.put("Panel.background", lightBlueBg);
            UIManager.put("OptionPane.background", lightBlueBg);
            UIManager.put("TabbedPane.background", lightBlueBg);
            UIManager.put("ComboBox.background", Color.WHITE);
            UIManager.put("TextField.background", Color.WHITE);
            UIManager.put("TextArea.background", Color.WHITE);
            UIManager.put("Table.background", Color.WHITE);
            UIManager.put("TabbedPane.selected", lightBlueAccent);
            UIManager.put("TabbedPane.contentAreaColor", lightBlueBg);
            
            UIManager.put("Panel.foreground", darkText);
            UIManager.put("Label.foreground", darkText);
            UIManager.put("Button.foreground", darkText);
            UIManager.put("TextField.foreground", darkText);
            UIManager.put("TextArea.foreground", darkText);
            UIManager.put("TabbedPane.foreground", darkText);
            UIManager.put("ComboBox.foreground", darkText);
            UIManager.put("Table.foreground", darkText);
            UIManager.put("TableHeader.foreground", darkText);
            
            UIManager.put("Button.background", new Color(200, 220, 240));
            UIManager.put("ToggleButton.background", new Color(200, 220, 240));
            UIManager.put("ToggleButton.select", lightBlueAccent);
            UIManager.put("ComboBox.selectionBackground", lightBlueAccent);
            UIManager.put("ComboBox.selectionForeground", Color.WHITE);
            
            // Update border colors
            UIManager.put("Table.gridColor", new Color(180, 190, 200));
            UIManager.put("TitledBorder.titleColor", darkText);
            UIManager.put("TabbedPane.borderColor", new Color(150, 160, 180));
            
            // Show a notification
            JOptionPane.showMessageDialog(this, 
                "Light blue mode enabled!", 
                "Theme Changed", 
                JOptionPane.INFORMATION_MESSAGE);
        } catch (Exception ex) {
            showError("Error applying light theme: " + ex.getMessage());
        }
    }
    
    private void saveDarkModePreference(boolean darkMode) {
        try (Connection conn = DatabaseUtil.getConnection()) {
            // Check if user_preferences table exists, if not create it
            try (Statement stmt = conn.createStatement()) {
                stmt.execute(
                    "CREATE TABLE IF NOT EXISTS user_preferences (" +
                    "user_id INTEGER PRIMARY KEY, " +
                    "dark_mode INTEGER, " +
                    "FOREIGN KEY (user_id) REFERENCES users(id)" +
                    ")"
                );
            }
            
            // Save the dark mode preference
            String query = "INSERT OR REPLACE INTO user_preferences (user_id, dark_mode) VALUES (?, ?)";
            try (PreparedStatement pstmt = conn.prepareStatement(query)) {
                pstmt.setInt(1, userId);
                pstmt.setInt(2, darkMode ? 1 : 0);
                pstmt.executeUpdate();
            }
        } catch (SQLException ex) {
            showError("Error saving theme preference: " + ex.getMessage());
        } catch (Exception ex) {
            showError("Error applying theme: " + ex.getMessage());
        }
    }
    
    private void loadUserPreferences() {
        try (Connection conn = DatabaseUtil.getConnection()) {
            String query = "SELECT dark_mode FROM user_preferences WHERE user_id = ?";
            try (PreparedStatement pstmt = conn.prepareStatement(query)) {
                pstmt.setInt(1, userId);
                
                ResultSet rs = pstmt.executeQuery();
                if (rs.next()) {
                    boolean darkMode = rs.getInt("dark_mode") == 1;
                    if (darkMode) {
                        applyDarkTheme();
                    }
                }
            }
        } catch (SQLException ex) {
            // Ignore if preferences don't exist yet
        } catch (Exception ex) {
            showError("Error loading theme preference: " + ex.getMessage());
        }
    }

    private JPanel createBrowserIntegrationPanel() {
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));
        
        // Create header
        JLabel headerLabel = new JLabel("Browser Extension Integration", SwingConstants.CENTER);
        headerLabel.setFont(new Font("Arial", Font.BOLD, 20));
        panel.add(headerLabel, BorderLayout.NORTH);
        
        // Create instructions panel
        JPanel instructionsPanel = new JPanel(new BorderLayout(10, 10));
        instructionsPanel.setBorder(BorderFactory.createTitledBorder("Setup Instructions"));
        
        JTextArea instructionsText = new JTextArea();
        instructionsText.setText(BrowserExtensionUtil.getExtensionInstructions());
        instructionsText.setEditable(false);
        instructionsText.setLineWrap(true);
        instructionsText.setWrapStyleWord(true);
        instructionsText.setBackground(panel.getBackground());
        
        JScrollPane instructionsScroll = new JScrollPane(instructionsText);
        instructionsScroll.setPreferredSize(new Dimension(400, 200));
        instructionsPanel.add(instructionsScroll, BorderLayout.CENTER);
        
        // Create status panel
        JPanel statusPanel = new JPanel(new BorderLayout(10, 10));
        statusPanel.setBorder(BorderFactory.createTitledBorder("Extension Server Status"));
        
        JPanel statusInfoPanel = new JPanel(new GridLayout(3, 2, 5, 5));
        statusInfoPanel.add(new JLabel("Status:"));
        JLabel statusLabel = new JLabel("Active", JLabel.LEFT);
        statusLabel.setForeground(new Color(0, 128, 0));
        statusInfoPanel.add(statusLabel);
        
        statusInfoPanel.add(new JLabel("Port:"));
        statusInfoPanel.add(new JLabel("45678", JLabel.LEFT));
        
        statusInfoPanel.add(new JLabel("Credentials Available:"));
        
        // Count available passwords
        int passwordCount = 0;
        try (Connection conn = DatabaseUtil.getConnection()) {
            String query = "SELECT COUNT(*) FROM passwords WHERE user_id = ? AND auto_fill_enabled = 1";
            try (PreparedStatement pstmt = conn.prepareStatement(query)) {
                pstmt.setInt(1, userId);
                ResultSet rs = pstmt.executeQuery();
                if (rs.next()) {
                    passwordCount = rs.getInt(1);
                }
            }
        } catch (SQLException ex) {
            showError("Error counting passwords: " + ex.getMessage());
        }
        
        statusInfoPanel.add(new JLabel(String.valueOf(passwordCount), JLabel.LEFT));
        
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
        JButton toggleServerButton = new JButton("Restart Server");
        
        toggleServerButton.addActionListener(e -> {
            try {
                BrowserExtensionUtil.stopExtensionServer();
                BrowserExtensionUtil.startExtensionServer();
                JOptionPane.showMessageDialog(this, 
                    "Browser extension server has been restarted.", 
                    "Server Restarted", 
                    JOptionPane.INFORMATION_MESSAGE);
            } catch (Exception ex) {
                showError("Failed to restart server: " + ex.getMessage());
            }
        });
        
        buttonPanel.add(toggleServerButton);
        
        statusPanel.add(statusInfoPanel, BorderLayout.CENTER);
        statusPanel.add(buttonPanel, BorderLayout.SOUTH);
        
        // Create QR code panel
        JPanel qrPanel = new JPanel(new BorderLayout(10, 10));
        qrPanel.setBorder(BorderFactory.createTitledBorder("Connection QR Code"));
        
        JLabel qrPlaceholder = new JLabel("QR Code would be generated here", SwingConstants.CENTER);
        qrPlaceholder.setPreferredSize(new Dimension(200, 200));
        qrPlaceholder.setBorder(BorderFactory.createLineBorder(Color.GRAY));
        
        qrPanel.add(qrPlaceholder, BorderLayout.CENTER);
        
        JButton generateQrButton = new JButton("Generate QR Code");
        JPanel qrButtonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
        qrButtonPanel.add(generateQrButton);
        qrPanel.add(qrButtonPanel, BorderLayout.SOUTH);
        
        // Add panels to main layout
        JPanel contentPanel = new JPanel(new GridLayout(1, 2, 20, 0));
        
        JPanel leftPanel = new JPanel(new BorderLayout(0, 10));
        leftPanel.add(instructionsPanel, BorderLayout.CENTER);
        
        JPanel rightPanel = new JPanel(new BorderLayout(0, 10));
        rightPanel.add(statusPanel, BorderLayout.NORTH);
        rightPanel.add(qrPanel, BorderLayout.CENTER);
        
        contentPanel.add(leftPanel);
        contentPanel.add(rightPanel);
        
        panel.add(contentPanel, BorderLayout.CENTER);
        
        return panel;
    }
    
    private JPanel createPasswordHealthPanel() {
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));
        
        // Create header
        JLabel headerLabel = new JLabel("Password Health Reports", SwingConstants.CENTER);
        headerLabel.setFont(new Font("Arial", Font.BOLD, 20));
        panel.add(headerLabel, BorderLayout.NORTH);
        
        // Create settings panel
        JPanel settingsPanel = new JPanel(new BorderLayout(10, 10));
        settingsPanel.setBorder(BorderFactory.createTitledBorder("Report Settings"));
        
        JPanel settingsFormPanel = new JPanel(new GridLayout(2, 2, 5, 5));
        
        // Email setting
        settingsFormPanel.add(new JLabel("Email Address:"));
        JTextField emailField = new JTextField(20);
        settingsFormPanel.add(emailField);
        
        // Frequency setting
        settingsFormPanel.add(new JLabel("Report Frequency:"));
        JComboBox<String> frequencyCombo = new JComboBox<>(new String[]{"Weekly", "Monthly", "Quarterly"});
        settingsFormPanel.add(frequencyCombo);
        
        // Load current settings
        try (Connection conn = DatabaseUtil.getConnection()) {
            String query = "SELECT email, report_frequency FROM users WHERE id = ?";
            try (PreparedStatement pstmt = conn.prepareStatement(query)) {
                pstmt.setInt(1, userId);
                ResultSet rs = pstmt.executeQuery();
                
                if (rs.next()) {
                    String email = rs.getString("email");
                    String frequency = rs.getString("report_frequency");
                    
                    if (email != null) {
                        emailField.setText(email);
                    }
                    
                    if (frequency != null) {
                        frequencyCombo.setSelectedItem(frequency.substring(0, 1) + frequency.substring(1).toLowerCase());
                    }
                }
            }
        } catch (SQLException ex) {
            showError("Error loading report settings: " + ex.getMessage());
        }
        
        // Save button
        JButton saveSettingsButton = new JButton("Save Settings");
        saveSettingsButton.addActionListener(e -> {
            String email = emailField.getText().trim();
            String frequency = ((String)frequencyCombo.getSelectedItem()).toUpperCase();
            
            if (email.isEmpty()) {
                showError("Please enter an email address for receiving reports.");
                return;
            }
            
            // Simple email validation
            if (!email.matches("^[\\w-\\.]+@([\\w-]+\\.)+[\\w-]{2,4}$")) {
                showError("Please enter a valid email address.");
                return;
            }
            
            // Save settings
            PasswordHealthUtil.setUserEmail(userId, email);
            PasswordHealthUtil.setReportFrequency(userId, frequency);
            
            JOptionPane.showMessageDialog(this, 
                "Report settings saved successfully!", 
                "Settings Saved", 
                JOptionPane.INFORMATION_MESSAGE);
        });
        
        JPanel settingsButtonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
        settingsButtonPanel.add(saveSettingsButton);
        
        settingsPanel.add(settingsFormPanel, BorderLayout.CENTER);
        settingsPanel.add(settingsButtonPanel, BorderLayout.SOUTH);
        
        // Create reports history panel
        JPanel reportsPanel = new JPanel(new BorderLayout(10, 10));
        reportsPanel.setBorder(BorderFactory.createTitledBorder("Report History"));
        
        // Table for report history
        String[] columnNames = {"Date", "Score", "Weak Passwords", "Reused Passwords", "Old Passwords"};
        DefaultTableModel reportsTableModel = new DefaultTableModel(columnNames, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        
        JTable reportsTable = new JTable(reportsTableModel);
        JScrollPane reportsScroll = new JScrollPane(reportsTable);
        
        // Load report history
        try (Connection conn = DatabaseUtil.getConnection()) {
            String query = "SELECT report_date, overall_score, weak_passwords, reused_passwords, old_passwords " +
                         "FROM password_health_reports WHERE user_id = ? ORDER BY report_date DESC LIMIT 10";
            
            try (PreparedStatement pstmt = conn.prepareStatement(query)) {
                pstmt.setInt(1, userId);
                ResultSet rs = pstmt.executeQuery();
                
                while (rs.next()) {
                    java.sql.Timestamp reportDate = rs.getTimestamp("report_date");
                    String formattedDate = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm").format(reportDate);
                    
                    reportsTableModel.addRow(new Object[]{
                        formattedDate,
                        rs.getInt("overall_score") + "%",
                        rs.getInt("weak_passwords"),
                        rs.getInt("reused_passwords"),
                        rs.getInt("old_passwords")
                    });
                }
            }
        } catch (SQLException ex) {
            showError("Error loading report history: " + ex.getMessage());
        }
        
        reportsPanel.add(reportsScroll, BorderLayout.CENTER);
        
        // Button panel for generate report and view report
        JPanel reportButtonsPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
        
        JButton generateReportButton = new JButton("Generate New Report");
        JButton viewReportButton = new JButton("View Selected Report");
        
        generateReportButton.addActionListener(e -> {
            String email = emailField.getText().trim();
            
            if (email.isEmpty()) {
                showError("Please enter an email address for receiving reports.");
                return;
            }
            
            // Simple email validation
            if (!email.matches("^[\\w-\\.]+@([\\w-]+\\.)+[\\w-]{2,4}$")) {
                showError("Please enter a valid email address.");
                return;
            }
            
            // Update email if changed
            PasswordHealthUtil.setUserEmail(userId, email);
            
            // Show generating dialog
            JDialog generatingDialog = new JDialog(this, "Generating Report", true);
            generatingDialog.setLayout(new BorderLayout());
            generatingDialog.add(new JLabel("Generating password health report...", SwingConstants.CENTER), BorderLayout.CENTER);
            generatingDialog.setSize(300, 100);
            generatingDialog.setLocationRelativeTo(this);
            
            // Generate report in background
            new Thread(() -> {
                boolean success = PasswordHealthUtil.generateHealthReport(userId);
                
                SwingUtilities.invokeLater(() -> {
                    generatingDialog.dispose();
                    
                    if (success) {
                        JOptionPane.showMessageDialog(this, 
                            "Password health report generated and sent to your email.", 
                            "Report Generated", 
                            JOptionPane.INFORMATION_MESSAGE);
                        
                        // Reload report history
                        try (Connection conn = DatabaseUtil.getConnection()) {
                            String query = "SELECT report_date, overall_score, weak_passwords, reused_passwords, old_passwords " +
                                         "FROM password_health_reports WHERE user_id = ? ORDER BY report_date DESC LIMIT 10";
                            
                            reportsTableModel.setRowCount(0);
                            
                            try (PreparedStatement pstmt = conn.prepareStatement(query)) {
                                pstmt.setInt(1, userId);
                                ResultSet rs = pstmt.executeQuery();
                                
                                while (rs.next()) {
                                    java.sql.Timestamp reportDate = rs.getTimestamp("report_date");
                                    String formattedDate = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm").format(reportDate);
                                    
                                    reportsTableModel.addRow(new Object[]{
                                        formattedDate,
                                        rs.getInt("overall_score") + "%",
                                        rs.getInt("weak_passwords"),
                                        rs.getInt("reused_passwords"),
                                        rs.getInt("old_passwords")
                                    });
                                }
                            }
                        } catch (SQLException ex) {
                            showError("Error reloading report history: " + ex.getMessage());
                        }
                    } else {
                        showError("Failed to generate report. Please check logs for details.");
                    }
                });
            }).start();
            
            generatingDialog.setVisible(true);
        });
        
        viewReportButton.addActionListener(e -> {
            int selectedRow = reportsTable.getSelectedRow();
            if (selectedRow == -1) {
                showError("Please select a report to view.");
                return;
            }
            
            String reportDate = (String) reportsTableModel.getValueAt(selectedRow, 0);
            
            try (Connection conn = DatabaseUtil.getConnection()) {
                String query = "SELECT report_data FROM password_health_reports " +
                             "WHERE user_id = ? AND strftime('%Y-%m-%d %H:%M', report_date) = ? " +
                             "LIMIT 1";
                
                try (PreparedStatement pstmt = conn.prepareStatement(query)) {
                    pstmt.setInt(1, userId);
                    pstmt.setString(2, reportDate);
                    ResultSet rs = pstmt.executeQuery();
                    
                    if (rs.next()) {
                        String reportData = rs.getString("report_data");
                        
                        JTextArea reportTextArea = new JTextArea(20, 50);
                        reportTextArea.setText(reportData);
                        reportTextArea.setEditable(false);
                        reportTextArea.setCaretPosition(0);
                        
                        JScrollPane scrollPane = new JScrollPane(reportTextArea);
                        
                        JDialog reportDialog = new JDialog(this, "Password Health Report - " + reportDate, true);
                        reportDialog.setLayout(new BorderLayout());
                        reportDialog.add(scrollPane, BorderLayout.CENTER);
                        
                        JButton closeButton = new JButton("Close");
                        closeButton.addActionListener(event -> reportDialog.dispose());
                        
                        JPanel dialogButtonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
                        dialogButtonPanel.add(closeButton);
                        
                        reportDialog.add(dialogButtonPanel, BorderLayout.SOUTH);
                        reportDialog.pack();
                        reportDialog.setLocationRelativeTo(this);
                        reportDialog.setVisible(true);
                    } else {
                        showError("Report details not found.");
                    }
                }
            } catch (SQLException ex) {
                showError("Error loading report: " + ex.getMessage());
            }
        });
        
        reportButtonsPanel.add(generateReportButton);
        reportButtonsPanel.add(viewReportButton);
        
        reportsPanel.add(reportButtonsPanel, BorderLayout.SOUTH);
        
        // Add panels to main layout
        JPanel contentPanel = new JPanel(new BorderLayout(0, 10));
        contentPanel.add(settingsPanel, BorderLayout.NORTH);
        contentPanel.add(reportsPanel, BorderLayout.CENTER);
        
        panel.add(contentPanel, BorderLayout.CENTER);
        
        return panel;
    }
    
    private void editUrlPattern(int selectedRow) {
        if (selectedRow == -1) {
            showError("Please select a password to edit!");
            return;
        }
        
        String website = (String) passwordTableModel.getValueAt(selectedRow, 0);
        String username = (String) passwordTableModel.getValueAt(selectedRow, 1);
        
        try (Connection conn = DatabaseUtil.getConnection()) {
            // Get current URL pattern
            String currentPattern = null;
            int passwordId = -1;
            
            String query = "SELECT id, url_pattern FROM passwords WHERE user_id = ? AND website = ? AND username = ?";
            try (PreparedStatement pstmt = conn.prepareStatement(query)) {
                pstmt.setInt(1, userId);
                pstmt.setString(2, website);
                pstmt.setString(3, username);
                ResultSet rs = pstmt.executeQuery();
                
                if (rs.next()) {
                    passwordId = rs.getInt("id");
                    currentPattern = rs.getString("url_pattern");
                } else {
                    showError("Password entry not found!");
                    return;
                }
            }
            
            // Show dialog to edit URL pattern
            JPanel panel = new JPanel(new GridLayout(3, 1));
            panel.add(new JLabel("<html>Enter the URL pattern for auto-fill matching.<br/>" +
                              "Use % as a wildcard. Example: https://%.example.com/%</html>"));
            
            JTextField patternField = new JTextField(currentPattern != null ? currentPattern : "");
            panel.add(patternField);
            
            panel.add(new JLabel("<html><small>Leave blank to match exact website name only.</small></html>"));
            
            int result = JOptionPane.showConfirmDialog(this, panel, "Edit URL Pattern",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
                
            if (result == JOptionPane.OK_OPTION) {
                String newPattern = patternField.getText().trim();
                
                // Update URL pattern
                BrowserExtensionUtil.updateUrlPattern(passwordId, newPattern);
                
                JOptionPane.showMessageDialog(this, 
                    "URL pattern updated successfully!", 
                    "Pattern Updated", 
                    JOptionPane.INFORMATION_MESSAGE);
            }
        } catch (SQLException ex) {
            showError("Error updating URL pattern: " + ex.getMessage());
        }
    }
    
    private void updateAutoFillSetting(String website, String username, boolean enabled) {
        try (Connection conn = DatabaseUtil.getConnection()) {
            String query = "SELECT id FROM passwords WHERE user_id = ? AND website = ? AND username = ?";
            try (PreparedStatement pstmt = conn.prepareStatement(query)) {
                pstmt.setInt(1, userId);
                pstmt.setString(2, website);
                pstmt.setString(3, username);
                ResultSet rs = pstmt.executeQuery();
                
                if (rs.next()) {
                    int passwordId = rs.getInt("id");
                    BrowserExtensionUtil.toggleAutoFill(passwordId, enabled);
                }
            }
        } catch (SQLException ex) {
            showError("Error updating auto-fill setting: " + ex.getMessage());
        }
    }

    private JPanel createDataTransferPanel() {
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));
        
        // Create header
        JLabel headerLabel = new JLabel("Data Import & Export", SwingConstants.CENTER);
        headerLabel.setFont(new Font("Arial", Font.BOLD, 20));
        panel.add(headerLabel, BorderLayout.NORTH);
        
        // Create main panel with tabs
        JTabbedPane transferTabs = new JTabbedPane();
        transferTabs.addTab("Export", createExportPanel());
        transferTabs.addTab("Import", createImportPanel());
        transferTabs.addTab("Browser Import", createBrowserImportPanel());
        
        panel.add(transferTabs, BorderLayout.CENTER);
        
        return panel;
    }
    
    private JPanel createExportPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.fill = GridBagConstraints.HORIZONTAL;
        
        // Format selection section
        JPanel formatPanel = new JPanel();
        formatPanel.setBorder(BorderFactory.createTitledBorder("Export Format"));
        ButtonGroup formatGroup = new ButtonGroup();
        JRadioButton csvRadio = new JRadioButton("CSV");
        JRadioButton jsonRadio = new JRadioButton("JSON");
        csvRadio.setSelected(true);
        formatGroup.add(csvRadio);
        formatGroup.add(jsonRadio);
        formatPanel.add(csvRadio);
        formatPanel.add(jsonRadio);
        
        // Options section
        JPanel optionsPanel = new JPanel(new GridLayout(3, 1, 5, 5));
        optionsPanel.setBorder(BorderFactory.createTitledBorder("Export Options"));
        JCheckBox includePasswordsCheck = new JCheckBox("Include actual passwords in export");
        JCheckBox encryptExportCheck = new JCheckBox("Create encrypted export file");
        
        // Password field for encrypted export
        JPanel passwordPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        passwordPanel.add(new JLabel("Encryption Password:"));
        JPasswordField passwordField = new JPasswordField(20);
        passwordField.setEnabled(false);
        passwordPanel.add(passwordField);
        
        encryptExportCheck.addActionListener(e -> {
            passwordField.setEnabled(encryptExportCheck.isSelected());
            if (encryptExportCheck.isSelected()) {
                includePasswordsCheck.setSelected(true);
                includePasswordsCheck.setEnabled(false);
            } else {
                includePasswordsCheck.setEnabled(true);
            }
        });
        
        optionsPanel.add(includePasswordsCheck);
        optionsPanel.add(encryptExportCheck);
        optionsPanel.add(passwordPanel);
        
        // Action buttons
        JPanel actionPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
        JButton exportButton = new JButton("Export Data");
        
        exportButton.addActionListener(e -> {
            JFileChooser fileChooser = new JFileChooser();
            
            // Set file extension filter based on selected format and encryption
            if (encryptExportCheck.isSelected()) {
                fileChooser.setFileFilter(new javax.swing.filechooser.FileFilter() {
                    public boolean accept(File f) {
                        return f.isDirectory() || f.getName().toLowerCase().endsWith(".nhce");
                    }
                    public String getDescription() {
                        return "Encrypted NHCE Files (*.nhce)";
                    }
                });
                fileChooser.setSelectedFile(new File("passwords_export.nhce"));
            } else if (csvRadio.isSelected()) {
                fileChooser.setFileFilter(new javax.swing.filechooser.FileFilter() {
                    public boolean accept(File f) {
                        return f.isDirectory() || f.getName().toLowerCase().endsWith(".csv");
                    }
                    public String getDescription() {
                        return "CSV Files (*.csv)";
                    }
                });
                fileChooser.setSelectedFile(new File("passwords_export.csv"));
            } else {
                fileChooser.setFileFilter(new javax.swing.filechooser.FileFilter() {
                    public boolean accept(File f) {
                        return f.isDirectory() || f.getName().toLowerCase().endsWith(".json");
                    }
                    public String getDescription() {
                        return "JSON Files (*.json)";
                    }
                });
                fileChooser.setSelectedFile(new File("passwords_export.json"));
            }
            
            int result = fileChooser.showSaveDialog(this);
            if (result == JFileChooser.APPROVE_OPTION) {
                File selectedFile = fileChooser.getSelectedFile();
                String filePath = selectedFile.getAbsolutePath();
                
                // Ensure correct extension
                if (encryptExportCheck.isSelected() && !filePath.toLowerCase().endsWith(".nhce")) {
                    filePath += ".nhce";
                } else if (csvRadio.isSelected() && !filePath.toLowerCase().endsWith(".csv")) {
                    filePath += ".csv";
                } else if (jsonRadio.isSelected() && !filePath.toLowerCase().endsWith(".json")) {
                    filePath += ".json";
                }
                
                try {
                    boolean success = false;
                    String format = csvRadio.isSelected() ? "csv" : "json";
                    
                    if (encryptExportCheck.isSelected()) {
                        char[] password = passwordField.getPassword();
                        if (password.length == 0) {
                            JOptionPane.showMessageDialog(this, 
                                "Please enter an encryption password.",
                                "Missing Password",
                                JOptionPane.WARNING_MESSAGE);
                            return;
                        }
                        
                        success = DataTransferUtil.createEncryptedExport(userId, filePath, password, format);
                        
                        // Clear password from memory
                        for (int i = 0; i < password.length; i++) {
                            password[i] = 0;
                        }
                    } else if (csvRadio.isSelected()) {
                        success = DataTransferUtil.exportToCSV(userId, filePath, includePasswordsCheck.isSelected());
                    } else {
                        success = DataTransferUtil.exportToJSON(userId, filePath, includePasswordsCheck.isSelected());
                    }
                    
                    if (success) {
                        JOptionPane.showMessageDialog(this, 
                            "Data exported successfully to: " + filePath,
                            "Export Successful",
                            JOptionPane.INFORMATION_MESSAGE);
                    } else {
                        showError("Failed to export data. Check console for details.");
                    }
                } catch (Exception ex) {
                    showError("Error exporting data: " + ex.getMessage());
                    ex.printStackTrace();
                }
            }
        });
        
        actionPanel.add(exportButton);
        
        // Add components to panel
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.gridwidth = 2;
        panel.add(formatPanel, gbc);
        
        gbc.gridy = 1;
        panel.add(optionsPanel, gbc);
        
        gbc.gridy = 2;
        panel.add(actionPanel, gbc);
        
        // Add description
        JTextArea descriptionArea = new JTextArea(
            "Export your saved passwords to a file to backup or transfer to another device.\n\n" +
            "• CSV format can be imported into spreadsheet applications\n" +
            "• JSON format preserves more metadata\n" +
            "• Encrypted export securely protects your data with a password\n\n" +
            "Note: Including actual passwords in the export file is a security risk unless the file is encrypted."
        );
        descriptionArea.setEditable(false);
        descriptionArea.setLineWrap(true);
        descriptionArea.setWrapStyleWord(true);
        descriptionArea.setBackground(panel.getBackground());
        descriptionArea.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        
        gbc.gridy = 3;
        gbc.fill = GridBagConstraints.BOTH;
        gbc.weighty = 1.0;
        panel.add(new JScrollPane(descriptionArea), gbc);
        
        return panel;
    }
    
    private JPanel createImportPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.fill = GridBagConstraints.HORIZONTAL;
        
        // File selection
        JPanel filePanel = new JPanel(new BorderLayout(5, 0));
        filePanel.setBorder(BorderFactory.createTitledBorder("Import File"));
        JTextField filePathField = new JTextField();
        filePathField.setEditable(false);
        JButton browseButton = new JButton("Browse...");
        
        filePanel.add(filePathField, BorderLayout.CENTER);
        filePanel.add(browseButton, BorderLayout.EAST);
        
        // Format detection panel
        JPanel formatPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        formatPanel.setBorder(BorderFactory.createTitledBorder("File Format"));
        JLabel formatLabel = new JLabel("Format: Not detected");
        JLabel encryptedLabel = new JLabel("Encrypted: No");
        
        formatPanel.add(formatLabel);
        formatPanel.add(Box.createHorizontalStrut(20));
        formatPanel.add(encryptedLabel);
        
        // Password field for encrypted import
        JPanel passwordPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        passwordPanel.setBorder(BorderFactory.createTitledBorder("Decryption Password"));
        JPasswordField passwordField = new JPasswordField(20);
        passwordField.setEnabled(false);
        passwordPanel.add(passwordField);
        
        // Duplicate handling options
        JPanel duplicatePanel = new JPanel();
        duplicatePanel.setBorder(BorderFactory.createTitledBorder("Duplicate Handling"));
        ButtonGroup duplicateGroup = new ButtonGroup();
        JRadioButton skipRadio = new JRadioButton("Skip");
        JRadioButton replaceRadio = new JRadioButton("Replace");
        JRadioButton keepBothRadio = new JRadioButton("Keep Both");
        skipRadio.setSelected(true);
        
        duplicateGroup.add(skipRadio);
        duplicateGroup.add(replaceRadio);
        duplicateGroup.add(keepBothRadio);
        
        duplicatePanel.add(skipRadio);
        duplicatePanel.add(replaceRadio);
        duplicatePanel.add(keepBothRadio);
        
        // Action buttons
        JPanel actionPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
        JButton importButton = new JButton("Import Data");
        importButton.setEnabled(false);
        
        // File chooser action
        final File[] selectedFile = new File[1]; // Using array to allow modification in lambda
        
        browseButton.addActionListener(e -> {
            JFileChooser fileChooser = new JFileChooser();
            fileChooser.setFileFilter(new javax.swing.filechooser.FileFilter() {
                public boolean accept(File f) {
                    return f.isDirectory() || 
                           f.getName().toLowerCase().endsWith(".csv") ||
                           f.getName().toLowerCase().endsWith(".json") ||
                           f.getName().toLowerCase().endsWith(".nhce");
                }
                public String getDescription() {
                    return "Password Files (*.csv, *.json, *.nhce)";
                }
            });
            
            int result = fileChooser.showOpenDialog(this);
            if (result == JFileChooser.APPROVE_OPTION) {
                selectedFile[0] = fileChooser.getSelectedFile();
                filePathField.setText(selectedFile[0].getAbsolutePath());
                
                // Detect format
                String filename = selectedFile[0].getName().toLowerCase();
                boolean isEncrypted = filename.endsWith(".nhce");
                String format = "Unknown";
                
                if (isEncrypted) {
                    format = "Encrypted";
                    passwordField.setEnabled(true);
                } else if (filename.endsWith(".csv")) {
                    format = "CSV";
                    passwordField.setEnabled(false);
                } else if (filename.endsWith(".json")) {
                    format = "JSON";
                    passwordField.setEnabled(false);
                }
                
                formatLabel.setText("Format: " + format);
                encryptedLabel.setText("Encrypted: " + (isEncrypted ? "Yes" : "No"));
                importButton.setEnabled(true);
            }
        });
        
        // Import action
        importButton.addActionListener(e -> {
            if (selectedFile[0] == null) {
                showError("Please select a file to import");
                return;
            }
            
            String importMode = "skip";
            if (replaceRadio.isSelected()) importMode = "replace";
            if (keepBothRadio.isSelected()) importMode = "keep_both";
            
            String filePath = selectedFile[0].getAbsolutePath();
            boolean isEncrypted = filePath.toLowerCase().endsWith(".nhce");
            
            try {
                int importedCount = 0;
                
                if (isEncrypted) {
                    char[] password = passwordField.getPassword();
                    if (password.length == 0) {
                        JOptionPane.showMessageDialog(this, 
                            "Please enter the decryption password.",
                            "Missing Password",
                            JOptionPane.WARNING_MESSAGE);
                        return;
                    }
                    
                    importedCount = DataTransferUtil.importFromEncryptedFile(userId, filePath, password, importMode);
                    
                    // Clear password from memory
                    for (int i = 0; i < password.length; i++) {
                        password[i] = 0;
                    }
                } else if (filePath.toLowerCase().endsWith(".csv")) {
                    importedCount = DataTransferUtil.importFromCSV(userId, filePath, importMode);
                } else if (filePath.toLowerCase().endsWith(".json")) {
                    importedCount = DataTransferUtil.importFromJSON(userId, filePath, importMode);
                } else {
                    showError("Unsupported file format");
                    return;
                }
                
                if (importedCount > 0) {
                    JOptionPane.showMessageDialog(this, 
                        importedCount + " passwords imported successfully!",
                        "Import Successful",
                        JOptionPane.INFORMATION_MESSAGE);
                        
                    // Refresh password list
                    loadPasswords();
                } else if (importedCount == 0) {
                    JOptionPane.showMessageDialog(this, 
                        "No passwords were imported. The file may be empty or contain only duplicates that were skipped.",
                        "No Passwords Imported",
                        JOptionPane.INFORMATION_MESSAGE);
                } else {
                    showError("Failed to import passwords. Check console for details.");
                }
            } catch (Exception ex) {
                showError("Error importing data: " + ex.getMessage());
                ex.printStackTrace();
            }
        });
        
        actionPanel.add(importButton);
        
        // Add components to panel
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.gridwidth = 2;
        panel.add(filePanel, gbc);
        
        gbc.gridy = 1;
        panel.add(formatPanel, gbc);
        
        gbc.gridy = 2;
        panel.add(passwordPanel, gbc);
        
        gbc.gridy = 3;
        panel.add(duplicatePanel, gbc);
        
        gbc.gridy = 4;
        panel.add(actionPanel, gbc);
        
        // Add description
        JTextArea descriptionArea = new JTextArea(
            "Import passwords from CSV or JSON files exported from this app or other password managers.\n\n" +
            "• CSV format should have at least Website, Username, and Password columns\n" +
            "• JSON format should contain a 'passwords' array with objects containing website, username, and password fields\n" +
            "• Encrypted files require the original password used during export\n\n" +
            "Duplicate handling:\n" +
            "• Skip: Ignore passwords that already exist in your database\n" +
            "• Replace: Replace existing passwords with imported ones\n" +
            "• Keep Both: Import all passwords, adding '(Imported)' to duplicates"
        );
        descriptionArea.setEditable(false);
        descriptionArea.setLineWrap(true);
        descriptionArea.setWrapStyleWord(true);
        descriptionArea.setBackground(panel.getBackground());
        descriptionArea.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        
        gbc.gridy = 5;
        gbc.fill = GridBagConstraints.BOTH;
        gbc.weighty = 1.0;
        panel.add(new JScrollPane(descriptionArea), gbc);
        
        return panel;
    }
    
    private JPanel createBrowserImportPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.fill = GridBagConstraints.HORIZONTAL;
        
        // Browser selection
        JPanel browserPanel = new JPanel();
        browserPanel.setBorder(BorderFactory.createTitledBorder("Select Browser"));
        ButtonGroup browserGroup = new ButtonGroup();
        JRadioButton chromeRadio = new JRadioButton("Google Chrome");
        JRadioButton firefoxRadio = new JRadioButton("Mozilla Firefox");
        chromeRadio.setSelected(true);
        
        browserGroup.add(chromeRadio);
        browserGroup.add(firefoxRadio);
        browserPanel.add(chromeRadio);
        browserPanel.add(firefoxRadio);
        
        // Duplicate handling options
        JPanel duplicatePanel = new JPanel();
        duplicatePanel.setBorder(BorderFactory.createTitledBorder("Duplicate Handling"));
        ButtonGroup duplicateGroup = new ButtonGroup();
        JRadioButton skipRadio = new JRadioButton("Skip");
        JRadioButton replaceRadio = new JRadioButton("Replace");
        JRadioButton keepBothRadio = new JRadioButton("Keep Both");
        skipRadio.setSelected(true);
        
        duplicateGroup.add(skipRadio);
        duplicateGroup.add(replaceRadio);
        duplicateGroup.add(keepBothRadio);
        
        duplicatePanel.add(skipRadio);
        duplicatePanel.add(replaceRadio);
        duplicatePanel.add(keepBothRadio);
        
        // Action buttons
        JPanel actionPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
        JButton importButton = new JButton("Import from Browser");
        
        importButton.addActionListener(e -> {
            String browserType = chromeRadio.isSelected() ? "chrome" : "firefox";
            
            String importMode = "skip";
            if (replaceRadio.isSelected()) importMode = "replace";
            if (keepBothRadio.isSelected()) importMode = "keep_both";
            
            try {
                int importedCount = DataTransferUtil.importFromBrowser(userId, browserType, importMode);
                
                if (importedCount > 0) {
                    JOptionPane.showMessageDialog(this, 
                        importedCount + " passwords imported successfully from " + browserType + "!",
                        "Import Successful",
                        JOptionPane.INFORMATION_MESSAGE);
                        
                    // Refresh password list
                    loadPasswords();
                } else if (importedCount == 0) {
                    JOptionPane.showMessageDialog(this, 
                        "No passwords were imported from " + browserType + ".",
                        "No Passwords Imported",
                        JOptionPane.INFORMATION_MESSAGE);
                } else {
                    showError("Failed to import passwords. Check console for details.");
                }
            } catch (Exception ex) {
                showError("Error importing from browser: " + ex.getMessage());
                ex.printStackTrace();
            }
        });
        
        actionPanel.add(importButton);
        
        // Add components to panel
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.gridwidth = 2;
        panel.add(browserPanel, gbc);
        
        gbc.gridy = 1;
        panel.add(duplicatePanel, gbc);
        
        gbc.gridy = 2;
        panel.add(actionPanel, gbc);
        
        // Add description and instructions
        JTextArea descriptionArea = new JTextArea(
            "Import passwords directly from your web browser.\n\n" +
            "IMPORTANT NOTE: This feature has limitations due to browser security:\n\n" +
            "• Chrome passwords are stored in an encrypted database that requires platform-specific code to access\n" +
            "• Firefox passwords require access to its NSS security libraries\n\n" +
            "As an alternative, please follow these steps to import browser passwords:\n\n" +
            "1. In Chrome: Settings > Passwords > ⋮ > Export passwords\n" +
            "2. In Firefox: about:logins > ⋯ > Export Logins\n" +
            "3. Save the file somewhere secure\n" +
            "4. Use the Import tab to import the saved file\n\n" +
            "This ensures your passwords can be imported securely and correctly."
        );
        descriptionArea.setEditable(false);
        descriptionArea.setLineWrap(true);
        descriptionArea.setWrapStyleWord(true);
        descriptionArea.setBackground(panel.getBackground());
        descriptionArea.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        
        gbc.gridy = 3;
        gbc.fill = GridBagConstraints.BOTH;
        gbc.weighty = 1.0;
        panel.add(new JScrollPane(descriptionArea), gbc);
        
        return panel;
    }

    @Override
    public void dispose() {
        // Stop the browser extension server when closing the application
        BrowserExtensionUtil.stopExtensionServer();
        super.dispose();
    }

    /**
     * Creates the Secure Notes panel with all functionality
     */
    private JPanel createSecureNotesPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        
        // Create header section
        JPanel headerPanel = new JPanel(new BorderLayout());
        JLabel titleLabel = new JLabel("Secure Encrypted Notes", SwingConstants.LEFT);
        titleLabel.setFont(new Font("Arial", Font.BOLD, 18));
        headerPanel.add(titleLabel, BorderLayout.WEST);
        
        // Search panel
        JPanel searchPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JTextField searchField = new JTextField(20);
        JButton searchButton = new JButton("Search");
        searchPanel.add(new JLabel("Search:"));
        searchPanel.add(searchField);
        searchPanel.add(searchButton);
        headerPanel.add(searchPanel, BorderLayout.EAST);
        
        panel.add(headerPanel, BorderLayout.NORTH);
        
        // Create the main split pane
        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
        splitPane.setDividerLocation(250);
        splitPane.setOneTouchExpandable(true);
        
        // Create notes list panel (left side)
        JPanel notesListPanel = createNotesListPanel();
        
        // Create note editor panel (right side)
        JPanel noteEditorPanel = createNoteEditorPanel();
        
        splitPane.setLeftComponent(notesListPanel);
        splitPane.setRightComponent(noteEditorPanel);
        
        panel.add(splitPane, BorderLayout.CENTER);
        
        // Configure the search button
        searchButton.addActionListener(e -> searchNotes(searchField.getText()));
        
        // Initialize the secure notes tables if they don't exist
        try {
            SecureNotesUtil.createNotesTableIfNotExists();
        } catch (SQLException e) {
            showError("Failed to initialize secure notes: " + e.getMessage());
        }
        
        // Load notes
        loadNotes();
        
        return panel;
    }
    
    // Notes list variables
    private DefaultListModel<NoteListItem> notesListModel;
    private JList<NoteListItem> notesList;
    
    // Note editor variables
    private JTextField titleField;
    private JComboBox<String> categoryCombo;
    private JComboBox<Integer> noteTypeCombo;
    private JTextField tagsField;
    private JComboBox<String> colorCombo;
    private JCheckBox favoriteCheckbox;
    private JTextPane contentPane;
    private int currentNoteId = 0;
    
    /**
     * Creates the notes list panel (left side of the split pane)
     */
    private JPanel createNotesListPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createEmptyBorder(0, 0, 0, 10),
            BorderFactory.createTitledBorder("My Notes")
        ));
        
        // Create the list model and list component
        notesListModel = new DefaultListModel<>();
        notesList = new JList<>(notesListModel);
        notesList.setCellRenderer(new NoteListCellRenderer());
        notesList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        
        // Add selection listener
        notesList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                int selectedIndex = notesList.getSelectedIndex();
                if (selectedIndex != -1) {
                    NoteListItem selectedNote = notesListModel.getElementAt(selectedIndex);
                    loadNoteDetails(selectedNote.getId());
                }
            }
        });
        
        JScrollPane scrollPane = new JScrollPane(notesList);
        panel.add(scrollPane, BorderLayout.CENTER);
        
        // Button panel
        JPanel buttonPanel = new JPanel(new GridLayout(1, 3, 5, 0));
        JButton newButton = new JButton("New");
        JButton deleteButton = new JButton("Delete");
        JButton templateButton = new JButton("Templates");
        
        buttonPanel.add(newButton);
        buttonPanel.add(deleteButton);
        buttonPanel.add(templateButton);
        
        // Button actions
        newButton.addActionListener(e -> createNewNote());
        deleteButton.addActionListener(e -> deleteSelectedNote());
        templateButton.addActionListener(e -> showTemplateDialog());
        
        panel.add(buttonPanel, BorderLayout.SOUTH);
        
        return panel;
    }
    
    /**
     * Creates the note editor panel (right side of the split pane)
     */
    private JPanel createNoteEditorPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createEmptyBorder(0, 10, 0, 0),
            BorderFactory.createTitledBorder("Note Editor")
        ));
        
        // Editor form panel
        JPanel formPanel = new JPanel(new BorderLayout());
        
        // Editor fields panel
        JPanel fieldsPanel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.fill = GridBagConstraints.HORIZONTAL;
        
        // Title field
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.gridwidth = 1;
        fieldsPanel.add(new JLabel("Title:"), gbc);
        
        gbc.gridx = 1;
        gbc.gridwidth = 3;
        titleField = new JTextField(30);
        fieldsPanel.add(titleField, gbc);
        
        // Category field
        gbc.gridx = 0;
        gbc.gridy = 1;
        gbc.gridwidth = 1;
        fieldsPanel.add(new JLabel("Category:"), gbc);
        
        gbc.gridx = 1;
        gbc.gridwidth = 1;
        categoryCombo = new JComboBox<>();
        categoryCombo.setEditable(true);
        fieldsPanel.add(categoryCombo, gbc);
        
        // Note type field
        gbc.gridx = 2;
        gbc.gridwidth = 1;
        fieldsPanel.add(new JLabel("Type:"), gbc);
        
        gbc.gridx = 3;
        noteTypeCombo = new JComboBox<>(new Integer[]{
            SecureNotesUtil.NOTE_TYPE_GENERIC,
            SecureNotesUtil.NOTE_TYPE_CREDIT_CARD,
            SecureNotesUtil.NOTE_TYPE_ID_DOCUMENT,
            SecureNotesUtil.NOTE_TYPE_PASSWORD,
            SecureNotesUtil.NOTE_TYPE_SOFTWARE_LICENSE
        });
        
        // Note type renderer to display friendly names
        noteTypeCombo.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                          boolean isSelected, boolean cellHasFocus) {
                int type = (Integer) value;
                String text;
                switch (type) {
                    case 0:
                        text = "Generic Note";
                        break;
                    case 1:
                        text = "Credit Card";
                        break;
                    case 2:
                        text = "ID Document";
                        break;
                    case 3:
                        text = "Password";
                        break;
                    case 4:
                        text = "Software License";
                        break;
                    default:
                        text = "Unknown Type";
                }
                return super.getListCellRendererComponent(list, text, index, isSelected, cellHasFocus);
            }
        });
        
        // Add note type change listener to load templates
        noteTypeCombo.addActionListener(e -> {
            String currentText = contentPane.getText();
            if (titleField.getText().isEmpty() && (currentText == null || currentText.isEmpty())) {
                // Only load template if this is a new note (empty fields)
                loadTemplateForType((Integer) noteTypeCombo.getSelectedItem());
            }
        });
        
        fieldsPanel.add(noteTypeCombo, gbc);
        
        // Tags field
        gbc.gridx = 0;
        gbc.gridy = 2;
        gbc.gridwidth = 1;
        fieldsPanel.add(new JLabel("Tags:"), gbc);
        
        gbc.gridx = 1;
        gbc.gridwidth = 3;
        tagsField = new JTextField();
        fieldsPanel.add(tagsField, gbc);
        
        // Color and Favorite
        gbc.gridx = 0;
        gbc.gridy = 3;
        gbc.gridwidth = 1;
        fieldsPanel.add(new JLabel("Color:"), gbc);
        
        gbc.gridx = 1;
        gbc.gridwidth = 1;
        colorCombo = new JComboBox<>(new String[]{
            "Default", "Red", "Green", "Blue", "Yellow", "Orange", "Purple", "Pink"
        });
        fieldsPanel.add(colorCombo, gbc);
        
        gbc.gridx = 2;
        gbc.gridwidth = 2;
        favoriteCheckbox = new JCheckBox("Mark as Favorite");
        fieldsPanel.add(favoriteCheckbox, gbc);
        
        formPanel.add(fieldsPanel, BorderLayout.NORTH);
        
        // Content text area with rich text formatting
        contentPane = new JTextPane();
        contentPane.setContentType("text/rtf");
        
        // Add formatting toolbar
        JPanel toolbarPanel = createRichTextToolbar(contentPane);
        
        // Add editor and toolbar to panel
        JPanel editorPanel = new JPanel(new BorderLayout());
        editorPanel.add(toolbarPanel, BorderLayout.NORTH);
        editorPanel.add(new JScrollPane(contentPane), BorderLayout.CENTER);
        
        formPanel.add(editorPanel, BorderLayout.CENTER);
        
        panel.add(formPanel, BorderLayout.CENTER);
        
        // Save button
        JButton saveButton = new JButton("Save Note");
        saveButton.addActionListener(e -> saveCurrentNote());
        
        panel.add(saveButton, BorderLayout.SOUTH);
        
        return panel;
    }
    
    /**
     * Creates a toolbar with rich text formatting buttons
     */
    private JPanel createRichTextToolbar(JTextPane textPane) {
        JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.LEFT));
        
        // Define styles for formatting
        final String BOLD = "bold";
        final String ITALIC = "italic";
        final String UNDERLINE = "underline";
        
        // Create action for toggling bold
        AbstractAction boldAction = new StyledEditorKit.BoldAction();
        boldAction.putValue(Action.NAME, "B");
        boldAction.putValue(Action.SHORT_DESCRIPTION, "Bold");
        
        // Create action for toggling italic
        AbstractAction italicAction = new StyledEditorKit.ItalicAction();
        italicAction.putValue(Action.NAME, "I");
        italicAction.putValue(Action.SHORT_DESCRIPTION, "Italic");
        
        // Create action for toggling underline
        AbstractAction underlineAction = new StyledEditorKit.UnderlineAction();
        underlineAction.putValue(Action.NAME, "U");
        underlineAction.putValue(Action.SHORT_DESCRIPTION, "Underline");
        
        // Font family selector
        String[] fontFamilies = GraphicsEnvironment.getLocalGraphicsEnvironment()
                .getAvailableFontFamilyNames();
        JComboBox<String> fontFamily = new JComboBox<>(fontFamilies);
        fontFamily.setSelectedItem("Arial");
        fontFamily.setMaximumSize(new Dimension(150, 25));
        fontFamily.addActionListener(e -> {
            String family = (String) fontFamily.getSelectedItem();
            MutableAttributeSet attr = new SimpleAttributeSet();
            StyleConstants.setFontFamily(attr, family);
            textPane.setCharacterAttributes(attr, false);
        });
        
        // Font size selector
        Integer[] fontSizes = {8, 10, 12, 14, 16, 18, 20, 24, 28, 36, 48, 72};
        JComboBox<Integer> fontSize = new JComboBox<>(fontSizes);
        fontSize.setSelectedItem(12);
        fontSize.setMaximumSize(new Dimension(50, 25));
        fontSize.addActionListener(e -> {
            int size = (Integer) fontSize.getSelectedItem();
            MutableAttributeSet attr = new SimpleAttributeSet();
            StyleConstants.setFontSize(attr, size);
            textPane.setCharacterAttributes(attr, false);
        });
        
        // Create buttons
        JButton boldButton = new JButton(boldAction);
        JButton italicButton = new JButton(italicAction);
        JButton underlineButton = new JButton(underlineAction);
        
        // Color chooser
        JButton colorButton = new JButton("Color");
        colorButton.addActionListener(e -> {
            Color selectedColor = JColorChooser.showDialog(this, "Choose Text Color", Color.BLACK);
            if (selectedColor != null) {
                MutableAttributeSet attr = new SimpleAttributeSet();
                StyleConstants.setForeground(attr, selectedColor);
                textPane.setCharacterAttributes(attr, false);
            }
        });
        
        // Paragraph alignment buttons
        JButton leftAlignButton = new JButton(new StyledEditorKit.AlignmentAction("Left", StyleConstants.ALIGN_LEFT));
        leftAlignButton.setText("L");
        leftAlignButton.setToolTipText("Left Align");
        
        JButton centerAlignButton = new JButton(new StyledEditorKit.AlignmentAction("Center", StyleConstants.ALIGN_CENTER));
        centerAlignButton.setText("C");
        centerAlignButton.setToolTipText("Center Align");
        
        JButton rightAlignButton = new JButton(new StyledEditorKit.AlignmentAction("Right", StyleConstants.ALIGN_RIGHT));
        rightAlignButton.setText("R");
        rightAlignButton.setToolTipText("Right Align");
        
        // Add components to toolbar
        toolbar.add(new JLabel("Font:"));
        toolbar.add(fontFamily);
        toolbar.add(new JLabel("Size:"));
        toolbar.add(fontSize);
        toolbar.add(boldButton);
        toolbar.add(italicButton);
        toolbar.add(underlineButton);
        toolbar.add(colorButton);
        toolbar.add(leftAlignButton);
        toolbar.add(centerAlignButton);
        toolbar.add(rightAlignButton);
        
        return toolbar;
    }
    
    /**
     * List item class for notes list
     */
    private class NoteListItem {
        private int id;
        private String title;
        private int noteType;
        private String category;
        private boolean favorite;
        private String color;
        private Date modifiedDate;
        
        public NoteListItem(int id, String title, int noteType, String category, 
                            boolean favorite, String color, Date modifiedDate) {
            this.id = id;
            this.title = title;
            this.noteType = noteType;
            this.category = category;
            this.favorite = favorite;
            this.color = color;
            this.modifiedDate = modifiedDate;
        }
        
        public int getId() {
            return id;
        }
        
        public String getTitle() {
            return title;
        }
        
        public int getNoteType() {
            return noteType;
        }
        
        public String getCategory() {
            return category;
        }
        
        public boolean isFavorite() {
            return favorite;
        }
        
        public String getColor() {
            return color;
        }
        
        public Date getModifiedDate() {
            return modifiedDate;
        }
        
        @Override
        public String toString() {
            return title;
        }
    }
    
    /**
     * Custom renderer for note list items
     */
    private class NoteListCellRenderer extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value, 
                                                    int index, boolean isSelected, boolean cellHasFocus) {
            JLabel label = (JLabel) super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
            
            NoteListItem item = (NoteListItem) value;
            
            // Create a rich display for the note
            StringBuilder displayText = new StringBuilder("<html>");
            displayText.append("<b>").append(item.getTitle()).append("</b>");
            
            if (item.getCategory() != null && !item.getCategory().isEmpty()) {
                displayText.append(" <i>(").append(item.getCategory()).append(")</i>");
            }
            
            if (item.isFavorite()) {
                displayText.append(" ★");
            }
            
            displayText.append("<br><small>");
            
            // Note type
            String typeText;
            switch (item.getNoteType()) {
                case 0: typeText = "Note"; break;
                case 1: typeText = "Credit Card"; break;
                case 2: typeText = "ID Document"; break;
                case 3: typeText = "Password"; break;
                case 4: typeText = "Software License"; break;
                default: typeText = "Unknown Type";
            }
            displayText.append(typeText);
            
            // Modified date
            if (item.getModifiedDate() != null) {
                SimpleDateFormat sdf = new SimpleDateFormat("MM/dd/yyyy HH:mm");
                displayText.append(" • Last modified: ").append(sdf.format(item.getModifiedDate()));
            }
            
            displayText.append("</small></html>");
            
            label.setText(displayText.toString());
            
            // Set custom color if specified and not selected
            if (!isSelected && item.getColor() != null && !item.getColor().isEmpty() && !"Default".equals(item.getColor())) {
                try {
                    Field field = Color.class.getField(item.getColor().toLowerCase());
                    Color color = (Color) field.get(null);
                    label.setBackground(new Color(color.getRed(), color.getGreen(), color.getBlue(), 50));
                    label.setOpaque(true);
                } catch (Exception e) {
                    // Ignore if color can't be set
                }
            }
            
            return label;
        }
    }

    /**
     * Loads all notes for the current user
     */
    private void loadNotes() {
        try {
            notesListModel.clear();
            
            List<Map<String, Object>> notes = com.datamanager.util.SecureNotesUtil.getAllNotes(userId);
            
            // Populate categories dropdown
            updateCategoriesDropdown();
            
            for (Map<String, Object> note : notes) {
                int id = (int) note.get("id");
                String title = (String) note.get("title");
                int noteType = (int) note.get("noteType");
                String category = (String) note.get("category");
                boolean favorite = (boolean) note.get("favorite");
                String color = (String) note.get("color");
                Date modifiedDate = (Date) note.get("modifiedDate");
                
                notesListModel.addElement(new NoteListItem(id, title, noteType, category, favorite, color, modifiedDate));
            }
            
            // Clear editor
            clearNoteEditor();
            
        } catch (SQLException e) {
            showError("Error loading notes: " + e.getMessage());
        }
    }
    
    /**
     * Searches notes by title, category, or tags
     */
    private void searchNotes(String searchText) {
        try {
            if (searchText == null || searchText.trim().isEmpty()) {
                // If search is empty, load all notes
                loadNotes();
                return;
            }
            
            notesListModel.clear();
            List<Map<String, Object>> notes = com.datamanager.util.SecureNotesUtil.searchNotes(userId, searchText);
            
            for (Map<String, Object> note : notes) {
                int id = (int) note.get("id");
                String title = (String) note.get("title");
                int noteType = (int) note.get("noteType");
                String category = (String) note.get("category");
                boolean favorite = (boolean) note.get("favorite");
                String color = (String) note.get("color");
                Date modifiedDate = (Date) note.get("modifiedDate");
                
                notesListModel.addElement(new NoteListItem(id, title, noteType, category, favorite, color, modifiedDate));
            }
            
        } catch (SQLException e) {
            showError("Error searching notes: " + e.getMessage());
        }
    }
    
    /**
     * Loads a specific note's details into the editor
     */
    private void loadNoteDetails(int noteId) {
        try {
            Map<String, Object> note = com.datamanager.util.SecureNotesUtil.getNoteById(noteId, userId);
            
            if (note.isEmpty()) {
                showError("Note not found or access denied.");
                return;
            }
            
            currentNoteId = noteId;
            titleField.setText((String) note.get("title"));
            
            // Set category
            String category = (String) note.get("category");
            categoryCombo.setSelectedItem(category != null ? category : "");
            
            // Set note type
            noteTypeCombo.setSelectedItem(note.get("noteType"));
            
            // Set tags
            tagsField.setText((String) note.get("tags"));
            
            // Set color
            String color = (String) note.get("color");
            colorCombo.setSelectedItem(color != null ? color : "Default");
            
            // Set favorite
            favoriteCheckbox.setSelected((boolean) note.get("favorite"));
            
            // Set content
            String rtfContent = (String) note.get("content");
            if (rtfContent != null && !rtfContent.isEmpty()) {
                contentPane.setContentType("text/rtf");
                contentPane.setText(rtfContent);
            } else {
                contentPane.setContentType("text/plain");
                contentPane.setText("");
            }
            
        } catch (SQLException e) {
            showError("Error loading note: " + e.getMessage());
        }
    }
    
    /**
     * Saves the current note
     */
    private void saveCurrentNote() {
        try {
            String title = titleField.getText().trim();
            
            if (title.isEmpty()) {
                showError("Title is required.");
                return;
            }
            
            // Get RTF content
            String rtfContent;
            
            // Check if we have RTF content
            if (contentPane.getContentType().equals("text/rtf")) {
                // Get RTF content directly
                rtfContent = contentPane.getText();
            } else {
                // Convert plain text to RTF
                StringWriter writer = new StringWriter();
                new RTFEditorKit().write(writer, contentPane.getDocument(), 0, contentPane.getDocument().getLength());
                rtfContent = writer.toString();
            }
            
            // Get other field values
            int noteType = (Integer) noteTypeCombo.getSelectedItem();
            String category = (String) categoryCombo.getSelectedItem();
            String tags = tagsField.getText().trim();
            String color = (String) colorCombo.getSelectedItem();
            boolean favorite = favoriteCheckbox.isSelected();
            
            // Save note
            int savedNoteId = com.datamanager.util.SecureNotesUtil.saveNote(
                currentNoteId, userId, title, rtfContent, noteType, category, tags, color, favorite);
            
            if (savedNoteId > 0) {
                // Update currentNoteId in case this was a new note
                currentNoteId = savedNoteId;
                
                // Refresh notes list
                loadNotes();
                
                // Find and select the saved note in the list
                for (int i = 0; i < notesListModel.getSize(); i++) {
                    if (notesListModel.getElementAt(i).getId() == currentNoteId) {
                        notesList.setSelectedIndex(i);
                        break;
                    }
                }
                
                JOptionPane.showMessageDialog(this, "Note saved successfully.", "Success", JOptionPane.INFORMATION_MESSAGE);
            }
            
        } catch (Exception e) {
            showError("Error saving note: " + e.getMessage());
        }
    }
    
    /**
     * Creates a new empty note
     */
    private void createNewNote() {
        clearNoteEditor();
        notesList.clearSelection();
    }
    
    /**
     * Clears the note editor fields
     */
    private void clearNoteEditor() {
        currentNoteId = 0;
        titleField.setText("");
        categoryCombo.setSelectedItem("");
        noteTypeCombo.setSelectedItem(0); // Use the integer value directly instead of the constant
        tagsField.setText("");
        colorCombo.setSelectedItem("Default");
        favoriteCheckbox.setSelected(false);
        contentPane.setText("");
    }
    
    /**
     * Deletes the selected note
     */
    private void deleteSelectedNote() {
        int selectedIndex = notesList.getSelectedIndex();
        
        if (selectedIndex == -1) {
            showError("Please select a note to delete.");
            return;
        }
        
        NoteListItem selectedNote = notesListModel.getElementAt(selectedIndex);
        
        int confirm = JOptionPane.showConfirmDialog(
            this,
            "Are you sure you want to delete the note \"" + selectedNote.getTitle() + "\"?",
            "Confirm Delete",
            JOptionPane.YES_NO_OPTION,
            JOptionPane.WARNING_MESSAGE
        );
        
        if (confirm == JOptionPane.YES_OPTION) {
            try {
                boolean deleted = com.datamanager.util.SecureNotesUtil.deleteNote(selectedNote.getId(), userId);
                
                if (deleted) {
                    loadNotes();
                    clearNoteEditor();
                    JOptionPane.showMessageDialog(this, "Note deleted successfully.", "Success", JOptionPane.INFORMATION_MESSAGE);
                } else {
                    showError("Failed to delete note.");
                }
                
            } catch (SQLException e) {
                showError("Error deleting note: " + e.getMessage());
            }
        }
    }
    
    /**
     * Updates the categories dropdown with available categories
     */
    private void updateCategoriesDropdown() {
        try {
            String currentSelection = (String) categoryCombo.getSelectedItem();
            
            categoryCombo.removeAllItems();
            categoryCombo.addItem(""); // Empty option
            
            List<String> categories = com.datamanager.util.SecureNotesUtil.getNoteCategories(userId);
            
            for (String category : categories) {
                categoryCombo.addItem(category);
            }
            
            // Restore previous selection if it exists
            if (currentSelection != null && !currentSelection.isEmpty()) {
                categoryCombo.setSelectedItem(currentSelection);
            }
            
        } catch (SQLException e) {
            // Silently fail
            e.printStackTrace();
        }
    }
    
    /**
     * Shows the template selection dialog
     */
    private void showTemplateDialog() {
        try {
            // Get available templates
            List<Map<String, Object>> templates = com.datamanager.util.SecureNotesUtil.getTemplates(userId);
            
            if (templates.isEmpty()) {
                JOptionPane.showMessageDialog(this, 
                    "No templates available. Default templates will be available when creating notes.",
                    "No Templates", JOptionPane.INFORMATION_MESSAGE);
                return;
            }
            
            // Create template selection dialog
            JDialog dialog = new JDialog(this, "Select Template", true);
            dialog.setLayout(new BorderLayout());
            
            // Create template list
            DefaultListModel<TemplateListItem> templateListModel = new DefaultListModel<>();
            
            for (Map<String, Object> template : templates) {
                int id = (int) template.get("id");
                String name = (String) template.get("name");
                int templateType = (int) template.get("templateType");
                
                templateListModel.addElement(new TemplateListItem(id, name, templateType));
            }
            
            JList<TemplateListItem> templateList = new JList<>(templateListModel);
            templateList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
            
            // Add list to scroll pane
            JScrollPane scrollPane = new JScrollPane(templateList);
            dialog.add(scrollPane, BorderLayout.CENTER);
            
            // Add buttons
            JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
            JButton cancelButton = new JButton("Cancel");
            JButton selectButton = new JButton("Select");
            
            cancelButton.addActionListener(e -> dialog.dispose());
            
            selectButton.addActionListener(e -> {
                int selectedIndex = templateList.getSelectedIndex();
                
                if (selectedIndex != -1) {
                    TemplateListItem selectedTemplate = templateListModel.getElementAt(selectedIndex);
                    applyTemplate(selectedTemplate.getId());
                    dialog.dispose();
                } else {
                    JOptionPane.showMessageDialog(dialog, 
                        "Please select a template.", 
                        "No Selection", JOptionPane.WARNING_MESSAGE);
                }
            });
            
            buttonPanel.add(cancelButton);
            buttonPanel.add(selectButton);
            dialog.add(buttonPanel, BorderLayout.SOUTH);
            
            // Show dialog
            dialog.setSize(400, 300);
            dialog.setLocationRelativeTo(this);
            dialog.setVisible(true);
            
        } catch (SQLException e) {
            showError("Error loading templates: " + e.getMessage());
        }
    }
    
    /**
     * Applies a template to the current note
     */
    private void applyTemplate(int templateId) {
        try {
            String templateContent = com.datamanager.util.SecureNotesUtil.getTemplateContent(templateId);
            
            if (templateContent != null) {
                // Apply template content
                contentPane.setContentType("text/rtf");
                contentPane.setText(templateContent);
            }
            
        } catch (SQLException e) {
            showError("Error applying template: " + e.getMessage());
        }
    }
    
    /**
     * Loads a template for the selected note type
     */
    private void loadTemplateForType(int noteType) {
        try {
            // Get available templates
            List<Map<String, Object>> templates = com.datamanager.util.SecureNotesUtil.getTemplates(userId);
            
            // Find a matching system template
            for (Map<String, Object> template : templates) {
                int templateType = (int) template.get("templateType");
                
                if (templateType == noteType) {
                    applyTemplate((int) template.get("id"));
                    break;
                }
            }
            
        } catch (SQLException e) {
            // Silently fail
            e.printStackTrace();
        }
    }
    
    /**
     * Template list item class
     */
    private class TemplateListItem {
        private int id;
        private String name;
        private int templateType;
        
        public TemplateListItem(int id, String name, int templateType) {
            this.id = id;
            this.name = name;
            this.templateType = templateType;
        }
        
        public int getId() {
            return id;
        }
        
        public String getName() {
            return name;
        }
        
        public int getTemplateType() {
            return templateType;
        }
        
        @Override
        public String toString() {
            return name;
        }
    }
} 