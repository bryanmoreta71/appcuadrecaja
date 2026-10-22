@echo off
rem Hace que el receptor arranque solo (oculto) cada vez que se enciende la computadora.
rem No necesita clave de administrador: usa la carpeta de Inicio del usuario.
powershell.exe -NoProfile -ExecutionPolicy Bypass -Command "$s = (New-Object -ComObject WScript.Shell).CreateShortcut([Environment]::GetFolderPath('Startup') + '\Receptor Cuadre de Caja.lnk'); $s.TargetPath = 'wscript.exe'; $s.Arguments = [char]34 + '%~dp0Iniciar receptor (oculto).vbs' + [char]34; $s.WorkingDirectory = '%~dp0'; $s.Save()"
if errorlevel 1 (
  echo No se pudo activar el inicio automatico.
) else (
  echo Listo: el receptor arrancara solo, sin ventana, cada vez que encienda la computadora.
  echo Ahora tambien se inicia oculto.
  wscript.exe "%~dp0Iniciar receptor (oculto).vbs"
)
pause
