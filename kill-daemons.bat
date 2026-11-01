@echo off
echo [Aether] Stopping Aether and related daemons...

:: Kill specific Aether processes
echo [Aether] Force killing Aether daemons...
taskkill /F /IM aether.exe /T >nul 2>&1
taskkill /F /IM aether-daemon.exe /T >nul 2>&1
taskkill /F /IM aether*.exe /T >nul 2>&1

:: Kill Node.js and Python processes which might host Aether backend
echo [Aether] Force killing Node.js and Python processes...
taskkill /F /IM node.exe /T >nul 2>&1
taskkill /F /IM python.exe /T >nul 2>&1

:: Stop Gradle daemon gracefully if possible
if exist gradlew.bat (
    echo [Aether] Stopping Gradle daemons...
    call gradlew.bat --stop
)

:: Forcefully kill Java (Gradle/Kotlin daemons)
echo [Aether] Force killing Java processes...
taskkill /F /IM java.exe /T >nul 2>&1

echo [Aether] All daemons forcefully killed! Aether can now safely resume generating output.
exit /b 0
