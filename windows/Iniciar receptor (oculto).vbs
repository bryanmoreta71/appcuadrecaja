' Abre el receptor de Cuadre de Caja sin ninguna ventana.
' La primera vez (sin configurar) abre la ventana normal para hacer la configuracion.
Set fso = CreateObject("Scripting.FileSystemObject")
Set shell = CreateObject("WScript.Shell")
carpeta = fso.GetParentFolderName(WScript.ScriptFullName)
script = carpeta & "\cuadre-receptor.ps1"
cmd = "powershell.exe -NoProfile -ExecutionPolicy Bypass -File """ & script & """"

If fso.FileExists(carpeta & "\cuadre-config.json") Then
    shell.Run cmd, 0, False
Else
    MsgBox "Primero hay que configurar el receptor. Se abrira la ventana de configuracion.", 64, "Cuadre de Caja"
    shell.Run "cmd /c """ & carpeta & "\Iniciar receptor.bat""", 1, False
End If
