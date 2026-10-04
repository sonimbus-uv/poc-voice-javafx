# PROGRESS

## Estado
- Nivel -1 (preflight): cerrado y aprobado. Evidencia: evidence/nivel-1-*.txt
- Verificación de versiones pendientes: hecha. Evidencia: evidence/nivel0-verificacion-versiones-20260921.txt
- Plan de 10 líneas aprobado; decisión del usuario: webrtc-java 0.18.0 (apt-get solo si hace falta, avisar).
- Nivel 0 (servidor): PASA. docker-compose.yml (livekit v1.13.7, host network, --dev). Dos pestañas de meet.livekit.io se oyen. Evidencia: evidence/nivel0-*.txt (observación del usuario incluida).
  Notas: en --dev el UDP es el puerto único 7882 (no 50000-60000). nodeIP cambia según la red. El contenedor no sobrevive a reinicios (restart: no): `docker compose up -d`. tools/token.py genera tokens de prueba (fallo previo = token mal pegado).
- Nivel 1 (webrtc-java 0.18.0 solo): PASA (2026-10-04). El usuario se oyó (por altavoces) y no notó eco.
  - Libs del sistema: todas instaladas (libglib2.0-0 = libglib2.0-0t64). Sin apt-get. Evidencia: evidence/nivel1-libs-sistema-20260921.txt
  - pom.xml (un módulo, webrtc-java 0.18.0 sin classifier; el nativo linux-x86_64 se resuelve solo) y
    src/main/java/sonimbus/poc/level1/Level1Loopback.java. Lanzar: `bash run-nivel1.sh 20`.
  - Lista 1 micrófono y 1 salida (PipeWire vía capa PulseAudio). Loopback conecta en ~130 ms, Opus 48 kHz,
    0 pérdidas, jitter 2 ms. Evidencia: evidence/nivel1-loopback-tecnico-20261004.txt
    OJO: esa corrida fue con micrófono y salida en MUTE (nivelMicLocal=0): prueba transporte, NO que se oiga.
  - Problema resuelto: AudioTrack.dispose() lanza java.lang.Error si no se hace removeTrack(sender) antes.
    Evidencia: evidence/nivel1-problema-dispose-20261004.txt
  - AEC/NS: AudioOptions expone echoCancellation/noiseSuppression/autoGainControl/highpassFilter (activados);
    las stats dan echoReturnLoss. Falta comprobar el efecto real al oído.
  - Observación del usuario: evidence/nivel1-observacion-usuario-20261004.txt. Sonó por altavoces, no por
    auriculares: el sistema marca el puerto de auriculares "not available" (no es fallo de webrtc-java).
    Supresión de ruido y retardo: sin evaluar todavía (retardo -> Nivel 3g).
- Nivel 2 (JavaFX + SDK Trirrin v0.1.4): PASA (2026-10-04), con dos workarounds explícitos sobre el SDK.
  - Código: src/main/java/sonimbus/poc/level2/ (TokenGenerator, VoiceClient, VoiceApp, Launcher, HeadlessCheck).
    Lanzar: `docker compose up -d` y `bash run-nivel2.sh [identidad]`.
  - pom.xml: + JitPack, SDK rtc v0.1.4, io.livekit:livekit-server 0.16.0 (tokens), javafx-controls 21.0.8.
    webrtc-java queda en 0.18.0 (el SDK pide 0.14.0): compila y corre.
  - Sin ventana: conecta, publica y el servidor registra "mediaTrack published". Evidencia: evidence/nivel2-headless-2-20261004.txt
  - La ventana arranca sin errores. Evidencia: evidence/nivel2-ventana-arranque-20261004.txt
  - Dos fallos del SDK rodeados con workaround EXPLÍCITO (evidence/nivel2-problemas-sdk-20261004.txt):
    1) nunca envía la oferta del publisher -> se llama a RtcClient.onPublisherNegotiationNeeded() tras publicar.
    2) SIGSEGV al salir si la pista se dispone tras shutdown() -> orden: unpublish, dispose pista, shutdown.
  - Otros hallazgos (mismo archivo): protocolo 13 (no 17); código del tag v0.1.4 es de 2025-12-06; sin AEC/NS
    configurable; sin getStats; 1236 clases duplicadas entre el SDK y io.livekit:livekit-server.
  - Primera prueba del usuario con el navegador (14:43-14:50Z): participantes visibles en ambos lados, Salir funciona.
    El servidor recibió voz de AMBOS micrófonos y la reenvió en ambos sentidos con 0 pérdidas
    (evidence/nivel2-servidor-audio-bidireccional-20261004.txt). El usuario NO confirmó que se oyera: con un solo
    micrófono físico abierto en los dos clientes a la vez no se distingue quién suena.
  - Hallazgo abierto: el servidor expulsó a javafx-1 por DUPLICATE_IDENTITY (dos conexiones con la misma identidad).
    El usuario dice que algo "se bloqueó". Sospecha SIN reproducir: tras una salida iniciada por el servidor,
    VoiceClient conserva `client` y Conectar responde "Ya hay una conexión activa"; además RtcClient.onLeave
    dispone la pista sin quitarla del sender. Reproducir en Nivel 3d (no se guardó el log del cliente esa vez).
  - run-nivel2.sh ahora guarda el log del cliente en evidence/nivel2-cliente-<identidad>-<fecha>.txt.
  - Prueba audible por sentidos separados (09:26-09:32 local): el usuario dice "se escucha bien de los 2 lados y me
    pude desconectar todo correcto no hay fallas". Evidencia: evidence/nivel2-observacion-usuario-20261004.txt (3),
    nivel2-cliente-javafx-1-20261004-092228.txt, nivel2-servidor-prueba-audible-20261004.txt.
    También funcionó Salir -> Conectar en la misma ventana y el mute se reflejó en ambos sentidos (adelanto de 3c).
  - REPORT.md rellenado hasta el Nivel 2 (versiones, tabla, sección 4.3, tamaño: 41 JARs, ~37,6 MiB).
- Nivel 3: EJECUTADO (2026-10-04), con varios apartados parciales. Con auriculares y micrófono de headset.
  - 3a, 3b: PASAN (prueba audible del Nivel 2 con un micrófono silenciado cada vez).
  - 3c: PASA. El usuario confirma que el icono de silencio cambia en ambos lados.
  - 3d: PARCIAL. Entradas/salidas bien. Identidad duplicada reproducida: el expulsado solo recibe onReconnecting,
    nunca onDisconnected, y queda colgado. evidence/nivel3d-entradas-salidas-identidad-duplicada-20261004.txt
  - 3e: PARCIAL / NO CONCLUYENTE. El comando de iptables solo bloqueó IPv4; los medios siguieron por IPv6 (0 pérdidas).
    Solo se demostró que la señalización de JavaFX aguanta ~10 s de bloqueo. Usuario: "se recupero solo".
    evidence/nivel3e-corte-red-20261004.txt. El README trae el comando corregido (iptables + ip6tables), SIN PROBAR.
  - 3f: PARCIAL. 10 min 29 s sin caídas, 0 pérdidas, memoria plana; al salir los dos procesos cayeron en nativo
    (pa_close assertion / XIO fatal). No reproducido en 45 s. evidence/nivel3f-10min-memoria-20261004.txt
  - 3g: PARCIAL. ~92 ms JavaFX -> navegador (tools/latency.sh), rtt 1-2 ms. Falta navegador -> JavaFX.
    evidence/nivel3g-retardo-javafx-navegador-20261004.txt
  - 3h: PARCIAL. javafx-1 + javafx-2 + navegador 64 s, 0 pérdidas; sin confirmación audible del usuario.
    evidence/nivel3h-dos-javafx-navegador-20261004.txt
  - Extra: natives de Windows y macOS disponibles en Maven Central, sin probar. evidence/extra-natives-windows-macos-20261004.txt
  - Observaciones literales del usuario: evidence/nivel3-observacion-usuario-20261004.txt
  - El log de javafx-1 de la sesión de las 09:53 no quedó guardado (167 bytes); causa sin determinar.
- Cierre de parciales del Nivel 3 (2026-10-04, 10:13-11:10):
  - 3e: PASA para un corte total de ~13 s (IPv4 + IPv6, tools/cut-network.sh). ICE disconnected -> connected solo,
    mismo participante y pista, un único hueco de 171 paquetes. La ventana no registró ningún evento durante el
    corte. Cortes largos sin probar. evidence/nivel3e-corte-red-total-20261004.txt
  - 3g: PASA en la misma máquina. 92 ms JavaFX -> navegador, 159 ms navegador -> JavaFX.
    evidence/nivel3g-retardo-navegador-javafx-20261004.txt
  - 3h: PASA. Segunda corrida de 2 min 22 s, 0 pérdidas; el usuario confirma "si se oian las 3".
    evidence/nivel3h-dos-javafx-navegador-2-20261004.txt, nivel3-observacion-usuario-20261004.txt (3) y (4)
  - 3f: PARCIAL y es el MAYOR RIESGO ABIERTO. Repetido sin jcmd: 10 min 29 s, memoria plana, 0 pérdidas, pero al
    salir soak-a abortó ("Pure virtual function called!") y soak-b quedó colgado en
    AudioDeviceModuleBase.disposeInternal (volcado de hilos guardado). Además la ventana javafx-1 (7 min 36 s) no
    terminó de desconectar, el usuario la cerró a mano y hubo SIGSEGV en libX11. 3 de 3 sesiones largas fallan al
    salir; jcmd descartado como causa. evidence/nivel3f-salida-sin-jcmd-20261004.txt,
    nivel3f-caida-al-salir-ventana-20261004.txt
  - 3d sigue PARCIAL (cliente expulsado queda colgado), sin cambios.
  - Incidencia NTFS: evidence/nivel3f-10min-sin-jcmd-20261004.txt quedó bloqueado en el kernel (procesos en
    estado D, pids 33271, 33666, 35929). NO escribir ni borrar ese archivo hasta reiniciar; no está en git.
    El bucle de muestreo de tools/soak.sh murió por eso (exit 139); el script en sí no se cambió más.
- REPORT.md COMPLETO y actualizado (recomendación: A con DOS condiciones: adoptar el SDK como código propio y
  resolver el fallo de cierre tras sesiones largas, sección 4.4.2; si no, B con navegador del sistema).
- Investigación del fallo de cierre (11:18-12:05): CAUSA NO LOCALIZADA; el fallo es INTERMITENTE.
  evidence/nivel3f-investigacion-cierre-20261004.txt (tabla de las 8 sesiones largas del día).
  - Sesiones largas con el SDK: 4 fallaron (09:51, 10:20, 10:35, 11:42) y 3 cerraron limpias (11:54, 12:05 x2).
  - Nivel 1 (webrtc-java solo) 630 s: limpio (evidence/nivel1-loopback-10min-cierre-20261004.txt). No exculpa
    a webrtc-java porque el fallo es intermitente.
  - Refutado: (1) ADM huérfano del SDK liberado antes de tiempo (experimento -Dpoc.cierre=adm-despues, REVERTIDO,
    diff en la evidencia); (2) lanzar con mvn exec:java vs java -cp; (3) GC; (4) jcmd.
  - Corrida bajo gdb: no falló, sin pila nativa.
  - journalctl -k: dos "kernel BUG" de ntfs3 (09:53:21 tee, 10:24:33 bash) escribiendo en evidence/. Explican el
    log perdido de las 09:53 y el archivo bloqueado. No demostrado que afecten al fallo de cierre.
  - Para lanzar sin Maven: classpath con `mvn dependency:build-classpath`, luego
    `java -cp target/classes:<cp> sonimbus.poc.level2.HeadlessCheck ws://localhost:7880 <sala> <id> <seg>`.
  - OJO: no redirigir salidas largas a archivos de la partición NTFS; escribir fuera y copiar al final.
- Siguiente paso si se sigue: reiniciar, mover el proyecto a ext4, repetir sesiones de 10 min bajo gdb hasta
  capturar la pila nativa del aborto. Otros pendientes: cortes de red largos; eco/ruido.
- Pregunta abierta del usuario: compartir un .exe con alguien lejos para probar. No existe: no hay empaquetado
  ni prueba en Windows, y el servidor solo escucha en esta máquina/red local en modo --dev. Sería trabajo nuevo
  (fuera del PoC): jpackage en Windows + servidor accesible desde internet.

## Decisiones (versiones a fijar, tras OK)
- Java 21 (source /media/.../env.sh o export JAVA_HOME), Maven 3.8.7
- livekit/livekit-server:v1.13.7
- webrtc-java 0.18.0 (decisión del usuario; el SDK fija 0.14.0, conflicto a resolver en Nivel 2)
- SDK Trirrin v0.1.4 (JitPack), io.livekit:livekit-server 0.16.0, JavaFX 21.0.8

## Necesita el usuario
- Reiniciar cuando pueda (libera el archivo bloqueado por ntfs3) y después borrar
  evidence/nivel3f-10min-sin-jcmd-20261004.txt.
- Decidir si se sigue investigando el fallo de cierre (tras reiniciar y mover a ext4) o se da el PoC por terminado.
