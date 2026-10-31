package avatar.Farm;

public class
Animal {
    private int id;//id vat nuoi
    private int health;              // Sức khỏe của động vật
    private int level;               // Cấp độ của động vật
    private int resourceCount;       // Số tài nguyên (sản phẩm) mà động vật tạo ra
    private int nextProductionTime;  // Thời gian để động vật tạo ra sản phẩm tiếp theo
    private boolean isAlive;         // Trạng thái động vật có còn sống không
    private boolean isReadyForBreeding; // Trạng thái động vật sẵn sàng sinh sản không
    private boolean isHarvestable;   // Trạng thái động vật có thể thu hoạch sản phẩm không

    // Constructor
    public Animal(int id,int health, int level, int resourceCount, int nextProductionTime, boolean isAlive, boolean isReadyForBreeding, boolean isHarvestable) {
        this.id = id;
        this.health = health;
        this.level = level;
        this.resourceCount = resourceCount;
        this.nextProductionTime = nextProductionTime;
        this.isAlive = isAlive;
        this.isReadyForBreeding = isReadyForBreeding;
        this.isHarvestable = isHarvestable;
    }


    // Getters và Setters
    public int getId() {
        return id;
    }

    public void setId(int Id) {
        this.id = Id;
    }


    // Getters và Setters
    public int getHealth() {
        return health;
    }

    public void setHealth(int health) {
        this.health = health;
    }

    public int getLevel() {
        return level;
    }

    public void setLevel(int level) {
        this.level = level;
    }

    public int getResourceCount() {
        return resourceCount;
    }

    public void setResourceCount(int resourceCount) {
        this.resourceCount = resourceCount;
    }

    public int getNextProductionTime() {
        return nextProductionTime;
    }

    public void setNextProductionTime(int nextProductionTime) {
        this.nextProductionTime = nextProductionTime;
    }

    public boolean isAlive() {
        return isAlive;
    }

    public void setAlive(boolean alive) {
        isAlive = alive;
    }

    public boolean isReadyForBreeding() {
        return isReadyForBreeding;
    }

    public void setReadyForBreeding(boolean readyForBreeding) {
        isReadyForBreeding = readyForBreeding;
    }

    public boolean isHarvestable() {
        return isHarvestable;
    }

    public void setHarvestable(boolean harvestable) {
        isHarvestable = harvestable;
    }
    public byte getType() {
        // Ví dụ trả về một ID động vật ngẫu nhiên (hoặc có thể được xác định theo loại động vật cụ thể)
        return (byte) (50 + level % 7);  // Giả sử ID động vật phụ thuộc vào cấp độ
    }
    @Override
    public String toString() {
        return "Animal{" +
                "health=" + health +
                ", level=" + level +
                ", resourceCount=" + resourceCount +
                ", nextProductionTime=" + nextProductionTime +
                ", isAlive=" + isAlive +
                ", isReadyForBreeding=" + isReadyForBreeding +
                ", isHarvestable=" + isHarvestable +
                '}';
    }
}
