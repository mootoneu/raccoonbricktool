@echo off
title RaccoonBrickTool - server
setlocal

cd /d "%~dp0"

echo Updating Raccoon BrickTool...
call mvn package -f "d:\Projects\lego\bricktools\pom.xml"
echo.

echo Press any key to close this window.
pause >nul

endlocal & exit /b 0
