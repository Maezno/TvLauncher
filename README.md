# TV Launcher

Launcher ligero y fluido para **tvbox y Android TV**, con estética inspirada en Apple TV, pensado para no tener *stuttering* ni en hardware modesto (2 GB de RAM, SoC de gama baja).

> Estado: **v0.1.0 – base funcional que compila** (debug, release con R8 y AAB verificados, lint sin errores). Falta probarlo en dispositivos reales y pulir arte (ver "Próximos pasos").

## Funciones

- Grilla de apps 16:9 (usa el **banner TV** de cada app; si no tiene, muestra el icono) con foco D‑pad: escala + borde blanco.
- **Orden**: por **Nombre** (alfabético sin distinguir mayúsculas/acentos), **Más usadas** o **Recientes**. El botón "Orden" del encabezado alterna entre los tres; también se elige en Ajustes.
- **Favoritas** (fila superior, reordenables), **ocultar apps**, info/desinstalar (vía pantalla de info del sistema).
- Mantené presionado OK sobre una app → menú contextual.
- Reloj/fecha (respeta 12/24 h del sistema), accesos a Ajustes del sistema y Red.
- Español e inglés.
- Botón Home / Atrás: vuelve al inicio y enfoca la primera tarjeta.

## Compilar

Requisitos: Android Studio reciente (o JDK 17/21) y SDK 36. **Gradle 8.13 no corre sobre JDK 25** (el JBR de Android Studio actual es 25): usá JDK 21 (por ejemplo `%USERPROFILE%\.jdks\jbr-21.0.11`) como *Gradle JDK* en Android Studio (Settings → Build Tools → Gradle).

```powershell
$env:JAVA_HOME = "$env:USERPROFILE\.jdks\jbr-21.0.11"
.\gradlew.bat assembleDebug            # APK de prueba
.\gradlew.bat lintRelease bundleRelease # AAB para Play (firmar, ver docs/PLAY_COMPLIANCE.md)
```

`local.properties` (con `sdk.dir`) se genera por máquina y está en `.gitignore`.

## Instalar en el tvbox

```powershell
adb connect <ip-del-tvbox>:5555
adb install -r app\build\outputs\apk\debug\app-debug.apk
```

Pulsá **Home** → elegí *TV Launcher* → "Siempre". Si el sistema (algunos Google TV certificados) no deja cambiar el launcher:

```powershell
adb shell cmd package set-home-activity com.tvlauncher.app.debug/com.tvlauncher.app.MainActivity
```

## Arquitectura (deliberadamente mínima)

```
app/src/main/java/com/tvlauncher/app/
  TvLauncherApp.kt        Application + AppContainer (DI manual, sin Hilt)
  MainActivity.kt         Única Activity (singleTask, HOME)
  data/AppRepository.kt   Lista de apps (PackageManager en IO, receiver de install/uninstall con debounce)
  data/IconLoader.kt      Decodificación de iconos/banners en hilos de fondo + caché LRU
  data/LauncherStore.kt   DataStore local: orden, favoritas, ocultas, contadores de uso
  ui/HomeViewModel.kt     Estado inmutable (combine + sort en Dispatchers.Default)
  ui/HomeScreen.kt        Grilla, encabezado, reloj
  ui/AppCard.kt           Tarjeta con foco estilo Apple TV
  ui/Dialogs.kt           Menú contextual y Ajustes (Dialog = foco atrapado)
  util/Launcher.kt        Intents explícitos
```

Dependencias: Compose (ui + foundation), **tv-material**, DataStore, lifecycle, coroutines, profileinstaller. Sin Room, sin Coil/Glide, sin Hilt, sin librería de iconos extendidos, sin red.

## Rendimiento: decisiones anti‑stutter

| Riesgo de stutter | Decisión |
|---|---|
| Decodificar iconos en el hilo de UI | Decodificación en 2 hilos de **prioridad BACKGROUND**; la UI solo hace `peek` síncrono de caché |
| Subida de textura a GPU en el primer dibujo | Bitmaps **HARDWARE** (API 26+) creados fuera del hilo de UI |
| Bitmaps gigantes / OOM en 1–2 GB de RAM | Tamaño fijo (banner 320×180, icono 160 px) + LRU acotado a 16–48 MB |
| Parpadeo de iconos al hacer scroll | **Precarga** en orden de pantalla al cargar; claves estables + `contentType` en la grilla |
| Jank de animaciones de foco | Escala vía `graphicsLayer` (solo fase de dibujo); **sin blur ni sombras difusas** |
| Recomposición global por el reloj | Reloj aislado, se actualiza 1 vez por minuto y se pausa si no está visible |
| Reordenar/ordenar en el hilo principal | `combine` + sort en `Dispatchers.Default`, estado `@Immutable` |
| Arranque lento / primer frame blanco | Sin trabajo en `onCreate`, tema oscuro desde el primer frame, sin recreación por cambios de configuración |
| Código sin compilar AOT al primer uso | **Baseline Profile** + `profileinstaller` + R8 full mode |
| Consumo en segundo plano | Cero servicios, cero jobs, cero red; el receptor de paquetes tiene debounce |

## "Más usadas" y privacidad

El uso se cuenta **dentro del propio launcher** (cada vez que lanzás una app desde acá) y se guarda localmente. No se pide `PACKAGE_USAGE_STATS` (permiso sensible que Play revisa con lupa y que obliga a un acceso especial del usuario). Contrapartida: no cuenta apps abiertas por otro camino (p. ej. un botón de la control remota). "Borrar historial de uso" está en Ajustes.

## Seguridad y privacidad (resumen; detalle en `docs/PLAY_COMPLIANCE.md`)

- **Cero permisos**, sin `INTERNET`, sin `QUERY_ALL_PACKAGES`: la visibilidad de paquetes se limita a apps lanzables con `<queries>`.
- Sin analíticas, anuncios, cuentas ni SDKs de terceros. Datos solo locales, excluidos de backup.
- `allowBackup=false`, sin tráfico en claro, solo una Activity exportada (necesaria para ser HOME), intents explícitos con manejo de `SecurityException`.
- Release con R8 + recorte de recursos + logs eliminados. Gradle Wrapper con checksum SHA‑256.

## Próximos pasos

1. **Probar en hardware real** (1080p y 4K, Android 9/11/14, 1–2 GB RAM) y medir con Perfetto / `gfxinfo` (frames > 16 ms).
2. Generar un **Baseline Profile medido** con un módulo Macrobenchmark (el actual es de reglas amplias).
3. Arte final: banner 320×180 (y 1280×720), icono, capturas TV 1920×1080 para la ficha.
4. Actualizar dependencias (lint reporta versiones nuevas: AGP 9.x, Compose BOM 2026.x, tv-material 1.1.0, etc.); se dejó un set verificado que compila. `targetSdk` 37 opcional.
5. Cambiar `applicationId` (hoy `com.tvlauncher.app`) antes de la primera subida a Play: es permanente.
6. Opcionales (todos locales y sin red): fondo de pantalla elegido por el usuario, filas por categoría, búsqueda, reordenar manual de todas las apps.
