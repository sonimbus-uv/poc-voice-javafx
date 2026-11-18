#!/usr/bin/env bash
# Nivel 4: ventana JavaFX con cámara y video remoto. Uso: bash run-nivel4.sh [identidad=javafx-video-1]
# Requiere el servidor: docker compose up -d
cd "$(dirname "$0")" || exit 1
source ./env.sh
# El log se escribe fuera de la partición NTFS y se copia a evidence/ al terminar (ver PROGRESS.md).
LOG="${TMPDIR:-/tmp}/nivel4-cliente-${1:-javafx-video-1}-$(date +%Y%m%d-%H%M%S).txt"
mvn -q -B compile exec:java -Dexec.mainClass=sonimbus.poc.level4.VideoLauncher -Dexec.args="${1:-javafx-video-1}" 2>&1 | tee "$LOG"
cp "$LOG" evidence/ && echo "Log del cliente copiado a evidence/$(basename "$LOG")"
