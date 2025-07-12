package com.datamanager.dao;

import com.datamanager.model.Task;
import com.datamanager.model.TaskCategory;
import com.datamanager.util.DatabaseUtil;

import java.sql.*;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class TaskDAO {
    
    public List<TaskCategory> getCategories(int userId) throws SQLException {
        List<TaskCategory> categories = new ArrayList<>();
        String query = "SELECT id, user_id, name, color FROM task_categories WHERE user_id = ?";
        
        try (Connection conn = DatabaseUtil.getConnection();
             PreparedStatement stmt = conn.prepareStatement(query)) {
            
            stmt.setInt(1, userId);
            ResultSet rs = stmt.executeQuery();
            
            while (rs.next()) {
                TaskCategory category = new TaskCategory(
                    rs.getInt("id"),
                    rs.getInt("user_id"),
                    rs.getString("name"),
                    rs.getString("color")
                );
                categories.add(category);
            }
        }
        
        return categories;
    }
    
    public TaskCategory addCategory(TaskCategory category) throws SQLException {
        String query = "INSERT INTO task_categories (user_id, name, color) VALUES (?, ?, ?)";
        
        try (Connection conn = DatabaseUtil.getConnection();
             PreparedStatement stmt = conn.prepareStatement(query, Statement.RETURN_GENERATED_KEYS)) {
            
            stmt.setInt(1, category.getUserId());
            stmt.setString(2, category.getName());
            stmt.setString(3, category.getColor());
            
            int affectedRows = stmt.executeUpdate();
            if (affectedRows == 0) {
                throw new SQLException("Creating category failed, no rows affected.");
            }
            
            try (ResultSet generatedKeys = stmt.getGeneratedKeys()) {
                if (generatedKeys.next()) {
                    category.setId(generatedKeys.getInt(1));
                } else {
                    throw new SQLException("Creating category failed, no ID obtained.");
                }
            }
        }
        
        return category;
    }
    
    public List<Task> getTasks(int userId) throws SQLException {
        Map<Integer, Task> tasksMap = new HashMap<>();
        
        // Query to get all tasks with category names
        String query = "SELECT t.*, c.name as category_name FROM tasks t " +
                       "LEFT JOIN task_categories c ON t.category_id = c.id " +
                       "WHERE t.user_id = ? ORDER BY t.due_date ASC";
        
        try (Connection conn = DatabaseUtil.getConnection();
             PreparedStatement stmt = conn.prepareStatement(query)) {
            
            stmt.setInt(1, userId);
            ResultSet rs = stmt.executeQuery();
            
            while (rs.next()) {
                Task task = extractTaskFromResultSet(rs);
                tasksMap.put(task.getId(), task);
            }
        }
        
        // Set up parent-child relationships for subtasks
        for (Task task : new ArrayList<>(tasksMap.values())) {
            if (task.getParentTaskId() != null) {
                Task parentTask = tasksMap.get(task.getParentTaskId());
                if (parentTask != null) {
                    parentTask.addSubtask(task);
                }
            }
        }
        
        // Filter out subtasks from the main list (they're now in their parent's subtasks list)
        List<Task> rootTasks = new ArrayList<>();
        for (Task task : tasksMap.values()) {
            if (task.getParentTaskId() == null) {
                rootTasks.add(task);
            }
        }
        
        // Load tags for each task
        loadTaskTags(tasksMap);
        
        return rootTasks;
    }
    
    private void loadTaskTags(Map<Integer, Task> tasksMap) throws SQLException {
        String query = "SELECT tt.task_id, tg.name FROM task_to_tag tt " +
                      "JOIN task_tags tg ON tt.tag_id = tg.id " +
                      "WHERE tt.task_id IN (" + String.join(",", tasksMap.keySet().stream().map(String::valueOf).toArray(String[]::new)) + ")";
        
        if (tasksMap.isEmpty()) return;
        
        try (Connection conn = DatabaseUtil.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(query)) {
            
            while (rs.next()) {
                int taskId = rs.getInt("task_id");
                String tagName = rs.getString("name");
                
                Task task = tasksMap.get(taskId);
                if (task != null) {
                    task.addTag(tagName);
                }
            }
        }
    }
    
    private Task extractTaskFromResultSet(ResultSet rs) throws SQLException {
        Task task = new Task();
        task.setId(rs.getInt("id"));
        task.setUserId(rs.getInt("user_id"));
        task.setTitle(rs.getString("title"));
        task.setDescription(rs.getString("description"));
        
        int categoryId = rs.getInt("category_id");
        if (!rs.wasNull()) {
            task.setCategoryId(categoryId);
            task.setCategoryName(rs.getString("category_name"));
        }
        
        task.setPriority(rs.getInt("priority"));
        task.setStatus(rs.getInt("status"));
        
        Timestamp dueDate = rs.getTimestamp("due_date");
        if (dueDate != null) {
            task.setDueDate(new Date(dueDate.getTime()));
        }
        
        Timestamp completionDate = rs.getTimestamp("completion_date");
        if (completionDate != null) {
            task.setCompletionDate(new Date(completionDate.getTime()));
        }
        
        Timestamp creationDate = rs.getTimestamp("creation_date");
        if (creationDate != null) {
            task.setCreationDate(new Date(creationDate.getTime()));
        }
        
        task.setRecurring(rs.getBoolean("is_recurring"));
        
        int recurrenceType = rs.getInt("recurrence_type");
        if (!rs.wasNull()) {
            task.setRecurrenceType(recurrenceType);
        }
        
        int recurrenceValue = rs.getInt("recurrence_value");
        if (!rs.wasNull()) {
            task.setRecurrenceValue(recurrenceValue);
        }
        
        int estimatedMinutes = rs.getInt("estimated_minutes");
        if (!rs.wasNull()) {
            task.setEstimatedMinutes(estimatedMinutes);
        }
        
        int actualMinutes = rs.getInt("actual_minutes");
        if (!rs.wasNull()) {
            task.setActualMinutes(actualMinutes);
        }
        
        task.setProgress(rs.getInt("progress"));
        
        int parentTaskId = rs.getInt("parent_task_id");
        if (!rs.wasNull()) {
            task.setParentTaskId(parentTaskId);
        }
        
        return task;
    }
    
    public Task addTask(Task task) throws SQLException {
        String query = "INSERT INTO tasks (user_id, title, description, category_id, priority, " +
                      "status, due_date, creation_date, is_recurring, recurrence_type, recurrence_value, " +
                      "estimated_minutes, progress, parent_task_id) " +
                      "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        
        try (Connection conn = DatabaseUtil.getConnection();
             PreparedStatement stmt = conn.prepareStatement(query, Statement.RETURN_GENERATED_KEYS)) {
            
            stmt.setInt(1, task.getUserId());
            stmt.setString(2, task.getTitle());
            stmt.setString(3, task.getDescription());
            
            if (task.getCategoryId() != null) {
                stmt.setInt(4, task.getCategoryId());
            } else {
                stmt.setNull(4, java.sql.Types.INTEGER);
            }
            
            stmt.setInt(5, task.getPriority());
            stmt.setInt(6, task.getStatus());
            
            if (task.getDueDate() != null) {
                stmt.setTimestamp(7, new Timestamp(task.getDueDate().getTime()));
            } else {
                stmt.setNull(7, java.sql.Types.TIMESTAMP);
            }
            
            if (task.getCreationDate() != null) {
                stmt.setTimestamp(8, new Timestamp(task.getCreationDate().getTime()));
            } else {
                stmt.setTimestamp(8, new Timestamp(new Date().getTime()));
            }
            
            stmt.setBoolean(9, task.isRecurring());
            
            if (task.getRecurrenceType() != null) {
                stmt.setInt(10, task.getRecurrenceType());
            } else {
                stmt.setNull(10, java.sql.Types.INTEGER);
            }
            
            if (task.getRecurrenceValue() != null) {
                stmt.setInt(11, task.getRecurrenceValue());
            } else {
                stmt.setNull(11, java.sql.Types.INTEGER);
            }
            
            if (task.getEstimatedMinutes() != null) {
                stmt.setInt(12, task.getEstimatedMinutes());
            } else {
                stmt.setNull(12, java.sql.Types.INTEGER);
            }
            
            stmt.setInt(13, task.getProgress());
            
            if (task.getParentTaskId() != null) {
                stmt.setInt(14, task.getParentTaskId());
            } else {
                stmt.setNull(14, java.sql.Types.INTEGER);
            }
            
            int affectedRows = stmt.executeUpdate();
            if (affectedRows == 0) {
                throw new SQLException("Creating task failed, no rows affected.");
            }
            
            try (ResultSet generatedKeys = stmt.getGeneratedKeys()) {
                if (generatedKeys.next()) {
                    task.setId(generatedKeys.getInt(1));
                } else {
                    throw new SQLException("Creating task failed, no ID obtained.");
                }
            }
            
            // Add tags if present
            if (task.getTags() != null && !task.getTags().isEmpty()) {
                saveTaskTags(conn, task);
            }
            
            // Add subtasks if present
            if (task.getSubtasks() != null && !task.getSubtasks().isEmpty()) {
                for (Task subtask : task.getSubtasks()) {
                    subtask.setParentTaskId(task.getId());
                    addTask(subtask);
                }
            }
        }
        
        return task;
    }
    
    private void saveTaskTags(Connection conn, Task task) throws SQLException {
        // First, ensure all tags exist in the database
        String insertTagQuery = "INSERT OR IGNORE INTO task_tags (user_id, name) VALUES (?, ?)";
        String getTagIdQuery = "SELECT id FROM task_tags WHERE user_id = ? AND name = ?";
        String linkTagQuery = "INSERT INTO task_to_tag (task_id, tag_id) VALUES (?, ?)";
        
        try (PreparedStatement insertTagStmt = conn.prepareStatement(insertTagQuery);
             PreparedStatement getTagIdStmt = conn.prepareStatement(getTagIdQuery);
             PreparedStatement linkTagStmt = conn.prepareStatement(linkTagQuery)) {
            
            for (String tagName : task.getTags()) {
                // Insert the tag if it doesn't exist
                insertTagStmt.setInt(1, task.getUserId());
                insertTagStmt.setString(2, tagName);
                insertTagStmt.executeUpdate();
                
                // Get the tag ID
                getTagIdStmt.setInt(1, task.getUserId());
                getTagIdStmt.setString(2, tagName);
                ResultSet rs = getTagIdStmt.executeQuery();
                
                if (rs.next()) {
                    int tagId = rs.getInt("id");
                    
                    // Link the tag to the task
                    linkTagStmt.setInt(1, task.getId());
                    linkTagStmt.setInt(2, tagId);
                    linkTagStmt.executeUpdate();
                }
            }
        }
    }
    
    public void updateTask(Task task) throws SQLException {
        String query = "UPDATE tasks SET title = ?, description = ?, category_id = ?, priority = ?, " +
                      "status = ?, due_date = ?, completion_date = ?, is_recurring = ?, " +
                      "recurrence_type = ?, recurrence_value = ?, estimated_minutes = ?, " +
                      "actual_minutes = ?, progress = ? WHERE id = ? AND user_id = ?";
        
        try (Connection conn = DatabaseUtil.getConnection();
             PreparedStatement stmt = conn.prepareStatement(query)) {
            
            stmt.setString(1, task.getTitle());
            stmt.setString(2, task.getDescription());
            
            if (task.getCategoryId() != null) {
                stmt.setInt(3, task.getCategoryId());
            } else {
                stmt.setNull(3, java.sql.Types.INTEGER);
            }
            
            stmt.setInt(4, task.getPriority());
            stmt.setInt(5, task.getStatus());
            
            if (task.getDueDate() != null) {
                stmt.setTimestamp(6, new Timestamp(task.getDueDate().getTime()));
            } else {
                stmt.setNull(6, java.sql.Types.TIMESTAMP);
            }
            
            if (task.getCompletionDate() != null) {
                stmt.setTimestamp(7, new Timestamp(task.getCompletionDate().getTime()));
            } else {
                stmt.setNull(7, java.sql.Types.TIMESTAMP);
            }
            
            stmt.setBoolean(8, task.isRecurring());
            
            if (task.getRecurrenceType() != null) {
                stmt.setInt(9, task.getRecurrenceType());
            } else {
                stmt.setNull(9, java.sql.Types.INTEGER);
            }
            
            if (task.getRecurrenceValue() != null) {
                stmt.setInt(10, task.getRecurrenceValue());
            } else {
                stmt.setNull(10, java.sql.Types.INTEGER);
            }
            
            if (task.getEstimatedMinutes() != null) {
                stmt.setInt(11, task.getEstimatedMinutes());
            } else {
                stmt.setNull(11, java.sql.Types.INTEGER);
            }
            
            if (task.getActualMinutes() != null) {
                stmt.setInt(12, task.getActualMinutes());
            } else {
                stmt.setNull(12, java.sql.Types.INTEGER);
            }
            
            stmt.setInt(13, task.getProgress());
            stmt.setInt(14, task.getId());
            stmt.setInt(15, task.getUserId());
            
            stmt.executeUpdate();
            
            // Update tags
            updateTaskTags(conn, task);
        }
    }
    
    private void updateTaskTags(Connection conn, Task task) throws SQLException {
        // Remove existing tag links
        String deleteTagLinks = "DELETE FROM task_to_tag WHERE task_id = ?";
        try (PreparedStatement stmt = conn.prepareStatement(deleteTagLinks)) {
            stmt.setInt(1, task.getId());
            stmt.executeUpdate();
        }
        
        // Add new tag links
        if (task.getTags() != null && !task.getTags().isEmpty()) {
            saveTaskTags(conn, task);
        }
    }
    
    public void deleteTask(int taskId, int userId) throws SQLException {
        // First delete all subtasks recursively
        List<Integer> subtaskIds = getSubtaskIds(taskId);
        for (int subtaskId : subtaskIds) {
            deleteTask(subtaskId, userId);
        }
        
        try (Connection conn = DatabaseUtil.getConnection()) {
            // Delete tag links
            String deleteTagLinks = "DELETE FROM task_to_tag WHERE task_id = ?";
            try (PreparedStatement stmt = conn.prepareStatement(deleteTagLinks)) {
                stmt.setInt(1, taskId);
                stmt.executeUpdate();
            }
            
            // Delete reminders
            String deleteReminders = "DELETE FROM task_reminders WHERE task_id = ?";
            try (PreparedStatement stmt = conn.prepareStatement(deleteReminders)) {
                stmt.setInt(1, taskId);
                stmt.executeUpdate();
            }
            
            // Delete the task
            String deleteTask = "DELETE FROM tasks WHERE id = ? AND user_id = ?";
            try (PreparedStatement stmt = conn.prepareStatement(deleteTask)) {
                stmt.setInt(1, taskId);
                stmt.setInt(2, userId);
                stmt.executeUpdate();
            }
        }
    }
    
    private List<Integer> getSubtaskIds(int parentTaskId) throws SQLException {
        List<Integer> subtaskIds = new ArrayList<>();
        String query = "SELECT id FROM tasks WHERE parent_task_id = ?";
        
        try (Connection conn = DatabaseUtil.getConnection();
             PreparedStatement stmt = conn.prepareStatement(query)) {
            
            stmt.setInt(1, parentTaskId);
            ResultSet rs = stmt.executeQuery();
            
            while (rs.next()) {
                subtaskIds.add(rs.getInt("id"));
            }
        }
        
        return subtaskIds;
    }
    
    public List<Task> searchTasks(int userId, String searchTerm) throws SQLException {
        Map<Integer, Task> tasksMap = new HashMap<>();
        
        String query = "SELECT t.*, c.name as category_name FROM tasks t " +
                       "LEFT JOIN task_categories c ON t.category_id = c.id " +
                       "WHERE t.user_id = ? AND (t.title LIKE ? OR t.description LIKE ?) " +
                       "ORDER BY t.due_date ASC";
        
        try (Connection conn = DatabaseUtil.getConnection();
             PreparedStatement stmt = conn.prepareStatement(query)) {
            
            stmt.setInt(1, userId);
            stmt.setString(2, "%" + searchTerm + "%");
            stmt.setString(3, "%" + searchTerm + "%");
            
            ResultSet rs = stmt.executeQuery();
            
            while (rs.next()) {
                Task task = extractTaskFromResultSet(rs);
                tasksMap.put(task.getId(), task);
            }
        }
        
        // Also search in tags
        String tagQuery = "SELECT t.*, c.name as category_name FROM tasks t " +
                         "LEFT JOIN task_categories c ON t.category_id = c.id " +
                         "JOIN task_to_tag tt ON t.id = tt.task_id " +
                         "JOIN task_tags tg ON tt.tag_id = tg.id " +
                         "WHERE t.user_id = ? AND tg.name LIKE ? " +
                         "ORDER BY t.due_date ASC";
        
        try (Connection conn = DatabaseUtil.getConnection();
             PreparedStatement stmt = conn.prepareStatement(tagQuery)) {
            
            stmt.setInt(1, userId);
            stmt.setString(2, "%" + searchTerm + "%");
            
            ResultSet rs = stmt.executeQuery();
            
            while (rs.next()) {
                Task task = extractTaskFromResultSet(rs);
                tasksMap.put(task.getId(), task);
            }
        }
        
        // Load tags for each task
        loadTaskTags(tasksMap);
        
        return new ArrayList<>(tasksMap.values());
    }
    
    public List<Task> filterTasks(int userId, Integer categoryId, Integer status, Integer priority) throws SQLException {
        Map<Integer, Task> tasksMap = new HashMap<>();
        
        StringBuilder queryBuilder = new StringBuilder();
        queryBuilder.append("SELECT t.*, c.name as category_name FROM tasks t ");
        queryBuilder.append("LEFT JOIN task_categories c ON t.category_id = c.id ");
        queryBuilder.append("WHERE t.user_id = ? ");
        
        if (categoryId != null) {
            queryBuilder.append("AND t.category_id = ? ");
        }
        
        if (status != null) {
            queryBuilder.append("AND t.status = ? ");
        }
        
        if (priority != null) {
            queryBuilder.append("AND t.priority = ? ");
        }
        
        queryBuilder.append("ORDER BY t.due_date ASC");
        
        try (Connection conn = DatabaseUtil.getConnection();
             PreparedStatement stmt = conn.prepareStatement(queryBuilder.toString())) {
            
            int paramIndex = 1;
            stmt.setInt(paramIndex++, userId);
            
            if (categoryId != null) {
                stmt.setInt(paramIndex++, categoryId);
            }
            
            if (status != null) {
                stmt.setInt(paramIndex++, status);
            }
            
            if (priority != null) {
                stmt.setInt(paramIndex++, priority);
            }
            
            ResultSet rs = stmt.executeQuery();
            
            while (rs.next()) {
                Task task = extractTaskFromResultSet(rs);
                tasksMap.put(task.getId(), task);
            }
        }
        
        // Load tags for each task
        loadTaskTags(tasksMap);
        
        return new ArrayList<>(tasksMap.values());
    }
} 