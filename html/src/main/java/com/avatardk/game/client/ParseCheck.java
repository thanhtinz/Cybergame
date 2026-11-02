package com.avatardk.game.client;

import com.avatardk.game.data.AvatarDataSet;
import com.avatardk.game.data.PartData;
import com.google.gwt.core.client.EntryPoint;
import com.google.gwt.http.client.Request;
import com.google.gwt.http.client.RequestBuilder;
import com.google.gwt.http.client.RequestCallback;
import com.google.gwt.http.client.Response;
import com.google.gwt.user.client.Window;

/** Trang kiểm chứng RIÊNG cho bản web (không phải một phần của game):
 *  tải 3 file JSON dữ liệu thật (bản đã biến đổi cho web) rồi chạy đúng
 *  AvatarDataSet.fromJson của game, ghi kết quả vào tiêu đề trang để
 *  Chrome headless đọc được. Sinh ra vì JsonReader của GWT chết
 *  RangeError trên dữ liệu gốc — xem tools/build_web.py. */
public class ParseCheck implements EntryPoint {
    private String items, imgs, npcs;
    private int got;

    @Override
    public void onModuleLoad() {
        fetch("pt-items.json", 0);
        fetch("pt-imgdata.json", 1);
        fetch("pt-npcs.json", 2);
    }

    private void fetch(String url, final int slot) {
        try {
            new RequestBuilder(RequestBuilder.GET, url).sendRequest(null, new RequestCallback() {
                @Override public void onError(Request r, Throwable e) {
                    Window.setTitle("PARSE_FETCH_ERROR " + url + " " + e);
                }
                @Override public void onResponseReceived(Request r, Response resp) {
                    String t = resp.getText();
                    if (slot == 0) items = t; else if (slot == 1) imgs = t; else npcs = t;
                    if (++got == 3) run();
                }
            });
        } catch (Exception e) {
            Window.setTitle("PARSE_SEND_ERROR " + e);
        }
    }

    private void run() {
        try {
            AvatarDataSet ds = AvatarDataSet.fromJson(items, imgs, npcs);
            int frames = 0;
            for (PartData p : ds.parts.values()) frames += p.frames.size();
            Window.setTitle("PARSE_OK parts=" + ds.parts.size()
                + " frames=" + frames + " npcs=" + ds.npcs.size());
        } catch (Throwable e) {
            Window.setTitle("PARSE_FAIL " + e);
        }
    }
}
