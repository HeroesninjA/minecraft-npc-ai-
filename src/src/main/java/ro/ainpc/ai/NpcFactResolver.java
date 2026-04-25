package ro.ainpc.ai;

import ro.ainpc.npc.AINPC;
import ro.ainpc.npc.NPCContext;
import ro.ainpc.npc.NPCState;
import ro.ainpc.topology.TopologyCategory;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Rezolva intrebarile factuale despre NPC fara a depinde de modelul AI.
 */
public final class NpcFactResolver {

    private NpcFactResolver() {
    }

    public static Optional<String> resolve(String playerMessage, NpcFacts facts) {
        if (playerMessage == null || playerMessage.isBlank() || facts == null) {
            return Optional.empty();
        }

        List<FactIntent> intents = detectIntents(playerMessage);
        if (intents.isEmpty()) {
            return Optional.empty();
        }

        List<String> answers = new ArrayList<>();
        for (FactIntent intent : intents) {
            String answer = resolveIntent(intent, facts);
            if (answer != null && !answer.isBlank()) {
                answers.add(answer);
            }
        }

        if (answers.isEmpty()) {
            return Optional.of("Nu stiu sigur.");
        }

        return Optional.of(joinNaturally(answers));
    }

    public static String describeCurrentActivity(String occupation, NPCState state) {
        String safeOccupation = occupation == null ? "" : occupation.trim();

        if (state == null) {
            return safeOccupation.isBlank()
                ? "astept putin"
                : "imi vad de treburile mele de " + safeOccupation;
        }

        return switch (state) {
            case IDLE, WAITING -> safeOccupation.isBlank()
                ? "astept putin"
                : "imi vad de treburile mele de " + safeOccupation;
            case WALKING -> "merg";
            case RUNNING -> "alerg";
            case TALKING -> "vorbesc";
            case LISTENING -> "ascult";
            case TRADING -> "fac schimburi";
            case WORKING, CRAFTING -> safeOccupation.isBlank()
                ? "lucrez"
                : "lucrez la treburile mele de " + safeOccupation;
            case FARMING -> "lucrez pamantul";
            case MINING -> "minez";
            case FISHING -> "pescuiesc";
            case SOCIALIZING -> "socializez";
            case CELEBRATING -> "sarbatoresc";
            case MOURNING -> "jelesc";
            case ARGUING -> "ma cert";
            case COMBAT -> "lupt";
            case FLEEING -> "fug";
            case GUARDING -> "pazesc zona";
            case PATROLLING -> "patrulez prin zona";
            case SLEEPING -> "dorm";
            case RESTING -> "ma odihnesc";
            case EATING -> "mananc";
            case DRINKING -> "beau";
            case PANICKING -> "incerc sa nu intru in panica";
            case CURIOUS -> "incerc sa aflu ce se intampla";
            case HIDING -> "ma ascund";
            case PRAYING -> "ma rog";
            case QUEST_GIVING -> "caut pe cineva care sa ma ajute";
            case FOLLOWING -> "urmez pe cineva";
        };
    }

    public static String describeLocation(AINPC npc, NPCContext context) {
        if (context != null) {
            TopologyCategory topologyCategory = context.getTopologyCategory();
            if (topologyCategory != null && topologyCategory != TopologyCategory.UNKNOWN) {
                String topology = topologyCategory.getDisplayName().toLowerCase(Locale.ROOT);
                if (topologyCategory == TopologyCategory.INTERIOR) {
                    return "interior";
                }
                return "zona de " + topology;
            }
        }

        if (npc != null && npc.getWorldName() != null && !npc.getWorldName().isBlank()) {
            return npc.getWorldName();
        }

        return "";
    }

    private static List<FactIntent> detectIntents(String playerMessage) {
        String normalizedMessage = normalize(playerMessage);
        Map<FactIntent, Integer> matches = new EnumMap<>(FactIntent.class);

        registerMatch(matches, FactIntent.NAME, normalizedMessage,
            "cine esti", "cum te cheama", "cum te numesti", "numele tau", "ce nume ai");
        registerMatch(matches, FactIntent.PROFESSION, normalizedMessage,
            "ce meserie ai", "ce profesie ai", "care e meseria ta", "care e profesia ta",
            "ce ocupatie ai", "cu ce te ocupi");
        registerMatch(matches, FactIntent.STATE, normalizedMessage,
            "cum te simti", "ce stare ai", "cum iti este");
        registerMatch(matches, FactIntent.ACTIVITY, normalizedMessage,
            "ce faci", "ce faci acum", "ce lucrezi", "la ce lucrezi", "ce muncesti", "cu ce te ocupi acum");
        registerMatch(matches, FactIntent.LOCATION, normalizedMessage,
            "unde esti", "unde te afli", "in ce loc esti", "unde te gasesc");

        return matches.entrySet().stream()
            .sorted(Comparator.comparingInt(Map.Entry::getValue))
            .map(Map.Entry::getKey)
            .toList();
    }

    private static void registerMatch(Map<FactIntent, Integer> matches,
                                      FactIntent intent,
                                      String message,
                                      String... patterns) {
        int earliestIndex = Integer.MAX_VALUE;
        for (String pattern : patterns) {
            int index = message.indexOf(pattern);
            if (index >= 0 && index < earliestIndex) {
                earliestIndex = index;
            }
        }

        if (earliestIndex != Integer.MAX_VALUE) {
            matches.merge(intent, earliestIndex, Math::min);
        }
    }

    private static String resolveIntent(FactIntent intent, NpcFacts facts) {
        return switch (intent) {
            case NAME -> facts.npcName() == null || facts.npcName().isBlank()
                ? null
                : "Sunt " + facts.npcName() + ".";
            case PROFESSION -> facts.occupation() == null || facts.occupation().isBlank()
                ? null
                : "Sunt " + facts.occupation() + ".";
            case STATE -> buildStateAnswer(facts);
            case ACTIVITY -> facts.currentActivity() == null || facts.currentActivity().isBlank()
                ? null
                : capitalizeSentence(facts.currentActivity()) + ".";
            case LOCATION -> facts.locationDescription() == null || facts.locationDescription().isBlank()
                ? null
                : "Sunt in " + facts.locationDescription() + ".";
        };
    }

    private static String buildStateAnswer(NpcFacts facts) {
        if (facts.emotionalState() != null && !facts.emotionalState().isBlank()) {
            return "Ma simt " + facts.emotionalState() + ".";
        }

        if (facts.currentState() != null && !facts.currentState().isBlank()) {
            return "Acum sunt " + facts.currentState().toLowerCase(Locale.ROOT) + ".";
        }

        return null;
    }

    private static String joinNaturally(List<String> answers) {
        List<String> cleanAnswers = answers.stream()
            .map(NpcFactResolver::stripTrailingPeriod)
            .toList();

        if (cleanAnswers.size() == 1) {
            return cleanAnswers.get(0) + ".";
        }

        if (cleanAnswers.size() == 2) {
            return cleanAnswers.get(0) + " si " + lowerCaseFirst(cleanAnswers.get(1)) + ".";
        }

        String last = cleanAnswers.get(cleanAnswers.size() - 1);
        String prefix = String.join(", ", cleanAnswers.subList(0, cleanAnswers.size() - 1));
        return prefix + " si " + lowerCaseFirst(last) + ".";
    }

    private static String stripTrailingPeriod(String text) {
        if (text == null) {
            return "";
        }

        String trimmed = text.trim();
        while (trimmed.endsWith(".")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1).trim();
        }
        return trimmed;
    }

    private static String lowerCaseFirst(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }

        return Character.toLowerCase(text.charAt(0)) + text.substring(1);
    }

    private static String capitalizeSentence(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }

        String trimmed = text.trim();
        return Character.toUpperCase(trimmed.charAt(0)) + trimmed.substring(1);
    }

    private static String normalize(String value) {
        String normalized = Normalizer.normalize(value.toLowerCase(Locale.ROOT), Normalizer.Form.NFD);
        return normalized.replaceAll("\\p{M}+", "");
    }

    private enum FactIntent {
        NAME,
        PROFESSION,
        STATE,
        ACTIVITY,
        LOCATION
    }

    public record NpcFacts(
        String npcName,
        String occupation,
        String emotionalState,
        String currentState,
        String currentActivity,
        String locationDescription
    ) {
    }
}
