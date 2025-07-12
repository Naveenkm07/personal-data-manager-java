# Download SQLite JDBC driver
$url = "https://github.com/xerial/sqlite-jdbc/releases/download/3.42.0.0/sqlite-jdbc-3.42.0.0.jar"
$output = "sqlite-jdbc.jar"

Write-Host "Downloading SQLite JDBC driver..."
Invoke-WebRequest -Uri $url -OutFile $output
Write-Host "Download complete. File saved to $output" 