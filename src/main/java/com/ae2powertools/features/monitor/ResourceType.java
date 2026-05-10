package com.ae2powertools.features.monitor;


/**
 * Enum representing the different types of resources that can be monitored.
 * Each type corresponds to a storage channel in AE2.
 */
public enum ResourceType {

    ITEM(0, "item"),
    FLUID(1, "fluid"),
    GAS(2, "gas"),
    ESSENTIA(3, "essentia");

    private final int id;
    private final String name;

    ResourceType(int id, String name) {
        this.id = id;
        this.name = name;
    }

    public int getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public static ResourceType fromId(int id) {
        for (ResourceType type : values()) {
            if (type.id == id) return type;
        }

        return ITEM;
    }
}
