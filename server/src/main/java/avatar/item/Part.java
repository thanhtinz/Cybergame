package avatar.item;

import avatar.common.BossShopItem;
import avatar.service.AvatarService;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Builder
@Getter
@AllArgsConstructor
public class Part {

    private int id;
    private String name;
    private int coin;
    private int gold;
    private short icon;
    private short type;
    private byte zOrder;
    private byte sell;
    private byte level;
    private byte gender;
    private int expiredDay;
    private short[] imgID;
    private byte[] dx;
    private byte[] dy;

    public static List<Item> shopByPart(List<Part> partItems) {
        // Create a list of part IDs
        List<Integer> partID = partItems.stream()
                .map(Part::getId) // Collect IDs directly
                .collect(Collectors.toList());

        // Filter and sort the ShopVQBD parts
        List<Item> shopPart = partItems
                .stream()
                .filter(part -> partID.contains(part.getId())) // Filter by ID
                .sorted(Comparator.comparingInt(part -> {
                    int index = partID.indexOf(part.getId());
                    return index == -1 ? Integer.MAX_VALUE : index; // Sort by original order
                }))
                .map(part -> new Item(part.getId())) // Assuming you create Item from Part ID
                .collect(Collectors.toList()); // Collect into a list of Item

        return shopPart; // Return the list of Items
    }

}
