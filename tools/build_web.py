#!/usr/bin/env python3
"""Biên dịch bản WEB (HTML5/GWT) của Thị Trấn Mặt Trời và đóng gói war
tĩnh vào build/web/ (đưa thư mục này lên GitHub Pages là chơi được).

Cách dùng: python3 tools/build_web.py [--draft]
  --draft: biên dịch nháp (nhanh, JS dễ đọc) để gỡ lỗi; bản thường là
  bản tối ưu OBF cho người chơi.

Đồ nghề (đã tải sẵn, không cần mạng):
  ~/workspace/tools/gwt-libs/gwt-2.11.0/  (GWT SDK: gwt-user + gwt-dev)
  ~/workspace/tools/gwt-libs/*.jar        (gdx + backend-gwt + sources)
"""
import os
import shutil
import subprocess
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
TOOLS = os.path.expanduser("~/workspace/tools")
GWT = os.path.join(TOOLS, "gwt-libs", "gwt-2.11.0")
LIBS = os.path.join(TOOLS, "gwt-libs")
JDK = os.path.join(TOOLS, "jdk17", "bin", "java")
OUT = os.path.join(ROOT, "build", "web")
SRC_ASSETS = os.path.join(ROOT, "build", "web-src-assets")
DRAFT = "--draft" in sys.argv


def prepare_web_assets():
    """Chép cây assets sang bản riêng cho web và trung hoà hai trường dữ
    liệu mà bộ parse JSON của GWT (JsonReader sinh bằng Ragel) xử lý
    lỗi — KHÔNG phải escape như lầm tưởng ban đầu (đã chia nhỏ kiểm
    chứng từng ký tự 2026-11-02):

    - items.json/animation: JsonReader của GWT chết RangeError "Invalid
      array length" khi một CHUỔI chứa SỐ nằm trong ngoặc nhọn (đã chia
      nhỏ kiểm chứng 2026-11-02: '{0}' và '{a:0}' chết; '{a}', '{a:b}',
      'a:0', '[abc]', chuỗi rỗng đều qua). Chuỗi animation gốc chứa đúng
      mẫu đó ('[{img:0,dx:-12,...}]') nên bản web chuyển animation thành
      MẢNG SỐ NGUYÊN PHẲNG [img,dx,dy,...] rút theo đúng cách parse gốc
      (số nguyên trong mỗi cặp ngoặc, từng bộ ba); AvatarDataSet đọc được
      cả hai dạng. Tên món, chat NPC và các file JSON còn lại đã quét:
      không chứa mẫu này.
    - npcs.json/chat (7 NPC): giải mã các câu thoại rồi nối bằng ' | ';
      AvatarDataSet có nhánh dự phòng đọc đúng dạng này (không chạy
      với dữ liệu gốc).
    Các file JSON còn lại đã quét không chứa mẫu nguy hiểm, giữ nguyên.
    """
    import json
    if os.path.isdir(SRC_ASSETS):
        shutil.rmtree(SRC_ASSETS)
    shutil.copytree(os.path.join(ROOT, "assets"), SRC_ASSETS)

    items_path = os.path.join(SRC_ASSETS, "data", "items.json")
    items = json.load(open(items_path, encoding="utf-8"))
    import re as _re
    for part in items:
        anim = part.get("animation", "") or ""
        ints = []
        for grp in _re.findall(r"\{([^{}]*)\}", anim):
            ints.extend(int(x) for x in _re.findall(r"-?\d+", grp))
        part["animation"] = ints
    with open(items_path, "w", encoding="utf-8") as f:
        json.dump(items, f, ensure_ascii=False)

    npcs_path = os.path.join(SRC_ASSETS, "data", "npcs.json")
    npcs = json.load(open(npcs_path, encoding="utf-8"))
    import re
    for npc in npcs:
        chat = npc.get("chat", "")
        if "\\" in chat:
            stripped = chat.replace("\\", "")
            segs = [s.strip() for s in re.findall(r'"([^"]*)"', stripped)]
            segs = [s for s in segs if s]
            npc["chat"] = " | ".join(segs) if segs else "[]"
    with open(npcs_path, "w", encoding="utf-8") as f:
        json.dump(npcs, f, ensure_ascii=False)
    print("web assets prepared:", SRC_ASSETS)


def write_build_module():
    """Module GWT sinh tự động cho lần build này: trỏ assetpath vào cây
    assets đã trung hoà (đường tuyệt đối theo máy build)."""
    path = os.path.join(ROOT, "html", "src", "main", "java",
                        "com", "avatardk", "game", "client", "WebGameBuild.gwt.xml")
    with open(path, "w", encoding="utf-8") as f:
        f.write('<?xml version="1.0" encoding="UTF-8"?>\n'
                '<!DOCTYPE module PUBLIC "-//Google Inc.//DTD Google Web Toolkit 2.11.0//EN" '
                '"http://www.gwtproject.org/doctype/2.11.0/gwt-module.dtd">\n'
                '<!-- FILE SINH TỰ ĐỘNG bởi tools/build_web.py — đừng sửa tay. -->\n'
                '<module rename-to="html">\n'
                '    <inherits name="com.avatardk.game.client.WebGame"/>\n'
                f'    <set-configuration-property name="gdx.assetpath" value="{SRC_ASSETS}"/>\n'
                '    <set-configuration-property name="gdx.assetoutputpath" value="build/web/"/>\n'
                '</module>\n')
    return path


def main():
    prepare_web_assets()
    write_build_module()
    cp = [
        os.path.join(GWT, "gwt-user.jar"),
        os.path.join(GWT, "gwt-dev.jar"),
        os.path.join(LIBS, "gdx-1.14.0.jar"),
        os.path.join(LIBS, "gdx-1.14.0-sources.jar"),
        os.path.join(LIBS, "gdx-backend-gwt-1.14.0.jar"),
        os.path.join(LIBS, "gdx-backend-gwt-1.14.0-sources.jar"),
        os.path.join(LIBS, "jsinterop-annotations-2.0.2-sources.jar"),
        os.path.join(ROOT, "core", "src", "main", "java"),
        os.path.join(ROOT, "html", "src", "main", "java"),
    ]
    classpath = ":".join(cp)
    if os.path.isdir(OUT):
        shutil.rmtree(OUT)
    os.makedirs(OUT)
    cmd = [JDK, "-Xmx3g", "-Dfile.encoding=UTF-8", "-cp", classpath,
           "com.google.gwt.dev.Compiler",
           "-war", OUT, "-localWorkers", "2",
           "-style", "PRETTY" if DRAFT else "OBF",
           "-logLevel", "INFO"]
    if DRAFT:
        cmd += ["-draftCompile"]
    cmd.append("com.avatardk.game.client.WebGameBuild")
    print("+", " ".join(cmd))
    r = subprocess.run(cmd, cwd=ROOT)
    if r.returncode != 0:
        print("GWT COMPILE FAILED")
        sys.exit(1)
    # trang chủ (assets đã được PreloaderBundleGenerator của backend GWT
    # tự chép vào build/web/assets/ trong lúc biên dịch)
    shutil.copy(os.path.join(ROOT, "html", "src", "main", "webapp", "index.html"),
                os.path.join(OUT, "index.html"))
    total = sum(os.path.getsize(os.path.join(dp, f))
                for dp, _d, fs in os.walk(OUT) for f in fs)
    print(f"WEB BUILD OK -> {OUT} (total {total/1048576:.1f} MB)")


if __name__ == "__main__":
    main()
