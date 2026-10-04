# REPORT — PoC cliente de voz JavaFX contra LiveKit (Sonimbus)

> Estado: niveles -1 a 3 y Extra ejecutados (2026-09-21 y 2026-10-04). Todo corrió en UNA sola máquina
> Linux. Cada resultado apunta a un archivo de `evidence/`. Lo que no se pudo probar está dicho como tal
> en las secciones 3, 7 y 8.

## 1. Resumen ejecutivo

- **Sí se puede**: un cliente de escritorio Java 21 + JavaFX entró en una sala de voz de un LiveKit
  autoalojado, publicó el micrófono y reprodujo el audio remoto. Se oyó en ambos sentidos contra el
  cliente web oficial, con 0 paquetes perdidos fuera del corte de red provocado y un retardo medido de
  unos 92 ms (JavaFX → navegador) y 159 ms (navegador → JavaFX). Un corte total de red de ~13 s se recuperó solo.
- **Con qué**: `webrtc-java` 0.18.0 (medios) + el SDK comunitario `Trirrin/livekit-java-sdk` v0.1.4
  (señalización LiveKit). `webrtc-java` se comportó bien. El SDK comunitario es el punto débil.
- **Lo que costó**: el SDK no funciona tal cual. Hicieron falta dos workarounds (no publicaba el
  micrófono; un orden de cierre incorrecto tumba la JVM con SIGSEGV). Además, cuando el servidor expulsa
  al cliente, el SDK se queda en "reconectando" para siempre, y **tras sesiones de más de ~7 minutos el
  cliente no cierra bien**: aborta en código nativo o se queda colgado al salir (3 de 3 sesiones largas).
- **Lo que no se probó**: red real (todo fue en localhost), cortes de red de más de ~13 s,
  Windows y macOS (solo se comprobó que existen los natives), calidad de la
  cancelación de eco y supresión de ruido.
- **Recomendación**: A (webrtc-java + SDK comunitario en JavaFX), condicionada a adoptar el SDK como
  código propio y a resolver el fallo de cierre tras sesiones largas; si no, B con el navegador del sistema.
  Detalle en la sección 8.

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
| 3e | Cortar la red 10 s: reconecta o cómo falla | pasa para un corte de ~13 s (IPv4 + IPv6): audio y señalización vuelven solos, sin reconexión ni reingreso; se pierde solo el audio del corte. La ventana no avisa de nada durante el corte. Cortes más largos sin probar. (El primer intento, solo IPv4, no fue concluyente) | `nivel3e-corte-red-total-20261004.txt`, `nivel3-observacion-usuario-20261004.txt` (3), `nivel3e-corte-red-20261004.txt` (primer intento) |
| 3f | 10 min sin caídas ni fugas de memoria evidentes | parcial: dos corridas de 10 min 29 s sin caídas, 0 pérdidas y memoria plana (RSS +0,3 %); pero **al salir tras una sesión larga el cliente no cierra bien**: 3 de 3 sesiones de más de 7 min acabaron en aborto nativo o colgadas en `disconnect()`, con y sin `jcmd` (ver 4.4.2) | `nivel3f-10min-memoria-20261004.txt`, `nivel3f-salida-sin-jcmd-20261004.txt`, `nivel3f-caida-al-salir-ventana-20261004.txt` |
| 3g | Latencia (aplauso; RTT y jitter si hay estadísticas); objetivo < 400 ms en red local | pasa en la misma máquina: ~92 ms JavaFX → navegador y ~159 ms navegador → JavaFX (ambos < 400 ms), RTT 1–2 ms. Sin red real de por medio y con una corrección de 75 ms medida aparte | `nivel3g-retardo-javafx-navegador-20261004.txt`, `nivel3g-retardo-navegador-javafx-20261004.txt` |
| 3h | Dos instancias JavaFX + un navegador a la vez | pasa: dos corridas (64 s y 2 min 22 s) con los tres conectados, cada ventana suscrita a los otros dos, 0 pérdidas y silencios propagados; el usuario confirma que se oían los tres | `nivel3h-dos-javafx-navegador-2-20261004.txt`, `nivel3-observacion-usuario-20261004.txt` (4), `nivel3h-dos-javafx-navegador-20261004.txt`, `nivel2-cliente-javafx-2-20261004-101812.txt` |
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

#### 4.4.2 El cliente no cierra bien tras una sesión larga (sin resolver, reproducido 3 veces)

- **Síntoma:** al salir después de una sesión de más de ~7 minutos, el proceso aborta en código nativo o se queda
  colgado. El servidor sí recibe la salida (`CLIENT_REQUEST_LEAVE`): los demás participantes no lo notan; lo que
  falla es liberar el audio local. Las salidas tras sesiones cortas (8 s a ~5 min) fueron todas limpias.
- **Las tres ocurrencias** (todas las sesiones largas del día):

  | Sesión | Duración | Qué pasó al salir |
  |---|---|---|
  | dos clientes sin ventana, con `jcmd` | 10 min 29 s | `Assertion 'pa_close(fds[0]) == 0' failed at ../src/pulsecore/core-util.c:2713` (exit=134) y `XIO: fatal IO error 0 (Success) on X server` (exit=1) |
  | ventana `javafx-1`, sin `jcmd` | 7 min 36 s | la desconexión no terminó (el log no llega a "Desconectado."); el usuario cerró la ventana a mano; `SIGSEGV` en `libX11.so.6 _XReply` y `corrupted double-linked list` |
  | dos clientes sin ventana, sin `jcmd` | 10 min 29 s | uno: `libc++abi: Pure virtual function called!`; el otro: colgado 33 min hasta matarlo |

- **Dónde se cuelga** (volcado de hilos del proceso colgado): en la llamada nativa
  `dev.onvoid.webrtc.media.audio.AudioDeviceModuleBase.disposeInternal`, invocada por el SDK
  (`MediaDevicesHelper.dispose` ← `PeerConnectionEngine.close` ← `RtcClient.disconnect` ← `RtcClient.shutdown`).
- **Qué se descartó:** que lo provocara el muestreo con `jcmd` (dos de las tres ocurrencias no lo usaron).
- **Sin determinar:** el umbral de duración y la causa dentro del código nativo (los errores apuntan a corrupción
  de memoria o a un doble cierre del módulo de audio; no está demostrado). No se probó si ocurre con `webrtc-java`
  solo, sin el SDK.
- **Evidencia:** `nivel3f-10min-memoria-20261004.txt`, `nivel3f-caida-al-salir-ventana-20261004.txt`,
  `nivel3f-salida-sin-jcmd-20261004.txt`, `nivel3-observacion-usuario-20261004.txt` (4).

#### 4.4.3 El primer corte de red no fue un corte (resuelto repitiendo la prueba)

- **Síntoma:** el comando de `iptables` solo filtra IPv4; la máquina tiene IPv6 y parte del tráfico iba por ahí.
  El audio de subida de JavaFX no perdió ni un paquete durante el "corte".
- **Solución:** `tools/cut-network.sh` bloquea IPv4 e IPv6 (7880/TCP, 7881/TCP, 7882/UDP). En la repetición el
  servidor dejó de recibir pings de JavaFX y del navegador durante ~13 s, marcó a ambos como `LOST`, ICE pasó a
  `disconnected` y 2 s después a `connected`, con el mismo participante y la misma pista. Pérdidas: un único
  hueco de 171 paquetes en la subida de JavaFX (209 en la del navegador).
- **Queda abierto:** el cliente JavaFX no registró ningún evento durante el corte (el SDK no avisa de la
  degradación), y no se probaron cortes más largos, donde el servidor acaba cerrando al participante (ver 4.4.1).
- **Evidencia:** `nivel3e-corte-red-20261004.txt` (primer intento), `nivel3e-corte-red-total-20261004.txt`.

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

Incidencia (2026-10-04): un archivo de `evidence/` que un script estaba escribiendo quedó bloqueado en el kernel
(procesos en estado `D` en `ntfs_file_write_iter` / `do_truncate`, imposibles de matar); cualquier escritura
posterior sobre ese archivo se colgaba. Es un problema del driver ntfs3, no del PoC; se esquivó escribiendo la
evidencia con otro nombre (`nivel3f-salida-sin-jcmd-20261004.txt`). Probablemente haga falta reiniciar para
liberar el archivo bloqueado (`nivel3f-10min-sin-jcmd-20261004.txt`, incompleto, no forma parte de la evidencia).

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
2. **Sin manejo de desconexiones del lado servidor.** El cliente expulsado queda colgado. Un corte de ~13 s se
   recupera solo, pero la aplicación no se entera de que hubo corte, y los cortes largos (en los que el servidor
   cierra al participante) no se probaron. (4.4.1, 4.4.3)
3. **Fallos nativos que matan el proceso**, no excepciones: SIGSEGV por orden de cierre (resuelto con workaround)
   y el cierre tras sesiones largas, que aborta o se cuelga en las 3 sesiones largas probadas (causa sin localizar). En
   una aplicación de escritorio esto cierra o congela toda la app, y una llamada de voz normal dura más de 7 minutos. (4.3.2, 4.4.2)
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
9. **Medidas de retardo con supuestos.** Los 92 ms y 159 ms excluyen el hardware, dependen de una corrección de
   75 ms medida por separado y se tomaron con todo en la misma máquina.

## 8. Recomendación final (A/B/C/D)

Opciones: A) webrtc-java + SDK comunitario en JavaFX; B) voz en navegador embebido o del sistema;
C) voz solo en el móvil (Flutter) durante el MVP; D) cambiar el cliente de escritorio a Flutter.

- **Opción elegida: A, con dos condiciones.** (1) Tratar el SDK comunitario como código propio: copiarlo al
  repositorio (o hacer un fork), corregir en él los fallos de 4.3 y 4.4.1 en lugar de rodearlos, y fijar
  webrtc-java 0.18.0. (2) Resolver antes el fallo de cierre tras sesiones largas (4.4.2): hoy una llamada de más
  de ~7 minutos termina con la aplicación abortada o colgada, y eso no es aceptable en un producto. Si el equipo
  no puede asumir el mantenimiento, o si 4.4.2 resulta no tener arreglo razonable, la alternativa es **B con el
  navegador del sistema**, que usa el SDK oficial de LiveKit.
- **Justificación (ligada a la sección 3):**
  - El riesgo que motivó el PoC —"no hay SDK oficial para Java de escritorio"— no bloquea: los niveles 1, 2, 3a,
    3b y 3c pasan, con interoperabilidad real contra el cliente web oficial.
  - La parte difícil y grande (WebRTC nativo, códecs, audio) la resuelve `webrtc-java`, que no dio problemas
    propios más allá del orden de liberación (4.2) y tiene natives para las tres plataformas (Extra).
  - Los fallos con causa identificada (4.3.1, 4.3.2, 4.4.1) están en la capa de señalización del SDK comunitario,
    que es pequeña y legible: las causas se localizaron leyendo su código. Es un problema de mantenimiento, no de
    viabilidad. El cierre tras sesiones largas (4.4.2) es la excepción y el mayor riesgo abierto: se reproduce
    siempre, está en la frontera entre el SDK y el código nativo de `webrtc-java`, y su causa no está localizada.
  - El retardo (3g, ~92 ms y ~159 ms según el sentido) y el consumo (3f, memoria plana) están dentro de lo
    aceptable, y un corte de red de ~13 s se recupera solo (3e).
  - C y D resolverían un problema que la evidencia no muestra: el escritorio JavaFX sí puede hacer voz. B queda
    como salida si A se complica; no se evaluó en este PoC, así que su coste real es desconocido.
- **Lo que debería probarse antes de comprometerse con A:**
  1. **localizar y corregir el fallo de cierre tras sesiones largas (4.4.2)**; empezar por repetir la sesión de
     10 minutos con `webrtc-java` solo (Nivel 1) para saber si el fallo es del SDK o de la biblioteca nativa. Si no
     se puede corregir, A deja de ser recomendable;
  2. probar cortes de red largos y decidir cómo reconectar (4.4.1, 4.4.3);
  3. una prueba entre dos máquinas por red real, idealmente con una en Windows;
  4. eco y ruido con altavoces, con y sin las opciones de procesado.
- **Niveles que no se pudieron cerrar:** 3d (cliente expulsado queda colgado), 3f (el cierre tras sesiones
  largas falla), y todo lo relativo a Windows/macOS, red real, cortes largos y calidad de eco/ruido.
  No se ha rellenado ninguno con suposiciones.
