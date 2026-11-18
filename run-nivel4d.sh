#!/usr/bin/env bash
# Nivel 4d (sonda de captura de pantalla). Uso: bash run-nivel4d.sh [archivo.png]
# MIRA la pantalla mientras corre: en Wayland puede aparecer un diálogo de GNOME para compartir pantalla.
cd "$(dirname "$0")" || exit 1
source ./env.sh
CP="${TMPDIR:-/tmp}/poc-cp.txt"
mvn -q -B compile dependency:build-classpath -Dmdep.outputFile="$CP" > /dev/null || exit 1
java -cp "target/classes:$(cat "$CP")" sonimbus.poc.level4.ScreenProbe "${1:-${TMPDIR:-/tmp}/screen.png}"
