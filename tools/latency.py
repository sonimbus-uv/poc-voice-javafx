#!/usr/bin/env python3
"""Nivel 3g: retardo entre dos canales de un WAV estéreo de 16 bits (izq = micrófono, der = lo que
suena por la salida). Correlaciona las envolventes (resolución 1 ms) y da el desfase con mayor parecido.
Uso: latency.py archivo.wav [max_ms=1500]"""
import struct, sys, wave

path = sys.argv[1]
max_ms = int(sys.argv[2]) if len(sys.argv) > 2 else 1500
w = wave.open(path)
assert w.getnchannels() == 2 and w.getsampwidth() == 2, "se espera WAV estéreo de 16 bits"
rate, n = w.getframerate(), w.getnframes()
data = struct.unpack("<%dh" % (2 * n), w.readframes(n))
step = rate // 1000


def envelope(ch):
    env = [sum(abs(data[2 * i + ch]) for i in range(s, s + step)) / step for s in range(0, n - step, step)]
    mean = sum(env) / len(env)
    return [v - mean for v in env], max(env)


mic, mic_peak = envelope(0)
out, out_peak = envelope(1)
print(f"duracion={n / rate:.1f}s pico_mic={mic_peak:.0f} pico_salida={out_peak:.0f} (escala 0-32767)")
if mic_peak < 500 or out_peak < 100:
    print("AVISO: señal muy baja en algún canal; el resultado no es fiable")


def score(lag):
    if lag >= 0:
        pairs = zip(mic[: len(mic) - lag], out[lag:])
    else:
        pairs = zip(mic[-lag:], out[: len(out) + lag])
    return sum(a * b for a, b in pairs)


lo = -200
scores = [(score(lag), lag) for lag in range(lo, max_ms)]
best, lag = max(scores)
norm = (sum(a * a for a in mic) * sum(b * b for b in out)) ** 0.5 or 1
print(f"retardo_ms={lag} correlacion={best / norm:.2f} (1.00 = idéntico; por debajo de ~0.3 no es fiable)")
top = sorted(scores, reverse=True)[:5]
print("mejores desfases (ms):", [l for _, l in top])
