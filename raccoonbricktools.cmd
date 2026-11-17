@echo off
title Raccoon BrickTool - server
setlocal

cd /d "%~dp0"

echo Starting Raccoon BrickTool...
echo Open http://localhost:8780 once the server is ready.
echo Press Ctrl+C to stop the server.
echo.

call java -jar .\target\bricktools-1.0.0.jar
set "EXIT_CODE=%ERRORLEVEL%"

echo.
if "%EXIT_CODE%"=="0" (
    echo The server has stopped.
) else (
    echo The server stopped with exit code %EXIT_CODE%.
	netstat -ano | findstr :8780
	echo You can kill the server with taskkill.exe /PID 12345 /F
)
echo Press any key to close this window.
pause >nul

endlocal & exit /b %EXIT_CODE%
