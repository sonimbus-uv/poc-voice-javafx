# Token de prueba para el navegador (solo Nivel 0). Uso: python3 tools/token.py <identidad> [sala]
import base64, hashlib, hmac, json, sys, time
b = lambda d: base64.urlsafe_b64encode(d).rstrip(b"=")
ident, room = sys.argv[1], (sys.argv[2] if len(sys.argv) > 2 else "poc")
h = b(json.dumps({"alg": "HS256", "typ": "JWT"}).encode())
p = b(json.dumps({"iss": "devkey", "sub": ident, "nbf": int(time.time()) - 10, "exp": int(time.time()) + 6 * 3600,
                  "video": {"roomJoin": True, "room": room}}).encode())
s = b(hmac.new(b"secret", h + b"." + p, hashlib.sha256).digest())
print((h + b"." + p + b"." + s).decode())
