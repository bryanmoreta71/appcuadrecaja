# Nota de entrega (Windows).
#
# Pregunta solo lo que hay que llenar de la plantilla que se mandó desde el celular
# (quién retira, productos...), la llena y la imprime tal como se diseñó.
# Mientras se escribe aparecen sugerencias de lo que ya se escribió antes:
#   Tab o flecha derecha = completar · flechas arriba/abajo = elegir · Enter = aceptar.
#
# Comandos en el menú: Enter = nueva nota, R = reimprimir la última, H = historial (últimas 5),
# Esc = salir. Las impresiones quedan guardadas aunque se cierre el programa.
#
# Uso: doble clic en "Nota de entrega.bat".

$ErrorActionPreference = 'Stop'
$LogPath = Join-Path $PSScriptRoot 'nota-entrega.log'
. (Join-Path $PSScriptRoot 'cuadre-comun.ps1')
Import-Codigo -ConNota

# En pruebas se guarda lo llenado en un archivo en vez de imprimir.
$SalidaPrueba = $env:NOTA_PRUEBA_SALIDA

try { [Console]::OutputEncoding = [Text.Encoding]::UTF8 } catch { }
try { $Host.UI.RawUI.WindowTitle = 'Nota de entrega' } catch { }

$Memoria = New-Object MemoriaNota (Join-Path $PSScriptRoot 'memoria-notas.json')
$CarpetaHistorial = Join-Path $PSScriptRoot 'historial-notas'
$MaxHistorial = 5
$Acento = 'Cyan'
$Suave = 'DarkGray'

# ---------- Pantalla ----------

function Get-Ancho {
    try { return [Math]::Max(50, [Math]::Min(100, [Console]::WindowWidth - 2)) } catch { return 78 }
}

function Show-Encabezado($subtitulo) {
    try { Clear-Host } catch { }
    $w = Get-Ancho
    $titulo = '  NOTA DE ENTREGA'
    Write-Host ''
    Write-Host ('  ' + ([string][char]0x256D) + ([string][char]0x2500) * ($w - 4) + [char]0x256E) -ForegroundColor DarkCyan
    Write-Host ('  ' + [char]0x2502) -ForegroundColor DarkCyan -NoNewline
    Write-Host (' ' + [char]0x25A0 + $titulo.PadRight($w - 6)) -ForegroundColor Cyan -NoNewline
    Write-Host ([char]0x2502) -ForegroundColor DarkCyan
    Write-Host ('  ' + [char]0x2502) -ForegroundColor DarkCyan -NoNewline
    Write-Host ('   ' + $subtitulo).PadRight($w - 4).Substring(0, $w - 4) -ForegroundColor DarkGray -NoNewline
    Write-Host ([char]0x2502) -ForegroundColor DarkCyan
    Write-Host ('  ' + [char]0x2570 + ([string][char]0x2500) * ($w - 4) + [char]0x256F) -ForegroundColor DarkCyan
    Write-Host ''
}

function Show-Seccion($texto) {
    Write-Host ''
    Write-Host ('  ' + $texto.ToUpper()) -ForegroundColor Magenta
    Write-Host ('  ' + ([string][char]0x2500) * [Math]::Min(40, $texto.Length + 4)) -ForegroundColor DarkMagenta
}

function Show-Mensaje($texto, $color = 'Green') {
    Write-Host ''
    Write-Host ('  ' + $texto) -ForegroundColor $color
}

# ---------- Escribir con sugerencias ----------

function Test-ConsolaInteractiva {
    try { [void][Console]::KeyAvailable; return -not [Console]::IsInputRedirected } catch { return $false }
}

# Lee una línea mostrando debajo las sugerencias que coinciden con lo escrito.
function Read-Texto {
    param(
        [string]$Etiqueta,
        [string]$Clave = $null,
        [string]$Pista = '',
        [switch]$Numero
    )
    $prefijo = "  $([char]0x203A) $Etiqueta`: "
    if (-not (Test-ConsolaInteractiva)) {
        Write-Host $prefijo -ForegroundColor $Acento -NoNewline
        return (Read-Host)
    }

    $max = 5
    $buffer = ''
    $sel = -1
    $w = Get-Ancho
    # Reservar las líneas de las sugerencias (si la pantalla se desplaza, se ajusta).
    Write-Host ("`n" * $max) -NoNewline
    $top = [Console]::CursorTop - $max
    if ($top -lt 0) { $top = 0 }

    while ($true) {
        $sug = @()
        if ($Clave -and $buffer.Trim().Length -gt 0) { $sug = @($Memoria.Sugerencias($Clave, $buffer, $max)) }
        if ($sel -ge $sug.Count) { $sel = $sug.Count - 1 }
        $fantasma = ''
        if ($sel -lt 0 -and $sug.Count -gt 0 -and $sug[0].ToLower().StartsWith($buffer.ToLower())) {
            $fantasma = $sug[0].Substring($buffer.Length)
        }

        # Línea de escritura.
        [Console]::SetCursorPosition(0, $top)
        Write-Host $prefijo -ForegroundColor $Acento -NoNewline
        $mostrar = if ($sel -ge 0) { $sug[$sel] } else { $buffer }
        Write-Host $mostrar -ForegroundColor White -NoNewline
        if ($buffer.Length -eq 0 -and $Pista) { Write-Host $Pista -ForegroundColor $Suave -NoNewline }
        else { Write-Host $fantasma -ForegroundColor $Suave -NoNewline }
        $usado = $prefijo.Length + $mostrar.Length + $(if ($buffer.Length -eq 0) { $Pista.Length } else { $fantasma.Length })
        Write-Host (' ' * [Math]::Max(0, $w - $usado)) -NoNewline

        # Sugerencias debajo.
        for ($k = 0; $k -lt $max; $k++) {
            [Console]::SetCursorPosition(0, $top + 1 + $k)
            if ($k -lt $sug.Count) {
                $txt = "      $($sug[$k])"
                if ($txt.Length -gt $w) { $txt = $txt.Substring(0, $w) }
                if ($k -eq $sel) { Write-Host $txt.PadRight($w) -ForegroundColor Black -BackgroundColor Cyan -NoNewline }
                else { Write-Host $txt.PadRight($w) -ForegroundColor DarkCyan -NoNewline }
            } else {
                Write-Host (' ' * $w) -NoNewline
            }
        }
        [Console]::SetCursorPosition([Math]::Min($w, $prefijo.Length + $mostrar.Length), $top)

        $tecla = [Console]::ReadKey($true)
        switch ($tecla.Key) {
            'Enter' {
                $final = if ($sel -ge 0) { $sug[$sel] } else { $buffer }
                # Borrar las sugerencias y dejar la línea escrita.
                for ($k = 1; $k -le $max; $k++) { [Console]::SetCursorPosition(0, $top + $k); Write-Host (' ' * $w) -NoNewline }
                [Console]::SetCursorPosition(0, $top)
                Write-Host $prefijo -ForegroundColor $Acento -NoNewline
                Write-Host ($final.PadRight([Math]::Max(0, $w - $prefijo.Length))) -ForegroundColor White
                [Console]::SetCursorPosition(0, $top + 1)
                return $final
            }
            'Tab' { if ($sel -ge 0) { $buffer = $sug[$sel]; $sel = -1 } elseif ($fantasma) { $buffer = $sug[0] } }
            'RightArrow' { if ($sel -ge 0) { $buffer = $sug[$sel]; $sel = -1 } elseif ($fantasma) { $buffer = $sug[0] } }
            'DownArrow' { if ($sug.Count -gt 0) { $sel = [Math]::Min($sug.Count - 1, $sel + 1) } }
            'UpArrow' { if ($sel -ge 0) { $sel-- } }
            'Escape' { $buffer = ''; $sel = -1 }
            'Backspace' {
                if ($sel -ge 0) { $buffer = $sug[$sel]; $sel = -1 }
                if ($buffer.Length -gt 0) { $buffer = $buffer.Substring(0, $buffer.Length - 1) }
            }
            default {
                $c = $tecla.KeyChar
                if ([int]$c -ge 32) {
                    if ($Numero -and -not ($c -match '[0-9.,]')) { break }
                    if ($sel -ge 0) { $buffer = $sug[$sel]; $sel = -1 }
                    $buffer += $c
                }
            }
        }
    }
}

function Read-Tecla {
    if (-not (Test-ConsolaInteractiva)) { return (Read-Host) }
    return [Console]::ReadKey($true)
}

# ---------- Plantillas ----------

function Get-Plantillas {
    $lista = @()
    if (-not (Test-Path $CarpetaPlantillas)) { return $lista }
    foreach ($f in (Get-ChildItem $CarpetaPlantillas -Filter '*.json' | Sort-Object LastWriteTime -Descending)) {
        try { $lista += [PaqueteNota]::LeerArchivo($f.FullName) }
        catch { Add-Registro "No se pudo leer la plantilla $($f.Name): $($_.Exception.Message)" }
    }
    return $lista
}

function Select-Plantilla($plantillas) {
    if ($plantillas.Count -eq 1) { return $plantillas[0] }
    Show-Seccion 'Elija la plantilla'
    for ($i = 0; $i -lt $plantillas.Count; $i++) {
        Write-Host ("    {0}  " -f ($i + 1)) -ForegroundColor Cyan -NoNewline
        Write-Host $plantillas[$i].Nombre -ForegroundColor White
    }
    while ($true) {
        $r = Read-Texto -Etiqueta 'Número' -Pista '1' -Numero
        if ([string]::IsNullOrWhiteSpace($r)) { return $plantillas[0] }
        $n = 0
        if ([int]::TryParse($r, [ref]$n) -and $n -ge 1 -and $n -le $plantillas.Count) { return $plantillas[$n - 1] }
        Write-Host '    Escriba un número de la lista.' -ForegroundColor Yellow
    }
}

# ---------- Llenar ----------

function Read-Nota($p) {
    $datos = New-Object DatosNota
    $ahora = Get-Date
    foreach ($x in $p.Llenables) {
        if ($x -is [CampoNota]) {
            if ($x.Automatico) {
                $v = if ($x.Tipo -eq 'FECHA') { [NotaDibujo]::Fecha($ahora) } else { [NotaDibujo]::Hora($ahora) }
                Write-Host "  $([char]0x203A) $($x.Etiqueta): " -ForegroundColor DarkCyan -NoNewline
                Write-Host "$v  (automática)" -ForegroundColor DarkGray
                continue
            }
            $pista = if ($x.ValorInicial) { $x.ValorInicial } else { '' }
            $v = Read-Texto -Etiqueta $x.Etiqueta -Clave ([MemoriaNota]::ClaveCampo($x.Etiqueta)) -Pista $pista -Numero:($x.Tipo -eq 'NUMERO')
            $datos.PonerCampo($x.Id, $v)
        } elseif ($x -is [TablaNota]) {
            Show-Seccion 'Productos'
            Write-Host '  Deje el producto en blanco y presione Enter para terminar.' -ForegroundColor DarkGray
            # Primero lo principal (el producto), luego las demás columnas (cantidad...).
            $principal = -1
            for ($c = 0; $c -lt $x.Columnas.Count; $c++) { if (-not $x.Columnas[$c].Numerica) { $principal = $c; break } }
            if ($principal -lt 0) { $principal = 0 }
            $n = 1
            while ($true) {
                Write-Host ''
                Write-Host "  #$n" -ForegroundColor Magenta
                $fila = New-Object string[] $x.Columnas.Count
                $col = $x.Columnas[$principal]
                $fila[$principal] = Read-Texto -Etiqueta $col.Titulo -Clave ([MemoriaNota]::ClaveColumna($col.Titulo)) -Numero:$col.Numerica
                if ([string]::IsNullOrWhiteSpace($fila[$principal])) { break }
                for ($c = 0; $c -lt $x.Columnas.Count; $c++) {
                    if ($c -eq $principal) { continue }
                    $col = $x.Columnas[$c]
                    $clave = if ($col.Numerica) { $null } else { [MemoriaNota]::ClaveColumna($col.Titulo) }
                    $fila[$c] = Read-Texto -Etiqueta $col.Titulo -Clave $clave -Numero:$col.Numerica
                }
                $datos.AgregarFila($x.Id, $fila)
                $n++
            }
        }
    }
    return $datos
}

function Show-Resumen($p, $datos) {
    Show-Seccion 'Resumen'
    foreach ($x in $p.Llenables) {
        if ($x -is [CampoNota] -and -not $x.Automatico) {
            $v = ''
            [void]$datos.Campos.TryGetValue($x.Id, [ref]$v)
            Write-Host ("  {0,-24} " -f ($x.Etiqueta + ':')) -ForegroundColor DarkCyan -NoNewline
            if ([string]::IsNullOrWhiteSpace($v)) { Write-Host '(en blanco: línea para llenar a mano)' -ForegroundColor DarkGray }
            else { Write-Host $v -ForegroundColor White }
        } elseif ($x -is [TablaNota]) {
            $filas = $datos.FilasDe($x.Id)
            Write-Host ''
            Write-Host ('  ' + (($x.Columnas | ForEach-Object { $_.Titulo.PadRight($(if ($_.Numerica) { 8 } else { 34 })) }) -join '')) -ForegroundColor Black -BackgroundColor DarkCyan
            foreach ($f in $filas) {
                $linea = ''
                for ($c = 0; $c -lt $x.Columnas.Count; $c++) {
                    $ancho = if ($x.Columnas[$c].Numerica) { 8 } else { 34 }
                    $t = [string]$f[$c]
                    if ($t.Length -gt $ancho - 1) { $t = $t.Substring(0, $ancho - 2) + [char]0x2026 }
                    $linea += $t.PadRight($ancho)
                }
                Write-Host ('  ' + $linea) -ForegroundColor White
            }
            if ($filas.Count -eq 0) { Write-Host '  (sin productos)' -ForegroundColor DarkGray }
        }
    }
}

function Save-EnMemoria($p, $datos) {
    foreach ($x in $p.Llenables) {
        if ($x -is [CampoNota] -and -not $x.Automatico -and $x.Tipo -ne 'NUMERO') {
            $v = ''
            if ($datos.Campos.TryGetValue($x.Id, [ref]$v)) { $Memoria.Recordar([MemoriaNota]::ClaveCampo($x.Etiqueta), $v) }
        } elseif ($x -is [TablaNota]) {
            foreach ($f in $datos.FilasDe($x.Id)) {
                for ($c = 0; $c -lt $x.Columnas.Count; $c++) {
                    if (-not $x.Columnas[$c].Numerica) { $Memoria.Recordar([MemoriaNota]::ClaveColumna($x.Columnas[$c].Titulo), $f[$c]) }
                }
            }
        }
    }
    $Memoria.Guardar()
}

function Invoke-Imprimir($p, $datos, [int]$copias, $config) {
    if ($SalidaPrueba) {
        $o = [ordered]@{ plantilla = $p.Id; copias = $copias; campos = @{}; filas = @{} }
        foreach ($k in $datos.Campos.Keys) { $o.campos[$k] = $datos.Campos[$k] }
        foreach ($k in $datos.Filas.Keys) { $o.filas[$k] = @($datos.Filas[$k] | ForEach-Object { , @($_) }) }
        $o | ConvertTo-Json -Depth 6 | Set-Content -Path $SalidaPrueba -Encoding UTF8
        Save-Historial $null $p $datos $copias
        return
    }
    $avisos = New-Object 'System.Collections.Generic.List[string]'
    $img = [NotaDibujo]::Dibujar($p, $datos, (Get-Date), $avisos)
    try {
        foreach ($a in $avisos) { Write-Host "  ! $a" -ForegroundColor Yellow }
        Send-Ticket $config ([NotaDibujo]::Trabajo($img, $copias))
        Add-Registro "Nota '$($p.Nombre)' impresa ($copias copia(s))."
        Save-Historial $img $p $datos $copias
    } finally { $img.Dispose() }
}

# ---------- Historial (últimas 5 impresiones, se conservan al cerrar el programa) ----------

function Get-Resumen($p, $datos) {
    $partes = @()
    foreach ($x in $p.Llenables) {
        if ($x -is [CampoNota] -and -not $x.Automatico) {
            $v = ''
            if ($datos.Campos.TryGetValue($x.Id, [ref]$v) -and $v.Trim()) { $partes += $v.Trim() }
        }
    }
    $n = 0
    foreach ($x in $p.Llenables) { if ($x -is [TablaNota]) { $n += $datos.FilasDe($x.Id).Count } }
    if ($n -gt 0) { $partes += "$n producto" + $(if ($n -ne 1) { 's' } else { '' }) }
    return ($partes -join ' · ')
}

# Guarda la nota ya dibujada (una copia), para reimprimirla igual aunque cambie la plantilla.
function Save-Historial($img, $p, $datos, [int]$copias) {
    try {
        New-Item -ItemType Directory -Path $CarpetaHistorial -Force | Out-Null
        $id = Get-Date -Format 'yyyyMMdd-HHmmss-fff'
        if ($img) { [NotaDibujo]::GuardarPng($img, (Join-Path $CarpetaHistorial "$id.png")) }
        [ordered]@{ id = $id; fecha = (Get-Date).ToString('o'); plantilla = $p.Nombre; resumen = (Get-Resumen $p $datos); copias = $copias } |
            ConvertTo-Json | Set-Content -Path (Join-Path $CarpetaHistorial "$id.json") -Encoding UTF8
        # Solo las últimas 5.
        Get-Historial | Select-Object -Skip $MaxHistorial | ForEach-Object {
            Remove-Item -Path (Join-Path $CarpetaHistorial "$($_.id).*") -ErrorAction SilentlyContinue
        }
    } catch {
        Add-Registro "No se pudo guardar en el historial: $($_.Exception.Message)"
    }
}

function Get-Historial {
    if (-not (Test-Path $CarpetaHistorial)) { return @() }
    $lista = @()
    foreach ($f in (Get-ChildItem $CarpetaHistorial -Filter '*.json')) {
        try { $lista += (Get-Content $f.FullName -Raw -Encoding UTF8 | ConvertFrom-Json) } catch { }
    }
    return @($lista | Sort-Object id -Descending)
}

function Format-Fecha($iso) {
    try { return ([datetime]::Parse($iso)).ToString('dd/MM/yyyy  hh:mm tt', [Globalization.CultureInfo]::GetCultureInfo('en-US')) } catch { return $iso }
}

function Show-Entrada($e, $numero = $null) {
    if ($numero) { Write-Host ("    {0}  " -f $numero) -ForegroundColor Cyan -NoNewline } else { Write-Host '    ' -NoNewline }
    Write-Host (Format-Fecha $e.fecha) -ForegroundColor White -NoNewline
    Write-Host "   $($e.plantilla)" -ForegroundColor DarkCyan -NoNewline
    Write-Host ("   · {0} copia{1}" -f $e.copias, $(if ($e.copias -ne 1) { 's' } else { '' })) -ForegroundColor DarkGray
    if ($e.resumen) { Write-Host "       $($e.resumen)" -ForegroundColor Gray }
}

# Pregunta cuántas copias; si se deja vacío, las mismas que la última vez.
function Read-Copias([int]$predeterminado) {
    if ($predeterminado -lt 1) { $predeterminado = 1 }
    $txt = Read-Texto -Etiqueta '¿Cuántas copias?' -Pista "$predeterminado" -Numero
    $n = 0
    if (-not [int]::TryParse($txt, [ref]$n) -or $n -lt 1) { $n = $predeterminado }
    return [Math]::Min(10, $n)
}

function Confirm-Imprimir {
    Write-Host ''
    Write-Host '  Enter = IMPRIMIR   ·   Esc = cancelar' -ForegroundColor Cyan
    $t = Read-Tecla
    return -not (($t -is [ConsoleKeyInfo] -and $t.Key -eq 'Escape') -or ($t -is [string] -and $t -eq 'cancelar'))
}

# Reimprime una nota guardada. Devuelve el mensaje para mostrar en el menú.
function Invoke-Reimpresion($e, $config) {
    Show-Encabezado "Reimprimir   ·   Impresora: $impresora"
    Show-Entrada $e
    Write-Host ''
    $copias = Read-Copias ([int]$e.copias)
    if (-not (Confirm-Imprimir)) { return @{ Texto = 'Reimpresión cancelada.'; Color = 'Yellow' } }
    try {
        Write-Host ''
        Write-Host '  Imprimiendo...' -ForegroundColor DarkGray
        if ($SalidaPrueba) {
            [ordered]@{ reimpresion = $e.id; copias = $copias } | ConvertTo-Json | Set-Content -Path $SalidaPrueba -Encoding UTF8
        } else {
            $ruta = Join-Path $CarpetaHistorial "$($e.id).png"
            if (-not (Test-Path $ruta)) { throw 'no se encontró la nota guardada' }
            $img = [NotaDibujo]::CargarImagen($ruta)
            try { Send-Ticket $config ([NotaDibujo]::Trabajo($img, $copias)) } finally { $img.Dispose() }
        }
        Add-Registro "Reimpresión de la nota $($e.id) ($copias copia(s))."
        return @{ Texto = "$([char]0x2713) Nota reimpresa" + $(if ($copias -gt 1) { " ($copias copias)" } else { '' }); Color = 'Green' }
    } catch {
        Add-Registro "Error al reimprimir: $($_.Exception.Message)"
        return @{ Texto = "No se pudo reimprimir: $($_.Exception.Message)"; Color = 'Red' }
    }
}

function Show-Historial($config) {
    $hist = @(Get-Historial)
    Show-Encabezado "Historial   ·   últimas $MaxHistorial impresiones"
    if ($hist.Count -eq 0) {
        Show-Mensaje 'Todavía no hay notas impresas.' 'Yellow'
        Write-Host ''
        Write-Host '  Enter = volver' -ForegroundColor DarkCyan
        [void](Read-Tecla)
        return $null
    }
    for ($i = 0; $i -lt $hist.Count; $i++) { Show-Entrada $hist[$i] ($i + 1); Write-Host '' }
    Write-Host '  Escriba el número para reimprimir, o Enter para volver.' -ForegroundColor DarkGray
    $r = Read-Texto -Etiqueta 'Número' -Numero
    $n = 0
    if (-not [int]::TryParse($r, [ref]$n) -or $n -lt 1 -or $n -gt $hist.Count) { return $null }
    return (Invoke-Reimpresion $hist[$n - 1] $config)
}

# Qué tecla se tocó en el menú: nueva, r, h o salir.
function Get-Comando($t) {
    if ($t -is [ConsoleKeyInfo]) {
        if ($t.Key -eq 'Escape') { return 'salir' }
        if ($t.Key -eq 'Enter' -or $t.KeyChar -eq 'n' -or $t.KeyChar -eq 'N') { return 'nueva' }
        if ($t.KeyChar -eq 'r' -or $t.KeyChar -eq 'R') { return 'r' }
        if ($t.KeyChar -eq 'h' -or $t.KeyChar -eq 'H') { return 'h' }
        return $null
    }
    $s = ([string]$t).Trim().ToLower()
    if ($s -eq '' -or $s -eq 'n') { return 'nueva' }
    if ($s -in @('r', 'h', 'salir')) { return $s }
    return $null
}

# ---------- Programa ----------

$config = Read-Config
if (-not $config -and -not $SalidaPrueba) {
    Show-Encabezado 'Falta configurar la impresora'
    Show-Mensaje 'Primero abra "Iniciar receptor.bat" y elija la impresora. Después vuelva a abrir este programa.' 'Yellow'
    [void](Read-Tecla)
    exit 1
}
$impresora = if ($config) { $config.impresora } else { 'prueba' }

$mensaje = $null
while ($true) {
    # ---------- Menú ----------
    $hist = @(Get-Historial)
    Show-Encabezado "Impresora: $impresora"
    if ($mensaje) { Write-Host ('  ' + $mensaje.Texto) -ForegroundColor $mensaje.Color; Write-Host ''; $mensaje = $null }
    if ($hist.Count -gt 0) {
        Write-Host '  Última impresión' -ForegroundColor DarkGray
        Show-Entrada $hist[0]
        Write-Host ''
    }
    foreach ($o in @(@('Enter', 'Nueva nota de entrega'), @('R', 'Reimprimir la última'), @('H', "Historial (últimas $MaxHistorial)"), @('Esc', 'Salir'))) {
        Write-Host ('   ' + (' ' + $o[0] + ' ').PadRight(8)) -ForegroundColor Black -BackgroundColor DarkCyan -NoNewline
        Write-Host ('  ' + $o[1]) -ForegroundColor White
        Write-Host ''
    }
    $comando = Get-Comando (Read-Tecla)

    if ($comando -eq 'salir') { break }
    if ($comando -eq 'r') {
        if ($hist.Count -eq 0) { $mensaje = @{ Texto = 'Todavía no hay notas impresas para reimprimir.'; Color = 'Yellow' } }
        else { $mensaje = Invoke-Reimpresion $hist[0] $config }
        continue
    }
    if ($comando -eq 'h') { $mensaje = Show-Historial $config; continue }
    if ($comando -ne 'nueva') { continue }

    # ---------- Nueva nota ----------
    $plantillas = @(Get-Plantillas)
    if ($plantillas.Count -eq 0) {
        Show-Encabezado "Impresora: $impresora"
        Show-Mensaje 'Todavía no hay plantillas en esta computadora.' 'Yellow'
        Write-Host ''
        Write-Host '  En el celular: Plantillas > tres puntos de la plantilla > "Actualizar en la computadora".' -ForegroundColor Gray
        Write-Host '  (El receptor debe estar funcionando.)' -ForegroundColor DarkGray
        Write-Host ''
        Write-Host '  Enter = volver' -ForegroundColor DarkCyan
        [void](Read-Tecla)
        continue
    }

    Show-Encabezado "Impresora: $impresora"
    $p = Select-Plantilla $plantillas
    $Memoria.Sembrar($p.Memoria)
    Show-Encabezado "$($p.Nombre)   ·   Impresora: $impresora"
    Write-Host '  Escriba y presione Enter. Sugerencias: Tab completa, flechas eligen.' -ForegroundColor DarkGray
    Write-Host ''

    $datos = Read-Nota $p
    Show-Resumen $p $datos

    Write-Host ''
    $copias = Read-Copias $(if ($hist.Count -gt 0) { [int]$hist[0].copias } else { 1 })

    if (-not (Confirm-Imprimir)) {
        $mensaje = @{ Texto = 'Nota cancelada.'; Color = 'Yellow' }
        continue
    }
    try {
        Write-Host ''
        Write-Host '  Imprimiendo...' -ForegroundColor DarkGray
        Invoke-Imprimir $p $datos $copias $config
        Save-EnMemoria $p $datos
        $mensaje = @{ Texto = "$([char]0x2713) Nota impresa" + $(if ($copias -gt 1) { " ($copias copias)" } else { '' }) + '   ·   R = reimprimir'; Color = 'Green' }
    } catch {
        $mensaje = @{ Texto = "No se pudo imprimir: $($_.Exception.Message)"; Color = 'Red' }
        Add-Registro "Error al imprimir la nota: $($_.Exception.Message)"
    }
}
