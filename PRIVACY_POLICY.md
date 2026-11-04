# Política de privacidad — AeroLaunch

*Última actualización: [FECHA]* · *Responsable: [NOMBRE / EMPRESA] · Contacto: [EMAIL]*

> Plantilla: completá los datos entre corchetes y publicala en una URL pública (Play Console la exige aunque la app no recopile datos).

## Resumen

AeroLaunch **no recopila, no transmite y no comparte ningún dato personal**. La app no tiene acceso a Internet, no incluye publicidad, analíticas, cuentas ni SDKs de terceros.

## Qué información procesa la app (siempre en tu dispositivo)

| Información | Para qué | Dónde se guarda |
|---|---|---|
| Lista de aplicaciones instaladas que se pueden abrir | Mostrarte la grilla de apps | Solo en memoria, se consulta al sistema en cada inicio; no se guarda ni se envía |
| Orden elegido, apps favoritas y apps ocultas | Recordar tus preferencias | Almacenamiento privado de la app en el dispositivo |
| Contador de aperturas y fecha de última apertura de cada app (solo las abiertas desde este launcher) | Ordenar por "Más usadas" / "Recientes" | Almacenamiento privado de la app en el dispositivo |

Esta información **nunca sale del dispositivo**, no se incluye en copias de seguridad y se elimina al desinstalar la app. Podés borrar el historial de uso en cualquier momento desde *Ajustes → Borrar historial de uso*.

## Permisos y Accesibilidad (AccessibilityService API)

La app no solicita ningún permiso estándar del sistema (no usa `INTERNET`, almacenamiento, ubicación, micrófono ni cámara).

### Uso del Servicio de Accesibilidad
Para habilitar la función de **"Mapeo de botones del control"** (que te permite asignar acciones como abrir apps o volver al inicio al presionar botones específicos del control remoto físico), AeroLaunch utiliza la **API de Servicio de Accesibilidad** (`AccessibilityService`).
*   **¿Para qué se usa?**: El servicio se usa exclusivamente para escuchar los eventos físicos del control remoto (las teclas presionadas) y ejecutar la acción de navegación que vos hayas configurado (abrir una app o volver al inicio).
*   **¿Qué datos recopila o comparte?**: El Servicio de Accesibilidad **no recopila, no almacena, no graba y no comparte** ninguna información personal, textos introducidos ni el contenido que aparece en tu pantalla. El servicio se limita a detectar pulsaciones de hardware de tu control remoto de forma completamente local y anónima.

## Terceros

No hay terceros: la app no se comunica con ningún servidor.

## Niños

La app no está dirigida a niños ni recopila información de nadie, incluidos menores.

## Cambios

Si esta política cambia, se publicará la nueva versión en esta misma dirección con la fecha de actualización.

## Contacto

[EMAIL]

---

# Privacy Policy — AeroLaunch (English summary)

AeroLaunch does **not collect, transmit or share any personal data**. It has no Internet access, ads, analytics, accounts or third-party SDKs. The list of launchable apps is read from the system on-device only to display it; your sort choice, favorites, hidden apps and local launch counters are stored in the app's private storage, excluded from backups, never leave the device, and are deleted on uninstall.

**AccessibilityService API usage:** The app optionally uses the AccessibilityService API strictly to enable the "Remote button mapping" feature. When enabled, this service only intercepts hardware remote control button presses to launch custom apps or go home as configured by you. It **does not collect, store or share** any personal data, typed text or screen content. The service operates 100% locally on the device.

Contact: [EMAIL].
