Write-Host "Starting [MINI-CDN] servers...`n" -ForegroundColor Cyan

# Start ORIGIN
Set-Location origin
Write-Host "Starting ORIGIN..."
Start-Process -NoNewWindow -FilePath "mvn" -ArgumentList "spring-boot:run -Dspring-boot.run.arguments=""--spring.profiles.active=origin""" -RedirectStandardOutput "..\origin.log" -RedirectStandardError "..\origin.log"
Set-Location ..

# Start EDGE
Set-Location edge
Write-Host "Starting EDGE..."
Start-Process -NoNewWindow -FilePath "mvn" -ArgumentList "spring-boot:run -Dspring-boot.run.arguments=""--spring.profiles.active=edge""" -RedirectStandardOutput "..\edge.log" -RedirectStandardError "..\edge.log"
Set-Location ..

# Start ROUTER
Set-Location router
Write-Host "Starting ROUTER...`n"
Start-Process -NoNewWindow -FilePath "mvn" -ArgumentList "spring-boot:run -Dspring-boot.run.arguments=""--spring.profiles.active=cdn""" -RedirectStandardOutput "..\router.log" -RedirectStandardError "..\router.log"
Set-Location ..

Write-Host "Waiting for servers to start (this takes ~60s the first time)...`n"
$maxWait = 120
$elapsed = 0

while ($true) {
    # Check ob die Ports hören (PID ermitteln)
    $originPid = (Get-NetTCPConnection -LocalPort 8080 -ErrorAction SilentlyContinue).OwningProcess
    $edgePid = (Get-NetTCPConnection -LocalPort 8081 -ErrorAction SilentlyContinue).OwningProcess
    $routerPid = (Get-NetTCPConnection -LocalPort 8082 -ErrorAction SilentlyContinue).OwningProcess

    $originStatus = if ($originPid) { "✅" } else { "⏳" }
    $edgeStatus = if ($edgePid) { "✅" } else { "⏳" }
    $routerStatus = if ($routerPid) { "✅" } else { "⏳" }

    Write-Host "`r  [$($elapsed)s]  ORIGIN:$originStatus  EDGE:$edgeStatus  ROUTER:$routerStatus" -NoNewline

    if ($originPid -and $edgePid -and $routerPid) {
        Write-Host "`n"
        break
    }

    if ($elapsed -ge $maxWait) {
        Write-Host "`n`nERROR: Timeout after ${maxWait}s. Check *.log files for errors." -ForegroundColor Red
        exit 1
    }

    Start-Sleep -Seconds 5
    $elapsed += 5
}

# Warten bis Router Health OK meldet
while ($true) {
    try {
        $response = Invoke-WebRequest -Uri "http://localhost:8082/api/cdn/health" -UseBasicParsing -ErrorAction Stop
        break
    } catch {
        Start-Sleep -Seconds 2
    }
}

Write-Host "✅ All servers running!`n" -ForegroundColor Green
Write-Host "[ORIGIN]: 8080 (PID: $($originPid[0]))"
Write-Host "[EDGE]:   8081 (PID: $($edgePid[0]))"
Write-Host "[ROUTER]: 8082 (PID: $($routerPid[0]))`n"

# Edge beim Router registrieren
Write-Host "Registering [EDGE] at [ROUTER]..."
try {
    $null = Invoke-WebRequest -Method Post -Uri "http://localhost:8082/api/cdn/routing?region=EU&url=http://localhost:8081" -Headers @{"X-Admin-Token"="secret-token"} -UseBasicParsing -ErrorAction Stop
    Write-Host "[EDGE] registered for region [EU]" -ForegroundColor Green
} catch {
    Write-Host "Failed to register EDGE: $_" -ForegroundColor Red
}

Write-Host "`n➡️  Start the CLI in another terminal: .\start-cli.ps1" -ForegroundColor Cyan

