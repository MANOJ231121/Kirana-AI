# Run the Kirana Store voice assistant.
# Loads .env, then starts the Spring Boot app.

. .\load-env.ps1

Write-Host "================================================" -ForegroundColor Cyan
Write-Host " Starting Kirana Store AI Assistant Server... " -ForegroundColor Green
Write-Host " Access Web App at: http://localhost:8080      " -ForegroundColor Yellow
Write-Host "================================================" -ForegroundColor Cyan

mvn spring-boot:run
