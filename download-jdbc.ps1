# Download SQLite JDBC driver (with retry logic)
$url = "https://github.com/xerial/sqlite-jdbc/releases/download/3.42.0.0/sqlite-jdbc-3.42.0.0.jar"
$output = "lib/sqlite-jdbc.jar"

Write-Host "Downloading SQLite JDBC driver..."
$tries = 0
$maxTries = 3
$success = $false

while (-not $success -and $tries -lt $maxTries) {
    $tries++
    try {
        Invoke-WebRequest -Uri $url -OutFile $output -UseBasicParsing
        if (Test-Path $output) {
            $fileSize = (Get-Item $output).Length
            if ($fileSize -gt 1000000) {  # Should be at least 1MB
                $success = $true
                Write-Host "Download complete. File saved to $output"
            } else {
                Write-Host "Downloaded file too small, retrying..."
                Remove-Item $output -Force
            }
        }
    } catch {
        Write-Host "Error downloading, retrying... $_"
    }
    
    if (-not $success) {
        Write-Host "Attempt $tries failed, waiting before retry..."
        Start-Sleep -Seconds 2
    }
}

if (-not $success) {
    Write-Host "Failed to download after $maxTries attempts"
    exit 1
}

# Download JSON-Simple library
$url = "https://repo1.maven.org/maven2/com/googlecode/json-simple/json-simple/1.1.1/json-simple-1.1.1.jar"
$output = "lib/json-simple.jar"

Write-Host "Downloading JSON-Simple library..."
Invoke-WebRequest -Uri $url -OutFile $output -UseBasicParsing
Write-Host "Download complete. File saved to $output" 