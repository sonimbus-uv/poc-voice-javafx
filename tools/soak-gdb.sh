#!/usr/bin/env bash
# Nivel 3f, segunda ronda: repite la sesión larga y CAPTURA LA PILA NATIVA del cierre fallido.
#
# Uso:  bash tools/soak-gdb.sh [segundos=630] [modo=2audio]        (modos: 2audio | 1audio | video)
#   GDB=0 bash tools/soak-gdb.sh 40 1audio      -> sin gdb (solo registra exit, cuelgue y muestreo)
#   GDB=late bash tools/soak-gdb.sh 630 2audio  -> SIN gdb durante la sesión; si un cliente se CUELGA, engancha gdb
#                                                  entonces (no perturba los tiempos; no captura un SIGABRT instantáneo)
#
# Modos (cada uno usa SU sala, para no mezclarse con otras pruebas):
#   2audio : dos clientes de audio (soak-a, soak-b) en la sala soak2-2audio, como en el soak original.
#   1audio : UN cliente de audio: descarta la hipótesis de la carrera entre dos cierres simultáneos.
#   video  : UN cliente que publica la cámara (640x480@15), sin micrófono: prueba el cierre con video.
#
# Cómo captura la pila (gdb enganchado a cada cliente 30 s ANTES de que cierre):
#   - si el cliente ABORTA (SIGABRT, exit=134): gdb se detiene en la señal y vuelca todos los hilos;
#   - si el cliente se CUELGA: pasada la espera, el script interrumpe gdb (SIGINT) y vuelca todos los hilos.
# Requiere kernel.yama.ptrace_scope=0 (lo cambia el USUARIO con sudo; ver README / PROGRESS.md):
#   sudo sysctl kernel.yama.ptrace_scope=0      # y al terminar:  sudo sysctl kernel.yama.ptrace_scope=1
#
# Todo se escribe en /tmp (NTFS da kernel BUG con escrituras largas) y al final se copia a evidence/.
cd "$(dirname "$0")/.." || exit 1
source ./env.sh
SECS="${1:-630}"
MODE="${2:-2audio}"
GDB="${GDB:-1}"
JOPTS="${JOPTS:-}"           # opciones extra de la JVM, p. ej. JOPTS=-Dpoc.cierreSeguroMs=15000
GRACE="${GRACE:-90}"        # s de espera tras el cierre previsto antes de dar un cliente por colgado
STAMP="$(date +%Y%m%d-%H%M%S)"
W="$(mktemp -d /tmp/soak-gdb.XXXXXX)"
OUT="$W/resumen.txt"
ETIQ="${ETIQ:-}"           # etiqueta opcional en el nombre de la evidencia (p. ej. parche / original)
EVID="evidence/nivel3f-soak2-${MODE}${ETIQ:+-$ETIQ}-${STAMP}"

if { [ "$GDB" = 1 ] || [ "$GDB" = late ]; } && [ "$(cat /proc/sys/kernel/yama/ptrace_scope)" != 0 ]; then
  echo "ptrace_scope=$(cat /proc/sys/kernel/yama/ptrace_scope): gdb no podrá engancharse." >&2
  echo "Ejecuta tú:  sudo sysctl kernel.yama.ptrace_scope=0   (o lanza con GDB=0 para correr sin gdb)" >&2
  exit 2
fi
if ! curl -s -o /dev/null --max-time 3 http://localhost:7880; then
  echo "El servidor LiveKit no responde en :7880. Ejecuta: docker compose up -d" >&2; exit 3
fi

echo "[prep] compilando y resolviendo classpath..." >&2
mvn -q -B compile dependency:build-classpath -Dmdep.outputFile="$W/cp.txt" > "$W/mvn.log" 2>&1 || { cat "$W/mvn.log"; exit 4; }
CP="target/classes:$(cat "$W/cp.txt")"
# webrtc-java borra su .so justo tras cargarlo (en /proc/PID/maps sale "(deleted)") y gdb no puede leerlo: se extrae
# del JAR y, al enganchar gdb, se copia con el MISMO nombre que el mapeado para que gdb resuelva sus símbolos
# exportados (el binario está stripped, pero exporta ~3000 símbolos dinámicos, p. ej. los Java_..._disposeInternal).
mkdir -p "$W/natlib" "$W/jar"
NATJAR="$(find ~/.m2 -name 'webrtc-java-0.18.0-linux-x86_64.jar' | head -1)"
[ -n "$NATJAR" ] && unzip -o -q "$NATJAR" -d "$W/jar"
NATSO="$(find "$W/jar" -name 'libwebrtc-java-linux-x86_64.so' | head -1)"

ROOM="soak2-$MODE"
declare -A PIDS GDBPIDS
launch() {  # launch <id> <clase> <args...>
  local id="$1"; shift
  java $JOPTS -cp "$CP" "$@" > "$W/$id.log" 2>&1 &
  PIDS[$id]=$!
}
case "$MODE" in
  2audio)
    launch soak-a sonimbus.poc.level2.HeadlessCheck ws://localhost:7880 "$ROOM" soak-a "$SECS"
    launch soak-b sonimbus.poc.level2.HeadlessCheck ws://localhost:7880 "$ROOM" soak-b "$SECS" ;;
  1audio)
    launch soak-a sonimbus.poc.level2.HeadlessCheck ws://localhost:7880 "$ROOM" soak-a "$SECS" ;;
  video)
    launch soak-v sonimbus.poc.level4.VideoHeadless ws://localhost:7880 "$ROOM" soak-v "$SECS" true 640 480 15 ;;
  *) echo "modo desconocido: $MODE" >&2; exit 1 ;;
esac

{
echo "# Nivel 3f segunda ronda - modo=$MODE segundos=$SECS gdb=$GDB sala=$ROOM etiqueta=[$ETIQ] opciones_jvm=[$JOPTS]"
echo "# Inicio: $(date -Iseconds)  | SO: $(grep PRETTY /etc/os-release | cut -d= -f2) | kernel $(uname -r) | $XDG_SESSION_TYPE | ptrace_scope=$(cat /proc/sys/kernel/yama/ptrace_scope)"
echo "# Pids: $(for k in "${!PIDS[@]}"; do echo -n "$k=${PIDS[$k]} "; done)"
echo "# Muestreo cada 30 s SOLO con ps (sin jcmd, para no perturbar la JVM): t_s id pid rss_kib hilos"
} > "$OUT"

START=$(date +%s)
GDB_AT=$(( SECS - 30 ))
GDB_DONE=0
while :; do
  T=$(( $(date +%s) - START ))
  # engancha gdb a cada cliente poco antes del cierre previsto
  if [ "$GDB" = 1 ] && [ "$GDB_DONE" = 0 ] && [ "$T" -ge "$GDB_AT" ]; then
    for id in "${!PIDS[@]}"; do
      MAPPED="$(grep -oE '/tmp/libwebrtc-java[^ ]*\.so' /proc/${PIDS[$id]}/maps 2>/dev/null | head -1)"
      [ -n "$MAPPED" ] && [ -n "$NATSO" ] && cp "$NATSO" "$W/natlib/$(basename "$MAPPED")"
      gdb -p "${PIDS[$id]}" -batch \
        -ex 'set pagination off' -ex 'set confirm off' -ex 'set debuginfod enabled off' \
        -ex "set solib-search-path $W/natlib" -ex 'sharedlibrary' \
        -ex 'handle SIGSEGV nostop noprint pass' -ex 'handle SIGUSR1 nostop noprint pass' \
        -ex 'handle SIGUSR2 nostop noprint pass' -ex 'handle SIGPIPE nostop noprint pass' \
        -ex 'handle SIGCHLD nostop noprint pass' -ex 'handle SIGALRM nostop noprint pass' \
        -ex 'handle SIGQUIT nostop noprint pass' -ex 'handle SIGABRT stop print pass' \
        -ex 'continue' \
        -ex 'echo \n===== PARADO (señal o interrupción) =====\n' \
        -ex 'info program' -ex 'info threads' -ex 'thread apply all bt 40' \
        -ex 'detach' > "$W/gdb-$id.txt" 2>&1 &
      GDBPIDS[$id]=$!
    done
    GDB_DONE=1
    echo "# t=$T s: gdb enganchado a los clientes" >> "$OUT"
  fi
  [ "$T" -ge $(( SECS + 10 )) ] && break
  for id in "${!PIDS[@]}"; do
    if kill -0 "${PIDS[$id]}" 2>/dev/null; then
      echo "$T $id ${PIDS[$id]} $(ps -o rss=,nlwp= -p "${PIDS[$id]}" | xargs)" >> "$OUT"
    fi
  done
  sleep 30 &
  wait $!
done

# --- espera de cierre ---
echo "# t=$(( $(date +%s) - START )) s: cierre previsto; esperando hasta ${GRACE}s a que terminen" >> "$OUT"
for _ in $(seq 1 "$GRACE"); do
  alive=0
  for id in "${!PIDS[@]}"; do kill -0 "${PIDS[$id]}" 2>/dev/null && alive=1; done
  [ "$alive" = 0 ] && break
  sleep 1
done

declare -A STATE
for id in "${!PIDS[@]}"; do
  pid="${PIDS[$id]}"
  if kill -0 "$pid" 2>/dev/null; then
    STATE[$id]="COLGADO (sigue vivo tras ${GRACE}s)"
    echo "# $(date +%T) $id COLGADO: pid $pid, wchan=$(cat /proc/$pid/wchan 2>/dev/null), estado=$(awk '/State/{print $2,$3}' /proc/$pid/status)" >> "$OUT"
    if [ "$GDB" = 1 ] && [ -n "${GDBPIDS[$id]}" ]; then
      kill -INT "${GDBPIDS[$id]}" 2>/dev/null          # interrumpe y vuelca la pila nativa
      wait "${GDBPIDS[$id]}" 2>/dev/null
    elif [ "$GDB" = late ]; then
      MAPPED="$(grep -oE '/tmp/libwebrtc-java[^ ]*\.so' /proc/$pid/maps 2>/dev/null | head -1)"
      [ -n "$MAPPED" ] && [ -n "$NATSO" ] && cp "$NATSO" "$W/natlib/$(basename "$MAPPED")"
      gdb -p "$pid" -batch -ex 'set pagination off' -ex 'set confirm off' -ex 'set debuginfod enabled off' \
        -ex "set solib-search-path $W/natlib" -ex 'sharedlibrary' \
        -ex 'echo \n===== PARADO (enganche tardío en un cliente colgado) =====\n' \
        -ex 'info threads' -ex 'thread apply all bt 40' -ex 'detach' > "$W/gdb-$id.txt" 2>&1
    fi
    jcmd "$pid" Thread.print > "$W/jstack-$id.txt" 2>&1   # pila Java, DESPUÉS de la nativa
    echo "# $(date +%T) $id: esperando 120 s más a ver si termina solo" >> "$OUT"
    for _ in $(seq 1 120); do kill -0 "$pid" 2>/dev/null || break; sleep 1; done
    if kill -0 "$pid" 2>/dev/null; then
      STATE[$id]="COLGADO hasta el final (se mató con SIGKILL)"
      kill -9 "$pid" 2>/dev/null
    fi
  fi
  wait "$pid" 2>/dev/null; code=$?
  [ -z "${STATE[$id]}" ] && STATE[$id]="terminó"
  [ "$code" != 0 ] && [[ "${STATE[$id]}" == terminó ]] && STATE[$id]="terminó CON ERROR"
  echo "# $id: ${STATE[$id]}, exit=$code" >> "$OUT"
  echo "$code" > "$W/$id.exit"
done
for id in "${!GDBPIDS[@]}"; do wait "${GDBPIDS[$id]}" 2>/dev/null; done

{
echo "# Fin: $(date -Iseconds)"
for id in "${!PIDS[@]}"; do
  echo; echo "=== log del cliente $id (exit=$(cat $W/$id.exit), líneas CIERRE SEGURO: $(grep -ac 'CIERRE SEGURO' "$W/$id.log"))"; grep -av SLF4J "$W/$id.log" | cat -v | tail -25
done
} >> "$OUT"

# --- copia a evidence/ ---
cp "$OUT" "${EVID}-resumen.txt"
for id in "${!PIDS[@]}"; do
  [ -s "$W/gdb-$id.txt" ] && cp "$W/gdb-$id.txt" "${EVID}-gdb-$id.txt"
  [ -s "$W/jstack-$id.txt" ] && cp "$W/jstack-$id.txt" "${EVID}-jstack-$id.txt"
done
echo "Resultado en $EVID-*.txt  (temporales en $W)"
echo "--- resumen:"; grep -aE '^# (t=|.*COLGADO|soak|.*terminó|Fin)' "$OUT"
for id in "${!PIDS[@]}"; do [ -s "$W/gdb-$id.txt" ] && { echo "--- gdb-$id: $(wc -l < "$W/gdb-$id.txt") líneas; ¿parado?: $(grep -c 'PARADO' "$W/gdb-$id.txt")"; }; done
