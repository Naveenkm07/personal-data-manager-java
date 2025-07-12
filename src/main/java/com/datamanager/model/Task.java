package com.datamanager.model;

import java.util.Date;
import java.util.ArrayList;
import java.util.List;

public class Task {
    private int id;
    private int userId;
    private String title;
    private String description;
    private Integer categoryId;
    private String categoryName;
    private int priority; // 1=Low, 2=Medium, 3=High
    private int status; // 0=Not Started, 1=In Progress, 2=Completed
    private Date dueDate;
    private Date completionDate;
    private Date creationDate;
    private boolean isRecurring;
    private Integer recurrenceType; // 1=Daily, 2=Weekly, 3=Monthly, 4=Yearly
    private Integer recurrenceValue;
    private Integer estimatedMinutes;
    private Integer actualMinutes;
    private int progress; // 0-100 percent
    private Integer parentTaskId;
    private List<Task> subtasks;
    private List<String> tags;
    private List<Date> reminders;
    
    public Task() {
        this.subtasks = new ArrayList<>();
        this.tags = new ArrayList<>();
        this.reminders = new ArrayList<>();
        this.priority = 1;
        this.status = 0;
        this.progress = 0;
        this.isRecurring = false;
    }
    
    // Required fields constructor
    public Task(int userId, String title) {
        this();
        this.userId = userId;
        this.title = title;
        this.creationDate = new Date();
    }
    
    // Full constructor
    public Task(int id, int userId, String title, String description, Integer categoryId, 
                String categoryName, int priority, int status, Date dueDate, Date completionDate, 
                Date creationDate, boolean isRecurring, Integer recurrenceType, 
                Integer recurrenceValue, Integer estimatedMinutes, Integer actualMinutes, 
                int progress, Integer parentTaskId) {
        this();
        this.id = id;
        this.userId = userId;
        this.title = title;
        this.description = description;
        this.categoryId = categoryId;
        this.categoryName = categoryName;
        this.priority = priority;
        this.status = status;
        this.dueDate = dueDate;
        this.completionDate = completionDate;
        this.creationDate = creationDate != null ? creationDate : new Date();
        this.isRecurring = isRecurring;
        this.recurrenceType = recurrenceType;
        this.recurrenceValue = recurrenceValue;
        this.estimatedMinutes = estimatedMinutes;
        this.actualMinutes = actualMinutes;
        this.progress = progress;
        this.parentTaskId = parentTaskId;
    }
    
    // Getters and Setters
    public int getId() {
        return id;
    }
    
    public void setId(int id) {
        this.id = id;
    }
    
    public int getUserId() {
        return userId;
    }
    
    public void setUserId(int userId) {
        this.userId = userId;
    }
    
    public String getTitle() {
        return title;
    }
    
    public void setTitle(String title) {
        this.title = title;
    }
    
    public String getDescription() {
        return description;
    }
    
    public void setDescription(String description) {
        this.description = description;
    }
    
    public Integer getCategoryId() {
        return categoryId;
    }
    
    public void setCategoryId(Integer categoryId) {
        this.categoryId = categoryId;
    }
    
    public String getCategoryName() {
        return categoryName;
    }
    
    public void setCategoryName(String categoryName) {
        this.categoryName = categoryName;
    }
    
    public int getPriority() {
        return priority;
    }
    
    public void setPriority(int priority) {
        this.priority = priority;
    }
    
    public String getPriorityText() {
        switch (priority) {
            case 1: return "Low";
            case 2: return "Medium";
            case 3: return "High";
            default: return "Unknown";
        }
    }
    
    public int getStatus() {
        return status;
    }
    
    public void setStatus(int status) {
        this.status = status;
        if (status == 2) { // Completed
            this.completionDate = new Date();
            this.progress = 100;
        }
    }
    
    public String getStatusText() {
        switch (status) {
            case 0: return "Not Started";
            case 1: return "In Progress";
            case 2: return "Completed";
            default: return "Unknown";
        }
    }
    
    public Date getDueDate() {
        return dueDate;
    }
    
    public void setDueDate(Date dueDate) {
        this.dueDate = dueDate;
    }
    
    public Date getCompletionDate() {
        return completionDate;
    }
    
    public void setCompletionDate(Date completionDate) {
        this.completionDate = completionDate;
    }
    
    public Date getCreationDate() {
        return creationDate;
    }
    
    public void setCreationDate(Date creationDate) {
        this.creationDate = creationDate;
    }
    
    public boolean isRecurring() {
        return isRecurring;
    }
    
    public void setRecurring(boolean recurring) {
        isRecurring = recurring;
    }
    
    public Integer getRecurrenceType() {
        return recurrenceType;
    }
    
    public void setRecurrenceType(Integer recurrenceType) {
        this.recurrenceType = recurrenceType;
    }
    
    public String getRecurrenceTypeText() {
        if (recurrenceType == null) return "";
        switch (recurrenceType) {
            case 1: return "Daily";
            case 2: return "Weekly";
            case 3: return "Monthly";
            case 4: return "Yearly";
            default: return "Unknown";
        }
    }
    
    public Integer getRecurrenceValue() {
        return recurrenceValue;
    }
    
    public void setRecurrenceValue(Integer recurrenceValue) {
        this.recurrenceValue = recurrenceValue;
    }
    
    public String getRecurrenceText() {
        if (!isRecurring || recurrenceType == null || recurrenceValue == null) return "Not recurring";
        return String.format("Every %d %s", recurrenceValue, getRecurrenceTypeText());
    }
    
    public Integer getEstimatedMinutes() {
        return estimatedMinutes;
    }
    
    public void setEstimatedMinutes(Integer estimatedMinutes) {
        this.estimatedMinutes = estimatedMinutes;
    }
    
    public String getEstimatedTimeText() {
        if (estimatedMinutes == null) return "";
        int hours = estimatedMinutes / 60;
        int minutes = estimatedMinutes % 60;
        return String.format("%d:%02d", hours, minutes);
    }
    
    public Integer getActualMinutes() {
        return actualMinutes;
    }
    
    public void setActualMinutes(Integer actualMinutes) {
        this.actualMinutes = actualMinutes;
    }
    
    public String getActualTimeText() {
        if (actualMinutes == null) return "";
        int hours = actualMinutes / 60;
        int minutes = actualMinutes % 60;
        return String.format("%d:%02d", hours, minutes);
    }
    
    public int getProgress() {
        return progress;
    }
    
    public void setProgress(int progress) {
        this.progress = Math.min(100, Math.max(0, progress));
        if (progress == 100 && status != 2) {
            status = 2;
            completionDate = new Date();
        }
    }
    
    public Integer getParentTaskId() {
        return parentTaskId;
    }
    
    public void setParentTaskId(Integer parentTaskId) {
        this.parentTaskId = parentTaskId;
    }
    
    public List<Task> getSubtasks() {
        return subtasks;
    }
    
    public void setSubtasks(List<Task> subtasks) {
        this.subtasks = subtasks;
    }
    
    public void addSubtask(Task subtask) {
        if (subtasks == null) subtasks = new ArrayList<>();
        subtasks.add(subtask);
    }
    
    public List<String> getTags() {
        return tags;
    }
    
    public void setTags(List<String> tags) {
        this.tags = tags;
    }
    
    public void addTag(String tag) {
        if (tags == null) tags = new ArrayList<>();
        tags.add(tag);
    }
    
    public String getTagsText() {
        if (tags == null || tags.isEmpty()) return "";
        return String.join(", ", tags);
    }
    
    public List<Date> getReminders() {
        return reminders;
    }
    
    public void setReminders(List<Date> reminders) {
        this.reminders = reminders;
    }
    
    public void addReminder(Date reminder) {
        if (reminders == null) reminders = new ArrayList<>();
        reminders.add(reminder);
    }
    
    @Override
    public String toString() {
        return title;
    }
} 