package com.avatardk.game.music;

import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Client YouTube "innertube" không cần API key riêng — dùng các khoá công
 * khai vẫn nhúng trong web/app YouTube, đúng cách các trình nghe nhạc mã
 * nguồn mở vẫn làm:
 *
 * <ul>
 *   <li>Tìm kiếm: client WEB, endpoint youtubei/v1/search.</li>
 *   <li>Lấy luồng audio: client ANDROID_VR kèm visitorData lấy từ một
 *       phiên gọi trước đó — tổ hợp này trả link googlevideo TRỰC TIẾP
 *       (không mã hoá chữ ký). Đã kiểm chứng thật 2026-11-01: thiếu
 *       visitorData thì YouTube trả LOGIN_REQUIRED "confirm you're not a
 *       bot", có visitorData thì OK với đủ format audio (opus 251, AAC
 *       140/139 đều có url thẳng).</li>
 *   <li>Playlist: browse "VL"+id, mục bài nằm trong lockupViewModel (kiểu
 *       mới) hoặc playlistVideoRenderer (kiểu cũ).</li>
 * </ul>
 *
 * Mọi hàm đều chặn (blocking) — gọi từ thread nền. Đây là đường không
 * chính thức: YouTube đổi cấu trúc trả về là phải cập nhật bộ bóc tách.
 */
public class YoutubeClient {

    public static class YoutubeException extends IOException {
        public YoutubeException(String message) {
            super(message);
        }
    }

    // Khoá công khai của web YouTube và của app Android (nhúng sẵn trong
    // chính các client đó — không phải khoá bí mật của ai).
    private static final String WEB_KEY = "AIzaSyAO_FJ2SlqU8Q4STEHLGCilw_Y9_11qcW8";
    private static final String VR_KEY = "AIzaSyA8eiZmM1FaDVjRy-df2KTyQ_vz_yYM39w";
    private static final String VR_UA =
        "com.google.android.apps.youtube.vr.oculus/1.65 (Linux; U; Android 12L; eureka-user Build/SQ3A.220605.009.A1) gzip";
    private static final String WEB_CTX =
        "\"clientName\":\"WEB\",\"clientVersion\":\"2.20240111.09.00\",\"hl\":\"vi\",\"gl\":\"VN\"";
    private static final String VR_CTX =
        "\"clientName\":\"ANDROID_VR\",\"clientVersion\":\"1.65\",\"deviceModel\":\"Quest 3\","
            + "\"androidSdkVersion\":32,\"osName\":\"Android\",\"osVersion\":\"12L\","
            + "\"hl\":\"en\",\"gl\":\"US\",\"userAgent\":\"" + VR_UA + "\"";

    private volatile String visitorData;
    private final JsonReader json = new JsonReader();

    // ------------------------------------------------------------ API

    /** Tìm video theo từ khoá (trang đầu, tối đa khoảng 20 kết quả). */
    public List<YtVideo> search(String query) throws IOException {
        JsonValue root = post("search", WEB_KEY, "1", "2.20240111.09.00", "Mozilla/5.0",
            WEB_CTX, false, "\"query\":\"" + esc(query) + "\"");
        return parseSearch(root);
    }

    /** Nhập một playlist YouTube từ link hoặc id trần. */
    public YtPlaylistData fetchPlaylist(String linkOrId) throws IOException {
        String id = extractPlaylistId(linkOrId);
        if (id == null || id.isEmpty()) {
            throw new YoutubeException("Link playlist không hợp lệ — hãy dán link có dạng youtube.com/playlist?list=…");
        }
        JsonValue root = post("browse", WEB_KEY, "1", "2.20240111.09.00", "Mozilla/5.0",
            WEB_CTX, false, "\"browseId\":\"VL" + esc(id) + "\"");
        YtPlaylistData pl = parsePlaylist(root, id);
        if (pl.videos.isEmpty()) {
            throw new YoutubeException("Không đọc được bài nào trong playlist (có thể playlist riêng tư hoặc đã bị xoá).");
        }
        return pl;
    }

    /** Lấy luồng audio tốt nhất (bitrate cao nhất còn link trực tiếp). */
    public YtStream resolveStream(String videoId) throws IOException {
        ensureVisitor();
        JsonValue root = post("player", VR_KEY, "21", "1.65", VR_UA,
            VR_CTX, true, "\"videoId\":\"" + esc(videoId) + "\",\"racyCheckOk\":true,\"contentCheckOk\":true");
        return parsePlayer(root, videoId);
    }

    /** Tải bytes của một URL (dùng cho ảnh bìa). */
    public byte[] fetchBytes(String url) throws IOException {
        HttpURLConnection c = open(new URL(url));
        c.setRequestProperty("User-Agent", "Mozilla/5.0");
        c.setConnectTimeout(15000);
        c.setReadTimeout(20000);
        try (InputStream in = c.getInputStream()) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[16384];
            int n;
            while ((n = in.read(buf)) >= 0) out.write(buf, 0, n);
            return out.toByteArray();
        } finally {
            c.disconnect();
        }
    }

    /** Bóc id playlist từ link đầy đủ, link rút gọn hoặc id trần. */
    public static String extractPlaylistId(String input) {
        if (input == null) return null;
        String s = input.trim();
        int i = s.indexOf("list=");
        if (i >= 0) {
            s = s.substring(i + 5);
            int cut = s.indexOf('&');
            if (cut >= 0) s = s.substring(0, cut);
            cut = s.indexOf('#');
            if (cut >= 0) s = s.substring(0, cut);
            return s.trim();
        }
        if (s.matches("[A-Za-z0-9_-]{10,}")) return s;
        return null;
    }

    // ------------------------------------------------------------ HTTP

    private void ensureVisitor() throws IOException {
        if (visitorData != null) return;
        post("browse", WEB_KEY, "1", "2.20240111.09.00", "Mozilla/5.0",
            WEB_CTX, false, "\"browseId\":\"FEwhat_to_watch\"");
        if (visitorData == null) {
            throw new YoutubeException("Không lấy được phiên khách YouTube (visitorData) — kiểm tra mạng rồi thử lại.");
        }
    }

    private JsonValue post(String endpoint, String key, String clientNameId, String clientVersion,
                           String ua, String clientCtx, boolean withVisitor, String extraFields)
        throws IOException {
        StringBuilder body = new StringBuilder("{\"context\":{\"client\":{");
        body.append(clientCtx).append('}');
        if (withVisitor && visitorData != null) {
            body.append(",\"visitorData\":\"").append(esc(visitorData)).append('"');
        }
        body.append("},").append(extraFields).append('}');
        HttpURLConnection c = open(new URL("https://www.youtube.com/youtubei/v1/" + endpoint + "?key=" + key));
        c.setRequestMethod("POST");
        c.setDoOutput(true);
        c.setConnectTimeout(15000);
        c.setReadTimeout(25000);
        c.setRequestProperty("Content-Type", "application/json");
        c.setRequestProperty("User-Agent", ua);
        c.setRequestProperty("X-YouTube-Client-Name", clientNameId);
        c.setRequestProperty("X-YouTube-Client-Version", clientVersion);
        if (visitorData != null) c.setRequestProperty("X-Goog-Visitor-Id", visitorData);
        byte[] payload = body.toString().getBytes(StandardCharsets.UTF_8);
        try (OutputStream out = c.getOutputStream()) {
            out.write(payload);
        }
        int code = c.getResponseCode();
        InputStream in = code >= 200 && code < 300 ? c.getInputStream() : c.getErrorStream();
        if (in == null) throw new YoutubeException("YouTube trả lỗi HTTP " + code + " (không có nội dung).");
        String text;
        try (InputStream rin = in) {
            ByteArrayOutputStream buf = new ByteArrayOutputStream();
            byte[] b = new byte[16384];
            int n;
            while ((n = rin.read(b)) >= 0) buf.write(b, 0, n);
            text = buf.toString(StandardCharsets.UTF_8);
        } finally {
            c.disconnect();
        }
        if (code < 200 || code >= 300) {
            throw new YoutubeException("YouTube trả lỗi HTTP " + code + " ở " + endpoint + ".");
        }
        JsonValue root = json.parse(text);
        JsonValue ctx = root.get("responseContext");
        if (ctx != null) {
            String vd = str(ctx.get("visitorData"));
            if (vd != null && !vd.isEmpty()) visitorData = vd;
        }
        return root;
    }

    private HttpURLConnection open(URL url) throws IOException {
        Proxy proxy = proxyFromEnv();
        return (HttpURLConnection) (proxy == null ? url.openConnection() : url.openConnection(proxy));
    }

    private static Proxy proxyFromEnv() {
        String p = System.getenv("https_proxy");
        if (p == null || p.isEmpty()) p = System.getenv("HTTPS_PROXY");
        if (p == null || p.isEmpty()) p = System.getenv("http_proxy");
        if (p == null || p.isEmpty()) return null;
        try {
            URL u = new URL(p);
            return new Proxy(Proxy.Type.HTTP, new InetSocketAddress(u.getHost(), u.getPort()));
        } catch (Exception e) {
            return null;
        }
    }

    // ------------------------------------------------------------ Bóc tách (tĩnh, có test)

    public static List<YtVideo> parseSearch(JsonValue root) {
        List<YtVideo> out = new ArrayList<>();
        List<JsonValue> renderers = new ArrayList<>();
        collect(root, "videoRenderer", renderers);
        Set<String> seen = new LinkedHashSet<>();
        for (JsonValue vr : renderers) {
            String id = str(vr.get("videoId"));
            if (id == null || !seen.add(id)) continue;
            YtVideo v = new YtVideo();
            v.id = id;
            v.title = textOf(vr.get("title"));
            v.channel = textOf(vr.get("ownerText"));
            if (v.channel.isEmpty()) v.channel = textOf(vr.get("longBylineText"));
            if (v.channel.isEmpty()) v.channel = textOf(vr.get("shortBylineText"));
            v.durationSec = YtVideo.parseDuration(textOf(vr.get("lengthText")));
            v.thumbUrl = lastThumb(vr.get("thumbnail"));
            out.add(v);
        }
        return out;
    }

    public static YtPlaylistData parsePlaylist(JsonValue root, String id) {
        YtPlaylistData pl = new YtPlaylistData();
        pl.id = id;
        List<JsonValue> headers = new ArrayList<>();
        collect(root, "playlistHeaderRenderer", headers);
        if (!headers.isEmpty()) pl.name = textOf(headers.get(0).get("title"));
        Set<String> seen = new LinkedHashSet<>();
        // Kiểu mới: lockupViewModel
        List<JsonValue> lockups = new ArrayList<>();
        collect(root, "lockupViewModel", lockups);
        for (JsonValue lv : lockups) {
            if (!"LOCKUP_CONTENT_TYPE_VIDEO".equals(str(lv.get("contentType")))) continue;
            String vid = str(lv.get("contentId"));
            if (vid == null || !seen.add(vid)) continue;
            YtVideo v = new YtVideo();
            v.id = vid;
            JsonValue md = child(child(lv, "metadata"), "lockupMetadataViewModel");
            if (md != null) {
                JsonValue title = md.get("title");
                v.title = title == null ? "" : nz(str(title.get("content")));
                JsonValue rows = child(child(md.get("metadata"), "contentMetadataViewModel"), "metadataRows");
                if (rows != null && rows.size > 0) {
                    JsonValue parts = rows.get(0).get("metadataParts");
                    if (parts != null && parts.size > 0) {
                        v.channel = nz(str(child(parts.get(0).get("text"), "content")));
                    }
                }
            }
            JsonValue img = child(child(lv, "contentImage"), "thumbnailViewModel");
            if (img != null) {
                JsonValue sources = child(img.get("image"), "sources");
                if (sources != null && sources.size > 0) {
                    v.thumbUrl = nz(str(sources.get(sources.size - 1).get("url")));
                }
                v.durationSec = YtVideo.parseDuration(findDurationBadge(img));
            }
            if (v.thumbUrl.isEmpty()) v.thumbUrl = "https://i.ytimg.com/vi/" + vid + "/hqdefault.jpg";
            pl.videos.add(v);
        }
        // Kiểu cũ: playlistVideoRenderer
        List<JsonValue> renderers = new ArrayList<>();
        collect(root, "playlistVideoRenderer", renderers);
        for (JsonValue vr : renderers) {
            String vid = str(vr.get("videoId"));
            if (vid == null || !seen.add(vid)) continue;
            YtVideo v = new YtVideo();
            v.id = vid;
            v.title = textOf(vr.get("title"));
            v.channel = textOf(vr.get("shortBylineText"));
            v.durationSec = YtVideo.parseDuration(textOf(vr.get("lengthText")));
            v.thumbUrl = lastThumb(vr.get("thumbnail"));
            pl.videos.add(v);
        }
        return pl;
    }

    public static YtStream parsePlayer(JsonValue root, String videoId) throws YoutubeException {
        JsonValue ps = root.get("playabilityStatus");
        String status = ps == null ? null : str(ps.get("status"));
        if (!"OK".equals(status)) {
            String reason = ps == null ? "không rõ lý do" : nz(str(ps.get("reason")));
            throw new YoutubeException("YouTube không cho phát video này: " + reason);
        }
        YtStream s = new YtStream();
        s.videoId = videoId;
        JsonValue details = root.get("videoDetails");
        if (details != null) {
            s.title = nz(str(details.get("title")));
            s.author = nz(str(details.get("author")));
            s.durationSec = intOf(details.get("lengthSeconds"));
        }
        JsonValue sd = root.get("streamingData");
        if (sd == null) throw new YoutubeException("Video không có dữ liệu luồng phát.");
        YtStream best = null;
        for (String key : new String[] {"adaptiveFormats", "formats"}) {
            JsonValue arr = sd.get(key);
            if (arr == null) continue;
            for (JsonValue f : arr) {
                String mime = str(f.get("mimeType"));
                String url = str(f.get("url"));
                if (mime == null || !mime.startsWith("audio/") || url == null) continue;
                int bitrate = intOf(f.get("bitrate"));
                if (best == null || bitrate > best.bitrate) {
                    best = new YtStream();
                    best.videoId = videoId;
                    best.url = url;
                    best.mimeType = mime;
                    best.itag = intOf(f.get("itag"));
                    best.bitrate = bitrate;
                }
            }
        }
        if (best == null) {
            throw new YoutubeException("Không tìm thấy luồng audio có link trực tiếp cho video này.");
        }
        best.title = s.title;
        best.author = s.author;
        best.durationSec = s.durationSec;
        return best;
    }

    // ------------------------------------------------------------ Hỗ trợ

    /** Thu thập mọi object con có tên khoá cho trước (đệ quy toàn cây). */
    static void collect(JsonValue node, String key, List<JsonValue> out) {
        if (node == null) return;
        if (node.isObject() || node.isArray()) {
            for (JsonValue c : node) {
                if (key.equals(c.name())) out.add(c);
                collect(c, key, out);
            }
        }
    }

    /** Chuỗi hiển thị của một node chữ YouTube: simpleText hoặc runs. */
    static String textOf(JsonValue node) {
        if (node == null) return "";
        String simple = str(node.get("simpleText"));
        if (simple != null) return simple;
        JsonValue runs = node.get("runs");
        if (runs != null) {
            StringBuilder sb = new StringBuilder();
            for (JsonValue r : runs) sb.append(nz(str(r.get("text"))));
            return sb.toString();
        }
        return "";
    }

    private static String lastThumb(JsonValue thumbnail) {
        JsonValue arr = thumbnail == null ? null : thumbnail.get("thumbnails");
        if (arr == null || arr.size == 0) return "";
        return nz(str(arr.get(arr.size - 1).get("url")));
    }

    /** Trong cây overlay của bìa, tìm badge có dạng thời lượng "4:39". */
    private static String findDurationBadge(JsonValue scope) {
        List<JsonValue> badges = new ArrayList<>();
        collect(scope, "thumbnailBadgeViewModel", badges);
        for (JsonValue b : badges) {
            String t = str(b.get("text"));
            if (t != null && t.matches("\\d+:\\d{2}(:\\d{2})?")) return t;
        }
        return "";
    }

    private static JsonValue child(JsonValue node, String name) {
        return node == null ? null : node.get(name);
    }

    private static String str(JsonValue v) {
        return v != null && v.isString() ? v.asString() : null;
    }

    private static int intOf(JsonValue v) {
        if (v == null) return 0;
        if (v.isNumber()) return v.asInt();
        if (v.isString()) {
            try {
                return Integer.parseInt(v.asString());
            } catch (NumberFormatException e) {
                return 0;
            }
        }
        return 0;
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }

    private static String esc(String s) {
        StringBuilder sb = new StringBuilder(s.length() + 8);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"': sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                default:
                    if (c < 0x20) sb.append(String.format("\\u%04x", (int) c));
                    else sb.append(c);
            }
        }
        return sb.toString();
    }
}
