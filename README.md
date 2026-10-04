# PoC de voz: JavaFX + webrtc-java contra LiveKit (Sonimbus)

PoC desechable. El resultado está en `REPORT.md`; el estado del trabajo, en `PROGRESS.md`.

## Requisitos

- Ubuntu 24.04, Java 21, Maven 3.8.7, Docker con Compose v2.
- En cada terminal: `source ./env.sh` (fija JAVA_HOME a Java 21). Los scripts ya lo hacen.
- La carpeta está en NTFS: lanza los scripts con `bash script.sh`, no con `./script.sh`.

## Pasos

1. Servidor LiveKit en modo dev (clave `devkey`, secreto `secret`):

   ```
   docker compose up -d
   ```

2. Nivel 1, loopback local con webrtc-java (te oyes a ti mismo; usa auriculares):

   ```
   bash run-nivel1.sh 20        # 20 segundos
   bash run-nivel1.sh --list    # solo lista dispositivos
   ```

3. Nivel 2, ventana JavaFX contra LiveKit:

   ```
   bash run-nivel2.sh javafx-1
   ```

   Pulsa **Conectar** (URL `ws://localhost:7880`, sala `poc`). El token se genera solo.

4. Navegador como segundo participante: abre https://meet.livekit.io, pestaña **Custom**, con
   URL `ws://localhost:7880` y el token que imprime:

   ```
   python3 tools/token.py navegador poc
   ```

5. Parar el servidor: `docker compose down`.
