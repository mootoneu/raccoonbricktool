@echo off
title RaccoonBrickTool - server
setlocal

cd /d "%~dp0"

echo Updating Raccoon BrickTool...
call mvn package -f "d:\Projects\lego\bricktools\pom.xml"
echo.

endlocal & exit /b 0
