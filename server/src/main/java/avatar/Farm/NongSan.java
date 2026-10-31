package avatar.Farm;

import avatar.lib.KeyValue;

import java.util.Vector;

public class NongSan {
    private int id;          // ID của hạt giống
    private int soluong;     // Số lượng của hạt giống

    // Constructor với 2 tham số id và soluong
    public NongSan(int id, int soluong) {
        this.id = id;
        this.soluong = soluong;
    }

    // Getter cho id
    public int getId() {
        return id;
    }

    // Setter cho id
    public void setId(int id) {
        this.id = id;
    }

    // Getter cho soluong
    public int getSoluong() {
        return soluong;
    }

    // Setter cho soluong
    public void setSoluong(int soluong) {
        this.soluong = soluong;
    }

    // Phương thức in thông tin về hạt giống
    public void printInfo() {
        System.out.println("ID Hạt giống: " + id + ", Số lượng: " + soluong);
    }
}
