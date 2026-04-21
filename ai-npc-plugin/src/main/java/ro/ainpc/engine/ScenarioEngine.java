package ro.ainpc.engine;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import ro.ainpc.AINPCPlugin;
import ro.ainpc.npc.*;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Motor de scenarii emergente
 * Scenariile nu sunt complet scriptate - au reguli, roluri, intentii si probabilitati
 * Ex: furt -> conflict -> garda -> reactii -> memorie
 */
public class ScenarioEngine {

    private final AINPCPlugin plugin;
    
    // Scenarii active
    private final Map<UUID, ActiveScenario> activeScenarios;
    
    // Tipuri de scenarii disponibile
    private final Map<ScenarioType, ScenarioTemplate> scenarioTemplates;

    public ScenarioEngine(AINPCPlugin plugin) {
        this.plugin = plugin;
        this.activeScenarios = new ConcurrentHashMap<>();
        this.scenarioTemplates = new EnumMap<>(ScenarioType.class);
        
        loadScenarioTemplates();
    }

    /**
     * Incarca template-urile de scenarii
     */
    private void loadScenarioTemplates() {
        // THEFT - Furt
        ScenarioTemplate theft = new ScenarioTemplate(ScenarioType.THEFT);
        theft.addRole("THIEF", "Hotul care fura");
        theft.addRole("VICTIM", "Victima furtului");
        theft.addRole("WITNESS", "Martor la furt");
        theft.addRole("GUARD", "Garda care intervine");
        theft.addPhase("PLANNING", "Hotul planuieste furtul");
        theft.addPhase("EXECUTION", "Furtul are loc");
        theft.addPhase("DISCOVERY", "Victima descopera furtul");
        theft.addPhase("CONFLICT", "Confruntare intre parti");
        theft.addPhase("RESOLUTION", "Rezolvare - garda intervine sau hotul fuge");
        theft.setTriggerProbability(0.05);
        scenarioTemplates.put(ScenarioType.THEFT, theft);
        
        // CONFLICT - Conflict intre NPC-uri
        ScenarioTemplate conflict = new ScenarioTemplate(ScenarioType.CONFLICT);
        conflict.addRole("AGGRESSOR", "Cel care incepe conflictul");
        conflict.addRole("DEFENDER", "Cel care se apara");
        conflict.addRole("MEDIATOR", "Cel care incearca sa medieze");
        conflict.addRole("SPECTATOR", "Spectatori");
        conflict.addPhase("TENSION", "Tensiune initiala");
        conflict.addPhase("ARGUMENT", "Cearta verbala");
        conflict.addPhase("ESCALATION", "Escaladare optionala");
        conflict.addPhase("RESOLUTION", "Rezolvare - pace sau lupta");
        conflict.setTriggerProbability(0.08);
        scenarioTemplates.put(ScenarioType.CONFLICT, conflict);
        
        // CELEBRATION - Sarbatoare
        ScenarioTemplate celebration = new ScenarioTemplate(ScenarioType.CELEBRATION);
        celebration.addRole("HOST", "Gazda sarbatorii");
        celebration.addRole("GUEST", "Invitati");
        celebration.addRole("ENTERTAINER", "Cel care anima atmosfera");
        celebration.addPhase("GATHERING", "Lumea se strange");
        celebration.addPhase("CELEBRATION", "Sarbatoarea propriu-zisa");
        celebration.addPhase("PEAK", "Momentul culminant");
        celebration.addPhase("ENDING", "Sfarsitul sarbatorii");
        celebration.setTriggerProbability(0.03);
        scenarioTemplates.put(ScenarioType.CELEBRATION, celebration);
        
        // EMERGENCY - Urgenta (atac de mob-uri, incendiu, etc)
        ScenarioTemplate emergency = new ScenarioTemplate(ScenarioType.EMERGENCY);
        emergency.addRole("VICTIM", "Cel in pericol");
        emergency.addRole("HELPER", "Cel care ajuta");
        emergency.addRole("COWARD", "Cel care fuge");
        emergency.addRole("LEADER", "Cel care organizeaza");
        emergency.addPhase("ALERT", "Alerta initiala");
        emergency.addPhase("PANIC", "Panica generala");
        emergency.addPhase("RESPONSE", "Raspunsul comunitatii");
        emergency.addPhase("RESOLUTION", "Rezolvare");
        emergency.setTriggerProbability(0.02);
        scenarioTemplates.put(ScenarioType.EMERGENCY, emergency);
        
        // ROMANCE - Romantism
        ScenarioTemplate romance = new ScenarioTemplate(ScenarioType.ROMANCE);
        romance.addRole("SUITOR", "Curtezanul");
        romance.addRole("BELOVED", "Persoana iubita");
        romance.addRole("RIVAL", "Rival in dragoste");
        romance.addRole("CONFIDANT", "Prieten confident");
        romance.addPhase("ATTRACTION", "Atractie initiala");
        romance.addPhase("COURTSHIP", "Curte");
        romance.addPhase("COMPLICATION", "Complicatii");
        romance.addPhase("RESOLUTION", "Rezolvare");
        romance.setTriggerProbability(0.04);
        scenarioTemplates.put(ScenarioType.ROMANCE, romance);
        
        // TRADE_DEAL - Afacere comerciala
        ScenarioTemplate tradeDeal = new ScenarioTemplate(ScenarioType.TRADE_DEAL);
        tradeDeal.addRole("SELLER", "Vanzatorul");
        tradeDeal.addRole("BUYER", "Cumparatorul");
        tradeDeal.addRole("COMPETITOR", "Competitor");
        tradeDeal.addPhase("NEGOTIATION", "Negociere");
        tradeDeal.addPhase("BARGAINING", "Tocmeala");
        tradeDeal.addPhase("AGREEMENT", "Acord sau esec");
        tradeDeal.setTriggerProbability(0.10);
        scenarioTemplates.put(ScenarioType.TRADE_DEAL, tradeDeal);
        
        // QUEST - Misiune
        ScenarioTemplate quest = new ScenarioTemplate(ScenarioType.QUEST);
        quest.addRole("QUEST_GIVER", "Cel care da misiunea");
        quest.addRole("HERO", "Eroul (jucatorul)");
        quest.addRole("HELPER", "Ajutor pentru erou");
        quest.addRole("ANTAGONIST", "Antagonistul");
        quest.addPhase("INTRODUCTION", "Prezentarea problemei");
        quest.addPhase("ACCEPTANCE", "Acceptarea misiunii");
        quest.addPhase("JOURNEY", "Calatoria/actiunea");
        quest.addPhase("COMPLETION", "Finalizare si recompensa");
        quest.setTriggerProbability(0.06);
        scenarioTemplates.put(ScenarioType.QUEST, quest);
        
        // GOSSIP_SPREAD - Raspandirea zvonurilor
        ScenarioTemplate gossip = new ScenarioTemplate(ScenarioType.GOSSIP_SPREAD);
        gossip.addRole("ORIGINATOR", "Sursa zvonului");
        gossip.addRole("SPREADER", "Cel care raspandeste");
        gossip.addRole("SUBJECT", "Subiectul zvonului");
        gossip.addRole("SKEPTIC", "Cel care nu crede");
        gossip.addPhase("ORIGIN", "Nasterea zvonului");
        gossip.addPhase("SPREAD", "Raspandirea");
        gossip.addPhase("DISCOVERY", "Subiectul afla");
        gossip.addPhase("CONFRONTATION", "Confruntare");
        gossip.setTriggerProbability(0.07);
        scenarioTemplates.put(ScenarioType.GOSSIP_SPREAD, gossip);
    }

    /**
     * Evalueaza daca ar trebui sa inceapa un scenariu nou
     */
    public void evaluateScenarioTriggers(List<AINPC> npcs, List<Player> nearbyPlayers) {
        if (npcs.size() < 2) return;
        
        Random random = new Random();
        
        for (ScenarioTemplate template : scenarioTemplates.values()) {
            // Verifica daca avem deja un scenariu activ de acest tip
            boolean hasActiveOfType = activeScenarios.values().stream()
                .anyMatch(s -> s.getType() == template.getType());
            
            if (hasActiveOfType) continue;
            
            // Verifica probabilitatea
            if (random.nextDouble() < template.getTriggerProbability()) {
                // Verifica conditiile
                if (canTriggerScenario(template, npcs, nearbyPlayers)) {
                    startScenario(template, npcs, nearbyPlayers);
                }
            }
        }
    }

    /**
     * Verifica daca un scenariu poate fi declansat
     */
    private boolean canTriggerScenario(ScenarioTemplate template, List<AINPC> npcs, List<Player> players) {
        int requiredNPCs = template.getRoles().size();
        
        switch (template.getType()) {
            case QUEST:
                // Quest necesita cel putin un jucator
                return !players.isEmpty() && npcs.size() >= 1;
            case ROMANCE:
                // Romantismul necesita NPC-uri de sexe diferite
                boolean hasMale = npcs.stream().anyMatch(n -> "male".equals(n.getGender()));
                boolean hasFemale = npcs.stream().anyMatch(n -> "female".equals(n.getGender()));
                return hasMale && hasFemale;
            case CONFLICT:
                // Conflictul necesita NPC-uri cu personalitati incompatibile
                return npcs.size() >= 2 && hasConflictingPersonalities(npcs);
            default:
                return npcs.size() >= Math.min(requiredNPCs, 2);
        }
    }

    /**
     * Verifica daca exista personalitati conflictuale
     */
    private boolean hasConflictingPersonalities(List<AINPC> npcs) {
        for (AINPC npc1 : npcs) {
            for (AINPC npc2 : npcs) {
                if (npc1 == npc2) continue;
                
                NPCPersonality p1 = npc1.getPersonality();
                NPCPersonality p2 = npc2.getPersonality();
                
                // Verifica daca unul e foarte agreabil si altul nu
                if (Math.abs(p1.getAgreeableness() - p2.getAgreeableness()) > 0.5) {
                    return true;
                }
                
                // Verifica daca ambii au neuroticism ridicat
                if (p1.getNeuroticism() > 0.7 && p2.getNeuroticism() > 0.7) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Porneste un scenariu nou
     */
    private void startScenario(ScenarioTemplate template, List<AINPC> npcs, List<Player> players) {
        UUID scenarioId = UUID.randomUUID();
        ActiveScenario scenario = new ActiveScenario(scenarioId, template.getType());
        
        // Asigneaza roluri
        assignRoles(scenario, template, npcs, players);
        
        // Seteaza faza initiala
        List<String> phases = template.getPhases();
        if (!phases.isEmpty()) {
            scenario.setCurrentPhase(phases.get(0));
        }
        
        activeScenarios.put(scenarioId, scenario);
        
        plugin.getLogger().info("Scenariu nou pornit: " + template.getType() + 
                               " (ID: " + scenarioId.toString().substring(0, 8) + ")");
        
        // Notifica participantii
        notifyParticipants(scenario);
    }

    /**
     * Asigneaza roluri NPC-urilor si jucatorilor
     */
    private void assignRoles(ActiveScenario scenario, ScenarioTemplate template, 
                            List<AINPC> npcs, List<Player> players) {
        List<String> roles = new ArrayList<>(template.getRoles().keySet());
        Random random = new Random();
        
        int roleIndex = 0;
        
        // Asigneaza rolul principal jucatorului daca e QUEST
        if (template.getType() == ScenarioType.QUEST && !players.isEmpty()) {
            scenario.assignPlayerRole(players.get(0).getUniqueId(), "HERO");
            roles.remove("HERO");
        }
        
        // Asigneaza rolurile ramase NPC-urilor
        for (AINPC npc : npcs) {
            if (roleIndex >= roles.size()) break;
            
            String role = roles.get(roleIndex);
            
            // Verifica daca NPC-ul e potrivit pentru rol
            if (isNPCSuitableForRole(npc, role)) {
                scenario.assignNPCRole(npc.getUuid(), role);
                roleIndex++;
            }
        }
        
        // Asigneaza roluri random pentru cele ramase
        while (roleIndex < roles.size() && !npcs.isEmpty()) {
            AINPC randomNPC = npcs.get(random.nextInt(npcs.size()));
            if (!scenario.hasNPCRole(randomNPC.getUuid())) {
                scenario.assignNPCRole(randomNPC.getUuid(), roles.get(roleIndex));
                roleIndex++;
            }
        }
    }

    /**
     * Verifica daca un NPC e potrivit pentru un rol
     */
    private boolean isNPCSuitableForRole(AINPC npc, String role) {
        NPCPersonality p = npc.getPersonality();
        NPCEmotions e = npc.getEmotions();
        
        return switch (role) {
            case "THIEF" -> p.getConscientiousness() < 0.4 && p.getAgreeableness() < 0.5;
            case "GUARD" -> "guard".equals(npc.getOccupation()) || "soldier".equals(npc.getOccupation());
            case "AGGRESSOR" -> p.getAgreeableness() < 0.4 || e.getAnger() > 0.5;
            case "MEDIATOR" -> p.getAgreeableness() > 0.6 && p.getExtraversion() > 0.5;
            case "COWARD" -> p.getNeuroticism() > 0.6 || e.getFear() > 0.5;
            case "LEADER" -> p.getExtraversion() > 0.6 && p.getConscientiousness() > 0.5;
            case "HOST" -> p.getExtraversion() > 0.5 && p.getAgreeableness() > 0.5;
            case "SUITOR" -> p.getExtraversion() > 0.5;
            case "ORIGINATOR" -> p.getOpenness() > 0.6; // Creativi creaza zvonuri
            default -> true;
        };
    }

    /**
     * Notifica participantii la scenariu
     */
    private void notifyParticipants(ActiveScenario scenario) {
        // Notifica NPC-urile despre rolurile lor (actualizeaza starea)
        for (Map.Entry<UUID, String> entry : scenario.getNpcRoles().entrySet()) {
            AINPC npc = plugin.getNpcManager().getNPCByUUID(entry.getKey());
            if (npc != null) {
                // Actualizeaza starea emotionala bazat pe rol
                adjustEmotionsForRole(npc, entry.getValue(), scenario.getType());
            }
        }
        
        // Notifica jucatorii
        for (Map.Entry<UUID, String> entry : scenario.getPlayerRoles().entrySet()) {
            Player player = Bukkit.getPlayer(entry.getKey());
            if (player != null) {
                // Trimite un mesaj subtil despre ce se intampla
                sendScenarioHint(player, scenario);
            }
        }
    }

    /**
     * Ajusteaza emotiile NPC-ului bazat pe rol
     */
    private void adjustEmotionsForRole(AINPC npc, String role, ScenarioType type) {
        NPCEmotions emotions = npc.getEmotions();
        
        switch (role) {
            case "THIEF":
                emotions.adjustFear(0.3);
                break;
            case "VICTIM":
                emotions.adjustAnger(0.4);
                emotions.adjustSadness(0.3);
                break;
            case "AGGRESSOR":
                emotions.adjustAnger(0.5);
                break;
            case "HOST":
                emotions.adjustHappiness(0.4);
                break;
            case "SUITOR":
                emotions.adjustHappiness(0.3);
                emotions.adjustFear(0.2); // Anxietate
                break;
        }
    }

    /**
     * Trimite un indiciu jucatorului despre scenariu
     */
    private void sendScenarioHint(Player player, ActiveScenario scenario) {
        String hint = switch (scenario.getType()) {
            case QUEST -> "Simti ca cineva are nevoie de ajutorul tau...";
            case CONFLICT -> "Tensiunea din aer e palpabila...";
            case CELEBRATION -> "Se aude muzica si rasete in apropiere!";
            case EMERGENCY -> "Ceva nu e in regula...";
            case ROMANCE -> "Aerul e plin de emotie...";
            default -> "";
        };
        
        if (!hint.isEmpty()) {
            player.sendMessage("§7§o" + hint);
        }
    }

    /**
     * Avanseaza un scenariu la urmatoarea faza
     */
    public void advanceScenario(UUID scenarioId) {
        ActiveScenario scenario = activeScenarios.get(scenarioId);
        if (scenario == null) return;
        
        ScenarioTemplate template = scenarioTemplates.get(scenario.getType());
        if (template == null) return;
        
        List<String> phases = template.getPhases();
        int currentIndex = phases.indexOf(scenario.getCurrentPhase());
        
        if (currentIndex < phases.size() - 1) {
            scenario.setCurrentPhase(phases.get(currentIndex + 1));
            plugin.debug("Scenariu " + scenarioId.toString().substring(0, 8) + 
                        " avansat la faza: " + scenario.getCurrentPhase());
        } else {
            // Scenariul s-a terminat
            endScenario(scenarioId);
        }
    }

    /**
     * Termina un scenariu
     */
    public void endScenario(UUID scenarioId) {
        ActiveScenario scenario = activeScenarios.remove(scenarioId);
        if (scenario == null) return;
        
        // Creeaza amintiri pentru participanti
        createScenarioMemories(scenario);
        
        plugin.getLogger().info("Scenariu terminat: " + scenario.getType() + 
                               " (ID: " + scenarioId.toString().substring(0, 8) + ")");
    }

    /**
     * Creeaza amintiri despre scenariu pentru participanti
     */
    private void createScenarioMemories(ActiveScenario scenario) {
        String memoryDescription = "A participat la: " + scenario.getType().getDisplayName();
        
        for (UUID npcId : scenario.getNpcRoles().keySet()) {
            // Salvam memoria in MemoryManager
            plugin.getMemoryManager().addScenarioMemory(npcId, scenario.getType().name(), 
                                                        scenario.getNpcRoles().get(npcId));
        }
    }

    /**
     * Obtine scenariile active
     */
    public Map<UUID, ActiveScenario> getActiveScenarios() {
        return new HashMap<>(activeScenarios);
    }

    /**
     * Obtine scenariul in care participa un NPC
     */
    public ActiveScenario getNPCScenario(UUID npcId) {
        for (ActiveScenario scenario : activeScenarios.values()) {
            if (scenario.hasNPCRole(npcId)) {
                return scenario;
            }
        }
        return null;
    }

    /**
     * Tipuri de scenarii
     */
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
    }

    /**
     * Template pentru scenarii
     */
    public static class ScenarioTemplate {
        private final ScenarioType type;
        private final Map<String, String> roles;
        private final List<String> phases;
        private double triggerProbability;

        public ScenarioTemplate(ScenarioType type) {
            this.type = type;
            this.roles = new LinkedHashMap<>();
            this.phases = new ArrayList<>();
            this.triggerProbability = 0.05;
        }

        public void addRole(String roleId, String description) {
            roles.put(roleId, description);
        }

        public void addPhase(String phaseId, String description) {
            phases.add(phaseId);
        }

        public void setTriggerProbability(double probability) {
            this.triggerProbability = probability;
        }

        public ScenarioType getType() { return type; }
        public Map<String, String> getRoles() { return roles; }
        public List<String> getPhases() { return phases; }
        public double getTriggerProbability() { return triggerProbability; }
    }

    /**
     * Scenariu activ
     */
    public static class ActiveScenario {
        private final UUID id;
        private final ScenarioType type;
        private final Map<UUID, String> npcRoles;
        private final Map<UUID, String> playerRoles;
        private String currentPhase;
        private long startTime;

        public ActiveScenario(UUID id, ScenarioType type) {
            this.id = id;
            this.type = type;
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
        public Map<UUID, String> getNpcRoles() { return npcRoles; }
        public Map<UUID, String> getPlayerRoles() { return playerRoles; }
        public String getCurrentPhase() { return currentPhase; }
        public void setCurrentPhase(String phase) { this.currentPhase = phase; }
        public long getStartTime() { return startTime; }
    }
}
