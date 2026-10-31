package com.avatardk.game.ui;

import java.util.List;

/**
 * Danh sách cụm + máy chủ cho màn chọn server (mockup v3 đã duyệt).
 * Client gốc gắn cứng một endpoint duy nhất nên hiện tại mọi máy chủ trong
 * danh sách đều trỏ về server local 127.0.0.1:19128 — khi có server thật,
 * chỉ cần sửa host/port từng dòng tại đây. Trạng thái và số người chơi là
 * số hiển thị mẫu như trong mockup.
 */
public final class Servers {
    public static final class Server {
        public final String label, statusText, players, host;
        public final int port;
        public final boolean crowded;

        Server(String label, String statusText, String players, boolean crowded) {
            this.label = label;
            this.statusText = statusText;
            this.players = players;
            this.crowded = crowded;
            this.host = "127.0.0.1";
            this.port = 19128;
        }
    }

    public static final class Cluster {
        public final String name;
        public final List<Server> servers;

        Cluster(String name, Server... servers) {
            this.name = name;
            this.servers = List.of(servers);
        }
    }

    public static final List<Cluster> CLUSTERS = List.of(
        new Cluster("Cụm Mặt Trời",
            new Server("S1 · Sao Mai", "Trạng thái: Tốt", "1.240 người chơi", false),
            new Server("S2 · Sao Hôm", "Trạng thái: Tốt", "860 người chơi", false),
            new Server("S3 · Sao Chổi", "Trạng thái: Đông", "2.310 người chơi", true)),
        new Cluster("Cụm Sao Băng",
            new Server("S4 · Sao Băng", "Trạng thái: Đông", "3.975 người chơi", true),
            new Server("S5 · Sao Hải", "Trạng thái: Tốt", "512 người chơi", false)),
        new Cluster("Cụm Thiên Hà",
            new Server("S6 · Thiên Hà", "Trạng thái: Tốt", "128 người chơi", false)));

    private static int selCluster = 0;
    private static int selServer = 0;

    private Servers() {}

    public static Server selected() {
        return CLUSTERS.get(selCluster).servers.get(selServer);
    }

    public static int selectedCluster() { return selCluster; }
    public static int selectedServer() { return selServer; }

    public static void select(int cluster, int server) {
        selCluster = cluster;
        selServer = server;
    }
}
