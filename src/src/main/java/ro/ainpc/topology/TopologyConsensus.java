package ro.ainpc.topology;

import java.util.List;

public class TopologyConsensus {

    private final TopologyCategory category;
    private final List<String> descriptions;
    private final List<String> biomes;
    private final List<String> dialogueHints;
    private final List<String> suggestedTraits;
    private final List<String> sourcePacks;

    public TopologyConsensus(TopologyCategory category,
                             List<String> descriptions,
                             List<String> biomes,
                             List<String> dialogueHints,
                             List<String> suggestedTraits,
                             List<String> sourcePacks) {
        this.category = category;
        this.descriptions = descriptions;
        this.biomes = biomes;
        this.dialogueHints = dialogueHints;
        this.suggestedTraits = suggestedTraits;
        this.sourcePacks = sourcePacks;
    }

    public TopologyCategory getCategory() {
        return category;
    }

    public List<String> getDescriptions() {
        return descriptions;
    }

    public List<String> getBiomes() {
        return biomes;
    }

    public List<String> getDialogueHints() {
        return dialogueHints;
    }

    public List<String> getSuggestedTraits() {
        return suggestedTraits;
    }

    public List<String> getSourcePacks() {
        return sourcePacks;
    }

    public String toPromptBlock() {
        StringBuilder builder = new StringBuilder();
        builder.append("Topologie: ").append(category.getDisplayName()).append(".\n");

        if (!descriptions.isEmpty()) {
            builder.append("Consens mediu: ")
                .append(String.join(" ", descriptions.subList(0, Math.min(2, descriptions.size()))))
                .append("\n");
        }

        if (!dialogueHints.isEmpty()) {
            builder.append("Hint-uri dialog: ")
                .append(String.join(", ", dialogueHints.subList(0, Math.min(3, dialogueHints.size()))))
                .append("\n");
        }

        if (!suggestedTraits.isEmpty()) {
            builder.append("Trasaturi potrivite: ")
                .append(String.join(", ", suggestedTraits.subList(0, Math.min(4, suggestedTraits.size()))))
                .append("\n");
        }

        if (!biomes.isEmpty()) {
            builder.append("Biome relevante: ")
                .append(String.join(", ", biomes.subList(0, Math.min(5, biomes.size()))))
                .append("\n");
        }

        return builder.toString();
    }
}
