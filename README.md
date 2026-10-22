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

## Compilar

- Abra la carpeta en Android Studio y presione ▶️, o
- `./gradlew assembleDebug` → `app/build/outputs/apk/debug/app-debug.apk`
- Cada push a GitHub compila el APK en *Actions → Compilar APK → Artifacts*.

Pruebas: `./gradlew testDebugUnitTest`
