#!/usr/bin/env bash
# Nivel 3f, reproductor RÁPIDO del aborto al cerrar ("libc++abi: Pure virtual function called!", exit=134), visto en
# sesiones cortas con video. Repite N sesiones cortas y cuenta cuántas terminan con error.
# Uso: bash tools/cierre-corto.sh <N> <plain|gdb> [segundos=8] [video|audio]
#   plain : solo mide la tasa de fallo (exit != 0).
#   gdb   : cada sesión corre BAJO gdb (desde el inicio, sin necesitar sudo ni ptrace_scope) y se PARA en el primer SIGABRT,
#           volcando la pila nativa de todos los hilos.
# Todo se escribe en /tmp y los resultados se copian a evidence/.
cd "$(dirname "$0")/.." || exit 1
source ./env.sh
N="${1:-30}"; MODE="${2:-plain}"; SECS="${3:-8}"; KIND="${4:-video}"
STAMP="$(date +%Y%m%d-%H%M%S)"
W="$(mktemp -d /tmp/cierre-corto.XXXXXX)"
EV="evidence/nivel3f-cierre-corto-${KIND}-${MODE}-${STAMP}"
curl -s -o /dev/null --max-time 3 http://localhost:7880 || { echo "Servidor caído: docker compose up -d" >&2; exit 3; }
mvn -q -B compile dependency:build-classpath -Dmdep.outputFile="$W/cp.txt" > "$W/mvn.log" 2>&1 || { cat "$W/mvn.log"; exit 4; }
CP="target/classes:$(cat "$W/cp.txt")"
OUT="$W/resumen.txt"
echo "# cierre-corto: n=$N modo=$MODE segundos=$SECS tipo=$KIND | $(grep PRETTY /etc/os-release | cut -d= -f2) | $XDG_SESSION_TYPE | inicio $(date -Iseconds)" > "$OUT"
fail=0; captured=0
for i in $(seq 1 "$N"); do
  if [ "$KIND" = video ]; then
    ARGS=(sonimbus.poc.level4.VideoHeadless ws://localhost:7880 "cc-$MODE" "cc$i" "$SECS" true 640 480 15)
  else
    ARGS=(sonimbus.poc.level2.HeadlessCheck ws://localhost:7880 "cc-$MODE" "cc$i" "$SECS")
  fi
  s=$(date +%s.%N)
  if [ "$MODE" = gdb ]; then
    timeout 120 gdb -batch -ex 'set pagination off' -ex 'set confirm off' -ex 'set debuginfod enabled off' \
      -ex 'handle SIGSEGV nostop noprint pass' -ex 'handle SIGUSR1 nostop noprint pass' -ex 'handle SIGUSR2 nostop noprint pass' \
      -ex 'handle SIGPIPE nostop noprint pass' -ex 'handle SIGCHLD nostop noprint pass' -ex 'handle SIGALRM nostop noprint pass' \
      -ex 'handle SIGQUIT nostop noprint pass' -ex 'handle SIGABRT stop print pass' \
      -ex run -ex 'echo \n===== PARADO EN UNA SEÑAL =====\n' -ex 'info threads' -ex 'thread apply all bt 40' -ex kill \
      --args java -cp "$CP" "${ARGS[@]}" > "$W/run-$i.txt" 2>&1
    if grep -aq 'SIGABRT' "$W/run-$i.txt"; then code=ABORT; captured=1; else code=ok; fi
  else
    timeout 60 java -cp "$CP" "${ARGS[@]}" > "$W/run-$i.txt" 2>&1; code=$?
  fi
  e=$(date +%s.%N)
  msg=$(grep -aoE 'Pure virtual function called|futex facility|Assertion .pa_close|CIERRE SEGURO' "$W/run-$i.txt" | head -1)
  [ "$code" != 0 ] && [ "$code" != ok ] && fail=$((fail+1))
  printf '#%s %s t=%.1fs desconectado_en_log=%s %s\n' "$i" "$code" "$(echo "$e - $s" | bc)" "$(grep -ac 'Desconectado\.' "$W/run-$i.txt")" "$msg" >> "$OUT"
  if [ "$captured" = 1 ]; then echo "# PILA CAPTURADA en la corrida $i" >> "$OUT"; cp "$W/run-$i.txt" "${EV}-gdb-run$i.txt"; break; fi
done
echo "# RESULTADO: $fail corridas con error de $i ejecutadas (modo $MODE); fin $(date -Iseconds)" >> "$OUT"
cp "$OUT" "${EV}-resumen.txt"
cat "$OUT" | tail -n 12
echo "evidencia: ${EV}-*.txt (temporales: $W)"
