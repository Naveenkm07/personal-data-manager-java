# 🔐 Personal Data Manager - Java

[![Java](https://img.shields.io/badge/Java-11+-orange.svg)](https://openjdk.java.net/)
[![Gradle](https://img.shields.io/badge/Gradle-7.0+-green.svg)](https://gradle.org/)
[![SQLite](https://img.shields.io/badge/SQLite-3.42+-blue.svg)](https://www.sqlite.org/)
[![License](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)
[![Build Status](https://img.shields.io/badge/Build-Passing-brightgreen.svg)]()

> A secure, feature-rich Java application for managing personal data including passwords, tasks, contacts, and more with encryption and backup capabilities.

## ✨ Features

### 🔒 Security Features
- **Password Hashing**: PBKDF2 with SHA-256 and salted storage
- **Encrypted Storage**: Secure encryption for saved credentials
- **Session Management**: Secure user authentication system
- **Data Validation**: Comprehensive input validation and sanitization

### 📊 Core Functionality
- **Password Manager**: Store and manage website credentials securely
- **Task Manager**: Create, organize, and track your daily tasks
- **Contact Manager**: Store and manage contact information
- **Dashboard**: Overview of all your data in one place

### 🔄 Backup & Restore
- **Automated Backups**: Timestamped ZIP archives with CSV exports
- **Easy Restoration**: One-click restore from any backup point
- **Data Portability**: Export data in multiple formats

### 🌐 Browser Integration
- **Browser Extension Server**: Runs on port 45678 for extension integration
- **Cross-Platform**: Works on Windows, macOS, and Linux

### 📈 Monitoring
- **Password Health Reports**: Daily automated reports at 1 AM
- **Security Alerts**: Notifications for potential security issues

## 🚀 Quick Start

### Prerequisites
- **Java JDK 11** or higher
- **Gradle 7.0** or higher
- **Internet connection** (for initial dependency download)

### Installation

1. **Clone the repository**
   ```bash
   git clone https://github.com/Naveenkm07/personal-data-manager-java.git
   cd personal-data-manager-java
   ```

2. **Run the application**
   ```bash
   # Option 1: Using the provided batch file (Windows)
   .\run.bat
   
   # Option 2: Using Gradle
   gradle run
   
   # Option 3: Build and run JAR
   gradle build
   java -jar build/libs/personal-data-manager-1.0-SNAPSHOT.jar
   ```

### First Time Setup
1. Launch the application
2. Click "Register" to create your first account
3. Set up your master password (this will be used to encrypt your data)
4. Start adding your passwords, tasks, and contacts!

## 🛠️ Technology Stack

- **Backend**: Java 11+
- **Database**: SQLite 3.42+
- **Build Tool**: Gradle 7.0+
- **Dependencies**:
  - SQLite JDBC Driver 3.36.0.3
  - JSON Simple 1.1.1
  - JavaMail API 1.6.2
  - Activation Framework 1.1.1

## 📁 Project Structure

```
personal-data-manager-java/
├── src/
│   └── main/java/com/datamanager/
│       ├── dao/          # Data Access Objects
│       ├── model/        # Data Models
│       ├── util/         # Utility Classes
│       └── Main.java     # Application Entry Point
├── lib/                  # External Dependencies
├── backups/              # Backup Files
├── build/                # Compiled Classes
├── build.gradle          # Gradle Configuration
├── run.bat              # Windows Run Script
└── README.md            # This File
```

## 🔧 Configuration

### Database
- **Location**: `personal_data.db` (created automatically)
- **Type**: SQLite (file-based, no server required)
- **Auto-backup**: Enabled by default

### Browser Extension
- **Port**: 45678
- **Protocol**: HTTP
- **Auto-start**: Enabled with application

### Password Health Reports
- **Schedule**: Daily at 1:00 AM
- **Location**: Application logs
- **Features**: Weak password detection, duplicate analysis

## 📖 Usage Guide

### Password Management
1. **Add New Password**:
   - Click "Add Password" in the Password Manager
   - Enter website URL, username, and password
   - Save securely with encryption

2. **View Passwords**:
   - Browse all saved passwords
   - Search by website or username
   - Copy passwords to clipboard

### Task Management
1. **Create Task**:
   - Add task title and description
   - Set priority and due date
   - Mark as complete when done

2. **Organize Tasks**:
   - Filter by status (pending/completed)
   - Sort by priority or due date
   - Bulk operations available

### Contact Management
1. **Add Contact**:
   - Enter name, email, phone
   - Add additional notes
   - Categorize contacts

2. **Manage Contacts**:
   - Search and filter contacts
   - Edit contact information
   - Export contact list

### Backup & Restore
1. **Create Backup**:
   - Automatic daily backups
   - Manual backup option available
   - Stored in `backups/` directory

2. **Restore Data**:
   - Select backup file
   - Preview data before restore
   - One-click restoration

## 🔒 Security Best Practices

### For Users
- Use a strong master password
- Enable two-factor authentication if available
- Regularly update your passwords
- Keep backups in a secure location
- Don't share your master password

### For Developers
- Review and update dependencies regularly
- Implement proper error handling
- Add comprehensive logging
- Consider additional encryption layers
- Regular security audits

## 🐛 Troubleshooting

### Common Issues

**Application won't start**
```bash
# Check Java installation
java -version

# Verify Gradle installation
gradle -version

# Clean and rebuild
gradle clean build
```

**Database connection errors**
- Ensure write permissions in application directory
- Check if `personal_data.db` is not locked by another process
- Verify SQLite JDBC driver is properly downloaded

**Browser extension not connecting**
- Check if port 45678 is available
- Verify firewall settings
- Restart the application

## 🤝 Contributing

We welcome contributions! Please follow these steps:

1. Fork the repository
2. Create a feature branch (`git checkout -b feature/AmazingFeature`)
3. Commit your changes (`git commit -m 'Add some AmazingFeature'`)
4. Push to the branch (`git push origin feature/AmazingFeature`)
5. Open a Pull Request

### Development Setup
```bash
# Clone and setup
git clone https://github.com/Naveenkm07/personal-data-manager-java.git
cd personal-data-manager-java

# Install dependencies
gradle build

# Run tests
gradle test

# Run application
gradle run
```

## 📄 License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

## 🙏 Acknowledgments

- [SQLite](https://www.sqlite.org/) for the database engine
- [Gradle](https://gradle.org/) for the build system
- [JSON Simple](https://github.com/fangyidong/json-simple) for JSON processing
- [JavaMail API](https://javaee.github.io/javamail/) for email functionality

## 📞 Support

- **Issues**: [GitHub Issues](https://github.com/Naveenkm07/personal-data-manager-java/issues)
- **Discussions**: [GitHub Discussions](https://github.com/Naveenkm07/personal-data-manager-java/discussions)
- **Email**: [kmnaveenkm01@gmail.com](mailto:kmnaveenkm01@gmail.com)

## 🔄 Version History

- **v1.0.0** - Initial release with core features
- **v1.0.1** - Bug fixes and performance improvements
- **v1.1.0** - Added browser extension support
- **v1.2.0** - Enhanced security features and UI improvements

---

⭐ **Star this repository if you find it helpful!**

🔗 **Repository**: [https://github.com/Naveenkm07/personal-data-manager-java](https://github.com/Naveenkm07/personal-data-manager-java) 