# REPORT — PoC cliente de voz JavaFX contra LiveKit (Sonimbus)

> Estado: niveles -1 a 3 y Extra ejecutados (2026-09-21 y 2026-10-04). Todo corrió en UNA sola máquina
> Linux. Cada resultado apunta a un archivo de `evidence/`. Lo que no se pudo probar está dicho como tal
> en las secciones 3, 7 y 8.

## 1. Resumen ejecutivo

- **Sí se puede**: un cliente de escritorio Java 21 + JavaFX entró en una sala de voz de un LiveKit
  autoalojado, publicó el micrófono y reprodujo el audio remoto. Se oyó en ambos sentidos contra el
  cliente web oficial, con 0 paquetes perdidos y un retardo medido de unos 92 ms en el sentido
  JavaFX → navegador.
- **Con qué**: `webrtc-java` 0.18.0 (medios) + el SDK comunitario `Trirrin/livekit-java-sdk` v0.1.4
  (señalización LiveKit). `webrtc-java` se comportó bien. El SDK comunitario es el punto débil.
- **Lo que costó**: el SDK no funciona tal cual. Hicieron falta dos workarounds (no publicaba el
  micrófono; un orden de cierre incorrecto tumba la JVM con SIGSEGV). Además, cuando el servidor expulsa
  al cliente, el SDK se queda en "reconectando" para siempre, y tras una sesión de 10 minutos los dos
  procesos de prueba cayeron en código nativo al salir (una vez, sin reproducir en corto).
- **Lo que no se probó**: red real (todo fue en localhost), pérdida real de medios durante 10 s (el
  corte solo bloqueó IPv4), Windows y macOS (solo se comprobó que existen los natives), calidad de la
  cancelación de eco y supresión de ruido.
- **Recomendación**: A (webrtc-java + SDK comunitario en JavaFX), condicionada a adoptar el SDK como
  código propio; si el equipo no puede asumir eso, B con el navegador del sistema. Detalle en la sección 8.

## 2. Entorno y versiones usadas

| Componente | Versión | Evidencia |
|---|---|---|
| Sistema operativo | Ubuntu 24.04, kernel 7.0.0-31-generic, sesión X11 | `nivel-1-java-maven-20260921.txt`, `nivel-1-mic-permisos-20260921.txt` |
| Java (JDK) | OpenJDK 21 (`/usr/lib/jvm/java-21-openjdk-amd64`, fijado con `env.sh`). El sistema trae JDK 24 por defecto | `nivel-1-java-maven-20260921.txt`, `nivel-1-java21-export-20260921.txt` |
| Maven | 3.8.7 | `nivel-1-java-maven-20260921.txt` |
| Docker / Compose | 29.1.3 / 2.40.3, sin sudo (grupo `docker`) | `nivel-1-docker-20260921.txt` |
| Servidor de audio | PipeWire 1.0.5 con WirePlumber; las aplicaciones entran por la capa PulseAudio | `nivel-1-audio-20260921.txt` |
| livekit/livekit-server (imagen) | v1.13.7, `--dev --bind 0.0.0.0`, `network_mode: host` | `nivel0-verificacion-versiones-20260921.txt`, `nivel0-servidor-arranque-20260921.txt` |
| dev.onvoid.webrtc:webrtc-java | 0.18.0 (nativo linux-x86_64). El SDK comunitario fija 0.14.0; Maven resuelve 0.18.0 | `nivel1-dependencias-20261004.txt`, `nivel2-problemas-sdk-20261004.txt` (E) |
| JavaFX | javafx-controls 21.0.8 | `nivel2-dependencias-tamano-20261004.txt` |
| SDK comunitario Trirrin/livekit-java-sdk (rtc) | tag v0.1.4 vía JitPack (commit 0fa2340, 2025-12-06). Protocolo de señal 13, no 17 | `nivel2-problemas-sdk-20261004.txt` (A) |
| io.livekit:livekit-server (tokens) | 0.16.0 | `nivel0-verificacion-versiones-20260921.txt`, `nivel2-dependencias-tamano-20261004.txt` |
| Navegador usado en las pruebas | Brave (cliente PipeWire "Brave"), con https://meet.livekit.io en modo Custom. Versión no registrada | `nivel-1-audio-20260921.txt` |

## 3. Tabla de resultados por nivel

Valores: pasa / falla / parcial / no probado.

| Nivel | Criterio | Resultado | Evidencia (en `evidence/`) |
|---|---|---|---|
| -1 Preflight | Java 21, Maven, Docker sin sudo, audio, permisos de micrófono, acceso a Maven Central y JitPack | pasa (hay que exportar JAVA_HOME a Java 21; el sistema arranca con 24) | `nivel-1-java-maven-20260921.txt`, `nivel-1-java21-export-20260921.txt`, `nivel-1-docker-20260921.txt`, `nivel-1-audio-20260921.txt`, `nivel-1-mic-permisos-20260921.txt`, `nivel-1-red-20260921.txt` |
| 0 Servidor | LiveKit en Docker en modo dev; dos pestañas del navegador se oyen entre sí | pasa | `nivel0-servidor-arranque-20260921.txt`, `nivel0-observacion-usuario-20260921.txt` |
| 1 webrtc-java solo | Listar dispositivos, capturar micrófono, loopback con dos PeerConnection en la misma JVM; se oye. Anotar cancelación de eco y supresión de ruido | pasa (se oyó por altavoces, sin eco percibido; supresión de ruido sin evaluar) | `nivel1-loopback-tecnico-20261004.txt`, `nivel1-observacion-usuario-20261004.txt` |
| 2 JavaFX + SDK comunitario | Ventana (URL, sala, identidad, Conectar/Salir, Silenciar, participantes, log); tokens con clave dev; publica micrófono y reproduce audio remoto | pasa, con dos workarounds explícitos sobre el SDK (ver 4.3) | `nivel2-observacion-usuario-20261004.txt` (3), `nivel2-cliente-javafx-1-20261004-092228.txt`, `nivel2-servidor-prueba-audible-20261004.txt` |
| 3a | JavaFX habla y el navegador oye | pasa (probado con el micrófono del navegador silenciado) | `nivel2-observacion-usuario-20261004.txt` (3), `nivel2-servidor-prueba-audible-20261004.txt` |
| 3b | El navegador habla y JavaFX oye | pasa (probado con el micrófono de JavaFX silenciado) | los mismos |
| 3c | Silenciar/reactivar se refleja en ambos lados | pasa | `nivel3-observacion-usuario-20261004.txt` (1), `nivel2-cliente-javafx-1-20261004-092228.txt` |
| 3d | Entradas/salidas de participantes | parcial: entradas y salidas normales se notifican bien; si el servidor expulsa al cliente (identidad duplicada), este no se entera y queda colgado | `nivel3d-entradas-salidas-identidad-duplicada-20261004.txt` |
| 3e | Cortar la red 10 s: reconecta o cómo falla | parcial / no concluyente: el corte solo bloqueó IPv4 y los medios siguieron por IPv6. La señalización de JavaFX aguantó ~10 s de bloqueo sin reconectar. No se probó una pérdida real de medios | `nivel3e-corte-red-20261004.txt`, `nivel3-observacion-usuario-20261004.txt` (2) |
| 3f | 10 min sin caídas ni fugas de memoria evidentes | parcial: 10 min 29 s sin caídas, 0 pérdidas, memoria plana; pero al salir los dos procesos cayeron en código nativo (no reproducido en 45 s) | `nivel3f-10min-memoria-20261004.txt` |
| 3g | Latencia (aplauso; RTT y jitter si hay estadísticas); objetivo < 400 ms en red local | parcial: ~92 ms JavaFX → navegador, RTT 1–2 ms; falta el sentido navegador → JavaFX y todo fue en la misma máquina | `nivel3g-retardo-javafx-navegador-20261004.txt` |
| 3h | Dos instancias JavaFX + un navegador a la vez | parcial: los tres conectados 64 s, 0 pérdidas, la segunda ventana se suscribió a los otros dos; la parte audible no fue confirmada por el usuario | `nivel3h-dos-javafx-navegador-20261004.txt`, `nivel2-cliente-javafx-2-20261004-100125.txt` |
| Extra | Natives empaquetados para Windows y macOS (solo disponibilidad, sin probar) | disponibles en Maven Central (webrtc-java 0.18.0: windows-x86_64/aarch64, macos-x86_64/aarch64; JavaFX 21.0.8: win, mac, mac-aarch64). No ejecutados | `extra-natives-windows-macos-20261004.txt` |

## 4. Problemas encontrados y soluciones

### 4.1 Nivel -1 y Nivel 0

- **Java por defecto es 24.** `java -version` daba 24.0.1. Solución: `source ./env.sh` (JAVA_HOME a
  Java 21); los scripts lo hacen solos. Evidencia: `nivel-1-java-maven-20260921.txt`,
  `nivel-1-java21-export-20260921.txt`.
- **Una pestaña salía "Disconnected".** Causa: token mal pegado; el servidor no registró ningún intento
  hasta que se pegó bien. Evidencia: `nivel0-diagnostico-desconexion-20260921.txt`.
- **Puertos en modo dev.** Con `--dev` el servidor usa un único puerto UDP (7882), no el rango
  50000–60000. Evidencia: `nivel0-servidor-arranque-20260921.txt`, `nivel0-ice-estados-20260921.txt`.

### 4.2 Nivel 1

- **`AudioTrack.dispose()` lanza `java.lang.Error`** si antes no se hace `removeTrack(sender)`.
  Solución: quitar la pista del sender antes de disponerla. Evidencia: `nivel1-problema-dispose-20261004.txt`.
- **El audio salió por altavoces, no por auriculares.** El sistema marcaba el conector de auriculares
  como "not available"; no es un fallo de webrtc-java, que usa la salida por defecto. Evidencia:
  `nivel1-observacion-usuario-20261004.txt`.
- **`libglib2.0-0` "falta"**: en Ubuntu 24.04 se llama `libglib2.0-0t64` y estaba instalada. No hizo
  falta ningún `apt-get`. Evidencia: `nivel1-libs-sistema-20260921.txt`.

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

#### 4.3.3 Limitaciones del SDK vistas en el código (no son fallos reproducidos)

Sin configuración de cancelación de eco / supresión de ruido; sin acceso a `getStats()` (RTT/jitter); la elección
de micrófono por `deviceId` no afecta a la captura real y no hay API para elegir salida; `onError` vacío (errores
tragados); 1236 clases duplicadas entre el módulo `protocol` del SDK y `io.livekit:livekit-server`.
Evidencia: `nivel2-problemas-sdk-20261004.txt` (D, E).

### 4.4 Nivel 3

#### 4.4.1 El cliente expulsado por el servidor se queda colgado (sin resolver)

- **Síntoma:** al entrar un segundo cliente con la misma identidad, el servidor expulsa al primero
  (`DUPLICATE_IDENTITY`). El expulsado solo recibe `onReconnecting`; nunca `onDisconnected`. No reconecta ni
  informa del motivo, y la aplicación sigue creyéndose conectada con la lista de participantes obsoleta.
  En la primera prueba del Nivel 2 el usuario lo vivió como "se bloqueó".
- **Efecto secundario:** al llamar después a `disconnect()` aparece
  `java.lang.NullPointerException: Object handle is null` al disponer la pista (el SDK ya la había dispuesto en
  `RtcClient.onLeave`). Queda capturada; no hubo SIGSEGV.
- **Estado:** reproducido, no corregido. Corregirlo exige tocar el SDK o envolverlo (tratar el `LeaveRequest`
  del servidor como desconexión).
- **Evidencia:** `nivel3d-entradas-salidas-identidad-duplicada-20261004.txt`,
  `nivel2-servidor-audio-bidireccional-20261004.txt`.

#### 4.4.2 Caída en código nativo al salir tras 10 minutos (sin resolver, sin reproducir)

- **Error exacto** (los dos procesos de la prueba, al llamar a `disconnect()` casi a la vez):

  ```
  Assertion 'pa_close(fds[0]) == 0' failed at ../src/pulsecore/core-util.c:2713, function pa_close_pipe(). Aborting.   (exit=134)
  XIO:  fatal IO error 0 (Success) on X server "<bytes basura>"                                                        (exit=1)
  ```

- **Qué se probó:** el mismo guion con 45 s salió limpio en ambos (exit=0). Las demás salidas del día (sesiones de
  8 s a 5 min, con y sin ventana) fueron limpias.
- **Sin determinar:** si depende de la duración de la sesión o del muestreo (unos 20 adjuntos de `jcmd` por
  proceso). Falta repetir 10 minutos sin `jcmd`.
- **Evidencia:** `nivel3f-10min-memoria-20261004.txt`.

#### 4.4.3 El corte de red no fue un corte

- **Síntoma:** el comando de `iptables` solo filtra IPv4; la máquina tiene IPv6 y parte del tráfico iba por ahí.
  El audio de subida de JavaFX no perdió ni un paquete durante el "corte".
- **Estado:** prueba no concluyente. Repetir bloqueando también IPv6 (`ip6tables`).
- **Evidencia:** `nivel3e-corte-red-20261004.txt`.

#### 4.4.4 Un log de cliente no quedó guardado

`nivel2-cliente-javafx-1-20261004-095318.txt` solo tiene las dos líneas de arranque, aunque esa ventana estuvo
conectada 9 minutos. Causa sin determinar. Por eso no hay registro de lo que mostró la ventana durante el corte.

## 5. Particularidades de Linux

### 5.1 Audio (PipeWire/PulseAudio)

- El sistema usa PipeWire 1.0.5; webrtc-java entra por la capa PulseAudio (`libpulse0`) y ve 1 micrófono y 1
  salida. Evidencia: `nivel-1-audio-20260921.txt`, `nivel1-loopback-tecnico-20261004.txt`.
- Varios programas pueden abrir el mismo micrófono a la vez (dos ventanas JavaFX y el navegador lo compartieron).
  Consecuencia para las pruebas: con un solo micrófono abierto en los dos clientes no se distingue quién suena;
  hay que silenciar un lado cada vez. Evidencia: `nivel2-servidor-audio-bidireccional-20261004.txt`.
- webrtc-java usa la entrada y la salida por defecto del sistema. El SDK comunitario no permite elegir
  dispositivo de forma efectiva (4.3.3).
- La caída del 4.4.2 ocurre dentro del cliente nativo de PulseAudio.

### 5.2 Permisos de micrófono

No hizo falta ningún permiso especial ni apareció ningún diálogo: la aplicación Java abrió el micrófono
directamente a través de PipeWire. El micrófono estaba silenciado y al 22 % al empezar; hubo que activarlo a mano.
Evidencia: `nivel-1-mic-permisos-20260921.txt`, `nivel0-observacion-usuario-20260921.txt`.

### 5.3 Partición NTFS

La carpeta está en NTFS (ntfs3) con permisos emulados: los scripts se lanzan con `bash script.sh`. Compilar y
ejecutar desde ahí funcionó. webrtc-java carga su biblioteca nativa desde un archivo temporal
(`libwebrtc-java-linux-x86_64…so`), no desde la carpeta del proyecto. Evidencia:
`nivel2-crash-sigsegv-hs_err-20261004.log`, `nivel1-loopback-tecnico-20261004.txt`.

## 6. Tamaño y dependencias

| Elemento | Valor | Evidencia |
|---|---|---|
| JARs (lista y tamaño) | 41 JARs en el classpath de ejecución, 38 552 KiB (~37,6 MiB) en total, solo linux-x86_64. Los mayores: webrtc-java nativo 9,4 MiB, `protocol` del SDK 4,6 MiB, javafx-graphics 4,5 MiB, livekit-server 4,0 MiB | `nivel2-dependencias-tamano-20261004.txt` |
| Natives (por plataforma) | webrtc-java 0.18.0: linux-x86_64 9,4 MiB, windows-x86_64 8,3 MiB, windows-aarch64 7,1 MiB, macos-x86_64 7,4 MiB, macos-aarch64 6,3 MiB. JavaFX 21.0.8 no publica linux-aarch64 | `extra-natives-windows-macos-20261004.txt` |
| Librerías del sistema requeridas | libx11-6, libxext6, libxfixes3, libxdamage1, libxtst6, libxrandr2, libxcomposite1, libglib2.0-0t64, libgbm1, libdrm2, libpulse0: todas ya instaladas en esta máquina | `nivel1-libs-sistema-20260921.txt` |
| Memoria en ejecución (cliente sin ventana) | ~188 MiB de RSS, ~12,3 MiB de heap tras GC, 52 hilos | `nivel3f-10min-memoria-20261004.txt` |
| Tamaño total del artefacto | No se construyó un artefacto distribuible (ni fat-jar ni instalador); el PoC se ejecuta con `mvn exec:java`. La cifra de referencia es la de los JARs | — |

## 7. Riesgos abiertos

1. **El SDK comunitario no es utilizable sin tocarlo.** Dos workarounds obligatorios, errores tragados, protocolo
   de señal 13, sin pruebas de integración propias, ~3 estrellas, último cambio de código de 2025-12-06, fijado a
   webrtc-java 0.14.0. Quien lo use lo mantiene. (4.3)
2. **Sin manejo de desconexiones del lado servidor.** El cliente expulsado queda colgado; el comportamiento ante
   una pérdida real de medios no se llegó a probar. (4.4.1, 4.4.3)
3. **Fallos nativos que matan el proceso**, no excepciones: SIGSEGV por orden de cierre (resuelto con workaround)
   y la caída al salir tras 10 minutos (sin explicar). En una aplicación de escritorio esto cierra toda la app. (4.3.2, 4.4.2)
4. **Nada probado fuera de localhost.** Sin NAT, sin TURN, sin TLS (`wss`), sin pérdida ni latencia de red reales.
   El servidor corrió en modo `--dev`.
5. **Windows y macOS sin probar.** Los natives existen; el comportamiento del audio y de los permisos de
   micrófono en esos sistemas es desconocido.
6. **Calidad de audio sin evaluar.** El SDK no deja configurar cancelación de eco ni supresión de ruido; en el
   Nivel 1 (webrtc-java directo, opciones activadas) el usuario no percibió eco por altavoces, pero no se comparó
   con las opciones desactivadas ni se evaluó con el SDK.
7. **Sin estadísticas en el cliente.** El SDK no expone `getStats()`; RTT y jitter salieron del servidor.
8. **Conflicto de clases.** 1236 clases duplicadas entre el SDK y `io.livekit:livekit-server` (tokens). Solo
   afecta si el cliente genera tokens, como hace este PoC.
9. **Medidas de retardo con supuestos.** Los 92 ms excluyen el hardware y dependen de una corrección de 75 ms
   medida por separado; solo se midió un sentido.

## 8. Recomendación final (A/B/C/D)

Opciones: A) webrtc-java + SDK comunitario en JavaFX; B) voz en navegador embebido o del sistema;
C) voz solo en el móvil (Flutter) durante el MVP; D) cambiar el cliente de escritorio a Flutter.

- **Opción elegida: A, con una condición.** La condición es tratar el SDK comunitario como código propio:
  copiarlo al repositorio (o hacer un fork), corregir en él los fallos de 4.3 y 4.4.1 en lugar de rodearlos, y
  fijar webrtc-java 0.18.0. Si el equipo no puede asumir ese mantenimiento, la alternativa es **B con el
  navegador del sistema**, que usa el SDK oficial de LiveKit.
- **Justificación (ligada a la sección 3):**
  - El riesgo que motivó el PoC —"no hay SDK oficial para Java de escritorio"— no bloquea: los niveles 1, 2, 3a,
    3b y 3c pasan, con interoperabilidad real contra el cliente web oficial.
  - La parte difícil y grande (WebRTC nativo, códecs, audio) la resuelve `webrtc-java`, que no dio problemas
    propios más allá del orden de liberación (4.2) y tiene natives para las tres plataformas (Extra).
  - Los fallos con causa identificada (4.3.1, 4.3.2, 4.4.1) están en la capa de señalización del SDK comunitario,
    que es pequeña y legible: las causas se localizaron leyendo su código. Es un problema de mantenimiento, no de
    viabilidad. La caída de 4.4.2 es la excepción: su causa no está localizada.
  - El retardo (3g, ~92 ms en un sentido) y el consumo (3f, memoria plana) están dentro de lo aceptable.
  - C y D resolverían un problema que la evidencia no muestra: el escritorio JavaFX sí puede hacer voz. B queda
    como salida si A se complica; no se evaluó en este PoC, así que su coste real es desconocido.
- **Lo que debería probarse antes de comprometerse con A:**
  1. repetir 3e con un corte real (IPv4 e IPv6) y decidir cómo reconectar;
  2. repetir 3f sin `jcmd` para aclarar la caída al salir (4.4.2);
  3. una prueba entre dos máquinas por red real, idealmente con una en Windows;
  4. eco y ruido con altavoces, con y sin las opciones de procesado.
- **Niveles que no se pudieron probar por completo:** 3e (corte parcial), 3g (un solo sentido), 3h (sin
  confirmación audible), 3f (caída al salir sin explicar), y todo lo relativo a Windows/macOS y a red real.
  No se ha rellenado ninguno con suposiciones.
