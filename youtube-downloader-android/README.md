# YouTube Downloader (Android, Java)

Pequeño proyecto de ejemplo que muestra cómo crear una app Android (Java) capaz de descargar videos de YouTube mediante la librería [youtube-dl-android](https://github.com/yausername/youtubedl-android).

> **Aviso:** Respeta los términos de uso de YouTube y descarga solo contenido que tengas permiso de guardar. La app solicita permisos de almacenamiento para guardar el archivo en la carpeta `Descargas`.

## Requisitos
- Android Studio Iguana o más reciente.
- JDK 17.
- Dispositivo o emulador con Android 7.0 (API 24) o superior.

## Cómo probar
1. Clona el repositorio y abre la carpeta `youtube-downloader-android` en Android Studio.
2. Android Studio descargará las dependencias, incluida `youtube-dl-android` (usa el repositorio JitPack definido en `settings.gradle`).
3. Conecta un dispositivo o inicia un emulador y ejecuta la app.
4. Escribe una URL de YouTube y toca **Descargar**. El video se guarda en `Descargas` con el formato `yt-<titulo>.<ext>`.

## Puntos clave del código
- **`app/build.gradle`**: declara dependencias y activa `viewBinding`.
- **`MainActivity.java`**: crea la interfaz y lanza la descarga en un hilo de fondo con `YoutubeDL`.
- **`AndroidManifest.xml`**: solicita permisos de red y almacenamiento.

Puedes personalizar el nombre del archivo o las opciones de `youtube-dl` modificando el `YoutubeDLRequest` en `MainActivity`.
