package com.datamanager;

import java.awt.EventQueue;
import javax.swing.UIManager;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.TimeUnit;
import com.datamanager.util.DatabaseUtil;
import com.datamanager.util.PasswordHealthUtil;
import com.datamanager.util.BrowserExtensionUtil;

public class Main {
    private static Timer reportScheduler;
    
    public static void main(String[] args) {
        try {
            // Set system look and feel
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            
            // Initialize database
            DatabaseUtil.getConnection();
            
            // Start the browser extension server
            BrowserExtensionUtil.startExtensionServer();
            
            // Schedule password health reports (check daily at 1 AM)
            schedulePasswordHealthReports();
            
            // Start the application
            EventQueue.invokeLater(() -> {
                try {
                    new LoginFrame();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            });
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    private static void schedulePasswordHealthReports() {
        reportScheduler = new Timer("PasswordHealthReportScheduler", true);
        
        // Set to run at 1 AM every day
        java.util.Calendar firstRun = java.util.Calendar.getInstance();
        firstRun.set(java.util.Calendar.HOUR_OF_DAY, 1);
        firstRun.set(java.util.Calendar.MINUTE, 0);
        firstRun.set(java.util.Calendar.SECOND, 0);
        
        // If it's already past 1 AM, schedule for tomorrow
        if (firstRun.getTimeInMillis() < System.currentTimeMillis()) {
            firstRun.add(java.util.Calendar.DAY_OF_MONTH, 1);
        }
        
        // Schedule the task
        reportScheduler.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                try {
                    PasswordHealthUtil.scheduleHealthReports();
                } catch (Exception e) {
                    System.err.println("Error scheduling health reports: " + e.getMessage());
                    e.printStackTrace();
                }
            }
        }, firstRun.getTime(), TimeUnit.MILLISECONDS.convert(1, TimeUnit.DAYS));
        
        System.out.println("Password health reports scheduled to run at 1 AM daily");
    }
    
    public static void shutdown() {
        // Clean shutdown
        if (reportScheduler != null) {
            reportScheduler.cancel();
        }
        
        BrowserExtensionUtil.stopExtensionServer();
        DatabaseUtil.closeConnection();
    }
} 