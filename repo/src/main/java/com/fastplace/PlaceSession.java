package com.fastplace;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.BlockFace;

import java.util.HashSet;
import java.util.Set;

public class PlaceSession {

    private final Material material;
    private final Set<Location> placedLocations = new HashSet<>();
    private int count = 0;
    private boolean active = true;
    private BlockFace lastFace;

    public PlaceSession(Material material, BlockFace initialFace) {
        this.material = material;
        this.lastFace = initialFace;
    }

    public Material getMaterial() {
        return material;
    }

    public boolean hasPlaced(Location loc) {
        return placedLocations.contains(loc);
    }

    public void addPlaced(Location loc) {
        placedLocations.add(loc);
        count++;
    }

    public int getCount() {
        return count;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public BlockFace getLastFace() {
        return lastFace;
    }

    public void setLastFace(BlockFace face) {
        this.lastFace = face;
    }
}
