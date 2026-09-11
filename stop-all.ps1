# Stop the Kirana Store backend + frontend processes started by start-all.ps1
Write-Host "Stopping Kirana Store processes..." -ForegroundColor Cyan

Get-CimInstance Win32_Process -Filter "Name = 'java.exe'" | ForEach-Object {
    if ($_.CommandLine -match 'kirana|spring-boot') {
        Write-Host "  Stopping backend PID $($_.ProcessId)" -ForegroundColor Yellow
        Stop-Process -Id $_.ProcessId -Force -ErrorAction SilentlyContinue
    }
}

Get-CimInstance Win32_Process -Filter "Name = 'node.exe'" | ForEach-Object {
    if ($_.CommandLine -match 'vite') {
        Write-Host "  Stopping frontend PID $($_.ProcessId)" -ForegroundColor Yellow
        Stop-Process -Id $_.ProcessId -Force -ErrorAction SilentlyContinue
    }
}

Write-Host "Done." -ForegroundColor Green