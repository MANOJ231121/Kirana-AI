# Loads environment variables from .env into the current PowerShell session.
# Usage:  . .\load-env.ps1   (note the leading dot + space to "dot-source")
if (Test-Path .env) {
    Get-Content .env | ForEach-Object {
        $line = $_ -replace '#.*$', ''
        if ($line -match '^\s*(.+?)\s*=\s*(.*?)\s*$') {
            [System.Environment]::SetEnvironmentVariable($matches[1], $matches[2], 'Process')
        }
    }
    Write-Host "Loaded environment variables from .env"
} else {
    Write-Host "No .env file found. Copy .env.example to .env and fill in your keys." -ForegroundColor Yellow
}
