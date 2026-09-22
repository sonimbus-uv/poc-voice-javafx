# PoC desechable: cliente de voz JavaFX contra LiveKit (proyecto Sonimbus)

## Objetivo
Saber con evidencia si un cliente de escritorio Java 21 + JavaFX puede participar en un canal de voz
WebRTC contra un SFU LiveKit autoalojado. LiveKit no tiene SDK oficial para Java de escritorio, y eso
es el riesgo técnico. El resultado es un informe con evidencia (REPORT.md), NO un producto.

## Entorno del usuario
- Ubuntu 24.04, zsh. Carpeta de trabajo: esta (poc-voice-javafx/). No toques nada fuera de ella,
  salvo ~/.m2 (caché de Maven) e imágenes de Docker.
- La carpeta está en una partición NTFS (ntfs3): los permisos son emulados. Lanza scripts con
  `bash script.sh`, no con `./script.sh`. Entrecomilla siempre las rutas (tienen espacios).
- Usa Java 21 (release 21). El sistema trae también JDK 24 por defecto: en cada sesión comprueba
  `java -version` y `mvn -v`. Si no dicen 21, PARA y pide al usuario que exporte
  JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 y lo antepone al PATH. No compiles con 24.
- Maven 3.8.7, Docker 29.x con Compose v2, git 2.43.
- Comandos con sudo (por ejemplo cortar la red en el Nivel 3e): NO los ejecutes tú. Muestra el
  comando exacto y pide al usuario que lo corra.

## Reglas (del brief original)
1. Un solo módulo Maven, sin frameworks extra, código simple.
2. Antes de programar, resume el plan en 10 líneas con las versiones que usarás y ESPERA mi OK.
3. No inventes APIs: clona/lee el README y el código de cada biblioteca y verifica versiones
   vigentes antes de fijarlas.
4. Niveles secuenciales: si un nivel falla sin solución razonable, detente y reporta.
5. Si el Nivel 1 no produce audio en 3 horas, detente y reporta.
6. Si algo falla, documenta el error exacto y lo que probaste en evidence/. Nada de hacks
   silenciosos, y no cambies de biblioteca sin avisar.
7. Tiempo total máximo: aprox. 1 día.

## Bibliotecas a evaluar y estado de verificación (de una investigación previa)
Verificado (fuentes web, revalida antes de fijar):
- dev.onvoid.webrtc:webrtc-java 0.18.0 (Maven Central, Apache-2.0). El POM referencia un JAR nativo
  por plataforma vía classifier: windows-x86_64/aarch64, linux-x86_64/aarch64/aarch32,
  macos-x86_64/aarch64. Lee su README para confirmar cómo declarar el classifier en Maven.
- SDK comunitario NO oficial: Trirrin/livekit-java-sdk, tag v0.1.4 vía JitPack
  (com.github.Trirrin.livekit-java-sdk:rtc:v0.1.4). Apache-2.0, Java 21+, construido con Gradle,
  ~3 estrellas, 28 commits, sin releases en GitHub. Protocolo de señal 17. Su README enlaza a un
  repo de webrtc-java distinto del oficial (devopvoid): anótalo como señal de riesgo.
- LiveKit oficial: `livekit-server --dev`, clave devkey / secreto secret; por defecto escucha solo
  en 127.0.0.1:7880 (usa --bind 0.0.0.0 para otros dispositivos).
- JavaFX: línea 21.x para Java 21 (21.0.8 confirmado en Maven Central). No uses la línea 25.

PENDIENTE de verificar (no lo des por hecho):
- Última actividad (fecha del último commit) del SDK de Trirrin y qué versión de webrtc-java fija su
  módulo `rtc` (si difiere de 0.18.0, decide cómo resolver el conflicto y documéntalo).
- Tag exacto de la imagen livekit/livekit-server (vista v1.13.3; pero no era la última).
- Puertos y modo de red de Docker en Linux: 7881/TCP y rango UDP (50000-60000 o un puerto único
  tipo 7882) y `network_mode: host` vienen de guías de TERCEROS. Confírmalos en la guía oficial de
  despliegue de docs.livekit.io antes de escribir el docker-compose.yml.
- io.livekit:livekit-server (SDK de servidor Java/Kotlin, solo para tokens): 0.13.0 vs 0.16.0;
  confirma la última en Maven Central. API de tokens: AccessToken, addGrants(RoomJoin, RoomName),
  toJwt().

## Niveles
- Nivel 0 Servidor: LiveKit en Docker en modo dev; dos pestañas del navegador (ejemplo oficial) se
  oyen entre sí.
- Nivel 1 webrtc-java solo: listar dispositivos de audio, capturar el micrófono, loopback local con
  dos PeerConnection en la misma JVM (SDP/ICE en memoria) y reproducir el audio recibido. Criterio:
  se oye. Anota si hay cancelación de eco y supresión de ruido.
- Nivel 2 JavaFX + SDK comunitario contra LiveKit: ventana con URL, sala, identidad, Conectar/Salir,
  Silenciar, lista de participantes y panel de log. Generador de tokens con la clave dev. Publica el
  micrófono y reproduce el audio remoto.
- Nivel 3 Interoperabilidad y robustez: a) JavaFX habla y el navegador oye; b) al revés;
  c) silenciar/reactivar se refleja en ambos lados; d) entradas/salidas de participantes;
  e) cortar la red 10 s: reconecta o cómo falla; f) 10 min sin caídas ni fugas de memoria
  evidentes; g) latencia (aplauso y, si hay estadísticas, RTT y jitter; objetivo < 400 ms en red
  local); h) dos instancias JavaFX + un navegador a la vez.
- Extra: ¿natives empaquetados para Windows y macOS? Solo verificar disponibilidad, no probar.

## Evidencia
Guarda en evidence/ cada log, captura de errores y salida de comandos que respalde un resultado
(nombre: nivelX-tema-fecha.txt). Todo resultado de REPORT.md debe apuntar a un archivo de ahí.
Cuando algo requiera oír audio o mirar el navegador, pídeselo al usuario y anota su respuesta
literal como evidencia de tipo "observación del usuario".

## Entregables
1. Código, docker-compose.yml y README con los pasos exactos para ejecutarlo.
2. REPORT.md: versiones usadas, tabla de resultados por nivel (pasa/falla/parcial con evidencia),
   problemas y soluciones, particularidades de Linux (PulseAudio o PipeWire, permisos de micrófono),
   tamaño y dependencias, riesgos abiertos.
3. Recomendación final justificada con la evidencia, entre:
   A) webrtc-java + SDK comunitario en JavaFX; B) voz en navegador embebido o del sistema;
   C) voz solo en el móvil (Flutter) durante el MVP; D) cambiar el cliente de escritorio a Flutter.
   Si un nivel no se pudo probar, dilo: no rellenes con suposiciones.

## Flujo de trabajo
- Empieza con Nivel -1 (preflight): versiones de Java/Maven/Docker, `docker ps` sin sudo,
  audio (`wpctl status` o `pactl info`), permisos de micrófono, acceso a Maven Central y JitPack.
  Reporta y espera OK.
- Después presenta el plan de 10 líneas. Solo tras mi OK, programa.
- Un commit por nivel con mensaje claro. Ya hay `git init` con core.fileMode=false.

## Bitácora (PROGRESS.md)
- Mantén PROGRESS.md en la raíz: nivel actual, qué pasó/falló, decisiones tomadas (con versiones
  fijadas), qué sigue y qué necesita hacer el usuario (oír audio, comandos con sudo).
- Actualízalo al terminar cada nivel y antes de cualquier pausa larga.
- Al iniciar cualquier sesión: lee CLAUDE.md y PROGRESS.md, resume en 3 líneas dónde estamos y
  espera instrucciones. No repitas trabajo ya hecho ni reejecutes niveles ya cerrados.
