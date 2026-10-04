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
- Siguiente: Nivel 3 (interoperabilidad y robustez). Pendiente del OK del usuario para empezar.
  Incluir en 3d la reproducción de la expulsión por identidad duplicada.

## Decisiones (versiones a fijar, tras OK)
- Java 21 (source /media/.../env.sh o export JAVA_HOME), Maven 3.8.7
- livekit/livekit-server:v1.13.7
- webrtc-java 0.18.0 (decisión del usuario; el SDK fija 0.14.0, conflicto a resolver en Nivel 2)
- SDK Trirrin v0.1.4 (JitPack), io.livekit:livekit-server 0.16.0, JavaFX 21.0.8

## Necesita el usuario
- Auriculares: el sistema no detecta el conector (puerto "not available"). Conviene arreglarlo antes del
  Nivel 3 (navegador + JavaFX en la misma máquina se acoplan por altavoces).
- Posibles apt-get de librerías nativas (sudo): lo hará el usuario.
- Nivel 3e: cortar la red 10 s requiere sudo; el comando se le dará al usuario para que lo ejecute.
