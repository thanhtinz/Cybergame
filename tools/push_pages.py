#!/usr/bin/env python3
"""Đẩy build/web/ lên nhánh gh-pages của thanhtinz/Cybergame bằng GitHub
Git Data API (bản web chơi thử trên GitHub Pages). Mỗi lần đẩy là một
commit mới trên gh-pages (cha = commit gh-pages trước nếu có).

Dùng: python3 tools/push_pages.py "ghi chú"
"""
import base64
import json
import os
import subprocess
import sys
import urllib.request

REPO = "thanhtinz/Cybergame"
API = f"https://api.github.com/repos/{REPO}"
ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
WEB = os.path.join(ROOT, "build", "web")


def call(method, url, token, payload=None):
    req = urllib.request.Request(url, method=method,
        data=json.dumps(payload).encode() if payload is not None else None,
        headers={"Authorization": f"Bearer {token}",
                 "Accept": "application/vnd.github+json",
                 "Content-Type": "application/json"})
    with urllib.request.urlopen(req, timeout=180) as r:
        return json.loads(r.read().decode())


def main():
    message = sys.argv[1] if len(sys.argv) > 1 else "Cap nhat ban web"
    token = subprocess.run(["gh", "auth", "token"], capture_output=True,
                           text=True, check=True).stdout.strip()
    # thu thập file (bỏ WEB-INF — Pages không cần)
    paths = []
    for dp, _dn, fs in os.walk(WEB):
        for f in fs:
            full = os.path.join(dp, f)
            rel = os.path.relpath(full, WEB).replace(os.sep, "/")
            if rel.startswith("WEB-INF/"):
                continue
            paths.append((rel, full))
    paths.sort()
    print("files:", len(paths))
    entries = []
    for i, (rel, full) in enumerate(paths):
        with open(full, "rb") as f:
            content = base64.b64encode(f.read()).decode()
        blob = call("POST", f"{API}/git/blobs", token,
                    {"content": content, "encoding": "base64"})
        entries.append({"path": rel, "mode": "100644", "type": "blob",
                        "sha": blob["sha"]})
        if (i + 1) % 50 == 0:
            print("  blobs", i + 1, "/", len(paths))
    entries.append({"path": ".nojekyll", "mode": "100644", "type": "blob",
                    "content": ""})
    tree = call("POST", f"{API}/git/trees", token, {"tree": entries})
    parents = []
    try:
        ref = call("GET", f"{API}/git/refs/heads/gh-pages", token)
        parents = [ref["object"]["sha"]]
    except Exception:
        pass
    commit = call("POST", f"{API}/git/commits", token,
                  {"message": message, "tree": tree["sha"], "parents": parents})
    if parents:
        call("PATCH", f"{API}/git/refs/heads/gh-pages", token,
             {"sha": commit["sha"], "force": True})
    else:
        call("POST", f"{API}/git/refs", token,
             {"ref": "refs/heads/gh-pages", "sha": commit["sha"]})
    print("gh-pages ->", commit["sha"][:12])


if __name__ == "__main__":
    main()
