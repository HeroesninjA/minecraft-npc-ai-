package ro.ainpc.topology;

import java.util.List;
import java.util.Locale;

/**
 * Categorie coerenta pentru topologia mediului in care se afla NPC-ul.
 * Reduce dependenta de biome-uri brute si ofera o clasificare stabila pentru AI si feature packs.
 */
public enum TopologyCategory {
    INTERIOR("interior", "Interior", "spatiu inchis si protejat", List.of()),
    UNDERGROUND("underground", "Subteran", "zona subterana, stramta sau sapata in roca",
        List.of("CAVE", "DEEP_DARK", "DRIPSTONE", "LUSH")),
    SNOW("snow", "Zona rece", "tinut rece, inzapezit sau inghetat",
        List.of("SNOW", "FROZEN", "ICE", "JAGGED_PEAKS", "FROZEN_PEAKS", "ICE_SPIKES")),
    DESERT("desert", "Zona arida", "teren uscat, cald si expus",
        List.of("DESERT", "BADLANDS", "ERODED", "SAVANNA")),
    JUNGLE("jungle", "Jungla", "vegetatie densa, umeda si salbatica",
        List.of("JUNGLE", "BAMBOO")),
    SWAMP("swamp", "Mlastina", "teren umed, greu de traversat",
        List.of("SWAMP", "MANGROVE")),
    TAIGA("taiga", "Taiga", "zona rece cu conifere si relief moale",
        List.of("TAIGA", "GROVE")),
    MOUNTAIN("mountain", "Munte", "teren inalt, abrupt si expus",
        List.of("MOUNTAIN", "WINDSWEPT", "PEAK", "STONY")),
    DARK_FOREST("dark_forest", "Padure intunecata", "padure deasa si apasatoare",
        List.of("DARK_FOREST", "PALE_GARDEN")),
    FOREST("forest", "Padure", "zona impadurita, buna pentru vanatoare sau cules",
        List.of("FOREST", "BIRCH", "OLD_GROWTH", "WOODLAND")),
    COAST("coast", "Coasta", "margine de uscat aproape de apa mare",
        List.of("BEACH", "SHORE")),
    RIVER("river", "Raul", "mal de rau sau culoar de apa dulce",
        List.of("RIVER")),
    OCEAN("ocean", "Ocean", "apa intinsa, adanca sau deschisa",
        List.of("OCEAN")),
    PLAINS("plains", "Camp deschis", "teren deschis, bun pentru asezari si agricultura",
        List.of("PLAINS", "MEADOW", "SUNFLOWER", "CHERRY")),
    NETHER("nether", "Nether", "mediu ostil, fierbinte si nenatural",
        List.of("NETHER", "CRIMSON", "WARPED", "BASALT", "SOUL_SAND")),
    END("end", "End", "mediu alienat, gol si nelinistitor",
        List.of("THE_END", "END")),
    UNKNOWN("unknown", "Necunoscuta", "mediu greu de clasificat", List.of());

    private final String id;
    private final String displayName;
    private final String description;
    private final List<String> biomeTokens;

    TopologyCategory(String id, String displayName, String description, List<String> biomeTokens) {
        this.id = id;
        this.displayName = displayName;
        this.description = description;
        this.biomeTokens = biomeTokens;
    }

    public String getId() {
        return id;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getDescription() {
        return description;
    }

    public List<String> getBiomeTokens() {
        return biomeTokens;
    }

    public static TopologyCategory fromBiome(String biome, boolean indoors) {
        if (indoors) {
            return INTERIOR;
        }

        if (biome == null || biome.isBlank()) {
            return UNKNOWN;
        }

        String normalized = biome.toUpperCase(Locale.ROOT);

        for (TopologyCategory category : values()) {
            if (category == INTERIOR || category == UNKNOWN) {
                continue;
            }

            for (String token : category.biomeTokens) {
                if (normalized.contains(token)) {
                    return category;
                }
            }
        }

        return UNKNOWN;
    }

    public static TopologyCategory fromId(String id) {
        if (id == null || id.isBlank()) {
            return UNKNOWN;
        }

        for (TopologyCategory category : values()) {
            if (category.id.equalsIgnoreCase(id) || category.name().equalsIgnoreCase(id)) {
                return category;
            }
        }

        return UNKNOWN;
    }
}
