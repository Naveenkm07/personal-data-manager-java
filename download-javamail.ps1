# Download JavaMail API
$url = "https://repo1.maven.org/maven2/com/sun/mail/javax.mail/1.6.2/javax.mail-1.6.2.jar"
$output = "lib/javax.mail.jar"

Write-Host "Downloading JavaMail API..."
try {
    Invoke-WebRequest -Uri $url -OutFile $output -UseBasicParsing
    Write-Host "Download complete. File saved to $output"
} catch {
    Write-Host "Error downloading JavaMail API: $_"
    exit 1
}

# Download JavaBeans Activation Framework (needed by JavaMail)
$url = "https://repo1.maven.org/maven2/javax/activation/activation/1.1.1/activation-1.1.1.jar"
$output = "lib/activation.jar"

Write-Host "Downloading JavaBeans Activation Framework..."
try {
    Invoke-WebRequest -Uri $url -OutFile $output -UseBasicParsing
    Write-Host "Download complete. File saved to $output"
} catch {
    Write-Host "Error downloading JavaBeans Activation Framework: $_"
    exit 1
} 