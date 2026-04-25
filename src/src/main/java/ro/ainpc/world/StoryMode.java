package ro.ainpc.world;

public enum StoryMode {
    STATIC("static", "Poveste fixa"),
    EVOLUTIVE("evolutive", "Poveste care avanseaza gradual"),
    ROTATIVE("rotative", "Poveste care se roteste intre stari");

    private final String id;
    private final String description;

    StoryMode(String id, String description) {
        this.id = id;
        this.description = description;
    }

    public String getId() {
        return id;
    }

    public String getDescription() {
        return description;
    }

    public static StoryMode fromId(String value) {
        if (value == null || value.isBlank()) {
            return EVOLUTIVE;
        }

        for (StoryMode mode : values()) {
            if (mode.id.equalsIgnoreCase(value) || mode.name().equalsIgnoreCase(value)) {
                return mode;
            }
        }

        return EVOLUTIVE;
    }
}
