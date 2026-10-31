package avatar.model;

import avatar.common.BossShopItem;
import avatar.constants.NpcName;
import avatar.handler.BossShopHandler;
import avatar.handler.UpgradeItemHandler;
import avatar.item.PartManager;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.text.MessageFormat;

@Getter
@Setter
@SuperBuilder
public class UpgradeItem extends BossShopItem {
    private boolean isOnlyLuong; // if true, can not upgrade by coins
    private int ratio;// tỉ lệ nâng cấp
    private int itemNeed; // item cần có để nâng
    private int itemRequest;//name_item
    private int xu;
    private int luong;
    private int scores;//điểm đổi sự kiện

    @Override
    public String initDialog(BossShop bossShop) {
        if (isOnlyLuong && bossShop.getIdShop() == BossShopHandler.SELECT_XU) {
            return "Bạn chỉ có thể nâng cấp vật phẩm này bằng lượng";
        }
        else if (bossShop.getIdBoss() == NpcName.bunma + Npc.ID_ADD) {
            if(getItem().getId() == 3861 ){
                return MessageFormat.format(
                        "Bạn có muốn đổi {0} bằng {1} điểm sự kiện không?(tối đa 1)",
                        super.getItem().getPart().getName(),
                        this.scores
                );
            }
            return MessageFormat.format(
                    "Bạn có muốn đổi {0} bằng {1} điểm sự kiện không?",
                    super.getItem().getPart().getName(),
                    this.scores
            );
        }else if (bossShop.getIdShop() == BossShopHandler.SELECT_DNS) {
            StringBuilder resources = new StringBuilder();
            if (luong > 0) {
                resources.append(luong).append(" Lượng");
            }
            if (xu > 0) {
                if (resources.length() > 0) {
                    resources.append(" + "); // Thêm dấu "+" nếu đã có Lượng
                }
                resources.append(xu).append(" Xu");
            }
            String ratioDisplay = "";
            if (ratio > 0 && ratio < 100) {
                ratioDisplay = ratio + "%";
            } else if (ratio == 0) {
                ratioDisplay = "Không xác định";
            }

// Điều kiện định dạng dựa vào tỷ lệ xác suất
            String formatString = ratioDisplay.isEmpty()
                    ? "Bạn có muốn đổi {0} từ {1} + {2} + {3}"
                    : "Bạn có muốn nâng cấp {0} từ {1} + {2} + {3} (xác suất {4})";

// Sử dụng formatString tùy vào điều kiện ratioDisplay
            return MessageFormat.format(
                    formatString,
                    super.getItem().getPart().getName(),
                    PartManager.getInstance().findPartById(itemNeed).getName(),
                    scores + " Đá ngũ sắc",
                    resources.toString(),
                    ratioDisplay
            );

        }else if (bossShop.getIdShop() == BossShopHandler.SELECT_HoaNS) {
            StringBuilder resources = new StringBuilder();
            if (luong > 0) {
                resources.append(luong).append(" Lượng");
            }
            if (xu > 0) {
                if (resources.length() > 0) {
                    resources.append(" + "); // Thêm dấu "+" nếu đã có Lượng
                }
                resources.append(xu).append(" Xu");
            }
            return MessageFormat.format(
                    "Bạn có muốn nâng cấp {0} từ {1} + {2} + {3} (xác suất {4})",
                    super.getItem().getPart().getName(),
                    PartManager.getInstance().findPartById(itemNeed).getName(),
                    scores == 12 ?  " 20 Sen Ngũ Sắc": scores + " Sen Ngũ Sắc",
                    resources.toString(), // Sử dụng chuỗi tài nguyên đã xây dựng
                    ratio > 0 ? (ratio + "%") : "Không xác định"
            );
        }
        else if (bossShop.getIdShop() == BossShopHandler.SELECT_ManhGhep) {
            return MessageFormat.format(
                    "Bạn có muốn đổi {0} bằng {1} {2} không ?",
                    super.getItem().getPart().getName(),
                    scores,
                    PartManager.getInstance().findPartById(itemNeed).getName()
            );
        } else if (bossShop.getIdBoss() == NpcName.Vegeta + Npc.ID_ADD) {
            return MessageFormat.format(
                    "Bạn có muốn đổi {0} bằng {1} không?",
                    super.getItem().getPart().getName(),
                    PartManager.getInstance().findPartById(itemNeed).getName()

            );
        } else if (bossShop.getIdBoss() == NpcName.Shop_Buy_Luong + Npc.ID_ADD) {
            return MessageFormat.format(
                    "Bạn có muốn đổi {0} bằng 1 jack à nhầm bằng {1} Lượng không?",
                    super.getItem().getPart().getName(),
                    super.getItem().getPart().getGold()
            );
        }else if (bossShop.getIdBoss() == NpcName.Chay_To_Win + Npc.ID_ADD) {
            return MessageFormat.format(
                    "Bạn có muốn đổi {0} bằng {1} xu không?",
                    super.getItem().getPart().getName(),
                    this.getXu()
            );
        }else if (bossShop.getIdBoss() == NpcName.Pay_To_Win + Npc.ID_ADD) {
            return MessageFormat.format(
                    "Bạn có muốn đổi {0} bằng {1} {2} không?",
                    super.getItem().getPart().getName(),
                    this.getScores(),
                    PartManager.getInstance().findPartById(itemNeed).getName()
            );
        }
        return MessageFormat.format(
                "Bạn có muốn ghép 1 {0}+{1} để lấy 1 {2}(xác suất {3})",
                PartManager.getInstance().findPartById(itemNeed).getName(),
                bossShop.getIdShop() == BossShopHandler.SELECT_XU ? (xu + " xu") : (luong + " lượng"),
                super.getItem().getPart().getName(),
                ratio > 0 ? (ratio + "%") : "Không xác định"
        );
    }
}