package com.ecoguard.helpers;

import com.ecoguard.models.ItemType;
import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

/**
 * Inventory - Manages storage of items for the farm.
 * Serializable for agent transport.
 */
public class Inventory implements Serializable {
    private static final long serialVersionUID = 1L;

    private final Map<ItemType, Integer> items;
    private final int maxCapacity;

    public Inventory(int maxCapacity) {
        this.items = new HashMap<>();
        this.maxCapacity = maxCapacity;

        // Initialize all item types to 0
        for (ItemType type : ItemType.values()) {
            items.put(type, 0);
        }
    }

    /**
     * Add items to inventory.
     * 
     * @return true if successful, false if exceeds capacity
     */
    public synchronized boolean addItem(ItemType type, int quantity) {
        if (quantity <= 0)
            return false;

        int currentTotal = getTotalItems();
        if (currentTotal + quantity > maxCapacity) {
            return false; // Would exceed capacity
        }

        items.put(type, items.get(type) + quantity);
        return true;
    }

    /**
     * Remove items from inventory.
     * 
     * @return true if successful, false if insufficient quantity
     */
    public synchronized boolean removeItem(ItemType type, int quantity) {
        if (quantity <= 0)
            return false;

        int current = items.get(type);
        if (current < quantity) {
            return false; // Insufficient quantity
        }

        items.put(type, current - quantity);
        return true;
    }

    /**
     * Check if inventory has sufficient quantity.
     */
    public synchronized boolean hasItem(ItemType type, int quantity) {
        return items.get(type) >= quantity;
    }

    /**
     * Get quantity of specific item.
     */
    public synchronized int getQuantity(ItemType type) {
        return items.get(type);
    }

    /**
     * Get total items in inventory.
     */
    public synchronized int getTotalItems() {
        return items.values().stream().mapToInt(Integer::intValue).sum();
    }

    /**
     * Get remaining capacity.
     */
    public synchronized int getRemainingCapacity() {
        return maxCapacity - getTotalItems();
    }

    /**
     * Check if inventory is full.
     */
    public synchronized boolean isFull() {
        return getTotalItems() >= maxCapacity;
    }

    /**
     * Get all items as a map copy.
     */
    public synchronized Map<ItemType, Integer> getAllItems() {
        return new HashMap<>(items);
    }

    /**
     * Convert to JSON for WebSocket.
     */
    public String toJson() {
        StringBuilder json = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<ItemType, Integer> entry : items.entrySet()) {
            if (entry.getValue() > 0) {
                if (!first)
                    json.append(",");
                json.append("\"").append(entry.getKey().name()).append("\":").append(entry.getValue());
                first = false;
            }
        }
        json.append("}");
        return json.toString();
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("Inventory [");
        boolean first = true;
        for (Map.Entry<ItemType, Integer> entry : items.entrySet()) {
            if (entry.getValue() > 0) {
                if (!first)
                    sb.append(", ");
                sb.append(entry.getKey().getDisplayName()).append(":").append(entry.getValue());
                first = false;
            }
        }
        sb.append("] (").append(getTotalItems()).append("/").append(maxCapacity).append(")");
        return sb.toString();
    }
}
