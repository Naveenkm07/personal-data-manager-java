package com.datamanager.util;

import java.io.*;
import java.sql.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.zip.*;

public class BackupUtil {
    private static final String BACKUP_DIR = "backups";
    
    public static String createBackup(int userId) throws IOException, SQLException {
        // Create backup directory if it doesn't exist
        File backupDir = new File(BACKUP_DIR);
        if (!backupDir.exists()) {
            backupDir.mkdirs();
        }
        
        // Generate backup filename with timestamp
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        String backupFile = String.format("%s/backup_%d_%s.zip", BACKUP_DIR, userId, timestamp);
        
        try (Connection conn = DatabaseUtil.getConnection();
             ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(backupFile))) {
            
            // Backup passwords
            backupTable(conn, zos, "passwords", userId);
            
            // Backup tasks
            backupTable(conn, zos, "tasks", userId);
            
            // Backup contacts
            backupTable(conn, zos, "contacts", userId);
        }
        
        return backupFile;
    }
    
    private static void backupTable(Connection conn, ZipOutputStream zos, String tableName, int userId) 
            throws SQLException, IOException {
        String query = String.format("SELECT * FROM %s WHERE user_id = ?", tableName);
        StringBuilder data = new StringBuilder();
        
        try (PreparedStatement pstmt = conn.prepareStatement(query)) {
            pstmt.setInt(1, userId);
            ResultSet rs = pstmt.executeQuery();
            ResultSetMetaData metaData = rs.getMetaData();
            int columnCount = metaData.getColumnCount();
            
            // Add column headers
            for (int i = 1; i <= columnCount; i++) {
                data.append(metaData.getColumnName(i)).append(",");
            }
            data.setLength(data.length() - 1); // Remove last comma
            data.append("\n");
            
            // Add data rows
            while (rs.next()) {
                for (int i = 1; i <= columnCount; i++) {
                    String value = rs.getString(i);
                    data.append(value != null ? value.replace(",", "\\,") : "").append(",");
                }
                data.setLength(data.length() - 1); // Remove last comma
                data.append("\n");
            }
        }
        
        // Add table data to zip file
        ZipEntry entry = new ZipEntry(tableName + ".csv");
        zos.putNextEntry(entry);
        zos.write(data.toString().getBytes());
        zos.closeEntry();
    }
    
    public static void restoreBackup(String backupFile, int userId) throws IOException, SQLException {
        try (Connection conn = DatabaseUtil.getConnection();
             ZipFile zipFile = new ZipFile(backupFile)) {
            
            // Begin transaction
            conn.setAutoCommit(false);
            try {
                // Delete existing data
                deleteUserData(conn, userId);
                
                // Restore each table
                restoreTable(conn, zipFile, "passwords", userId);
                restoreTable(conn, zipFile, "tasks", userId);
                restoreTable(conn, zipFile, "contacts", userId);
                
                conn.commit();
            } catch (Exception e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        }
    }
    
    private static void deleteUserData(Connection conn, int userId) throws SQLException {
        String[] tables = {"passwords", "tasks", "contacts"};
        for (String table : tables) {
            String query = String.format("DELETE FROM %s WHERE user_id = ?", table);
            try (PreparedStatement pstmt = conn.prepareStatement(query)) {
                pstmt.setInt(1, userId);
                pstmt.executeUpdate();
            }
        }
    }
    
    private static void restoreTable(Connection conn, ZipFile zipFile, String tableName, int userId) 
            throws IOException, SQLException {
        ZipEntry entry = zipFile.getEntry(tableName + ".csv");
        if (entry == null) return;
        
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(zipFile.getInputStream(entry)))) {
            // Read column headers
            String headerLine = reader.readLine();
            if (headerLine == null) return;
            
            String[] columns = headerLine.split(",");
            
            // Prepare insert statement
            StringBuilder insertQuery = new StringBuilder("INSERT INTO " + tableName + " (");
            insertQuery.append(String.join(",", columns)).append(") VALUES (");
            for (int i = 0; i < columns.length; i++) {
                insertQuery.append(i > 0 ? ",?" : "?");
            }
            insertQuery.append(")");
            
            try (PreparedStatement pstmt = conn.prepareStatement(insertQuery.toString())) {
                String line;
                while ((line = reader.readLine()) != null) {
                    String[] values = line.split(",(?=([^\\\\]|\\\\[^,])*$)");
                    for (int i = 0; i < values.length; i++) {
                        String value = values[i].replace("\\,", ",");
                        pstmt.setString(i + 1, value.isEmpty() ? null : value);
                    }
                    pstmt.executeUpdate();
                }
            }
        }
    }
} 