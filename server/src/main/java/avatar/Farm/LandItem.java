package avatar.Farm;

import java.time.Duration;
import java.time.LocalDateTime;

public class LandItem {
    private int growthTime;        // Thời gian cần để cây trưởng thành
    private int type;
    private int sucKhoe;
    private int resourceCount;     // Số lượng tài nguyên trong ô đất (có thể là số quả hoặc các tài nguyên khác)
    private boolean isWatered;     // Trạng thái tưới nước
    private boolean isFertilized;  // Trạng thái đã bón phân
    private boolean isHarvestable; // Trạng thái có thể thu hoạch
    private LocalDateTime plantedTime; // Time when the plant was planted
    // Constructor
    public LandItem(int growthTime, int type,int suckhoe, int resourceCount, boolean isWatered, boolean isFertilized, boolean isHarvestable, LocalDateTime plantedTime) {
        this.growthTime = growthTime;
        this.type = type;
        this.sucKhoe = suckhoe;
        this.resourceCount = resourceCount;
        this.isWatered = isWatered;
        this.isFertilized = isFertilized;
        this.isHarvestable = isHarvestable;
        this.plantedTime = plantedTime;
    }


    public LocalDateTime getPlantedTime() {
        return plantedTime;
    }

    public void setPlantedTime(LocalDateTime plantedTime) {
        this.plantedTime = plantedTime;
    }
    // Getters và Setters
    public int getGrowthTime() {
        return growthTime;
    }

    public void setGrowthTime(int growthTime) {
        this.growthTime = growthTime;
    }

    public int getType() {
        return type;
    }

    public void setType(int type) {
        this.type = type;
    }
    public int getSucKhoe() {
        return sucKhoe;
    }

    public void setSucKhoe(int sucKhoe) {
        this.sucKhoe = sucKhoe;
    }

    public int getResourceCount() {
        return resourceCount;
    }

    public void setResourceCount(int resourceCount) {
        this.resourceCount = resourceCount;
    }

    public boolean isWatered() {
        return isWatered;
    }

    public void setWatered(boolean watered) {
        isWatered = watered;
    }

    public boolean isFertilized() {
        return isFertilized;
    }

    public void setFertilized(boolean fertilized) {
        isFertilized = fertilized;
    }

    public boolean isHarvestable() {
        return isHarvestable;
    }

    public void setHarvestable(boolean harvestable) {
        isHarvestable = harvestable;
    }

    @Override
    public String toString() {
        return "Land{" +
                "growthTime=" + growthTime +
                ", type=" + sucKhoe +
                ", resourceCount=" + resourceCount +
                ", isWatered=" + isWatered +
                ", isFertilized=" + isFertilized +
                ", isHarvestable=" + isHarvestable +
                '}';
    }

    public long getMinutesSincePlanted() {
        if (this.plantedTime == null) {
            throw new IllegalStateException("plantedTime is not set");
        }

        LocalDateTime now = LocalDateTime.now();
        Duration duration = Duration.between(this.plantedTime, now);
        return duration.toMinutes();
    }
}
