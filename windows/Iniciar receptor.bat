@echo off
rem Abre el receptor de Cuadre de Caja. No necesita instalar nada.
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0cuadre-receptor.ps1" %*
pause
