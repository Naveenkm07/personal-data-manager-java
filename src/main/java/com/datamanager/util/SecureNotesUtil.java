package com.datamanager.util;

import java.sql.*;
import java.util.*;

public class SecureNotesUtil {
    // Note type constants
    public static final int NOTE_TYPE_GENERIC = 0;
    public static final int NOTE_TYPE_CREDIT_CARD = 1;
    public static final int NOTE_TYPE_ID_DOCUMENT = 2;
    public static final int NOTE_TYPE_PASSWORD = 3;
    public static final int NOTE_TYPE_SOFTWARE_LICENSE = 4;

    /**
     * Creates the secure notes tables if they don't exist
     */
    public static void createNotesTableIfNotExists() throws SQLException {
        // This method is just a stub for now - tables are created in DatabaseUtil
    }

    /**
     * Get all notes for a user
     */
    public static List<Map<String, Object>> getAllNotes(int userId) throws SQLException {
        List<Map<String, Object>> notes = new ArrayList<>();
        try (Connection conn = DatabaseUtil.getConnection()) {
            String query = "SELECT id, title, note_type, category, favorite, color, modified_date FROM secure_notes WHERE user_id = ?";
            try (PreparedStatement pstmt = conn.prepareStatement(query)) {
                pstmt.setInt(1, userId);
                ResultSet rs = pstmt.executeQuery();
                
                while (rs.next()) {
                    Map<String, Object> note = new HashMap<>();
                    note.put("id", rs.getInt("id"));
                    note.put("title", rs.getString("title"));
                    note.put("noteType", rs.getInt("note_type"));
                    note.put("category", rs.getString("category"));
                    note.put("favorite", rs.getBoolean("favorite"));
                    note.put("color", rs.getString("color"));
                    note.put("modifiedDate", rs.getDate("modified_date"));
                    notes.add(note);
                }
            }
        }
        return notes;
    }

    /**
     * Search notes for a user
     */
    public static List<Map<String, Object>> searchNotes(int userId, String searchTerm) throws SQLException {
        List<Map<String, Object>> notes = new ArrayList<>();
        try (Connection conn = DatabaseUtil.getConnection()) {
            String query = "SELECT id, title, note_type, category, favorite, color, modified_date FROM secure_notes " +
                          "WHERE user_id = ? AND (title LIKE ? OR category LIKE ? OR tags LIKE ?)";
            try (PreparedStatement pstmt = conn.prepareStatement(query)) {
                String searchPattern = "%" + searchTerm + "%";
                pstmt.setInt(1, userId);
                pstmt.setString(2, searchPattern);
                pstmt.setString(3, searchPattern);
                pstmt.setString(4, searchPattern);
                ResultSet rs = pstmt.executeQuery();
                
                while (rs.next()) {
                    Map<String, Object> note = new HashMap<>();
                    note.put("id", rs.getInt("id"));
                    note.put("title", rs.getString("title"));
                    note.put("noteType", rs.getInt("note_type"));
                    note.put("category", rs.getString("category"));
                    note.put("favorite", rs.getBoolean("favorite"));
                    note.put("color", rs.getString("color"));
                    note.put("modifiedDate", rs.getDate("modified_date"));
                    notes.add(note);
                }
            }
        }
        return notes;
    }

    /**
     * Get a specific note by ID
     */
    public static Map<String, Object> getNoteById(int noteId, int userId) throws SQLException {
        Map<String, Object> note = new HashMap<>();
        try (Connection conn = DatabaseUtil.getConnection()) {
            String query = "SELECT * FROM secure_notes WHERE id = ? AND user_id = ?";
            try (PreparedStatement pstmt = conn.prepareStatement(query)) {
                pstmt.setInt(1, noteId);
                pstmt.setInt(2, userId);
                ResultSet rs = pstmt.executeQuery();
                
                if (rs.next()) {
                    note.put("id", rs.getInt("id"));
                    note.put("title", rs.getString("title"));
                    note.put("content", rs.getString("encrypted_content"));
                    note.put("noteType", rs.getInt("note_type"));
                    note.put("category", rs.getString("category"));
                    note.put("tags", rs.getString("tags"));
                    note.put("color", rs.getString("color"));
                    note.put("favorite", rs.getBoolean("favorite"));
                    note.put("createdDate", rs.getDate("created_date"));
                    note.put("modifiedDate", rs.getDate("modified_date"));
                }
            }
        }
        return note;
    }

    /**
     * Save a note (create or update)
     */
    public static int saveNote(int noteId, int userId, String title, String content, 
                            int noteType, String category, String tags, String color, 
                            boolean favorite) throws SQLException {
        try (Connection conn = DatabaseUtil.getConnection()) {
            if (noteId > 0) {
                // Update existing note
                String query = "UPDATE secure_notes SET title = ?, encrypted_content = ?, note_type = ?, " +
                             "category = ?, tags = ?, color = ?, favorite = ?, modified_date = CURRENT_TIMESTAMP " +
                             "WHERE id = ? AND user_id = ?";
                try (PreparedStatement pstmt = conn.prepareStatement(query)) {
                    pstmt.setString(1, title);
                    pstmt.setString(2, content);
                    pstmt.setInt(3, noteType);
                    pstmt.setString(4, category);
                    pstmt.setString(5, tags);
                    pstmt.setString(6, color);
                    pstmt.setInt(7, favorite ? 1 : 0);
                    pstmt.setInt(8, noteId);
                    pstmt.setInt(9, userId);
                    pstmt.executeUpdate();
                }
                return noteId;
            } else {
                // Create new note
                String query = "INSERT INTO secure_notes (user_id, title, encrypted_content, note_type, " +
                             "category, tags, color, favorite) VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
                try (PreparedStatement pstmt = conn.prepareStatement(query, Statement.RETURN_GENERATED_KEYS)) {
                    pstmt.setInt(1, userId);
                    pstmt.setString(2, title);
                    pstmt.setString(3, content);
                    pstmt.setInt(4, noteType);
                    pstmt.setString(5, category);
                    pstmt.setString(6, tags);
                    pstmt.setString(7, color);
                    pstmt.setInt(8, favorite ? 1 : 0);
                    pstmt.executeUpdate();
                    
                    ResultSet rs = pstmt.getGeneratedKeys();
                    if (rs.next()) {
                        return rs.getInt(1);
                    }
                }
            }
        }
        return -1;
    }

    /**
     * Delete a note
     */
    public static boolean deleteNote(int noteId, int userId) throws SQLException {
        try (Connection conn = DatabaseUtil.getConnection()) {
            String query = "DELETE FROM secure_notes WHERE id = ? AND user_id = ?";
            try (PreparedStatement pstmt = conn.prepareStatement(query)) {
                pstmt.setInt(1, noteId);
                pstmt.setInt(2, userId);
                return pstmt.executeUpdate() > 0;
            }
        }
    }

    /**
     * Get all note categories for a user
     */
    public static List<String> getNoteCategories(int userId) throws SQLException {
        List<String> categories = new ArrayList<>();
        try (Connection conn = DatabaseUtil.getConnection()) {
            String query = "SELECT DISTINCT category FROM secure_notes WHERE user_id = ? AND category IS NOT NULL AND category != ''";
            try (PreparedStatement pstmt = conn.prepareStatement(query)) {
                pstmt.setInt(1, userId);
                ResultSet rs = pstmt.executeQuery();
                
                while (rs.next()) {
                    categories.add(rs.getString("category"));
                }
            }
        }
        return categories;
    }

    /**
     * Get templates for a user
     */
    public static List<Map<String, Object>> getTemplates(int userId) throws SQLException {
        List<Map<String, Object>> templates = new ArrayList<>();
        try (Connection conn = DatabaseUtil.getConnection()) {
            // Just a stub implementation for now
            // In a real implementation, this would query a templates table
            for (int i = 0; i < 5; i++) {
                Map<String, Object> template = new HashMap<>();
                template.put("id", i);
                template.put("name", "Template " + i);
                template.put("templateType", i);
                templates.add(template);
            }
        }
        return templates;
    }

    /**
     * Get template content by ID
     */
    public static String getTemplateContent(int templateId) throws SQLException {
        // Just a stub implementation for now
        return "{\\rtf1\\ansi Sample template content}";
    }
} 