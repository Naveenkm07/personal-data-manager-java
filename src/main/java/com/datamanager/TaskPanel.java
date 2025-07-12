package com.datamanager;

import com.datamanager.dao.TaskDAO;
import com.datamanager.model.Task;
import com.datamanager.model.TaskCategory;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableRowSorter;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.io.PrintWriter;
import java.sql.SQLException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.List;
import java.util.Map;

public class TaskPanel extends JPanel {
    private final int userId;
    private final JFrame parentFrame;
    private final TaskDAO taskDAO;
    
    private JTable taskTable;
    private DefaultTableModel taskTableModel;
    private List<Task> currentTasks;
    private JComboBox<TaskCategory> categoryFilter;
    private JComboBox<String> statusFilter;
    private JComboBox<String> priorityFilter;
    private JTextField searchField;
    private JProgressBar progressBar;
    
    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd");
    
    public TaskPanel(JFrame parentFrame, int userId) {
        this.parentFrame = parentFrame;
        this.userId = userId;
        this.taskDAO = new TaskDAO();
        this.currentTasks = new ArrayList<>();
        
        setLayout(new BorderLayout());
        
        // Create top panel with search and filters
        JPanel topPanel = createTopPanel();
        add(topPanel, BorderLayout.NORTH);
        
        // Create table with tasks
        JPanel tablePanel = createTablePanel();
        add(tablePanel, BorderLayout.CENTER);
        
        // Create bottom panel with action buttons
        JPanel bottomPanel = createBottomPanel();
        add(bottomPanel, BorderLayout.SOUTH);
        
        // Load tasks
        refreshTasks();
    }
    
    private JPanel createTopPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(new EmptyBorder(5, 5, 5, 5));
        
        // Search panel
        JPanel searchPanel = new JPanel(new BorderLayout());
        searchField = new JTextField(20);
        JButton searchButton = new JButton("Search");
        searchButton.addActionListener(e -> searchTasks());
        searchPanel.add(new JLabel("Search: "), BorderLayout.WEST);
        searchPanel.add(searchField, BorderLayout.CENTER);
        searchPanel.add(searchButton, BorderLayout.EAST);
        
        // Filter panel
        JPanel filterPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        
        // Category filter
        filterPanel.add(new JLabel("Category:"));
        categoryFilter = new JComboBox<>();
        categoryFilter.addItem(null); // All categories
        try {
            for (TaskCategory category : taskDAO.getCategories(userId)) {
                categoryFilter.addItem(category);
            }
        } catch (SQLException e) {
            showError("Error loading categories: " + e.getMessage());
        }
        categoryFilter.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
                if (value == null) {
                    value = "All Categories";
                }
                return super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
            }
        });
        categoryFilter.addActionListener(e -> applyFilters());
        filterPanel.add(categoryFilter);
        
        // Status filter
        filterPanel.add(new JLabel("Status:"));
        statusFilter = new JComboBox<>(new String[]{"All", "Not Started", "In Progress", "Completed"});
        statusFilter.addActionListener(e -> applyFilters());
        filterPanel.add(statusFilter);
        
        // Priority filter
        filterPanel.add(new JLabel("Priority:"));
        priorityFilter = new JComboBox<>(new String[]{"All", "Low", "Medium", "High"});
        priorityFilter.addActionListener(e -> applyFilters());
        filterPanel.add(priorityFilter);
        
        // Reset button
        JButton resetButton = new JButton("Reset Filters");
        resetButton.addActionListener(e -> resetFilters());
        filterPanel.add(resetButton);
        
        panel.add(searchPanel, BorderLayout.NORTH);
        panel.add(filterPanel, BorderLayout.CENTER);
        
        return panel;
    }
    
    private JPanel createTablePanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(new EmptyBorder(5, 5, 5, 5));
        
        // Task table
        String[] columnNames = {"Title", "Category", "Priority", "Status", "Due Date", "Progress"};
        taskTableModel = new DefaultTableModel(columnNames, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
            
            @Override
            public Class<?> getColumnClass(int columnIndex) {
                if (columnIndex == 5) { // Progress column
                    return JProgressBar.class;
                }
                return Object.class;
            }
        };
        
        taskTable = new JTable(taskTableModel);
        taskTable.setRowHeight(25);
        taskTable.setDefaultRenderer(JProgressBar.class, (table, value, isSelected, hasFocus, row, column) -> {
            JProgressBar bar = new JProgressBar(0, 100);
            bar.setValue((Integer) value);
            bar.setStringPainted(true);
            return bar;
        });
        
        // Add double-click listener for editing
        taskTable.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    int row = taskTable.getSelectedRow();
                    if (row >= 0) {
                        editTask(row);
                    }
                }
            }
        });
        
        // Add sorting
        TableRowSorter<DefaultTableModel> sorter = new TableRowSorter<>(taskTableModel);
        taskTable.setRowSorter(sorter);
        
        JScrollPane scrollPane = new JScrollPane(taskTable);
        panel.add(scrollPane, BorderLayout.CENTER);
        
        return panel;
    }
    
    private JPanel createBottomPanel() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        panel.setBorder(new EmptyBorder(5, 5, 5, 5));
        
        JButton addButton = new JButton("Add Task");
        JButton editButton = new JButton("Edit Task");
        JButton deleteButton = new JButton("Delete Task");
        JButton completeButton = new JButton("Mark Complete");
        JButton exportButton = new JButton("Export Tasks");
        JButton statsButton = new JButton("Task Statistics");
        
        addButton.addActionListener(e -> addTask());
        editButton.addActionListener(e -> editTask(taskTable.getSelectedRow()));
        deleteButton.addActionListener(e -> deleteTask(taskTable.getSelectedRow()));
        completeButton.addActionListener(e -> markTaskComplete(taskTable.getSelectedRow()));
        exportButton.addActionListener(e -> exportTasks());
        statsButton.addActionListener(e -> showTaskStatistics());
        
        panel.add(addButton);
        panel.add(editButton);
        panel.add(deleteButton);
        panel.add(completeButton);
        panel.add(exportButton);
        panel.add(statsButton);
        
        return panel;
    }
    
    private void refreshTasks() {
        try {
            currentTasks = taskDAO.getTasks(userId);
            updateTaskTable(currentTasks);
        } catch (SQLException e) {
            showError("Error loading tasks: " + e.getMessage());
        }
    }
    
    private void updateTaskTable(List<Task> tasks) {
        taskTableModel.setRowCount(0);
        
        for (Task task : tasks) {
            String dueDate = task.getDueDate() != null ? DATE_FORMAT.format(task.getDueDate()) : "";
            taskTableModel.addRow(new Object[]{
                task.getTitle(),
                task.getCategoryName() != null ? task.getCategoryName() : "",
                task.getPriorityText(),
                task.getStatusText(),
                dueDate,
                task.getProgress()
            });
            
            // Add subtasks if any
            for (Task subtask : task.getSubtasks()) {
                dueDate = subtask.getDueDate() != null ? DATE_FORMAT.format(subtask.getDueDate()) : "";
                taskTableModel.addRow(new Object[]{
                    "    ↳ " + subtask.getTitle(),
                    subtask.getCategoryName() != null ? subtask.getCategoryName() : "",
                    subtask.getPriorityText(),
                    subtask.getStatusText(),
                    dueDate,
                    subtask.getProgress()
                });
            }
        }
    }
    
    private void searchTasks() {
        String searchTerm = searchField.getText().trim();
        if (searchTerm.isEmpty()) {
            refreshTasks();
            return;
        }
        
        try {
            List<Task> searchResults = taskDAO.searchTasks(userId, searchTerm);
            updateTaskTable(searchResults);
        } catch (SQLException e) {
            showError("Error searching tasks: " + e.getMessage());
        }
    }
    
    private void applyFilters() {
        try {
            Integer categoryId = null;
            if (categoryFilter.getSelectedItem() != null && categoryFilter.getSelectedItem() instanceof TaskCategory) {
                categoryId = ((TaskCategory) categoryFilter.getSelectedItem()).getId();
            }
            
            Integer status = null;
            if (statusFilter.getSelectedIndex() > 0) {
                status = statusFilter.getSelectedIndex() - 1;
            }
            
            Integer priority = null;
            if (priorityFilter.getSelectedIndex() > 0) {
                priority = priorityFilter.getSelectedIndex();
            }
            
            if (categoryId == null && status == null && priority == null) {
                refreshTasks();
                return;
            }
            
            List<Task> filteredTasks = taskDAO.filterTasks(userId, categoryId, status, priority);
            updateTaskTable(filteredTasks);
        } catch (SQLException e) {
            showError("Error applying filters: " + e.getMessage());
        }
    }
    
    private void resetFilters() {
        categoryFilter.setSelectedIndex(0);
        statusFilter.setSelectedIndex(0);
        priorityFilter.setSelectedIndex(0);
        searchField.setText("");
        refreshTasks();
    }
    
    private void addTask() {
        TaskDialog dialog = new TaskDialog(parentFrame, userId, null, currentTasks);
        dialog.setVisible(true);
        
        if (dialog.isConfirmed()) {
            Task newTask = dialog.getTask();
            try {
                taskDAO.addTask(newTask);
                refreshTasks();
                JOptionPane.showMessageDialog(parentFrame, 
                    "Task added successfully!", 
                    "Success", JOptionPane.INFORMATION_MESSAGE);
            } catch (SQLException e) {
                showError("Error adding task: " + e.getMessage());
            }
        }
    }
    
    private void editTask(int selectedRow) {
        if (selectedRow < 0) {
            showError("Please select a task to edit!");
            return;
        }
        
        // Find the task for the selected row
        String title = (String) taskTable.getValueAt(selectedRow, 0);
        Task selectedTask = null;
        
        // Check if it's a subtask (titles of subtasks start with an arrow)
        boolean isSubtask = title.trim().startsWith("↳");
        
        if (isSubtask) {
            // Remove the arrow prefix for comparison
            String cleanTitle = title.replace("↳", "").trim();
            
            // Search through all tasks and their subtasks
            for (Task task : currentTasks) {
                for (Task subtask : task.getSubtasks()) {
                    if (subtask.getTitle().equals(cleanTitle)) {
                        selectedTask = subtask;
                        break;
                    }
                }
                if (selectedTask != null) break;
            }
        } else {
            // It's a main task
            for (Task task : currentTasks) {
                if (task.getTitle().equals(title)) {
                    selectedTask = task;
                    break;
                }
            }
        }
        
        if (selectedTask == null) {
            showError("Could not find the selected task!");
            return;
        }
        
        TaskDialog dialog = new TaskDialog(parentFrame, userId, selectedTask, currentTasks);
        dialog.setVisible(true);
        
        if (dialog.isConfirmed()) {
            try {
                taskDAO.updateTask(selectedTask);
                refreshTasks();
                JOptionPane.showMessageDialog(parentFrame, 
                    "Task updated successfully!", 
                    "Success", JOptionPane.INFORMATION_MESSAGE);
            } catch (SQLException e) {
                showError("Error updating task: " + e.getMessage());
            }
        }
    }
    
    private void deleteTask(int selectedRow) {
        if (selectedRow < 0) {
            showError("Please select a task to delete!");
            return;
        }
        
        // Find the task for the selected row
        String title = (String) taskTable.getValueAt(selectedRow, 0);
        Task selectedTask = null;
        
        // Check if it's a subtask
        boolean isSubtask = title.trim().startsWith("↳");
        
        if (isSubtask) {
            // Remove the arrow prefix for comparison
            String cleanTitle = title.replace("↳", "").trim();
            
            // Search through all tasks and their subtasks
            for (Task task : currentTasks) {
                for (Task subtask : task.getSubtasks()) {
                    if (subtask.getTitle().equals(cleanTitle)) {
                        selectedTask = subtask;
                        break;
                    }
                }
                if (selectedTask != null) break;
            }
        } else {
            // It's a main task
            for (Task task : currentTasks) {
                if (task.getTitle().equals(title)) {
                    selectedTask = task;
                    break;
                }
            }
        }
        
        if (selectedTask == null) {
            showError("Could not find the selected task!");
            return;
        }
        
        int confirm = JOptionPane.showConfirmDialog(parentFrame,
            "Are you sure you want to delete this task?\n" +
            (selectedTask.getSubtasks() != null && !selectedTask.getSubtasks().isEmpty() ?
             "This will also delete all subtasks!" : ""),
            "Confirm Delete",
            JOptionPane.YES_NO_OPTION,
            JOptionPane.WARNING_MESSAGE);
            
        if (confirm == JOptionPane.YES_OPTION) {
            try {
                taskDAO.deleteTask(selectedTask.getId(), userId);
                refreshTasks();
                JOptionPane.showMessageDialog(parentFrame, 
                    "Task deleted successfully!", 
                    "Success", JOptionPane.INFORMATION_MESSAGE);
            } catch (SQLException e) {
                showError("Error deleting task: " + e.getMessage());
            }
        }
    }
    
    private void markTaskComplete(int selectedRow) {
        if (selectedRow < 0) {
            showError("Please select a task to mark as complete!");
            return;
        }
        
        // Find the task for the selected row
        String title = (String) taskTable.getValueAt(selectedRow, 0);
        Task selectedTask = null;
        
        // Check if it's a subtask
        boolean isSubtask = title.trim().startsWith("↳");
        
        if (isSubtask) {
            // Remove the arrow prefix for comparison
            String cleanTitle = title.replace("↳", "").trim();
            
            // Search through all tasks and their subtasks
            for (Task task : currentTasks) {
                for (Task subtask : task.getSubtasks()) {
                    if (subtask.getTitle().equals(cleanTitle)) {
                        selectedTask = subtask;
                        break;
                    }
                }
                if (selectedTask != null) break;
            }
        } else {
            // It's a main task
            for (Task task : currentTasks) {
                if (task.getTitle().equals(title)) {
                    selectedTask = task;
                    break;
                }
            }
        }
        
        if (selectedTask == null) {
            showError("Could not find the selected task!");
            return;
        }
        
        selectedTask.setStatus(2); // Completed
        selectedTask.setProgress(100);
        selectedTask.setCompletionDate(new Date());
        
        try {
            taskDAO.updateTask(selectedTask);
            refreshTasks();
            JOptionPane.showMessageDialog(parentFrame, 
                "Task marked as complete!", 
                "Success", JOptionPane.INFORMATION_MESSAGE);
        } catch (SQLException e) {
            showError("Error updating task: " + e.getMessage());
        }
    }
    
    private void exportTasks() {
        // Choose export format
        String[] options = {"CSV", "Text", "Cancel"};
        int choice = JOptionPane.showOptionDialog(parentFrame,
            "Select export format:",
            "Export Tasks",
            JOptionPane.DEFAULT_OPTION,
            JOptionPane.QUESTION_MESSAGE,
            null,
            options,
            options[0]);
            
        if (choice == 2 || choice == JOptionPane.CLOSED_OPTION) {
            return; // User canceled
        }
        
        // Choose file to save
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setDialogTitle("Save Export File");
        
        String extension = choice == 0 ? ".csv" : ".txt";
        fileChooser.setSelectedFile(new File("tasks_export" + extension));
        
        if (fileChooser.showSaveDialog(parentFrame) != JFileChooser.APPROVE_OPTION) {
            return; // User canceled
        }
        
        File file = fileChooser.getSelectedFile();
        if (!file.getName().endsWith(extension)) {
            file = new File(file.getPath() + extension);
        }
        
        try {
            if (choice == 0) {
                exportToCSV(file);
            } else {
                exportToText(file);
            }
            JOptionPane.showMessageDialog(parentFrame, 
                "Tasks exported successfully to " + file.getName(), 
                "Export Complete", JOptionPane.INFORMATION_MESSAGE);
        } catch (Exception e) {
            showError("Error exporting tasks: " + e.getMessage());
        }
    }
    
    private void exportToCSV(File file) throws Exception {
        try (PrintWriter writer = new PrintWriter(file)) {
            // Write header
            writer.println("Title,Category,Priority,Status,Due Date,Progress,Tags,Description");
            
            // Write tasks
            for (Task task : currentTasks) {
                writer.println(
                    String.format("\"%s\",\"%s\",\"%s\",\"%s\",\"%s\",%d,\"%s\",\"%s\"",
                        escapeCsv(task.getTitle()),
                        escapeCsv(task.getCategoryName() != null ? task.getCategoryName() : ""),
                        task.getPriorityText(),
                        task.getStatusText(),
                        task.getDueDate() != null ? DATE_FORMAT.format(task.getDueDate()) : "",
                        task.getProgress(),
                        escapeCsv(task.getTagsText()),
                        escapeCsv(task.getDescription() != null ? task.getDescription() : "")
                    )
                );
                
                // Write subtasks
                for (Task subtask : task.getSubtasks()) {
                    writer.println(
                        String.format("\"%s\",\"%s\",\"%s\",\"%s\",\"%s\",%d,\"%s\",\"%s\"",
                            escapeCsv("- " + subtask.getTitle()),
                            escapeCsv(subtask.getCategoryName() != null ? subtask.getCategoryName() : ""),
                            subtask.getPriorityText(),
                            subtask.getStatusText(),
                            subtask.getDueDate() != null ? DATE_FORMAT.format(subtask.getDueDate()) : "",
                            subtask.getProgress(),
                            escapeCsv(subtask.getTagsText()),
                            escapeCsv(subtask.getDescription() != null ? subtask.getDescription() : "")
                        )
                    );
                }
            }
        }
    }
    
    private void exportToText(File file) throws Exception {
        try (PrintWriter writer = new PrintWriter(file)) {
            writer.println("TASK LIST");
            writer.println("=========");
            writer.println();
            
            SimpleDateFormat fullDateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm");
            
            for (Task task : currentTasks) {
                writer.println("TASK: " + task.getTitle());
                writer.println("Category: " + (task.getCategoryName() != null ? task.getCategoryName() : "None"));
                writer.println("Priority: " + task.getPriorityText());
                writer.println("Status: " + task.getStatusText() + " (" + task.getProgress() + "% complete)");
                
                if (task.getDueDate() != null) {
                    writer.println("Due Date: " + DATE_FORMAT.format(task.getDueDate()));
                }
                
                if (task.getCreationDate() != null) {
                    writer.println("Created: " + fullDateFormat.format(task.getCreationDate()));
                }
                
                if (task.getCompletionDate() != null) {
                    writer.println("Completed: " + fullDateFormat.format(task.getCompletionDate()));
                }
                
                if (task.isRecurring()) {
                    writer.println("Recurrence: " + task.getRecurrenceText());
                }
                
                if (task.getEstimatedMinutes() != null) {
                    writer.println("Time Estimate: " + task.getEstimatedTimeText());
                }
                
                if (task.getTags() != null && !task.getTags().isEmpty()) {
                    writer.println("Tags: " + task.getTagsText());
                }
                
                if (task.getDescription() != null && !task.getDescription().isEmpty()) {
                    writer.println("Description: " + task.getDescription());
                }
                
                if (!task.getSubtasks().isEmpty()) {
                    writer.println("Subtasks:");
                    for (Task subtask : task.getSubtasks()) {
                        writer.println("  - " + subtask.getTitle() + " (" + subtask.getStatusText() + 
                                      ", " + subtask.getProgress() + "% complete)");
                    }
                }
                
                writer.println("-------------------------------------------------");
                writer.println();
            }
        }
    }
    
    private String escapeCsv(String s) {
        return s.replace("\"", "\"\"");
    }
    
    private void showTaskStatistics() {
        // Calculate statistics
        int totalTasks = 0;
        int completedTasks = 0;
        int inProgressTasks = 0;
        int notStartedTasks = 0;
        int overdueTasks = 0;
        int dueTodayTasks = 0;
        
        Map<String, Integer> categoryStats = new HashMap<>();
        Map<String, Integer> priorityStats = new HashMap<>();
        
        Date today = new Date();
        today.setHours(0);
        today.setMinutes(0);
        today.setSeconds(0);
        
        // Process main tasks
        for (Task task : currentTasks) {
            totalTasks++;
            
            if (task.getStatus() == 2) completedTasks++;
            else if (task.getStatus() == 1) inProgressTasks++;
            else notStartedTasks++;
            
            if (task.getDueDate() != null && task.getStatus() != 2) {
                if (task.getDueDate().before(today)) {
                    overdueTasks++;
                } else if (isSameDay(task.getDueDate(), today)) {
                    dueTodayTasks++;
                }
            }
            
            // Category stats
            String category = task.getCategoryName() != null ? task.getCategoryName() : "None";
            categoryStats.put(category, categoryStats.getOrDefault(category, 0) + 1);
            
            // Priority stats
            String priority = task.getPriorityText();
            priorityStats.put(priority, priorityStats.getOrDefault(priority, 0) + 1);
            
            // Process subtasks
            for (Task subtask : task.getSubtasks()) {
                totalTasks++;
                
                if (subtask.getStatus() == 2) completedTasks++;
                else if (subtask.getStatus() == 1) inProgressTasks++;
                else notStartedTasks++;
                
                if (subtask.getDueDate() != null && subtask.getStatus() != 2) {
                    if (subtask.getDueDate().before(today)) {
                        overdueTasks++;
                    } else if (isSameDay(subtask.getDueDate(), today)) {
                        dueTodayTasks++;
                    }
                }
            }
        }
        
        // Build statistics message
        StringBuilder message = new StringBuilder();
        message.append("Task Statistics\n\n");
        
        message.append("Total Tasks: ").append(totalTasks).append("\n");
        message.append("Completed: ").append(completedTasks).append(" (")
               .append(totalTasks > 0 ? (completedTasks * 100 / totalTasks) : 0).append("%)\n");
        message.append("In Progress: ").append(inProgressTasks).append("\n");
        message.append("Not Started: ").append(notStartedTasks).append("\n\n");
        
        message.append("Overdue Tasks: ").append(overdueTasks).append("\n");
        message.append("Due Today: ").append(dueTodayTasks).append("\n\n");
        
        message.append("Tasks by Category:\n");
        for (Map.Entry<String, Integer> entry : categoryStats.entrySet()) {
            message.append("  ").append(entry.getKey()).append(": ").append(entry.getValue()).append("\n");
        }
        
        message.append("\nTasks by Priority:\n");
        for (Map.Entry<String, Integer> entry : priorityStats.entrySet()) {
            message.append("  ").append(entry.getKey()).append(": ").append(entry.getValue()).append("\n");
        }
        
        // Show statistics dialog
        JTextArea textArea = new JTextArea(message.toString());
        textArea.setEditable(false);
        textArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        
        JScrollPane scrollPane = new JScrollPane(textArea);
        scrollPane.setPreferredSize(new Dimension(400, 400));
        
        JOptionPane.showMessageDialog(parentFrame, scrollPane, "Task Statistics", JOptionPane.INFORMATION_MESSAGE);
    }
    
    private boolean isSameDay(Date date1, Date date2) {
        Calendar cal1 = Calendar.getInstance();
        cal1.setTime(date1);
        Calendar cal2 = Calendar.getInstance();
        cal2.setTime(date2);
        return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
               cal1.get(Calendar.MONTH) == cal2.get(Calendar.MONTH) &&
               cal1.get(Calendar.DAY_OF_MONTH) == cal2.get(Calendar.DAY_OF_MONTH);
    }
    
    private void showError(String message) {
        JOptionPane.showMessageDialog(parentFrame,
            message,
            "Error",
            JOptionPane.ERROR_MESSAGE);
    }
} 