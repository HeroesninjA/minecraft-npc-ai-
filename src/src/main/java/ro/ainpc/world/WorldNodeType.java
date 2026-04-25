package ro.ainpc.world;

public enum WorldNodeType {
    NPC_SPAWN("npc_spawn"),
    QUEST_TRIGGER("quest_trigger"),
    BOSS("boss"),
    INTERACTION("interaction"),
    PROGRESSION("progression"),
    CUSTOM("custom");

    private final String id;

    WorldNodeType(String id) {
        this.id = id;
    }

    public String getId() {
        return id;
    }

    public static WorldNodeType fromId(String value) {
        if (value == null || value.isBlank()) {
            return CUSTOM;
        }

        for (WorldNodeType type : values()) {
            if (type.id.equalsIgnoreCase(value) || type.name().equalsIgnoreCase(value)) {
                return type;
            }
        }

        return CUSTOM;
    }
}
