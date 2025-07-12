# Personal Data Manager

A Java-based personal data management application that helps you organize your passwords, tasks, contacts, and provides backup/restore functionality.

## Features

- Secure login system with password hashing
- Dashboard overview
- Password management with encryption
- Task management
- Contact management
- Backup and restore functionality
- User-friendly interface

## Requirements

- Java JDK 11 or higher
- Gradle 7.0 or higher

## How to Build and Run

1. Clone the repository
2. Navigate to the project directory
3. Build the project:
   ```bash
   gradle build
   ```
4. Run the application:
   ```bash
   gradle run
   ```

Alternatively, you can run the JAR file directly:
```bash
java -jar build/libs/personal-data-manager-1.0-SNAPSHOT.jar
```

## Security Features

- Password hashing using PBKDF2 with SHA-256
- Salted password storage
- Encrypted password storage for saved credentials
- Secure backup/restore functionality

## Database

The application uses SQLite for data storage. The database file `personal_data.db` will be created automatically in the application directory.

## Backup and Restore

- Backups are stored in the `backups` directory
- Backup files are ZIP archives containing CSV exports of your data
- Each backup is timestamped for easy identification
- Restore functionality allows you to recover your data from any backup

## Security Note

This is a basic implementation. For production use, please implement additional security measures:
- Use a more secure encryption method for stored passwords
- Implement proper session management
- Add additional data validation
- Implement proper error handling
- Add logging for security events

## Default Login

For first-time use, create a new account using the Register button on the login screen.

## Features Description

### Password Manager
- Store website credentials securely
- View and manage saved passwords
- Add new password entries

### Task Manager
- Create and manage your tasks
- Mark tasks as complete
- Simple and intuitive interface

### Contacts
- Store contact information
- Manage your contact list
- Easy to add and edit contacts

### Backup/Restore
- Create backups of your data
- Restore from previous backups
- Keep your data safe

## Security Note

This is a basic implementation. For production use, please implement proper security measures:
- Encrypt stored passwords
- Use secure authentication
- Implement proper data validation
- Add error handling 