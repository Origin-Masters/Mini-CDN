Write-Host "Starting MINI-CDN..." -ForegroundColor Cyan

$root = Get-Location

$originJar = "$root\origin\target\origin-1.0-SNAPSHOT-exec.jar"
$edgeJar   = "$root\edge\target\edge-1.0-SNAPSHOT-exec.jar"
$routerJar = "$root\router\target\router-1.0-SNAPSHOT-exec.jar"


function Start-Service {

    param (
        $name,
        $jar,
        $profile,
        $log
    )

    Write-Host "Starting $name..."

    $cmd = "java -jar `"$jar`" --spring.profiles.active=$profile > `"$log`" 2>&1"

    Start-Process cmd.exe -ArgumentList "/c $cmd" -WindowStyle Hidden
}


# START SERVICES

Start-Service "ORIGIN" $originJar "origin" "$root\origin.log"

Start-Service "EDGE" $edgeJar "edge" "$root\edge.log"

Start-Service "ROUTER" $routerJar "router" "$root\router.log"



# WAIT FOR PORTS

Write-Host "Waiting for ports..."

$success = $false

for ($i=0; $i -lt 60; $i++) {

    $o = Get-NetTCPConnection -LocalPort 8080 -ErrorAction SilentlyContinue
    $e = Get-NetTCPConnection -LocalPort 8081 -ErrorAction SilentlyContinue
    $r = Get-NetTCPConnection -LocalPort 8082 -ErrorAction SilentlyContinue

    if ($o -and $e -and $r) {

        $success = $true
        break
    }

    Start-Sleep 2
}


if (-not $success) {

    Write-Host "Startup failed. Check logs." -ForegroundColor Red
    exit
}



# WAIT FOR ROUTER HEALTH

Write-Host "Waiting for Router health..."

$routerReady = $false

for ($i=0; $i -lt 60; $i++) {

    try {

        Invoke-WebRequest `
            -Uri "http://localhost:8082/api/cdn/health" `
            -UseBasicParsing `
            -TimeoutSec 2 | Out-Null

        $routerReady = $true
        break
    }
    catch {

        Start-Sleep 2
    }
}


if (-not $routerReady) {

    Write-Host "Router not ready. Check router.log" -ForegroundColor Red
    exit
}



# REGISTER EDGE

Write-Host "Registering EDGE..."

try {

    Invoke-WebRequest `
        -Method POST `
        -Uri "http://localhost:8082/api/cdn/routing?region=EU&url=http://localhost:8081" `
        -Headers @{ "X-Admin-Token" = "secret-token" } `
        -UseBasicParsing | Out-Null

    Write-Host "EDGE registered successfully." -ForegroundColor Green
}
catch {

    Write-Host "EDGE registration failed." -ForegroundColor Red
}



# SUCCESS

Write-Host ""
Write-Host "MINI-CDN RUNNING" -ForegroundColor Green
Write-Host "Origin : http://localhost:8080"
Write-Host "Edge   : http://localhost:8081"
Write-Host "Router : http://localhost:8082"
Write-Host ""
Write-Host "Logs:"
Write-Host "origin.log"
Write-Host "edge.log"
Write-Host "router.log"