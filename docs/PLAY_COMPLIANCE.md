# Cumplimiento Google Play — TV Launcher

Qué ya cumple el código, qué verificar y qué falta hacer en Play Console. Las políticas cambian: **antes de publicar, revisá el Policy Center** (https://play.google.com/about/developer-content-policy/) y la página de requisitos de Android TV.

## 1. Estado del código

| Requisito | Estado |
|---|---|
| Target API | `targetSdk = 36`. Play Help indica que desde el 31/08/2026 las apps nuevas y actualizaciones deben apuntar a API 36 (para Android TV el mínimo publicado es API 34); 36 cumple ambos |
| Formato | Se publica como **AAB** (`bundleRelease`); sin código nativo → no hay requisito de 64 bits que atender |
| Permisos | **Ninguno**. Sin `INTERNET`, `QUERY_ALL_PACKAGES`, `PACKAGE_USAGE_STATS`, `REQUEST_DELETE_PACKAGES` → no hay formularios de declaración de permisos sensibles |
| Visibilidad de paquetes | `<queries>` solo para apps con intent `LAUNCHER` / `LEANBACK_LAUNCHER` (evita `QUERY_ALL_PACKAGES`, restringido por Play) |
| Datos del usuario | Nada se recopila ni se envía; todo local y fuera de backup (`allowBackup=false`, reglas de extracción vacías) |
| Red | Sin permiso de Internet, `usesCleartextTraffic=false` |
| Android TV | `leanback` requerido, `touchscreen` no requerido, banner 320×180, filtro `LEANBACK_LAUNCHER`, navegación 100 % D‑pad, márgenes de overscan |
| Launcher | Filtro `HOME`+`DEFAULT`; el usuario elige el launcher (no se fuerza ni se cambia ningún ajuste del sistema) |
| Seguridad | R8 + recorte de recursos, sin logs en release, `debuggable=false`, 1 sola Activity exportada, intents explícitos con captura de `SecurityException`, Gradle Wrapper con SHA‑256 |
| Anuncios / compras | Ninguno |

## 2. Hacer en Play Console

1. **Política de privacidad**: publicar `PRIVACY_POLICY.md` (completar corchetes) en una URL pública y enlazarla.
2. **Seguridad de los datos (Data safety)**: "No recopila datos" / "No comparte datos". Cifrado en tránsito: no aplica (sin red). Eliminación de datos: se borran al desinstalar o con "Borrar historial de uso".
3. **App access**: sin login. **Ads**: no contiene anuncios. **Target audience**: 13+ / adultos, *no* orientada a niños (evita requisitos de Families).
4. **Clasificación de contenido** (cuestionario IARC) y categoría de la app.
5. **Android TV**: activar el factor de forma TV, subir capturas 1920×1080 (≥ 2, sin marcos de dispositivo ni contenido de terceros con derechos), banner 320×180 / 1280×720, icono 512×512 y gráfico de funciones 1024×500.
6. **Firma**: usar **Play App Signing**. Generar una clave de subida y crear `keystore.properties` (ignorado por git) en la raíz:
   ```
   storeFile=../keys/upload.jks
   storePassword=...
   keyAlias=upload
   keyPassword=...
   ```
   `bundleRelease` firma automáticamente si existe. Guardar la clave **fuera** del repo y con copia de seguridad.
7. **Pruebas**: crear pista de prueba interna/cerrada y revisar el *pre-launch report*. Cuentas personales nuevas: Play exige un período de prueba cerrada antes de producción (verificar requisito vigente).
8. **applicationId**: cambiar `com.tvlauncher.app` por uno propio (p. ej. dominio que controles) **antes** de la primera subida; no se puede cambiar después.

## 3. Puntos a verificar (no confirmados en esta sesión)

- Texto vigente de la política **"Inventario de apps instaladas"**: la app lee la lista de apps lanzables solo en el dispositivo, solo para mostrarla, y lo declara en la política de privacidad. Confirmar que no exige una declaración adicional para launchers.
- Comportamiento de **Google TV certificados**: pueden limitar el reemplazo del launcher de fábrica; en tvbox AOSP/ATV genéricos se elige desde el diálogo de Home. No es un problema de política, pero afecta la experiencia en algunos equipos.
- Revisión de calidad de TV (https://developer.android.com/docs/quality-guidelines/tv-app-quality): foco visible siempre, Atrás/Home coherentes, sin elementos que requieran táctil.

## 4. Mantenimiento de seguridad

- Revisar dependencias cada trimestre (`./gradlew lintRelease` avisa de versiones nuevas) y subir el `targetSdk` cada agosto.
- No agregar `INTERNET` ni SDKs de terceros sin actualizar Data safety y la política de privacidad.
- Nunca versionar `keystore.properties`, `*.jks` ni `local.properties`.
