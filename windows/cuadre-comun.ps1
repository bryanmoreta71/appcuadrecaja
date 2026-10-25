# Partes compartidas del receptor y del programa "Nota de entrega":
# configuración (cuadre-config.json), registro e impresión en la impresora de la computadora.

$ConfigPath = Join-Path $PSScriptRoot 'cuadre-config.json'
if (-not $LogPath) { $LogPath = Join-Path $PSScriptRoot 'cuadre-receptor.log' }
$CarpetaPlantillas = Join-Path $PSScriptRoot 'plantillas'

# Carga el código C# (impresión y, si se pide, las notas de entrega).
function Import-Codigo([switch]$ConNota) {
    if ('CuadreImpresora' -as [type]) { return }   # ya cargado
    $fuente = Get-Content (Join-Path $PSScriptRoot 'cuadre-impresora.cs') -Raw -Encoding UTF8
    $refs = @('System.Drawing', 'System.Core')
    if ($ConNota) {
        $fuente += "`n" + (Get-Content (Join-Path $PSScriptRoot 'cuadre-nota.cs') -Raw -Encoding UTF8)
    }
    # Los "using" deben ir al principio: se juntan los de ambos archivos.
    $usings = ([regex]::Matches($fuente, '(?m)^using [\w.]+;\s*$') | ForEach-Object { $_.Value.Trim() } | Sort-Object -Unique) -join "`n"
    $cuerpo = [regex]::Replace($fuente, '(?m)^using [\w.]+;\s*$', '')
    Add-Type -ReferencedAssemblies $refs -TypeDefinition ($usings + "`n" + $cuerpo)
}

# Solo al archivo de registro (sin mostrar nada en pantalla).
function Add-Registro($texto) {
    try {
        if ((Test-Path $LogPath) -and (Get-Item $LogPath).Length -gt 1MB) { Remove-Item $LogPath }
        Add-Content -Path $LogPath -Value ("[{0:dd/MM/yyyy HH:mm:ss}] {1}" -f (Get-Date), $texto) -Encoding UTF8
    } catch { }
}

function Write-Info($texto, $color = 'Gray') {
    Write-Host ("[{0:HH:mm:ss}] {1}" -f (Get-Date), $texto) -ForegroundColor $color
    # También se anota en el registro, para poder revisar cuando funciona sin ventana.
    try {
        if ((Test-Path $LogPath) -and (Get-Item $LogPath).Length -gt 1MB) { Remove-Item $LogPath }
        Add-Content -Path $LogPath -Value ("[{0:dd/MM/yyyy HH:mm:ss}] {1}" -f (Get-Date), $texto) -Encoding UTF8
    } catch { }
}

# Líneas de papel en blanco al final, para que todo el ticket quede fuera de la
# cuchilla al arrancarlo. Si aún queda texto adentro, suba "lineasFinal" en
# cuadre-config.json (por ejemplo a 8); si sobra mucho papel, bájelo.
function Get-LineasFinal($config) { return [int](Get-Ajuste $config 'lineasFinal' 5) }

# Ajustes opcionales de cuadre-config.json, con su valor por defecto si no están.
#   margenIzqMm: mueve la impresión a la derecha (mm). 0 = pegado al borde izquierdo.
#   margenSupMm: espacio arriba en modo texto (mm).
#   anchoImpresionMm: ancho útil de la tirilla (72 mm en papel de 80 mm).
function Get-Ajuste($config, $nombre, $porDefecto) {
    if ($config.PSObject.Properties[$nombre]) { return $config.$nombre }
    return $porDefecto
}

function Send-Ticket($config, [byte[]]$datos) {
    $lineas = [Math]::Max(0, [Math]::Min(30, (Get-LineasFinal $config)))
    if ($config.modo -eq 'texto') {
        $ancho = if ([int]$config.papelMm -eq 58) { 48 } else { 72 }
        [CuadreImpresora]::ImprimirTexto($config.impresora, $datos,
            [float](Get-Ajuste $config 'anchoImpresionMm' $ancho),
            [float](Get-Ajuste $config 'margenIzqMm' 0),
            [float](Get-Ajuste $config 'margenSupMm' 0),
            $lineas)
    } else {
        $margen = [int]([double](Get-Ajuste $config 'margenIzqMm' 0) * 8)  # 8 puntos por mm
        $datos = [CuadreImpresora]::AjustarAncho($datos, [int]$config.papelMm, $margen)
        # ESC d n: avanzar n líneas. GS V 66 0: cortar (si la impresora tiene cortador).
        # Después se devuelve la impresora a como estaba (separación de letras, margen y ancho
        # normales + ESC @), porque esos ajustes se quedan guardados y descuadran lo que
        # impriman otros programas, como el de facturas.
        $final = [byte[]](0x1b, 0x64, $lineas, 0x1d, 0x56, 66, 0,
            0x1b, 0x20, 0,              # ESC SP 0: separación de letras normal
            0x1d, 0x4c, 0, 0,           # GS L 0: sin margen izquierdo
            0x1d, 0x57, 0x40, 0x02,     # GS W 576: todo el ancho (la impresora lo limita a su máximo)
            0x1b, 0x40)                 # ESC @: valores de fábrica
        [CuadreImpresora]::ImprimirRaw($config.impresora, [byte[]]($datos + $final))
    }
}

function Read-Config {
    if (-not (Test-Path $ConfigPath)) { return $null }
    return Get-Content $ConfigPath -Raw | ConvertFrom-Json
}
