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
- Siguiente: Nivel 2 (JavaFX + SDK Trirrin). Resolver conflicto 0.14.0 (SDK) vs 0.18.0 (decidido).

## Decisiones (versiones a fijar, tras OK)
- Java 21 (source /media/.../env.sh o export JAVA_HOME), Maven 3.8.7
- livekit/livekit-server:v1.13.7
- webrtc-java 0.18.0 (decisión del usuario; el SDK fija 0.14.0, conflicto a resolver en Nivel 2)
- SDK Trirrin v0.1.4 (JitPack), io.livekit:livekit-server 0.16.0, JavaFX 21.0.8

## Necesita el usuario
- Auriculares: el sistema no detecta el conector (puerto "not available"). Conviene arreglarlo antes del
  Nivel 3 (navegador + JavaFX en la misma máquina se acoplan por altavoces).
- Posibles apt-get de librerías nativas (sudo): lo hará el usuario.
