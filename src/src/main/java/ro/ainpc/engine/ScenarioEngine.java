package ro.ainpc.engine;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import ro.ainpc.AINPCPlugin;
import ro.ainpc.npc.AINPC;
import ro.ainpc.npc.NPCEmotions;
import ro.ainpc.npc.NPCPersonality;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Motor de scenarii emergente.
 * Scenariile built-in pot fi suprascrise de scenarii definite in addon packs.
 */
public class ScenarioEngine {

    private final AINPCPlugin plugin;
    private final Map<UUID, ActiveScenario> activeScenarios;
    private final Map<ScenarioType, ScenarioTemplate> scenarioTemplates;

    public ScenarioEngine(AINPCPlugin plugin) {
        this.plugin = plugin;
        this.activeScenarios = new ConcurrentHashMap<>();
        this.scenarioTemplates = new EnumMap<>(ScenarioType.class);

        loadScenarioTemplates();
    }

    public void reloadTemplates() {
        loadScenarioTemplates();
    }

    /**
     * Incarca template-urile implicite si apoi aplica override-urile din addon packs.
     */
    private void loadScenarioTemplates() {
        scenarioTemplates.clear();

        ScenarioTemplate theft = new ScenarioTemplate(ScenarioType.THEFT);
        theft.addRole("THIEF", "Hotul care fura");
        theft.addRole("VICTIM", "Victima furtului");
        theft.addRole("WITNESS", "Martor la furt", true);
        theft.addRole("GUARD", "Garda care intervine", true);
        theft.addPhase("PLANNING", "Hotul planuieste furtul");
        theft.addPhase("EXECUTION", "Furtul are loc");
        theft.addPhase("DISCOVERY", "Victima descopera furtul");
        theft.addPhase("CONFLICT", "Confruntare intre parti");
        theft.addPhase("RESOLUTION", "Rezolvare - garda intervine sau hotul fuge");
        theft.setTriggerProbability(0.05);
        theft.setMinimumNpcCount(2);
        scenarioTemplates.put(ScenarioType.THEFT, theft);

        ScenarioTemplate conflict = new ScenarioTemplate(ScenarioType.CONFLICT);
        conflict.addRole("AGGRESSOR", "Cel care incepe conflictul");
        conflict.addRole("DEFENDER", "Cel care se apara");
        conflict.addRole("MEDIATOR", "Cel care incearca sa medieze", true);
        conflict.addRole("SPECTATOR", "Spectatori", true);
        conflict.addPhase("TENSION", "Tensiune initiala");
        conflict.addPhase("ARGUMENT", "Cearta verbala");
        conflict.addPhase("ESCALATION", "Escaladare optionala");
        conflict.addPhase("RESOLUTION", "Rezolvare - pace sau lupta");
        conflict.setTriggerProbability(0.08);
        conflict.setMinimumNpcCount(2);
        scenarioTemplates.put(ScenarioType.CONFLICT, conflict);

        ScenarioTemplate celebration = new ScenarioTemplate(ScenarioType.CELEBRATION);
        celebration.addRole("HOST", "Gazda sarbatorii");
        celebration.addRole("GUEST", "Invitati", true);
        celebration.addRole("ENTERTAINER", "Cel care anima atmosfera", true);
        celebration.addPhase("GATHERING", "Lumea se strange");
        celebration.addPhase("CELEBRATION", "Sarbatoarea propriu-zisa");
        celebration.addPhase("PEAK", "Momentul culminant");
        celebration.addPhase("ENDING", "Sfarsitul sarbatorii");
        celebration.setTriggerProbability(0.03);
        celebration.setMinimumNpcCount(2);
        scenarioTemplates.put(ScenarioType.CELEBRATION, celebration);

        ScenarioTemplate emergency = new ScenarioTemplate(ScenarioType.EMERGENCY);
        emergency.addRole("VICTIM", "Cel in pericol");
        emergency.addRole("HELPER", "Cel care ajuta");
        emergency.addRole("COWARD", "Cel care fuge", true);
        emergency.addRole("LEADER", "Cel care organizeaza", true);
        emergency.addPhase("ALERT", "Alerta initiala");
        emergency.addPhase("PANIC", "Panica generala");
        emergency.addPhase("RESPONSE", "Raspunsul comunitatii");
        emergency.addPhase("RESOLUTION", "Rezolvare");
        emergency.setTriggerProbability(0.02);
        emergency.setMinimumNpcCount(2);
        scenarioTemplates.put(ScenarioType.EMERGENCY, emergency);

        ScenarioTemplate romance = new ScenarioTemplate(ScenarioType.ROMANCE);
        romance.addRole("SUITOR", "Curtezanul");
        romance.addRole("BELOVED", "Persoana iubita");
        romance.addRole("RIVAL", "Rival in dragoste", true);
        romance.addRole("CONFIDANT", "Prieten confident", true);
        romance.addPhase("ATTRACTION", "Atractie initiala");
        romance.addPhase("COURTSHIP", "Curte");
        romance.addPhase("COMPLICATION", "Complicatii");
        romance.addPhase("RESOLUTION", "Rezolvare");
        romance.setTriggerProbability(0.04);
        romance.setMinimumNpcCount(2);
        scenarioTemplates.put(ScenarioType.ROMANCE, romance);

        ScenarioTemplate tradeDeal = new ScenarioTemplate(ScenarioType.TRADE_DEAL);
        tradeDeal.addRole("SELLER", "Vanzatorul");
        tradeDeal.addRole("BUYER", "Cumparatorul");
        tradeDeal.addRole("COMPETITOR", "Competitor", true);
        tradeDeal.addPhase("NEGOTIATION", "Negociere");
        tradeDeal.addPhase("BARGAINING", "Tocmeala");
        tradeDeal.addPhase("AGREEMENT", "Acord sau esec");
        tradeDeal.setTriggerProbability(0.10);
        tradeDeal.setMinimumNpcCount(2);
        scenarioTemplates.put(ScenarioType.TRADE_DEAL, tradeDeal);

        ScenarioTemplate quest = new ScenarioTemplate(ScenarioType.QUEST);
        quest.addRole("QUEST_GIVER", "Cel care da misiunea");
        quest.addPlayerRole("HERO", "Eroul (jucatorul)");
        quest.addRole("HELPER", "Ajutor pentru erou", true);
        quest.addRole("ANTAGONIST", "Antagonistul", true);
        quest.addPhase("INTRODUCTION", "Prezentarea problemei");
        quest.addPhase("ACCEPTANCE", "Acceptarea misiunii");
        quest.addPhase("JOURNEY", "Calatoria/actiunea");
        quest.addPhase("COMPLETION", "Finalizare si recompensa");
        quest.setTriggerProbability(0.06);
        quest.setMinimumNpcCount(1);
        quest.setRequiresPlayer(true);
        scenarioTemplates.put(ScenarioType.QUEST, quest);

        ScenarioTemplate gossip = new ScenarioTemplate(ScenarioType.GOSSIP_SPREAD);
        gossip.addRole("ORIGINATOR", "Sursa zvonului");
        gossip.addRole("SPREADER", "Cel care raspandeste");
        gossip.addRole("SUBJECT", "Subiectul zvonului", true);
        gossip.addRole("SKEPTIC", "Cel care nu crede", true);
        gossip.addPhase("ORIGIN", "Nasterea zvonului");
        gossip.addPhase("SPREAD", "Raspandirea");
        gossip.addPhase("DISCOVERY", "Subiectul afla");
        gossip.addPhase("CONFRONTATION", "Confruntare");
        gossip.setTriggerProbability(0.07);
        gossip.setMinimumNpcCount(2);
        scenarioTemplates.put(ScenarioType.GOSSIP_SPREAD, gossip);

        loadAddonScenarioTemplates();
    }

    private void loadAddonScenarioTemplates() {
        FeaturePackLoader featurePackLoader = plugin.getFeaturePackLoader();
        if (featurePackLoader == null) {
            return;
        }

        FeaturePackLoader.FeaturePack primaryScenarioPack = featurePackLoader.getPrimaryScenarioPack();

        for (FeaturePackLoader.FeaturePack pack : featurePackLoader.getLoadedPacks()) {
            for (FeaturePackLoader.ScenarioDefinition definition : pack.getScenarios()) {
                ScenarioTemplate template = new ScenarioTemplate(definition.getBaseType());
                template.setTemplateId(pack.getId() + ":" + definition.getId());
                template.setDisplayName(definition.getName());
                template.setDescription(definition.getDescription());
                template.setSourcePackId(pack.getId());
                template.setHint(definition.getHint());
                template.setTriggerProbability(definition.getTriggerProbability());
                template.setMinimumNpcCount(definition.getMinimumNpcCount());
                template.setRequiresPlayer(definition.isRequiresPlayer());
                template.setPreferredTopologies(definition.getPreferredTopologies());
                template.setNarrativeHints(definition.getNarrativeHints());

                for (FeaturePackLoader.ScenarioRoleDefinition roleDefinition : definition.getRoles().values()) {
                    ScenarioRoleRule role = new ScenarioRoleRule(
                        roleDefinition.getId(),
                        roleDefinition.getDescription(),
                        roleDefinition.isPlayerRole(),
                        roleDefinition.isOptional()
                    );
                    role.setPreferredProfessions(roleDefinition.getPreferredProfessions());
                    role.setRequiredTraits(roleDefinition.getRequiredTraits());
                    role.setPreferredTraits(roleDefinition.getPreferredTraits());
                    template.addRole(role);
                }

                for (String phase : definition.getPhases()) {
                    template.addPhase(phase, phase);
                }

                boolean shouldReplace = definition.isReplaceBaseType()
                    || primaryScenarioPack != null && primaryScenarioPack.getId().equalsIgnoreCase(pack.getId())
                    || !scenarioTemplates.containsKey(definition.getBaseType());

                if (shouldReplace) {
                    scenarioTemplates.put(definition.getBaseType(), template);
                    plugin.getLogger().info("Scenariu addon incarcat: " + template.getDisplayName()
                        + " (" + template.getTemplateId() + ")");
                }
            }
        }
    }

    /**
     * Evalueaza daca ar trebui sa inceapa un scenariu nou.
     */
    public void evaluateScenarioTriggers(List<AINPC> npcs, List<Player> nearbyPlayers) {
        if (npcs.isEmpty()) {
            return;
        }

        Random random = new Random();

        for (ScenarioTemplate template : scenarioTemplates.values()) {
            boolean hasActiveOfType = activeScenarios.values().stream()
                .anyMatch(scenario -> scenario.getType() == template.getType());
            if (hasActiveOfType) {
                continue;
            }

            if (random.nextDouble() < template.getTriggerProbability()
                && canTriggerScenario(template, npcs, nearbyPlayers)) {
                startScenario(template, npcs, nearbyPlayers);
            }
        }
    }

    /**
     * Verifica daca un scenariu poate fi declansat.
     */
    private boolean canTriggerScenario(ScenarioTemplate template, List<AINPC> npcs, List<Player> players) {
        if (template.requiresPlayer() && players.isEmpty()) {
            return false;
        }

        if (npcs.size() < template.getMinimumNpcCount()) {
            return false;
        }

        return switch (template.getType()) {
            case ROMANCE -> hasMixedGenders(npcs);
            case CONFLICT -> hasConflictingPersonalities(npcs);
            default -> true;
        };
    }

    private boolean hasMixedGenders(List<AINPC> npcs) {
        boolean hasMale = npcs.stream().anyMatch(npc -> "male".equalsIgnoreCase(npc.getGender()));
        boolean hasFemale = npcs.stream().anyMatch(npc -> "female".equalsIgnoreCase(npc.getGender()));
        return hasMale && hasFemale;
    }

    /**
     * Verifica daca exista personalitati conflictuale.
     */
    private boolean hasConflictingPersonalities(List<AINPC> npcs) {
        for (AINPC npc1 : npcs) {
            for (AINPC npc2 : npcs) {
                if (npc1 == npc2) {
                    continue;
                }

                NPCPersonality p1 = npc1.getPersonality();
                NPCPersonality p2 = npc2.getPersonality();

                if (Math.abs(p1.getAgreeableness() - p2.getAgreeableness()) > 0.5) {
                    return true;
                }

                if (p1.getNeuroticism() > 0.7 && p2.getNeuroticism() > 0.7) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Porneste un scenariu nou.
     */
    private void startScenario(ScenarioTemplate template, List<AINPC> npcs, List<Player> players) {
        UUID scenarioId = UUID.randomUUID();
        ActiveScenario scenario = new ActiveScenario(scenarioId, template);

        assignRoles(scenario, template, npcs, players);

        if (!template.getPhases().isEmpty()) {
            scenario.setCurrentPhase(template.getPhases().get(0));
        }

        activeScenarios.put(scenarioId, scenario);

        plugin.getLogger().info("Scenariu nou pornit: " + template.getDisplayName()
            + " (ID: " + scenarioId.toString().substring(0, 8) + ")");

        notifyParticipants(scenario);
    }

    /**
     * Asigneaza roluri NPC-urilor si jucatorilor.
     */
    private void assignRoles(ActiveScenario scenario,
                             ScenarioTemplate template,
                             List<AINPC> npcs,
                             List<Player> players) {
        List<AINPC> availableNpcs = new ArrayList<>(npcs);
        List<Player> availablePlayers = new ArrayList<>(players);
        Random random = new Random();

        for (ScenarioRoleRule role : template.getPlayerRoles()) {
            if (availablePlayers.isEmpty()) {
                if (!role.isOptional()) {
                    plugin.debug("Scenariul " + template.getDisplayName() + " nu are jucator pentru rolul " + role.getId());
                }
                continue;
            }

            Player player = availablePlayers.remove(0);
            scenario.assignPlayerRole(player.getUniqueId(), role.getId());
        }

        List<ScenarioRoleRule> mandatoryFallback = new ArrayList<>();
        List<ScenarioRoleRule> optionalFallback = new ArrayList<>();

        for (ScenarioRoleRule role : template.getNpcRoles()) {
            AINPC selected = selectBestNpcForRole(availableNpcs, role);
            if (selected != null) {
                scenario.assignNPCRole(selected.getUuid(), role.getId());
                availableNpcs.remove(selected);
                continue;
            }

            if (role.isOptional()) {
                optionalFallback.add(role);
            } else {
                mandatoryFallback.add(role);
            }
        }

        assignFallbackRoles(scenario, mandatoryFallback, availableNpcs, random);
        assignFallbackRoles(scenario, optionalFallback, availableNpcs, random);
    }

    private void assignFallbackRoles(ActiveScenario scenario,
                                     List<ScenarioRoleRule> roles,
                                     List<AINPC> availableNpcs,
                                     Random random) {
        for (ScenarioRoleRule role : roles) {
            if (availableNpcs.isEmpty()) {
                return;
            }

            AINPC randomNpc = availableNpcs.remove(random.nextInt(availableNpcs.size()));
            scenario.assignNPCRole(randomNpc.getUuid(), role.getId());
        }
    }

    private AINPC selectBestNpcForRole(List<AINPC> candidates, ScenarioRoleRule role) {
        if (candidates.isEmpty()) {
            return null;
        }

        AINPC bestNpc = null;
        int bestScore = Integer.MIN_VALUE;

        for (AINPC npc : candidates) {
            int score = scoreNpcForRole(npc, role);
            if (score > bestScore) {
                bestNpc = npc;
                bestScore = score;
            }
        }

        return bestScore == Integer.MIN_VALUE ? null : bestNpc;
    }

    private int scoreNpcForRole(AINPC npc, ScenarioRoleRule role) {
        if (!hasRequiredTraits(npc, role.getRequiredTraits())) {
            return Integer.MIN_VALUE;
        }

        int score = baseRoleScore(npc, role.getId());

        if (!role.getPreferredProfessions().isEmpty()) {
            boolean professionMatch = role.getPreferredProfessions().stream()
                .anyMatch(reference -> plugin.getFeaturePackLoader() != null
                    && plugin.getFeaturePackLoader().matchesProfession(npc.getOccupation(), reference));
            score += professionMatch ? 90 : -15;
        }

        for (String preferredTrait : role.getPreferredTraits()) {
            if (npc.hasTrait(preferredTrait)) {
                score += 25;
            }
        }

        return score;
    }

    private boolean hasRequiredTraits(AINPC npc, List<String> requiredTraits) {
        if (requiredTraits == null || requiredTraits.isEmpty()) {
            return true;
        }

        for (String requiredTrait : requiredTraits) {
            if (!npc.hasTrait(requiredTrait)) {
                return false;
            }
        }

        return true;
    }

    private int baseRoleScore(AINPC npc, String roleId) {
        NPCPersonality personality = npc.getPersonality();
        NPCEmotions emotions = npc.getEmotions();

        return switch (roleId) {
            case "THIEF" -> scoreBoolean(personality.getConscientiousness() < 0.4
                && personality.getAgreeableness() < 0.5, 40);
            case "GUARD" -> scoreBoolean(matchesOccupation(npc, "guard", "soldier", "garda"), 45);
            case "AGGRESSOR" -> scoreBoolean(personality.getAgreeableness() < 0.4
                || emotions.getAnger() > 0.5, 35);
            case "MEDIATOR" -> scoreBoolean(personality.getAgreeableness() > 0.6
                && personality.getExtraversion() > 0.5, 35);
            case "COWARD" -> scoreBoolean(personality.getNeuroticism() > 0.6
                || emotions.getFear() > 0.5, 35);
            case "LEADER" -> scoreBoolean(personality.getExtraversion() > 0.6
                && personality.getConscientiousness() > 0.5, 35);
            case "HOST" -> scoreBoolean(personality.getExtraversion() > 0.5
                && personality.getAgreeableness() > 0.5, 30);
            case "SUITOR" -> scoreBoolean(personality.getExtraversion() > 0.5, 25);
            case "ORIGINATOR" -> scoreBoolean(personality.getOpenness() > 0.6, 25);
            case "QUEST_GIVER" -> scoreBoolean(personality.getAgreeableness() > 0.45
                || personality.getExtraversion() > 0.45, 30);
            case "HELPER" -> scoreBoolean(personality.getAgreeableness() > 0.5
                || emotions.getTrust() > 0.55, 28);
            case "ANTAGONIST" -> scoreBoolean(personality.getAgreeableness() < 0.45
                || emotions.getAnger() > 0.45, 28);
            case "WITNESS" -> scoreBoolean(personality.getOpenness() > 0.45
                || personality.getExtraversion() > 0.45, 20);
            case "SELLER" -> scoreBoolean(matchesOccupation(npc, "merchant", "negustor"), 35);
            case "BUYER" -> 10;
            default -> 0;
        };
    }

    private boolean matchesOccupation(AINPC npc, String... references) {
        if (references == null || references.length == 0) {
            return false;
        }

        String occupation = npc.getOccupation();
        if (occupation == null || occupation.isBlank()) {
            return false;
        }

        FeaturePackLoader loader = plugin.getFeaturePackLoader();
        for (String reference : references) {
            if (loader != null && loader.matchesProfession(occupation, reference)) {
                return true;
            }

            if (normalize(occupation).equals(normalize(reference))) {
                return true;
            }
        }

        return false;
    }

    private int scoreBoolean(boolean condition, int positiveScore) {
        return condition ? positiveScore : 0;
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    /**
     * Notifica participantii la scenariu.
     */
    private void notifyParticipants(ActiveScenario scenario) {
        for (Map.Entry<UUID, String> entry : scenario.getNpcRoles().entrySet()) {
            AINPC npc = plugin.getNpcManager().getNPCByUUID(entry.getKey());
            if (npc != null) {
                adjustEmotionsForRole(npc, entry.getValue(), scenario.getType());
            }
        }

        for (Map.Entry<UUID, String> entry : scenario.getPlayerRoles().entrySet()) {
            Player player = Bukkit.getPlayer(entry.getKey());
            if (player != null) {
                sendScenarioHint(player, scenario);
            }
        }
    }

    /**
     * Ajusteaza emotiile NPC-ului bazat pe rol.
     */
    private void adjustEmotionsForRole(AINPC npc, String role, ScenarioType type) {
        NPCEmotions emotions = npc.getEmotions();

        switch (role) {
            case "THIEF" -> emotions.adjustFear(0.3);
            case "VICTIM" -> {
                emotions.adjustAnger(0.4);
                emotions.adjustSadness(0.3);
            }
            case "AGGRESSOR", "ANTAGONIST" -> emotions.adjustAnger(0.5);
            case "HOST", "QUEST_GIVER" -> emotions.adjustHappiness(0.3);
            case "HELPER" -> {
                emotions.applyEmotion("trust", 0.7);
                emotions.applyEmotion("anticipation", 0.6);
            }
            case "SUITOR" -> {
                emotions.adjustHappiness(0.3);
                emotions.adjustFear(0.2);
            }
            default -> {
            }
        }
    }

    /**
     * Trimite un indiciu jucatorului despre scenariu.
     */
    private void sendScenarioHint(Player player, ActiveScenario scenario) {
        String hint = scenario.getHint();
        if (hint == null || hint.isBlank()) {
            hint = switch (scenario.getType()) {
                case QUEST -> "Simti ca cineva are nevoie de ajutorul tau...";
                case CONFLICT -> "Tensiunea din aer e palpabila...";
                case CELEBRATION -> "Se aude muzica si rasete in apropiere!";
                case EMERGENCY -> "Ceva nu e in regula...";
                case ROMANCE -> "Aerul e plin de emotie...";
                default -> "";
            };
        }

        if (!hint.isEmpty()) {
            player.sendMessage("§7§o" + hint);
        }
    }

    /**
     * Avanseaza un scenariu la urmatoarea faza.
     */
    public void advanceScenario(UUID scenarioId) {
        ActiveScenario scenario = activeScenarios.get(scenarioId);
        if (scenario == null) {
            return;
        }

        ScenarioTemplate template = scenarioTemplates.get(scenario.getType());
        if (template == null) {
            return;
        }

        List<String> phases = template.getPhases();
        int currentIndex = phases.indexOf(scenario.getCurrentPhase());

        if (currentIndex < phases.size() - 1) {
            scenario.setCurrentPhase(phases.get(currentIndex + 1));
            plugin.debug("Scenariu " + scenarioId.toString().substring(0, 8)
                + " avansat la faza: " + scenario.getCurrentPhase());
        } else {
            endScenario(scenarioId);
        }
    }

    /**
     * Termina un scenariu.
     */
    public void endScenario(UUID scenarioId) {
        ActiveScenario scenario = activeScenarios.remove(scenarioId);
        if (scenario == null) {
            return;
        }

        createScenarioMemories(scenario);

        plugin.getLogger().info("Scenariu terminat: " + scenario.getDisplayName()
            + " (ID: " + scenarioId.toString().substring(0, 8) + ")");
    }

    /**
     * Creeaza amintiri despre scenariu pentru participanti.
     */
    private void createScenarioMemories(ActiveScenario scenario) {
        for (UUID npcId : scenario.getNpcRoles().keySet()) {
            plugin.getMemoryManager().addScenarioMemory(
                npcId,
                scenario.getType().name(),
                scenario.getNpcRoles().get(npcId)
            );
        }
    }

    public Map<UUID, ActiveScenario> getActiveScenarios() {
        return new HashMap<>(activeScenarios);
    }

    public ActiveScenario getNPCScenario(UUID npcId) {
        for (ActiveScenario scenario : activeScenarios.values()) {
            if (scenario.hasNPCRole(npcId)) {
                return scenario;
            }
        }
        return null;
    }

    public enum ScenarioType {
        THEFT("Furt"),
        CONFLICT("Conflict"),
        CELEBRATION("Sarbatoare"),
        EMERGENCY("Urgenta"),
        ROMANCE("Romantism"),
        TRADE_DEAL("Afacere"),
        QUEST("Misiune"),
        GOSSIP_SPREAD("Raspandirea zvonurilor");

        private final String displayName;

        ScenarioType(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }

        public static ScenarioType fromId(String value) {
            if (value == null || value.isBlank()) {
                return QUEST;
            }

            for (ScenarioType type : values()) {
                if (type.name().equalsIgnoreCase(value) || type.displayName.equalsIgnoreCase(value)) {
                    return type;
                }
            }

            return QUEST;
        }
    }

    public static class ScenarioTemplate {
        private final ScenarioType type;
        private final Map<String, ScenarioRoleRule> roles;
        private final List<String> phases;
        private String templateId;
        private String displayName;
        private String description;
        private String sourcePackId;
        private String hint;
        private List<String> preferredTopologies;
        private List<String> narrativeHints;
        private double triggerProbability;
        private int minimumNpcCount;
        private boolean requiresPlayer;

        public ScenarioTemplate(ScenarioType type) {
            this.type = type;
            this.roles = new LinkedHashMap<>();
            this.phases = new ArrayList<>();
            this.templateId = type.name().toLowerCase(Locale.ROOT);
            this.displayName = type.getDisplayName();
            this.description = "";
            this.sourcePackId = "core";
            this.hint = "";
            this.preferredTopologies = new ArrayList<>();
            this.narrativeHints = new ArrayList<>();
            this.triggerProbability = 0.05;
            this.minimumNpcCount = 2;
            this.requiresPlayer = false;
        }

        public void addRole(String roleId, String description) {
            addRole(roleId, description, false);
        }

        public void addRole(String roleId, String description, boolean optional) {
            addRole(new ScenarioRoleRule(roleId, description, false, optional));
        }

        public void addPlayerRole(String roleId, String description) {
            addRole(new ScenarioRoleRule(roleId, description, true, false));
        }

        public void addRole(ScenarioRoleRule role) {
            roles.put(role.getId(), role);
        }

        public void addPhase(String phaseId, String description) {
            phases.add(phaseId);
        }

        public ScenarioType getType() { return type; }
        public Map<String, ScenarioRoleRule> getRoles() { return roles; }
        public List<String> getPhases() { return phases; }
        public double getTriggerProbability() { return triggerProbability; }
        public void setTriggerProbability(double triggerProbability) { this.triggerProbability = triggerProbability; }
        public int getMinimumNpcCount() { return minimumNpcCount; }
        public void setMinimumNpcCount(int minimumNpcCount) { this.minimumNpcCount = minimumNpcCount; }
        public boolean requiresPlayer() { return requiresPlayer; }
        public void setRequiresPlayer(boolean requiresPlayer) { this.requiresPlayer = requiresPlayer; }
        public String getTemplateId() { return templateId; }
        public void setTemplateId(String templateId) { this.templateId = templateId; }
        public String getDisplayName() { return displayName; }
        public void setDisplayName(String displayName) { this.displayName = displayName; }
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
        public String getSourcePackId() { return sourcePackId; }
        public void setSourcePackId(String sourcePackId) { this.sourcePackId = sourcePackId; }
        public String getHint() { return hint; }
        public void setHint(String hint) { this.hint = hint == null ? "" : hint; }
        public List<String> getPreferredTopologies() { return preferredTopologies; }
        public void setPreferredTopologies(List<String> preferredTopologies) {
            this.preferredTopologies = preferredTopologies != null ? preferredTopologies : new ArrayList<>();
        }
        public List<String> getNarrativeHints() { return narrativeHints; }
        public void setNarrativeHints(List<String> narrativeHints) {
            this.narrativeHints = narrativeHints != null ? narrativeHints : new ArrayList<>();
        }

        public List<ScenarioRoleRule> getNpcRoles() {
            return roles.values().stream()
                .filter(role -> !role.isPlayerRole())
                .sorted(Comparator.comparing(ScenarioRoleRule::isOptional))
                .toList();
        }

        public List<ScenarioRoleRule> getPlayerRoles() {
            return roles.values().stream()
                .filter(ScenarioRoleRule::isPlayerRole)
                .toList();
        }
    }

    public static class ScenarioRoleRule {
        private final String id;
        private final String description;
        private final boolean playerRole;
        private final boolean optional;
        private List<String> preferredProfessions;
        private List<String> requiredTraits;
        private List<String> preferredTraits;

        public ScenarioRoleRule(String id, String description, boolean playerRole, boolean optional) {
            this.id = id;
            this.description = description;
            this.playerRole = playerRole;
            this.optional = optional;
            this.preferredProfessions = new ArrayList<>();
            this.requiredTraits = new ArrayList<>();
            this.preferredTraits = new ArrayList<>();
        }

        public String getId() { return id; }
        public String getDescription() { return description; }
        public boolean isPlayerRole() { return playerRole; }
        public boolean isOptional() { return optional; }
        public List<String> getPreferredProfessions() { return preferredProfessions; }
        public void setPreferredProfessions(List<String> preferredProfessions) {
            this.preferredProfessions = preferredProfessions != null ? preferredProfessions : Collections.emptyList();
        }
        public List<String> getRequiredTraits() { return requiredTraits; }
        public void setRequiredTraits(List<String> requiredTraits) {
            this.requiredTraits = requiredTraits != null ? requiredTraits : Collections.emptyList();
        }
        public List<String> getPreferredTraits() { return preferredTraits; }
        public void setPreferredTraits(List<String> preferredTraits) {
            this.preferredTraits = preferredTraits != null ? preferredTraits : Collections.emptyList();
        }
    }

    public static class ActiveScenario {
        private final UUID id;
        private final ScenarioType type;
        private final String templateId;
        private final String displayName;
        private final String hint;
        private final Map<UUID, String> npcRoles;
        private final Map<UUID, String> playerRoles;
        private String currentPhase;
        private final long startTime;

        public ActiveScenario(UUID id, ScenarioTemplate template) {
            this.id = id;
            this.type = template.getType();
            this.templateId = template.getTemplateId();
            this.displayName = template.getDisplayName();
            this.hint = template.getHint();
            this.npcRoles = new HashMap<>();
            this.playerRoles = new HashMap<>();
            this.startTime = System.currentTimeMillis();
        }

        public void assignNPCRole(UUID npcId, String role) {
            npcRoles.put(npcId, role);
        }

        public void assignPlayerRole(UUID playerId, String role) {
            playerRoles.put(playerId, role);
        }

        public boolean hasNPCRole(UUID npcId) {
            return npcRoles.containsKey(npcId);
        }

        public UUID getId() { return id; }
        public ScenarioType getType() { return type; }
        public String getTemplateId() { return templateId; }
        public String getDisplayName() { return displayName; }
        public String getHint() { return hint; }
        public Map<UUID, String> getNpcRoles() { return npcRoles; }
        public Map<UUID, String> getPlayerRoles() { return playerRoles; }
        public String getCurrentPhase() { return currentPhase; }
        public void setCurrentPhase(String currentPhase) { this.currentPhase = currentPhase; }
        public long getStartTime() { return startTime; }
    }
}
