#!/usr/bin/env python3
"""Đẩy một danh sách file của project lên thanhtinz/Cybergame (nhánh main)
bằng GitHub Git Data API — project không phải git repo local nên đi
đường này: ref -> blobs -> tree (base_tree) -> commit -> PATCH ref.

Dùng: python3 tools/push_github.py "nội dung commit" file1 file2 ...
Token lấy từ `gh auth token`. Đường dẫn file tính theo gốc project
(= gốc repo)."""
import base64
import json
import subprocess
import sys
import urllib.request

REPO = "thanhtinz/Cybergame"
API = f"https://api.github.com/repos/{REPO}"


def call(method, url, token, payload=None):
    req = urllib.request.Request(url, method=method,
                                 data=json.dumps(payload).encode() if payload is not None else None,
                                 headers={"Authorization": f"Bearer {token}",
                                          "Accept": "application/vnd.github+json",
                                          "Content-Type": "application/json"})
    with urllib.request.urlopen(req, timeout=120) as r:
        return json.loads(r.read().decode())


def main():
    message = sys.argv[1]
    files = sys.argv[2:]
    token = subprocess.check_output(["gh", "auth", "token"]).decode().strip()
    ref = call("GET", f"{API}/git/refs/heads/main", token)
    base_sha = ref["object"]["sha"]
    base_commit = call("GET", f"{API}/git/commits/{base_sha}", token)
    base_tree = base_commit["tree"]["sha"]
    print("base:", base_sha[:12], "| files:", len(files))
    entries = []
    for path in files:
        with open(path, "rb") as f:
            content = base64.b64encode(f.read()).decode()
        blob = call("POST", f"{API}/git/blobs", token,
                    {"content": content, "encoding": "base64"})
        entries.append({"path": path, "mode": "100644", "type": "blob", "sha": blob["sha"]})
        print("  blob", path)
    tree = call("POST", f"{API}/git/trees", token,
                {"base_tree": base_tree, "tree": entries})
    commit = call("POST", f"{API}/git/commits", token,
                  {"message": message, "tree": tree["sha"], "parents": [base_sha]})
    call("PATCH", f"{API}/git/refs/heads/main", token, {"sha": commit["sha"]})
    print("PUSHED:", commit["sha"][:12], "-", message)


if __name__ == "__main__":
    main()
