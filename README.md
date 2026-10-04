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

## Pruebas del Nivel 3

- Log del cliente: `run-nivel2.sh` lo guarda solo en `evidence/nivel2-cliente-<identidad>-<fecha>.txt`.
- Segunda ventana: `bash run-nivel2.sh javafx-2` (cada ventana necesita una identidad distinta; con la misma,
  el servidor expulsa a la primera).
- Cliente sin ventana: `mvn -q exec:java -Dexec.mainClass=sonimbus.poc.level2.HeadlessCheck -Dexec.args="ws://localhost:7880 poc <identidad> <segundos>"`
- 10 minutos con muestreo de memoria (dos clientes sin ventana; te oirás por los auriculares):

  ```
  bash tools/soak.sh 630 > evidence/nivel3f-10min-memoria-<fecha>.txt
  ```

- Retardo micrófono → salida (graba 40 s; da palmadas con un solo micrófono abierto en la sala). Necesita `ffmpeg`:

  ```
  bash tools/latency.sh 40
  ```

- Corte de red de 10 s sobre los puertos de LiveKit (pide sudo; bloquea IPv4 e IPv6):

  ```
  sudo sh -c 'R="udp --dport 7882|udp --sport 7882|tcp --dport 7880|tcp --sport 7880"; for t in iptables ip6tables; do echo "$R" | tr "|" "\n" | while read r; do $t -I INPUT -p $r -j DROP; done; done; sleep 10; for t in iptables ip6tables; do echo "$R" | tr "|" "\n" | while read r; do $t -D INPUT -p $r -j DROP; done; done; echo red restaurada'
  ```
