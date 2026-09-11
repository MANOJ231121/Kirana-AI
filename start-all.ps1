# Start the full Kirana Store stack: backend (Spring Boot :8080) + frontend (Vite :5173)
# Requires MongoDB running (docker compose up -d).
#
# Usage:  .\start-all.ps1   (run as admin not required)
# Stop everything later with:  .\stop-all.ps1

$ErrorActionPreference = 'Continue'

Write-Host "================================================" -ForegroundColor Cyan
Write-Host " Kirana Store - Starting Backend + Frontend    " -ForegroundColor Green
Write-Host "================================================" -ForegroundColor Cyan

# 1. Check MongoDB (quick, non-fatal)
if (Test-NetConnection -ComputerName localhost -Port 27017 -InformationLevel Quiet -WarningAction SilentlyContinue) {
    Write-Host "[OK] MongoDB is running on :27017" -ForegroundColor Green
} else {
    Write-Host "[WARN] MongoDB is NOT running. Start it with: docker compose up -d" -ForegroundColor Yellow
    Write-Host "  -> Trying to start via Docker..." -ForegroundColor Yellow
    try {
        docker compose up -d 2>&1 | Out-Host
        Start-Sleep -Seconds 6
    } catch {
        Write-Host "[WARN] Could not start MongoDB via docker: $_" -ForegroundColor Yellow
    }
}

# 2. Start backend (Spring Boot) in a new window
Write-Host "`n[1/2] Starting Spring Boot backend on :8080 ..." -ForegroundColor Cyan
# Force IPv4 networking for the JVM - avoids DNS/A-query hangs on this PC.
$env:JAVA_TOOL_OPTIONS = '-Djava.net.preferIPv4Stack=true -Djava.net.preferIPv4Addresses=true'
$backendLog = Join-Path $PSScriptRoot "backend.log"
Start-Process -FilePath "cmd.exe" -ArgumentList "/c", "cd /d `"$PSScriptRoot`" && mvn spring-boot:run > `"$backendLog`" 2>&1" -WindowStyle Minimized
Write-Host "Backend launching in background (log: $backendLog) ..."

# 3. Start frontend (Vite) in a new window
Write-Host "[2/2] Starting Vite frontend on :5173 ..." -ForegroundColor Cyan
$frontendDir = Join-Path $PSScriptRoot "frontend"
$frontendLog = Join-Path $PSScriptRoot "frontend.log"
Start-Process -FilePath "cmd.exe" -ArgumentList "/c", "cd /d `"$frontendDir`" && npm run dev > `"$frontendLog`" 2>&1" -WindowStyle Minimized
Write-Host "Frontend launching in background (log: $frontendLog) ..."

Write-Host "`n================================================" -ForegroundColor Cyan
Write-Host " Waiting for services ..." -ForegroundColor Yellow
Start-Sleep -Seconds 40

# 4. Verify
$be = Test-NetConnection -ComputerName localhost -Port 8080 -InformationLevel Quiet -WarningAction SilentlyContinue
$fe = Test-NetConnection -ComputerName localhost -Port 5173 -InformationLevel Quiet -WarningAction SilentlyContinue

Write-Host "`n------------------------------------------------" -ForegroundColor Cyan
if ($be) {
    Write-Host "[OK] Backend  : http://localhost:8080  (health: /health)" -ForegroundColor Green
} else {
    Write-Host "[FAIL] Backend : start it manually - open backend.log" -ForegroundColor Red
    if (Test-Path (Join-Path $PSScriptRoot "backend.log")) {
        Get-Content (Join-Path $PSScriptRoot "backend.log") -Tail 20
    }
}
if ($fe) {
    Write-Host "[OK] Frontend : http://localhost:5173" -ForegroundColor Green
    Write-Host "      Customer   : http://localhost:5173/customer" -ForegroundColor Cyan
    Write-Host "      Shopkeeper : http://localhost:5173/shopkeeper" -ForegroundColor Cyan
} else {
    Write-Host "[FAIL] Frontend : start it manually - open frontend.log" -ForegroundColor Red
    if (Test-Path (Join-Path $PSScriptRoot "frontend.log")) {
        Get-Content (Join-Path $PSScriptRoot "frontend.log") -Tail 20
    }
}
Write-Host "------------------------------------------------" -ForegroundColor Cyan