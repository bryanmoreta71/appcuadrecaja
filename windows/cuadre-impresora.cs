// Impresión ESC/POS en Windows: RAW o con el controlador, y tickets empaquetados del celular.
// Lo usan el receptor y el programa "Nota de entrega".

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

    // ---------- Tickets empaquetados ----------
    // "CCJ1" + largo (4 bytes) + CRC32 (4 bytes) + datos ESC/POS.

    private static readonly byte[] Magia = { 0x43, 0x43, 0x4A, 0x31 };

    // -1: no es un ticket empaquetado (app vieja); 0: todavía falta; >0: tamaño total ya recibido.
    public static int TamanoTrama(byte[] b, int n)
    {
        for (int i = 0; i < 4 && i < n; i++) if (b[i] != Magia[i]) return -1;
        if (n < 12) return 0;
        int largo = BitConverter.ToInt32(b, 4);
        if (largo < 0 || largo > 20000000) return -1;
        return n >= 12 + largo ? 12 + largo : 0;
    }

    public static byte[] AbrirTrama(byte[] b, out bool integro)
    {
        int largo = BitConverter.ToInt32(b, 4);
        uint crc = BitConverter.ToUInt32(b, 8);
        byte[] d = new byte[largo];
        Array.Copy(b, 12, d, 0, largo);
        integro = Crc32(d) == crc;
        return d;
    }

    public static uint Crc32(byte[] d)
    {
        uint crc = 0xFFFFFFFF;
        foreach (byte x in d)
        {
            crc ^= x;
            for (int k = 0; k < 8; k++) crc = (crc & 1) != 0 ? (crc >> 1) ^ 0xEDB88320 : crc >> 1;
        }
        return ~crc;
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
