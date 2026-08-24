# PDF Run

Aplicación Android nativa para escanear documentos o cargar imágenes/TXT y convertirlos en PDF.

## Funciones

- Escáner documental real de Google ML Kit en modo completo.
- Detección de bordes, recorte, corrección de perspectiva y filtros.
- Escaneo de varias páginas y revisión antes de regresar a la aplicación.
- Carga múltiple de imágenes y archivos TXT.
- Vista previa individual y eliminación de páginas.
- Guardado visible en `Descargas/PDF`.
- Compartir el PDF desde Android.

## Compilar

Requiere JDK 17 y Android SDK 35.

```bash
./gradlew assembleDebug
```

La APK queda en `app/build/outputs/apk/debug/app-debug.apk`.

También se compila automáticamente con GitHub Actions en cada envío a `main`.
