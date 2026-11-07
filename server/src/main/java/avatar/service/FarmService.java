package avatar.service;

import avatar.Farm.*;
import avatar.db.DbManager;
import avatar.item.PartManager;
import avatar.lib.KeyValue;
import avatar.model.GameData;
import avatar.model.ImageInfo;
import avatar.constants.Cmd;
import avatar.model.User;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Vector;
import avatar.server.Avatar;
import java.io.IOException;
import java.io.DataOutputStream;
import avatar.network.Message;
import avatar.network.Session;
import java.util.List;
import org.apache.log4j.Logger;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.JSONValue;

import java.sql.*;

public class FarmService extends Service {

    private static final Logger logger = Logger.getLogger(Service.class);

    public FarmService(Session cl) {
        super(cl);
    }


    // [PORT] Bản fan là stub (chỉ mở dialog "id"). Client gốc đọc gói 63 trả về:
    // sellMoney (int) · curMoney (int, xu sau bán) · idItem (short) -> onSell.
    // Giá bán lấy từ bảng sản phẩm của farm_info.dat (FarmProductData): nông sản cây = 3 xu,
    // quả khế = 10, món nấu = giá niêm yết dat... Bán TOÀN BỘ số đang có của nông sản đó
    // (gói xin chỉ mang id, không mang số lượng). Tìm trong kho nông sản trước, rồi kho đặc biệt.
    // Nông sản vật nuôi (trứng/sữa/lông, id 50-59) dat không niêm yết ở bảng sản phẩm ->
    // để giai đoạn vật nuôi (GĐ4) chốt theo priceProduct của AnimalInfo.
    public void sellFarmitm(User user, Message ms) throws IOException {
        short idFarmItm = ms.reader().readShort();
        int price = FarmProductData.sellPriceOf(idFarmItm);
        // [PORT Farm cấp 2] hạng Sản phẩm: giá bán nông sản +10%
        if (price > 0 && Farm2Service.upOf(user.getId(), Farm2Service.UP_PRODUCT)) {
            price = price * 110 / 100;
        }
        if (price < 0) {
            user.getAvatarService().serverDialog("Chưa bán được nông sản này.");
            return;
        }
        NongSan target = null;
        for (NongSan ns : this.session.user.NongSan) {
            if (ns.getId() == idFarmItm) {
                target = ns;
                break;
            }
        }
        if (target != null && target.getSoluong() > 0) {
            int total = price * target.getSoluong();
            this.session.user.NongSan.remove(target);
            this.session.user.updateXu(total);
            sendSellResult(total, idFarmItm);
            return;
        }
        NongSanDacBiet special = null;
        for (NongSanDacBiet db : this.session.user.NongSanDacBiet) {
            if (db.getId() == idFarmItm) {
                special = db;
                break;
            }
        }
        if (special != null && special.getSoluong() > 0) {
            int total = price * special.getSoluong();
            this.session.user.NongSanDacBiet.remove(special);
            this.session.user.updateXu(total);
            sendSellResult(total, idFarmItm);
            return;
        }
        user.getAvatarService().serverDialog("Không có nông sản này trong kho.");
    }

    private void sendSellResult(int total, short idFarmItm) throws IOException {
        Message ms = new Message(Cmd.GET_CARD);
        DataOutputStream ds = ms.writer();
        ds.writeInt(total);
        ds.writeInt((int) this.session.user.getXu());
        ds.writeShort(idFarmItm);
        ds.flush();
        this.session.sendMessage(ms);
    }


    //trồng cây
// Phương thức trồng cây
    public void plandSeed(Message ms) throws IOException, SQLException {
        int idUser = ms.reader().readInt();
        int indexCell = ms.reader().readByte();
        int idSeed = ms.reader().readByte();

        HatGiong hd = this.session.user.findhatgiong(idSeed);
        // Kiểm tra ô đất có tồn tại trong danh sách không
        // [PORT] Bản fan không đặt lại plantedTime (phút đã trồng tính từ lần trồng TRƯỚC) và trồng đè lên ô đang có cây.
        if (indexCell >= 0 && indexCell < this.session.user.landItems.size() && hd != null && hd.getSoluong() > 0
                && this.session.user.landItems.get(indexCell).getType() < 0) {
            // Lấy ô đất tại vị trí indexCell và cập nhật thông tin
            LandItem landItem = this.session.user.landItems.get(indexCell);
            landItem.setType(idSeed); // Đặt mã cây mới
            hd.setSoluong(hd.getSoluong()-1);
            landItem.setSucKhoe(100);
            landItem.setGrowthTime(0); // Reset thời gian tăng trưởng, vì cây mới vừa trồng
            landItem.setResourceCount(0); // Đặt số lượng tài nguyên về 0, vì cây mới chưa có tài nguyên
            landItem.setWatered(false); // Đặt trạng thái tưới nước ban đầu
            landItem.setFertilized(false); // Đặt trạng thái bón phân ban đầu
            landItem.setHarvestable(false); // Đặt trạng thái chưa thể thu hoạch
            landItem.setPlantedTime(LocalDateTime.now());
        } else {
            // Nếu ô đất không tồn tại, có thể xử lý ngoại lệ hoặc thông báo lỗi
            System.out.println("Ô đất không tồn tại hoặc indexCell không hợp lệ.");
            return;
        }

        // Tạo Message và gửi thông tin trồng cây
        ms = new Message(Cmd.PLANT_SEED);
        DataOutputStream ds = ms.writer();
        ds.writeInt(idUser);
        ds.writeByte(indexCell);
        ds.writeByte(idSeed);
        ds.flush();
        this.session.sendMessage(ms);
    }


    //thu hoạch
    // [PORT] Bản fan: công thức sản lượng (quantity % 100) * sucKhoe / 100 (cây sản lượng 720 chỉ thu 20),
    // không cộng nông sản vào kho, không kiểm tra cây đã chín. Sửa cả ba theo dữ kiện gốc:
    // sản lượng = quantity * sucKhoe / 100 (farmitems), nông sản vào kho với id = id cây.
    public void treeHarvest(Message ms) throws IOException {
        int idfarm = ms.reader().readInt();//idUser
        byte indexcell = ms.reader().readByte();
        if (indexcell < 0 || indexcell >= this.session.user.landItems.size()) {
            System.out.println("Ô đất không tồn tại hoặc indexCell không hợp lệ.");
            return;
        }
        LandItem landItem = this.session.user.landItems.get(indexcell);
        if (landItem.getType() < 0) {
            return;
        }
        farmItem itemf = PartManager.getInstance().findFarmitemByID(landItem.getType());
        if (itemf == null) {
            return;
        }
        if (landItem.getMinutesSincePlanted() < itemf.getTime()) {
            this.session.user.getAvatarService().serverDialog("Cây chưa chín, chưa thu hoạch được.");
            return;
        }
        int sanluong = itemf.getQuantity() * landItem.getSucKhoe() / 100;
        int treeId = landItem.getType();
        // [PORT] Cây động (id >= 30) thu ra sản phẩm có id riêng theo dat (Bắp 30 -> sản phẩm 200...).
        int productId = FarmProductData.productIdOf(treeId);
        landItem.setType(-1);
        landItem.setWatered(false);
        landItem.setFertilized(false);
        landItem.setHarvestable(false);
        landItem.setResourceCount(0);
        sanluong = Farm2Service.roomFor(this.session.user, sanluong);
        if (sanluong > 0) {
            NongSan existing = null;
            for (NongSan ns : this.session.user.NongSan) {
                if (ns.getId() == productId) {
                    existing = ns;
                    break;
                }
            }
            if (existing != null) {
                existing.setSoluong(existing.getSoluong() + sanluong);
            } else {
                this.session.user.NongSan.add(new NongSan(productId, sanluong));
            }
            TitleService.bump(this.session.user.getId(), "harvest");
        }
        ms = new Message(Cmd.TREE_HARVEST);
        DataOutputStream ds = ms.writer();
        ds.writeByte(indexcell);
        ds.writeShort(sanluong);//so luong thu hoach duoc
        ds.flush();
        this.session.sendMessage(ms);
    }
    // [PORT] Giá mở ô đất KHÔNG có ở bất cứ đâu trong Pack5 (bản fan để nguyên
    // mẫu chuỗi có dấu @). Bảng giá dưới đây là TẠM — chỉ server live mới có số
    // thật; khi dò được (gói 69 trên server live) thì thay đúng 2 hàm này
    // (client FarmWorld có bảng gương giống hệt cho hộp xác nhận).
    public static final int MAX_CELLS = 48; // 4 khối đất x 12 ô trên map farm

    public static int landPriceXu(int ownedCells) {
        return (ownedCells - 5) * 1000;
    }

    public static int landPriceLuong(int ownedCells) {
        return Math.max(1, (ownedCells - 5) / 2);
    }

    private static String fmtMoney(int n) {
        return String.format("%,d", n).replace(',', '.');
    }

   // mở ô đất — xin giá (gói 69): client gốc hiện chuỗi này trong hộp thoại
   // kèm 2 nút Xu / Lượng (FarmScr.onPricePlant).
    public void doRequestslot(Message ms) throws IOException {
        int id = ms.reader().readInt();//id user
        int owned = this.session.user.landItems.size();
        ms = new Message(Cmd.REQUEST_SLOT);
        DataOutputStream ds = ms.writer();
        if (owned >= MAX_CELLS) {
            ds.writeUTF("Bạn đã mở hết ô đất rồi.");
        } else {
            ds.writeUTF("Bạn có muốn mở ô đất với giá " + fmtMoney(landPriceXu(owned))
                    + " xu hoặc " + landPriceLuong(owned) + " lượng không ?");
        }
        ds.flush();
        this.session.sendMessage(ms);
    }

//mở ô đất (gói 70): {int idFarm, byte typeBuy 1=xu 2=lượng} -> trừ tiền thật,
//trả đúng khuôn onOpenLand của client gốc {int idFarm, int curMoney, byte typeBuy,
//UTF text, int xu, int luong, int luongKhoa}. Bản fan cộng ô MIỄN PHÍ + curMoney=1.
    public void openLand(Message ms) throws IOException {
        int id = ms.reader().readInt(); // ID của nông trại
        byte typeBuy = ms.reader().readByte(); // 1 = xu, 2 = lượng
        int owned = this.session.user.landItems.size();
        if (owned >= MAX_CELLS) {
            this.session.user.getAvatarService().serverDialog("Bạn đã mở hết ô đất rồi.");
            return;
        }
        boolean byXu = typeBuy == 1;
        int price = byXu ? landPriceXu(owned) : landPriceLuong(owned);
        long before = byXu ? this.session.user.getXu() : this.session.user.getLuong();
        if (before < price) {
            this.session.user.getAvatarService().serverDialog(
                    byXu ? "Bạn không đủ xu để mở ô đất." : "Bạn không đủ lượng để mở ô đất.");
            return;
        }
        if (byXu) {
            this.session.user.updateXu(-price);
        } else {
            this.session.user.updateLuong(-price);
        }
        this.session.user.landItems.add(new LandItem(0, -1, 100, 0, false, false, false, LocalDateTime.now()));

        ms = new Message(Cmd.OPEN_LAND);
        DataOutputStream ds = ms.writer();
        ds.writeInt(id);
        ds.writeInt((int) (byXu ? this.session.user.getXu() : this.session.user.getLuong()));
        ds.writeByte(typeBuy);
        ds.writeUTF("Đã mở ô đất thành công");
        ds.writeInt((int) this.session.user.getXu());
        ds.writeInt((int) this.session.user.getLuong());
        ds.writeInt((int) this.session.user.getLuongKhoa());
        ds.flush();
        this.session.sendMessage(ms);
    }

    // [PORT] Gói 65 USING_ITEM — bản fan KHÔNG có case này trong handler (method
    // này là viết mới theo đúng client gốc): xin {int idUser, byte ô, short idItem}.
    // Item 100 = tưới nước; 111/112 = phân bón (tăng sức khoẻ); 113/114/115 = giảm
    // 15/30/60 phút chín; 123/124 = diệt cỏ/trừ sâu (farm port không phát sinh 2
    // trạng thái này). Trả gói 65 {byte ô, short idItem} cho client gốc trừ kho
    // listItemFarm — đúng case 65 phía client.
    public void useItem(Message ms) throws IOException {
        ms.reader().readInt(); // idUser (farm của chính mình)
        int idx = ms.reader().readByte();
        int idItem = ms.reader().readShort();
        // [PORT] Thức ăn vật nuôi (116-119, action 5 của dat): byte thứ hai là
        // CHỈ SỐ VẬT NUÔI thay cho chỉ số ô. Khu vực khớp: 116 -> hồ cá (4),
        // 117 -> gia cầm (1), 118 -> gia súc (2), 119 -> chó (3). Bản fan không
        // có nhánh này; client gốc cũng không để lại dấu vết giao thức cho ăn
        // nên bản port quy ước vậy và ghi rõ ở đây.
        if (idItem >= 116 && idItem <= 119) {
            if (idx < 0 || idx >= this.session.user.Animal.size()) {
                return;
            }
            Animal pet = this.session.user.Animal.get(idx);
            int needArea = idItem == 116 ? 4 : idItem == 117 ? 1 : idItem == 118 ? 2 : 3;
            int petArea = pet.getId() == 50 || pet.getId() == 56 ? 1
                    : pet.getId() == 53 ? 3
                    : pet.getId() == 54 || pet.getId() == 59 ? 4 : 2;
            if (petArea != needArea) {
                return;
            }
            PhanBon food = null;
            for (PhanBon p : this.session.user.PhanBon) {
                if (p.getId() == idItem && p.getSoluong() > 0) {
                    food = p;
                    break;
                }
            }
            if (food == null) {
                return;
            }
            food.setSoluong(food.getSoluong() - 1);
            if (food.getSoluong() <= 0) {
                this.session.user.PhanBon.remove(food);
            }
            pet.setLastFeed(System.currentTimeMillis() / 1000L);
            pet.setHealth(100);
            sendUseItemEcho(idx, idItem);
            return;
        }
        if (idx < 0 || idx >= this.session.user.landItems.size()) {
            return;
        }
        LandItem cell = this.session.user.landItems.get(idx);
        if (idItem == 100) {
            if (cell.getType() >= 0) {
                cell.setWatered(true);
            }
            sendUseItemEcho(idx, idItem);
            return;
        }
        PhanBon pb = null;
        for (PhanBon p : this.session.user.PhanBon) {
            if (p.getId() == idItem && p.getSoluong() > 0) {
                pb = p;
                break;
            }
        }
        if (pb == null || cell.getType() < 0) {
            return;
        }
        switch (idItem) {
            case 111: // phân bón trung cấp: +10 sức khoẻ
                cell.setFertilized(true);
                cell.setSucKhoe(Math.min(100, cell.getSucKhoe() + 10));
                break;
            case 112: // phân bón siêu cấp: sức khoẻ đầy
                cell.setFertilized(true);
                cell.setSucKhoe(100);
                break;
            case 113:
                cell.setPlantedTime(cell.getPlantedTime().minusMinutes(15));
                break;
            case 114:
                cell.setPlantedTime(cell.getPlantedTime().minusMinutes(30));
                break;
            case 115:
                cell.setPlantedTime(cell.getPlantedTime().minusMinutes(60));
                break;
            case 123: // thuốc diệt cỏ
            case 124: // thuốc trừ sâu — không trạng thái để chữa trong bản port
                break;
            default:
                return; // không phải vật phẩm dùng trên ô đất — không trừ kho
        }
        pb.setSoluong(pb.getSoluong() - 1);
        if (pb.getSoluong() <= 0) {
            this.session.user.PhanBon.remove(pb);
        }
        sendUseItemEcho(idx, idItem);
    }

    private void sendUseItemEcho(int idx, int idItem) throws IOException {
        Message ms = new Message(Cmd.USING_ITEM);
        DataOutputStream ds = ms.writer();
        ds.writeByte(idx);
        ds.writeShort(idItem);
        ds.flush();
        this.session.sendMessage(ms);
    }

    // ---- [PORT] Vật nuôi: giá bán (72) / bán (73) / thu hoạch (74) ----
    // Bản fan không hề có 3 handler này (chỉ có hằng số Cmd). Khuôn gói lấy từ
    // client gốc: 72 C->S {int idFarm, byte index} -> S->C {byte index, UTF text};
    // 73 C->S {int idFarm, byte index} -> S->C {int idFarm, byte index, int xu};
    // 74 C->S {int idFarm, byte index} -> S->C {byte index, short number}.

    private static String animalName(int species) {
        switch (species) {
            case 50: return "Gà";
            case 51: return "Bò";
            case 52: return "Heo";
            case 53: return "Chó";
            case 54: return "Cá chép";
            case 55: return "Cừu";
            case 56: return "Vịt";
            case 58: return "Trâu";
            case 59: return "Rùa";
            default: return "Vật nuôi";
        }
    }

    public void priceAnimal(Message ms) throws IOException {
        ms.reader().readInt(); // idFarm
        int idx = ms.reader().readByte();
        if (idx < 0 || idx >= this.session.user.Animal.size()) {
            return;
        }
        Animal pet = this.session.user.Animal.get(idx);
        int[] e = econOf(pet);
        int sellback = e == null ? 0 : e[2];
        String text = sellback > 0
                ? "Bạn có muốn bán " + animalName(pet.getId()) + " với giá "
                    + String.format("%,d", sellback).replace(',', '.') + " xu không?"
                : animalName(pet.getId()) + " là chó giữ nhà, không bán được.";
        ms = new Message(Cmd.PRICE_ANIMAL);
        DataOutputStream ds = ms.writer();
        ds.writeByte(idx);
        ds.writeUTF(text);
        ds.flush();
        this.session.sendMessage(ms);
    }

    public void sellAnimal(Message ms) throws IOException {
        int idFarm = ms.reader().readInt();
        int idx = ms.reader().readByte();
        if (idx < 0 || idx >= this.session.user.Animal.size()) {
            return;
        }
        Animal pet = this.session.user.Animal.get(idx);
        int[] e = econOf(pet);
        if (e == null || e[2] <= 0) {
            this.session.user.getAvatarService().serverDialog("Vật nuôi này không bán được");
            return;
        }
        this.session.user.Animal.remove(idx);
        this.session.user.updateXu(e[2]);
        ms = new Message(Cmd.SELL_ANIMAL);
        DataOutputStream ds = ms.writer();
        ds.writeInt(idFarm);
        ds.writeByte(idx);
        ds.writeInt((int) this.session.user.getXu());
        ds.flush();
        this.session.sendMessage(ms);
    }

    public void harvestAnimal(Message ms) throws IOException {
        ms.reader().readInt(); // idFarm
        int idx = ms.reader().readByte();
        int n = 0;
        if (idx >= 0 && idx < this.session.user.Animal.size()) {
            Animal pet = this.session.user.Animal.get(idx);
            int[] e = econOf(pet);
            if (e != null && e[0] > 0) {
                long from = pet.getLastHarvest() > 0 ? pet.getLastHarvest() : pet.getBornTime();
                long cycles = (System.currentTimeMillis() / 1000L - from) / (e[0] * 60L);
                if (cycles > 0) {
                    n = producible(pet);
                    n = Farm2Service.roomFor(this.session.user, n);
                    if (n > 0) {
                        // sản phẩm vật nuôi mang id = species (client gốc addProductAnimal)
                        avatar.Farm.NongSan exist = null;
                        for (avatar.Farm.NongSan p : this.session.user.NongSan) {
                            if (p.getId() == pet.getId()) {
                                exist = p;
                                break;
                            }
                        }
                        if (exist != null) {
                            exist.setSoluong(exist.getSoluong() + n);
                        } else {
                            this.session.user.NongSan.add(new avatar.Farm.NongSan(pet.getId(), n));
                        }
                        // Đói chỉ giảm phần thu, chu kỳ vẫn chốt đủ số chu kỳ đã trôi.
                        pet.setLastHarvest(from + cycles * e[0] * 60L);
                    }
                }
            }
        }
        ms = new Message(Cmd.HARVEST_ANIMAL);
        DataOutputStream ds = ms.writer();
        ds.writeByte(idx);
        ds.writeShort(Math.min(32000, n));
        ds.flush();
        this.session.sendMessage(ms);
    }

    /** Số quả khế hiện có (áp mốc ra quả lại nếu đã tới). */
    public int currentStarNum() {
        avatar.model.User u = this.session.user;
        if (u.starNum < 0) return 180; // chưa nạp dữ liệu: đầy cây như gốc
        if (u.starNum <= 0 && u.starRegrow > 0
                && System.currentTimeMillis() / 1000L >= u.starRegrow) {
            u.starNum = 180;
            u.starRegrow = 0;
        }
        return u.starNum;
    }

    // ---- [PORT] Thu hoạch khế (gói 85) ----
    // Client gốc đọc S->C 85: {short productID, short numberPro}. Server
    // gốc và bản fan đều không có handler (chỉ có hằng số). Quy ước port:
    // thu hết quả đang có -> sản phẩm 215 vào kho nông sản; cây ra quả lại
    // (180 quả) sau 24 giờ (star_regrow), trạng thái lưu ở farm_data.
    public void harvestStarFruit(Message ms) throws IOException {
        avatar.model.User u = this.session.user;
        int n = currentStarNum();
        n = Farm2Service.roomFor(u, n);
        if (n > 0) {
            avatar.Farm.NongSan exist = null;
            for (avatar.Farm.NongSan p : u.NongSan) {
                if (p.getId() == 215) {
                    exist = p;
                    break;
                }
            }
            if (exist != null) {
                exist.setSoluong(exist.getSoluong() + n);
            } else {
                u.NongSan.add(new avatar.Farm.NongSan(215, n));
            }
            u.starNum = 0;
            u.starRegrow = System.currentTimeMillis() / 1000L + 24L * 3600L;
        }
        Message out = new Message(Cmd.HARVEST_STARFRUIT);
        DataOutputStream ds = out.writer();
        ds.writeShort(215);
        ds.writeShort(Math.min(32000, n));
        ds.flush();
        this.session.sendMessage(out);
    }

    // ---- [PORT] Nấu ăn (91 bắt đầu / 92 nhận món) ----
    // Bản fan để trống cả hai. Công thức từ FarmFoodData (sinh từ dat):
    // nguyên liệu trừ trong kho nông sản (thường + đặc biệt), món chín sau
    // cookTime phút, nhận bằng gói 92 -> sản phẩm vào kho (+1).

    public void doCooking(Message ms) throws IOException {
        short foodId = ms.reader().readShort();
        avatar.Farm.FarmFoodData.Food food = avatar.Farm.FarmFoodData.FOODS.get((int) foodId);
        if (food == null) {
            return;
        }
        if (this.session.user.foodId > 0) {
            this.session.user.getAvatarService().serverDialog("Đang nấu món khác, hãy chờ món đó chín đã");
            return;
        }
        // Kiểm đủ nguyên liệu trước khi trừ (gộp cả kho thường + đặc biệt).
        for (int[] mat : food.materials) {
            int have = 0;
            for (avatar.Farm.NongSan p : this.session.user.NongSan) {
                if (p.getId() == mat[0]) have += p.getSoluong();
            }
            for (avatar.Farm.NongSanDacBiet p : this.session.user.NongSanDacBiet) {
                if (p.getId() == mat[0]) have += p.getSoluong();
            }
            if (have < mat[1]) {
                this.session.user.getAvatarService().serverDialog("Không đủ nguyên liệu để nấu món này");
                return;
            }
        }
        for (int[] mat : food.materials) {
            int need = mat[1];
            for (java.util.Iterator<avatar.Farm.NongSan> it = this.session.user.NongSan.iterator(); it.hasNext() && need > 0;) {
                avatar.Farm.NongSan p = it.next();
                if (p.getId() != mat[0]) continue;
                int take = Math.min(need, p.getSoluong());
                p.setSoluong(p.getSoluong() - take);
                need -= take;
                if (p.getSoluong() <= 0) it.remove();
            }
            for (java.util.Iterator<avatar.Farm.NongSanDacBiet> it = this.session.user.NongSanDacBiet.iterator(); it.hasNext() && need > 0;) {
                avatar.Farm.NongSanDacBiet p = it.next();
                if (p.getId() != mat[0]) continue;
                int take = Math.min(need, p.getSoluong());
                p.setSoluong(p.getSoluong() - take);
                need -= take;
                if (p.getSoluong() <= 0) it.remove();
            }
        }
        this.session.user.foodId = foodId;
        // [PORT Farm cấp 2] hạng Bếp: thời gian nấu giảm một nửa
        int cookMins = Farm2Service.upOf(this.session.user.getId(), Farm2Service.UP_COOKING)
                ? Math.max(1, food.cookMinutes / 2) : food.cookMinutes;
        this.session.user.cookFinish = System.currentTimeMillis() / 1000L + cookMins * 60L;
        ms = new Message(Cmd.COOKING);
        DataOutputStream ds = ms.writer();
        ds.writeShort(foodId);
        ds.writeShort(cookMins);
        ds.flush();
        this.session.sendMessage(ms);
    }

    public void harvestCook(Message ms) throws IOException {
        if (this.session.user.foodId <= 0) {
            return;
        }
        if (System.currentTimeMillis() / 1000L < this.session.user.cookFinish) {
            return; // chưa chín — client gốc cũng chỉ chờ, không có phản hồi
        }
        avatar.Farm.FarmFoodData.Food food = avatar.Farm.FarmFoodData.FOODS.get(this.session.user.foodId);
        this.session.user.foodId = 0;
        this.session.user.cookFinish = 0;
        if (food != null) {
            avatar.Farm.NongSan exist = null;
            for (avatar.Farm.NongSan p : this.session.user.NongSan) {
                if (p.getId() == food.productId) {
                    exist = p;
                    break;
                }
            }
            if (exist != null) {
                exist.setSoluong(exist.getSoluong() + 1);
            } else {
                this.session.user.NongSan.add(new avatar.Farm.NongSan(food.productId, 1));
            }
            TitleService.bump(this.session.user.getId(), "cook");
        }
        ms = new Message(Cmd.HARVEST_COOK);
        ms.writer().flush();
        this.session.sendMessage(ms);
    }

    // ---- Nấu nhanh (gói 93) theo bản chính: FarmScr case 11/22 ----
    // C->S {byte step}: step 0 = hỏi giá, step 1 = xác nhận trả.
    // S->C step 0: {byte 0, UTF info} (client hiện hộp OK, OK gửi
    // tiếp step 1); S->C step 1: {byte 1, int giá, int lượng mới,
    // int lượng khoá mới} và món chín ngay (remainTime = 0).
    // Giá [PORT]: 1 lượng mỗi 5 phút còn lại, tối thiểu 1 — server
    // gốc không còn để tra giá.
    public void nauNhanh(Message ms) throws IOException {
        byte step = ms.reader().readByte();
        User u = this.session.user;
        if (u.foodId <= 0) {
            return;
        }
        avatar.Farm.FarmFoodData.Food food = avatar.Farm.FarmFoodData.FOODS.get(u.foodId);
        if (food == null) {
            return;
        }
        long nowSec = System.currentTimeMillis() / 1000L;
        long remain = u.cookFinish - nowSec;
        Message out = new Message(Cmd.NAU_NHANH);
        DataOutputStream ds = out.writer();
        if (remain <= 0) {
            ds.writeByte(1);
            ds.writeInt(0);
            ds.writeInt(u.getLuong());
            ds.writeInt(u.luongKhoa);
            ds.flush();
            this.session.sendMessage(out);
            return;
        }
        int price = (int) Math.max(1, (remain + 299) / 300);
        if (step == 0) {
            // Tên món: sản phẩm farm không có tên ở bảng items nên
            // chỉ ghép tên khi tra được, tránh câu cụt/duplicated
            String fname = null;
            try {
                avatar.item.Part p = avatar.item.PartManager.getInstance()
                    .findPartById(food.productId);
                if (p != null && p.getName() != null && !p.getName().isEmpty()) {
                    fname = p.getName();
                }
            } catch (Exception ignored) {
            }
            ds.writeByte(0);
            ds.writeUTF(fname != null
                ? "Nấu xong ngay món " + fname + " với giá " + price + " lượng?"
                : "Nấu xong ngay với giá " + price + " lượng?");
            ds.flush();
            this.session.sendMessage(out);
            return;
        }
        if (u.getLuong() + u.luongKhoa < price) {
            u.getAvatarService().serverDialog("Không đủ lượng để nấu nhanh.");
            return;
        }
        u.updateLuong(-price);
        u.cookFinish = nowSec;
        ds.writeByte(1);
        ds.writeInt(price);
        ds.writeInt(u.getLuong());
        ds.writeInt(u.luongKhoa);
        ds.flush();
        this.session.sendMessage(out);
    }


    public void setBigFarm(Message ms) throws IOException {


        ms = new Message(51);
        DataOutputStream ds = ms.writer();
        int[] images = {99, 206};
        ds.writeByte(images.length);
        for (int i = 0; i < images.length; ++i) {
            ds.writeShort(i);
            ds.writeShort(images[i]);
        }
        ds.writeInt(15378);
        ds.writeInt(62724);
        ds.flush();
        this.session.sendMessage(ms);


        //apk goc
//        ms = new Message(51);
//        DataOutputStream ds = ms.writer();
//        short b19 = 2;
//        ds.writeByte(b19);
//        short[] array2 = {0, 1};
//        for (short value : array2) {
//            ds.writeShort(value);
//        }
//        short[] array3 = {66, 6};
//        for (short value : array3) {
//            ds.writeShort(value);
//        }
//        ds.writeInt(15378);
//        ds.writeInt(59669);
//        ds.flush();
//        this.session.sendMessage(ms);
    }

    // [PORT] Giá vật phẩm farm (phân bón/thức ăn/thuốc, id 111-124) theo
    // farm_info.dat: {priceXu, priceLuong}; -1/0 = không bán bằng loại tiền đó.
    private static final java.util.Map<Integer, int[]> FARM_ITEM_PRICES = new java.util.HashMap<>();
    static {
        FARM_ITEM_PRICES.put(111, new int[]{7, -1});
        FARM_ITEM_PRICES.put(112, new int[]{10, -1});
        FARM_ITEM_PRICES.put(113, new int[]{20, -1});
        FARM_ITEM_PRICES.put(114, new int[]{30, -1});
        FARM_ITEM_PRICES.put(115, new int[]{-1, 1});
        FARM_ITEM_PRICES.put(116, new int[]{1, -1});
        FARM_ITEM_PRICES.put(117, new int[]{1, -1});
        FARM_ITEM_PRICES.put(118, new int[]{1, -1});
        FARM_ITEM_PRICES.put(119, new int[]{1, -1});
        FARM_ITEM_PRICES.put(120, new int[]{10, -1});
        FARM_ITEM_PRICES.put(121, new int[]{10, -1});
        FARM_ITEM_PRICES.put(122, new int[]{25, -1});
        FARM_ITEM_PRICES.put(123, new int[]{5, -1});
        FARM_ITEM_PRICES.put(124, new int[]{5, -1});
    }

    public void Buy_item_farm(Message ms) throws IOException {
        short id = ms.reader().readShort();
        byte n = ms.reader().readByte();
        byte type = ms.reader().readByte();
        System.out.println("item farm id "+id+" sl "+n+" type sell"+type);

        // [PORT] Vật phẩm farm (id >= 100): mua vào kho PhanBon, cùng khuôn gói
        // 62 như mua hạt. Bản fan chỉ tra bảng cây nên mua phân bón luôn báo lỗi.
        if (id >= 100) {
            int[] prices = FARM_ITEM_PRICES.get((int) id);
            if (prices == null) {
                this.session.user.getAvatarService().serverDialog("Không có vật phẩm này");
                return;
            }
            boolean byXuF = type == 1;
            int priceF = byXuF ? prices[0] : prices[1];
            if (priceF <= 0) {
                this.session.user.getAvatarService().serverDialog(byXuF
                        ? "Vật phẩm này không bán bằng xu" : "Vật phẩm này không bán bằng lượng");
                return;
            }
            long beforeF = byXuF ? this.session.user.getXu() : this.session.user.getLuong();
            if (beforeF < (long) priceF * n) {
                this.session.user.getAvatarService().serverDialog(byXuF ? "bạn không đủ xu" : "bạn không đủ lượng");
                return;
            }
            PhanBon exist = null;
            for (PhanBon p : this.session.user.PhanBon) {
                if (p.getId() == id) { exist = p; break; }
            }
            if (exist != null && exist.getSoluong() + n > 99) {
                this.session.user.getAvatarService().serverDialog("số lượng nhiều rồi");
                return;
            }
            if (byXuF) {
                this.session.user.updateXu(-priceF * n);
            } else {
                this.session.user.updateLuong(-priceF * n);
            }
            if (exist != null) {
                exist.setSoluong(exist.getSoluong() + n);
            } else {
                this.session.user.PhanBon.add(new PhanBon(id, n));
            }
            ms = new Message(62);
            DataOutputStream ds = ms.writer();
            ds.writeShort(id);
            ds.writeByte(n);
            ds.writeInt((int) beforeF);
            ds.writeByte(type);
            ds.writeInt((int) this.session.user.getXu());
            ds.writeInt((int) this.session.user.getLuong());
            ds.writeInt(0);
            ds.flush();
            this.session.sendMessage(ms);
            return;
        }

        farmItem itemf = PartManager.getInstance().findFarmitemByID(id);
        if (itemf == null) {
            this.session.user.getAvatarService().serverDialog("Không có hạt giống này");
            return;
        }

        // [PORT] Bản fan luôn trừ xu ở gói trả về kể cả khi mua bằng lượng, và cộng dồn hạt bằng
        // cách thêm dòng mới trùng id (kho hiện 2 dòng cùng hạt). Sửa: newMoney = số dư TRƯỚC
        // của đúng loại tiền đã dùng; hạt cộng vào dòng có sẵn.
        // Giá hạt theo farm_info.dat: mỗi cây có giá xu và giá lượng riêng (0 = không bán
        // bằng loại tiền đó — vd Nha Đam chỉ bán 1 lượng, không bán bằng xu).
        boolean byXu = type == 1;
        int priceSeed = byXu ? itemf.getSell() : itemf.getSellLuong();
        if (priceSeed <= 0) {
            this.session.user.getAvatarService().serverDialog(byXu
                    ? "Hạt này không bán bằng xu" : "Hạt này không bán bằng lượng");
            return;
        }
        long before = byXu ? this.session.user.getXu() : this.session.user.getLuong();
        if (before < (long) priceSeed * n) {
            this.session.user.getAvatarService().serverDialog(byXu ? "bạn không đủ xu" : "bạn không đủ lượng");
            return;
        }

        HatGiong hdid = this.session.user.findhatgiong(id);
        if (hdid != null && (hdid.getSoluong() + n) > 99) {
            this.session.user.getAvatarService().serverDialog("số lượng nhiều rồi");
            return;
        }

        if (byXu) {
            this.session.user.updateXu(-priceSeed * n);
        } else {
            this.session.user.updateLuong(-priceSeed * n);
        }
        if (hdid != null) {
            hdid.setSoluong(hdid.getSoluong() + n);
        } else {
            this.session.user.hatgiong.add(new HatGiong(id, n));
        }

        ms = new Message(62);
        DataOutputStream ds = ms.writer();
        ds.writeShort(id);
        ds.writeByte(n);
        ds.writeInt((int) before);//newMoney
        ds.writeByte(type);
        ds.writeInt((int) this.session.user.getXu());
        ds.writeInt(this.session.user.getLuong());//luong
        ds.writeInt(0);//luongK
        ds.flush();
        this.session.sendMessage(ms);
    }

    // [PORT] Bảng giá vật nuôi theo farm_info.dat (giá -1/0 = không bán bằng loại tiền đó).
    // species -> {priceXu, priceLuong}: 50 Gà, 51 Bò, 52 Heo, 53 Chó, 54 Cá chép, 55 Cừu, 56 Vịt, 58 Trâu, 59 Rùa.
    private static final java.util.Map<Integer, int[]> ANIMAL_PRICES = new java.util.HashMap<>();
    static {
        ANIMAL_PRICES.put(50, new int[]{500, -1});
        ANIMAL_PRICES.put(51, new int[]{-1, 5});
        ANIMAL_PRICES.put(52, new int[]{1000, -1});
        ANIMAL_PRICES.put(53, new int[]{-1, 1});
        ANIMAL_PRICES.put(54, new int[]{5000, -1});
        ANIMAL_PRICES.put(55, new int[]{-1, 2});
        ANIMAL_PRICES.put(56, new int[]{600, -1});
        ANIMAL_PRICES.put(58, new int[]{1000, -1});
        ANIMAL_PRICES.put(59, new int[]{2000, -1});
    }

    // [PORT] Bản fan trả toàn 0 và không trừ tiền. Client gốc đọc gói 71:
    // species (byte) · newMoney (int, số dư TRƯỚC khi trừ của loại tiền đã dùng) · typeBuy (byte) ·
    // xu (int) · luong (int) · luongK (int) -> onBuyItem.
    public void Buy_ANIMAL(Message ms) throws IOException {
        byte n = ms.reader().readByte();//loai
        byte type = ms.reader().readByte();//typemua (1 = xu, khác = lượng)

        int[] prices = ANIMAL_PRICES.get((int) n);
        if (prices == null) {
            this.session.user.getAvatarService().serverDialog("Không có loài vật này.");
            return;
        }
        boolean byXu = type == 1;
        int price = byXu ? prices[0] : prices[1];
        if (price <= 0) {
            this.session.user.getAvatarService().serverDialog(byXu
                    ? "Loài này không bán bằng xu." : "Loài này không bán bằng lượng.");
            return;
        }
        long before;
        if (byXu) {
            before = this.session.user.getXu();
            if (before < price) {
                this.session.user.getAvatarService().serverDialog("Bạn không đủ xu.");
                return;
            }
            this.session.user.updateXu(-price);
        } else {
            before = this.session.user.getLuong();
            if (before < price) {
                this.session.user.getAvatarService().serverDialog("Bạn không đủ lượng.");
                return;
            }
            this.session.user.updateLuong(-price);
        }

        // [PORT Farm cấp 2] giới hạn số con mỗi loài theo hạng chuồng
        int sameSpecies = 0;
        for (Animal a0 : this.session.user.Animal) if (a0.getId() == n) sameSpecies++;
        if (sameSpecies >= Farm2Service.animalCap(this.session.user.getId(), n)) {
            // hoàn tiền vừa trừ
            if (byXu) this.session.user.updateXu(price);
            else this.session.user.updateLuong(price);
            this.session.user.getAvatarService().serverDialog(
                    "Chuồng đã đầy loài này (nâng cấp chuồng ở Farm cấp 2 để nuôi thêm).");
            return;
        }
        Animal newAnimal = new Animal(n, 100, 1, 0, 0, true, false, false);
        newAnimal.setBornTime(System.currentTimeMillis() / 1000L);
        newAnimal.setLastFeed(newAnimal.getBornTime());
        newAnimal.setLastHarvest(newAnimal.getBornTime());
        this.session.user.Animal.add(newAnimal);

        ms = new Message(Cmd.BUY_ANIMAL);
        DataOutputStream ds = ms.writer();
        ds.writeByte(n);
        ds.writeInt((int) before);
        ds.writeByte(type);
        ds.writeInt((int) this.session.user.getXu());
        ds.writeInt(this.session.user.getLuong());
        ds.writeInt(0);//luongK
        ds.flush();
        this.session.sendMessage(ms);
    }

    public void getBigFarm(Message ms) throws IOException {
        short imageID = ms.reader().readShort();
        String folder = this.session.getResourcesPath() + "bigFarm/";
        byte[] dat = Avatar.getFile(folder + imageID + ".png");
        if (dat == null) {
            return;
        }
        ms = new Message(54);
        DataOutputStream ds = ms.writer();
        ds.writeShort(imageID);
        ds.writeShort(dat.length);
        ds.writeShort(dat.length);
        for (int i = 0; i < dat.length; ++i) {
            ds.writeByte(dat[i]);
        }
        ds.flush();
        this.session.sendMessage(ms);
    }

    public void getImageData() {
        try {
            List<ImageInfo> imageInfos = GameData.getInstance().getFarmImageDatas();
            Message ms = new Message(Cmd.GET_IMAGE_FARM);
            DataOutputStream ds = ms.writer();
            ds.writeShort(imageInfos.size());
            for (ImageInfo imageInfo : imageInfos) {
                ds.writeShort(imageInfo.getId());
                ds.writeShort(imageInfo.getBigImageID());
                ds.writeByte(imageInfo.getX());
                ds.writeByte(imageInfo.getY());
                ds.writeByte(imageInfo.getW());
                ds.writeByte(imageInfo.getH());
            }
            ds.flush();
            this.sendMessage(ms);

        } catch (IOException e) {
            logger.debug("getImageData: " + e.getMessage());
        }
    }

    public void getTreeInfo(Message ms) throws IOException {
        byte[] dat = Avatar.getFile("res/data/farm_info.dat");
        if (dat == null) {
            return;
        }
        ms = new Message(Cmd.GET_TREE_INFO);
        DataOutputStream ds = ms.writer();
        ds.write(dat);
        ds.flush();
        this.session.sendMessage(ms);
    }

    // [PORT] Viết đúng thứ tự client gốc đọc (FarmMsgHandler case 60):
    // 1) hạt {byte id, short n}  2) phân bón {byte id, short n}  3) int LƯỢNG (client nạp vào money[1])
    // 4) lvFarm byte + perLv byte  5) đặc biệt {short, short}  6) đặc biệt lượt 1 {short, short}
    // 7) levelStore byte + capacity int + isNew bool  8) lvFarm short + perLv byte
    // 9) nông sản {short id, int n}  10) đặc biệt {short id, int n}
    // Bản fan ghi lệch: nông sản vào ô phân bón, xu vào ô lượng, lượt 2 nông sản sai cỡ số,
    // và chèn 3 dòng đặc biệt CỨNG (255/215/214) không phải dữ liệu của người chơi.
    public void getInventory(Message ms) throws IOException {
        User us = session.user;
        ms = new Message(60);
        DataOutputStream ds = ms.writer();
        ds.writeByte(this.session.user.hatgiong.size());
        for (HatGiong hatgiong : this.session.user.hatgiong) {
            ds.writeByte(hatgiong.getId());
            ds.writeShort(hatgiong.getSoluong());
        }

        ds.writeByte(this.session.user.PhanBon.size());
        for (PhanBon pb : this.session.user.PhanBon) {
            ds.writeByte(pb.getId());
            ds.writeShort(pb.getSoluong());
        }

        ds.writeInt(this.session.user.getLuong());
        ds.writeByte(us.getLeverFarm());
        ds.writeByte(us.getLeverPercen());

        // [PORT] Lượt {short, short} thứ nhất sau lvFarm là listItemFarm của
        // client gốc = vật phẩm DÙNG ĐƯỢC trong farm (phân bón/thuốc) — tức
        // kho PhanBon, không phải nông sản đặc biệt (bản GĐ0 ghi nhầm dacbiet
        // vào cả hai lượt). Lượt thứ hai mới là đặc biệt lượt 1.
        ds.writeByte(this.session.user.PhanBon.size());
        for (PhanBon pb : this.session.user.PhanBon) {
            ds.writeShort(pb.getId());
            ds.writeShort(pb.getSoluong());
        }
        ds.writeByte(this.session.user.NongSanDacBiet.size());
        for (NongSanDacBiet db : this.session.user.NongSanDacBiet) {
            ds.writeShort(db.getId());
            ds.writeShort(db.getSoluong());
        }
        ds.writeByte(1);        // levelStore
        ds.writeInt(64000);     // capacity
        ds.writeBoolean(true);  // isNew
        ds.writeShort(us.getLeverFarm());
        ds.writeByte(us.getLeverPercen());
        ds.writeByte(this.session.user.NongSan.size());
        for (NongSan ns : this.session.user.NongSan) {
            ds.writeShort(ns.getId());
            ds.writeInt(ns.getSoluong());
        }

        ds.writeByte(this.session.user.NongSanDacBiet.size());
        for (NongSanDacBiet db : this.session.user.NongSanDacBiet) {
            ds.writeShort(db.getId());
            ds.writeInt(db.getSoluong());
        }
        ds.flush();
        this.session.sendMessage(ms);
    }

    // [PORT] Ghi đúng nghĩa client gốc đọc (FarmMsgHandler.readInfoCell):
    // time (short, phút đã trồng) · vitalityPer (byte) · hervestPer (byte, % lớn) ·
    // isArid (bool, đất khô) · isWorm (bool) · isGrass (bool).
    // Bản fan ghi watered/fertilized/harvestable vào 3 bool cuối -> client hiểu thành khô/sâu/cỏ (lệch nghĩa).
    private void writeInfoCell(DataOutputStream ds, LandItem land) throws IOException {
        long minutes = land.getType() >= 0 ? land.getMinutesSincePlanted() : 0;
        ds.writeShort((int) Math.min(minutes, Short.MAX_VALUE));
        ds.writeByte(land.getSucKhoe());
        int hervestPer = 0;
        if (land.getType() >= 0) {
            farmItem itemf = PartManager.getInstance().findFarmitemByID(land.getType());
            if (itemf != null && itemf.getTime() > 0) {
                hervestPer = (int) Math.min(100, minutes * 100 / itemf.getTime());
            }
        }
        ds.writeByte(hervestPer);
        ds.writeBoolean(land.getType() >= 0 && !land.isWatered()); // isArid = chưa tưới
        ds.writeBoolean(false); // isWorm — model gốc không có trạng thái sâu
        ds.writeBoolean(false); // isGrass — model gốc không có trạng thái cỏ
    }

    // [PORT] Ghi đúng nghĩa client gốc đọc (FarmMsgHandler.readInfoAnimal):
    // bornTime (int) · health (byte) · harvestPer (byte) · numEggOne (byte) ·
    // hunger (bool) · disease[0] (bool) · disease[1] (bool).
    // Bản fan ghi health(int)/level/resourceCount/... vào đúng vị trí nhưng sai nghĩa toàn bộ.
    // [PORT] Kinh tế vật nuôi theo farm_info.dat (mục des của AnimalInfo, đã kiểm
    // chứng từng loài 2026-11-03): species -> {chu kỳ phút, sản lượng/chu kỳ,
    // giá bán lại (xu)}. Loài không có dòng sản phẩm (heo/chó/cá/trâu/rùa) chỉ
    // bán lại được. Chó không có giá bán trong dat -> 0 = không bán được.
    private static final java.util.Map<Integer, int[]> ANIMAL_ECON = new java.util.HashMap<>();
    static {
        ANIMAL_ECON.put(50, new int[]{360, 20, 950});    // Gà: 20 trứng / 6 giờ
        ANIMAL_ECON.put(51, new int[]{360, 30, 2000});   // Bò: 30 sữa / 6 giờ
        ANIMAL_ECON.put(52, new int[]{0, 0, 2000});      // Heo
        ANIMAL_ECON.put(53, new int[]{0, 0, 0});         // Chó (giữ nhà)
        ANIMAL_ECON.put(54, new int[]{0, 0, 10000});     // Cá chép
        ANIMAL_ECON.put(55, new int[]{720, 20, 1500});   // Cừu: 20 lông / 12 giờ
        ANIMAL_ECON.put(56, new int[]{360, 20, 1200});   // Vịt: 20 trứng / 6 giờ
        ANIMAL_ECON.put(58, new int[]{0, 0, 3000});      // Trâu
        ANIMAL_ECON.put(59, new int[]{0, 0, 5000});      // Rùa
    }

    private static int[] econOf(Animal a) {
        return ANIMAL_ECON.get(a.getId());
    }

    /** Đói: quá 6 giờ chưa cho ăn (mốc ăn gần nhất, mặc định = lúc mua). */
    private boolean isHungry(Animal a) {
        long last = a.getLastFeed() > 0 ? a.getLastFeed() : a.getBornTime();
        return System.currentTimeMillis() / 1000L - last > 6L * 3600L;
    }

    /** Số sản phẩm đang chờ thu (đói -> chỉ một nửa). */
    private int producible(Animal a) {
        int[] e = econOf(a);
        if (e == null || e[0] <= 0) {
            return 0;
        }
        long from = a.getLastHarvest() > 0 ? a.getLastHarvest() : a.getBornTime();
        long cycles = (System.currentTimeMillis() / 1000L - from) / (e[0] * 60L);
        if (cycles <= 0) {
            return 0;
        }
        int n = (int) Math.min(100000, cycles * e[1]);
        return isHungry(a) ? n / 2 : n;
    }

    /** % tiến độ tới chu kỳ sản phẩm kế tiếp (cho thanh harvestPer của client). */
    private int harvestProgress(Animal a) {
        int[] e = econOf(a);
        if (e == null || e[0] <= 0) {
            return 0;
        }
        long from = a.getLastHarvest() > 0 ? a.getLastHarvest() : a.getBornTime();
        long elapsed = System.currentTimeMillis() / 1000L - from;
        long cycleSec = e[0] * 60L;
        return (int) Math.min(100, (elapsed % cycleSec) * 100 / cycleSec);
    }

    private void writeInfoAnimal(DataOutputStream ds, Animal animal) throws IOException {
        ds.writeInt((int) animal.getBornTime());
        ds.writeByte(Math.min(100, Math.max(0, animal.getHealth())));
        // [PORT] harvestPer/numEggOne tính từ chu kỳ dat thay cho cờ lưu sẵn của bản fan.
        int ready = producible(animal);
        ds.writeByte(ready > 0 ? 100 : harvestProgress(animal));
        ds.writeByte(Math.min(255, ready));
        ds.writeBoolean(isHungry(animal)); // hunger — bản port mô phỏng theo mốc cho ăn
        ds.writeBoolean(false); // disease[0]
        ds.writeBoolean(false); // disease[1]
    }


    public void joinFarm(Message ms) throws IOException, SQLException {
        int userId = ms.reader().readInt();
        // [PORT] Farm khởi tạo lười: người chơi chưa có ô đất nào (farm trống lần đầu) -> cấp ô khởi đầu.
        // Client gốc (FarmScr.setCellAll) đặt các ô lên vị trí đất của map và chừa vị trí kế tiếp làm
        // điểm "mở ô", nên số ô khởi đầu chỉ cần nhỏ hơn tổng số vị trí đất trên map farm.
        // STARTER_CELLS là số tạm — sẽ đối chiếu farm nguyên trạng của server live ở giai đoạn dựng cảnh (GĐ1).
        if (userId == this.session.user.getId() && this.session.user.landItems.isEmpty()) {
            final int STARTER_CELLS = 6;
            for (int i = 0; i < STARTER_CELLS; i++) {
                this.session.user.landItems.add(new LandItem(0, -1, 100, 0, false, false, false, LocalDateTime.now()));
            }
        }
        ms = new Message(Cmd.JOIN);
        DataOutputStream ds = ms.writer();
        ds.writeInt(userId);


        int landSize = this.session.user.landItems.size();
        ds.writeByte(landSize);  // Ghi số lượng ô đất

        // Ghi thông tin các ô đất (cây)
        for (int i = 0; i < landSize; ++i) {
            LandItem landItem =  this.session.user.landItems.get(i);
            if (landItem.getType() >= 0) {
                ds.writeByte(landItem.getType());  //id
                writeInfoCell(ds, landItem);
            } else {
                ds.writeByte(-1);  // Không có cây trong ô đất này
            }
        }
        ds.writeByte(this.session.user.Animal.size());  // Ghi số lượng động vật
        for (Animal animal : this.session.user.Animal) {
            ds.writeByte(animal.getId());  // Ghi ID động vật (có thể là `animal.getType()` hoặc ID khác)
            writeInfoAnimal(ds, animal);  // Ghi thông tin về động vật vào DataOutputStream
        }

        // Ghi thông tin khác
        ds.writeByte(10);// chỉnh kích cỡ chuồng bò
        ds.writeByte(10);//chỉnh kích cỡ hồ cá
        ds.writeShort(10);  //cấp độ cây khế
        ds.writeShort(43);//43//img cay khe
        ds.writeShort(46);//46//img qua khe
        ds.writeShort(currentStarNum());  // Số khế có thể thu hoạch (trạng thái thật của user)
        ds.writeShort(0);//
        ds.writeShort(0);//0
        ds.writeShort(0);//0

        // Ghi trạng thái các ô đất
        for (int i = 0; i < landSize; ++i) {
            ds.writeByte(1);  // Trạng thái ô đất
        }

        // [PORT] Bản fan luôn ghi foodID=3 + 5 phút -> client tưởng đang nấu món Cơm mãi mãi.
        // Bản port: chỉ ghi đuôi foodID + remain(phút) khi THẬT SỰ đang nấu (gói 91);
        // client đọc available()>0 mới lấy, mặc định 0 = không nấu.
        if (this.session.user.foodId > 0) {
            long remainSec = this.session.user.cookFinish - System.currentTimeMillis() / 1000L;
            ds.writeShort(this.session.user.foodId);
            ds.writeShort((short) Math.max(0, (remainSec + 59) / 60));
        }
        ds.flush();

        this.session.sendMessage(ms);
    }


    public void getImgFarm(Message ms) throws IOException {
        short imageID = ms.reader().readShort();
        String folder = session.getResourcesPath() + "farm/";
        byte[] dat = Avatar.getFile(folder + imageID + ".png");
        if (dat == null) {
            return;
        }
        ms = new Message(Cmd.GET_IMG_FARM);
        DataOutputStream ds = ms.writer();
        ds.writeShort(imageID);
        ds.writeShort(dat.length);
        ds.write(dat);
        ds.flush();
        this.session.sendMessage(ms);
    }
}
