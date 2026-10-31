/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package avatar.item;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import avatar.model.User;
import avatar.server.Utils;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

/**
 *
 * @author kitakeyos - Hoàng Hữu Dũng
 */
@Getter
@Setter
public class Item {
    
    private int id;
    private long expired;
    private int quantity;
    private Part part;
    
    public Item(int id) {
        this.id = id;
        init();
    }
    
    @Builder
    public Item(int id, long expired, int quantity) {
        this.id = id;
        this.expired = expired;
        this.quantity = quantity;
        init();
    }
    
    public boolean isForever() {
        return this.expired == -1;
    }
    
    public int getDay() {
        return (int) ((this.expired - System.currentTimeMillis()) / 1000 / 60 / 60 / 24) + 1;
    }
    public synchronized int increase(User us, int quantity, int itemId) {
        // Kiểm tra nếu số lượng yêu cầu là hợp lệ
        if (quantity <= 0) {
            return this.quantity;
        }

        // Áp dụng giới hạn số lượng tối đa là 100
        if (this.quantity + quantity > 20000) {
            Utils.writeLog(us, "quantity, increase " + quantity + " by " + itemId);
            this.quantity = 20000;
        } else {
            this.quantity += quantity;
        }
        return this.quantity;
    }



    public synchronized int reduce(int quantity) {
        if (quantity <= 0 || this.quantity - quantity < 0) {
            return this.quantity;
        }
        this.quantity -= quantity;

        return this.quantity;
    }


    public String expiredString() {
        if (isForever()) {
            return "";
        }
        DateFormat dateFormat = new SimpleDateFormat("dd-MM-yyyy");
        return "Ngày hết hạn: " + dateFormat.format(new Date(this.expired));
    }
    
    public byte reliability() {
        int reliability = getDay() * 100 / 30;
        if (isForever()) {
            reliability = 100;
        } else if (reliability > 100) {
            reliability = 100;
        } else if (reliability < 0) {
            reliability = 0;
        }
        return (byte) reliability;
    }
    
    public void init() {
        this.part = PartManager.getInstance().findPartByID(id);
    }
}
