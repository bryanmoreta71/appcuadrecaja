# Receptor de Cuadre de Caja para Windows.
#
# Recibe el ticket que manda la app del celular por Bluetooth (puerto COM entrante)
# y lo imprime directo en la impresora térmica (SAT u otra), sin ventana de imprimir.
#
# Uso: doble clic en "Iniciar receptor.bat". La primera vez pregunta el puerto COM
# y la impresora, y lo guarda en cuadre-config.json. Para volver a configurar:
# "Iniciar receptor.bat" -Reconfigurar  (o borrar cuadre-config.json).
# Para que funcione sin ventana: "Iniciar receptor (oculto).vbs". Para apagarlo: "Detener receptor.bat".

param(
    [switch]$Reconfigurar
)

$ErrorActionPreference = 'Stop'
$ConfigPath = Join-Path $PSScriptRoot 'cuadre-config.json'
$LogPath = Join-Path $PSScriptRoot 'cuadre-receptor.log'

# Las versiones nuevas de la app mandan cada ticket "empaquetado": con su tamaño y un código
# para comprobar que llegó entero (ver CuadreImpresora.TamanoTrama). Las versiones viejas
# no: en ese caso, si pasan estos milisegundos sin recibir datos, el ticket se da por completo.
$FinDeTicketMs = 2500
# Si un ticket empaquetado deja de llegar a medias por este tiempo, se descarta (no se imprime basura).
$TicketIncompletoMs = 20000

# Configuración, registro e impresión (compartido con el programa "Nota de entrega").
. (Join-Path $PSScriptRoot 'cuadre-comun.ps1')
Import-Codigo -ConNota



function Read-Opcion($pregunta, $max, $porDefecto) {
    while ($true) {
        $r = Read-Host "$pregunta [$porDefecto]"
        if ([string]::IsNullOrWhiteSpace($r)) { return $porDefecto }
        $n = 0
        if ([int]::TryParse($r, [ref]$n) -and $n -ge 1 -and $n -le $max) { return $n }
        Write-Host "  Escriba un número del 1 al $max." -ForegroundColor Yellow
    }
}

function Select-Puerto {
    $nombres = @{}
    Get-CimInstance Win32_PnPEntity -Filter "Name LIKE '%(COM%'" -ErrorAction SilentlyContinue | ForEach-Object {
        if ($_.Name -match '\((COM\d+)\)') { $nombres[$Matches[1]] = $_.Name }
    }
    $puertos = @([System.IO.Ports.SerialPort]::GetPortNames() | Sort-Object { [int]($_ -replace '\D', '') } -Unique)
    if ($puertos.Count -eq 0) {
        Write-Host ''
        Write-Host 'No hay puertos COM. Cree el puerto COM entrante de Bluetooth (vea LEAME.txt) y vuelva a abrir el receptor.' -ForegroundColor Red
        Read-Host 'Presione Enter para salir'
        exit 1
    }
    Write-Host ''
    Write-Host 'Puertos COM disponibles (elija el ENTRANTE de Bluetooth):' -ForegroundColor Cyan
    $sugerido = 1
    for ($i = 0; $i -lt $puertos.Count; $i++) {
        $desc = $nombres[$puertos[$i]]
        if (-not $desc) { $desc = $puertos[$i] }
        Write-Host ("  {0}) {1}" -f ($i + 1), $desc)
        if ($desc -match 'Bluetooth') { $sugerido = $i + 1 }
    }
    return $puertos[(Read-Opcion 'Número del puerto' $puertos.Count $sugerido) - 1]
}

function Select-Impresora {
    $impresoras = @(Get-CimInstance Win32_Printer | Sort-Object Name)
    Write-Host ''
    Write-Host 'Impresoras instaladas:' -ForegroundColor Cyan
    $sugerido = 1
    for ($i = 0; $i -lt $impresoras.Count; $i++) {
        $p = $impresoras[$i]
        Write-Host ("  {0}) {1}{2}" -f ($i + 1), $p.Name, $(if ($p.Default) { '  (predeterminada)' } else { '' }))
        if ($p.Default) { $sugerido = $i + 1 }
    }
    for ($i = 0; $i -lt $impresoras.Count; $i++) {
        if ($impresoras[$i].Name -match 'SAT') { $sugerido = $i + 1 }
    }
    return $impresoras[(Read-Opcion 'Número de la impresora' $impresoras.Count $sugerido) - 1].Name
}

function Get-TicketPrueba {
    $esc = [byte]0x1b; $gs = [byte]0x1d
    $b = New-Object System.Collections.Generic.List[byte]
    $b.AddRange([byte[]]($esc, 0x40, $esc, 0x61, 1, $esc, 0x45, 1, $gs, 0x21, 0x11))
    $b.AddRange([Text.Encoding]::ASCII.GetBytes("PRUEBA`n"))
    $b.AddRange([byte[]]($gs, 0x21, 0, $esc, 0x45, 0))
    $b.AddRange([Text.Encoding]::ASCII.GetBytes("Receptor de Cuadre de Caja`n$((Get-Date).ToString('dd/MM/yyyy hh:mm tt'))`n`n`n`n"))
    return $b.ToArray()
}




function New-Config {
    Write-Host ''
    Write-Host '=== Configuración del receptor ===' -ForegroundColor Cyan
    $config = [ordered]@{
        puerto    = Select-Puerto
        impresora = Select-Impresora
        papelMm     = 80
        modo        = 'raw'
        lineasFinal = 5
    }
    Write-Host ''
    Write-Host 'Ancho del papel:  1) 80 mm   2) 58 mm' -ForegroundColor Cyan
    if ((Read-Opcion 'Número' 2 1) -eq 2) { $config.papelMm = 58 }

    Write-Host ''
    Write-Info 'Imprimiendo ticket de prueba...'
    try { Send-Ticket $config (Get-TicketPrueba) } catch { Write-Info $_.Exception.Message 'Red' }
    $ok = Read-Host '¿Salió el ticket de prueba bien impreso? (S/N) [S]'
    if ($ok -match '^[nN]') {
        $config.modo = 'texto'
        Write-Info 'Probando con el controlador de Windows (modo texto)...'
        try { Send-Ticket $config (Get-TicketPrueba) } catch { Write-Info $_.Exception.Message 'Red' }
        Write-Host 'Si tampoco salió, revise que la impresora imprima una página de prueba desde Windows.' -ForegroundColor Yellow
    }

    $config | ConvertTo-Json | Set-Content -Path $ConfigPath -Encoding UTF8
    Write-Info "Configuración guardada en $ConfigPath" 'Green'
    return [pscustomobject]$config
}

# ---------- Inicio ----------

$Host.UI.RawUI.WindowTitle = 'Receptor - Cuadre de Caja'

# Solo un receptor a la vez: dos no pueden usar el mismo puerto COM.
$creado = $false
$unico = New-Object System.Threading.Mutex($true, 'Local\CuadreCajaReceptor', [ref]$creado)
if (-not $creado) {
    Write-Host 'El receptor ya está funcionando (quizás oculto). Use "Detener receptor.bat" para apagarlo.' -ForegroundColor Yellow
    Start-Sleep -Seconds 5
    exit
}
if ($Reconfigurar -or -not (Test-Path $ConfigPath)) {
    $config = New-Config
} else {
    $config = Get-Content $ConfigPath -Raw | ConvertFrom-Json
}

Write-Host ''
Write-Host '========================================' -ForegroundColor Green
Write-Host '  RECEPTOR DE CUADRE DE CAJA' -ForegroundColor Green
Write-Host "  Puerto:     $($config.puerto)"
Write-Host "  Impresora:  $($config.impresora)"
Write-Host '  Deje esta ventana abierta (puede minimizarla).'
Write-Host '========================================' -ForegroundColor Green

function Send-Respuesta($puerto, $texto) {
    # La app espera esta respuesta para decir "Impreso" o pedir que se reintente.
    try { $puerto.Write("$texto`n") } catch { }
}

# Guarda la plantilla que mandó el celular en la carpeta "plantillas" (una por archivo).
function Save-Plantilla([byte[]]$datos) {
    $texto = [Text.Encoding]::UTF8.GetString($datos)
    # Se comprueba que esté bien antes de guardarla (la anterior queda si llegó dañada).
    try { $p = [PaqueteNota]::Leer($texto) } catch { throw 'la plantilla llegó incompleta o dañada' }
    New-Item -ItemType Directory -Path $CarpetaPlantillas -Force | Out-Null
    $nombre = ($p.Id -replace '[^\w-]', '_') + '.json'
    $ruta = Join-Path $CarpetaPlantillas $nombre
    $tmp = "$ruta.tmp"
    [IO.File]::WriteAllText($tmp, $texto, (New-Object Text.UTF8Encoding($false)))
    Move-Item -Path $tmp -Destination $ruta -Force
    Write-Info "Plantilla '$($p.Nombre)' actualizada ($($p.Llenables.Count) cosas para llenar)." 'Green'
}

function Invoke-Ticket($config, [byte[]]$datos) {
    Write-Info "Ticket recibido ($($datos.Length) bytes). Imprimiendo..."
    Send-Ticket $config $datos
    Write-Info 'Ticket impreso.' 'Green'
}

# Lee el puerto sin parar y manda a imprimir cada ticket que llega completo.
function Watch-Puerto($puerto, $config) {
    $buffer = New-Object System.IO.MemoryStream
    $ultimo = Get-Date
    $leer = New-Object byte[] 65536

    while ($true) {
        $disponibles = $puerto.BytesToRead
        if ($disponibles -gt 0) {
            $n = $puerto.Read($leer, 0, [Math]::Min($disponibles, $leer.Length))
            $buffer.Write($leer, 0, $n)
            $ultimo = Get-Date
            continue
        }
        if ($buffer.Length -eq 0) { Start-Sleep -Milliseconds 20; continue }

        $arr = $buffer.ToArray()
        $total = [CuadreImpresora]::TamanoTrama($arr, $arr.Length)
        $quieto = ((Get-Date) - $ultimo).TotalMilliseconds

        if ($total -gt 0) {
            # Ticket empaquetado completo: comprobar que llegó entero antes de imprimir.
            $resto = $arr.Length - $total
            $buffer.SetLength(0)
            if ($resto -gt 0) { $buffer.Write($arr, $total, $resto) }
            $integro = $false
            $datos = [CuadreImpresora]::AbrirTrama($arr, [ref]$integro)
            if (-not $integro) {
                Write-Info "El ticket llegó dañado ($($datos.Length) bytes); no se imprime. Se pidió reintentar." 'Red'
                Send-Respuesta $puerto 'ERROR llego danado'
                continue
            }
            if ([PaqueteNota]::EsPaquete($datos)) {
                # Una plantilla para el programa "Nota de entrega": se guarda, no se imprime.
                try {
                    Save-Plantilla $datos
                    Send-Respuesta $puerto 'OK'
                } catch {
                    Write-Info "No se pudo guardar la plantilla: $($_.Exception.Message)" 'Red'
                    Send-Respuesta $puerto "ERROR $($_.Exception.Message)"
                }
                continue
            }
            try {
                Invoke-Ticket $config $datos
                Send-Respuesta $puerto 'OK'
            } catch {
                Write-Info "No se pudo imprimir: $($_.Exception.Message)" 'Red'
                Send-Respuesta $puerto "ERROR $($_.Exception.Message)"
            }
        } elseif ($total -eq 0) {
            # Empaquetado pero incompleto: esperar el resto, sin imprimir pedazos.
            if ($quieto -ge $TicketIncompletoMs) {
                Write-Info "El ticket llegó incompleto ($($arr.Length) bytes); se descarta." 'Red'
                Send-Respuesta $puerto 'ERROR incompleto'
                $buffer.SetLength(0)
            } else {
                Start-Sleep -Milliseconds 20
            }
        } elseif ($quieto -ge $FinDeTicketMs) {
            # App vieja (sin empaquetar).
            $buffer.SetLength(0)
            if ($arr[0] -ne 0x1b) {
                # Un ticket siempre empieza con ESC @; esto es un pedazo suelto: imprimirlo saldría en códigos raros.
                Write-Info "Se ignoraron $($arr.Length) bytes sueltos (no es un ticket completo)." 'Yellow'
                continue
            }
            try { Invoke-Ticket $config $arr } catch { Write-Info "No se pudo imprimir: $($_.Exception.Message)" 'Red' }
        } else {
            Start-Sleep -Milliseconds 20
        }
    }
}

while ($true) {
    $puerto = New-Object System.IO.Ports.SerialPort $config.puerto, 9600
    $puerto.ReadTimeout = 500
    # Búfer grande: una nota de entrega con imágenes pesa decenas de KB.
    $puerto.ReadBufferSize = 1048576
    try {
        $puerto.Open()
        Write-Info "Esperando tickets del celular en $($config.puerto)..." 'Cyan'
        Watch-Puerto $puerto $config
    } catch {
        Write-Info "Problema con $($config.puerto): $($_.Exception.Message). Reintentando en 5 segundos..." 'Yellow'
        Start-Sleep -Seconds 5
    } finally {
        if ($puerto.IsOpen) { $puerto.Close() }
        $puerto.Dispose()
    }
}
