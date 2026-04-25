package ro.ainpc.world;

public enum RegionType {
    SETTLEMENT("settlement"),
    CASTLE("castle"),
    DUNGEON("dungeon"),
    CAVE("cave"),
    WILDERNESS("wilderness"),
    CUSTOM("custom");

    private final String id;

    RegionType(String id) {
        this.id = id;
    }

    public String getId() {
        return id;
    }

    public static RegionType fromId(String value) {
        if (value == null || value.isBlank()) {
            return CUSTOM;
        }

        for (RegionType type : values()) {
            if (type.id.equalsIgnoreCase(value) || type.name().equalsIgnoreCase(value)) {
                return type;
            }
        }

        return CUSTOM;
    }
}
