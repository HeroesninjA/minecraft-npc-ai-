package ro.ainpc.engine;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import ro.ainpc.AINPCPlugin;
import ro.ainpc.npc.NPCAction;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.*;

/**
 * Incarca Feature Packs din fisiere YAML
 * Feature Packs definesc: traits, profesii, dialoguri, scenarii, etc.
 * Caracteristicile NU sunt in Java complex - sunt definite in YAML si Java doar citeste si aplica
 */
public class FeaturePackLoader {

    private final AINPCPlugin plugin;
    
    // Feature packs incarcate
    private final Map<String, FeaturePack> loadedPacks;
    
    // Traits din toate pachetele
    private final Map<String, TraitDefinition> allTraits;
    
    // Profesii din toate pachetele
    private final Map<String, ProfessionDefinition> allProfessions;
    
    // Dialoguri din toate pachetele
    private final Map<String, List<String>> allDialogues;

    public FeaturePackLoader(AINPCPlugin plugin) {
        this.plugin = plugin;
        this.loadedPacks = new HashMap<>();
        this.allTraits = new HashMap<>();
        this.allProfessions = new HashMap<>();
        this.allDialogues = new HashMap<>();
    }

    /**
     * Incarca toate feature packs din folderul packs/
     */
    public void loadAllPacks() {
        // Creeaza folderul daca nu exista
        File packsFolder = new File(plugin.getDataFolder(), "packs");
        if (!packsFolder.exists()) {
            packsFolder.mkdirs();
            
            // Salveaza pachetele default
            saveDefaultPacks();
        }
        
        // Incarca fiecare fisier YAML
        File[] files = packsFolder.listFiles((dir, name) -> name.endsWith(".yml") || name.endsWith(".yaml"));
        if (files != null) {
            for (File file : files) {
                loadPack(file);
            }
        }
        
        // Incarca pachetul medieval default din resurse daca nu exista niciunul
        if (loadedPacks.isEmpty()) {
            loadDefaultMedievalPack();
        }
        
        plugin.getLogger().info("Feature Packs incarcate: " + loadedPacks.size());
        plugin.getLogger().info("Traits disponibile: " + allTraits.size());
        plugin.getLogger().info("Profesii disponibile: " + allProfessions.size());
    }

    /**
     * Salveaza pachetele default in folderul packs/
     */
    private void saveDefaultPacks() {
        saveResource("packs/medieval.yml");
        saveResource("packs/modern.yml");
        saveResource("packs/social.yml");
    }

    /**
     * Salveaza o resursa din plugin
     */
    private void saveResource(String resourcePath) {
        try {
            File outFile = new File(plugin.getDataFolder(), resourcePath);
            if (!outFile.exists()) {
                plugin.saveResource(resourcePath, false);
            }
        } catch (Exception e) {
            plugin.debug("Nu s-a putut salva resursa: " + resourcePath);
        }
    }

    /**
     * Incarca un feature pack dintr-un fisier
     */
    public void loadPack(File file) {
        try {
            YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
            
            String id = config.getString("id", file.getName().replace(".yml", ""));
            String name = config.getString("name", id);
            String description = config.getString("description", "");
            
            FeaturePack pack = new FeaturePack(id, name, description);
            
            // Incarca traits
            ConfigurationSection traitsSection = config.getConfigurationSection("traits");
            if (traitsSection != null) {
                loadTraits(pack, traitsSection);
            }
            
            // Incarca profesii
            ConfigurationSection professionsSection = config.getConfigurationSection("professions");
            if (professionsSection != null) {
                loadProfessions(pack, professionsSection);
            }
            
            // Incarca dialoguri
            ConfigurationSection dialoguesSection = config.getConfigurationSection("dialogues");
            if (dialoguesSection != null) {
                loadDialogues(pack, dialoguesSection);
            }
            
            // Incarca scenarii custom
            ConfigurationSection scenariosSection = config.getConfigurationSection("scenarios");
            if (scenariosSection != null) {
                loadScenarios(pack, scenariosSection);
            }
            
            loadedPacks.put(id, pack);
            plugin.debug("Feature Pack incarcat: " + name + " (" + id + ")");
            
        } catch (Exception e) {
            plugin.getLogger().warning("Eroare la incarcarea feature pack: " + file.getName());
            e.printStackTrace();
        }
    }

    /**
     * Incarca traits dintr-o sectiune YAML
     */
    private void loadTraits(FeaturePack pack, ConfigurationSection section) {
        for (String traitId : section.getKeys(false)) {
            ConfigurationSection traitSection = section.getConfigurationSection(traitId);
            if (traitSection == null) continue;
            
            String name = traitSection.getString("name", traitId);
            String description = traitSection.getString("description", "");
            
            TraitDefinition trait = new TraitDefinition(traitId, name, description);
            
            // Incarca modificatori
            ConfigurationSection modifiersSection = traitSection.getConfigurationSection("modifiers");
            if (modifiersSection != null) {
                ConfigurationSection actionScores = modifiersSection.getConfigurationSection("action_scores");
                if (actionScores != null) {
                    for (String actionName : actionScores.getKeys(false)) {
                        int modifier = actionScores.getInt(actionName);
                        trait.addActionModifier(actionName, modifier);
                    }
                }
                
                ConfigurationSection emotionModifiers = modifiersSection.getConfigurationSection("emotions");
                if (emotionModifiers != null) {
                    for (String emotion : emotionModifiers.getKeys(false)) {
                        double modifier = emotionModifiers.getDouble(emotion);
                        trait.addEmotionModifier(emotion, modifier);
                    }
                }
            }
            
            pack.addTrait(trait);
            allTraits.put(traitId, trait);
        }
    }

    /**
     * Incarca profesii dintr-o sectiune YAML
     */
    private void loadProfessions(FeaturePack pack, ConfigurationSection section) {
        for (String professionId : section.getKeys(false)) {
            ConfigurationSection profSection = section.getConfigurationSection(professionId);
            if (profSection == null) continue;
            
            String name = profSection.getString("name", professionId);
            String description = profSection.getString("description", "");
            
            ProfessionDefinition profession = new ProfessionDefinition(professionId, name, description);
            
            // Incarca schedule
            ConfigurationSection scheduleSection = profSection.getConfigurationSection("schedule");
            if (scheduleSection != null) {
                for (String timeOfDay : scheduleSection.getKeys(false)) {
                    String activity = scheduleSection.getString(timeOfDay);
                    profession.addScheduleEntry(timeOfDay, activity);
                }
            }
            
            // Incarca locatii
            List<String> locations = profSection.getStringList("work_locations");
            profession.setWorkLocations(locations);
            
            // Incarca tools/items
            List<String> tools = profSection.getStringList("tools");
            profession.setTools(tools);
            
            // Incarca dialoguri specifice
            List<String> dialogues = profSection.getStringList("dialogues");
            profession.setDialogues(dialogues);
            
            pack.addProfession(profession);
            allProfessions.put(professionId, profession);
        }
    }

    /**
     * Incarca dialoguri dintr-o sectiune YAML
     */
    private void loadDialogues(FeaturePack pack, ConfigurationSection section) {
        for (String category : section.getKeys(false)) {
            List<String> lines = section.getStringList(category);
            if (!lines.isEmpty()) {
                String key = pack.getId() + ":" + category;
                allDialogues.put(key, lines);
                pack.addDialogueCategory(category, lines);
            }
        }
    }

    /**
     * Incarca scenarii custom dintr-o sectiune YAML
     */
    private void loadScenarios(FeaturePack pack, ConfigurationSection section) {
        // Implementare pentru scenarii custom
        // Acestea pot fi adaugate la ScenarioEngine
    }

    /**
     * Incarca pachetul medieval default din resurse
     */
    private void loadDefaultMedievalPack() {
        FeaturePack medieval = new FeaturePack("medieval", "Medieval", "Pachet pentru setari medievale");
        
        // Traits default
        TraitDefinition greedy = new TraitDefinition("greedy", "Lacom", "Doreste sa acumuleze bogatie");
        greedy.addActionModifier("TRADE", 15);
        greedy.addActionModifier("GIVE_ITEM", -20);
        greedy.addEmotionModifier("happiness", 0.2);
        allTraits.put("greedy", greedy);
        medieval.addTrait(greedy);
        
        TraitDefinition brave = new TraitDefinition("brave", "Curajos", "Nu se teme de pericole");
        brave.addActionModifier("FLEE", -30);
        brave.addActionModifier("ATTACK", 20);
        brave.addActionModifier("DEFEND", 15);
        brave.addEmotionModifier("fear", -0.3);
        allTraits.put("brave", brave);
        medieval.addTrait(brave);
        
        TraitDefinition shy = new TraitDefinition("shy", "Timid", "Evita interactiunile sociale");
        shy.addActionModifier("GREET", -15);
        shy.addActionModifier("SOCIALIZE", -20);
        shy.addActionModifier("TALK", -10);
        shy.addEmotionModifier("fear", 0.2);
        allTraits.put("shy", shy);
        medieval.addTrait(shy);
        
        TraitDefinition friendly = new TraitDefinition("friendly", "Prietenos", "Iubeste sa interactioneze cu altii");
        friendly.addActionModifier("GREET", 20);
        friendly.addActionModifier("SOCIALIZE", 25);
        friendly.addActionModifier("HELP", 15);
        friendly.addEmotionModifier("happiness", 0.3);
        allTraits.put("friendly", friendly);
        medieval.addTrait(friendly);
        
        TraitDefinition lazy = new TraitDefinition("lazy", "Lenes", "Evita munca grea");
        lazy.addActionModifier("START_WORK", -25);
        lazy.addActionModifier("CONTINUE_WORK", -20);
        lazy.addActionModifier("REST", 30);
        allTraits.put("lazy", lazy);
        medieval.addTrait(lazy);
        
        TraitDefinition hardworking = new TraitDefinition("hardworking", "Harnic", "Iubeste sa munceasca");
        hardworking.addActionModifier("START_WORK", 25);
        hardworking.addActionModifier("CONTINUE_WORK", 20);
        hardworking.addActionModifier("REST", -15);
        allTraits.put("hardworking", hardworking);
        medieval.addTrait(hardworking);
        
        // Profesii default
        ProfessionDefinition blacksmith = new ProfessionDefinition("blacksmith", "Fierar", "Lucreaza metalul");
        blacksmith.addScheduleEntry("MORNING", "Deschide atelierul");
        blacksmith.addScheduleEntry("AFTERNOON", "Forjeaza unelte");
        blacksmith.addScheduleEntry("EVENING", "Curata atelierul");
        blacksmith.addScheduleEntry("NIGHT", "Doarme");
        blacksmith.setTools(Arrays.asList("ciocan", "nicovala", "clesti"));
        allProfessions.put("blacksmith", blacksmith);
        medieval.addProfession(blacksmith);
        
        ProfessionDefinition farmer = new ProfessionDefinition("farmer", "Fermier", "Cultiva pamantul");
        farmer.addScheduleEntry("MORNING", "Se trezeste devreme, uda plantele");
        farmer.addScheduleEntry("AFTERNOON", "Lucreaza campul");
        farmer.addScheduleEntry("EVENING", "Hraneste animalele");
        farmer.addScheduleEntry("NIGHT", "Doarme");
        farmer.setTools(Arrays.asList("sapa", "coasa", "galeata"));
        allProfessions.put("farmer", farmer);
        medieval.addProfession(farmer);
        
        ProfessionDefinition guard = new ProfessionDefinition("guard", "Garda", "Pazeste si protejeaza");
        guard.addScheduleEntry("MORNING", "Patruleaza");
        guard.addScheduleEntry("AFTERNOON", "Pazeste poarta");
        guard.addScheduleEntry("EVENING", "Patruleaza");
        guard.addScheduleEntry("NIGHT", "Pazeste sau doarme in ture");
        guard.setTools(Arrays.asList("sabie", "scut", "lancie"));
        allProfessions.put("guard", guard);
        medieval.addProfession(guard);
        
        loadedPacks.put("medieval", medieval);
    }

    /**
     * Obtine un trait dupa ID
     */
    public TraitDefinition getTrait(String id) {
        return allTraits.get(id);
    }

    /**
     * Obtine o profesie dupa ID
     */
    public ProfessionDefinition getProfession(String id) {
        return allProfessions.get(id);
    }

    /**
     * Obtine dialoguri pentru o categorie
     */
    public List<String> getDialogues(String packId, String category) {
        return allDialogues.getOrDefault(packId + ":" + category, Collections.emptyList());
    }

    /**
     * Obtine toate traits disponibile
     */
    public Collection<TraitDefinition> getAllTraits() {
        return allTraits.values();
    }

    /**
     * Obtine toate profesiile disponibile
     */
    public Collection<ProfessionDefinition> getAllProfessions() {
        return allProfessions.values();
    }

    /**
     * Aplica modificatorii unui trait la scorurile de actiune
     */
    public int applyTraitModifiers(String traitId, NPCAction action, int baseScore) {
        TraitDefinition trait = allTraits.get(traitId);
        if (trait == null) return baseScore;
        
        Integer modifier = trait.getActionModifier(action.name());
        return modifier != null ? baseScore + modifier : baseScore;
    }

    /**
     * Aplica modificatorii unui trait la emotii
     */
    public double applyTraitEmotionModifiers(String traitId, String emotion, double baseValue) {
        TraitDefinition trait = allTraits.get(traitId);
        if (trait == null) return baseValue;
        
        Double modifier = trait.getEmotionModifier(emotion);
        return modifier != null ? baseValue + modifier : baseValue;
    }

    // ==================== Inner Classes ====================

    /**
     * Feature Pack - pachet de caracteristici
     */
    public static class FeaturePack {
        private final String id;
        private final String name;
        private final String description;
        private final List<TraitDefinition> traits;
        private final List<ProfessionDefinition> professions;
        private final Map<String, List<String>> dialogues;

        public FeaturePack(String id, String name, String description) {
            this.id = id;
            this.name = name;
            this.description = description;
            this.traits = new ArrayList<>();
            this.professions = new ArrayList<>();
            this.dialogues = new HashMap<>();
        }

        public void addTrait(TraitDefinition trait) { traits.add(trait); }
        public void addProfession(ProfessionDefinition profession) { professions.add(profession); }
        public void addDialogueCategory(String category, List<String> lines) { dialogues.put(category, lines); }

        public String getId() { return id; }
        public String getName() { return name; }
        public String getDescription() { return description; }
        public List<TraitDefinition> getTraits() { return traits; }
        public List<ProfessionDefinition> getProfessions() { return professions; }
        public Map<String, List<String>> getDialogues() { return dialogues; }
    }

    /**
     * Definitia unui trait
     */
    public static class TraitDefinition {
        private final String id;
        private final String name;
        private final String description;
        private final Map<String, Integer> actionModifiers;
        private final Map<String, Double> emotionModifiers;

        public TraitDefinition(String id, String name, String description) {
            this.id = id;
            this.name = name;
            this.description = description;
            this.actionModifiers = new HashMap<>();
            this.emotionModifiers = new HashMap<>();
        }

        public void addActionModifier(String action, int modifier) {
            actionModifiers.put(action, modifier);
        }

        public void addEmotionModifier(String emotion, double modifier) {
            emotionModifiers.put(emotion, modifier);
        }

        public Integer getActionModifier(String action) { return actionModifiers.get(action); }
        public Double getEmotionModifier(String emotion) { return emotionModifiers.get(emotion); }

        public String getId() { return id; }
        public String getName() { return name; }
        public String getDescription() { return description; }
        public Map<String, Integer> getActionModifiers() { return actionModifiers; }
        public Map<String, Double> getEmotionModifiers() { return emotionModifiers; }
    }

    /**
     * Definitia unei profesii
     */
    public static class ProfessionDefinition {
        private final String id;
        private final String name;
        private final String description;
        private final Map<String, String> schedule;
        private List<String> workLocations;
        private List<String> tools;
        private List<String> dialogues;

        public ProfessionDefinition(String id, String name, String description) {
            this.id = id;
            this.name = name;
            this.description = description;
            this.schedule = new LinkedHashMap<>();
            this.workLocations = new ArrayList<>();
            this.tools = new ArrayList<>();
            this.dialogues = new ArrayList<>();
        }

        public void addScheduleEntry(String timeOfDay, String activity) {
            schedule.put(timeOfDay, activity);
        }

        public String getId() { return id; }
        public String getName() { return name; }
        public String getDescription() { return description; }
        public Map<String, String> getSchedule() { return schedule; }
        public List<String> getWorkLocations() { return workLocations; }
        public void setWorkLocations(List<String> workLocations) { this.workLocations = workLocations; }
        public List<String> getTools() { return tools; }
        public void setTools(List<String> tools) { this.tools = tools; }
        public List<String> getDialogues() { return dialogues; }
        public void setDialogues(List<String> dialogues) { this.dialogues = dialogues; }
    }
}
