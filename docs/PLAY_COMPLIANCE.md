# Cumplimiento Google Play — TV Launcher

Qué ya cumple el código, qué verificar y qué falta hacer en Play Console. Las políticas cambian: **antes de publicar, revisá el Policy Center** (https://play.google.com/about/developer-content-policy/) y la página de requisitos de Android TV.

## 1. Estado del código

| Requisito | Estado |
|---|---|
| Target API | `compileSdk = 36`, `targetSdk = 36` (Android 16). Obligatorio para apps nuevas y actualizaciones desde el 31/08/2026 |
| Formato | Se publica como **AAB** (`bundleRelease`); sin código nativo → no hay requisito de 64 bits que atender |
| Permisos | **Ninguno sensible**. Sin `INTERNET`, `QUERY_ALL_PACKAGES` (se usa `<queries>` limitado), ni `PACKAGE_USAGE_STATS`. |
| Accesibilidad | **TvKeyMappingService** (opcional, lo activa el usuario): configuración mínima (solo `flagRequestFilterKeyEvents`, sin eventos de accesibilidad, `canRetrieveWindowContent=false`, `isAccessibilityTool=false`), sin logs, sin `directBootAware`. **Aviso destacado in-app** con consentimiento explícito (`AccessibilityDisclosureDialog`) antes de abrir Ajustes de accesibilidad. Justificación también en la Política de Privacidad. |
| Visibilidad de paquetes | `<queries>` solo para apps con intent `LAUNCHER` / `LEANBACK_LAUNCHER` (evita `QUERY_ALL_PACKAGES`, restringido por Play) |
| Datos del usuario | Nada se recopila ni se envía; todo local y fuera de backup (`allowBackup=false`, reglas de extracción vacías) |
| Red | Sin permiso de Internet, `usesCleartextTraffic=false` |
| Android TV | `leanback` requerido, `touchscreen` no requerido, banner 320×180, filtro `LEANBACK_LAUNCHER`, navegación 100 % D‑pad, márgenes de overscan |
| Launcher | Filtro `HOME`+`DEFAULT`; el usuario elige el launcher (no se fuerza ni se cambia ningún ajuste del sistema) |
| Seguridad | R8 + recorte de recursos, sin logs en release, `debuggable=false`, 1 sola Activity exportada, intents explícitos con captura de `SecurityException`, Gradle Wrapper con SHA‑256 |
| Anuncios / compras | Ninguno |

## 2. Hacer en Play Console

1. **Política de privacidad**: 
   - Publicar el contenido de `PRIVACY_POLICY.md` (completando email y nombre) en una URL pública (por ejemplo en Google Sites o GitHub Pages). 
   - **IMPORTANTE:** La sección de *Accesibilidad (AccessibilityService)* es obligatoria para evitar el rechazo.
2. **Seguridad de los datos (Data safety)**: 
   - "¿La app recopila o comparte datos?": **No**
   - Cifrado en tránsito: No aplica (no tiene internet)
3. **App access**: "Toda la funcionalidad está disponible sin acceso especial".
4. **Ads**: La aplicación **No contiene anuncios**.
5. **Target audience**: 13+ / adultos. Marcar explícitamente que *no* está orientada a niños (para evitar los rigurosos requisitos del programa Designed for Families).
6. **Contenido de la aplicación > Permisos de la API de accesibilidad** (obligatorio): declarar el uso de `AccessibilityService`, que **no** es una herramienta de accesibilidad, que no recopila datos y que sirve para remapear botones del control. Adjuntar un **vídeo** (YouTube no listado) que muestre: Ajustes › Mapeo de botones → aviso destacado → Acepto → activar el servicio → pulsar el botón mapeado y ver la acción.
7. **Android TV**: 
   - Activar el "factor de forma TV" en la configuración avanzada.
   - Subir al menos 2 capturas de 1920x1080 (solo capturas de la UI, sin bordes de televisores ni logos de terceros como Netflix o Disney para evitar rechazos por Copyright).
   - Icono 512x512 y Banner TV 320x180 (o 1280x720).
8. **Firma de la App (Play App Signing)**: 
   - Crear un archivo `keystore.jks` y registrarlo en `keystore.properties` (que ya está excluido en git) en la raíz:
   ```
   storeFile=../keys/upload.jks
   storePassword=...
   keyAlias=upload
   keyPassword=...
   ```
   `bundleRelease` firma automáticamente si existe. Si no existe, el AAB sale **sin firmar** (ya no se usa la clave de debug como respaldo). Guardar la clave `.jks` de subida en un lugar seguro (y con copia de seguridad).
9. **Pruebas cerradas (Closed Testing)**: Para cuentas nuevas de desarrollador, Google Play exige 20 testers internos durante 14 días.
10. **ApplicationId ÚNICO**: Cambiar `com.tvlauncher.app` (en `app/build.gradle.kts`) por un nombre de paquete único (ej. `com.tunombre.tvlauncher`) **antes** de subir el primer AAB. No se puede modificar después.

## 3. Puntos a verificar por el usuario
- **Logos con Copyright en las Capturas**: Cuando tomes capturas de pantalla para la Play Store, **NO muestres logos de apps con copyright** (como Netflix, YouTube, Prime, etc.) porque los bots de Google rechazarán la app por Propiedad Intelectual. Utiliza las 20 apps de prueba generadas en `:dummyapps` para tomar unas capturas de pantalla 100% libres de derechos de autor.
- Texto vigente de la política **"Inventario de apps instaladas"**: la app lee la lista de apps lanzables solo en el dispositivo, solo para mostrarla, y lo declara en la política de privacidad.
- Revisión de calidad de TV: foco visible siempre (garantizado por Compose `tv-material`), Atrás/Home coherentes, sin elementos que requieran interfaz táctil (garantizado).

## 4. Mantenimiento y Seguridad
- Revisar dependencias cada semestre (`./gradlew lintRelease` avisa de versiones nuevas).
- El Target SDK deberá subirse a 37 cuando Play lo exija (habitualmente agosto de cada año).
- No agregar el permiso `INTERNET` ni SDKs de terceros de analíticas; mantendrá el "Data Safety" limpio y facilitará la aprobación futura.
