Write-Host "Starting Mini-CDN Smoke Test..."

# Edge Endpoint (NICHT Origin!)
$url = "http://localhost:8081/api/edge/files/smoke-test.txt"

# Mindestgröße: 100 kB
$minSize = 100000

# Maximale Zeit: 2 Sekunden (in ms)
$maxTimeMs = 2000

try {
    $start = Get-Date

    $response = Invoke-WebRequest $url -UseBasicParsing

    $end = Get-Date

    $timeMs = ($end - $start).TotalMilliseconds
    $size = $response.RawContentLength

    Write-Host "Downloaded bytes: $size"
    Write-Host "Time: $timeMs ms"

    $sizeOk = $size -ge $minSize
    $timeOk = $timeMs -lt $maxTimeMs

    if ($sizeOk -and $timeOk) {
        Write-Host "SMOKE TEST PASSED"
        exit 0
    }
    else {
        Write-Host "SMOKE TEST FAILED"

        if (-not $sizeOk) {
            Write-Host "Reason: File too small (expected >= $minSize bytes)"
        }

        if (-not $timeOk) {
            Write-Host "Reason: Too slow (limit $maxTimeMs ms)"
        }

        exit 1
    }
}
catch {
    Write-Host "SMOKE TEST FAILED"
    Write-Host $_
    exit 1
}
