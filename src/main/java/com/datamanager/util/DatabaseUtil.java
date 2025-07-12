package com.datamanager.util;

import java.sql.*;
import java.io.File;

public class DatabaseUtil {
    private static final String DB_FILE = "personal_data.db";
    private static Connection connection = null;
    
    static {
        try {
            // Load the SQLite JDBC driver
            Class.forName("org.sqlite.JDBC");
        } catch (ClassNotFoundException e) {
            System.err.println("SQLite JDBC Driver not found: " + e.getMessage());
        }
    }
    
    public static Connection getConnection() throws SQLException {
        if (connection == null || connection.isClosed()) {
            // Create database directory if it doesn't exist
            File dbFile = new File(DB_FILE);
            File parentDir = dbFile.getParentFile();
            if (parentDir != null && !parentDir.exists()) {
                parentDir.mkdirs();
            }
            
            // Connect to database
            connection = DriverManager.getConnection("jdbc:sqlite:" + DB_FILE);
            createTablesIfNotExist();
            updateDatabaseSchema();
        }
        return connection;
    }
    
    private static void createTablesIfNotExist() throws SQLException {
        try (Statement stmt = connection.createStatement()) {
            // Users table
            stmt.execute(
                "CREATE TABLE IF NOT EXISTS users (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "username VARCHAR(50) UNIQUE NOT NULL, " +
                "password_hash VARCHAR(255) NOT NULL, " +
                "salt VARCHAR(50) NOT NULL, " +
                "totp_secret VARCHAR(255), " +
                "totp_enabled INTEGER DEFAULT 0, " +
                "backup_codes TEXT, " +
                "last_login DATETIME, " +
                "email VARCHAR(100), " +
                "report_frequency VARCHAR(20) DEFAULT 'MONTHLY'" +
                ")"
            );
            
            // Passwords table with additional fields for auto-fill and health reporting
            stmt.execute(
                "CREATE TABLE IF NOT EXISTS passwords (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "user_id INTEGER NOT NULL, " +
                "website VARCHAR(255) NOT NULL, " +
                "username VARCHAR(100) NOT NULL, " +
                "encrypted_password VARCHAR(255) NOT NULL, " +
                "last_used DATETIME, " +
                "strength_score INTEGER, " +
                "url_pattern VARCHAR(255), " +
                "auto_fill_enabled INTEGER DEFAULT 1, " +
                "FOREIGN KEY (user_id) REFERENCES users(id)" +
                ")"
            );
            
            // Password Health Reports table
            stmt.execute(
                "CREATE TABLE IF NOT EXISTS password_health_reports (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "user_id INTEGER NOT NULL, " +
                "report_date DATETIME NOT NULL, " +
                "overall_score INTEGER NOT NULL, " +
                "weak_passwords INTEGER NOT NULL, " +
                "reused_passwords INTEGER NOT NULL, " +
                "old_passwords INTEGER NOT NULL, " +
                "report_data TEXT, " +
                "email_sent INTEGER DEFAULT 0, " +
                "FOREIGN KEY (user_id) REFERENCES users(id)" +
                ")"
            );
            
            // Task categories table
            stmt.execute(
                "CREATE TABLE IF NOT EXISTS task_categories (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "user_id INTEGER NOT NULL, " +
                "name VARCHAR(50) NOT NULL, " +
                "color VARCHAR(20), " +
                "UNIQUE (user_id, name), " +
                "FOREIGN KEY (user_id) REFERENCES users(id)" +
                ")"
            );
            
            // Tasks table - enhanced version
            stmt.execute(
                "CREATE TABLE IF NOT EXISTS tasks (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "user_id INTEGER NOT NULL, " +
                "title TEXT NOT NULL, " +
                "description TEXT, " +
                "category_id INTEGER, " +
                "priority INTEGER DEFAULT 1, " + // 1=Low, 2=Medium, 3=High
                "status INTEGER DEFAULT 0, " + // 0=Not Started, 1=In Progress, 2=Completed
                "due_date DATE, " +
                "completion_date DATE, " +
                "creation_date DATE, " +
                "is_recurring INTEGER DEFAULT 0, " + // 0=No, 1=Yes
                "recurrence_type INTEGER, " + // 1=Daily, 2=Weekly, 3=Monthly, 4=Yearly
                "recurrence_value INTEGER, " + // Every X days/weeks/months/years
                "estimated_minutes INTEGER, " + // Estimated time in minutes
                "actual_minutes INTEGER, " + // Actual time spent in minutes
                "progress INTEGER DEFAULT 0, " + // Progress percentage (0-100)
                "parent_task_id INTEGER, " + // For subtasks
                "FOREIGN KEY (user_id) REFERENCES users(id), " +
                "FOREIGN KEY (category_id) REFERENCES task_categories(id), " +
                "FOREIGN KEY (parent_task_id) REFERENCES tasks(id)" +
                ")"
            );
            
            // Task tags table
            stmt.execute(
                "CREATE TABLE IF NOT EXISTS task_tags (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "user_id INTEGER NOT NULL, " +
                "name VARCHAR(50) NOT NULL, " +
                "UNIQUE (user_id, name), " +
                "FOREIGN KEY (user_id) REFERENCES users(id)" +
                ")"
            );
            
            // Task to tag mapping table
            stmt.execute(
                "CREATE TABLE IF NOT EXISTS task_tag_mapping (" +
                "task_id INTEGER NOT NULL, " +
                "tag_id INTEGER NOT NULL, " +
                "PRIMARY KEY (task_id, tag_id), " +
                "FOREIGN KEY (task_id) REFERENCES tasks(id), " +
                "FOREIGN KEY (tag_id) REFERENCES task_tags(id)" +
                ")"
            );
            
            // Task reminders table
            stmt.execute(
                "CREATE TABLE IF NOT EXISTS task_reminders (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "task_id INTEGER NOT NULL, " +
                "reminder_time DATETIME NOT NULL, " +
                "is_notified INTEGER DEFAULT 0, " + // 0=No, 1=Yes
                "FOREIGN KEY (task_id) REFERENCES tasks(id)" +
                ")"
            );
            
            // Contacts table
            stmt.execute(
                "CREATE TABLE IF NOT EXISTS contacts (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "user_id INTEGER NOT NULL, " +
                "name VARCHAR(100) NOT NULL, " +
                "phone VARCHAR(20), " +
                "email VARCHAR(100), " +
                "address TEXT, " +
                "company VARCHAR(100), " +
                "job_title VARCHAR(100), " +
                "website VARCHAR(255), " +
                "birthday DATE, " +
                "notes TEXT, " +
                "category VARCHAR(50), " +
                "favorite INTEGER DEFAULT 0, " +
                "FOREIGN KEY (user_id) REFERENCES users(id)" +
                ")"
            );
            
            // Secure notes table
            stmt.execute(
                "CREATE TABLE IF NOT EXISTS secure_notes (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "user_id INTEGER NOT NULL, " +
                "title VARCHAR(255) NOT NULL, " +
                "encrypted_content TEXT NOT NULL, " +
                "note_type INTEGER DEFAULT 0, " + // 0=Generic, 1=Credit Card, 2=ID Document, etc.
                "category VARCHAR(100), " +
                "tags TEXT, " +
                "created_date DATETIME DEFAULT CURRENT_TIMESTAMP, " +
                "modified_date DATETIME DEFAULT CURRENT_TIMESTAMP, " +
                "color VARCHAR(20), " +
                "favorite INTEGER DEFAULT 0, " + // 0=No, 1=Yes
                "FOREIGN KEY (user_id) REFERENCES users(id)" +
                ")"
            );
            
            // Note templates table
            stmt.execute(
                "CREATE TABLE IF NOT EXISTS note_templates (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "user_id INTEGER NOT NULL, " +
                "name VARCHAR(100) NOT NULL, " +
                "template_content TEXT NOT NULL, " +
                "template_type INTEGER NOT NULL, " +
                "FOREIGN KEY (user_id) REFERENCES users(id)" +
                ")"
            );
            
            // Insert default categories for tasks if none exist
            stmt.execute(
                "INSERT OR IGNORE INTO task_categories (user_id, name, color) " +
                "SELECT id, 'Work', '#FF0000' FROM users " +
                "UNION SELECT id, 'Personal', '#00FF00' FROM users " +
                "UNION SELECT id, 'Shopping', '#0000FF' FROM users " +
                "UNION SELECT id, 'Health', '#FF00FF' FROM users " +
                "UNION SELECT id, 'Education', '#FFFF00' FROM users"
            );
            
            // Insert default note templates if none exist
            stmt.execute(
                "INSERT OR IGNORE INTO note_templates (user_id, name, template_content, template_type) " +
                "VALUES (0, 'Credit Card', '{\\rtf1\\ansi\\deff0{\\fonttbl{\\f0\\fnil\\fcharset0 Arial;}}\\viewkind4\\uc1\\pard\\f0\\fs24\\b Card Information\\b0\\par\\par Card Type: [Type]\\par Card Number: [Number]\\par Cardholder Name: [Name]\\par Expiration Date: [Expiry]\\par CVV: [CVV]\\par\\par\\b Billing Address\\b0\\par [Billing Address]\\par\\par\\b Additional Information\\b0\\par [Notes]\\par}', 1),"
                + "(0, 'ID Document', '{\\rtf1\\ansi\\deff0{\\fonttbl{\\f0\\fnil\\fcharset0 Arial;}}\\viewkind4\\uc1\\pard\\f0\\fs24\\b Document Information\\b0\\par\\par Document Type: [Type]\\par Document Number: [Number]\\par Full Name: [Name]\\par Issuing Authority: [Authority]\\par Issue Date: [Issue Date]\\par Expiration Date: [Expiry Date]\\par\\par\\b Personal Information\\b0\\par Date of Birth: [DOB]\\par Place of Birth: [Birth Place]\\par Nationality: [Nationality]\\par\\par\\b Additional Information\\b0\\par [Notes]\\par}', 2),"
                + "(0, 'Software License', '{\\rtf1\\ansi\\deff0{\\fonttbl{\\f0\\fnil\\fcharset0 Arial;}}\\viewkind4\\uc1\\pard\\f0\\fs24\\b Software License Information\\b0\\par\\par Software Name: [Name]\\par Version: [Version]\\par License Key: [Key]\\par Purchased Date: [Purchase Date]\\par Expiration Date: [Expiry Date]\\par Licensed To: [Owner]\\par Email Used: [Email]\\par\\par\\b Vendor Information\\b0\\par Company: [Company]\\par Website: [Website]\\par Support Email: [Support]\\par\\par\\b Additional Information\\b0\\par [Notes]\\par}', 4)"
            );
        }
    }
    
    private static void updateDatabaseSchema() {
        try {
            System.out.println("Checking database schema and updating if necessary...");
            DatabaseMetaData meta = connection.getMetaData();
            
            // Update users table
            updateTableIfNeeded("users", "email", "VARCHAR(100)");
            updateTableIfNeeded("users", "report_frequency", "VARCHAR(20) DEFAULT 'MONTHLY'");
            
            // Update passwords table
            updateTableIfNeeded("passwords", "auto_fill_enabled", "INTEGER DEFAULT 1");
            updateTableIfNeeded("passwords", "url_pattern", "VARCHAR(255)");
            updateTableIfNeeded("passwords", "strength_score", "INTEGER");
            updateTableIfNeeded("passwords", "last_used", "DATETIME");
            
            // Check if secure_notes table exists, if not create it
            ResultSet tables = meta.getTables(null, null, "secure_notes", null);
            if (!tables.next()) {
                try (Statement stmt = connection.createStatement()) {
                    stmt.execute(
                        "CREATE TABLE secure_notes (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                        "user_id INTEGER NOT NULL, " +
                        "title VARCHAR(255) NOT NULL, " +
                        "encrypted_content TEXT NOT NULL, " +
                        "note_type INTEGER DEFAULT 0, " +
                        "category VARCHAR(100), " +
                        "tags TEXT, " +
                        "created_date DATETIME DEFAULT CURRENT_TIMESTAMP, " +
                        "modified_date DATETIME DEFAULT CURRENT_TIMESTAMP, " +
                        "color VARCHAR(20), " +
                        "favorite INTEGER DEFAULT 0, " +
                        "FOREIGN KEY (user_id) REFERENCES users(id)" +
                        ")"
                    );
                    System.out.println("Created secure_notes table");
                }
            }
            
            // Check if note_templates table exists, if not create it
            tables = meta.getTables(null, null, "note_templates", null);
            if (!tables.next()) {
                try (Statement stmt = connection.createStatement()) {
                    stmt.execute(
                        "CREATE TABLE note_templates (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                        "user_id INTEGER NOT NULL, " +
                        "name VARCHAR(100) NOT NULL, " +
                        "template_content TEXT NOT NULL, " +
                        "template_type INTEGER NOT NULL, " +
                        "FOREIGN KEY (user_id) REFERENCES users(id)" +
                        ")"
                    );
                    
                    // Insert default templates
                    stmt.execute(
                        "INSERT INTO note_templates (user_id, name, template_content, template_type) " +
                        "VALUES (0, 'Credit Card', '{\\rtf1\\ansi\\deff0{\\fonttbl{\\f0\\fnil\\fcharset0 Arial;}}\\viewkind4\\uc1\\pard\\f0\\fs24\\b Card Information\\b0\\par\\par Card Type: [Type]\\par Card Number: [Number]\\par Cardholder Name: [Name]\\par Expiration Date: [Expiry]\\par CVV: [CVV]\\par\\par\\b Billing Address\\b0\\par [Billing Address]\\par\\par\\b Additional Information\\b0\\par [Notes]\\par}', 1),"
                        + "(0, 'ID Document', '{\\rtf1\\ansi\\deff0{\\fonttbl{\\f0\\fnil\\fcharset0 Arial;}}\\viewkind4\\uc1\\pard\\f0\\fs24\\b Document Information\\b0\\par\\par Document Type: [Type]\\par Document Number: [Number]\\par Full Name: [Name]\\par Issuing Authority: [Authority]\\par Issue Date: [Issue Date]\\par Expiration Date: [Expiry Date]\\par\\par\\b Personal Information\\b0\\par Date of Birth: [DOB]\\par Place of Birth: [Birth Place]\\par Nationality: [Nationality]\\par\\par\\b Additional Information\\b0\\par [Notes]\\par}', 2),"
                        + "(0, 'Software License', '{\\rtf1\\ansi\\deff0{\\fonttbl{\\f0\\fnil\\fcharset0 Arial;}}\\viewkind4\\uc1\\pard\\f0\\fs24\\b Software License Information\\b0\\par\\par Software Name: [Name]\\par Version: [Version]\\par License Key: [Key]\\par Purchased Date: [Purchase Date]\\par Expiration Date: [Expiry Date]\\par Licensed To: [Owner]\\par Email Used: [Email]\\par\\par\\b Vendor Information\\b0\\par Company: [Company]\\par Website: [Website]\\par Support Email: [Support]\\par\\par\\b Additional Information\\b0\\par [Notes]\\par}', 4)"
                    );
                    
                    System.out.println("Created note_templates table with default templates");
                }
            }
            
            System.out.println("Database schema update completed");
        } catch (SQLException e) {
            System.err.println("Error updating database schema: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * Update a table to add a column if it doesn't exist
     * @param tableName The table to update
     * @param columnName The column to add
     * @param columnType The SQL type of the column
     */
    private static void updateTableIfNeeded(String tableName, String columnName, String columnType) throws SQLException {
        DatabaseMetaData meta = connection.getMetaData();
        ResultSet columns = meta.getColumns(null, null, tableName, columnName);
        
        if (!columns.next()) {
            try (Statement stmt = connection.createStatement()) {
                stmt.execute("ALTER TABLE " + tableName + " ADD COLUMN " + columnName + " " + columnType);
                System.out.println("Added column " + columnName + " to table " + tableName);
            }
        }
        columns.close();
    }
    
    /**
     * Close the database connection
     */
    public static void closeConnection() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
                connection = null;
            }
        } catch (SQLException e) {
            System.err.println("Error closing database connection: " + e.getMessage());
        }
    }
} 