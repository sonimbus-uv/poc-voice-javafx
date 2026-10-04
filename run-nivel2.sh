#!/usr/bin/env bash
# Nivel 2: ventana JavaFX contra LiveKit. Uso: bash run-nivel2.sh [identidad=javafx-1]
# Requiere el servidor: docker compose up -d
cd "$(dirname "$0")" || exit 1
source ./env.sh
# El log de la ventana también sale por consola: se guarda como evidencia.
LOG="evidence/nivel2-cliente-${1:-javafx-1}-$(date +%Y%m%d-%H%M%S).txt"
mvn -q -B compile exec:java -Dexec.mainClass=sonimbus.poc.level2.Launcher -Dexec.args="${1:-javafx-1}" 2>&1 | tee "$LOG"
echo "Log del cliente guardado en $LOG"
