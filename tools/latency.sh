#!/usr/bin/env bash
# Nivel 3g: graba a la vez el micrófono (canal izq) y lo que suena por la salida (monitor, canal der)
# y calcula el retardo micrófono -> salida. Uso: bash tools/latency.sh [segundos=12]
# Antes mide el desfase propio de la grabación (micrófono en los dos canales, debería ser 0) y lo resta.
cd "$(dirname "$0")/.." || exit 1
SECS="${1:-12}"
MIC="$(pactl get-default-source)"; MON="$(pactl get-default-sink).monitor"
rec() {  # rec <fuente canal der> <segundos>
  local out; out="$(mktemp --suffix=.wav)"
  ffmpeg -loglevel error -y -f pulse -i "$MIC" -f pulse -i "$1" \
    -filter_complex "[0:a]pan=mono|c0=c0[a];[1:a]pan=mono|c0=c0[b];[a][b]amerge=inputs=2" \
    -ar 8000 -c:a pcm_s16le -t "$2" "$out" || exit 1
  python3 tools/latency.py "$out"; rm -f "$out"
}
echo "# $(date -Iseconds) calibración (izq=der=$MIC, 4 s)"
CAL_OUT="$(rec "$MIC" 4)"; echo "$CAL_OUT"
SKEW="$(echo "$CAL_OUT" | grep -o 'retardo_ms=-\?[0-9]*' | cut -d= -f2)"
echo "# $(date -Iseconds) medida ($SECS s): izq=$MIC der=$MON"
M_OUT="$(rec "$MON" "$SECS")"; echo "$M_OUT"
RAW="$(echo "$M_OUT" | grep -o 'retardo_ms=-\?[0-9]*' | cut -d= -f2)"
echo "RESULTADO: retardo micrófono->salida = $((RAW - SKEW)) ms (bruto $RAW ms, desfase de grabación $SKEW ms)"
echo "Supuesto: el desfase de grabación es el mismo con el monitor que con el micrófono (estable en 4 calibraciones)."
