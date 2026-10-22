@echo off
rem Quita el arranque automatico del receptor y lo apaga.
del "%APPDATA%\Microsoft\Windows\Start Menu\Programs\Startup\Receptor Cuadre de Caja.lnk" 2>nul
echo El receptor ya no arrancara solo al encender la computadora.
call "%~dp0Detener receptor.bat"
