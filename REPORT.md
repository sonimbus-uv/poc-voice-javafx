# REPORT — PoC cliente de voz JavaFX contra LiveKit (Sonimbus)

> Estado del documento: ESQUELETO. Todas las secciones son placeholders. Ningún dato de este
> archivo debe leerse como resultado hasta que sustituya a su "PENDIENTE" y apunte a un archivo
> de `evidence/`.

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
| livekit/livekit-server (imagen) | PENDIENTE — por volcar desde la evidencia del Nivel 0 | PENDIENTE |
| dev.onvoid.webrtc:webrtc-java | PENDIENTE — Nivel 1 aún no corrido | PENDIENTE |
| JavaFX | PENDIENTE — Nivel 2 aún no corrido | PENDIENTE |
| SDK comunitario Trirrin/livekit-java-sdk (rtc) | PENDIENTE — Nivel 2 aún no corrido | PENDIENTE |
| io.livekit:livekit-server (tokens) | PENDIENTE — Nivel 2 aún no corrido | PENDIENTE |
| Navegador usado en las pruebas | PENDIENTE — Nivel 3 aún no corrido | PENDIENTE |

## 3. Tabla de resultados por nivel

Valores posibles de Resultado: pasa / falla / parcial / no probado.

| Nivel | Criterio | Resultado | Evidencia (ruta en `evidence/`) |
|---|---|---|---|
| -1 Preflight | Java 21, Maven, Docker sin sudo, audio, permisos de micrófono, acceso a Maven Central y JitPack | PENDIENTE — por volcar desde la evidencia del Nivel -1 | PENDIENTE |
| 0 Servidor | LiveKit en Docker en modo dev; dos pestañas del navegador se oyen entre sí | PENDIENTE — por volcar desde la evidencia del Nivel 0 | PENDIENTE |
| 1 webrtc-java solo | Listar dispositivos, capturar micrófono, loopback con dos PeerConnection en la misma JVM; se oye. Anotar cancelación de eco y supresión de ruido | PENDIENTE — Nivel 1 aún no corrido | PENDIENTE |
| 2 JavaFX + SDK comunitario | Ventana (URL, sala, identidad, Conectar/Salir, Silenciar, participantes, log); tokens con clave dev; publica micrófono y reproduce audio remoto | PENDIENTE — Nivel 2 aún no corrido | PENDIENTE |
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

PENDIENTE — Nivel 2 aún no corrido.

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
| JARs (lista y tamaño) | PENDIENTE — Nivel 2 aún no corrido | PENDIENTE |
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
