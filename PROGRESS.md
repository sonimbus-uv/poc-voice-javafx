# PROGRESS

## Estado
- Nivel -1 (preflight): cerrado y aprobado. Evidencia: evidence/nivel-1-*.txt
- Verificación de versiones pendientes: hecha. Evidencia: evidence/nivel0-verificacion-versiones-20260921.txt
- Plan de 10 líneas aprobado; decisión del usuario: webrtc-java 0.18.0 (apt-get solo si hace falta, avisar).
- Nivel 0 (servidor): PASA. docker-compose.yml (livekit v1.13.7, host network, --dev). Dos pestañas de meet.livekit.io se oyen. Evidencia: evidence/nivel0-*.txt (observación del usuario incluida).
  Notas: en --dev el UDP es el puerto único 7882 (no 50000-60000). nodeIP cambia según la red. El contenedor no sobrevive a reinicios (restart: no): `docker compose up -d`. tools/token.py genera tokens de prueba (fallo previo = token mal pegado).
- Siguiente: Nivel 1 (webrtc-java 0.18.0 solo). Comprobar libs del sistema primero.

## Decisiones (versiones a fijar, tras OK)
- Java 21 (source /media/.../env.sh o export JAVA_HOME), Maven 3.8.7
- livekit/livekit-server:v1.13.7
- webrtc-java: pendiente decidir 0.14.0 (fijada por el SDK) vs 0.18.0 (última); ver plan
- SDK Trirrin v0.1.4 (JitPack), io.livekit:livekit-server 0.16.0, JavaFX 21.0.8

## Necesita el usuario
- Micrófono estaba en mute en el preflight (desmutear antes del Nivel 1).
- Posibles apt-get de librerías nativas (sudo): lo hará el usuario.
