#!/usr/bin/env bash
# Nivel 3e: bloquea 10 s los puertos de LiveKit (7880/TCP señal, 7881/TCP y 7882/UDP medios) en IPv4 e IPv6.
# Uso: sudo bash tools/cut-network.sh [segundos=10]
SECS="${1:-10}"
RULES=("udp --dport 7882" "udp --sport 7882" "tcp --dport 7880" "tcp --sport 7880" "tcp --dport 7881" "tcp --sport 7881")
apply() {  # apply -I | -D
  for t in iptables ip6tables; do
    for r in "${RULES[@]}"; do $t "$1" INPUT -p $r -j DROP 2>/dev/null; done
  done
}
trap 'apply -D; echo "$(date -u +%H:%M:%S.%3N)Z red restaurada"; exit' INT TERM EXIT
apply -I
echo "$(date -u +%H:%M:%S.%3N)Z CORTE iniciado ($SECS s, IPv4 + IPv6)"
sleep "$SECS"
