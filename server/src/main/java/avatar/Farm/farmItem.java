package avatar.Farm;
import avatar.item.PartManager;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Builder
@Getter
public class farmItem {
    private int id;
    private String name;
    private int time;
    private int quantity;
    private int sell;
    // [PORT] Giá hạt bằng Lượng theo farm_info.dat (priceSeedLuong). sell = giá hạt bằng Xu.
    // Chỉ Nha Đam (31) bán bằng lượng (1); các cây khác seedLuong = 0 = không bán bằng lượng.
    private int sellLuong;

    public void setSellLuong(int sellLuong) {
        this.sellLuong = sellLuong;
    }

    public farmItem(int id) {
        this.id = id;
    }

    @Builder
    public farmItem(int id,String name, int time, int quantity,int sell, int sellLuong) {
        this.id = id;
        this.name = name;
        this.time = time;
        this.quantity = quantity;
        this.sell = sell;
        this.sellLuong = sellLuong;
        //init();
    }

//    public void init() {
//    }
}
