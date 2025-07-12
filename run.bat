@echo off
echo Compiling the Personal Data Manager application...

:: Check if Java is installed
where java >nul 2>nul
if %ERRORLEVEL% NEQ 0 (
    echo Java is not installed or not in PATH. Please install Java and try again.
    exit /b 1
)

:: Create directories for compiled classes
if not exist build\classes mkdir build\classes
if not exist lib mkdir lib

:: Download SQLite JDBC driver if it doesn't exist
if not exist lib\sqlite-jdbc-3.36.0.3.jar (
    echo Downloading SQLite JDBC driver...
    powershell -Command "(New-Object Net.WebClient).DownloadFile('https://repo1.maven.org/maven2/org/xerial/sqlite-jdbc/3.36.0.3/sqlite-jdbc-3.36.0.3.jar', 'lib\sqlite-jdbc-3.36.0.3.jar')"
    if %ERRORLEVEL% NEQ 0 (
        echo Failed to download SQLite JDBC driver. Please check your internet connection.
        exit /b 1
    )
    echo SQLite JDBC driver downloaded successfully.
)

:: Download JSON Simple library if it doesn't exist
if not exist lib\json-simple-1.1.1.jar (
    echo Downloading JSON Simple library...
    powershell -Command "(New-Object Net.WebClient).DownloadFile('https://repo1.maven.org/maven2/com/googlecode/json-simple/json-simple/1.1.1/json-simple-1.1.1.jar', 'lib\json-simple-1.1.1.jar')"
    if %ERRORLEVEL% NEQ 0 (
        echo Failed to download JSON Simple library. Please check your internet connection.
        exit /b 1
    )
    echo JSON Simple library downloaded successfully.
)

:: Download JavaMail API if it doesn't exist
if not exist lib\javax.mail-1.6.2.jar (
    echo Downloading JavaMail API...
    powershell -Command "(New-Object Net.WebClient).DownloadFile('https://repo1.maven.org/maven2/com/sun/mail/javax.mail/1.6.2/javax.mail-1.6.2.jar', 'lib\javax.mail-1.6.2.jar')"
    if %ERRORLEVEL% NEQ 0 (
        echo Failed to download JavaMail API. Please check your internet connection.
        exit /b 1
    )
    echo JavaMail API downloaded successfully.
)

:: Download Activation Framework if it doesn't exist
if not exist lib\activation-1.1.1.jar (
    echo Downloading Activation Framework...
    powershell -Command "(New-Object Net.WebClient).DownloadFile('https://repo1.maven.org/maven2/javax/activation/activation/1.1.1/activation-1.1.1.jar', 'lib\activation-1.1.1.jar')"
    if %ERRORLEVEL% NEQ 0 (
        echo Failed to download Activation Framework. Please check your internet connection.
        exit /b 1
    )
    echo Activation Framework downloaded successfully.
)

:: Set classpath for compilation
set CLASSPATH=src\main\java;lib\sqlite-jdbc-3.36.0.3.jar;lib\json-simple-1.1.1.jar;lib\javax.mail-1.6.2.jar;lib\activation-1.1.1.jar

:: Compile all Java files
echo Compiling Java files...
javac -d build\classes -cp "%CLASSPATH%" src\main\java\com\datamanager\*.java src\main\java\com\datamanager\dao\*.java src\main\java\com\datamanager\model\*.java src\main\java\com\datamanager\util\*.java

if %ERRORLEVEL% NEQ 0 (
    echo Compilation failed. Please check the errors above.
    exit /b 1
)

echo Compilation successful!
echo Running the application...

:: Run the application
java -cp "build\classes;lib\sqlite-jdbc-3.36.0.3.jar;lib\json-simple-1.1.1.jar;lib\javax.mail-1.6.2.jar;lib\activation-1.1.1.jar" com.datamanager.Main

echo Application terminated. 