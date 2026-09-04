# Run the Kirana Store voice assistant.
# Loads .env, then starts the Spring Boot app.
# Requires: MongoDB running, ngrok pointing at port 8080, and PUBLIC_BASE_URL set in .env.

. .\load-env.ps1

$required = @('TWILIO_ACCOUNT_SID','TWILIO_AUTH_TOKEN','RIME_API_KEY','GROQ_API_KEY','PUBLIC_BASE_URL')
$missing = $required | Where-Object { -not (Get-Item -Path "env:$($_)" -ErrorAction SilentlyContinue) }
if ($missing) {
    Write-Host "Missing environment variables: $($missing -join ', ')" -ForegroundColor Red
    Write-Host "Fill them in .env then re-run."
    exit 1
}

Write-Host "Starting Kirana Store assistant..."
mvn spring-boot:run
