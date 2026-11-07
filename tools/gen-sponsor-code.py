#!/usr/bin/env python3
"""Author-side unlock-code generator for Aerial 管家's mirror (同屏) feature.

The app shows the user a "设备码" (derived from their ANDROID_ID). After they sponsor,
run this with that code to get the matching 解锁码:

    python gen-sponsor-code.py                      # interactive
    python gen-sponsor-code.py <android_id>         # if the user sent the raw id
    python gen-sponsor-code.py --from-device K7F2-9QXM   # can't reverse; use ANDROID_ID

Keep SALT in sync with Unlock.java (aerial-buddy).
"""
import hashlib
import hmac
import sys

SALT = b"aerial-buddy-2026-sponsor"
ALPHABET = "0123456789ABCDEFGHJKLMNPQRSTUVWXYZ"


def code_for(android_id: str) -> str:
    raw = hmac.new(SALT, android_id.encode("utf-8"), hashlib.sha256).digest()
    chars = [ALPHABET[raw[i] % len(ALPHABET)] for i in range(10)]
    return "".join(chars[:5]) + "-" + "".join(chars[5:])


def main() -> None:
    if len(sys.argv) > 1:
        print(code_for(sys.argv[1].strip()))
        return
    print("Aerial 管家 · 赞助解锁码生成")
    print("（输入用户发来的 ANDROID_ID；设备码本身无法反推，必须要有原始 ID）")
    while True:
        try:
            value = input("ANDROID_ID> ").strip()
        except (EOFError, KeyboardInterrupt):
            break
        if not value:
            break
        print("解锁码:", code_for(value))


if __name__ == "__main__":
    main()
