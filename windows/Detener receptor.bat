@echo off
rem Apaga el receptor de Cuadre de Caja (tambien el que funciona oculto).
powershell.exe -NoProfile -ExecutionPolicy Bypass -Command "$p = Get-CimInstance Win32_Process | Where-Object { $_.ProcessId -ne $PID -and $_.CommandLine -like '*-File*cuadre-receptor.ps1*' }; if ($p) { $p | ForEach-Object { Stop-Process -Id $_.ProcessId -Force }; Write-Host 'Receptor detenido.' } else { Write-Host 'El receptor no estaba funcionando.' }"
timeout /t 3 >nul
