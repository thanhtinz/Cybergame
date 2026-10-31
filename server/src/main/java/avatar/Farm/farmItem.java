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

    public farmItem(int id) {
        this.id = id;
    }

    @Builder
    public farmItem(int id,String name, int time, int quantity,int sell) {
        this.id = id;
        this.name = name;
        this.time = time;
        this.quantity = quantity;
        this.sell = sell;
        //init();
    }

//    public void init() {
//    }
}
