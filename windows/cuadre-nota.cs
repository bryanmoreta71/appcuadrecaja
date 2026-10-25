// Programa "Nota de entrega" de Windows: lee la plantilla que manda el celular, la llena con
// lo que se escribe en la computadora y la dibuja igual que el celular para imprimirla.
//
// Escrito en C# 5 (el que trae Windows PowerShell 5.1), sin librerías externas.

using System;
using System.Collections;
using System.Collections.Generic;
using System.Drawing;
using System.Drawing.Drawing2D;
using System.Drawing.Imaging;
using System.Drawing.Text;
using System.Globalization;
using System.IO;
using System.Runtime.InteropServices;
using System.Text;
using System.Text.RegularExpressions;

public class CampoNota
{
    public string Id;
    public string Etiqueta;
    public string Tipo;          // TEXTO, NUMERO, FECHA, HORA, PARRAFO
    public string ValorInicial;
    public bool Automatico { get { return Tipo == "FECHA" || Tipo == "HORA"; } }
}

public class ColumnaNota
{
    public string Titulo;
    public int Ancho;
    public string Alineacion;
    public bool Numerica;
}

public class TablaNota
{
    public string Id;
    public List<ColumnaNota> Columnas = new List<ColumnaNota>();
}

/** Lo que se llena: valores de los campos y filas de las tablas. */
public class DatosNota
{
    public Dictionary<string, string> Campos = new Dictionary<string, string>();
    public Dictionary<string, List<string[]>> Filas = new Dictionary<string, List<string[]>>();

    public void PonerCampo(string id, string valor) { Campos[id] = valor ?? ""; }

    public void AgregarFila(string tablaId, string[] celdas)
    {
        if (!Filas.ContainsKey(tablaId)) Filas[tablaId] = new List<string[]>();
        Filas[tablaId].Add(celdas);
    }

    public List<string[]> FilasDe(string tablaId)
    {
        List<string[]> l;
        return Filas.TryGetValue(tablaId, out l) ? l : new List<string[]>();
    }
}

/** La plantilla tal como la manda el celular ("CCPLANTILLA1" + JSON). */
public class PaqueteNota
{
    public const string Prefijo = "CCPLANTILLA1\n";

    public string Id;
    public string Nombre;
    public int LargoMm;
    public int AnchoPuntos;
    public List<Dictionary<string, object>> Bloques = new List<Dictionary<string, object>>();
    public Dictionary<string, List<string>> Memoria = new Dictionary<string, List<string>>();
    /** Campos y tablas para llenar, en el orden de la plantilla. */
    public List<object> Llenables = new List<object>();
    public List<CampoNota> Campos = new List<CampoNota>();

    public static bool EsPaquete(byte[] datos)
    {
        byte[] p = Encoding.ASCII.GetBytes(Prefijo);
        if (datos.Length < p.Length) return false;
        for (int i = 0; i < p.Length; i++) if (datos[i] != p[i]) return false;
        return true;
    }

    public static PaqueteNota Leer(string texto)
    {
        if (texto.StartsWith(Prefijo)) texto = texto.Substring(Prefijo.Length);
        Dictionary<string, object> raiz = (Dictionary<string, object>)Json.Leer(texto);
        PaqueteNota p = new PaqueteNota();
        Dictionary<string, object> pl = (Dictionary<string, object>)raiz["plantilla"];
        p.Id = Texto(pl, "id", "");
        p.Nombre = Texto(pl, "nombre", "Nota de entrega");
        p.LargoMm = Entero(pl, "largoMm", 0);
        p.AnchoPuntos = Entero(raiz, "anchoPuntos", 576);
        foreach (object b in (object[])raiz["bloques"])
        {
            Dictionary<string, object> bloque = (Dictionary<string, object>)b;
            p.Bloques.Add(bloque);
            if (Texto(bloque, "tipo", "") != "elemento") continue;
            Dictionary<string, object> e = (Dictionary<string, object>)bloque["elemento"];
            string tipo = Texto(e, "tipo", "");
            if (tipo == "campo")
            {
                CampoNota c = new CampoNota();
                c.Id = Texto(e, "id", "");
                c.Etiqueta = Texto(e, "etiqueta", "");
                c.Tipo = Texto(e, "tipoCampo", "TEXTO");
                c.ValorInicial = Texto(e, "valorInicial", "");
                p.Llenables.Add(c);
                p.Campos.Add(c);
            }
            else if (tipo == "tabla")
            {
                TablaNota t = new TablaNota();
                t.Id = Texto(e, "id", "");
                foreach (object co in Lista(e, "columnas"))
                {
                    Dictionary<string, object> c = (Dictionary<string, object>)co;
                    ColumnaNota col = new ColumnaNota();
                    col.Titulo = Texto(c, "titulo", "");
                    col.Ancho = Math.Max(1, Entero(c, "ancho", 1));
                    col.Alineacion = Texto(c, "alineacion", "IZQUIERDA");
                    col.Numerica = Logico(c, "numerica", false);
                    t.Columnas.Add(col);
                }
                p.Llenables.Add(t);
            }
        }
        object mem;
        if (raiz.TryGetValue("memoria", out mem) && mem is Dictionary<string, object>)
        {
            foreach (KeyValuePair<string, object> kv in (Dictionary<string, object>)mem)
            {
                List<string> l = new List<string>();
                foreach (object x in (object[])kv.Value) l.Add(Convert.ToString(x));
                p.Memoria[kv.Key] = l;
            }
        }
        return p;
    }

    public static PaqueteNota LeerArchivo(string ruta) { return Leer(File.ReadAllText(ruta, Encoding.UTF8)); }

    internal static string Texto(Dictionary<string, object> d, string k, string def)
    {
        object v; return d.TryGetValue(k, out v) && v != null ? Convert.ToString(v, CultureInfo.InvariantCulture) : def;
    }
    internal static int Entero(Dictionary<string, object> d, string k, int def)
    {
        object v; return d.TryGetValue(k, out v) && v != null ? Convert.ToInt32(v, CultureInfo.InvariantCulture) : def;
    }
    internal static bool Logico(Dictionary<string, object> d, string k, bool def)
    {
        object v; return d.TryGetValue(k, out v) && v is bool ? (bool)v : def;
    }
    internal static object[] Lista(Dictionary<string, object> d, string k)
    {
        object v; return d.TryGetValue(k, out v) && v is object[] ? (object[])v : new object[0];
    }
}

/** Lector de JSON pequeño (sin depender de librerías de Windows). */
public static class Json
{
    public static object Leer(string s)
    {
        int i = 0;
        object v = Valor(s, ref i);
        return v;
    }

    private static void Espacios(string s, ref int i) { while (i < s.Length && char.IsWhiteSpace(s[i])) i++; }

    private static object Valor(string s, ref int i)
    {
        Espacios(s, ref i);
        if (i >= s.Length) throw new FormatException("JSON incompleto");
        char c = s[i];
        if (c == '{')
        {
            Dictionary<string, object> d = new Dictionary<string, object>();
            i++;
            Espacios(s, ref i);
            if (s[i] == '}') { i++; return d; }
            while (true)
            {
                Espacios(s, ref i);
                string k = Cadena(s, ref i);
                Espacios(s, ref i);
                if (s[i] != ':') throw new FormatException("Falta ':' en JSON");
                i++;
                d[k] = Valor(s, ref i);
                Espacios(s, ref i);
                if (s[i] == ',') { i++; continue; }
                if (s[i] == '}') { i++; return d; }
                throw new FormatException("JSON mal formado");
            }
        }
        if (c == '[')
        {
            List<object> l = new List<object>();
            i++;
            Espacios(s, ref i);
            if (s[i] == ']') { i++; return l.ToArray(); }
            while (true)
            {
                l.Add(Valor(s, ref i));
                Espacios(s, ref i);
                if (s[i] == ',') { i++; continue; }
                if (s[i] == ']') { i++; return l.ToArray(); }
                throw new FormatException("JSON mal formado");
            }
        }
        if (c == '"') return Cadena(s, ref i);
        if (s.Substring(i, Math.Min(4, s.Length - i)) == "true") { i += 4; return true; }
        if (s.Substring(i, Math.Min(5, s.Length - i)) == "false") { i += 5; return false; }
        if (s.Substring(i, Math.Min(4, s.Length - i)) == "null") { i += 4; return null; }
        int inicio = i;
        while (i < s.Length && "+-0123456789.eE".IndexOf(s[i]) >= 0) i++;
        string num = s.Substring(inicio, i - inicio);
        long entero;
        if (long.TryParse(num, NumberStyles.Integer, CultureInfo.InvariantCulture, out entero)) return entero;
        return double.Parse(num, CultureInfo.InvariantCulture);
    }

    private static string Cadena(string s, ref int i)
    {
        if (s[i] != '"') throw new FormatException("Se esperaba texto en JSON");
        i++;
        StringBuilder sb = new StringBuilder();
        while (s[i] != '"')
        {
            char c = s[i++];
            if (c != '\\') { sb.Append(c); continue; }
            char e = s[i++];
            switch (e)
            {
                case 'n': sb.Append('\n'); break;
                case 't': sb.Append('\t'); break;
                case 'r': sb.Append('\r'); break;
                case 'b': sb.Append('\b'); break;
                case 'f': sb.Append('\f'); break;
                case 'u': sb.Append((char)Convert.ToInt32(s.Substring(i, 4), 16)); i += 4; break;
                default: sb.Append(e); break;
            }
        }
        i++;
        return sb.ToString();
    }

    /** Texto con comillas y caracteres escapados, para escribir JSON. */
    public static string Texto(string t)
    {
        StringBuilder sb = new StringBuilder("\"");
        foreach (char c in t ?? "")
        {
            if (c == '"' || c == '\\') sb.Append('\\').Append(c);
            else if (c < 32) sb.Append("\\u").Append(((int)c).ToString("x4"));
            else sb.Append(c);
        }
        return sb.Append('"').ToString();
    }
}

/** Memoria de lo escrito (igual que en el celular): sugerencias según lo que se escribe. */
public class MemoriaNota
{
    private class Entrada { public string Texto; public int Usos; public long Ultimo; }

    private readonly string ruta;
    private readonly Dictionary<string, List<Entrada>> datos = new Dictionary<string, List<Entrada>>();

    public MemoriaNota(string ruta)
    {
        this.ruta = ruta;
        try
        {
            if (!File.Exists(ruta)) return;
            Dictionary<string, object> o = (Dictionary<string, object>)Json.Leer(File.ReadAllText(ruta, Encoding.UTF8));
            foreach (KeyValuePair<string, object> kv in o)
            {
                List<Entrada> l = new List<Entrada>();
                foreach (object x in (object[])kv.Value)
                {
                    Dictionary<string, object> e = (Dictionary<string, object>)x;
                    Entrada en = new Entrada();
                    en.Texto = PaqueteNota.Texto(e, "t", "");
                    en.Usos = PaqueteNota.Entero(e, "u", 1);
                    object f; en.Ultimo = e.TryGetValue("f", out f) ? Convert.ToInt64(f, CultureInfo.InvariantCulture) : 0;
                    l.Add(en);
                }
                datos[kv.Key] = l;
            }
        }
        catch { }
    }

    public static string Normalizar(string s)
    {
        string d = (s ?? "").Trim().ToLowerInvariant().Normalize(NormalizationForm.FormD);
        StringBuilder sb = new StringBuilder();
        foreach (char c in d) if (CharUnicodeInfo.GetUnicodeCategory(c) != UnicodeCategory.NonSpacingMark) sb.Append(c);
        return Regex.Replace(sb.ToString(), "\\s+", " ");
    }

    public static string ClaveCampo(string etiqueta) { return "campo:" + Normalizar(etiqueta); }
    public static string ClaveColumna(string titulo) { return "columna:" + Normalizar(titulo); }

    /** Agrega lo que vino del celular sin pisar lo que ya se usó en la computadora. */
    public void Sembrar(Dictionary<string, List<string>> desdeCelular)
    {
        foreach (KeyValuePair<string, List<string>> kv in desdeCelular)
            foreach (string t in kv.Value)
                if (Buscar(kv.Key, t) == null) Lista(kv.Key).Add(Nueva(t, 1, 0));
    }

    public List<string> Sugerencias(string clave, string escrito, int maximo)
    {
        string b = Normalizar(escrito);
        List<KeyValuePair<int, Entrada>> encontradas = new List<KeyValuePair<int, Entrada>>();
        if (b.Length == 0 || !datos.ContainsKey(clave)) return new List<string>();
        foreach (Entrada e in datos[clave])
        {
            string n = Normalizar(e.Texto);
            if (n == b) continue;
            int orden;
            if (n.StartsWith(b)) orden = 0;
            else if (Array.Exists(n.Split(' '), delegate(string w) { return w.StartsWith(b); })) orden = 1;
            else if (n.Contains(b)) orden = 2;
            else continue;
            encontradas.Add(new KeyValuePair<int, Entrada>(orden, e));
        }
        encontradas.Sort(delegate(KeyValuePair<int, Entrada> x, KeyValuePair<int, Entrada> y)
        {
            if (x.Key != y.Key) return x.Key.CompareTo(y.Key);
            if (x.Value.Usos != y.Value.Usos) return y.Value.Usos.CompareTo(x.Value.Usos);
            return y.Value.Ultimo.CompareTo(x.Value.Ultimo);
        });
        List<string> r = new List<string>();
        for (int i = 0; i < encontradas.Count && i < maximo; i++) r.Add(encontradas[i].Value.Texto);
        return r;
    }

    public void Recordar(string clave, string texto)
    {
        string t = (texto ?? "").Trim();
        if (t.Length == 0) return;
        long ahora = (long)(DateTime.UtcNow - new DateTime(1970, 1, 1)).TotalMilliseconds;
        Entrada e = Buscar(clave, t);
        if (e != null) { e.Texto = t; e.Usos++; e.Ultimo = ahora; }
        else Lista(clave).Add(Nueva(t, 1, ahora));
    }

    public void Olvidar(string clave, string texto)
    {
        Entrada e = Buscar(clave, texto);
        if (e != null) datos[clave].Remove(e);
    }

    public void Guardar()
    {
        StringBuilder sb = new StringBuilder("{");
        bool primera = true;
        foreach (KeyValuePair<string, List<Entrada>> kv in datos)
        {
            if (!primera) sb.Append(',');
            primera = false;
            sb.Append(Json.Texto(kv.Key)).Append(":[");
            for (int i = 0; i < kv.Value.Count; i++)
            {
                Entrada e = kv.Value[i];
                if (i > 0) sb.Append(',');
                sb.Append("{\"t\":").Append(Json.Texto(e.Texto)).Append(",\"u\":").Append(e.Usos).Append(",\"f\":").Append(e.Ultimo).Append('}');
            }
            sb.Append(']');
        }
        sb.Append('}');
        File.WriteAllText(ruta, sb.ToString(), new UTF8Encoding(false));
    }

    private List<Entrada> Lista(string clave)
    {
        if (!datos.ContainsKey(clave)) datos[clave] = new List<Entrada>();
        return datos[clave];
    }

    private Entrada Buscar(string clave, string texto)
    {
        if (!datos.ContainsKey(clave)) return null;
        string n = Normalizar(texto);
        return datos[clave].Find(delegate(Entrada e) { return Normalizar(e.Texto) == n; });
    }

    private static Entrada Nueva(string t, int usos, long f) { Entrada e = new Entrada(); e.Texto = t; e.Usos = usos; e.Ultimo = f; return e; }
}

/** Dibuja la nota llena (igual que Renderizador.kt del celular) y arma los comandos para imprimirla. */
public static class NotaDibujo
{
    public const int AltoMaximo = 6000;
    public const int PuntosPorMm = 8;
    private static readonly CultureInfo US = CultureInfo.GetCultureInfo("en-US");

    public static string Fecha(DateTime d) { return d.ToString("dd/MM/yyyy", US); }
    public static string Hora(DateTime d) { return d.ToString("hh:mm tt", US); }

    private class Lienzo
    {
        public Graphics G;
        public int Ancho;
        public int Margen = 6;
        public float Y = 4;
        public List<string> Avisos;
        public int Util { get { return Ancho - Margen * 2; } }
    }

    public static Bitmap Dibujar(PaqueteNota p, DatosNota datos, DateTime ahora, List<string> avisos)
    {
        Bitmap lienzo = new Bitmap(p.AnchoPuntos, AltoMaximo, PixelFormat.Format32bppArgb);
        Lienzo l = new Lienzo();
        l.Ancho = p.AnchoPuntos;
        l.Avisos = avisos ?? new List<string>();
        using (Graphics g = Graphics.FromImage(lienzo))
        {
            g.Clear(Color.White);
            g.TextRenderingHint = TextRenderingHint.AntiAliasGridFit;
            g.SmoothingMode = SmoothingMode.AntiAlias;
            g.InterpolationMode = InterpolationMode.NearestNeighbor;
            g.PixelOffsetMode = PixelOffsetMode.Half;
            l.G = g;

            Dictionary<string, string> valores = new Dictionary<string, string>();
            valores["fecha"] = Fecha(ahora);
            valores["hora"] = Hora(ahora);
            foreach (CampoNota c in p.Campos)
                valores[c.Etiqueta.Trim().ToLowerInvariant()] = ValorCampo(c, datos, ahora, false);

            foreach (Dictionary<string, object> b in p.Bloques)
            {
                if (l.Y > AltoMaximo - 200) { l.Avisos.Add("La plantilla es demasiado larga; se cortó el final."); break; }
                string tipo = PaqueteNota.Texto(b, "tipo", "");
                if (tipo == "imagen")
                {
                    byte[] png = Convert.FromBase64String(PaqueteNota.Texto(b, "png", ""));
                    using (MemoryStream ms = new MemoryStream(png))
                    using (Bitmap img = new Bitmap(ms))
                    {
                        g.DrawImage(img, new Rectangle(0, (int)l.Y, img.Width, img.Height), 0, 0, img.Width, img.Height, GraphicsUnit.Pixel);
                    }
                    l.Y += PaqueteNota.Entero(b, "alto", 0);
                }
                else if (tipo == "espacio")
                {
                    l.Y += PaqueteNota.Entero(b, "alto", 0);
                }
                else if (tipo == "elemento")
                {
                    Dictionary<string, object> e = (Dictionary<string, object>)b["elemento"];
                    string te = PaqueteNota.Texto(e, "tipo", "");
                    if (te == "texto") Texto(l, Reemplazar(PaqueteNota.Texto(e, "texto", ""), valores), Estilo.De(e));
                    else if (te == "campo") Campo(l, e, ValorCampo(BuscarCampo(p, PaqueteNota.Texto(e, "id", "")), datos, ahora, true));
                    else if (te == "tabla") Tabla(l, e, datos.FilasDe(PaqueteNota.Texto(e, "id", "")));
                    else if (te == "qr") Qr(l, e, Reemplazar(PaqueteNota.Texto(e, "contenido", ""), valores));
                    else if (te == "barras") Barras(l, e, Reemplazar(PaqueteNota.Texto(e, "contenido", ""), valores));
                }
            }
        }

        int alto = Math.Max(40, Math.Min(AltoMaximo, (int)(l.Y + 8)));
        if (p.LargoMm > 0)
        {
            int fijo = Math.Min(p.LargoMm * PuntosPorMm, AltoMaximo);
            if (alto > fijo) l.Avisos.Add("El contenido mide " + (alto / PuntosPorMm) + " mm, más que el largo elegido (" + p.LargoMm + " mm). Se imprime completo.");
            alto = Math.Max(alto, fijo);
        }
        Bitmap final = lienzo.Clone(new Rectangle(0, 0, lienzo.Width, alto), PixelFormat.Format32bppArgb);
        lienzo.Dispose();
        ABlancoYNegro(final);
        return final;
    }

    private static CampoNota BuscarCampo(PaqueteNota p, string id)
    {
        CampoNota c = p.Campos.Find(delegate(CampoNota x) { return x.Id == id; });
        if (c == null) { c = new CampoNota(); c.Id = id; c.Etiqueta = ""; c.Tipo = "TEXTO"; c.ValorInicial = ""; }
        return c;
    }

    private static string ValorCampo(CampoNota c, DatosNota d, DateTime ahora, bool conAutomatico)
    {
        string v;
        if (d.Campos.TryGetValue(c.Id, out v) && !string.IsNullOrWhiteSpace(v)) return v;
        if (!string.IsNullOrWhiteSpace(c.ValorInicial)) return c.ValorInicial;
        if (c.Tipo == "FECHA") return Fecha(ahora);
        if (c.Tipo == "HORA") return Hora(ahora);
        return "";
    }

    public static string Reemplazar(string texto, Dictionary<string, string> valores)
    {
        if (texto.IndexOf('{') < 0) return texto;
        return Regex.Replace(texto, "\\{([^{}]+)\\}", delegate(Match m)
        {
            string v;
            return valores.TryGetValue(m.Groups[1].Value.Trim().ToLowerInvariant(), out v) ? v : m.Value;
        });
    }

    // ---------- Estilo de letra ----------

    private class Estilo
    {
        public string Letra = "MODERNA";
        public int Tamano = 24;
        public bool Negrita, Cursiva, Subrayado, Invertido;
        public string Alineacion = "IZQUIERDA";

        public static Estilo De(Dictionary<string, object> e)
        {
            Estilo s = new Estilo();
            object o;
            if (!e.TryGetValue("estilo", out o) || !(o is Dictionary<string, object>)) return s;
            Dictionary<string, object> d = (Dictionary<string, object>)o;
            s.Letra = PaqueteNota.Texto(d, "letra", "MODERNA");
            s.Tamano = PaqueteNota.Entero(d, "tamano", 24);
            s.Negrita = PaqueteNota.Logico(d, "negrita", false);
            s.Cursiva = PaqueteNota.Logico(d, "cursiva", false);
            s.Subrayado = PaqueteNota.Logico(d, "subrayado", false);
            s.Invertido = PaqueteNota.Logico(d, "invertido", false);
            s.Alineacion = PaqueteNota.Texto(d, "alineacion", "IZQUIERDA");
            return s;
        }

        public Estilo Copia() { return (Estilo)MemberwiseClone(); }
    }

    private static readonly Dictionary<string, string[]> Familias = new Dictionary<string, string[]>
    {
        { "MODERNA", new[] { "Segoe UI", "Arial", "DejaVu Sans" } },
        { "CLASICA", new[] { "Georgia", "Times New Roman", "DejaVu Serif" } },
        { "MAQUINA", new[] { "Consolas", "Courier New", "DejaVu Sans Mono" } },
        { "ANGOSTA", new[] { "Arial Narrow", "Segoe UI Semilight", "Arial", "DejaVu Sans Condensed" } },
        { "MANUSCRITA", new[] { "Segoe Script", "Brush Script MT", "Comic Sans MS", "DejaVu Serif" } },
    };

    private static HashSet<string> instaladas;

    private static string Familia(string letra)
    {
        if (instaladas == null)
        {
            instaladas = new HashSet<string>(StringComparer.OrdinalIgnoreCase);
            using (InstalledFontCollection f = new InstalledFontCollection())
                foreach (FontFamily ff in f.Families) instaladas.Add(ff.Name);
        }
        string[] opciones;
        if (!Familias.TryGetValue(letra, out opciones)) opciones = Familias["MODERNA"];
        foreach (string o in opciones) if (instaladas.Contains(o)) return o;
        return FontFamily.GenericSansSerif.Name;
    }

    private static Font Fuente(Estilo e)
    {
        FontStyle st = FontStyle.Regular;
        if (e.Negrita) st |= FontStyle.Bold;
        if (e.Cursiva) st |= FontStyle.Italic;
        if (e.Subrayado) st |= FontStyle.Underline;
        return new Font(Familia(e.Letra), Math.Max(10, Math.Min(120, e.Tamano)), st, GraphicsUnit.Pixel);
    }

    private static StringFormat Formato(string alineacion)
    {
        StringFormat f = new StringFormat(StringFormat.GenericDefault);
        f.Alignment = alineacion == "CENTRO" ? StringAlignment.Center : alineacion == "DERECHA" ? StringAlignment.Far : StringAlignment.Near;
        f.Trimming = StringTrimming.None;
        return f;
    }

    private static float Medir(Graphics g, string t, Font f, float ancho, StringFormat fmt)
    {
        if (string.IsNullOrEmpty(t)) return f.GetHeight(g);
        return (float)Math.Ceiling(g.MeasureString(t, f, new SizeF(Math.Max(1, ancho), 10000), fmt).Height);
    }

    private static float Escribir(Graphics g, string t, Font f, Brush b, float x, float y, float ancho, StringFormat fmt)
    {
        float h = Medir(g, t, f, ancho, fmt);
        if (!string.IsNullOrEmpty(t)) g.DrawString(t, f, b, new RectangleF(x, y, Math.Max(1, ancho), h + 4), fmt);
        return h;
    }

    // ---------- Elementos ----------

    private static void Texto(Lienzo l, string t, Estilo e)
    {
        if (string.IsNullOrEmpty(t)) return;
        int pad = e.Invertido ? 8 : 0;
        using (Font f = Fuente(e))
        using (StringFormat fmt = Formato(e.Alineacion))
        {
            float h = Medir(l.G, t, f, l.Util - pad * 2, fmt);
            if (e.Invertido) l.G.FillRectangle(Brushes.Black, l.Margen, l.Y, l.Ancho - l.Margen * 2, h + pad * 2);
            Escribir(l.G, t, f, e.Invertido ? Brushes.White : Brushes.Black, l.Margen + pad, l.Y + pad, l.Util - pad * 2, fmt);
            l.Y += h + pad * 2 + 4;
        }
    }

    private static void Campo(Lienzo l, Dictionary<string, object> c, string valor)
    {
        Estilo e = Estilo.De(c);
        string forma = PaqueteNota.Texto(c, "forma", "EN_LINEA");
        string borde = PaqueteNota.Texto(c, "borde", "PUNTILLOS");
        string etiqueta = PaqueteNota.Texto(c, "etiqueta", "");
        Graphics g = l.G;
        if (forma == "EN_LINEA")
        {
            Estilo neg = e.Copia(); neg.Negrita = true;
            using (Font fe = Fuente(neg))
            using (Font fv = Fuente(e))
            using (StringFormat izq = Formato("IZQUIERDA"))
            {
                string et = etiqueta + ": ";
                float wEt;
                using (StringFormat tipo = (StringFormat)StringFormat.GenericTypographic.Clone())
                {
                    // Que cuente el espacio después de los dos puntos.
                    tipo.FormatFlags |= StringFormatFlags.MeasureTrailingSpaces;
                    wEt = g.MeasureString(et, fe, new PointF(0, 0), tipo).Width;
                }
                float h = Medir(g, et, fe, l.Util, izq);
                g.DrawString(et, fe, Brushes.Black, l.Margen, l.Y, StringFormat.GenericTypographic);
                if (string.IsNullOrWhiteSpace(valor))
                {
                    // Sin valor: queda una línea para llenar a mano.
                    float baseLinea = l.Y + Ascenso(fe) + 3;
                    Linea(g, l.Margen + wEt + 4, baseLinea, l.Ancho - l.Margen, baseLinea, BordeOLinea(borde), 2);
                }
                else
                {
                    h = Math.Max(h, Escribir(g, valor, fv, Brushes.Black, l.Margen + wEt, l.Y, l.Util - wEt, izq));
                }
                l.Y += h + 8;
            }
        }
        else if (forma == "APILADO")
        {
            Estilo chica = e.Copia(); chica.Negrita = true; chica.Tamano = (int)(e.Tamano * 0.75f);
            using (Font fe = Fuente(chica))
            using (Font fv = Fuente(e))
            using (StringFormat fmt = Formato(e.Alineacion))
            {
                l.Y += Escribir(g, etiqueta, fe, Brushes.Black, l.Margen, l.Y, l.Util, fmt) + 2;
                if (string.IsNullOrWhiteSpace(valor))
                {
                    l.Y += e.Tamano * 1.3f;
                    Linea(g, l.Margen, l.Y, l.Ancho - l.Margen, l.Y, BordeOLinea(borde), 2);
                    l.Y += 16;
                }
                else
                {
                    l.Y += Escribir(g, valor, fv, Brushes.Black, l.Margen, l.Y, l.Util, fmt) + 8;
                }
            }
        }
        else // CELDA
        {
            int pad = 8;
            float top = l.Y;
            Estilo chica = e.Copia(); chica.Negrita = true; chica.Tamano = Math.Max(14, (int)(e.Tamano * 0.65f));
            using (Font fe = Fuente(chica))
            using (Font fv = Fuente(e))
            using (StringFormat izq = Formato("IZQUIERDA"))
            using (StringFormat fmt = Formato(e.Alineacion))
            {
                float interior = top + pad + Escribir(g, etiqueta.ToUpper(), fe, Brushes.Black, l.Margen + pad, top + pad, l.Util - pad * 2, izq) + 2;
                if (string.IsNullOrWhiteSpace(valor)) interior += e.Tamano * 1.3f;
                else interior += Escribir(g, valor, fv, Brushes.Black, l.Margen + pad, interior, l.Util - pad * 2, fmt);
                float bottom = interior + pad;
                Caja(g, l.Margen, top, l.Ancho - l.Margen, bottom, borde, 2);
                l.Y = bottom + 6;
            }
        }
    }

    private static void Tabla(Lienzo l, Dictionary<string, object> t, List<string[]> todas)
    {
        List<ColumnaNota> cols = new List<ColumnaNota>();
        foreach (object co in PaqueteNota.Lista(t, "columnas"))
        {
            Dictionary<string, object> c = (Dictionary<string, object>)co;
            ColumnaNota col = new ColumnaNota();
            col.Titulo = PaqueteNota.Texto(c, "titulo", "");
            col.Ancho = Math.Max(1, PaqueteNota.Entero(c, "ancho", 1));
            col.Alineacion = PaqueteNota.Texto(c, "alineacion", "IZQUIERDA");
            col.Numerica = PaqueteNota.Logico(c, "numerica", false);
            cols.Add(col);
        }
        if (cols.Count == 0) return;
        List<string[]> filas = todas.FindAll(delegate(string[] f) { return Array.Exists(f, delegate(string x) { return !string.IsNullOrWhiteSpace(x); }); });
        Estilo estilo = Estilo.De(t);
        string borde = PaqueteNota.Texto(t, "borde", "LINEA");
        bool encabezadoInvertido = PaqueteNota.Logico(t, "encabezadoInvertido", true);
        bool mostrarTotal = PaqueteNota.Logico(t, "mostrarTotal", true);
        int filasVacias = Math.Max(0, Math.Min(30, PaqueteNota.Entero(t, "filasVacias", 0)));

        int pad = 6;
        float izquierda = l.Margen, derecha = l.Ancho - l.Margen;
        int pesoTotal = 0;
        foreach (ColumnaNota c in cols) pesoTotal += c.Ancho;
        List<float> xs = new List<float>();
        xs.Add(izquierda);
        foreach (ColumnaNota c in cols) xs.Add(xs[xs.Count - 1] + (derecha - izquierda) * c.Ancho / pesoTotal);
        float grosor = 2;
        bool conLineas = borde != "NINGUNO";
        Graphics g = l.G;

        Action<string[], Estilo, bool> fila = delegate(string[] celdas, Estilo es, bool invertida)
        {
            using (Font f = Fuente(es))
            {
                float alto = 0;
                for (int i = 0; i < cols.Count; i++)
                    using (StringFormat fmt = Formato(cols[i].Alineacion))
                        alto = Math.Max(alto, Medir(g, i < celdas.Length ? celdas[i] : "", f, xs[i + 1] - xs[i] - pad * 2, fmt));
                alto += pad * 2;
                if (invertida) g.FillRectangle(Brushes.Black, izquierda, l.Y, derecha - izquierda, alto);
                for (int i = 0; i < cols.Count; i++)
                    using (StringFormat fmt = Formato(cols[i].Alineacion))
                        Escribir(g, i < celdas.Length ? celdas[i] : "", f, invertida ? Brushes.White : Brushes.Black, xs[i] + pad, l.Y + pad, xs[i + 1] - xs[i] - pad * 2, fmt);
                if (conLineas && !invertida)
                    for (int i = 1; i < xs.Count - 1; i++) Linea(g, xs[i], l.Y, xs[i], l.Y + alto, borde, grosor);
                l.Y += alto;
                if (conLineas) Linea(g, izquierda, l.Y, derecha, l.Y, borde, grosor);
            }
        };

        float inicio = l.Y;
        Estilo negrita = estilo.Copia(); negrita.Negrita = true;
        if (conLineas && !encabezadoInvertido) Linea(g, izquierda, l.Y, derecha, l.Y, borde, grosor);
        string[] titulos = new string[cols.Count];
        for (int i = 0; i < cols.Count; i++) titulos[i] = cols[i].Titulo;
        fila(titulos, negrita, encabezadoInvertido);
        if (!conLineas && !encabezadoInvertido) Linea(g, izquierda, l.Y, derecha, l.Y, "LINEA", grosor);
        foreach (string[] f in filas) fila(f, estilo, false);
        for (int k = 0; k < filasVacias; k++) fila(new string[cols.Count], estilo, false);

        if (mostrarTotal && filas.Count > 0)
        {
            int colNum = cols.FindIndex(delegate(ColumnaNota c) { return c.Numerica; });
            if (colNum >= 0)
            {
                double suma = 0;
                foreach (string[] f in filas)
                {
                    double v;
                    if (colNum < f.Length && double.TryParse((f[colNum] ?? "").Replace(',', '.'), NumberStyles.Float, CultureInfo.InvariantCulture, out v)) suma += v;
                }
                string total = suma % 1 == 0 ? ((long)suma).ToString(CultureInfo.InvariantCulture) : suma.ToString("0.00", CultureInfo.InvariantCulture);
                int colTexto = -1;
                for (int i = 0; i < cols.Count; i++) if (i != colNum) { colTexto = i; break; }
                if (colTexto < 0) colTexto = colNum;
                string[] celdas = new string[cols.Count];
                for (int i = 0; i < cols.Count; i++) celdas[i] = i == colNum ? total : i == colTexto ? "TOTAL" : "";
                fila(celdas, negrita, false);
            }
        }
        if (conLineas)
        {
            Linea(g, izquierda, inicio, izquierda, l.Y, borde, grosor);
            Linea(g, derecha, inicio, derecha, l.Y, borde, grosor);
        }
        l.Y += 6;
    }

    private static void Qr(Lienzo l, Dictionary<string, object> q, string contenido)
    {
        if (string.IsNullOrWhiteSpace(contenido)) { l.Avisos.Add("Un código QR no tiene contenido."); return; }
        string densidad = PaqueteNota.Texto(q, "densidad", "SENCILLO");
        int nivel = densidad == "LIGERO" ? 0 : densidad == "ESPESO" ? 3 : 1;
        bool[,] m;
        try { m = CodigoQr.Crear(contenido, nivel); }
        catch (Exception) { l.Avisos.Add("No se pudo crear el código QR (contenido muy largo)."); return; }
        int n = m.GetLength(0);
        int objetivo = l.Util * Math.Max(15, Math.Min(100, PaqueteNota.Entero(q, "tamanoPorcentaje", 45))) / 100;
        int modulo = Math.Max(2, objetivo / n);
        int lado = modulo * n;
        string alineacion = PaqueteNota.Texto(q, "alineacion", "CENTRO");
        float x0 = XPara(l, lado, alineacion);
        bool puntos = PaqueteNota.Texto(q, "forma", "CUADROS") == "PUNTOS";
        for (int my = 0; my < n; my++)
            for (int mx = 0; mx < n; mx++)
            {
                if (!m[my, mx]) continue;
                float x = x0 + mx * modulo, y = l.Y + my * modulo;
                bool ojo = (mx < 7 && my < 7) || (mx >= n - 7 && my < 7) || (mx < 7 && my >= n - 7);
                if (puntos && !ojo) l.G.FillEllipse(Brushes.Black, x + modulo * 0.02f, y + modulo * 0.02f, modulo * 0.96f, modulo * 0.96f);
                else l.G.FillRectangle(Brushes.Black, x, y, modulo, modulo);
            }
        l.Y += lado + 6;
        if (PaqueteNota.Logico(q, "mostrarTexto", false))
        {
            Estilo e = new Estilo(); e.Tamano = 18; e.Alineacion = alineacion;
            Texto(l, contenido, e);
        }
    }

    private static void Barras(Lienzo l, Dictionary<string, object> b, string contenido)
    {
        List<int> anchos;
        try { anchos = Code128.Codificar(contenido); }
        catch (Exception) { l.Avisos.Add("Código de barras: \"" + contenido + "\" no es válido."); return; }
        int total = 0;
        foreach (int a in anchos) total += a;
        string grosor = PaqueteNota.Texto(b, "grosor", "NORMAL");
        int porcentaje = grosor == "DELGADO" ? 55 : grosor == "GRUESO" ? 100 : 75;
        int modulo = l.Util * porcentaje / 100 / total;
        if (modulo < 1) { l.Avisos.Add("El código de barras es demasiado largo para el ancho del papel."); return; }
        int w = modulo * total;
        float x0 = XPara(l, w, PaqueteNota.Texto(b, "alineacion", "CENTRO"));
        int alto = Math.Max(30, Math.Min(300, PaqueteNota.Entero(b, "alto", 80)));
        float x = x0;
        for (int i = 0; i < anchos.Count; i++)
        {
            if (i % 2 == 0) l.G.FillRectangle(Brushes.Black, x, l.Y, anchos[i] * modulo, alto);
            x += anchos[i] * modulo;
        }
        l.Y += alto + 4;
        if (PaqueteNota.Logico(b, "mostrarTexto", true))
        {
            Estilo e = new Estilo(); e.Letra = "MAQUINA"; e.Tamano = 20;
            using (Font f = Fuente(e))
            using (StringFormat fmt = Formato("CENTRO"))
                l.Y += Escribir(l.G, contenido, f, Brushes.Black, x0, l.Y, Math.Max(40, w), fmt);
        }
        l.Y += 6;
    }

    // ---------- Ayudantes de dibujo ----------

    private static float Ascenso(Font f)
    {
        FontFamily ff = f.FontFamily;
        return f.Size * ff.GetCellAscent(f.Style) / ff.GetEmHeight(f.Style);
    }

    private static float XPara(Lienzo l, int w, string a)
    {
        if (a == "IZQUIERDA") return l.Margen;
        if (a == "DERECHA") return l.Ancho - l.Margen - w;
        return (l.Ancho - w) / 2;
    }

    private static string BordeOLinea(string b) { return b == "NINGUNO" ? "PUNTILLOS" : b; }

    private static void Linea(Graphics g, float x1, float y1, float x2, float y2, string borde, float grosor)
    {
        if (borde == "NINGUNO") return;
        using (Pen p = new Pen(Color.Black, grosor))
        {
            // Los guiones se miden en múltiplos del grosor, como en el celular.
            if (borde == "PUNTILLOS") p.DashPattern = new float[] { 1.5f, 2.5f };
            else if (borde == "GUIONES") p.DashPattern = new float[] { 12f / grosor, 8f / grosor };
            g.DrawLine(p, x1, y1, x2, y2);
            if (borde == "DOBLE")
            {
                float d = grosor + 3;
                if (x1 == x2) g.DrawLine(p, x1 + d, y1, x2 + d, y2); else g.DrawLine(p, x1, y1 + d, x2, y2 + d);
            }
        }
    }

    private static void Caja(Graphics g, float l, float t, float r, float b, string borde, float grosor)
    {
        Linea(g, l, t, r, t, borde, grosor);
        Linea(g, l, b, r, b, borde, grosor);
        Linea(g, l, t, l, b, borde, grosor);
        Linea(g, r, t, r, b, borde, grosor);
    }

    /** Solo puntos negros y blancos (luminancia < 140 = negro), como en el celular. */
    private static void ABlancoYNegro(Bitmap b)
    {
        Rectangle r = new Rectangle(0, 0, b.Width, b.Height);
        BitmapData d = b.LockBits(r, ImageLockMode.ReadWrite, PixelFormat.Format32bppArgb);
        int[] px = new int[b.Width * b.Height];
        Marshal.Copy(d.Scan0, px, 0, px.Length);
        for (int i = 0; i < px.Length; i++)
        {
            int c = px[i];
            int a = (c >> 24) & 0xff, rr = (c >> 16) & 0xff, gg = (c >> 8) & 0xff, bb = c & 0xff;
            bool negro = a > 127 && (rr * 299 + gg * 587 + bb * 114) / 1000 < 140;
            px[i] = negro ? unchecked((int)0xFF000000) : unchecked((int)0xFFFFFFFF);
        }
        Marshal.Copy(px, 0, d.Scan0, px.Length);
        b.UnlockBits(d);
    }

    public static void GuardarPng(Bitmap b, string ruta) { b.Save(ruta, ImageFormat.Png); }

    /** Abre una nota guardada (PNG) como imagen de 32 bits, lista para Trabajo(). */
    public static Bitmap CargarImagen(string ruta)
    {
        using (MemoryStream ms = new MemoryStream(File.ReadAllBytes(ruta)))
        using (Bitmap leida = new Bitmap(ms))
        {
            Bitmap b = new Bitmap(leida.Width, leida.Height, PixelFormat.Format32bppArgb);
            using (Graphics g = Graphics.FromImage(b))
            {
                g.Clear(Color.White);
                g.InterpolationMode = InterpolationMode.NearestNeighbor;
                g.DrawImage(leida, new Rectangle(0, 0, b.Width, b.Height), 0, 0, leida.Width, leida.Height, GraphicsUnit.Pixel);
            }
            return b;
        }
    }

    /** Comandos ESC/POS: la imagen en bandas, con avance y corte entre copias (igual que EscPos.kt). */
    public static byte[] Trabajo(Bitmap b, int copias)
    {
        int w = b.Width, h = b.Height, bytesFila = (w + 7) / 8;
        Rectangle r = new Rectangle(0, 0, w, h);
        BitmapData d = b.LockBits(r, ImageLockMode.ReadOnly, PixelFormat.Format32bppArgb);
        int[] px = new int[w * h];
        Marshal.Copy(d.Scan0, px, 0, px.Length);
        b.UnlockBits(d);

        MemoryStream img = new MemoryStream();
        for (int inicio = 0; inicio < h; inicio += 128)
        {
            int filas = Math.Min(128, h - inicio);
            img.Write(new byte[] { 0x1d, 0x76, 0x30, 0, (byte)(bytesFila & 0xff), (byte)(bytesFila >> 8), (byte)(filas & 0xff), (byte)(filas >> 8) }, 0, 8);
            for (int y = inicio; y < inicio + filas; y++)
                for (int bx = 0; bx < bytesFila; bx++)
                {
                    int v = 0;
                    for (int bit = 0; bit < 8; bit++)
                    {
                        int x = bx * 8 + bit;
                        if (x < w && (px[y * w + x] & 0xff) < 128) v |= 0x80 >> bit;
                    }
                    img.WriteByte((byte)v);
                }
        }
        byte[] imagen = img.ToArray();
        MemoryStream o = new MemoryStream();
        o.Write(new byte[] { 0x1b, 0x40, 0x1b, 0x20, 0, 0x1b, 0x61, 0 }, 0, 8);
        int n = Math.Max(1, Math.Min(20, copias));
        for (int i = 1; i <= n; i++)
        {
            o.Write(imagen, 0, imagen.Length);
            if (i < n) o.Write(new byte[] { 0x1b, 0x64, 5, 0x1d, 0x56, 66, 0 }, 0, 7);
        }
        o.Write(new byte[] { 0x0a, 0x0a, 0x0a, 0x0a }, 0, 4);
        return o.ToArray();
    }
}

/** Código de barras Code 128 (B para texto, C para números de largo par). */
public static class Code128
{
    private static readonly string[] Patrones = {
        "212222","222122","222221","121223","121322","131222","122213","122312","132212","221213",
        "221312","231212","112232","122132","122231","113222","123122","123221","223211","221132",
        "221231","213212","223112","312131","311222","321122","321221","312212","322112","322211",
        "212123","212321","232121","111323","131123","131321","112313","132113","132311","211313",
        "231113","231311","112133","112331","132131","113123","113321","133121","313121","211331",
        "231131","213113","213311","213131","311123","311321","331121","312113","312311","332111",
        "314111","221411","431111","111224","111422","121124","121421","141122","141221","112214",
        "112412","122114","122411","142112","142211","241211","221114","413111","241112","134111",
        "111242","121142","121241","114212","124112","124211","411212","421112","421211","212141",
        "214121","412121","111143","111341","131141","114113","114311","411113","411311","113141",
        "114131","311141","411131","211412","211214","211232","2331112" };

    /** Anchos de barras y espacios alternados, empezando por barra. */
    public static List<int> Codificar(string texto)
    {
        if (string.IsNullOrEmpty(texto)) throw new ArgumentException("vacío");
        // Sin tildes ni caracteres fuera de ASCII.
        string limpio = QuitarTildes(texto);
        List<int> valores = new List<int>();
        bool soloNumeros = limpio.Length % 2 == 0 && Regex.IsMatch(limpio, "^[0-9]+$");
        if (soloNumeros)
        {
            valores.Add(105);
            for (int i = 0; i < limpio.Length; i += 2) valores.Add(int.Parse(limpio.Substring(i, 2), CultureInfo.InvariantCulture));
        }
        else
        {
            valores.Add(104);
            foreach (char c in limpio)
            {
                if (c < 32 || c > 126) throw new ArgumentException("carácter no válido");
                valores.Add(c - 32);
            }
        }
        int suma = valores[0];
        for (int i = 1; i < valores.Count; i++) suma += valores[i] * i;
        valores.Add(suma % 103);
        valores.Add(106);
        List<int> anchos = new List<int>();
        foreach (int v in valores) foreach (char c in Patrones[v]) anchos.Add(c - '0');
        return anchos;
    }

    private static string QuitarTildes(string s)
    {
        string d = s.Normalize(NormalizationForm.FormD);
        StringBuilder sb = new StringBuilder();
        foreach (char c in d) if (CharUnicodeInfo.GetUnicodeCategory(c) != UnicodeCategory.NonSpacingMark) sb.Append(c);
        return sb.ToString();
    }
}

/** Código QR (modo byte, UTF-8), según la norma ISO 18004. */
public static class CodigoQr
{
    // Por nivel de corrección (L, M, Q, H) y versión 1..40.
    private static readonly int[,] EccPorBloque = {
        { -1, 7, 10, 15, 20, 26, 18, 20, 24, 30, 18, 20, 24, 26, 30, 22, 24, 28, 30, 28, 28, 28, 28, 30, 30, 26, 28, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30 },
        { -1, 10, 16, 26, 18, 24, 16, 18, 22, 22, 26, 30, 22, 22, 24, 24, 28, 28, 26, 26, 26, 26, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28 },
        { -1, 13, 22, 18, 26, 18, 24, 18, 22, 20, 24, 28, 26, 24, 20, 30, 24, 28, 28, 26, 30, 28, 30, 30, 30, 30, 28, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30 },
        { -1, 17, 28, 22, 16, 22, 28, 26, 26, 24, 28, 24, 28, 22, 24, 24, 30, 28, 28, 26, 28, 30, 24, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30 },
    };
    private static readonly int[,] Bloques = {
        { -1, 1, 1, 1, 1, 1, 2, 2, 2, 2, 4, 4, 4, 4, 4, 6, 6, 6, 6, 7, 8, 8, 9, 9, 10, 12, 12, 12, 13, 14, 15, 16, 17, 18, 19, 19, 20, 21, 22, 24, 25 },
        { -1, 1, 1, 1, 2, 2, 4, 4, 4, 5, 5, 5, 8, 9, 9, 10, 10, 11, 13, 14, 16, 17, 17, 18, 20, 21, 23, 25, 26, 28, 29, 31, 33, 35, 37, 38, 40, 43, 45, 47, 49 },
        { -1, 1, 1, 2, 2, 4, 4, 6, 6, 8, 8, 8, 10, 12, 16, 12, 17, 16, 18, 21, 20, 23, 23, 25, 27, 29, 34, 34, 35, 38, 40, 43, 45, 48, 51, 53, 56, 59, 62, 65, 68 },
        { -1, 1, 1, 2, 4, 4, 4, 5, 6, 8, 8, 11, 11, 16, 16, 18, 16, 19, 21, 25, 25, 25, 34, 30, 32, 35, 37, 40, 42, 45, 48, 51, 54, 57, 60, 63, 66, 70, 74, 77, 81 },
    };
    private static readonly int[] BitsFormato = { 1, 0, 3, 2 }; // L, M, Q, H

    /** [y, x] = true es negro. nivel: 0 = L, 1 = M, 2 = Q, 3 = H. */
    public static bool[,] Crear(string texto, int nivel)
    {
        byte[] datos = Encoding.UTF8.GetBytes(texto);
        int version = 1;
        for (; version <= 40; version++)
        {
            int bits = 4 + (version <= 9 ? 8 : 16) + datos.Length * 8;
            if (bits <= CodewordsDatos(version, nivel) * 8) break;
        }
        if (version > 40) throw new ArgumentException("Texto muy largo para un QR");

        // Bits de datos.
        List<bool> bb = new List<bool>();
        Agregar(bb, 4, 4);
        Agregar(bb, datos.Length, version <= 9 ? 8 : 16);
        foreach (byte b in datos) Agregar(bb, b, 8);
        int capacidad = CodewordsDatos(version, nivel) * 8;
        Agregar(bb, 0, Math.Min(4, capacidad - bb.Count));
        Agregar(bb, 0, (8 - bb.Count % 8) % 8);
        for (int pad = 0xEC; bb.Count < capacidad; pad ^= 0xEC ^ 0x11) Agregar(bb, pad, 8);
        byte[] codewords = new byte[bb.Count / 8];
        for (int i = 0; i < bb.Count; i++) if (bb[i]) codewords[i >> 3] |= (byte)(1 << (7 - (i & 7)));

        byte[] todo = AgregarCorreccion(codewords, version, nivel);
        int n = version * 4 + 17;
        bool[,] modulos = new bool[n, n];
        bool[,] funcion = new bool[n, n];
        DibujarFunciones(modulos, funcion, version, nivel);
        DibujarCodewords(modulos, funcion, todo);

        // Elegir la máscara con menos penalización.
        int mejor = 0;
        long minimo = long.MaxValue;
        for (int mask = 0; mask < 8; mask++)
        {
            Mascara(modulos, funcion, mask);
            DibujarFormato(modulos, funcion, nivel, mask);
            long p = Penalizacion(modulos);
            if (p < minimo) { minimo = p; mejor = mask; }
            Mascara(modulos, funcion, mask); // deshacer (XOR)
        }
        Mascara(modulos, funcion, mejor);
        DibujarFormato(modulos, funcion, nivel, mejor);
        return modulos;
    }

    private static void Agregar(List<bool> bb, int valor, int largo)
    {
        for (int i = largo - 1; i >= 0; i--) bb.Add(((valor >> i) & 1) != 0);
    }

    private static int ModulosDatos(int ver)
    {
        int r = (16 * ver + 128) * ver + 64;
        if (ver >= 2)
        {
            int numAlign = ver / 7 + 2;
            r -= (25 * numAlign - 10) * numAlign - 55;
            if (ver >= 7) r -= 36;
        }
        return r;
    }

    private static int CodewordsDatos(int ver, int nivel)
    {
        return ModulosDatos(ver) / 8 - EccPorBloque[nivel, ver] * Bloques[nivel, ver];
    }

    private static byte[] AgregarCorreccion(byte[] datos, int ver, int nivel)
    {
        int numBloques = Bloques[nivel, ver];
        int eccLen = EccPorBloque[nivel, ver];
        int crudos = ModulosDatos(ver) / 8;
        int cortos = numBloques - crudos % numBloques;
        int largoCorto = crudos / numBloques;
        byte[] divisor = Divisor(eccLen);
        List<byte[]> bloques = new List<byte[]>();
        for (int i = 0, k = 0; i < numBloques; i++)
        {
            int largo = largoCorto - eccLen + (i < cortos ? 0 : 1);
            byte[] dat = new byte[largo];
            Array.Copy(datos, k, dat, 0, largo);
            k += largo;
            byte[] ecc = Resto(dat, divisor);
            byte[] bloque = new byte[largoCorto + 1];
            Array.Copy(dat, 0, bloque, 0, largo);
            // Los bloques cortos llevan un hueco para que la intercalación cuadre.
            Array.Copy(ecc, 0, bloque, largoCorto + 1 - eccLen, eccLen);
            bloques.Add(bloque);
        }
        List<byte> r = new List<byte>();
        for (int i = 0; i < largoCorto + 1; i++)
            for (int j = 0; j < bloques.Count; j++)
                if (i != largoCorto - eccLen || j >= cortos) r.Add(bloques[j][i]);
        return r.ToArray();
    }

    private static int Mult(int x, int y)
    {
        int z = 0;
        for (int i = 7; i >= 0; i--)
        {
            z = (z << 1) ^ ((z >> 7) * 0x11D);
            z ^= ((y >> i) & 1) * x;
        }
        return z;
    }

    private static byte[] Divisor(int grado)
    {
        byte[] r = new byte[grado];
        r[grado - 1] = 1;
        int raiz = 1;
        for (int i = 0; i < grado; i++)
        {
            for (int j = 0; j < grado; j++)
            {
                r[j] = (byte)Mult(r[j], raiz);
                if (j + 1 < grado) r[j] ^= r[j + 1];
            }
            raiz = Mult(raiz, 0x02);
        }
        return r;
    }

    private static byte[] Resto(byte[] datos, byte[] divisor)
    {
        byte[] r = new byte[divisor.Length];
        foreach (byte b in datos)
        {
            int factor = b ^ r[0];
            Array.Copy(r, 1, r, 0, r.Length - 1);
            r[r.Length - 1] = 0;
            for (int i = 0; i < r.Length; i++) r[i] ^= (byte)Mult(divisor[i], factor);
        }
        return r;
    }

    private static void Poner(bool[,] m, bool[,] f, int x, int y, bool negro) { m[y, x] = negro; f[y, x] = true; }

    private static void DibujarFunciones(bool[,] m, bool[,] f, int ver, int nivel)
    {
        int n = m.GetLength(0);
        for (int i = 0; i < n; i++) { Poner(m, f, 6, i, i % 2 == 0); Poner(m, f, i, 6, i % 2 == 0); }
        Ojo(m, f, 3, 3); Ojo(m, f, n - 4, 3); Ojo(m, f, 3, n - 4);
        int[] pos = PosicionesAlineacion(ver);
        for (int i = 0; i < pos.Length; i++)
            for (int j = 0; j < pos.Length; j++)
            {
                if ((i == 0 && j == 0) || (i == 0 && j == pos.Length - 1) || (i == pos.Length - 1 && j == 0)) continue;
                for (int dy = -2; dy <= 2; dy++)
                    for (int dx = -2; dx <= 2; dx++)
                        Poner(m, f, pos[i] + dx, pos[j] + dy, Math.Max(Math.Abs(dx), Math.Abs(dy)) != 1);
            }
        DibujarFormato(m, f, nivel, 0);
        if (ver >= 7)
        {
            int rem = ver;
            for (int i = 0; i < 12; i++) rem = (rem << 1) ^ ((rem >> 11) * 0x1F25);
            int bits = ver << 12 | rem;
            for (int i = 0; i < 18; i++)
            {
                bool bit = ((bits >> i) & 1) != 0;
                int a = n - 11 + i % 3, b = i / 3;
                Poner(m, f, a, b, bit);
                Poner(m, f, b, a, bit);
            }
        }
    }

    private static void Ojo(bool[,] m, bool[,] f, int x, int y)
    {
        int n = m.GetLength(0);
        for (int dy = -4; dy <= 4; dy++)
            for (int dx = -4; dx <= 4; dx++)
            {
                int dist = Math.Max(Math.Abs(dx), Math.Abs(dy));
                int xx = x + dx, yy = y + dy;
                if (xx >= 0 && xx < n && yy >= 0 && yy < n) Poner(m, f, xx, yy, dist != 2 && dist != 4);
            }
    }

    private static int[] PosicionesAlineacion(int ver)
    {
        if (ver == 1) return new int[0];
        int num = ver / 7 + 2;
        int paso = ver == 32 ? 26 : (ver * 4 + num * 2 + 1) / (num * 2 - 2) * 2;
        int[] r = new int[num];
        r[0] = 6;
        for (int i = num - 1, p = ver * 4 + 10; i >= 1; i--, p -= paso) r[i] = p;
        return r;
    }

    private static void DibujarFormato(bool[,] m, bool[,] f, int nivel, int mask)
    {
        int n = m.GetLength(0);
        int datos = BitsFormato[nivel] << 3 | mask;
        int rem = datos;
        for (int i = 0; i < 10; i++) rem = (rem << 1) ^ ((rem >> 9) * 0x537);
        int bits = (datos << 10 | rem) ^ 0x5412;
        for (int i = 0; i <= 5; i++) Poner(m, f, 8, i, Bit(bits, i));
        Poner(m, f, 8, 7, Bit(bits, 6));
        Poner(m, f, 8, 8, Bit(bits, 7));
        Poner(m, f, 7, 8, Bit(bits, 8));
        for (int i = 9; i < 15; i++) Poner(m, f, 14 - i, 8, Bit(bits, i));
        for (int i = 0; i < 8; i++) Poner(m, f, n - 1 - i, 8, Bit(bits, i));
        for (int i = 8; i < 15; i++) Poner(m, f, 8, n - 15 + i, Bit(bits, i));
        Poner(m, f, 8, n - 8, true);
    }

    private static bool Bit(int x, int i) { return ((x >> i) & 1) != 0; }

    private static void DibujarCodewords(bool[,] m, bool[,] f, byte[] datos)
    {
        int n = m.GetLength(0);
        int i = 0;
        for (int derecha = n - 1; derecha >= 1; derecha -= 2)
        {
            if (derecha == 6) derecha = 5;
            for (int vert = 0; vert < n; vert++)
                for (int j = 0; j < 2; j++)
                {
                    int x = derecha - j;
                    bool subiendo = ((derecha + 1) & 2) == 0;
                    int y = subiendo ? n - 1 - vert : vert;
                    if (!f[y, x] && i < datos.Length * 8)
                    {
                        m[y, x] = Bit(datos[i >> 3], 7 - (i & 7));
                        i++;
                    }
                }
        }
    }

    private static void Mascara(bool[,] m, bool[,] f, int mask)
    {
        int n = m.GetLength(0);
        for (int y = 0; y < n; y++)
            for (int x = 0; x < n; x++)
            {
                bool inv;
                switch (mask)
                {
                    case 0: inv = (x + y) % 2 == 0; break;
                    case 1: inv = y % 2 == 0; break;
                    case 2: inv = x % 3 == 0; break;
                    case 3: inv = (x + y) % 3 == 0; break;
                    case 4: inv = (x / 3 + y / 2) % 2 == 0; break;
                    case 5: inv = x * y % 2 + x * y % 3 == 0; break;
                    case 6: inv = (x * y % 2 + x * y % 3) % 2 == 0; break;
                    default: inv = ((x + y) % 2 + x * y % 3) % 2 == 0; break;
                }
                if (inv && !f[y, x]) m[y, x] = !m[y, x];
            }
    }

    private static long Penalizacion(bool[,] m)
    {
        int n = m.GetLength(0);
        long p = 0;
        // 1) Rachas de 5 o más del mismo color, en filas y columnas.
        for (int dir = 0; dir < 2; dir++)
            for (int a = 0; a < n; a++)
            {
                int racha = 1;
                for (int b = 1; b < n; b++)
                {
                    bool actual = dir == 0 ? m[a, b] : m[b, a];
                    bool previo = dir == 0 ? m[a, b - 1] : m[b - 1, a];
                    if (actual == previo) racha++;
                    else { if (racha >= 5) p += 3 + racha - 5; racha = 1; }
                }
                if (racha >= 5) p += 3 + racha - 5;
            }
        // 2) Bloques de 2x2 del mismo color.
        for (int y = 0; y < n - 1; y++)
            for (int x = 0; x < n - 1; x++)
                if (m[y, x] == m[y, x + 1] && m[y, x] == m[y + 1, x] && m[y, x] == m[y + 1, x + 1]) p += 3;
        // 3) Patrones parecidos a los cuadros de las esquinas.
        bool[] patron = { true, false, true, true, true, false, true, false, false, false, false };
        for (int dir = 0; dir < 2; dir++)
            for (int a = 0; a < n; a++)
                for (int b = 0; b + 11 <= n; b++)
                {
                    bool igual = true, inverso = true;
                    for (int k = 0; k < 11; k++)
                    {
                        bool v = dir == 0 ? m[a, b + k] : m[b + k, a];
                        if (v != patron[k]) igual = false;
                        if (v != patron[10 - k]) inverso = false;
                    }
                    if (igual) p += 40;
                    if (inverso) p += 40;
                }
        // 4) Proporción de negros lejos del 50%.
        int negros = 0;
        foreach (bool v in m) if (v) negros++;
        int total = n * n;
        int k5 = (Math.Abs(negros * 20 - total * 10) + total - 1) / total - 1;
        p += Math.Max(0, k5) * 10;
        return p;
    }
}
