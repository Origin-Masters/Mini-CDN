Write-Host "Checking ROUTER availability..."
try {
    $null = Invoke-WebRequest -Uri "http://localhost:8082/api/cdn/health" -UseBasicParsing -ErrorAction Stop
} catch {
    Write-Host "ERROR: [ROUTER] is not running on [8082]`nStart servers first with: .\start-servers.ps1" -ForegroundColor Red
    exit 1
}

Write-Host "[ROUTER] is up. Starting [MINI-CDN CLI]...`n" -ForegroundColor Green
Set-Location cli
mvn -q exec:java

