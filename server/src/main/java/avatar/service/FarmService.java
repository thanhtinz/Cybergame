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

    public void Buy_item_farm(Message ms) throws IOException {
        short id = ms.reader().readShort();
        byte n = ms.reader().readByte();
        byte type = ms.reader().readByte();
        System.out.println("item farm id "+id+" sl "+n+" type sell"+type);

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

        Animal newAnimal = new Animal(n, 100, 1, 0, 0, true, false, false);
        newAnimal.setBornTime(System.currentTimeMillis() / 1000L);
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
    private void writeInfoAnimal(DataOutputStream ds, Animal animal) throws IOException {
        ds.writeInt((int) animal.getBornTime());
        ds.writeByte(Math.min(100, Math.max(0, animal.getHealth())));
        ds.writeByte(animal.isHarvestable() ? 100 : 0);
        ds.writeByte(Math.min(255, Math.max(0, animal.getResourceCount())));
        ds.writeBoolean(false); // hunger — model gốc không mô phỏng đói
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
        ds.writeShort(180);  // Số khế có thể thu hoạch
        ds.writeShort(0);//
        ds.writeShort(0);//0
        ds.writeShort(0);//0

        // Ghi trạng thái các ô đất
        for (int i = 0; i < landSize; ++i) {
            ds.writeByte(1);  // Trạng thái ô đất
        }

        // [PORT] Bản fan luôn ghi foodID=3 + 5 phút -> client tưởng đang nấu món Cơm mãi mãi.
        // Không ghi gì thêm: client đọc available()>0 mới lấy foodID, mặc định 0 = không nấu.
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
