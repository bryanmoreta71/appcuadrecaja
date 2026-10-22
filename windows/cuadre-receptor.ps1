# Receptor de Cuadre de Caja para Windows.
#
# Recibe el ticket que manda la app del celular por Bluetooth (puerto COM entrante)
# y lo imprime directo en la impresora térmica (SAT u otra), sin ventana de imprimir.
#
# Uso: doble clic en "Iniciar receptor.bat". La primera vez pregunta el puerto COM
# y la impresora, y lo guarda en cuadre-config.json. Para volver a configurar:
# "Iniciar receptor.bat" -Reconfigurar  (o borrar cuadre-config.json).

param(
    [switch]$Reconfigurar
)

$ErrorActionPreference = 'Stop'
$ConfigPath = Join-Path $PSScriptRoot 'cuadre-config.json'

# Si pasan más de estos milisegundos sin recibir datos, el ticket se da por completo.
$FinDeTicketMs = 1500

Add-Type -ReferencedAssemblies System.Drawing -TypeDefinition @'
using System;
using System.Collections.Generic;
using System.Drawing;
using System.Drawing.Printing;
using System.IO;
using System.Runtime.InteropServices;
using System.Text;

public static class CuadreImpresora
{
    [StructLayout(LayoutKind.Sequential, CharSet = CharSet.Unicode)]
    private class DOCINFO
    {
        public string pDocName;
        public string pOutputFile;
        public string pDataType;
    }

    [DllImport("winspool.drv", CharSet = CharSet.Unicode, SetLastError = true)]
    private static extern bool OpenPrinter(string pPrinterName, out IntPtr phPrinter, IntPtr pDefault);
    [DllImport("winspool.drv", SetLastError = true)]
    private static extern bool ClosePrinter(IntPtr hPrinter);
    [DllImport("winspool.drv", CharSet = CharSet.Unicode, SetLastError = true)]
    private static extern bool StartDocPrinter(IntPtr hPrinter, int level, [In] DOCINFO di);
    [DllImport("winspool.drv", SetLastError = true)]
    private static extern bool EndDocPrinter(IntPtr hPrinter);
    [DllImport("winspool.drv", SetLastError = true)]
    private static extern bool StartPagePrinter(IntPtr hPrinter);
    [DllImport("winspool.drv", SetLastError = true)]
    private static extern bool EndPagePrinter(IntPtr hPrinter);
    [DllImport("winspool.drv", SetLastError = true)]
    private static extern bool WritePrinter(IntPtr hPrinter, byte[] pBytes, int dwCount, out int dwWritten);

    // Manda los bytes ESC/POS tal cual a la impresora (modo RAW): sale con negritas,
    // letra grande y centrado, igual que en una impresora de punto de venta.
    public static void ImprimirRaw(string impresora, byte[] datos)
    {
        IntPtr h;
        if (!OpenPrinter(impresora, out h, IntPtr.Zero))
            throw new Exception("No se pudo abrir la impresora '" + impresora + "' (error " + Marshal.GetLastWin32Error() + ").");
        try
        {
            DOCINFO di = new DOCINFO();
            di.pDocName = "Cuadre de caja";
            di.pDataType = "RAW";
            if (!StartDocPrinter(h, 1, di))
                throw new Exception("La impresora rechazó el trabajo (error " + Marshal.GetLastWin32Error() + ").");
            try
            {
                StartPagePrinter(h);
                int escritos;
                if (!WritePrinter(h, datos, datos.Length, out escritos))
                    throw new Exception("No se pudo enviar el ticket (error " + Marshal.GetLastWin32Error() + ").");
                EndPagePrinter(h);
            }
            finally { EndDocPrinter(h); }
        }
        finally { ClosePrinter(h); }
    }

    // Ajusta el ticket (hecho para 32 columnas) al papel: en 80 mm lo centra con margen.
    public static byte[] AjustarAncho(byte[] datos, int anchoPapelMm)
    {
        if (anchoPapelMm != 80) return datos;
        // 80 mm = 576 puntos; 32 columnas = 384 puntos -> margen izquierdo de 96 puntos.
        byte[] margen = { 0x1d, 0x4c, 96, 0, 0x1d, 0x57, 0x80, 0x01 };
        int inicio = (datos.Length >= 2 && datos[0] == 0x1b && datos[1] == 0x40) ? 2 : 0;
        byte[] r = new byte[datos.Length + margen.Length];
        Array.Copy(datos, 0, r, 0, inicio);
        Array.Copy(margen, 0, r, inicio, margen.Length);
        Array.Copy(datos, inicio, r, inicio + margen.Length, datos.Length - inicio);
        return r;
    }

    // Quita los comandos ESC/POS y deja solo el texto del ticket.
    public static string ATexto(byte[] d)
    {
        StringBuilder sb = new StringBuilder();
        int i = 0;
        while (i < d.Length)
        {
            byte b = d[i];
            if (b == 0x1b && i + 1 < d.Length)
            {
                byte c = d[i + 1];
                if (c == 0x40) i += 2;            // ESC @
                else if (c == 0x64) i += 3;       // ESC d n
                else i += 3;                       // ESC a n, ESC E n, etc.
            }
            else if (b == 0x1d && i + 1 < d.Length)
            {
                byte c = d[i + 1];
                if (c == 0x4c || c == 0x57) i += 4; // GS L / GS W
                else i += 3;                         // GS ! n, etc.
            }
            else
            {
                if (b == 0x0a || (b >= 0x20 && b < 0x7f)) sb.Append((char)b);
                i++;
            }
        }
        return sb.ToString().TrimEnd('\n') + "\n";
    }

    // Plan B para impresoras cuyo controlador no acepta RAW: imprime el texto con el
    // controlador de Windows, en letra de ancho fijo, sin mostrar ninguna ventana.
    public static void ImprimirTexto(string impresora, string texto)
    {
        string[] lineas = texto.Split('\n');
        int siguiente = 0;
        using (Font fuente = new Font("Courier New", 9f))
        using (PrintDocument doc = new PrintDocument())
        {
            doc.PrinterSettings.PrinterName = impresora;
            doc.DocumentName = "Cuadre de caja";
            doc.PrintController = new StandardPrintController();
            doc.DefaultPageSettings.Margins = new Margins(5, 5, 5, 5);
            doc.PrintPage += delegate(object s, PrintPageEventArgs e)
            {
                float y = e.MarginBounds.Top;
                float alto = fuente.GetHeight(e.Graphics);
                while (siguiente < lineas.Length && y + alto <= e.MarginBounds.Bottom)
                {
                    e.Graphics.DrawString(lineas[siguiente], fuente, Brushes.Black, e.MarginBounds.Left, y);
                    y += alto;
                    siguiente++;
                }
                e.HasMorePages = siguiente < lineas.Length;
            };
            doc.Print();
        }
    }
}
'@

function Write-Info($texto, $color = 'Gray') {
    Write-Host ("[{0:HH:mm:ss}] {1}" -f (Get-Date), $texto) -ForegroundColor $color
}

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

function Send-Ticket($config, [byte[]]$datos) {
    if ($config.modo -eq 'texto') {
        [CuadreImpresora]::ImprimirTexto($config.impresora, [CuadreImpresora]::ATexto($datos))
    } else {
        [CuadreImpresora]::ImprimirRaw($config.impresora, [CuadreImpresora]::AjustarAncho($datos, [int]$config.papelMm))
    }
}

function New-Config {
    Write-Host ''
    Write-Host '=== Configuración del receptor ===' -ForegroundColor Cyan
    $config = [ordered]@{
        puerto    = Select-Puerto
        impresora = Select-Impresora
        papelMm   = 80
        modo      = 'raw'
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

while ($true) {
    $puerto = New-Object System.IO.Ports.SerialPort $config.puerto, 9600
    $puerto.ReadTimeout = 500
    try {
        $puerto.Open()
        Write-Info "Esperando tickets del celular en $($config.puerto)..." 'Cyan'
        $buffer = New-Object System.IO.MemoryStream
        $ultimo = Get-Date
        $leer = New-Object byte[] 4096

        while ($true) {
            $disponibles = $puerto.BytesToRead
            if ($disponibles -gt 0) {
                $n = $puerto.Read($leer, 0, [Math]::Min($disponibles, $leer.Length))
                $buffer.Write($leer, 0, $n)
                $ultimo = Get-Date
            } elseif ($buffer.Length -gt 0 -and ((Get-Date) - $ultimo).TotalMilliseconds -ge $FinDeTicketMs) {
                $datos = $buffer.ToArray()
                $buffer.SetLength(0)
                Write-Info "Ticket recibido ($($datos.Length) bytes). Imprimiendo..."
                try {
                    Send-Ticket $config $datos
                    Write-Info 'Ticket impreso.' 'Green'
                } catch {
                    Write-Info "No se pudo imprimir: $($_.Exception.Message)" 'Red'
                }
            } else {
                Start-Sleep -Milliseconds 100
            }
        }
    } catch {
        Write-Info "Problema con $($config.puerto): $($_.Exception.Message). Reintentando en 5 segundos..." 'Yellow'
        Start-Sleep -Seconds 5
    } finally {
        if ($puerto.IsOpen) { $puerto.Close() }
        $puerto.Dispose()
    }
}
