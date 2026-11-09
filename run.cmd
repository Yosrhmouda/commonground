@echo off
cd /d "%~dp0"
echo Starting CommonGround. Open http://localhost:8080 after startup.
java -jar release\commonground-1.0.0.jar
pause
