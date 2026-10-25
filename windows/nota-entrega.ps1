# Nota de entrega (Windows).
#
# Pregunta solo lo que hay que llenar de la plantilla que se mandó desde el celular
# (quién retira, productos...), la llena y la imprime tal como se diseñó.
# Mientras se escribe aparecen sugerencias de lo que ya se escribió antes:
#   Tab o flecha derecha = completar · flechas arriba/abajo = elegir · Enter = aceptar.
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
        return
    }
    $avisos = New-Object 'System.Collections.Generic.List[string]'
    $img = [NotaDibujo]::Dibujar($p, $datos, (Get-Date), $avisos)
    try {
        $trabajo = [NotaDibujo]::Trabajo($img, $copias)
    } finally { $img.Dispose() }
    foreach ($a in $avisos) { Write-Host "  ! $a" -ForegroundColor Yellow }
    Send-Ticket $config $trabajo
    Add-Registro "Nota '$($p.Nombre)' impresa ($copias copia(s))."
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

while ($true) {
    $plantillas = @(Get-Plantillas)
    if ($plantillas.Count -eq 0) {
        Show-Encabezado "Impresora: $impresora"
        Show-Mensaje 'Todavía no hay plantillas en esta computadora.' 'Yellow'
        Write-Host ''
        Write-Host '  En el celular: Plantillas > tres puntos de la plantilla > "Actualizar en la computadora".' -ForegroundColor Gray
        Write-Host '  (El receptor debe estar funcionando.)' -ForegroundColor DarkGray
        Write-Host ''
        Write-Host '  Enter = volver a revisar   ·   Esc = salir' -ForegroundColor DarkCyan
        $t = Read-Tecla
        if ($t -is [ConsoleKeyInfo] -and $t.Key -eq 'Escape') { exit 0 }
        if ($t -is [string] -and $t -eq 'salir') { exit 0 }
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
    $txtCopias = Read-Texto -Etiqueta 'Copias' -Pista '1' -Numero
    $copias = 1
    if (-not [int]::TryParse($txtCopias, [ref]$copias) -or $copias -lt 1) { $copias = 1 }
    $copias = [Math]::Min(10, $copias)

    Write-Host ''
    Write-Host '  Enter = IMPRIMIR   ·   Esc = cancelar' -ForegroundColor Cyan
    $t = Read-Tecla
    $cancelar = ($t -is [ConsoleKeyInfo] -and $t.Key -eq 'Escape') -or ($t -is [string] -and $t -eq 'cancelar')
    if ($cancelar) {
        Show-Mensaje 'Nota cancelada.' 'Yellow'
    } else {
        try {
            Write-Host ''
            Write-Host '  Imprimiendo...' -ForegroundColor DarkGray
            Invoke-Imprimir $p $datos $copias $config
            Save-EnMemoria $p $datos
            Show-Mensaje ("$([char]0x2713) Nota impresa" + $(if ($copias -gt 1) { " ($copias copias)" } else { '' })) 'Green'
        } catch {
            Show-Mensaje "No se pudo imprimir: $($_.Exception.Message)" 'Red'
            Add-Registro "Error al imprimir la nota: $($_.Exception.Message)"
        }
    }
    Write-Host ''
    Write-Host '  Enter = nueva nota   ·   Esc = salir' -ForegroundColor DarkCyan
    $t = Read-Tecla
    if (($t -is [ConsoleKeyInfo] -and $t.Key -eq 'Escape') -or ($t -is [string] -and $t -eq 'salir')) { break }
}
