package ro.ainpc.world;

public enum WorldMode {
    STATIC("static", "Lume fixa"),
    FINITE_DYNAMIC("finite_dynamic", "Lume semi-dinamica, controlata"),
    OPEN_DYNAMIC("open_dynamic", "Lume dinamica deschisa");

    private final String id;
    private final String description;

    WorldMode(String id, String description) {
        this.id = id;
        this.description = description;
    }

    public String getId() {
        return id;
    }

    public String getDescription() {
        return description;
    }

    public static WorldMode fromId(String value) {
        if (value == null || value.isBlank()) {
            return FINITE_DYNAMIC;
        }

        for (WorldMode mode : values()) {
            if (mode.id.equalsIgnoreCase(value) || mode.name().equalsIgnoreCase(value)) {
                return mode;
            }
        }

        return FINITE_DYNAMIC;
    }
}
