#!/usr/bin/env bash
# Nivel 3f: dos clientes sin ventana en la misma sala durante N segundos, muestreando memoria.
# Uso: bash tools/soak.sh [segundos=630] > evidence/nivel3f-...txt
#   NOJCMD=1 bash tools/soak.sh ...  -> sin jcmd (solo RSS e hilos con ps), para no tocar la JVM
cd "$(dirname "$0")/.." || exit 1
source ./env.sh
SECS="${1:-630}"
TMP="$(mktemp -d)"
run() { mvn -q -B exec:java -Dexec.mainClass=sonimbus.poc.level2.HeadlessCheck \
          -Dexec.args="ws://localhost:7880 poc $1 $SECS" > "$TMP/$1.log" 2>&1; echo "exit=$?" >> "$TMP/$1.log"; }
echo "# Nivel 3f - $SECS s, clientes soak-a y soak-b. Inicio: $(date -Iseconds)"
run soak-a & run soak-b &
sleep 15
echo "# t_s cliente pid rss_kib hilos heap_usado_tras_gc_kib"
START=$(date +%s)
while :; do
  T=$(( $(date +%s) - START + 15 ))
  [ "$T" -ge "$((SECS - 10))" ] && break
  for id in soak-a soak-b; do
    PID=$(pgrep -f "poc $id $SECS" | head -1)
    if [ -z "$PID" ]; then echo "$T $id PROCESO_NO_ENCONTRADO"; continue; fi
    HEAP=-
    if [ -z "$NOJCMD" ]; then
      jcmd "$PID" GC.run > /dev/null 2>&1
      HEAP=$(jcmd "$PID" GC.heap_info 2>/dev/null | grep -o 'used [0-9]*K' | head -1 | tr -dc 0-9)
    fi
    echo "$T $id $PID $(ps -o rss=,nlwp= -p "$PID" | xargs) $HEAP"
  done
  sleep 30
done
wait
for id in soak-a soak-b; do echo; echo "=== log del cliente $id"; grep -av SLF4J "$TMP/$id.log" | cat -v; done
echo "# Fin: $(date -Iseconds)"
