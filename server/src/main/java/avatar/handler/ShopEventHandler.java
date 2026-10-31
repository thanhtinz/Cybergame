package avatar.handler;

import avatar.common.BossShopItem;
import avatar.constants.NpcName;
import avatar.item.PartManager;
import avatar.model.BossShop;
import avatar.model.Npc;
import avatar.model.User;
import avatar.service.AvatarService;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;



public class ShopEventHandler {

    public static void displayUI(User us,int npcID, int... itemIds) {
        AvatarService service = us.getAvatarService();
        List<Integer> itemIdList = IntStream.of(itemIds)
                .boxed()
                .collect(Collectors.toList());
        List<BossShopItem> EventShop = PartManager.getInstance().getUpgradeItems()
                .stream()
                .filter(upgradeItem -> itemIdList.contains(upgradeItem.getItem().getId()))
                .sorted(Comparator.comparingInt(item -> {
                    int index = itemIdList.indexOf(item.getItem().getId());
                    return index == -1 ? Integer.MAX_VALUE : index;
                }))
                .collect(Collectors.toList());
        us.setBossShopItems(EventShop);
        service.openUIShopEvent(
                BossShop.builder()
                        .idBoss(npcID + Npc.ID_ADD)
                        .idShop((byte) 0)
                        .typeShop((byte) 0)
                        .name("Event")
                        .build()
                , EventShop);
    }

    public static void handle() {

    }
}