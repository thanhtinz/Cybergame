package avatar.model;

import avatar.item.Item;
import avatar.network.Message;

import java.util.ArrayList;
import java.util.List;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

public class Npc extends User {

    public static final int ID_ADD = 2000000000;

    @Getter
    @Setter
    private List<String> textChats;

    private Thread autoChatBot = new Thread(() -> {
        while (true) {
            try {
                for (String text : textChats) {
                    getMapService().chat(this, text);
                    Thread.sleep(6000);
                }
                if (textChats == null || textChats.size() == 0) {
                    Thread.sleep(10000);
                }
            } catch (InterruptedException ignored) {
            }
        }
    });

    private Thread autoChatBotSpeed = new Thread(() -> {
        while (true) {
            try {
                for (String text : textChats) {
                    getMapService().chat(this, text);
                    Thread.sleep(500);
                }
                if (textChats == null || textChats.size() == 0) {
                    Thread.sleep(1000);
                }
            } catch (InterruptedException ignored) {
            }
        }
    });

    @Builder
    public Npc(int id, String name, short x, short y, ArrayList<Item> wearing) {
        setId(id > ID_ADD ? id : id + ID_ADD);
        setUsername(name);
        setRole((byte) 0);
        setX(x);
        setY(y);
        setWearing(wearing);
        textChats = new ArrayList<>();
        if(id == 864){
            autoChatBotSpeed.start();
        }else{
            autoChatBot.start();
        }
    }

    public void addChat(String chat) {
        textChats.add(chat);
    }


    @Override
    public void sendMessage(Message ms) {

    }
}