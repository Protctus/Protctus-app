param (
    [Parameter(Position = 0)]
    [string]$Command
)

if ($Command -eq "--stop") {
    Write-Host "[Aether] Stopping Aether and related daemons..." -ForegroundColor Yellow
    
    # Forcefully kill Aether daemons
    Write-Host "[Aether] Force killing Aether daemons..." -ForegroundColor Cyan
    Stop-Process -Name "aether*" -Force -ErrorAction SilentlyContinue
    
    # Forcefully kill Node.js and Python processes which might host Aether backend
    Write-Host "[Aether] Force killing Node.js and Python processes..." -ForegroundColor Cyan
    Stop-Process -Name "node" -Force -ErrorAction SilentlyContinue
    Stop-Process -Name "python" -Force -ErrorAction SilentlyContinue
    
    # Stop Gradle daemon gracefully
    if (Test-Path ".\gradlew.bat") {
        Write-Host "[Aether] Stopping Gradle daemons..." -ForegroundColor Cyan
        .\gradlew.bat --stop
    }
    
    # Forcefully kill Java (Gradle/Kotlin daemons)
    Write-Host "[Aether] Force killing Java processes..." -ForegroundColor Cyan
    Stop-Process -Name "java" -Force -ErrorAction SilentlyContinue
    
    Write-Host "[Aether] All daemons forcefully killed! Aether can now safely resume generating output." -ForegroundColor Green
}
else {
    Write-Host "[Aether] Unknown command." -ForegroundColor Red
    Write-Host "Usage:"
    Write-Host "  aether --stop    Forcefully kills Aether daemons to fix output generation freezing."
}
