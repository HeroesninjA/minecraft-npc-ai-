package ro.ainpc.addons;

public enum AddonType {
    CORE("core", "Nucleu platforma"),
    SCENARIO("scenario", "Scenariu principal"),
    FEATURE("feature", "Feature addon"),
    INTEGRATION("integration", "Addon de integrare");

    private final String id;
    private final String displayName;

    AddonType(String id, String displayName) {
        this.id = id;
        this.displayName = displayName;
    }

    public String getId() {
        return id;
    }

    public String getDisplayName() {
        return displayName;
    }

    public static AddonType fromId(String value) {
        if (value == null || value.isBlank()) {
            return FEATURE;
        }

        for (AddonType type : values()) {
            if (type.id.equalsIgnoreCase(value) || type.name().equalsIgnoreCase(value)) {
                return type;
            }
        }

        return FEATURE;
    }
}
