#!/usr/bin/env python3
"""push_pages.py bản chịu lỗi mạng: retry từng blob, lưu entries ra file
để chạy lại bước tree/commit mà không up lại blob; tree lớn thì chia
nhỏ bằng base_tree; in NGUYÊN VĂN body lỗi của GitHub.

Dùng: python3 tools/push_pages2.py "ghi chú"
"""
import base64
import json
import os
import subprocess
import sys
import time
import urllib.error
import urllib.request

REPO = "thanhtinz/Cybergame"
API = f"https://api.github.com/repos/{REPO}"
ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
WEB = os.path.join(ROOT, "build", "web")
STATE = os.path.expanduser("~/workspace/logs-avatardk/pages_entries.json")


def call(method, url, token, payload=None, tries=4):
    last = None
    for t in range(tries):
        try:
            req = urllib.request.Request(url, method=method,
                data=json.dumps(payload).encode() if payload is not None else None,
                headers={"Authorization": f"Bearer {token}",
                         "Accept": "application/vnd.github+json",
                         "Content-Type": "application/json"})
            with urllib.request.urlopen(req, timeout=180) as r:
                return json.loads(r.read().decode())
        except urllib.error.HTTPError as e:
            body = e.read().decode(errors="replace")
            print(f"  HTTP {e.code} {method} {url.rsplit('/', 2)[-1]}: {body[:400]}")
            if e.code in (422, 400, 404):
                raise
            last = e
        except Exception as e:  # RemoteDisconnected, timeout...
            print(f"  net err ({type(e).__name__}) try {t + 1}/{tries}")
            last = e
        time.sleep(3 * (t + 1))
    raise last


def main():
    message = sys.argv[1] if len(sys.argv) > 1 else "Cap nhat ban web"
    token = subprocess.run(["gh", "auth", "token"], capture_output=True,
                           text=True, check=True).stdout.strip()
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
    if os.path.exists(STATE):
        with open(STATE, encoding="utf-8") as f:
            saved = json.load(f)
        if [e["path"] for e in saved] == [p for p, _ in paths]:
            entries = saved
            print("reuse saved entries:", len(entries))
    if not entries:
        for i, (rel, full) in enumerate(paths):
            with open(full, "rb") as f:
                content = base64.b64encode(f.read()).decode()
            blob = call("POST", f"{API}/git/blobs", token,
                        {"content": content, "encoding": "base64"})
            entries.append({"path": rel, "mode": "100644", "type": "blob",
                            "sha": blob["sha"]})
            if (i + 1) % 50 == 0:
                print("  blobs", i + 1, "/", len(paths), flush=True)
                with open(STATE, "w", encoding="utf-8") as f:
                    json.dump(entries, f)
        with open(STATE, "w", encoding="utf-8") as f:
            json.dump(entries, f)

    all_entries = entries + [{"path": ".nojekyll", "mode": "100644",
                              "type": "blob", "content": ""}]
    # thử tree một phát; 422 thì chia nhỏ bằng base_tree
    try:
        tree = call("POST", f"{API}/git/trees", token, {"tree": all_entries})
        tree_sha = tree["sha"]
    except urllib.error.HTTPError:
        print("full tree failed -> chunked base_tree mode")
        tree_sha = None
        CHUNK = 100
        for i in range(0, len(all_entries), CHUNK):
            part = all_entries[i:i + CHUNK]
            payload = {"tree": part}
            if tree_sha:
                payload["base_tree"] = tree_sha
            tree = call("POST", f"{API}/git/trees", token, payload)
            tree_sha = tree["sha"]
            print("  tree chunk", i + len(part), "/", len(all_entries), flush=True)

    parents = []
    try:
        ref = call("GET", f"{API}/git/refs/heads/gh-pages", token)
        parents = [ref["object"]["sha"]]
    except Exception:
        pass
    commit = call("POST", f"{API}/git/commits", token,
                  {"message": message, "tree": tree_sha, "parents": parents})
    if parents:
        call("PATCH", f"{API}/git/refs/heads/gh-pages", token,
             {"sha": commit["sha"], "force": True})
    else:
        call("POST", f"{API}/git/refs", token,
             {"ref": "refs/heads/gh-pages", "sha": commit["sha"]})
    print("gh-pages ->", commit["sha"][:12])


if __name__ == "__main__":
    main()
