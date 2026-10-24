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

    // Ajusta el ticket (hecho para 32 columnas) al papel. En 80 mm separa un poco las letras
    // (ESC SP) para que las 32 columnas ocupen todo el ancho desde el borde izquierdo, y aplica
    // el margen izquierdo configurado (GS L, en puntos de 1/8 mm).
    public static byte[] AjustarAncho(byte[] datos, int anchoPapelMm, int margenIzqPuntos)
    {
        List<byte> extra = new List<byte>();
        if (margenIzqPuntos > 0)
            extra.AddRange(new byte[] { 0x1d, 0x4c, (byte)(margenIzqPuntos % 256), (byte)(margenIzqPuntos / 256) });
        if (anchoPapelMm == 80)
            extra.AddRange(new byte[] { 0x1b, 0x20, 5 }); // 12 + 5 = 17 puntos por letra -> 32 letras = 68 mm
        if (extra.Count == 0) return datos;
        int inicio = (datos.Length >= 2 && datos[0] == 0x1b && datos[1] == 0x40) ? 2 : 0;
        byte[] r = new byte[datos.Length + extra.Count];
        Array.Copy(datos, 0, r, 0, inicio);
        extra.CopyTo(r, inicio);
        Array.Copy(datos, inicio, r, inicio + extra.Count, datos.Length - inicio);
        return r;
    }

    // Un pedazo del ticket ya interpretado: una línea de texto o una imagen.
    private class Pieza
    {
        public string Texto;
        public byte[] Puntos;   // imagen en blanco y negro, 1 bit por punto, filas de AnchoBytes
        public int AnchoBytes;
        public int Filas;
        public bool EsImagen { get { return Puntos != null; } }
        public int Ancho { get { return AnchoBytes * 8; } }
    }

    private static Bitmap CrearImagen(Pieza p)
    {
        Bitmap img = new Bitmap(p.Ancho, p.Filas);
        using (Graphics g = Graphics.FromImage(img)) g.Clear(Color.White);
        for (int y = 0; y < p.Filas; y++)
            for (int x = 0; x < p.Ancho; x++)
                if ((p.Puntos[y * p.AnchoBytes + x / 8] & (0x80 >> (x % 8))) != 0) img.SetPixel(x, y, Color.Black);
        return img;
    }

    // Lee los comandos ESC/POS: separa el texto, las imágenes (GS v 0, como las notas de
    // entrega) y las páginas (cada corte GS V empieza una nueva copia).
    private static List<List<Pieza>> Interpretar(byte[] d)
    {
        List<List<Pieza>> paginas = new List<List<Pieza>>();
        List<Pieza> pagina = new List<Pieza>();
        StringBuilder linea = new StringBuilder();
        int i = 0;
        while (i < d.Length)
        {
            byte b = d[i];
            if (b == 0x1b && i + 1 < d.Length)
            {
                byte c = d[i + 1];
                if (c == 0x40) i += 2;                                  // ESC @
                else if (c == 0x64 && i + 2 < d.Length)                 // ESC d n: n líneas en blanco
                {
                    if (linea.Length > 0) { Pieza p0 = new Pieza(); p0.Texto = linea.ToString(); pagina.Add(p0); linea.Length = 0; }
                    for (int k = 0; k < d[i + 2]; k++) { Pieza p1 = new Pieza(); p1.Texto = ""; pagina.Add(p1); }
                    i += 3;
                }
                else i += 3;                                            // ESC a n, ESC E n, ESC SP n...
            }
            else if (b == 0x1d && i + 1 < d.Length)
            {
                byte c = d[i + 1];
                if (c == 0x76 && i + 7 < d.Length)                      // GS v 0 m xL xH yL yH datos
                {
                    int bytesFila = d[i + 4] + d[i + 5] * 256;
                    int filas = d[i + 6] + d[i + 7] * 256;
                    int inicio = i + 8;
                    if (bytesFila > 0 && filas > 0 && inicio + bytesFila * filas <= d.Length)
                    {
                        Pieza pi = new Pieza();
                        pi.AnchoBytes = bytesFila;
                        pi.Filas = filas;
                        pi.Puntos = new byte[bytesFila * filas];
                        Array.Copy(d, inicio, pi.Puntos, 0, pi.Puntos.Length);
                        pagina.Add(pi);
                    }
                    i = inicio + bytesFila * filas;
                }
                else if (c == 0x56)                                     // GS V m [n]: corte -> nueva página
                {
                    i += (i + 2 < d.Length && d[i + 2] >= 65) ? 4 : 3;
                    if (linea.Length > 0) { Pieza p2 = new Pieza(); p2.Texto = linea.ToString(); pagina.Add(p2); linea.Length = 0; }
                    paginas.Add(pagina);
                    pagina = new List<Pieza>();
                }
                else if (c == 0x4c || c == 0x57) i += 4;                // GS L / GS W
                else i += 3;                                            // GS ! n...
            }
            else
            {
                if (b == 0x0a) { Pieza p3 = new Pieza(); p3.Texto = linea.ToString(); pagina.Add(p3); linea.Length = 0; }
                else if (b >= 0x20 && b < 0x7f) linea.Append((char)b);
                i++;
            }
        }
        if (linea.Length > 0) { Pieza p4 = new Pieza(); p4.Texto = linea.ToString(); pagina.Add(p4); }
        // Sin líneas en blanco al final de cada página (el avance lo pone "lineasFinal").
        if (pagina.Count > 0) paginas.Add(pagina);
        foreach (List<Pieza> pg in paginas)
            while (pg.Count > 0 && !pg[pg.Count - 1].EsImagen && pg[pg.Count - 1].Texto.Trim().Length == 0) pg.RemoveAt(pg.Count - 1);
        paginas.RemoveAll(delegate(List<Pieza> pg) { return pg.Count == 0; });
        return paginas;
    }

    // Plan B para impresoras cuyo controlador no acepta RAW: imprime con el controlador de
    // Windows, sin mostrar ninguna ventana. El texto va en letra de ancho fijo ajustada al
    // ancho de la tirilla y las imágenes (notas de entrega) a su tamaño real (203 ppp).
    // Cada copia es una página, así el controlador corta entre copias. El largo de la hoja
    // se ajusta al contenido para no gastar una hoja entera de 300 mm.
    public static void ImprimirTexto(string impresora, byte[] datos, float anchoMm, float margenIzqMm, float margenSupMm, int lineasFinal)
    {
        List<List<Pieza>> paginas = Interpretar(datos);
        if (paginas.Count == 0) return;
        // Courier New: cada letra mide 0.6 del tamaño de la fuente. 32 letras en el ancho, con 5% de holgura.
        float puntos = (anchoMm / 25.4f) * 72f / (32f * 0.6f) * 0.95f;
        float x0 = margenIzqMm / 25.4f * 100f;
        float y0 = margenSupMm / 25.4f * 100f;
        float altoLinea = puntos * 1.15f / 72f * 100f;
        float maxAnchoImagen = anchoMm / 25.4f * 100f;
        int pagina = 0;

        float largoMaximo = 0;
        foreach (List<Pieza> pg in paginas)
        {
            float alto = y0 + altoLinea * (lineasFinal + 1);
            foreach (Pieza p in pg) alto += p.EsImagen ? AltoImagen(p, maxAnchoImagen) : altoLinea;
            largoMaximo = Math.Max(largoMaximo, alto);
        }

        using (Font fuente = new Font("Courier New", puntos, FontStyle.Bold))
        using (PrintDocument doc = new PrintDocument())
        {
            doc.PrinterSettings.PrinterName = impresora;
            doc.DocumentName = "Cuadre de caja";
            doc.PrintController = new StandardPrintController();
            doc.OriginAtMargins = false;
            doc.DefaultPageSettings.Margins = new Margins(0, 0, 0, 0);
            doc.DefaultPageSettings.PaperSize = new PaperSize("Ticket", (int)(anchoMm / 25.4f * 100f), Math.Max((int)largoMaximo + 20, 100));
            doc.PrintPage += delegate(object s, PrintPageEventArgs e)
            {
                float y = y0;
                foreach (Pieza p in paginas[pagina])
                {
                    if (p.EsImagen)
                    {
                        float w = Math.Min(p.Ancho / 203f * 100f, maxAnchoImagen);
                        float h = AltoImagen(p, maxAnchoImagen);
                        e.Graphics.InterpolationMode = System.Drawing.Drawing2D.InterpolationMode.NearestNeighbor;
                        using (Bitmap img = CrearImagen(p)) e.Graphics.DrawImage(img, x0, y, w, h);
                        y += h;
                    }
                    else
                    {
                        e.Graphics.DrawString(p.Texto, fuente, Brushes.Black, x0, y);
                        y += altoLinea;
                    }
                }
                // El punto final obliga al controlador a no recortar el papel en blanco del final.
                e.Graphics.DrawString(".", fuente, Brushes.Black, x0, y + altoLinea * lineasFinal);
                pagina++;
                e.HasMorePages = pagina < paginas.Count;
            };
            doc.Print();
        }
    }

    private static float AltoImagen(Pieza p, float maxAncho)
    {
        float w = Math.Min(p.Ancho / 203f * 100f, maxAncho);
        return p.Filas * w / p.Ancho;
    }

    // Solo el texto del ticket, sin comandos (para revisar en pruebas).
    public static string ATexto(byte[] d)
    {
        StringBuilder sb = new StringBuilder();
        foreach (List<Pieza> pg in Interpretar(d))
        {
            foreach (Pieza p in pg) sb.Append(p.EsImagen ? "[imagen " + p.Ancho + "x" + p.Filas + "]" : p.Texto).Append('\n');
            sb.Append("--- corte ---\n");
        }
        return sb.ToString();
    }
}
'@

function Write-Info($texto, $color = 'Gray') {
    Write-Host ("[{0:HH:mm:ss}] {1}" -f (Get-Date), $texto) -ForegroundColor $color
    # También se anota en el registro, para poder revisar cuando funciona sin ventana.
    try {
        if ((Test-Path $LogPath) -and (Get-Item $LogPath).Length -gt 1MB) { Remove-Item $LogPath }
        Add-Content -Path $LogPath -Value ("[{0:dd/MM/yyyy HH:mm:ss}] {1}" -f (Get-Date), $texto) -Encoding UTF8
    } catch { }
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
