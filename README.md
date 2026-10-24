# Cuadre de Caja

App Android (Kotlin + Jetpack Compose) para contar la caja en dólares estadounidenses
e imprimir el reporte en una impresora térmica Bluetooth de 58 mm.

## Cómo se usa

1. **Billetes**: aparecen $100, $50, $20, $10, $5, $2 y $1. Ponga cuántos tiene de cada uno
   (con el teclado o con − / +). Ejemplo: 5 de $20 = $100.00.
2. Regrese y toque **Monedas**: $1.00, 50¢, 25¢, 10¢, 5¢ y 1¢, igual que los billetes.
3. Toque **Cuadrar caja**: se muestra el reporte y se manda a imprimir solo.

El reporte trae: *CUADRE DE CAJA*, fecha, hora, el detalle de cada denominación
contada y el **TOTAL EN CAJA**.

Lo contado se guarda aunque se cierre la app. Para un conteo nuevo toque **Empezar de nuevo**.

## Modo impresión

Arriba en el inicio (o en los tres puntos **⋮**) se cambia entre **modo cuadre** (verde) y
**modo impresión** (índigo). En los tres puntos también están:

- **Nota de entrega**: se llenan los campos de la plantilla (establecimiento, quien retira,
  productos con cantidades...), se ve la vista previa y al imprimir pregunta **cuántas
  copias**: imprime una, corta, imprime la otra y corta.
- **Memoria**: al imprimir una nota se guarda lo escrito (nombres, establecimientos,
  productos). La próxima vez, al tocar el campo o escribir unas letras ("Salen"), aparece
  "Salentine vino tinto 750" para elegirlo. Con la ✕ se olvida uno; en Ajustes se borra todo.
- **Plantillas**: diseño de la nota. Elementos: texto (tipo de letra, tamaño, negrita,
  cursiva, subrayado, fondo negro, alineación), campos para llenar (en línea, apilados o
  dentro de celda, con borde de línea completa, puntillos, guiones o doble), tabla de
  productos (columnas, tipo de celdas, total, filas vacías), líneas, espacios, imágenes o
  logos, **código QR** (ligero, sencillo o espeso; cuadros o puntos) y **código de barras**
  (Code 128, Code 39, Code 93, EAN-13, EAN-8, UPC-A, ITF, Codabar) y firma. En textos, QR y
  códigos se puede usar `{Nombre del campo}`, `{fecha}` y `{hora}`.
- **Historial**: los últimos 5 tickets impresos, para verlos o reimprimirlos.
- **Ajustes**: ancho del papel (80 o 58 mm) e impresora guardada.

La nota se dibuja como imagen del ancho del papel, así que se imprime igual que la vista
previa.

## Impresora

- Funciona con impresoras térmicas portátiles Bluetooth que usan ESC/POS (PT-210, MTP-II,
  Goojprt, Xprinter y similares).
- La app la encuentra sola entre los dispositivos emparejados y la recuerda. Si no está
  emparejada o no puede saber cuál es, muestra **Buscar impresoras cercanas**: toque la
  impresora y la app la empareja (PIN 0000 o 1234 si lo pide) e imprime.
- Si en *Ajustes → Bluetooth* el teléfono la muestra como "acceso a Internet" o dice
  "no se pudo conectar", es normal: estas impresoras solo se emparejan, la conexión la
  hace la app al imprimir. Puede apagar "Acceso a Internet" en el engranaje del dispositivo.
- **Cambiar impresora** en la pantalla del reporte permite elegir otra.

## Imprimir en la computadora (impresora SAT u otra de Windows)

La carpeta [`windows/`](windows/) tiene un receptor que no se instala: se abre con doble clic
en `Iniciar receptor.bat`. Recibe el ticket del celular por Bluetooth (puerto COM entrante
de Windows) y lo imprime directo en la impresora, sin ventana de imprimir.
Los pasos están en [`windows/LEAME.txt`](windows/LEAME.txt).

## Compilar

- Abra la carpeta en Android Studio y presione ▶️, o
- `./gradlew assembleDebug` → `app/build/outputs/apk/debug/app-debug.apk`
- Cada push a GitHub compila el APK en *Actions → Compilar APK → Artifacts*.

Pruebas: `./gradlew testDebugUnitTest` (incluye pruebas con Robolectric que guardan imágenes
de las pantallas y de los tickets en `build/capturas/`).
