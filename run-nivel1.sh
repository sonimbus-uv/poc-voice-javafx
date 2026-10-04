#!/usr/bin/env bash
# Nivel 1: loopback local con webrtc-java. Uso: bash run-nivel1.sh [segundos=20] [--list]
cd "$(dirname "$0")" || exit 1
source ./env.sh
mvn -q -B compile exec:java -Dexec.mainClass=sonimbus.poc.level1.Level1Loopback -Dexec.args="$*"
