#!/usr/bin/env python3
"""Khoi phuc tai nguyen nhi phan (anh/font) tu cac file .b64 trong assets-src/.

Chay 1 lan sau khi clone:  python3 tools/restore_assets.py
Moi file assets-src/<duong-dan-tuong-doi>.b64 se duoc giai ma ve dung vi tri
trong cay thu muc du an (vi du assets-src/core/src/main/resources/art/keyart.jpg.b64
-> core/src/main/resources/art/keyart.jpg).
"""
import base64
import pathlib
import sys

ROOT = pathlib.Path(__file__).resolve().parent.parent
SRC = ROOT / "assets-src"


def main() -> int:
    files = sorted(SRC.rglob("*.b64"))
    if not files:
        print("Khong tim thay file .b64 nao trong", SRC)
        return 1
    for f in files:
        rel = f.relative_to(SRC)
        target = ROOT / str(rel)[: -len(".b64")]
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_bytes(base64.b64decode(f.read_text(encoding="ascii")))
        print("OK", rel, "->", target.relative_to(ROOT))
    return 0


if __name__ == "__main__":
    sys.exit(main())
