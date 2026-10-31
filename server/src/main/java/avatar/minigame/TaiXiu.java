package avatar.minigame;

import avatar.db.DbManager;
import avatar.model.Npc;
import avatar.model.User;
import avatar.server.UserManager;
import avatar.server.Utils;

import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;

public class TaiXiu {
    private static TaiXiu instance; // Singleton instance
    private List<Npc> TaiXiu = new ArrayList<>();
    private int gameId;
    private int countdown = 40;
    private static final int GAME_DURATION_SECONDS = 40;
    private static final int RESULT_DISPLAY_DURATION_SECONDS = 5;

    public static TaiXiu getInstance() {
        if (instance == null) {
            instance = new TaiXiu();
        }
        return instance;
    }

    private TaiXiu() {this.gameId = getLastGameId();}

    // Thêm một NPC vào danh sách
    public void setNpcTaiXiu(Npc npc) {
        if (npc != null && this.TaiXiu.size() == 0) {  // Đảm bảo chỉ có 1 NPC
            this.TaiXiu.add(npc);
            System.out.println("NPC đã được thêm vào TaiXiu, kích thước hiện tại: " + TaiXiu.size());
            startCountdown();  // Bắt đầu luồng autoChat khi đã thêm NPC
        }
    }

    public List<Npc> getNpcTaiXiu() {
        return new ArrayList<>(TaiXiu);
    }

    public void startCountdown() {
        new Thread(() -> {
            long startTime = System.currentTimeMillis();
            try {
                while (countdown > 0) {
                    long elapsedSeconds = (System.currentTimeMillis() - startTime) / 1000;
                    countdown = 40 - (int) elapsedSeconds;
                    updateNpcChat(countdown);
                    Thread.sleep(1000);
                }
                handleEndGame(); // Kết thúc game, trả kết quả
                startNewGame(); // Bắt đầu phiên mới
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }).start();
    }

    // Cập nhật chat của NPC với thời gian còn lại
    private void updateNpcChat(int countdown) {
        for (Npc npc : this.TaiXiu) {
            npc.setTextChats(List.of(MessageFormat.format("Phiên id : {0}. Thời gian còn lại: {1} giây",gameId, countdown)));
        }
    }

    // Bắt đầu phiên cược mới
    private void startNewGame() {
        countdown = 40;
        gameId++;

        saveGameRoundResult(gameId, "Pending");
        System.out.println("Bắt đầu phiên mới với ID: " + gameId);
        startCountdown();
    }

    // Xử lý khi hết thời gian, tính toán và trả kết quả cho người chơi
    private void handleEndGame() throws InterruptedException {
        String result = calculateResult();
        List<Bet> bets = getAllBetsForGame(gameId);
        endGame(result);
        long preStart = System.currentTimeMillis();
        int preCountdown = RESULT_DISPLAY_DURATION_SECONDS;
        if (bets == null || bets.isEmpty()) {
            System.out.println("Không có cược nào cho phiên " + gameId);
        }else {
            for (Bet bet : bets) {
                User user = bet.getUser();
                if (user != null) { // Kiểm tra user không null
                    String username = user.getUsername();
                    String betType = bet.getBetType();
                    String currency = bet.getCurrency();
                    double betAmount = bet.getAmount();

                    // In thông tin chi tiết của từng người chơi
                    System.out.println("Người chơi: " + username);
                    System.out.println("Loại cược: " + betType);
                    System.out.println("Tiền cược: " + betAmount + " " + currency);

                    if (result.contains(bet.getBetType())) {
                        // Trả thưởng nếu thắng
                        double reward = betAmount * 2 * 0.95;
                        updateBalance(user,currency, (int)reward); // Thêm tiền vào tài khoản người chơi
                        user.getAvatarService().serverDialog("Chúc mừng " + username + "! Bạn đã thắng cược và nhận được " + (int)reward + " " + currency + ".");
                        updateBetStatus(bet.getId(), "Win");
                        Utils.writeLog(user,"Chúc mừng " + username + "! Bạn đã thắng cược và nhận được " + (int)reward + " " + currency + "." + result);
                        Utils.writeLog(user,"tiền XU tx:" + user.getXu() + "Lượng tx"+user.getLuong());
                        try {
                            user.getAvatarService().SendTabmsg(" bạn đã thắng "+ betType+ " " +(int)reward);
                        }
                        catch (Exception e) {}
                    } else {
                        user.getAvatarService().serverDialog("Rất tiếc, " + username + ", bạn đã thua cược.");
                        updateBetStatus(bet.getId(), "Lose");
                        Utils.writeLog(user,"Rất tiếc, " + username + ", bạn đã thua cược."+result);
                        Utils.writeLog(user,"tiền XU tx:" + user.getXu() + "Lượng tx"+user.getLuong());
                        try {
                            user.getAvatarService().SendTabmsg(" bạn đã thua "+ betType+ " " +currency);
                        }
                        catch (Exception e) {}
                    }
                } else {
                    System.out.println("Lỗi: Không thể tìm thấy người chơi cho cược ID: " + bet.getId());
                }
            }


        }

        // Cập nhật kết quả và đếm ngược hiển thị
        while (preCountdown > 0) {
            if (!getNpcTaiXiu().isEmpty()) {
                Npc npc = TaiXiu.get(0);
                npc.setTextChats(List.of(
                        MessageFormat.format("Kết quả: {0}, Ván mới sẽ bắt đầu sau: {1} giây", result, preCountdown)
                ));
            }
            Thread.sleep(1000);
            preCountdown = RESULT_DISPLAY_DURATION_SECONDS - (int) ((System.currentTimeMillis() - preStart) / 1000);
        }

        //clearBetsForGame(gameId); // Xóa cược cho ván hiện tại
        updateGameRewardStatus(gameId); // Cập nhật trạng thái trả thưởng

        System.out.println("Kết quả phiên " + gameId + ": " + result);
    }

    // Tính kết quả ngẫu nhiên cho phiên cược
    private String calculateResult() {
        Random random = new Random();
        int dice1 = random.nextInt(6) + 1;
        int dice2 = random.nextInt(6) + 1;
        int dice3 = random.nextInt(6) + 1;
        int total = dice1 + dice2 + dice3;
        endGame("khóa");
        System.out.println("khóa cuoc");
        String result = (total >= 11 && total <= 18) ? "Tài" : "Xỉu";
        return " " + dice1 + ", " + dice2 + ", " + dice3 + " " + total + " " + result;
    }

    public void handleBetWithInput(User us, int menuId, int userId, String text) {
        try {
            int betAmount = Integer.parseInt(text); // Chuyển text thành số nguyên để lấy số cược

            // Kiểm tra số tiền cược hợp lệ
            if (betAmount <= 0) {
                us.getAvatarService().serverDialog("Vui lòng nhập số tiền cược hợp lệ.");
                return;
            }

            String betType = ""; // Loại cược: "Tài" hoặc "Xỉu"
            String currency = ""; // Loại tiền tệ: "Xu" hoặc "Lượng"

            // Xác định loại cược và loại tiền tệ dựa trên menuId
            switch (menuId) {
                case 0: // Cược Tài (Xu)
                    betType = "Tài";
                    currency = "Xu";
                    break;
                case 1: // Cược Xỉu (Xu)
                    betType = "Xỉu";
                    currency = "Xu";
                    break;
                case 2: // Cược Tài (Lượng)
                    betType = "Tài";
                    currency = "Lượng";
                    break;
                case 3: // Cược Xỉu (Lượng)
                    betType = "Xỉu";
                    currency = "Lượng";
                    break;
            }

// Kiểm tra giới hạn cược cho từng loại tiền tệ và hiển thị thông báo phù hợp
            if (currency.equals("Xu") && betAmount > 50000000) {
                us.getAvatarService().serverDialog("Giới hạn cược cho Xu là 50 triệu Xu.");
                return;
            } else if (currency.equals("Lượng") && betAmount > 5000) {
                us.getAvatarService().serverDialog("Giới hạn cược cho Lượng là 5 nghìn Lượng.");
                return;
            }


            // Kiểm tra số dư và thực hiện đặt cược tương ứng
            if (hasSufficientBalance(us, currency, betAmount)) {
                handleBet(us, betType, currency, betAmount);
            } else {
                us.getAvatarService().serverDialog("Bạn không đủ " + currency + " để đặt cược.");
            }

        } catch (NumberFormatException e) {
            us.getAvatarService().serverDialog("Vui lòng nhập một số hợp lệ.");
        }
    }


    // Kiểm tra số dư của người chơi cho loại tiền tệ cụ thể
    private boolean hasSufficientBalance(User user, String currency, int betAmount) {
        if (currency.equals("Xu")) {
            return user.getXu() >= betAmount;
        } else if (currency.equals("Lượng")) {
            return user.getLuong() >= betAmount;
        }
        return false;
    }

    // Xử lý cược và trừ số tiền cược từ số dư của người chơi
    private void handleBet(User user, String choice, String currency, int betAmount) {
        // Trừ số tiền cược từ tài khoản của người chơi
        if (!hasSufficientBalance(user, currency, betAmount)) {
            user.getAvatarService().serverDialog("Bạn không đủ tiền để đặt cược!");
            return; // Kết thúc hàm nếu không đủ tiền
        }
        if (!isGameOpen()) {
            user.getAvatarService().serverDialog("Không thể đặt cược. Phiên chơi đã kết thúc.");
            return;
        }
        // Kiểm tra xem người chơi đã cược trong phiên này chưa
        if (hasBetInCurrentRound(user.getId(), gameId)) {
            user.getAvatarService().serverDialog("Bạn đã cược trong phiên này rồi. Không thể đặt cược thêm!");
            return; // Kết thúc hàm nếu đã cược
        }

        // Cập nhật số dư
        updateBalance(user, currency, -betAmount); // Giả sử có phương thức updateBalance để cập nhật số dư

        // Ghi lại cược vào hệ thống (database)
        saveBetToDatabase(user.getId(), gameId, choice, currency, betAmount);

        // Thông báo cho người chơi
        user.getAvatarService().serverDialog("Bạn đã đặt cược " + betAmount + " " + currency + " vào " + choice + ".");
        Utils.writeLog(user,user.getUsername()+" cuoc " + betAmount + " " + currency + " vào " + choice + ".");
    }

    private void updateBalance(User user, String currency, int amount) {
        if (currency.equals("Xu")) {
            user.updateXu(amount); // Cập nhật số dư Xu
            user.getAvatarService().updateMoney(0);
        } else if (currency.equals("Lượng")) {
            user.updateLuong(amount); // Cập nhật số dư Xu
            user.getAvatarService().updateMoney(0);
        }
    }

    // Lưu thông tin đặt cược vào cơ sở dữ liệu
    private void saveBetToDatabase(int userId, int gameId, String betType, String currency, int betAmount) {
        String insertQuery = "INSERT INTO betgame (user_id, game_id, bet_type, currency, bet_amount, status) VALUES (?, ?, ?, ?, ?, 'Pending')";

        try (Connection conn = DbManager.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(insertQuery)) {
            ps.setInt(1, userId);
            ps.setInt(2, gameId);
            ps.setString(3, betType);
            ps.setString(4, currency);
            ps.setInt(5, betAmount);
            ps.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }


    // Lấy tất cả các cược trong phiên hiện tại từ database
    private List<Bet> getAllBetsForGame(int gameId) {
        List<Bet> bets = new ArrayList<>();
        try (Connection conn = DbManager.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT * FROM betgame WHERE game_id = ?")) {
            ps.setInt(1, gameId);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                int userId = rs.getInt("user_id");
                User user = UserManager.getInstance().find(userId);
                String betType = rs.getString("bet_type");
                String currency = rs.getString("currency");
                int amount = rs.getInt("bet_amount");
                int betId = rs.getInt("bet_id");
                bets.add(new Bet(betId, user, betType, currency, amount));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return bets;
    }

    // Xóa tất cả các cược trong phiên sau khi xử lý
    private void clearBetsForGame(int gameId) {
        try (Connection conn = DbManager.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement("DELETE FROM betgame WHERE game_id = ?")) {
            ps.setInt(1, gameId);
            ps.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    // Cập nhật trạng thái cược
    private void updateBetStatus(int betId, String status) {
        try (Connection conn = DbManager.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement("UPDATE betgame SET status = ? WHERE bet_id = ?")) {
            ps.setString(1, status);
            ps.setInt(2, betId);
            ps.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    // Lưu kết quả phiên chơi
// Lưu kết quả phiên chơi
    public void saveGameRoundResult(int gameId, String result) {
        String insertQuery = "INSERT INTO game_rounds (game_id, result, created_at, game_status, reward_status) VALUES (?, ?, NOW(), 'Open', 'chưa')"; // Đã sửa 'ceate' thành 'chưa
        try (Connection conn = DbManager.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(insertQuery)) {
            ps.setInt(1, gameId);
            ps.setString(2, result);
            ps.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }


    // Cập nhật trạng thái trả thưởng
    public void updateGameRewardStatus(int gameId) {
        String updateQuery = "UPDATE game_rounds SET reward_status = 'đã trả' WHERE game_id = ?";
        try (Connection conn = DbManager.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(updateQuery)) {
            ps.setInt(1, gameId);
            ps.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
        System.out.println("đã trả thưởng");
    }

    // Kiểm tra trạng thái phiên
    private boolean isGameOpen() {
        String query = "SELECT game_status FROM game_rounds WHERE game_id = ?";
        try (Connection conn = DbManager.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(query)) {
            ps.setInt(1, gameId);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                String gameStatus = rs.getString("game_status");
                return "Open".equals(gameStatus);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return false;
    }
    public void endGame(String result) {
        // Cập nhật trạng thái trò chơi và thời gian kết thúc
        updateGameEndStatus(gameId, result);
        System.out.println("Kết thúc phiên với ID: " + gameId + " với kết quả: " + result);

    }

    // Cập nhật trạng thái khi kết thúc trò chơi
    public void updateGameEndStatus(int gameId, String result) {
        String updateQuery = "UPDATE game_rounds SET game_status = 'Closed', end_time = CURRENT_TIMESTAMP, result = ? WHERE game_id = ?";
        try (Connection conn = DbManager.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(updateQuery)) {
            ps.setString(1, result);
            ps.setInt(2, gameId);
            ps.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
        System.out.println("endGame không cho cược game ID "+ gameId);
    }


    private int getLastGameId() {
        int lastGameId = 0; // Mặc định là 0 nếu không tìm thấy
        try (Connection conn = DbManager.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT MAX(game_id) FROM game_rounds")) {
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                lastGameId = rs.getInt(1);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return lastGameId;
    }
    private boolean hasBetInCurrentRound(int userId, int gameId) {
        String query = "SELECT COUNT(*) FROM betgame WHERE user_id = ? AND game_id = ?";
        try (Connection conn = DbManager.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(query)) {
            ps.setInt(1, userId);
            ps.setInt(2, gameId);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                System.out.println("co cuoc");
                return rs.getInt(1) > 0; // Trả về true nếu có cược
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return false; // Không có cược
    }

    public void viewBetHistory(User us) {
        String query = "SELECT bg.bet_amount, bg.currency, gr.result, gr.game_status, bg.bet_type " +
                "FROM betgame bg " +
                "JOIN game_rounds gr ON bg.game_id = gr.game_id " +
                "WHERE bg.user_id = ?";

        try (Connection conn = DbManager.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(query)) {

            ps.setInt(1, us.getId());
            ResultSet rs = ps.executeQuery();

            int totalBets = 0;
            int totalWins = 0;
            int totalLosses = 0;
            double totalAmountBetXu = 0;
            double totalAmountBetLuong = 0;
            double totalWinXu = 0;
            double totalLossXu = 0;
            double totalWinLuong = 0;
            double totalLossLuong = 0;

            while (rs.next()) {
                double betAmount = rs.getDouble("bet_amount");
                String currency = rs.getString("currency");
                String result = rs.getString("result");
                String gameStatus = rs.getString("game_status");
                String betType = rs.getString("bet_type");

                if (currency == null || result == null || betType == null) continue;

                totalBets++;

                // Separate total bet amounts by currency
                if (currency.equals("Xu")) {
                    totalAmountBetXu += betAmount;
                } else if (currency.equals("Lượng")) {
                    totalAmountBetLuong += betAmount;
                }

                // Determine win/loss based on result and game status
                if (gameStatus.equals("Closed")) {
                    if (result.contains(betType)) { // Player wins
                        totalWins++;
                        if (currency.equals("Xu")) {
                            totalWinXu += betAmount * 0.95;
                        } else if (currency.equals("Lượng")) {
                            totalWinLuong += betAmount * 0.95;
                        }
                    } else { // Player loses
                        totalLosses++;
                        if (currency.equals("Xu")) {
                            totalLossXu += betAmount;
                        } else if (currency.equals("Lượng")) {
                            totalLossLuong += betAmount;
                        }
                    }
                }
            }
            // Calculate win rate
            double winRate = totalBets > 0 ? (double) totalWins / totalBets * 100 : 0;
            winRate = Math.round(winRate * 100.0) / 100.0;

            // Calculate net result for Xu and Luong
            double netXu = totalWinXu - totalLossXu;
            double netLuong = totalWinLuong - totalLossLuong;

            // Build the result message
            StringBuilder sb = new StringBuilder();
            sb.append("Lịch sử đặt cược của: ").append(us.getUsername()).append("\n");
            sb.append("Tổng số cược: ").append(totalBets).append("\n");
            sb.append(" Thắng: ").append(totalWins);
            sb.append(" Thua: ").append(totalLosses);
            sb.append(" Tỷ lệ thắng: ").append(winRate).append("%\n");
            sb.append("Tổng cược: ").append((int) totalAmountBetXu).append(" Xu\n");
            sb.append("Tổng cược: ").append((int) totalAmountBetLuong).append("Lượng\n");

            if (netXu > 0) {
                sb.append("tổng: Thắng ").append((int) netXu).append(" xu\n");
            } else {
                sb.append("tổng: Thua ").append((int) Math.abs(netXu)).append(" xu\n");
            }

            if (netLuong > 0) {
                sb.append("tổng: Thắng ").append((int) netLuong).append(" lượng\n");
            } else {
                sb.append("tổng: Thua ").append((int) Math.abs(netLuong)).append(" lượng\n");
            }

            // Display the result
            us.getAvatarService().serverDialog(sb.toString());

        } catch (SQLException e) {
            e.printStackTrace();
        }
    }




    public void viewGameRoundHistory(User us) {
        String query = "SELECT game_id, result " +
                "FROM game_rounds " +
                "ORDER BY created_at DESC " + // Sắp xếp theo thời gian tạo
                "LIMIT 10"; // Giới hạn 10 kết quả

        try (Connection conn = DbManager.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(query);
             ResultSet rs = ps.executeQuery()) {

            StringBuilder sb = new StringBuilder();
            sb.append("Lịch sử 10 phiên gần nhất:\n");

            while (rs.next()) {
                int gameId = rs.getInt("game_id");
                String result = rs.getString("result");

                sb.append("Phiên ").append(gameId).append(" : ")
                        .append(" ").append(result).append("\n");
            }

            us.getAvatarService().serverDialog(sb.toString());
            System.out.println(sb.toString());

        } catch (SQLException e) {
            e.printStackTrace();
        }
    }


    public void getTopWinerXu(User user) {
        String query = "SELECT bg.user_id, us.username, bg.currency, " +
                "SUM(CASE WHEN gr.result LIKE CONCAT('%', bg.bet_type, '%') THEN bg.bet_amount * 0.95 ELSE 0 END) - " +
                "SUM(CASE WHEN gr.result NOT LIKE CONCAT('%', bg.bet_type, '%') THEN bg.bet_amount ELSE 0 END) AS net_win " +
                "FROM betgame bg " +
                "JOIN game_rounds gr ON bg.game_id = gr.game_id " +
                "JOIN users us ON bg.user_id = us.id " +
                "WHERE gr.game_status = 'Closed' AND bg.currency = 'Xu' " +
                "GROUP BY bg.user_id, us.username, bg.currency " +
                "HAVING net_win > 0 " +
                "ORDER BY net_win DESC " +
                "LIMIT 5";

        try (Connection conn = DbManager.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(query)) {

            ResultSet rs = ps.executeQuery();

            StringBuilder sb = new StringBuilder();
            sb.append("Top 5 người WIN Xu:\n");

            while (rs.next()) {
                String username = rs.getString("username");
                String currency = rs.getString("currency");
                double netWin = rs.getDouble("net_win");

                sb.append("").append(username)
                        .append(" - Tổng thắng : ").append((int) netWin).append(" ")
                        .append(currency).append("\n");
            }
            sb.append(" ------------------------------------------------------ ").append("\n");
            user.getAvatarService().SendTabmsg(sb.toString());

        } catch (SQLException e) {
            e.printStackTrace();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public void getTopLossXu(User user) {
        String query = "SELECT bg.user_id, us.username, bg.currency, " +
                "SUM(CASE WHEN gr.result LIKE CONCAT('%', bg.bet_type, '%') THEN bg.bet_amount * 2 " +
                "ELSE -bg.bet_amount END) AS net_loss " +
                "FROM betgame bg " +
                "JOIN game_rounds gr ON bg.game_id = gr.game_id " +
                "JOIN users us ON bg.user_id = us.id " +
                "WHERE gr.game_status = 'Closed' AND bg.currency = 'Xu' " +
                "GROUP BY bg.user_id, us.username, bg.currency " +
                "HAVING net_loss < 0 " +  // chỉ lấy người thua
                "ORDER BY net_loss ASC " +
                "LIMIT 5";

        try (Connection conn = DbManager.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(query)) {

            ResultSet rs = ps.executeQuery();

            StringBuilder sb = new StringBuilder();
            sb.append("Top 5 người LOSS Xu :\n");

            while (rs.next()) {
                String username = rs.getString("username");
                String currency = rs.getString("currency");
                double netLoss = rs.getDouble("net_loss");

                sb.append("Người chơi: ").append(username)
                        .append(" - Tổng thua: ").append((int) Math.abs(netLoss)).append(" ")
                        .append(currency).append("\n");
            }
            sb.append(" ------------------------------------------------------ ").append("\n");


            // Display the result
            user.getAvatarService().SendTabmsg(sb.toString());

        } catch (SQLException e) {
            e.printStackTrace();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public void getTopLossLuong(User user) {
        String query = "SELECT bg.user_id, us.username, bg.currency, " +
                "SUM(CASE WHEN gr.result LIKE CONCAT('%', bg.bet_type, '%') THEN bg.bet_amount * 2 " +
                "ELSE -bg.bet_amount END) AS net_loss " +
                "FROM betgame bg " +
                "JOIN game_rounds gr ON bg.game_id = gr.game_id " +
                "JOIN users us ON bg.user_id = us.id " +
                "WHERE gr.game_status = 'Closed' AND bg.currency = 'Lượng' " +
                "GROUP BY bg.user_id, us.username, bg.currency " +
                "HAVING net_loss < 0 " +  // chỉ lấy người thua
                "ORDER BY net_loss ASC " +
                "LIMIT 5";

        try (Connection conn = DbManager.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(query)) {

            ResultSet rs = ps.executeQuery();

            StringBuilder sb = new StringBuilder();
            sb.append("Top 5 người chơi thua Lượng:\n");

            while (rs.next()) {
                String username = rs.getString("username");
                String currency = rs.getString("currency");
                double netLoss = rs.getDouble("net_loss");

                sb.append("Người chơi: ").append(username)
                        .append(" - Tổng thua: ").append((int) Math.abs(netLoss)).append(" ")
                        .append(currency).append("\n");
            }
            sb.append(" Chúc bạn may mắn. ").append("\n");
            // Display the result
            user.getAvatarService().SendTabmsg(sb.toString());

        } catch (SQLException e) {
            e.printStackTrace();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public void getTopWinLuong(User user) {
        String query = "SELECT bg.user_id, us.username, bg.currency, " +
                "SUM(CASE WHEN gr.result LIKE CONCAT('%', bg.bet_type, '%') THEN bg.bet_amount * 0.95 " +
                "ELSE -bg.bet_amount END) AS net_win " +
                "FROM betgame bg " +
                "JOIN game_rounds gr ON bg.game_id = gr.game_id " +
                "JOIN users us ON bg.user_id = us.id " +
                "WHERE gr.game_status = 'Closed' AND bg.currency = 'Lượng' " +
                "GROUP BY bg.user_id, us.username, bg.currency " +
                "HAVING net_win > 0 " +  // chỉ lấy người thắng
                "ORDER BY net_win DESC " +
                "LIMIT 5";

        try (Connection conn = DbManager.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(query)) {

            ResultSet rs = ps.executeQuery();

            StringBuilder sb = new StringBuilder();
            sb.append("Top 5 người chơi thắng Lượng:\n");

            while (rs.next()) {
                String username = rs.getString("username");
                String currency = rs.getString("currency");
                double netWin = rs.getDouble("net_win");

                sb.append("Người chơi: ").append(username)
                        .append(" - Tổng thắng: ").append((int) netWin).append(" ")
                        .append(currency).append("\n");
            }
            sb.append(" ------------------------------------------------------ ").append("\n");
            // Display the result
            user.getAvatarService().SendTabmsg(sb.toString());

        } catch (SQLException e) {
            e.printStackTrace();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }



    // Lớp đại diện cho cược của người chơi
    private static class Bet {
        private final int id;
        private final User user;
        private final String betType;
        private final String currency;
        private final int amount;

        public Bet(int id, User user, String betType, String currency, int amount) {
            this.id = id;
            this.user = user;
            this.betType = betType;
            this.currency = currency;
            this.amount = amount;
        }

        public int getId() {
            return id;
        }

        public User getUser() {
            return user;
        }

        public String getBetType() {
            return betType;
        }

        public String getCurrency() {
            return currency;
        }

        public int getAmount() {
            return amount;
        }
    }
}
