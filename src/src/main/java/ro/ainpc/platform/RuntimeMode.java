package ro.ainpc.platform;

import java.util.EnumSet;
import java.util.List;

public enum RuntimeMode {
    STANDALONE("standalone", "Fara servicii externe obligatorii"),
    HYBRID("hybrid", "Servicii externe optionale"),
    ADVANCED("advanced", "Servicii externe si sync dedicate");

    private final String id;
    private final String description;

    RuntimeMode(String id, String description) {
        this.id = id;
        this.description = description;
    }

    public String getId() {
        return id;
    }

    public String getDescription() {
        return description;
    }

    public boolean usesExternalAi() {
        return this != STANDALONE;
    }

    public boolean usesExternalDatabase() {
        return this == ADVANCED;
    }

    public boolean usesDistributedSync() {
        return this == ADVANCED;
    }

    public static RuntimeMode fromId(String value) {
        if (value == null || value.isBlank()) {
            return STANDALONE;
        }

        for (RuntimeMode mode : values()) {
            if (mode.id.equalsIgnoreCase(value) || mode.name().equalsIgnoreCase(value)) {
                return mode;
            }
        }

        return STANDALONE;
    }

    public static EnumSet<RuntimeMode> fromIds(List<String> values) {
        if (values == null || values.isEmpty()) {
            return EnumSet.allOf(RuntimeMode.class);
        }

        EnumSet<RuntimeMode> result = EnumSet.noneOf(RuntimeMode.class);
        for (String value : values) {
            result.add(fromId(value));
        }
        return result.isEmpty() ? EnumSet.allOf(RuntimeMode.class) : result;
    }
}
