
package avatar.handler;

import avatar.common.BossShopItem;
import avatar.constants.Cmd;
import avatar.constants.NpcName;
import avatar.item.Item;
import avatar.item.Part;
import avatar.item.PartManager;
import avatar.minigame.TaiXiu;
import avatar.model.*;

import java.util.*;
import java.math.BigInteger;

import avatar.lucky.DialLucky;
import avatar.lucky.DialLuckyManager;

import java.util.concurrent.CountDownLatch;
import java.io.IOException;
import java.util.concurrent.*;
import avatar.network.Message;
import avatar.play.MapManager;
import avatar.play.NpcManager;
import avatar.play.Zone;
import avatar.server.*;
import avatar.service.AvatarService;

import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.concurrent.CountDownLatch;

import static avatar.constants.NpcName.*;
import static avatar.constants.NpcName.boss;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;


import java.time.LocalTime;
import java.util.stream.Collectors;

public class NpcHandler {

    private static final Map<Integer, Long> lastActionTimes = new HashMap<>();
    private static final long ACTION_COOLDOWN_MS = 90; // 2 giây cooldown


    public static void handleDiaLucky(User us, byte type) {
        DialLucky dl = DialLuckyManager.getInstance().find(type);
        if (dl != null) {
            if (dl.getType() == DialLuckyManager.MIEN_PHI) {
                if(us.chests.size() >= us.getChestSlot()-2){
                    us.getAvatarService().serverDialog("Bạn phải có ít nhất 3 ô trống trong rương đồ");
                    return;
                }
                Item itm = us.findItemInChests(593);
                if (itm.getQuantity() <=0 || itm.getQuantity() > 1998)
                {
                    return;
                }
                if (itm == null || itm.getQuantity() <= 0) {
                    us.getAvatarService().serverDialog("Bạn không có Vé quay số miễn phí!");
                    return;
                }
            }
            if (dl.getType() == DialLuckyManager.XU) {
                if(us.chests.size() >= us.getChestSlot()-2){
                    us.getAvatarService().serverDialog("Bạn phải có ít nhất 3 ô trống trong rương đồ");
                    return;
                }
                if (us.getXu() < 25000) {
                    us.getAvatarService().serverDialog("Bạn không đủ xu!");
                    return;
                }
            }
            if (dl.getType() == DialLuckyManager.LUONG) {
                if(us.chests.size() >= us.getChestSlot()-2){
                    us.getAvatarService().serverDialog("Bạn phải có ít nhất 3 ô trống trong rương đồ");
                    return;
                }
                if (us.getLuong() < 5) {
                    us.getAvatarService().serverDialog("Bạn không đủ lượng!!");
                    return;
                }
            }
        }
        us.setDialLucky(dl);
        dl.show(us);
    }

    public static void handlerCommunicate(int npcId, User us) throws IOException {
        Zone z = us.getZone();
        if (z != null) {
            User u = z.find(npcId);
            if (u == null) {
                return;
            }
        } else {
            return;
        }

        long currentTime = System.currentTimeMillis();
        long lastActionTime = lastActionTimes.getOrDefault(us.getId(), 0L);

        if (currentTime - lastActionTime < ACTION_COOLDOWN_MS) {
            us.getAvatarService().serverDialog("Từ từ thôi bạn!");
            return;
        }
        // Cập nhật thời gian thực hiện hành động
        lastActionTimes.put(us.getId(), currentTime);
        int npcIdCase = npcId - Npc.ID_ADD;
        User boss = z.find(npcId);
        double maxDistance = 55.0;
        int playerX = us.getX();
        int playerY = us.getY();
        int bossX = boss.getX();
        int bossY = boss.getY();
        double distance = Utils.distanceBetween(playerX, playerY, bossX, bossY);


        if (npcIdCase > 1000 && npcIdCase<=9999)
        {
            Random random = new Random(); // Khởi tạo Random
            if (us.getRandomTimeInMillis() == 0) {
                int randomMinutes = 10 + random.nextInt(11); // Tạo số phút ngẫu nhiên từ 10-20
                long randomTimeInMillis = randomMinutes * 60 * 1000; // Chuyển đổi phút sang mili giây
                // Gán thời gian ngẫu nhiên cho user
                us.setRandomTimeInMillis(randomTimeInMillis);
                us.setLastTimeSet(System.currentTimeMillis()); // Lưu thời gian hiện tại khi gán
            }

            // Kiểm tra thời gian hiện tại và thời gian đã gán
            long currentTime1 = System.currentTimeMillis();
            System.out.println(currentTime1);
            if (currentTime1 - us.getLastTimeSet() >= us.getRandomTimeInMillis() ||us.getspamclickBoss()) {
                // Nếu thời gian đã hết, hiện thông báo rô bốt
                us.getAvatarService().openMenuOption(1000, 1,
                        "bạn có phải robot không? : Đúng rồi",
                        "robot là bạn hả ? : Chắc chắn rồi",
                        "robot hả : Không phải",
                        "bạn là robot : Yes sir");
                us.setspamclickBoss(true);
                us.setRandomTimeInMillis(0); // Đặt lại để lần sau có thể gán thời gian mới
                return;
            }
            if(us.getSession().isResourceHD()){
                List<Menu> listmenuboss = new ArrayList<>();
                Menu bossmenu = Menu.builder().name(" Đánh ").action(() -> {
                    us.getAvatarService().serverDialog("OK !");
                }).build();
                listmenuboss.add(bossmenu);
                us.setMenus(listmenuboss);
                us.getAvatarService().openMenuOption(npcId, 0, listmenuboss);
            }

            if (boss.isDefeated()) {
                us.getAvatarService().serverDialog("boss đã chết");
                return;
            }
            if (distance > maxDistance) {
                us.getAvatarService().serverDialog("Bạn đứng xa rồi : v");
                return;
            }
            LocalTime now = LocalTime.now();

            // Đặt khoảng thời gian hợp lệ
            LocalTime startTime = LocalTime.of(7, 0); // 6h sáng
            LocalTime endTime = LocalTime.of(22, 59);  // 11h đêm

            // Kiểm tra nếu thời gian hiện tại nằm trong khoảng
            if (now.isAfter(startTime) && now.isBefore(endTime)) {
                us.updateXuKillBoss(+1);
            } else {
                // Xử lý nếu thời gian không nằm trong khoảng
                System.out.println("Hàm không được kích hoạt ngoài khoảng thời gian từ 6h sáng đến 11h đêm.");
            }
            us.updateXu(+us.getDameToXu());

            us.getAvatarService().updateMoney(0);

            List<User> lstUs = us.getZone().getPlayers();

            int skill = us.getUseSkill();
            switch (skill) {
                case 0:
                    us.skillUidToBoss(lstUs,us.getId(),npcId,(byte)23,(byte)24);
                    break;
                case 1:
                    us.skillUidToBoss(lstUs,us.getId(),npcId,(byte)25,(byte)26);
                    break;
                case 2:
                    us.skillUidToBoss(lstUs,us.getId(),npcId,(byte)12,(byte)13);
                    break;
                case 3:
                    // Thực hiện hành động khi skill = 3
                    break;
                case 4:
                    us.skillUidToBoss(lstUs,us.getId(),npcId,(byte)38,(byte)39);
                    break;
                case 5:
                    us.skillUidToBoss(lstUs,us.getId(),npcId,(byte)42,(byte)43);
                    break;
                case 6:
                    us.skillUidToBoss(lstUs,us.getId(),npcId,(byte)36,(byte)37);
                    break;
                case 7:
                    us.skillUidToBoss(lstUs,us.getId(),npcId,(byte)44,(byte)48);
                    break;
                default:
                    us.skillUidToBoss(lstUs,us.getId(),npcId,(byte)23,(byte)24);
                    break;
            }
            boss.updateHP(-us.getDameToXu(),(Boss)boss, us);


//            }else if (us.findItemInWearing(4715)!=null) {
//                boss.updateHP(-us.getDameToXu(),(Boss)boss, us);


        } else if (npcIdCase >= 10000) {
            if (distance > maxDistance) {
                us.getAvatarService().serverDialog("Bạn đứng xa rồi : v");
                return;
            }
            if(boss.isSpam()){
                us.getAvatarService().serverDialog("hộp này đã nhặt");
                return;
            }
            us.updateSpam(-1,(Boss)boss,us);
        }else {
            switch (npcIdCase) {
                case NpcName.Than_Tai_Xiu: {
                    List<Menu> menuList = List.of(
                            Menu.builder().name("Cược bằng Xu").menus(
                                    List.of(
                                            Menu.builder().name("Cược Tài (Xu)").action(() -> {
                                                us.getAvatarService().sendTextBoxPopup(us.getId(), 0, "Nhập số xu bạn muốn cược vào Tài:", 1);
                                            }).build(),
                                            Menu.builder().name("Cược Xỉu (Xu)").action(() -> {
                                                us.getAvatarService().sendTextBoxPopup(us.getId(), 1, "Nhập số xu bạn muốn cược vào Xỉu:", 1);
                                            }).build()
                                    )).build(),

                            Menu.builder().name("Cược bằng Lượng").menus(
                                    List.of(
                                            Menu.builder().name("Cược Tài (Lượng)").action(() -> {
                                                us.getAvatarService().sendTextBoxPopup(us.getId(), 2, "Nhập số lượng bạn muốn cược vào Tài:", 1);
                                            }).build(),
                                            Menu.builder().name("Cược Xỉu (Lượng)").action(() -> {
                                                us.getAvatarService().sendTextBoxPopup(us.getId(), 3, "Nhập số lượng bạn muốn cược vào Xỉu:", 1);
                                            }).build()
                                    )).build(),

                            Menu.builder().name("lịch sử 10 ván").action(() -> {
                                TaiXiu.getInstance().viewGameRoundHistory(us);
                            }).build(),
                            Menu.builder().name("top cao thủ thắng và thua nhiều nhất").action(() -> {
                                TaiXiu.getInstance().getTopWinerXu(us);
                                TaiXiu.getInstance().getTopLossXu(us);
                                TaiXiu.getInstance().getTopWinLuong(us);
                                TaiXiu.getInstance().getTopLossLuong(us);
                                us.getAvatarService().serverDialog("check tin nhắn đi");
                            }).build(),
                            Menu.builder().name("Thành tích").action(() -> {
                                TaiXiu.getInstance().viewBetHistory(us);
                            }).build(),
                            Menu.builder().name("Thoát").action(() -> {
                            }).build()
                    );
                    us.setMenus(menuList);
                    us.getAvatarService().openMenuOption(npcId, 0, menuList);
                    break;
                }

                case NpcName.Tien_chi_mu_Lovanga: {
                    List<Menu> list = new ArrayList<>();
                    Menu tienchi = Menu.builder().name("Dự đoán vị trí người yêu").action(() -> {
                        us.getService().serverDialog(us.getService().DuDoanNY(us));
                    }).build();
                    list.add(tienchi);
                    list.add(Menu.builder().name("Dự đoán kết quả sổ số").action(() -> {
                        us.getService().serverDialog("Dự đoán kết quả : Comingsion");
                    }).build());
                    list.add(Menu.builder().name("Dự đoán kết quả Tài Xỉu").action(() -> {
                        us.getService().serverDialog("Dự đoán kết quả : Comingsion");
                    }).build());
                    list.add(Menu.builder().name("Thoát").build());
                    us.setMenus(list);
                    us.getAvatarService().openMenuOption(npcId, 0, list);
                    break;
                }
                case NpcName.SAITAMA: {
                    List<Menu> QuanLyItem = new ArrayList<>();
                    Menu QuanLyeye = Menu.builder().name("Quản Lý Mặt").action(() -> {
                        List<Item> _chests = us.chests.stream().filter(item -> {
                            return item.getPart().getZOrder() == 30;
                        }).collect(Collectors.toList());
                        us.getAvatarService().viewChest(_chests);

                    }).build();
                    QuanLyItem.add(QuanLyeye);
                    QuanLyItem.add(Menu.builder().name("Quản Lý Mắt").action(() -> {
                        List<Item> _chests = us.chests.stream().filter(item -> {
                            return item.getPart().getZOrder() == 40;
                        }).collect(Collectors.toList());
                        us.getAvatarService().viewChest(_chests);
                    }).build());

                    QuanLyItem.add(Menu.builder().name("Xem Ô Rương Còn Trống").action(() -> {
                        int totalSlots = us.getChestSlot(); // Tổng số ô rương của người dùng
                        int usedSlots = us.chests.size();   // Số ô hiện đang sử dụng trong rương
                        int emptySlots = totalSlots - usedSlots; // Tính số ô trống còn lại

                        // Kiểm tra nếu còn trống, không thì hiển thị rương đã đầy
                        if (emptySlots > 0) {
                            us.getAvatarService().serverDialog("Số ô trống còn lại trong rương: " + emptySlots + "/" + totalSlots + " Rương Lv " + us.session.user.getChestLevel());
                        } else {
                            us.getAvatarService().serverDialog("Rương đã đầy!");
                        }
                    }).build());


                    QuanLyItem.add(Menu.builder().name("Xóa Vật Phẩm Trùng (Mặt và Mắt)").action(() -> {
                        // Lọc ra các vật phẩm có ZOrder là 30 hoặc 40
                        List<Item> filteredItems = us.chests.stream()
                                .filter(item -> {
                                    int zOrder = item.getPart().getZOrder();
                                    return zOrder == 30 || zOrder == 40;
                                })
                                .collect(Collectors.toList());

                        // Tạo một Map để giữ lại mỗi loại item duy nhất dựa trên ID
                        Map<Integer, Item> uniqueItemsMap = filteredItems.stream()
                                .collect(Collectors.toMap(
                                        Item::getId,         // Sử dụng ID để xác định vật phẩm trùng
                                        item -> item,
                                        (existing, duplicate) -> existing // Giữ lại item đầu tiên nếu trùng
                                ));

                        // Xóa các vật phẩm có ZOrder 30 hoặc 40 trong `chests`
                        us.chests.removeIf(item -> {
                            int zOrder = item.getPart().getZOrder();
                            return zOrder == 30 || zOrder == 40;
                        });

                        // Thêm lại các vật phẩm duy nhất vào `chests`
                        us.chests.addAll(uniqueItemsMap.values());

                        //us.getAvatarService().viewChest(us.chests);  // Hiển thị lại danh sách không còn vật phẩm trùng lặp
                        us.getAvatarService().serverDialog("ok bạn ơi");
                    }).build());

                    QuanLyItem.add(Menu.builder().name("Thoát").build());
                    us.setMenus(QuanLyItem);
                    us.getAvatarService().openMenuOption(npcId, 0, QuanLyItem);
                    break;
                }
                case NpcName.bunma: {
                    List<Menu> list1 = new ArrayList<>();
                    Menu Event = Menu.builder().name("Đổi Quà").action(() -> {
                        ShopEventHandler.displayUI(us, bunma, 3861,2295,2543,4277,3494,4261,5103,3962,5104,3964,5130,5539,4726,4903,3364,3509,3679,2577,4195,3358,5303,5304,5305,5306,5307,5308,6415,5501,5502,6429,5126,4044,2119);
                    }).build();
                    list1.add(Event);
                    list1.add(Menu.builder().name("Góp Kẹo")
                            .action(() -> {
                                GopDiemSK(us);
                            })
                            .build());
                    list1.add(Menu.builder().name("Nhận thưởng đua top")
                            .action(() -> {
                                try {
                                    us.session.NhanThuongEventluong();
                                    us.session.NhanThuongEventXuBoss();
                                } catch (IOException e) {
                                    throw new RuntimeException(e);
                                }
                            })
                            .build());

                    list1.add(Menu.builder().name("Thành tích bản thân")
                            .action(() -> {
                                StringBuilder detailedMessage = new StringBuilder("Thành tích bản thân");
                                detailedMessage.append(String.format("\n Bạn đang có %d điểm sự kiện", us.getScores()));
                                int rankPhaoLuong = us.getService().getUserRankPhaoLuong(us);
                                detailedMessage.append(String.format("\n Bạn đang ở top %d thả pháo lượng : %d", rankPhaoLuong, us.getTopPhaoLuong()));

//                                int rankPhaoXu = us.getService().getUserRankPhaoXu(us);
//                                detailedMessage.append(String.format("\n Bạn đang ở top %d thả pháo xu : %d", rankPhaoXu, us.getTopPhaoXu()));

                                int rankXuboss = us.getService().getUserRankXuBoss(us);
                                detailedMessage.append(String.format("\n Bạn đang ở top %d điểm đánh boss : %d", rankXuboss, us.getXu_from_boss()));
                                us.getAvatarService().serverDialog(detailedMessage.toString());
                            })
                            .build());
                    list1.add(Menu.builder().name("Bảng xếp hạng thả pháo lượng")
                            .action(() -> {
                                List<User> topPlayers = us.getService().getTopPhaoLuong();
                                StringBuilder result = new StringBuilder();
                                int rank = 1; // Biến đếm để theo dõi thứ hạng

                                for (User player : topPlayers) {
                                    if (player.getTopPhaoLuong() > 0) {
                                        result.append(player.getUsername())
                                                .append(" Top ").append(rank).append(" : ")
                                                .append(player.getTopPhaoLuong())
                                                .append("\n");
                                        rank++; // Tăng thứ hạng sau mỗi lần thêm người chơi vào kết quả
                                    }
                                }
                                us.getAvatarService().customTab("Top 10 thả pháo lượng", result.toString());
                            })
                            .build());
//                    list1.add(Menu.builder().name("Bảng xếp hạng thả pháo Xu")
//                            .action(() -> {
//                                List<User> topPlayers = us.getService().getTopPhaoXu();
//                                StringBuilder result = new StringBuilder();
//                                int rank = 1; // Biến đếm để theo dõi thứ hạng
//
//                                for (User player : topPlayers) {
//                                    if (player.getTopPhaoXu() > 0) {
//                                        result.append(player.getUsername())
//                                                .append(" Top ").append(rank).append(" : ")
//                                                .append(player.getTopPhaoXu())
//                                                .append("\n");
//                                        rank++; // Tăng thứ hạng sau mỗi lần thêm người chơi vào kết quả
//                                    }
//                                }
//                                us.getAvatarService().customTab("Top 10 thả pháo xu", result.toString());
//                            })
//                            .build());
                    list1.add(Menu.builder().name("Bảng xếp hạng điểm đánh boss")
                            .action(() -> {
                                List<User> topPlayers = us.getService().getTop10PlayersByXuFromBoss();
                                StringBuilder result = new StringBuilder();
                                int rank = 1; // Biến đếm để theo dõi thứ hạng

                                for (User player : topPlayers) {
                                    if (player.getXu_from_boss() > 0) {
                                        result.append(player.getUsername())
                                                .append(" Top ").append(rank).append(" : ")
                                                .append(player.getXu_from_boss())
                                                .append(" Điểm\n");
                                        rank++; // Tăng thứ hạng sau mỗi lần thêm người chơi vào kết quả
                                    }
                                }
                                us.getAvatarService().customTab("Top 10 Điểm đánh boss", result.toString());
                            })
                            .build());
                    list1.add(Menu.builder().name("Xem hướng dẫn")
                            .action(() -> {
                                us.getAvatarService().customTab("Hướng dẫn", "cứ đánh boss là 1 điểm chỉ tính trong thời gian từ 7h đến 23h hàng ngày");
                            })
                            .build());
                    list1.add(Menu.builder().name("Thoát").id(npcId).build());
                    us.setMenus(list1);
                    us.getAvatarService().openMenuOption(npcId, 0, list1);
                    break;
                }
                case NpcName.Vegeta: {
                    List<Menu> lstVegeta = new ArrayList<>();


                    Menu vegenta = Menu.builder().name("Quà Thẻ VIP PREMIUM").action(() -> {
                        ShopEventHandler.displayUI(us, Vegeta, 6450,6314,6555,5822,4560,4698,4699,6430,5651,5455,5341);
                    }).build();
                    lstVegeta.add(vegenta);

                    lstVegeta.add(Menu.builder().name("Quà Thẻ VIP Cao Cấp").action(() -> {
                        ShopEventHandler.displayUI(us, Vegeta, 6113,6553,4561,4562,4563,4304,6449,5761);
                    }).build());

                    lstVegeta.add(Menu.builder().name("Quà Thẻ VIP").action(() -> {
                        ShopEventHandler.displayUI(us, Vegeta, 3638, 3636, 620, 2090, 6541, 2052, 2053, 3636, 3638);
                    }).build());

                    lstVegeta.add(Menu.builder().name("Xem hướng dẫn")
                            .action(() -> {
                                us.getAvatarService().customTab("Hướng dẫn", "Top 5 đánh boss nhận 1 thẻ vip trong top 3 được tùy chọn 1 trong 2 set Xác ướp hoặc Tù trưởng xác ướp riêng top 1 nhận thêm 1 thẻ vip. Top 4-5 thả pháo lượng nhận 2 thẻ vip trong top 3 nhận thẻ vip cao cấp và được tùy chọn 1 trong 3 set xác ướp , tù trưởng xác ướp, doremon (doremon thì ko có dame)");
                            })
                            .build());
                    lstVegeta.add(Menu.builder().name("Thoát").id(npcId).build());
                    us.setMenus(lstVegeta);

                    us.getAvatarService().openMenuOption(npcId, 0, lstVegeta);
                    break;
                }
                case NpcName.CHU_DAU_TU: {
                    List<Menu> chudautu = new ArrayList<>();
                    Menu chuDautu = Menu.builder().name("Mua biệt thự").action(() -> {
                        try {
                            us.session.BuyHouse();
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    }).build();
                    chudautu.add(chuDautu);
                    us.setMenus(chudautu);
                    us.getAvatarService().openMenuOption(npcId, 0, chudautu);
                    break;
                }

                case NpcName.DAU_GIA: {
                    us.getAvatarService().serverDialog("đấu giá đang bảo trì các bạn vui lòng quay lại sau");
                    break;
                }
//                    case NpcName.VE_SO:{
//                        List<Menu> listet = new ArrayList<>();
//                        List<Item> Items = Part.shopByPart(PartManager.getInstance().getParts());
//                        Menu quaySo = Menu.builder().name("vật phẩm").menus(
//                                        List.of(
//                                                Menu.builder().name("demo item").action(() -> {
//                                                    us.getAvatarService().openUIShop(-49,"em.thinh",Items);
//                                                }).build()
//                                        ))
//                                .id(npcId)
//                                .npcName("donate đi")
//                                .npcChat("show Item")
//                                .build();
//                        listet.add(quaySo);
//                        listet.add(Menu.builder().name("Hướng dẫn").action(() -> {
//                            us.getAvatarService().customTab("Hướng dẫn", "hãy nạp lần đầu để mở khóa mua =)))");
//                        }).build());
//                        listet.add(Menu.builder().name("Thoát").build());
//                        us.setMenus(listet);
//                        us.getAvatarService().openUIMenu(npcId, 0, listet, "donate đi", "");
//                        break;
//                    }
                case NpcName.QUAY_SO: {
                    List<Menu> qs = new ArrayList<>();
                    Menu quaySo1 = Menu.builder().name("Quay số").menus(
                                    List.of(
                                            Menu.builder().name("5 lượng").action(() -> {
                                                System.out.println("Action for 5 lượng triggered");
                                                handleDiaLucky(us, DialLuckyManager.LUONG);
                                            }).build(),
                                            Menu.builder().name("25.000 xu").action(() -> {
                                                System.out.println("Action for 15.000 xu triggered");
                                                handleDiaLucky(us, DialLuckyManager.XU);
                                            }).build(),
                                            Menu.builder().name("Q.S miễn phí").action(() -> {
                                                System.out.println("Action for Q.S miễn phí triggered");
                                                handleDiaLucky(us, DialLuckyManager.MIEN_PHI);
                                            }).build(),
                                            Menu.builder().name("Thoát").action(() -> {
                                                System.out.println("Exit menu triggered");
                                            }).build()
                                    ))
                            .id(npcId)
                            .build();
                    qs.add(quaySo1);
                    qs.add(Menu.builder().name("Xem hướng dẫn").action(() -> {
                        System.out.println("Action for Xem hướng dẫn triggered");
                        us.getAvatarService().customTab("Hướng dẫn", "Để tham gia quay số bạn phải có ít nhất 5 lượng hoặc 25 ngàn xu trong tài khoản và 3 ô trống trong rương\n Bạn sẽ nhận được danh sách những món đồ đặc biệt mà bạn muốn quay. Những món đồ đặc biệt này bạn sẽ không thể tìm thấy trong bất cứ shop nào của thành phố.\n Sau khi chọn được món đồ muốn quay bạn sẽ bắt đầu chỉnh vòng quay để quay\n Khi quay bạn giữ phím 5 để chỉnh lực quay sau đó thả ra để bắt đầu quay\n Khi quay bạn sẽ có cơ hội trúng từ 1 đến 3 món quà\n Quà của bạn nhận được có thể là vật phẩm bất kì, xu, hoặc điểm kinh nghiệm\n Bạn có thể quay được những bộ đồ bán bằng lượng như đồ hiệp sĩ, pháp sư...\n Tuy nhiên vật phẩm bạn quay được sẽ có hạn sử dụng trong một số ngày nhất định.\n Nếu bạn quay được đúng món đồ mà bạn đã chọn thì bạn sẽ được sở hữu món đồ đó vĩnh viễn.\n Hãy thử vận may để sở hữa các món đồ cực khủng nào !!!");
                    }).build());
                    qs.add(Menu.builder().name("Thoát").build());
                    us.setMenus(qs);
                    us.getAvatarService().openMenuOption(npcId, 0, qs);
                    break;
                }
                case NpcName.THO_KIM_HOAN: {
                    List<Menu> nangcap = new ArrayList<>();
                    String npcChat = "Muốn nâng cấp đồ thì vào đây";
                    Menu upgrade = Menu.builder().name("Nâng cấp").id(npcId).menus(
                                    List.of(
                                            Menu.builder().name("Nâng cấp xu").id(npcId)
                                                    .menus(listItemUpgrade(npcId, us, BossShopHandler.SELECT_XU))
                                                    .build(),
                                            Menu.builder().name("Nâng cấp lượng").id(npcId)
                                                    .menus(listItemUpgrade(npcId, us, BossShopHandler.SELECT_LUONG))
                                                    .id(npcId)
                                                    .build(),
                                            Menu.builder().name("Thoát").id(npcId).build()
                                    )
                            )
                            .build();
                    nangcap.add(upgrade);
                    nangcap.add(Menu.builder().name("Xem hướng dẫn")
                            .action(() -> {
                                us.getAvatarService().customTab("Hướng dẫn", "Nâng thì nâng không nâng thì cút!");
                            })
                            .build());
                    nangcap.add(Menu.builder().name("Thoát").id(npcId).build());
                    us.setMenus(nangcap);
                    us.getAvatarService().openMenuOption(npcId, 0, nangcap);
                    break;
                }
                case NpcName.LAI_BUON: {
                    List<Menu> laibuon = new ArrayList<>();
                    Menu LAI_BUON = Menu.builder().name("Điểm Danh").action(() -> {
                        //Item item = new Item(593, -1, 1);
                        //us.addItemToChests(item);
                        //us.addExp(5);
                        us.getService().serverMessage("đang xây dựng");//Bạn nhận được 5 điểm exp + 1 thẻ quay số miễn phí");
                    }).build();
                    laibuon.add(LAI_BUON);
                    laibuon.add(Menu.builder().name("Xem hướng dẫn").action(() -> {
                        us.getAvatarService().customTab("Hướng dẫn", "Đăng nhập mỗi ngày để nhận quà.\nDùng điểm chuyên cần để nhận đucợ những món quà có giá trị trong tương lai");
                    }).build());
                    laibuon.add(Menu.builder().name("Thoát").build());
                    us.setMenus(laibuon);
                    us.getAvatarService().openMenuOption(npcId, 0, laibuon);
                    break;
                }
                case NpcName.THO_CAU: {
                    List<Menu> thocau = new ArrayList<>();
                    Menu thoCau = Menu.builder().name("Câu cá").action(() -> {
                        List<Item> Items1 = new ArrayList<>();
                        Item item = new Item(446, 30, 0);//câu vip
                        Items1.add(item);
                        Item item1 = new Item(460, 2, 0);//vé cau
                        Items1.add(item1);
                        Item item2 = new Item(448, 30, 1);//mồi
                        Items1.add(item2);
                        us.getAvatarService().openUIShop(npcId, "Trùm Câu Cá,", Items1);
                        us.getAvatarService().updateMoney(0);
                    }).build();
                    thocau.add(thoCau);
                    thocau.add(Menu.builder().name("Bán cá").action(() -> {
                        try {
                            sellFish(us);
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    }).build());
                    thocau.add(Menu.builder().name("Xem hướng dẫn").action(() -> {
                        us.getAvatarService().customTab("Hướng dẫn", "Câu cá kiếm được nhiều xu bản auto lên thanhpholo.com");
                    }).build());
                    thocau.add(Menu.builder().name("Thoát").build());
                    us.setMenus(thocau);
                    us.getAvatarService().openMenuOption(npcId, 0, thocau);
                    break;
                }
                case NpcName.CUA_HANG: {
                    List<Menu> listet = new ArrayList<>();
                    List<Item> Items = new ArrayList<>();

                    Menu quaySo = Menu.builder().name("shop noname").action(() ->{
                        List<Item> itemshop0 = Part.shopByPart(PartManager.getInstance().getShop0());

                        if (itemshop0 == null) {
                            System.out.println("Items list is null");
                            return; // Handle the null case
                        }
                        us.getAvatarService().openUIShop(5, "shop 0", itemshop0);
                    }).build();
                    listet.add(quaySo);
                    listet.add(Menu.builder().name("Hướng dẫn").action(() -> {
                        us.getAvatarService().customTab("Hướng dẫn", "chua co huong dan");
                    }).build());
                    listet.add(Menu.builder().name("Thoát").build());
                    us.setMenus(listet);
                    us.getAvatarService().openUIMenu(npcId, 0, listet, "text 1", "text2");
                    break;
                }
                case NpcName.Shop_Buy_Luong: {
                    List<Menu> ListDacBiet = new ArrayList<>();
                    Menu ShopDacBiet = Menu.builder().name("Đổi Quà").action(() -> {
                        ShopEventHandler.displayUI(us, Shop_Buy_Luong,3672,5898,4331,3112,4736);
                    }).build();
                    ListDacBiet.add(ShopDacBiet);
                    ListDacBiet.add(Menu.builder().name("Xem hướng dẫn")
                            .action(() -> {
                                us.getAvatarService().customTab("Hướng dẫn", "co cai d.");
                            })
                            .build());
                    ListDacBiet.add(Menu.builder().name("Thoát").id(npcId).build());
                    us.setMenus(ListDacBiet);
                    us.getAvatarService().openMenuOption(npcId, 0, ListDacBiet);
                    break;
                }
                case NpcName.Pay_To_Win: {
                    List<Menu> ListDacBiet = new ArrayList<>();
                    Menu ShopDacBiet = Menu.builder().name("Pay To Win").action(() -> {
                        ShopEventHandler.displayUI(us, Pay_To_Win,6803,6824,3076);
                    }).build();
                    ListDacBiet.add(ShopDacBiet);
                    Menu ShopQuaSet = Menu.builder().name("Pay To Win cả Set").action(() -> {
                        ShopEventHandler.displayUI(us, Pay_To_Win,5880,5324,5408,4345,6556,6560);
                    }).build();
                    ListDacBiet.add(ShopQuaSet);
                    ListDacBiet.add(Menu.builder().name("Shop Nâng Cấp Pay To Win").id(npcId)
                            .menus(listItemUpgradePay(npcId, us, BossShopHandler.SELECT_HoaNS))
                            .build());
                    ListDacBiet.add(Menu.builder().name("Đổi quà cả Set là gì ?")
                            .action(() -> {
                                us.getAvatarService().customTab("Hướng dẫn", "Đổi cả set là được cả set(ít nhất 3 món) , Hộp quà vũ trụ mở ra set 80 dame như : iron , Venmon , DeadPool,(nam,nữ) Dr Strange(nam),  TiDus(nam)/Yuna(nữ), Batman(nam), Người mèo(nữ) | hộp quà siêu nhân 50 dame : gao xanh, đỏ ,đen,| hộp quà hải tặc (80 dame) : Luffy: Nam,Nami: Nữ,Mihawk: Nam,Nico Robin: Nữ,Zoro: Nam");
                            })
                            .build());
                    ListDacBiet.add(Menu.builder().name("Sen Ngũ Sắc, Đá Ngũ Sắc ở đâu?")
                            .action(() -> {
                                us.getAvatarService().customTab("Hướng dẫn", "Sen Ngũ Sắc khi nạp 20k(1400lg) bạn sẽ được kèm thêm 50 Sen Ngũ Sắc chỉ nạp mới có, còn Đá ngũ sắc mở từ hộp quà may mắn (nhặt khi đánh boss) , mua bằng lượng thì qua hawai");
                            })
                            .build());
//                    ListDacBiet.add(Menu.builder().name("đổi từ Kim Cương Vũ Trụ qua Hoa Ngũ sắc")
//                            .action(() -> {
//                                Item kcvt = us.findItemInChests(690);
//                                if (kcvt != null && kcvt.getQuantity() > 0) {
//                                    int quantity = kcvt.getQuantity();
//                                    double quantityDouble = quantity * 2.5;
//                                    us.removeItem(kcvt.getId(),quantity);
//                                    Item senns = new Item(5389,-1,(int)quantityDouble);
//                                    us.addItemToChests(senns);
//                                    us.getAvatarService().serverDialog("Bạn vừa đổi thành công "+ quantity +" Kim Cương thành "+ quantityDouble + " Sen ngũ sắc");
//                                }else {
//                                    us.getAvatarService().serverDialog("Bạn không có kim cương vũ trụ để đổi");
//                                }
//                            })
//                            .build());
                    ListDacBiet.add(Menu.builder().name("Thoát").id(npcId).build());
                    us.setMenus(ListDacBiet);
                    us.getAvatarService().openMenuOption(npcId, 0, ListDacBiet);
                    break;
                }
                case NpcName.Chay_To_Win: {
                    List<Menu> ListDacBiet = new ArrayList<>();
                    Menu ShopDacBiet = Menu.builder().name("Chay To Win (Xu)").action(() -> {
                        ShopEventHandler.displayUI(us, Chay_To_Win,2049,2050,2051,2054,2041,2056,2354,2355,3440,3441,3442,3443,3445,3446,3627,3628,3629,3630,3631,3632,3633,3634,3360,6822,6731,4736);
                    }).build();
                    ListDacBiet.add(ShopDacBiet);
                    ListDacBiet.add(Menu.builder().name("Shop Nâng Cấp Chay To Win").id(npcId)
                            .menus(listItemUpgradeChay(npcId, us, BossShopHandler.SELECT_DNS))
                            .build());
                    ListDacBiet.add(Menu.builder().name("Shop Đổi Bằng Đá Ngũ Sắc").action(() -> {
                        ShopEventHandler.displayUI(us, Pay_To_Win,3743,3742);
                    }).build());
                    ListDacBiet.add(Menu.builder().name("Sen Ngũ Sắc, Đá Ngũ Sắc ở đâu ?")
                            .action(() -> {
                                us.getAvatarService().customTab("Hướng dẫn", "Sen Ngũ Sắc khi nạp trên 20k(1400lg) bạn sẽ được kèm thêm 50 Sen Ngũ Sắc chỉ nạp mới có, còn Đá ngũ sắc mở từ hộp quà may mắn (nhặt khi đánh boss) , mua bằng lượng thì qua hawai");
                            })
                            .build());
                    ListDacBiet.add(Menu.builder().name("Thoát").id(npcId).build());
                    us.setMenus(ListDacBiet);
                    us.getAvatarService().openMenuOption(npcId, 0, ListDacBiet);
                    break;
                }
            }
        }
    }

    public static void sellFish(User us) throws IOException {
        int[] array = {2130,2131,2132,454,455,456,457};

        for (int idFish : array) {
            Item item = us.findItemInChests(idFish); // Tìm item trong rương theo idFish

            // Nếu không tìm thấy item, tiếp tục với ID tiếp theo
            while (item != null && item.getQuantity() > 0) {
                int sellPrice = item.getPart().getCoin(); // Giá bán 1 món đồ
                String message = String.format("Bạn vừa bán 1 %s với giá = %d xu.", item.getPart().getName(), sellPrice);

                us.removeItemFromChests(item);
                us.updateXu(sellPrice); // Cập nhật xu

                us.getAvatarService().updateMoney(0); // Cập nhật tiền tệ
                us.getAvatarService().SendTabmsg(message); // Gửi thông báo bán hàng

                // Cập nhật lại item để kiểm tra số lượng
                item = us.findItemInChests(idFish);
            }
        }
    }

    public static void GopDiemSK(User us){
        java.util.Map<Integer, Integer> itemsToProcess = new HashMap<>();
        itemsToProcess.put(2383, 1);
        itemsToProcess.put(2384, 2);
        itemsToProcess.put(2385, 3);
        int addscores = 0;
// Lặp qua từng cặp ID và số lượng

        StringBuilder detailedMessage = new StringBuilder("Bạn đã đổi thành công:");
        for (java.util.Map.Entry<Integer, Integer> entry : itemsToProcess.entrySet()) {
            int itemId = entry.getKey();
            int scores = entry.getValue();
            Item item = us.findItemInChests(itemId);
            if (item != null && item.getQuantity() > 0) {
                addscores += item.getQuantity()*scores;
                detailedMessage.append(String.format("\n%s :(Điểm %d) Số lượng %d  x  tong %d điểm", item.getPart().getName(), scores, item.getQuantity(), item.getQuantity()*scores));
                us.updateScores(+addscores);
                us.removeItem(itemId, item.getQuantity());
            }
        }
        if(addscores > 0){
            detailedMessage.append(String.format("\n Tổng tất cả %d",addscores) +" điểm");
            us.getAvatarService().serverDialog(detailedMessage.toString());
        }else {
            us.getAvatarService().serverDialog("Bạn không còn kẹo");
        }
    }

    public static List<Menu> listItemUpgradeChay(int npcId, User us, byte type) {
        return List.of(
                Menu.builder()
                        .name("Nâng cấp item Chay To Win")
                        .id(npcId)
                        .action(() -> {
                            BossShopHandler.displayUI(us, BossShopHandler.SELECT_DNS, 3774,3776,3775,4157,5012,5010,5011,5401,6491,3855);
                        })
                        .build(),
                Menu.builder()
                        .name("Nâng cấp Item Quay Số/Nâng Cấp")//Nâng bằng đá ngũ sắc;
                        .id(npcId)
                        .action(() -> {
                            BossShopHandler.displayUI(us, BossShopHandler.SELECT_DNS, 4907,4119,5516,5099,5337,5342,5470,5667,5846,6575);
                        })
                        .build(),
                Menu.builder()
                        .name("Đổi item từ mảnh ghép")//đổi mảnh ghép thành item
                        .id(npcId)
                        .action(() -> {
                            BossShopHandler.displayUI(us, BossShopHandler.SELECT_ManhGhep, 6837);
                        })
                        .build()
        );
    }

    public static List<Menu> listItemUpgradePay(int npcId, User us, byte type) {
        return List.of(
                Menu.builder()
                        .name("Pay, Nâng cấp item")
                        .id(npcId)
                        .action(() -> {
                            BossShopHandler.displayUI(us, BossShopHandler.SELECT_HoaNS, 6671,6672,5012,5010,5011,5401,6491);
                        })
                        .build(),
                Menu.builder()
                        .name("Pay, Nâng cấp Item Quay Số/Nâng Cấp")
                        .id(npcId)
                        .action(() -> {
                            BossShopHandler.displayUI(us, BossShopHandler.SELECT_HoaNS, 6671,6672);
                        })
                        .build()
        );
    }


    public static List<Menu> listItemUpgrade(int npcId, User us, byte type) {
        //String npcName = "Thợ KH";
        //String npcChat = "Muốn đồ đang mặc đẹp hơn không? Ta có thể giúp bạn đấy";
        return List.of(
                Menu.builder().name("Quà cầm tay").id(npcId)
                        .menus(List.of(
                                        Menu.builder().name("Bông hoa cổ tích").action(() -> {
                                            BossShopHandler.displayUI(us, type, 6212, 6213, 6214);
                                        }).build(),
                                        Menu.builder().name("Hoa hồng phong thần").action(() -> {
                                            BossShopHandler.displayUI(us, type, 5321, 5322, 5323);
                                        }).build(),
                                        Menu.builder().name("Hoa hồng xanh pha lê thần thoại").action(() -> {
                                            BossShopHandler.displayUI(us, type, 5286, 5287, 5288);
                                        }).build(),
                                        Menu.builder().name("Mộc thảo hồ điệp").action(() -> {
                                            BossShopHandler.displayUI(us, type, 4160, 4161, 4162, 4163, 5050);
                                        }).build(),
                                        Menu.builder().name("Cung thần tình yêu thần thoại").action(() -> {
                                            BossShopHandler.displayUI(us, type, 4893, 4894, 4895);
                                        }).build(),
                                        Menu.builder().name("Cung xanh thần thoại").action(() -> {
                                            BossShopHandler.displayUI(us, type, 4890, 4891, 4892);
                                        }).build(),
                                        Menu.builder().name("Gậy thả thính mê hoặc").action(() -> {
                                            BossShopHandler.displayUI(us, type, 3507, 4218);
                                        }).build(),
                                        Menu.builder().name("Chong chóng thiên thần").action(() -> {
                                            BossShopHandler.displayUI(us, type, 2238, 2239, 2274, 2275, 2404);
                                        }).build(),
                                        Menu.builder().name("Cục vàng huyền thoại").action(() -> {
                                            BossShopHandler.displayUI(us, type, 2217, 2218, 2219, 2220, 2221, 2222, 2223);
                                        }).build()

                                )
                        )
                        .build(),
                Menu.builder().name("Nón").menus(List.of(
                                Menu.builder().name("Nón phù thuỷ hoả ngục truyền thuyết").action(() -> {
                                    BossShopHandler.displayUI(us, type, 2411, 2412, 2413, 2414, 5503, 5504);
                                }).build(),
                                Menu.builder().name("Vương miện hoàng thân").action(() -> {
                                    BossShopHandler.displayUI(us, type, 5394);
                                }).build(),
                                Menu.builder().name("Vương miện hoàng thân").action(() -> {
                                    BossShopHandler.displayUI(us, type, 5391);
                                }).build(),
                                Menu.builder().name("Tôi thấy hoa vàng trên cỏ xanh").action(() -> {
                                    BossShopHandler.displayUI(us, type, 3266, 3267, 3268, 3269, 3954);
                                }).build(),
                                Menu.builder().name("Vương miện phép màu").action(() -> {
                                    BossShopHandler.displayUI(us, type, 3422, 3423, 3639, 3640);
                                }).build(),
                                Menu.builder().name("Mũ ảo thuật tinh anh").action(() -> {
                                    BossShopHandler.displayUI(us, type, 2899, 2900, 2901, 2902, 2903, 3037, 3038, 3039);
                                }).build(),
                                Menu.builder().name("Vương miện huyền vũ").action(() -> {
                                    BossShopHandler.displayUI(us, type, 2997, 2998, 2999);
                                }).build()
                        ))
                        .build(),
                Menu.builder().name("Trang phục")
                        .menus(
                                List.of(
                                        Menu.builder().name("Danh gia vọng tộc").action(() -> {
                                            BossShopHandler.displayUI(us, type, 5392, 5393);
                                        }).build(),
                                        Menu.builder().name("Nữ hoàng sương mai").action(() -> {
                                            BossShopHandler.displayUI(us, type, 5054, 5055);
                                        }).build(),
                                        Menu.builder().name("Bá tước bóng đêm").action(() -> {
                                            BossShopHandler.displayUI(us, type, 2876, 2877);
                                        }).build(),
                                        Menu.builder().name("Napoleon").action(() -> {
                                            BossShopHandler.displayUI(us, type, 2231, 2232);
                                        }).build(),
                                        Menu.builder().name("Elizabeth").action(() -> {
                                            BossShopHandler.displayUI(us, type, 2229, 2230);
                                        }).build()
                                )
                        )
                        .build(),
                Menu.builder().name("Cánh")
                        .menus(
                                List.of(
                                        Menu.builder().name("Cánh Thần Mặt Trời").action(() -> {
                                            BossShopHandler.displayUI(us, type, 5312);
                                        }).build(),
                                        Menu.builder().name("Cánh chiến thần hắc hoá").action(() -> {
                                            BossShopHandler.displayUI(us, type, 5971, 5972, 5973, 5974);
                                        }).build(),
                                        Menu.builder().name("Cánh quạ đen hoả ngục").action(() -> {
                                            BossShopHandler.displayUI(us, type, 4332, 5313);
                                        }).build(),
                                        Menu.builder().name("Cánh tiểu thần phong linh").action(() -> {
                                            BossShopHandler.displayUI(us, type, 2419, 2482, 2483, 2505, 2506, 5252, 5253);
                                        }).build(),
                                        Menu.builder().name("Cửu vỹ hồ ly thần thoại").action(() -> {
                                            BossShopHandler.displayUI(us, type, 4333, 4910, 4911, 4912, 4913, 4914, 4915, 4916, 4334, 4889);
                                        }).build(),
                                        Menu.builder().name("Cánh vàng ròng đa sắc").action(() -> {
                                            BossShopHandler.displayUI(us, type, 3376, 3377, 3404, 4897);
                                        }).build(),
                                        Menu.builder().name("Cánh thiên thần tiên bướm").action(() -> {
                                            BossShopHandler.displayUI(us, type, 4056, 4796);
                                        }).build(),
                                        Menu.builder().name("Cánh thiên hồ tình yêu vĩnh cửu").action(() -> {
                                            BossShopHandler.displayUI(us, type, 4196, 4435);
                                        }).build(),
                                        Menu.builder().name("Cánh băng hoả thần thoại").action(() -> {
                                            BossShopHandler.displayUI(us, type, 3448, 4057, 4375);
                                        }).build(),
                                        Menu.builder().name("Cánh hoả thần").action(() -> {
                                            BossShopHandler.displayUI(us, type, 4311, 4312, 4313);
                                        }).build(),
                                        Menu.builder().name("Cánh thiên sứ tình yêu").action(() -> {
                                            BossShopHandler.displayUI(us, type, 2148, 2149, 2150, 2151, 2152, 3637);
                                        }).build(),
                                        Menu.builder().name("Cánh thiên sứ").action(() -> {
                                            BossShopHandler.displayUI(us, type, 2142, 2143, 2144, 2145, 2146, 3635);
                                        }).build(),
                                        Menu.builder().name("Cánh địa ngục hắc ám").action(() -> {
                                            BossShopHandler.displayUI(us, type, 3529, 3530, 3531, 3532);
                                        }).build(),
                                        Menu.builder().name("Cánh cổng địa ngục").action(() -> {
                                            BossShopHandler.displayUI(us, type, 3522, 3523, 3524, 3525, 3526, 3527);
                                        }).build(),
                                        Menu.builder().name("Cánh bướm đêm huyền thoại").action(() -> {
                                            BossShopHandler.displayUI(us, type, 3366, 3379);
                                        }).build(),
                                        Menu.builder().name("Cánh băng giá huyền thoại").action(() -> {
                                            BossShopHandler.displayUI(us, type, 3365, 3378);
                                        }).build(),
                                        Menu.builder().name("Cánh phép màu ước mơ").action(() -> {
                                            BossShopHandler.displayUI(us, type, 2793, 2794, 2795, 2796);
                                        }).build(),
                                        Menu.builder().name("Cánh blue vững vàng").action(() -> {
                                            BossShopHandler.displayUI(us, type, 2788, 2789, 2790, 2791);
                                        }).build()
                                )
                        )
                        .build(),
                Menu.builder().name("Thú cưng")
                        .menus(List.of(
//                                Menu.builder().name("Lang thần lãnh nguyên").action(() -> {
//                                    BossShopHandler.displayUI(us, type, 5517, 5518);
//                                }).build(),
//                                Menu.builder().name("Thiên thần hồ điệp").action(() -> {
//                                    BossShopHandler.displayUI(us, type, 5486, 5487);
//                                }).build(),
                                Menu.builder().name("Thiên thần hộ mệnh toàn năng").action(() -> {
                                    BossShopHandler.displayUI(us, type, 5224, 5225, 5226);
                                }).build(),
                                Menu.builder().name("Cáo tuyết cửu vỹ").action(() -> {
                                    BossShopHandler.displayUI(us, type, 4904, 4905);
                                }).build(),
                                Menu.builder().name("Cửu vỹ hồ ly").action(() -> {
                                    BossShopHandler.displayUI(us, type, 4724, 4728, 4729);
                                }).build(),
//                                Menu.builder().name("Tiểu tiên bướm").action(() -> {
//                                    BossShopHandler.displayUI(us, type, 4305, 5058);
//                                }).build(),
//                                Menu.builder().name("Ma vương").action(() -> {
//                                    BossShopHandler.displayUI(us, type, 4096, 4731);
//                                }).build(),
//                                Menu.builder().name("Lợn lém lỉnh").action(() -> {
//                                    BossShopHandler.displayUI(us, type, 4376);
//                                }).build(),
//                                Menu.builder().name("Tuần lộc tinh anh").action(() -> {
//                                    BossShopHandler.displayUI(us, type, 4323, 4324);
//                                }).build(),
                                Menu.builder().name("Bay nax 2.0").action(() -> {
                                    BossShopHandler.displayUI(us, type, 4079, 4080);
                                }).build(),
                                Menu.builder().name("Phương hoàng lửa").action(() -> {
                                    BossShopHandler.displayUI(us, type, 3668, 3771, 3772, 3773, 3854);
                                }).build(),
                                Menu.builder().name("King Kong").action(() -> {
                                    BossShopHandler.displayUI(us, type, 3744);
                                }).build(),
                                Menu.builder().name("Kỳ lân truyền thuyết").action(() -> {
                                    BossShopHandler.displayUI(us, type, 2726, 2727, 2728, 2729, 2730);
                                }).build()
                        ))
                        .build(),
                Menu.builder().name("Tóc")
                        .menus(List.of(
                                Menu.builder().name("Tóc Siêu Xaya").action(() -> {
                                    BossShopHandler.displayUI(us, type, 2019);
                                }).build()
                        ))
                        .build()

        );
    }

    public static void handlerAction(User us, int npcId, byte menuId, byte select) throws IOException {
        Zone z = us.getZone();
        if (z != null) {
            User u = z.find(npcId);
            if (u == null) {
                return;
            }
        } else {
            return;
        }
//        if (menuId == 0 && select == 0) {
//            // Trường hợp đặc biệt khi lần đầu mở menu
//            System.out.println("Initial menu open, displaying options without performing action.");
//            us.getAvatarService().openMenuOption(npcId, menuId,us.getMenus());
//            return;
//        }
//        int npcIdCase = npcId - 2000000000;
        List<Menu> menus = us.getMenus();
        if (menus != null && select < menus.size()) {
            Menu menu = menus.get(select);
            if (menu.isMenu()) {
                us.setMenus(menu.getMenus());
                us.getAvatarService().openUIMenu(npcId, menuId + 1, menu.getMenus(), menu.getNpcName(), menu.getNpcChat());
            } else if (menu.getAction() != null) {
                menu.perform();
            } else {
                switch (menu.getId()) {

                }
            }
            return;
        }
    }
}
