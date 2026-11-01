#!/usr/bin/env python3
"""Bản tham chiếu ĐỘC LẬP (Python) cho core nhạc YouTube của AvatarDK.

Chứng minh đường innertube không cần API key riêng:
  1. search  -> danh sách video (id, tiêu đề, kênh, thời lượng, bìa)
  2. player (client ANDROID) -> link audio trực tiếp googlevideo (không cipher)
  3. playlist (browse) -> tên playlist + các bài

Chạy: python3 yt_ref.py search "từ khoá" | player <videoId> | playlist <listId>
Kết quả cũng được ghi ra tools/yt-fixtures/ để làm fixture test cho bản Java.
"""
import json
import subprocess
import sys
import urllib.request

FIX = "tools/yt-fixtures"
API_KEY = "AIzaSyAO_FJ2SlqU8Q4STEHLGCilw_Y9_11qcW8"  # khoá công khai của web YouTube

WEB_CTX = {"client": {"clientName": "WEB", "clientVersion": "2.20240111.09.00",
                      "hl": "vi", "gl": "VN"}}
ANDROID_CTX = {"client": {"clientName": "ANDROID", "clientVersion": "19.09.37",
                          "androidSdkVersion": 30, "hl": "vi", "gl": "VN",
                          "userAgent": "com.google.android.youtube/19.09.37 (Linux; U; Android 11) gzip"}}


def post(endpoint, ctx, extra):
    body = {"context": ctx}
    body.update(extra)
    req = urllib.request.Request(
        f"https://www.youtube.com/youtubei/v1/{endpoint}?key={API_KEY}",
        data=json.dumps(body).encode(),
        headers={"Content-Type": "application/json",
                 "User-Agent": ctx["client"].get("userAgent", "Mozilla/5.0")})
    with urllib.request.urlopen(req, timeout=30) as r:
        return json.loads(r.read().decode())


def walk(node, key, out):
    if isinstance(node, dict):
        if key in node:
            out.append(node[key])
        for v in node.values():
            walk(v, key, out)
    elif isinstance(node, list):
        for v in node:
            walk(v, key, out)


def text_of(node):
    if not node:
        return ""
    if "simpleText" in node:
        return node["simpleText"]
    return "".join(r.get("text", "") for r in node.get("runs", []))


def parse_search(data):
    renderers = []
    walk(data, "videoRenderer", renderers)
    videos = []
    for vr in renderers:
        vid = vr.get("videoId")
        if not vid:
            continue
        thumbs = vr.get("thumbnail", {}).get("thumbnails", [])
        videos.append({
            "id": vid,
            "title": text_of(vr.get("title")),
            "channel": text_of(vr.get("ownerText")) or text_of(vr.get("longBylineText")),
            "duration": text_of(vr.get("lengthText")),
            "thumb": thumbs[-1]["url"] if thumbs else "",
        })
    return videos


def parse_player_audio(data):
    sd = data.get("streamingData") or {}
    fmts = (sd.get("adaptiveFormats") or []) + (sd.get("formats") or [])
    audios = [f for f in fmts if str(f.get("mimeType", "")).startswith("audio/")]
    audios.sort(key=lambda f: f.get("bitrate", 0), reverse=True)
    details = (data.get("videoDetails") or {})
    return {
        "title": details.get("title"),
        "author": details.get("author"),
        "lengthSec": int(details.get("lengthSeconds") or 0),
        "playable": (data.get("playabilityStatus") or {}).get("status"),
        "audios": [{
            "itag": f.get("itag"),
            "mime": f.get("mimeType"),
            "bitrate": f.get("bitrate"),
            "hasUrl": "url" in f,
            "hasCipher": "signatureCipher" in f or "cipher" in f,
            "url": f.get("url", "")[:110],
        } for f in audios[:5]],
        "bestUrl": audios[0].get("url") if audios else None,
    }


def parse_playlist(data, list_id):
    # YouTube hiện tại trả mục playlist bằng lockupViewModel (renderer cũ
    # playlistVideoRenderer chỉ còn ở vài mặt cũ — hỗ trợ cả hai).
    lockups = []
    walk(data, "lockupViewModel", lockups)
    videos = []
    for lv in lockups:
        if lv.get("contentType") != "LOCKUP_CONTENT_TYPE_VIDEO":
            continue
        vid = lv.get("contentId")
        if not vid:
            continue
        md = (lv.get("metadata") or {}).get("lockupMetadataViewModel") or {}
        rows = ((md.get("metadata") or {}).get("contentMetadataViewModel") or {}).get("metadataRows") or []
        channel = ""
        if rows and rows[0].get("metadataParts"):
            channel = ((rows[0]["metadataParts"][0].get("text") or {}).get("content")) or ""
        img = ((lv.get("contentImage") or {}).get("thumbnailViewModel") or {})
        sources = (img.get("image") or {}).get("sources") or []
        duration = ""
        for ov in img.get("overlays") or []:
            badges = ((ov.get("thumbnailBottomOverlayViewModel") or {}).get("badges")) or []
            if badges:
                duration = ((badges[0].get("thumbnailBadgeViewModel") or {}).get("text")) or ""
        videos.append({
            "id": vid,
            "title": (md.get("title") or {}).get("content", ""),
            "channel": channel,
            "duration": duration,
            "thumb": sources[-1]["url"] if sources else f"https://i.ytimg.com/vi/{vid}/hqdefault.jpg",
        })
    if not videos:
        renderers = []
        walk(data, "playlistVideoRenderer", renderers)
        for vr in renderers:
            vid = vr.get("videoId")
            if not vid:
                continue
            thumbs = vr.get("thumbnail", {}).get("thumbnails", [])
            videos.append({
                "id": vid,
                "title": text_of(vr.get("title")),
                "channel": text_of(vr.get("shortBylineText")),
                "duration": text_of(vr.get("lengthText")),
                "thumb": thumbs[-1]["url"] if thumbs else "",
            })
    titles = []
    walk(data, "playlistHeaderRenderer", titles)
    name = ""
    if titles:
        name = text_of(titles[0].get("title"))
    return {"id": list_id, "name": name, "videos": videos}


def main():
    cmd = sys.argv[1]
    if cmd == "search":
        data = post("search", WEB_CTX, {"query": sys.argv[2]})
        with open(f"{FIX}/search_raw.json", "w") as f:
            json.dump(data, f)
        vids = parse_search(data)
        print(json.dumps(vids[:8], ensure_ascii=False, indent=1))
    elif cmd == "player":
        data = post("player", ANDROID_CTX, {"videoId": sys.argv[2]})
        with open(f"{FIX}/player_raw.json", "w") as f:
            json.dump(data, f)
        info = parse_player_audio(data)
        print(json.dumps(info, ensure_ascii=False, indent=1))
        if info["bestUrl"]:
            with open(f"{FIX}/best_audio_url.txt", "w") as f:
                f.write([f for f in (data.get("streamingData") or {}).get("adaptiveFormats", [])
                         if str(f.get("mimeType", "")).startswith("audio/")][0].get("url", ""))
    elif cmd == "playlist":
        data = post("browse", WEB_CTX, {"browseId": "VL" + sys.argv[2]})
        with open(f"{FIX}/playlist_raw.json", "w") as f:
            json.dump(data, f)
        print(json.dumps(parse_playlist(data, sys.argv[2]), ensure_ascii=False, indent=1)[:2500])


if __name__ == "__main__":
    main()
