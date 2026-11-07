#!/usr/bin/env python3
"""Tiny license server for Aerial 管家 (同屏 / 对讲).

Run it on any always-on box (NAS, mini PC, VPS) in the same LAN or on the internet:

    python3 license-server.py --port 8787 --secret CHANGE_ME --data codes.json

Endpoints
    POST /redeem        form: device=<android_id>, code=<CODE>
                        -> {"ok":true} once; the code is then bound to that device
                        -> {"ok":false,"reason":"..."} otherwise
    GET  /admin/new?n=20&secret=...
                        -> {"codes":["ABC12-DEF34", ...]} (also printed)
    GET  /admin/list?secret=...
                        -> {"total":N,"used":M,"codes":[...]}
    GET  /health        -> {"ok":true}

Sell the generated codes through any auto-delivery shop (面包多 / 发卡网 / 爱发电私信).
One code unlocks one phone (the first device that redeems it).
"""
import argparse
import hmac
import json
import os
import random
import string
import threading
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.parse import parse_qs, urlparse

ALPHABET = string.digits + "ABCDEFGHJKLMNPQRSTUVWXYZ"
LOCK = threading.Lock()


def load(path):
    if os.path.exists(path):
        with open(path, encoding="utf-8") as f:
            return json.load(f)
    return {"codes": {}}


def save(path, data):
    tmp = path + ".tmp"
    with open(tmp, "w", encoding="utf-8") as f:
        json.dump(data, f, ensure_ascii=False, indent=1)
    os.replace(tmp, path)


def new_code():
    raw = "".join(random.choice(ALPHABET) for _ in range(10))
    return raw[:5] + "-" + raw[5:]


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--port", type=int, default=8787)
    ap.add_argument("--secret", required=True)
    ap.add_argument("--data", default="codes.json")
    args = ap.parse_args()

    class Handler(BaseHTTPRequestHandler):
        def _send(self, obj, status=200):
            body = json.dumps(obj, ensure_ascii=False).encode("utf-8")
            self.send_response(status)
            self.send_header("Content-Type", "application/json; charset=utf-8")
            self.send_header("Content-Length", str(len(body)))
            self.end_headers()
            self.wfile.write(body)

        def log_message(self, *a):
            pass

        def do_GET(self):
            u = urlparse(self.path)
            q = parse_qs(u.query)
            if u.path == "/health":
                return self._send({"ok": True})
            if u.path in ("/admin/new", "/admin/list"):
                if not hmac.compare_digest(q.get("secret", [""])[0], args.secret):
                    return self._send({"ok": False, "reason": "bad secret"}, 403)
                with LOCK:
                    data = load(args.data)
                    if u.path == "/admin/new":
                        n = min(int(q.get("n", ["10"])[0]), 500)
                        made = []
                        for _ in range(n):
                            c = new_code()
                            while c in data["codes"]:
                                c = new_code()
                            data["codes"][c] = {"device": None}
                            made.append(c)
                        save(args.data, data)
                        print("issued:", " ".join(made), flush=True)
                        return self._send({"ok": True, "codes": made})
                    codes = [{"code": k, "device": v.get("device")} for k, v in data["codes"].items()]
                    used = sum(1 for c in codes if c["device"])
                    return self._send({"ok": True, "total": len(codes), "used": used, "codes": codes})
            return self._send({"ok": False, "reason": "not found"}, 404)

        def do_POST(self):
            if urlparse(self.path).path != "/redeem":
                return self._send({"ok": False, "reason": "not found"}, 404)
            length = int(self.headers.get("Content-Length", "0"))
            form = parse_qs(self.rfile.read(length).decode("utf-8", "ignore"))
            device = form.get("device", [""])[0].strip().lower()
            code = form.get("code", [""])[0].strip().upper().replace(" ", "")
            if "-" not in code and len(code) == 10:
                code = code[:5] + "-" + code[5:]
            if not device or not code:
                return self._send({"ok": False, "reason": "缺少设备码或兑换码"}, 400)
            with LOCK:
                data = load(args.data)
                entry = data["codes"].get(code)
                if entry is None:
                    return self._send({"ok": False, "reason": "兑换码不存在"})
                owner = entry.get("device")
                if owner and owner != device:
                    return self._send({"ok": False, "reason": "这个兑换码已经被别的手机用了"})
                entry["device"] = device
                save(args.data, data)
            print("redeemed:", code, "->", device, flush=True)
            return self._send({"ok": True})

    print(f"license server on :{args.port}  data={args.data}", flush=True)
    ThreadingHTTPServer(("0.0.0.0", args.port), Handler).serve_forever()


if __name__ == "__main__":
    main()
