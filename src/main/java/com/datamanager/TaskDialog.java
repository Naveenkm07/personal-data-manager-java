package com.datamanager;

import com.datamanager.dao.TaskDAO;
import com.datamanager.model.Task;
import com.datamanager.model.TaskCategory;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.sql.SQLException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class TaskDialog extends JDialog {
    private final Task task;
    private final int userId;
    private final boolean isNew;
    private final TaskDAO taskDAO;
    private boolean confirmed = false;
    
    // Form fields
    private JTextField titleField;
    private JTextArea descriptionArea;
    private JComboBox<TaskCategory> categoryCombo;
    private JComboBox<String> priorityCombo;
    private JComboBox<String> statusCombo;
    private JTextField dueDateField;
    private JCheckBox recurringCheckBox;
    private JComboBox<String> recurrenceTypeCombo;
    private JSpinner recurrenceValueSpinner;
    private JSpinner hoursSpinner;
    private JSpinner minutesSpinner;
    private JSlider progressSlider;
    private JTextField tagsField;
    private JComboBox<Task> parentTaskCombo;
    private JList<Task> subtasksList;
    private DefaultListModel<Task> subtasksModel;
    
    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd");
    
    public TaskDialog(JFrame parent, int userId, Task task, List<Task> allTasks) {
        super(parent, task == null ? "Add New Task" : "Edit Task", true);
        this.userId = userId;
        this.taskDAO = new TaskDAO();
        
        if (task == null) {
            this.task = new Task(userId, "");
            this.isNew = true;
        } else {
            this.task = task;
            this.isNew = false;
        }
        
        setupUI(allTasks);
        loadTaskData();
        
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setSize(700, 600);
        setLocationRelativeTo(parent);
    }
    
    private void setupUI(List<Task> allTasks) {
        JPanel contentPanel = new JPanel();
        contentPanel.setBorder(new EmptyBorder(10, 10, 10, 10));
        contentPanel.setLayout(new BorderLayout());
        
        // Main form panel
        JPanel formPanel = new JPanel();
        formPanel.setLayout(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.fill = GridBagConstraints.HORIZONTAL;
        
        // Title
        gbc.gridx = 0;
        gbc.gridy = 0;
        formPanel.add(new JLabel("Title:"), gbc);
        
        gbc.gridx = 1;
        gbc.gridwidth = 3;
        titleField = new JTextField(30);
        formPanel.add(titleField, gbc);
        
        // Description
        gbc.gridx = 0;
        gbc.gridy = 1;
        gbc.gridwidth = 1;
        formPanel.add(new JLabel("Description:"), gbc);
        
        gbc.gridx = 1;
        gbc.gridwidth = 3;
        descriptionArea = new JTextArea(5, 30);
        descriptionArea.setLineWrap(true);
        descriptionArea.setWrapStyleWord(true);
        JScrollPane descriptionScrollPane = new JScrollPane(descriptionArea);
        formPanel.add(descriptionScrollPane, gbc);
        
        // Category
        gbc.gridx = 0;
        gbc.gridy = 2;
        gbc.gridwidth = 1;
        formPanel.add(new JLabel("Category:"), gbc);
        
        gbc.gridx = 1;
        categoryCombo = new JComboBox<>();
        try {
            List<TaskCategory> categories = taskDAO.getCategories(userId);
            for (TaskCategory category : categories) {
                categoryCombo.addItem(category);
            }
        } catch (SQLException e) {
            JOptionPane.showMessageDialog(this, "Error loading categories: " + e.getMessage());
        }
        formPanel.add(categoryCombo, gbc);
        
        JButton newCategoryButton = new JButton("New Category");
        newCategoryButton.addActionListener(e -> createNewCategory());
        gbc.gridx = 2;
        formPanel.add(newCategoryButton, gbc);
        
        // Priority
        gbc.gridx = 0;
        gbc.gridy = 3;
        formPanel.add(new JLabel("Priority:"), gbc);
        
        gbc.gridx = 1;
        priorityCombo = new JComboBox<>(new String[]{"Low", "Medium", "High"});
        formPanel.add(priorityCombo, gbc);
        
        // Status
        gbc.gridx = 2;
        formPanel.add(new JLabel("Status:"), gbc);
        
        gbc.gridx = 3;
        statusCombo = new JComboBox<>(new String[]{"Not Started", "In Progress", "Completed"});
        formPanel.add(statusCombo, gbc);
        
        // Due Date
        gbc.gridx = 0;
        gbc.gridy = 4;
        formPanel.add(new JLabel("Due Date:"), gbc);
        
        gbc.gridx = 1;
        dueDateField = new JTextField(10);
        formPanel.add(dueDateField, gbc);
        
        JButton calendarButton = new JButton("...");
        calendarButton.addActionListener(e -> showDatePicker());
        gbc.gridx = 2;
        gbc.gridwidth = 1;
        formPanel.add(calendarButton, gbc);
        
        // Recurring task
        gbc.gridx = 0;
        gbc.gridy = 5;
        formPanel.add(new JLabel("Recurring:"), gbc);
        
        gbc.gridx = 1;
        recurringCheckBox = new JCheckBox("This is a recurring task");
        recurringCheckBox.addActionListener(e -> updateRecurrenceFields());
        formPanel.add(recurringCheckBox, gbc);
        
        // Recurrence type
        gbc.gridx = 0;
        gbc.gridy = 6;
        formPanel.add(new JLabel("Repeat every:"), gbc);
        
        gbc.gridx = 1;
        recurrenceValueSpinner = new JSpinner(new SpinnerNumberModel(1, 1, 100, 1));
        formPanel.add(recurrenceValueSpinner, gbc);
        
        gbc.gridx = 2;
        recurrenceTypeCombo = new JComboBox<>(new String[]{"Day", "Week", "Month", "Year"});
        formPanel.add(recurrenceTypeCombo, gbc);
        
        // Estimated time
        gbc.gridx = 0;
        gbc.gridy = 7;
        formPanel.add(new JLabel("Estimated Time:"), gbc);
        
        JPanel timePanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        hoursSpinner = new JSpinner(new SpinnerNumberModel(0, 0, 100, 1));
        minutesSpinner = new JSpinner(new SpinnerNumberModel(0, 0, 59, 1));
        timePanel.add(hoursSpinner);
        timePanel.add(new JLabel("h"));
        timePanel.add(minutesSpinner);
        timePanel.add(new JLabel("m"));
        gbc.gridx = 1;
        gbc.gridwidth = 2;
        formPanel.add(timePanel, gbc);
        
        // Progress
        gbc.gridx = 0;
        gbc.gridy = 8;
        gbc.gridwidth = 1;
        formPanel.add(new JLabel("Progress:"), gbc);
        
        gbc.gridx = 1;
        gbc.gridwidth = 3;
        progressSlider = new JSlider(0, 100, 0);
        progressSlider.setMajorTickSpacing(25);
        progressSlider.setMinorTickSpacing(5);
        progressSlider.setPaintTicks(true);
        progressSlider.setPaintLabels(true);
        progressSlider.addChangeListener(e -> updateStatusBasedOnProgress());
        formPanel.add(progressSlider, gbc);
        
        // Tags
        gbc.gridx = 0;
        gbc.gridy = 9;
        gbc.gridwidth = 1;
        formPanel.add(new JLabel("Tags:"), gbc);
        
        gbc.gridx = 1;
        gbc.gridwidth = 3;
        tagsField = new JTextField();
        formPanel.add(tagsField, gbc);
        
        // Parent task
        gbc.gridx = 0;
        gbc.gridy = 10;
        gbc.gridwidth = 1;
        formPanel.add(new JLabel("Parent Task:"), gbc);
        
        gbc.gridx = 1;
        gbc.gridwidth = 3;
        parentTaskCombo = new JComboBox<>();
        parentTaskCombo.addItem(null); // No parent
        if (allTasks != null) {
            for (Task t : allTasks) {
                if (!t.equals(task)) { // Don't add current task as potential parent
                    parentTaskCombo.addItem(t);
                }
            }
        }
        parentTaskCombo.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
                if (value == null) {
                    value = "None";
                }
                return super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
            }
        });
        formPanel.add(parentTaskCombo, gbc);
        
        // Subtasks
        gbc.gridx = 0;
        gbc.gridy = 11;
        gbc.gridwidth = 1;
        formPanel.add(new JLabel("Subtasks:"), gbc);
        
        gbc.gridx = 1;
        gbc.gridwidth = 3;
        subtasksModel = new DefaultListModel<>();
        subtasksList = new JList<>(subtasksModel);
        JScrollPane subtasksScrollPane = new JScrollPane(subtasksList);
        subtasksScrollPane.setPreferredSize(new Dimension(300, 100));
        formPanel.add(subtasksScrollPane, gbc);
        
        JPanel subtaskButtonPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton addSubtaskButton = new JButton("Add Subtask");
        JButton removeSubtaskButton = new JButton("Remove Selected");
        
        addSubtaskButton.addActionListener(e -> addSubtask());
        removeSubtaskButton.addActionListener(e -> removeSubtask());
        
        subtaskButtonPanel.add(addSubtaskButton);
        subtaskButtonPanel.add(removeSubtaskButton);
        
        gbc.gridx = 1;
        gbc.gridy = 12;
        gbc.gridwidth = 3;
        formPanel.add(subtaskButtonPanel, gbc);
        
        JScrollPane formScrollPane = new JScrollPane(formPanel);
        contentPanel.add(formScrollPane, BorderLayout.CENTER);
        
        // Buttons panel
        JPanel buttonPanel = new JPanel();
        JButton saveButton = new JButton("Save");
        JButton cancelButton = new JButton("Cancel");
        
        saveButton.addActionListener(e -> {
            if (validateAndSave()) {
                confirmed = true;
                dispose();
            }
        });
        
        cancelButton.addActionListener(e -> dispose());
        
        buttonPanel.add(saveButton);
        buttonPanel.add(cancelButton);
        contentPanel.add(buttonPanel, BorderLayout.SOUTH);
        
        setContentPane(contentPanel);
        updateRecurrenceFields();
    }
    
    private void loadTaskData() {
        // Set default values for a new task
        if (isNew) {
            statusCombo.setSelectedIndex(0); // Not Started
            priorityCombo.setSelectedIndex(0); // Low
            return;
        }
        
        // Load existing task data
        titleField.setText(task.getTitle());
        descriptionArea.setText(task.getDescription());
        
        // Set category
        if (task.getCategoryId() != null) {
            for (int i = 0; i < categoryCombo.getItemCount(); i++) {
                TaskCategory category = categoryCombo.getItemAt(i);
                if (category.getId() == task.getCategoryId()) {
                    categoryCombo.setSelectedIndex(i);
                    break;
                }
            }
        }
        
        priorityCombo.setSelectedIndex(task.getPriority() - 1);
        statusCombo.setSelectedIndex(task.getStatus());
        
        if (task.getDueDate() != null) {
            dueDateField.setText(DATE_FORMAT.format(task.getDueDate()));
        }
        
        recurringCheckBox.setSelected(task.isRecurring());
        
        if (task.getRecurrenceType() != null) {
            recurrenceTypeCombo.setSelectedIndex(task.getRecurrenceType() - 1);
        }
        
        if (task.getRecurrenceValue() != null) {
            recurrenceValueSpinner.setValue(task.getRecurrenceValue());
        }
        
        if (task.getEstimatedMinutes() != null) {
            int hours = task.getEstimatedMinutes() / 60;
            int minutes = task.getEstimatedMinutes() % 60;
            hoursSpinner.setValue(hours);
            minutesSpinner.setValue(minutes);
        }
        
        progressSlider.setValue(task.getProgress());
        
        // Load tags
        if (task.getTags() != null && !task.getTags().isEmpty()) {
            tagsField.setText(task.getTagsText());
        }
        
        // Set parent task
        if (task.getParentTaskId() != null) {
            for (int i = 0; i < parentTaskCombo.getItemCount(); i++) {
                Task parent = parentTaskCombo.getItemAt(i);
                if (parent != null && parent.getId() == task.getParentTaskId()) {
                    parentTaskCombo.setSelectedIndex(i);
                    break;
                }
            }
        }
        
        // Load subtasks
        if (task.getSubtasks() != null) {
            for (Task subtask : task.getSubtasks()) {
                subtasksModel.addElement(subtask);
            }
        }
        
        updateRecurrenceFields();
    }
    
    private boolean validateAndSave() {
        // Validate required fields
        String title = titleField.getText().trim();
        if (title.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Title is required!", "Validation Error", JOptionPane.ERROR_MESSAGE);
            return false;
        }
        
        // Update task with form values
        task.setTitle(title);
        task.setDescription(descriptionArea.getText().trim());
        
        TaskCategory selectedCategory = (TaskCategory) categoryCombo.getSelectedItem();
        if (selectedCategory != null) {
            task.setCategoryId(selectedCategory.getId());
            task.setCategoryName(selectedCategory.getName());
        } else {
            task.setCategoryId(null);
            task.setCategoryName(null);
        }
        
        task.setPriority(priorityCombo.getSelectedIndex() + 1);
        task.setStatus(statusCombo.getSelectedIndex());
        
        String dueDateStr = dueDateField.getText().trim();
        if (!dueDateStr.isEmpty()) {
            try {
                task.setDueDate(DATE_FORMAT.parse(dueDateStr));
            } catch (ParseException e) {
                JOptionPane.showMessageDialog(this, "Invalid date format. Please use yyyy-MM-dd format.", 
                    "Validation Error", JOptionPane.ERROR_MESSAGE);
                return false;
            }
        } else {
            task.setDueDate(null);
        }
        
        task.setRecurring(recurringCheckBox.isSelected());
        
        if (task.isRecurring()) {
            task.setRecurrenceType(recurrenceTypeCombo.getSelectedIndex() + 1);
            task.setRecurrenceValue((Integer) recurrenceValueSpinner.getValue());
        } else {
            task.setRecurrenceType(null);
            task.setRecurrenceValue(null);
        }
        
        int hours = (Integer) hoursSpinner.getValue();
        int minutes = (Integer) minutesSpinner.getValue();
        if (hours > 0 || minutes > 0) {
            task.setEstimatedMinutes(hours * 60 + minutes);
        } else {
            task.setEstimatedMinutes(null);
        }
        
        task.setProgress(progressSlider.getValue());
        
        // Parse tags
        String tagsText = tagsField.getText().trim();
        if (!tagsText.isEmpty()) {
            task.setTags(new ArrayList<>());
            String[] tagArray = tagsText.split(",");
            for (String tag : tagArray) {
                task.addTag(tag.trim());
            }
        } else {
            task.setTags(new ArrayList<>());
        }
        
        // Parent task
        Task selectedParent = (Task) parentTaskCombo.getSelectedItem();
        if (selectedParent != null) {
            task.setParentTaskId(selectedParent.getId());
        } else {
            task.setParentTaskId(null);
        }
        
        // Clear subtasks and add from model
        task.setSubtasks(new ArrayList<>());
        for (int i = 0; i < subtasksModel.size(); i++) {
            task.addSubtask(subtasksModel.getElementAt(i));
        }
        
        return true;
    }
    
    private void updateRecurrenceFields() {
        boolean recurring = recurringCheckBox.isSelected();
        recurrenceValueSpinner.setEnabled(recurring);
        recurrenceTypeCombo.setEnabled(recurring);
    }
    
    private void updateStatusBasedOnProgress() {
        int progress = progressSlider.getValue();
        if (progress == 100) {
            statusCombo.setSelectedIndex(2); // Completed
        } else if (progress > 0 && statusCombo.getSelectedIndex() == 0) {
            statusCombo.setSelectedIndex(1); // In Progress
        }
    }
    
    private void showDatePicker() {
        // A simple date picker dialog could be implemented here
        // For now, show a message about the date format
        JOptionPane.showMessageDialog(this, 
            "Please enter date in format: yyyy-MM-dd\nExample: 2025-06-15", 
            "Date Format", JOptionPane.INFORMATION_MESSAGE);
    }
    
    private void createNewCategory() {
        String name = JOptionPane.showInputDialog(this, "Enter category name:");
        if (name != null && !name.trim().isEmpty()) {
            String color = "#" + Integer.toHexString((int)(Math.random() * 0xFFFFFF));
            TaskCategory category = new TaskCategory(0, userId, name.trim(), color);
            
            try {
                category = taskDAO.addCategory(category);
                categoryCombo.addItem(category);
                categoryCombo.setSelectedItem(category);
            } catch (SQLException e) {
                JOptionPane.showMessageDialog(this, 
                    "Error creating category: " + e.getMessage(), 
                    "Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }
    
    private void addSubtask() {
        String title = JOptionPane.showInputDialog(this, "Enter subtask title:");
        if (title != null && !title.trim().isEmpty()) {
            Task subtask = new Task(userId, title.trim());
            subtask.setParentTaskId(task.getId());
            subtasksModel.addElement(subtask);
        }
    }
    
    private void removeSubtask() {
        int selectedIndex = subtasksList.getSelectedIndex();
        if (selectedIndex >= 0) {
            subtasksModel.remove(selectedIndex);
        }
    }
    
    public Task getTask() {
        return task;
    }
    
    public boolean isConfirmed() {
        return confirmed;
    }
} 