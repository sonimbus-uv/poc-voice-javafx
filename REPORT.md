# REPORT — PoC cliente de voz JavaFX contra LiveKit (Sonimbus)

> Estado del documento: EN CURSO. Rellenado hasta el Nivel 2 (2026-10-04). Lo que sigue marcado
> "PENDIENTE" no es un resultado. Cada resultado apunta a un archivo de `evidence/`.

## 1. Resumen ejecutivo

PENDIENTE — se redacta al cerrar todos los niveles.

## 2. Entorno y versiones usadas

| Componente | Versión | Fuente de verificación |
|---|---|---|
| Sistema operativo | PENDIENTE — por volcar desde la evidencia del Nivel -1 | PENDIENTE |
| Java (JDK) | PENDIENTE — por volcar desde la evidencia del Nivel -1 | PENDIENTE |
| Maven | PENDIENTE — por volcar desde la evidencia del Nivel -1 | PENDIENTE |
| Docker / Compose | PENDIENTE — por volcar desde la evidencia del Nivel -1 | PENDIENTE |
| Servidor de audio (PipeWire/PulseAudio) | PENDIENTE — por volcar desde la evidencia del Nivel -1 | PENDIENTE |
| livekit/livekit-server (imagen) | v1.13.7, modo `--dev`, red host | `nivel0-verificacion-versiones-20260921.txt`, `nivel0-servidor-arranque-20260921.txt` |
| dev.onvoid.webrtc:webrtc-java | 0.18.0 (nativo linux-x86_64). El SDK comunitario fija 0.14.0; Maven resuelve 0.18.0 | `nivel1-dependencias-20261004.txt`, `nivel2-problemas-sdk-20261004.txt` (E) |
| JavaFX | javafx-controls 21.0.8 | `nivel2-dependencias-tamano-20261004.txt` |
| SDK comunitario Trirrin/livekit-java-sdk (rtc) | tag v0.1.4 vía JitPack (commit 0fa2340, 2025-12-06). Protocolo de señal 13, no 17 | `nivel2-problemas-sdk-20261004.txt` (A) |
| io.livekit:livekit-server (tokens) | 0.16.0 | `nivel0-verificacion-versiones-20260921.txt`, `nivel2-dependencias-tamano-20261004.txt` |
| Navegador usado en las pruebas | PENDIENTE — Nivel 3 aún no corrido | PENDIENTE |

## 3. Tabla de resultados por nivel

Valores posibles de Resultado: pasa / falla / parcial / no probado.

| Nivel | Criterio | Resultado | Evidencia (ruta en `evidence/`) |
|---|---|---|---|
| -1 Preflight | Java 21, Maven, Docker sin sudo, audio, permisos de micrófono, acceso a Maven Central y JitPack | PENDIENTE — por volcar desde la evidencia del Nivel -1 | PENDIENTE |
| 0 Servidor | LiveKit en Docker en modo dev; dos pestañas del navegador se oyen entre sí | pasa | `nivel0-servidor-arranque-20260921.txt`, `nivel0-observacion-usuario-20260921.txt` |
| 1 webrtc-java solo | Listar dispositivos, capturar micrófono, loopback con dos PeerConnection en la misma JVM; se oye. Anotar cancelación de eco y supresión de ruido | pasa (se oyó por altavoces, sin eco percibido; supresión de ruido sin evaluar) | `nivel1-loopback-tecnico-20261004.txt`, `nivel1-observacion-usuario-20261004.txt` |
| 2 JavaFX + SDK comunitario | Ventana (URL, sala, identidad, Conectar/Salir, Silenciar, participantes, log); tokens con clave dev; publica micrófono y reproduce audio remoto | pasa, con dos workarounds explícitos sobre el SDK (ver 4.3) | `nivel2-observacion-usuario-20261004.txt` (3), `nivel2-cliente-javafx-1-20261004-092228.txt`, `nivel2-servidor-prueba-audible-20261004.txt` |
| 3a | JavaFX habla y el navegador oye | PENDIENTE — Nivel 3 aún no corrido | PENDIENTE |
| 3b | El navegador habla y JavaFX oye | PENDIENTE — Nivel 3 aún no corrido | PENDIENTE |
| 3c | Silenciar/reactivar se refleja en ambos lados | PENDIENTE — Nivel 3 aún no corrido | PENDIENTE |
| 3d | Entradas/salidas de participantes | PENDIENTE — Nivel 3 aún no corrido | PENDIENTE |
| 3e | Cortar la red 10 s: reconecta o cómo falla | PENDIENTE — Nivel 3 aún no corrido | PENDIENTE |
| 3f | 10 min sin caídas ni fugas de memoria evidentes | PENDIENTE — Nivel 3 aún no corrido | PENDIENTE |
| 3g | Latencia (aplauso; RTT y jitter si hay estadísticas); objetivo < 400 ms en red local | PENDIENTE — Nivel 3 aún no corrido | PENDIENTE |
| 3h | Dos instancias JavaFX + un navegador a la vez | PENDIENTE — Nivel 3 aún no corrido | PENDIENTE |
| Extra | Natives empaquetados para Windows y macOS (solo disponibilidad, sin probar) | PENDIENTE — Extra aún no corrido | PENDIENTE |

## 4. Problemas encontrados y soluciones

Una subsección por problema, con el error exacto citado.

### 4.x Plantilla (copiar por cada problema)

- **Nivel:** PENDIENTE
- **Síntoma / error exacto:**

  ```
  PENDIENTE
  ```

- **Qué se probó:** PENDIENTE
- **Solución o estado final:** PENDIENTE
- **Evidencia:** PENDIENTE

### 4.1 Nivel -1 y Nivel 0

PENDIENTE — por volcar desde la evidencia de los Niveles -1 y 0.

### 4.2 Nivel 1

PENDIENTE — Nivel 1 aún no corrido.

### 4.3 Nivel 2

Detalle completo y citas del código del SDK: `evidence/nivel2-problemas-sdk-20261004.txt`.

#### 4.3.1 El micrófono no se publica: el SDK nunca envía la oferta del publisher

- **Síntoma:** el cliente dice "Micrófono publicado" pero el servidor se queda en `pending track added`; nunca
  aparece `mediaTrack published`. Sin ningún error visible en el cliente.
- **Causa (leída en el código):** el evento "negotiation needed" se consume al crear los data channels, antes de
  haber pistas, y `RtcClient.onPublisherNegotiationNeeded()` lo ignora; al añadir la pista no se repite.
- **Solución aplicada (workaround explícito):** llamar a `RtcClient.onPublisherNegotiationNeeded()` justo después
  de `publishAudioTrack()` (`VoiceClient.publishMicrophone`).
- **Evidencia:** `nivel2-headless-20261004.txt` (falla), `nivel2-headless-2-20261004.txt` (publica).

#### 4.3.2 Caída nativa (SIGSEGV) al salir

- **Error exacto:**

  ```
  SIGSEGV (0xb) at pc=0x00007275d5c598c6, pid=15620, tid=15683
  C  [libwebrtc-java-linux-x86_6414489899291519249994.so+0x6598c6]
  ```

- **Causa:** disponer la pista local después de `RtcClient.shutdown()` (factory ya liberada). El SDK no libera la
  pista local en `disconnect()`.
- **Qué se probó:** unpublish → shutdown → dispose = SIGSEGV; unpublish → dispose → shutdown = salida limpia.
- **Solución aplicada:** el segundo orden. Un orden incorrecto no lanza excepción: mata la JVM.
- **Evidencia:** `nivel2-crash-sigsegv-hs_err-20261004.log`, `nivel2-headless-20261004.txt`.

#### 4.3.3 Abierto: expulsión por identidad duplicada

- **Síntoma:** en la primera prueba el servidor expulsó a `javafx-1` con `DUPLICATE_IDENTITY` y el usuario reportó
  que algo "se bloqueó". No se guardó el log del cliente esa vez.
- **Estado:** sin reproducir. Sospecha: tras una salida iniciada por el servidor la ventana conserva la conexión
  antigua, y `RtcClient.onLeave` dispone la pista sin quitarla antes del sender. Se probará en el Nivel 3d.
- **Evidencia:** `nivel2-servidor-audio-bidireccional-20261004.txt`, `nivel2-observacion-usuario-20261004.txt` (2).

#### 4.3.4 Limitaciones del SDK vistas en el código (no son fallos reproducidos)

Sin configuración de cancelación de eco / supresión de ruido; sin acceso a `getStats()` (RTT/jitter); la elección
de micrófono por `deviceId` no afecta a la captura real y no hay API para elegir salida; `onError` vacío (errores
tragados); 1236 clases duplicadas entre el módulo `protocol` del SDK y `io.livekit:livekit-server`.
Evidencia: `nivel2-problemas-sdk-20261004.txt` (D, E).

### 4.4 Nivel 3

PENDIENTE — Nivel 3 aún no corrido.

## 5. Particularidades de Linux

### 5.1 Audio (PipeWire/PulseAudio)

PENDIENTE — por volcar desde la evidencia del Nivel -1; comportamiento con webrtc-java: Nivel 1 aún no corrido.

### 5.2 Permisos de micrófono

PENDIENTE — por volcar desde la evidencia del Nivel -1; comportamiento con webrtc-java: Nivel 1 aún no corrido.

### 5.3 Partición NTFS

PENDIENTE — efectos sobre compilación y carga de natives: Nivel 1 aún no corrido.

## 6. Tamaño y dependencias

| Elemento | Valor | Evidencia |
|---|---|---|
| JARs (lista y tamaño) | 41 JARs en el classpath de ejecución, 38 552 KiB (~37,6 MiB) en total, solo linux-x86_64. Los mayores: webrtc-java nativo 9,4 MiB, `protocol` del SDK 4,6 MiB, javafx-graphics 4,5 MiB, livekit-server 4,0 MiB | `nivel2-dependencias-tamano-20261004.txt` |
| Natives (por plataforma) | PENDIENTE — Nivel 1 aún no corrido | PENDIENTE |
| Librerías del sistema requeridas | PENDIENTE — Nivel 1 aún no corrido | PENDIENTE |
| Tamaño total del artefacto | PENDIENTE — Nivel 2 aún no corrido | PENDIENTE |

## 7. Riesgos abiertos

PENDIENTE — se redacta al cerrar todos los niveles.

## 8. Recomendación final (A/B/C/D)

Opciones: A) webrtc-java + SDK comunitario en JavaFX; B) voz en navegador embebido o del sistema;
C) voz solo en el móvil (Flutter) durante el MVP; D) cambiar el cliente de escritorio a Flutter.

- **Opción elegida:** PENDIENTE — se decide al cerrar todos los niveles.
- **Justificación (ligada a las filas de la sección 3):** PENDIENTE
- **Niveles que no se pudieron probar:** PENDIENTE
