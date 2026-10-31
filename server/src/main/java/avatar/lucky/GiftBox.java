package avatar.lucky;

import avatar.item.Item;
import avatar.lib.RandomCollection;
import avatar.model.Gift;
import avatar.model.User;
import avatar.server.Utils;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.Random;

public class GiftBox {
    private static final byte Items = 0;
    private static final byte XU = 1;
    private static final byte XP = 2;
    private static final byte LUONG = 3;

    private final RandomCollection<Byte> randomType = new RandomCollection<>();
    private final RandomCollection<Integer> randomItemList1 = new RandomCollection<>();
    private final RandomCollection<Integer> randomItemList2 = new RandomCollection<>();
    private final RandomCollection<Integer> randomItemList3 = new RandomCollection<>();

    private final RandomCollection<Byte> randomType1 = new RandomCollection<>();
    private final RandomCollection<Integer> randomEvent1 = new RandomCollection<>();
    private final RandomCollection<Integer> randomEvent2 = new RandomCollection<>();//xp hiế, hơn// item loại 2



    private static List<List<Item>> SieuNhan = new ArrayList<>();

    private static List<List<Item>> setHaiTac = new ArrayList<>();

    private static List<List<Item>> setVuTru = new ArrayList<>();


    static {
        List<Item> luffySet = new ArrayList<>();
        for (int i = 5409; i <= 5413; i++) {
            luffySet.add(new Item(i));
        }
        setHaiTac.add(luffySet);//nam

        List<Item> namiSet = new ArrayList<>();
        for (int i = 5414; i <= 5418; i++) {
            namiSet.add(new Item(i));
        }
        setHaiTac.add(namiSet);

        List<Item> mihawkSet = new ArrayList<>();
        for (int i = 5419; i <= 5423; i++) {
            mihawkSet.add(new Item(i));
        }
        setHaiTac.add(mihawkSet);//nam

        List<Item> NicoRobin = new ArrayList<>();
        for (int i = 5424; i <= 5427; i++) {
            NicoRobin.add(new Item(i));
        }
        setHaiTac.add(NicoRobin);//nam

        List<Item> Zoro = new ArrayList<>();
        for (int i = 5428; i <= 5432; i++) {
            Zoro.add(new Item(i));
        }
        setHaiTac.add(Zoro);//nam
///hop sieu nhan

        List<Item> gaoDen = new ArrayList<>();
        for (int i = 3937; i <= 3939; i++) {
            gaoDen.add(new Item(i));
        }
        SieuNhan.add(gaoDen);


        List<Item> gaoDO = new ArrayList<>();
        for (int i = 3940; i <= 3942; i++) {
            gaoDO.add(new Item(i));
        }
        SieuNhan.add(gaoDO);//nam


        List<Item> gaoXanh = new ArrayList<>();
        for (int i = 3943; i <= 3945; i++) {
            gaoXanh.add(new Item(i));
        }
        SieuNhan.add(gaoXanh);//nam
//hop qua vu tru


        List<Item> iron = new ArrayList<>();
        for (int i = 3174; i <= 3177; i++) {
            if (i == 3175) {continue;}
            iron.add(new Item(i));
        }
        setVuTru.add(iron);

        List<Item> Venom = new ArrayList<>();
        for (int i = 4306; i <= 4308; i++) {
            Venom.add(new Item(i));
        }
        setVuTru.add(Venom);

        List<Item> Deadpool = new ArrayList<>();
        for (int i = 4104; i <= 4107; i++) {
            Deadpool.add(new Item(i));
        }
        setVuTru.add(Deadpool);


        List<Item> DrStrange = new ArrayList<>();
        for (int i = 4564; i <= 4567; i++) {
            DrStrange.add(new Item(i));
        }
        setVuTru.add(DrStrange);

        List<Item> tiDus = new ArrayList<>();
        for (int i = 5343; i <= 5346; i++) {
            tiDus.add(new Item(i));
        }
        setVuTru.add(tiDus);

        List<Item> Yuna = new ArrayList<>();
        for (int i = 5347; i <= 5350; i++) {
            Yuna.add(new Item(i));
        }
        setVuTru.add(Yuna);

        List<Item> Batman = new ArrayList<>();
        for (int i = 5373; i <= 5375; i++) {
            Batman.add(new Item(i));
        }
        setVuTru.add(Batman);

        List<Item> NguoiMeo = new ArrayList<>();
        for (int i = 5376; i <= 5378; i++) {
            NguoiMeo.add(new Item(i));
        }
        setVuTru.add(NguoiMeo);
    }


    public GiftBox() {
        randomType.add(45, Items);
        randomType.add(25, XU);   // Tỷ lệ
        randomType.add(15, XP);   // Tỷ lệ
        randomType.add(15, LUONG); // Tỷ lệ 10%

        randomItemList1.add(33, 3672);//dns

        randomItemList2.add(33, 593);//item

        randomItemList3.add(34, 0);//item

        randomType1.add(45, Items);
        randomType1.add(15, XP);   // Tỷ lệ
        randomType1.add(25, XU);   // Tỷ lệ
        randomType1.add(15, LUONG); // Tỷ lệ 10%

        randomEvent1.add(10,5499);
        randomEvent1.add(10,4882);
        randomEvent1.add(10,4689);
        randomEvent1.add(10,4692);

        randomEvent2.add(10,6428);
        randomEvent2.add(10,3495);
        randomEvent2.add(10,5497);
        randomEvent2.add(10,6030);
        randomEvent2.add(10,6040);
        randomEvent2.add(10,4686);
        randomEvent2.add(10,4282);


    }

    public void open(User us, Item item) {
        byte type = randomType.next();
        switch (type) {
            case Items:
                RandomCollection<Integer> chosenItemCollection = chooseItemCollection();
                int idItems = chosenItemCollection.next();
                if( idItems == 0){
                    us.getAvatarService().serverDialog("Bạn nhận được cái nịt : V"+ String.format(" Số lượng còn lại: %,d", item.getQuantity()));
                }
                if( idItems == 3672 || idItems == 593){
                    Item Nro = new Item(idItems,-1,1);
                    if(us.findItemInChests(idItems) !=null){
                        int quantity = us.findItemInChests(idItems).getQuantity();
                        us.findItemInChests(idItems).setQuantity(quantity+1);
                    }else {
                        us.addItemToChests(Nro);
                    }
                    us.getAvatarService().serverDialog("Bạn nhận được "+ Nro.getPart().getName()  + String.format(" Số lượng còn lại: %,d", item.getQuantity()));
                }
                break;
            case XU:
                int xu = Utils.nextInt(1, 5) * 1000;
                us.updateXu(xu);
                us.getAvatarService().serverDialog("Bạn nhận được "+ xu +" Xu " + String.format("Số lượng còn lại: %,d", item.getQuantity()));
                us.getAvatarService().updateMoney(0);
                break;
            case XP:
                int xp = Utils.nextInt(1, 10) * 10;
                us.updateXP(xp);
                us.getAvatarService().serverDialog("Bạn nhận được "+ xp +" XP "+ String.format("Số lượng còn lại : %,d", item.getQuantity()));
                us.getAvatarService().updateMoney(0);
                break;
            case LUONG:
                int luong = Utils.nextInt(1,2);
                us.updateLuong(luong);
                us.getAvatarService().serverDialog("Bạn nhận được "+ luong +" Lượng "+ String.format("Số lượng còn lại : %,d", item.getQuantity()));
                us.getAvatarService().updateMoney(0);
                break;
        }
    }

    public void openHaiTac(User us, Item item) {
        Random random = new Random();
        List<Item> set;

        do {
            int setIndex = random.nextInt(setHaiTac.size());
            set = setHaiTac.get(setIndex);
        } while (!isGenderCompatible(set.get(0), us)); // Kiểm tra giới tính

        for (int i = 0; i < set.size(); i++) {
            set.get(i).setExpired(-1);
            us.addItemToChests(set.get(i));
        }

        us.getAvatarService().serverDialog("Bạn nhận được set " + set.get(0).getPart().getName() +
                String.format(" Số lượng còn lại : %,d", item.getQuantity()));
    }

    public void openSieuNhan(User us, Item item) {
        Random random = new Random();
        int setIndex = random.nextInt(SieuNhan.size());

        List<Item> set = SieuNhan.get(setIndex);

        for (int i = 0; i < set.size(); i++) {
            set.get(i).setExpired(-1);
            us.addItemToChests(set.get(i));
        }
        us.getAvatarService().serverDialog("Bạn nhận được set "+ set.get(0).getPart().getName() + String.format(" Số lượng còn lại : %,d", item.getQuantity()));
    }

    public void openSetVuTru(User us, Item item) {
        Random random = new Random();
        List<Item> set;

        do {
            int setIndex = random.nextInt(setVuTru.size());
            set = setVuTru.get(setIndex);
        } while (!isGenderCompatible(set.get(0), us)); // Kiểm tra giới tính

        for (int i = 0; i < set.size(); i++) {
            set.get(i).setExpired(-1);
            us.addItemToChests(set.get(i));
        }

        us.getAvatarService().serverDialog("Bạn nhận được set " + set.get(0).getPart().getName() +
                String.format(" Số lượng còn lại : %,d", item.getQuantity()));
    }

    public void openHopQuaMaQuai(User us, Item item) {
        byte type = randomType.next();
        switch (type) {
            case Items:
                RandomCollection<Integer> chosenItemCollection = choseItemGiftEvent();
                int idItems = chosenItemCollection.next();
                Item rewardItem  = new Item(idItems);
                boolean ok =  (Utils.nextInt(100) < 80) ? true : false;
                if(ok){
                    rewardItem.setExpired(-1);
                    us.addItemToChests(rewardItem);
                    us.getAvatarService().serverDialog("Bạn nhận được "+ rewardItem.getPart().getName()  + String.format(" Vĩnh viễn"));
                }else {
                    rewardItem.setExpired(System.currentTimeMillis() + (86400000L * 7));
                    us.addItemToChests(rewardItem);
                    us.getAvatarService().serverDialog("Bạn nhận được "+ rewardItem.getPart().getName()  + String.format(" 7 ngày"));
                }
                break;
            case XP:
                RandomCollection<Integer> chosenItemCollection1 = choseItemGiftEvent();
                int idItems1 = chosenItemCollection1.next();
                Item rewardItem1  = new Item(idItems1);
                boolean ok1 =  (Utils.nextInt(100) < 80) ? true : false;
                if(ok1){
                    rewardItem1.setExpired(-1);
                    us.addItemToChests(rewardItem1);
                    us.getAvatarService().serverDialog("Bạn nhận được "+ rewardItem1.getPart().getName()  + String.format(" Vĩnh viễn"));
                }else {
                    rewardItem1.setExpired(System.currentTimeMillis() + (86400000L * 7));
                    us.addItemToChests(rewardItem1);
                    us.getAvatarService().serverDialog("Bạn nhận được "+ rewardItem1.getPart().getName()  + String.format(" 7 ngày"));
                }
                break;
            case XU:
                int xu = Utils.nextInt(200, 500) * 1000;
                us.updateXu(xu);
                us.getAvatarService().serverDialog("Bạn nhận được "+ xu +" Xu ");
                us.getAvatarService().updateMoney(0);
                break;
            case LUONG:
                int luong = Utils.nextInt(20, 60);
                us.updateLuong(luong);
                us.getAvatarService().serverDialog("Bạn nhận được "+ luong +" Lượng ");
                us.getAvatarService().updateMoney(0);
                break;
        }
    }

    private boolean isGenderCompatible(Item item, User user) {
        int itemGender = item.getPart().getGender(); // Giới tính của item (0 = cả hai giới, 1 = nam, 2 = nữ)
        int userGender = user.getGender(); // Giới tính của user (1 = nam, 2 = nữ)

        // Nếu itemGender là 0, thì cả hai giới đều dùng được
        if (itemGender == 0) {
            return true;
        }

        // Nếu không, kiểm tra xem giới tính của item có khớp với giới tính của user không
        return itemGender == userGender;
    }

    private RandomCollection<Integer> chooseItemCollection() {
        RandomCollection<RandomCollection<Integer>> itemCollections = new RandomCollection<>();
        itemCollections.add(20, randomItemList1);
        itemCollections.add(35, randomItemList2);
        itemCollections.add(45, randomItemList3);
        return itemCollections.next();
    }

    private RandomCollection<Integer> choseItemGiftEvent() {
        RandomCollection<RandomCollection<Integer>> itemCollections = new RandomCollection<>();
        itemCollections.add(70, randomEvent1);
        itemCollections.add(30, randomEvent2);
        return itemCollections.next();
    }
}
