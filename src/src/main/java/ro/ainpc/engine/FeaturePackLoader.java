package ro.ainpc.engine;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import ro.ainpc.AINPCPlugin;
import ro.ainpc.addons.AddonDescriptor;
import ro.ainpc.addons.AddonType;
import ro.ainpc.npc.NPCAction;
import ro.ainpc.platform.RuntimeMode;
import ro.ainpc.topology.TopologyCategory;
import ro.ainpc.topology.TopologyConsensus;

import java.io.File;
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

    // Topologii din toate pachetele
    private final Map<String, TopologyDefinition> allTopologies;
    private final Map<TopologyCategory, List<TopologyDefinition>> topologiesByCategory;
    private final Map<String, ScenarioDefinition> allScenarios;

    public FeaturePackLoader(AINPCPlugin plugin) {
        this.plugin = plugin;
        this.loadedPacks = new LinkedHashMap<>();
        this.allTraits = new HashMap<>();
        this.allProfessions = new HashMap<>();
        this.allDialogues = new HashMap<>();
        this.allTopologies = new HashMap<>();
        this.topologiesByCategory = new EnumMap<>(TopologyCategory.class);
        this.allScenarios = new LinkedHashMap<>();
    }

    /**
     * Incarca toate feature packs din folderul packs/
     */
    public void loadAllPacks() {
        loadedPacks.clear();
        allTraits.clear();
        allProfessions.clear();
        allDialogues.clear();
        allTopologies.clear();
        topologiesByCategory.clear();
        allScenarios.clear();
        if (plugin.getPlatform() != null) {
            plugin.getPlatform().getAddonRegistry().removeByOrigin(AddonDescriptor.ORIGIN_FEATURE_PACK);
        }

        // Creeaza folderul daca nu exista
        File packsFolder = new File(plugin.getDataFolder(), "packs");
        if (!packsFolder.exists()) {
            packsFolder.mkdirs();
        }

        // Salveaza pachetele default lipsa inclusiv la upgrade-uri.
        saveDefaultPacks();
        
        // Incarca fiecare fisier YAML
        File[] files = packsFolder.listFiles((dir, name) -> name.endsWith(".yml") || name.endsWith(".yaml"));
        if (files != null) {
            Arrays.sort(files, Comparator.comparing(File::getName, String.CASE_INSENSITIVE_ORDER));
            for (File file : files) {
                loadPack(file);
            }
        }
        
        // Incarca pachetul medieval default din resurse daca nu exista niciunul
        if (loadedPacks.isEmpty()) {
            loadDefaultMedievalPack();
        }

        if (allTopologies.isEmpty()) {
            loadDefaultTopologies();
        }
        
        plugin.getLogger().info("Feature Packs incarcate: " + loadedPacks.size());
        plugin.getLogger().info("Traits disponibile: " + allTraits.size());
        plugin.getLogger().info("Profesii disponibile: " + allProfessions.size());
        plugin.getLogger().info("Topologii disponibile: " + allTopologies.size());
    }

    /**
     * Salveaza pachetele default in folderul packs/
     */
    private void saveDefaultPacks() {
        saveResource("packs/medieval.yml");
        saveResource("packs/medieval_quest.yml");
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

            // Incarca topologii
            ConfigurationSection topologiesSection = config.getConfigurationSection("topologies");
            if (topologiesSection != null) {
                loadTopologies(pack, topologiesSection);
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

            registerPackDescriptor(pack, config.getConfigurationSection("addon"));
            
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

            profession.setAliases(profSection.getStringList("aliases"));
            profession.setSuggestedTraits(profSection.getStringList("suggested_traits"));
            
            // Incarca dialoguri specifice
            List<String> dialogues = profSection.getStringList("dialogues");
            profession.setDialogues(dialogues);
            
            pack.addProfession(profession);
            allProfessions.put(professionId, profession);
        }
    }

    /**
     * Incarca topologii dintr-o sectiune YAML.
     */
    private void loadTopologies(FeaturePack pack, ConfigurationSection section) {
        for (String topologyId : section.getKeys(false)) {
            ConfigurationSection topologySection = section.getConfigurationSection(topologyId);
            if (topologySection == null) continue;

            String name = topologySection.getString("name", topologyId);
            TopologyCategory category = TopologyCategory.fromId(
                topologySection.getString("category", topologyId)
            );
            String description = topologySection.getString("description", category.getDescription());

            TopologyDefinition topology = new TopologyDefinition(
                pack.getId(),
                topologyId,
                name,
                category,
                description
            );
            topology.setBiomes(topologySection.getStringList("biomes"));
            topology.setDialogueHints(topologySection.getStringList("dialogue_hints"));
            topology.setSuggestedTraits(topologySection.getStringList("suggested_traits"));
            registerTopology(pack, topology);
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
        for (String scenarioId : section.getKeys(false)) {
            ConfigurationSection scenarioSection = section.getConfigurationSection(scenarioId);
            if (scenarioSection == null) {
                continue;
            }

            ScenarioEngine.ScenarioType baseType = ScenarioEngine.ScenarioType.fromId(
                scenarioSection.getString("base_type", scenarioSection.getString("type", "QUEST"))
            );

            ScenarioDefinition scenario = new ScenarioDefinition(
                pack.getId(),
                scenarioId,
                scenarioSection.getString("name", scenarioId),
                scenarioSection.getString("description", ""),
                baseType
            );
            scenario.setTriggerProbability(scenarioSection.getDouble("trigger_probability", 0.05));
            scenario.setMinimumNpcCount(Math.max(1, scenarioSection.getInt("min_npcs", 2)));
            scenario.setRequiresPlayer(scenarioSection.getBoolean(
                "requires_player",
                baseType == ScenarioEngine.ScenarioType.QUEST
            ));
            scenario.setReplaceBaseType(scenarioSection.getBoolean("replace_base_type", false));
            scenario.setHint(scenarioSection.getString("hint", ""));
            scenario.setPreferredTopologies(scenarioSection.getStringList("preferred_topologies"));
            scenario.setNarrativeHints(scenarioSection.getStringList("narrative_hints"));

            ConfigurationSection rolesSection = scenarioSection.getConfigurationSection("roles");
            if (rolesSection != null) {
                for (String roleId : rolesSection.getKeys(false)) {
                    ConfigurationSection roleSection = rolesSection.getConfigurationSection(roleId);
                    if (roleSection == null) {
                        continue;
                    }

                    ScenarioRoleDefinition role = new ScenarioRoleDefinition(
                        roleId,
                        roleSection.getString("description", roleId)
                    );
                    role.setPlayerRole(roleSection.getBoolean("player_role", false));
                    role.setOptional(roleSection.getBoolean("optional", false));
                    role.setPreferredProfessions(roleSection.getStringList("preferred_professions"));
                    role.setRequiredTraits(roleSection.getStringList("required_traits"));
                    role.setPreferredTraits(roleSection.getStringList("preferred_traits"));
                    scenario.addRole(role);
                }
            }

            ConfigurationSection phasesSection = scenarioSection.getConfigurationSection("phases");
            if (phasesSection != null) {
                for (String phaseId : phasesSection.getKeys(false)) {
                    scenario.addPhase(phaseId);
                }
            } else {
                scenario.setPhases(scenarioSection.getStringList("phases"));
            }

            pack.addScenario(scenario);
            allScenarios.put(pack.getId() + ":" + scenarioId, scenario);
        }

        if (!pack.getScenarios().isEmpty()) {
            pack.markHasScenarioDefinitions();
        }
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

        TopologyDefinition village = new TopologyDefinition(
            medieval.getId(),
            "village_center",
            "Sat deschis",
            TopologyCategory.PLAINS,
            "Asezare rurala activa, cu munca, schimb social si comunitate."
        );
        village.setBiomes(Arrays.asList("PLAINS", "MEADOW", "SUNFLOWER_PLAINS"));
        village.setDialogueHints(Arrays.asList(
            "vorbeste despre recolta, vecini si targ",
            "pastreaza un ton practic si comunitar"
        ));
        village.setSuggestedTraits(Arrays.asList("friendly", "hardworking"));
        registerTopology(medieval, village);

        TopologyDefinition forestEdge = new TopologyDefinition(
            medieval.getId(),
            "forest_edge",
            "Margine de padure",
            TopologyCategory.FOREST,
            "Zona de tranzitie intre sat si salbaticie, buna pentru avertismente si zvonuri."
        );
        forestEdge.setBiomes(Arrays.asList("FOREST", "BIRCH_FOREST", "DARK_FOREST"));
        forestEdge.setDialogueHints(Arrays.asList(
            "accent pe prudenta, vanatoare si drumuri periculoase",
            "discuta despre creaturi si lemnari"
        ));
        forestEdge.setSuggestedTraits(Arrays.asList("brave", "suspicious"));
        registerTopology(medieval, forestEdge);

        registerPackDescriptor(medieval, null);
        loadedPacks.put("medieval", medieval);
    }

    private void loadDefaultTopologies() {
        FeaturePack pack = loadedPacks.computeIfAbsent(
            "core_topology",
            ignored -> new FeaturePack("core_topology", "Core Topology", "Fallback intern pentru topologii")
        );

        TopologyDefinition interior = new TopologyDefinition(
            pack.getId(),
            "interior_default",
            "Interior de baza",
            TopologyCategory.INTERIOR,
            "Spatiu inchis, sigur si mai controlat decat exteriorul."
        );
        interior.setDialogueHints(Arrays.asList(
            "accent pe siguranta, adapost si organizare",
            "replici mai calme si apropiate"
        ));
        registerTopology(pack, interior);

        TopologyDefinition plains = new TopologyDefinition(
            pack.getId(),
            "plains_default",
            "Camp de baza",
            TopologyCategory.PLAINS,
            "Zona deschisa, buna pentru asezari, agricultura si intalniri sociale."
        );
        plains.setDialogueHints(Arrays.asList(
            "discuta despre drumuri, campuri si comunitate",
            "ton practic, deschis si local"
        ));
        registerTopology(pack, plains);

        TopologyDefinition forest = new TopologyDefinition(
            pack.getId(),
            "forest_default",
            "Padure de baza",
            TopologyCategory.FOREST,
            "Zona impadurita care sugereaza prudenta, resurse si mister."
        );
        forest.setDialogueHints(Arrays.asList(
            "discuta despre lemn, creaturi si poteci ascunse"
        ));
        registerTopology(pack, forest);
        registerPackDescriptor(pack, null);
    }

    private void registerTopology(FeaturePack pack, TopologyDefinition topology) {
        String key = pack.getId() + ":" + topology.getId();
        allTopologies.put(key, topology);
        topologiesByCategory.computeIfAbsent(topology.getCategory(), ignored -> new ArrayList<>()).add(topology);
        pack.addTopology(topology);
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
     * Obtine o topologie dupa ID simplu sau calificat.
     */
    public TopologyDefinition getTopology(String id) {
        if (id == null || id.isBlank()) {
            return null;
        }

        TopologyDefinition direct = allTopologies.get(id);
        if (direct != null) {
            return direct;
        }

        for (TopologyDefinition topology : allTopologies.values()) {
            if (topology.getId().equalsIgnoreCase(id)) {
                return topology;
            }
        }

        return null;
    }

    /**
     * Obtine topologiile asociate unei categorii.
     */
    public List<TopologyDefinition> getTopologies(TopologyCategory category) {
        if (category == null) {
            return Collections.emptyList();
        }

        return Collections.unmodifiableList(
            topologiesByCategory.getOrDefault(category, Collections.emptyList())
        );
    }

    /**
     * Construieste un consens din toate feature pack-urile pentru o anumita topologie.
     */
    public TopologyConsensus buildTopologyConsensus(TopologyCategory category) {
        List<TopologyDefinition> definitions = topologiesByCategory.get(category);
        if (definitions == null || definitions.isEmpty()) {
            return null;
        }

        LinkedHashSet<String> descriptions = new LinkedHashSet<>();
        LinkedHashSet<String> biomes = new LinkedHashSet<>();
        LinkedHashSet<String> dialogueHints = new LinkedHashSet<>();
        LinkedHashSet<String> suggestedTraits = new LinkedHashSet<>();
        LinkedHashSet<String> sourcePacks = new LinkedHashSet<>();

        for (TopologyDefinition definition : definitions) {
            if (definition.getDescription() != null && !definition.getDescription().isBlank()) {
                descriptions.add(definition.getDescription());
            }
            biomes.addAll(definition.getBiomes());
            dialogueHints.addAll(definition.getDialogueHints());
            suggestedTraits.addAll(definition.getSuggestedTraits());
            sourcePacks.add(definition.getPackId());
        }

        return new TopologyConsensus(
            category,
            new ArrayList<>(descriptions),
            new ArrayList<>(biomes),
            new ArrayList<>(dialogueHints),
            new ArrayList<>(suggestedTraits),
            new ArrayList<>(sourcePacks)
        );
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

    public Collection<TopologyDefinition> getAllTopologies() {
        return allTopologies.values();
    }

    public Collection<ScenarioDefinition> getAllScenarios() {
        return Collections.unmodifiableCollection(allScenarios.values());
    }

    public Collection<FeaturePack> getLoadedPacks() {
        return Collections.unmodifiableCollection(loadedPacks.values());
    }

    public FeaturePack getPrimaryScenarioPack() {
        return loadedPacks.values().stream()
            .filter(pack -> pack.getAddonDescriptor() != null)
            .filter(pack -> pack.getAddonDescriptor().getType() == AddonType.SCENARIO)
            .filter(pack -> pack.getAddonDescriptor().isPrimaryScenario())
            .filter(FeaturePack::hasScenarioDefinitions)
            .findFirst()
            .orElseGet(() -> loadedPacks.values().stream()
                .filter(FeaturePack::hasScenarioDefinitions)
                .findFirst()
                .orElse(null));
    }

    public ProfessionDefinition findProfessionDefinition(String reference) {
        if (reference == null || reference.isBlank()) {
            return null;
        }

        ProfessionDefinition direct = allProfessions.get(reference);
        if (direct != null) {
            return direct;
        }

        return allProfessions.values().stream()
            .filter(profession -> profession.matches(reference))
            .findFirst()
            .orElse(null);
    }

    public ProfessionDefinition findPrimaryScenarioProfession(String occupation) {
        FeaturePack primaryScenario = getPrimaryScenarioPack();
        if (primaryScenario != null) {
            for (ProfessionDefinition profession : primaryScenario.getProfessions()) {
                if (profession.matches(occupation)) {
                    return profession;
                }
            }
        }

        return findProfessionDefinition(occupation);
    }

    public boolean matchesProfession(String occupation, String professionReference) {
        if (occupation == null || occupation.isBlank() || professionReference == null || professionReference.isBlank()) {
            return false;
        }

        ProfessionDefinition target = findProfessionDefinition(professionReference);
        if (target == null) {
            return normalizeText(occupation).equals(normalizeText(professionReference));
        }

        return target.matches(occupation);
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

    private void registerPackDescriptor(FeaturePack pack, ConfigurationSection addonSection) {
        AddonType addonType = addonSection != null
            ? AddonType.fromId(addonSection.getString("type"))
            : inferAddonType(pack);

        if (addonType == AddonType.SCENARIO && !pack.hasScenarioDefinitions()) {
            addonType = AddonType.FEATURE;
        }

        boolean primaryScenario = addonSection != null
            ? addonSection.getBoolean("primary_scenario", false)
            : addonType == AddonType.SCENARIO && "medieval".equalsIgnoreCase(pack.getId());
        primaryScenario = primaryScenario && addonType == AddonType.SCENARIO && pack.hasScenarioDefinitions();

        List<String> capabilities = addonSection != null && !addonSection.getStringList("capabilities").isEmpty()
            ? addonSection.getStringList("capabilities")
            : detectCapabilities(pack);
        capabilities = sanitizeCapabilities(capabilities, pack);
        List<String> dependencies = addonSection != null
            ? addonSection.getStringList("dependencies")
            : Collections.emptyList();
        Set<RuntimeMode> runtimeModes = addonSection != null
            ? RuntimeMode.fromIds(addonSection.getStringList("runtime_modes"))
            : EnumSet.allOf(RuntimeMode.class);

        AddonDescriptor descriptor = new AddonDescriptor(
            AddonDescriptor.ORIGIN_FEATURE_PACK,
            pack.getId(),
            pack.getName(),
            addonSection != null ? addonSection.getString("version", "1.0.0") : "1.0.0",
            pack.getDescription(),
            addonType,
            primaryScenario,
            runtimeModes,
            capabilities,
            dependencies
        );

        pack.setAddonDescriptor(descriptor);
        if (plugin.getPlatform() != null) {
            plugin.getPlatform().getAddonRegistry().registerDescriptor(descriptor);
        }
    }

    private AddonType inferAddonType(FeaturePack pack) {
        String id = pack.getId().toLowerCase(Locale.ROOT);
        if (id.startsWith("core_")) {
            return AddonType.FEATURE;
        }
        if (id.contains("vault") || id.contains("worldedit") || id.contains("integration")) {
            return AddonType.INTEGRATION;
        }
        if (!pack.getProfessions().isEmpty() || !pack.getTopologies().isEmpty() || pack.hasScenarioDefinitions()) {
            return AddonType.SCENARIO;
        }
        return AddonType.FEATURE;
    }

    private List<String> detectCapabilities(FeaturePack pack) {
        List<String> capabilities = new ArrayList<>();
        if (!pack.getTraits().isEmpty()) {
            capabilities.add("traits");
        }
        if (!pack.getProfessions().isEmpty()) {
            capabilities.add("professions");
        }
        if (!pack.getDialogues().isEmpty()) {
            capabilities.add("dialogues");
        }
        if (!pack.getTopologies().isEmpty()) {
            capabilities.add("topology");
        }
        if (pack.hasScenarioDefinitions()) {
            capabilities.add("scenarios");
        }
        return capabilities;
    }

    private List<String> sanitizeCapabilities(List<String> configuredCapabilities, FeaturePack pack) {
        LinkedHashSet<String> sanitized = new LinkedHashSet<>(configuredCapabilities != null
            ? configuredCapabilities
            : Collections.emptyList());

        if (pack.hasScenarioDefinitions()) {
            sanitized.add("scenarios");
        } else {
            sanitized.remove("scenarios");
        }

        return new ArrayList<>(sanitized);
    }

    private static String normalizeText(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
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
        private final List<TopologyDefinition> topologies;
        private final List<ScenarioDefinition> scenarios;
        private final Map<String, List<String>> dialogues;
        private boolean hasScenarioDefinitions;
        private AddonDescriptor addonDescriptor;

        public FeaturePack(String id, String name, String description) {
            this.id = id;
            this.name = name;
            this.description = description;
            this.traits = new ArrayList<>();
            this.professions = new ArrayList<>();
            this.topologies = new ArrayList<>();
            this.scenarios = new ArrayList<>();
            this.dialogues = new HashMap<>();
            this.hasScenarioDefinitions = false;
        }

        public void addTrait(TraitDefinition trait) { traits.add(trait); }
        public void addProfession(ProfessionDefinition profession) { professions.add(profession); }
        public void addTopology(TopologyDefinition topology) { topologies.add(topology); }
        public void addScenario(ScenarioDefinition scenario) { scenarios.add(scenario); }
        public void addDialogueCategory(String category, List<String> lines) { dialogues.put(category, lines); }
        public void markHasScenarioDefinitions() { hasScenarioDefinitions = true; }

        public String getId() { return id; }
        public String getName() { return name; }
        public String getDescription() { return description; }
        public List<TraitDefinition> getTraits() { return traits; }
        public List<ProfessionDefinition> getProfessions() { return professions; }
        public List<TopologyDefinition> getTopologies() { return topologies; }
        public List<ScenarioDefinition> getScenarios() { return scenarios; }
        public Map<String, List<String>> getDialogues() { return dialogues; }
        public boolean hasScenarioDefinitions() { return hasScenarioDefinitions; }
        public AddonDescriptor getAddonDescriptor() { return addonDescriptor; }
        public void setAddonDescriptor(AddonDescriptor addonDescriptor) { this.addonDescriptor = addonDescriptor; }
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
        private List<String> aliases;
        private List<String> suggestedTraits;

        public ProfessionDefinition(String id, String name, String description) {
            this.id = id;
            this.name = name;
            this.description = description;
            this.schedule = new LinkedHashMap<>();
            this.workLocations = new ArrayList<>();
            this.tools = new ArrayList<>();
            this.dialogues = new ArrayList<>();
            this.aliases = new ArrayList<>();
            this.suggestedTraits = new ArrayList<>();
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
        public List<String> getAliases() { return aliases; }
        public void setAliases(List<String> aliases) { this.aliases = aliases != null ? aliases : new ArrayList<>(); }
        public List<String> getSuggestedTraits() { return suggestedTraits; }
        public void setSuggestedTraits(List<String> suggestedTraits) {
            this.suggestedTraits = suggestedTraits != null ? suggestedTraits : new ArrayList<>();
        }

        public boolean matches(String value) {
            String normalized = normalizeText(value);
            if (normalized.isBlank()) {
                return false;
            }

            if (normalizeText(id).equals(normalized) || normalizeText(name).equals(normalized)) {
                return true;
            }

            for (String alias : aliases) {
                if (normalizeText(alias).equals(normalized)) {
                    return true;
                }
            }

            return false;
        }
    }

    /**
     * Definitia unei topologii de mediu.
     */
    public static class TopologyDefinition {
        private final String packId;
        private final String id;
        private final String name;
        private final TopologyCategory category;
        private final String description;
        private List<String> biomes;
        private List<String> dialogueHints;
        private List<String> suggestedTraits;

        public TopologyDefinition(String packId, String id, String name, TopologyCategory category, String description) {
            this.packId = packId;
            this.id = id;
            this.name = name;
            this.category = category;
            this.description = description;
            this.biomes = new ArrayList<>();
            this.dialogueHints = new ArrayList<>();
            this.suggestedTraits = new ArrayList<>();
        }

        public String getPackId() { return packId; }
        public String getId() { return id; }
        public String getName() { return name; }
        public TopologyCategory getCategory() { return category; }
        public String getDescription() { return description; }
        public List<String> getBiomes() { return biomes; }
        public void setBiomes(List<String> biomes) { this.biomes = biomes != null ? biomes : new ArrayList<>(); }
        public List<String> getDialogueHints() { return dialogueHints; }
        public void setDialogueHints(List<String> dialogueHints) { this.dialogueHints = dialogueHints != null ? dialogueHints : new ArrayList<>(); }
        public List<String> getSuggestedTraits() { return suggestedTraits; }
        public void setSuggestedTraits(List<String> suggestedTraits) { this.suggestedTraits = suggestedTraits != null ? suggestedTraits : new ArrayList<>(); }
    }

    public static class ScenarioDefinition {
        private final String packId;
        private final String id;
        private final String name;
        private final String description;
        private final ScenarioEngine.ScenarioType baseType;
        private final Map<String, ScenarioRoleDefinition> roles;
        private List<String> phases;
        private List<String> preferredTopologies;
        private List<String> narrativeHints;
        private double triggerProbability;
        private int minimumNpcCount;
        private boolean requiresPlayer;
        private boolean replaceBaseType;
        private String hint;

        public ScenarioDefinition(String packId,
                                  String id,
                                  String name,
                                  String description,
                                  ScenarioEngine.ScenarioType baseType) {
            this.packId = packId;
            this.id = id;
            this.name = name;
            this.description = description;
            this.baseType = baseType;
            this.roles = new LinkedHashMap<>();
            this.phases = new ArrayList<>();
            this.preferredTopologies = new ArrayList<>();
            this.narrativeHints = new ArrayList<>();
            this.triggerProbability = 0.05;
            this.minimumNpcCount = 2;
            this.requiresPlayer = false;
            this.replaceBaseType = false;
            this.hint = "";
        }

        public void addRole(ScenarioRoleDefinition role) {
            roles.put(role.getId(), role);
        }

        public void addPhase(String phaseId) {
            if (phaseId != null && !phaseId.isBlank()) {
                phases.add(phaseId);
            }
        }

        public String getPackId() { return packId; }
        public String getId() { return id; }
        public String getName() { return name; }
        public String getDescription() { return description; }
        public ScenarioEngine.ScenarioType getBaseType() { return baseType; }
        public Map<String, ScenarioRoleDefinition> getRoles() { return roles; }
        public List<String> getPhases() { return phases; }
        public void setPhases(List<String> phases) { this.phases = phases != null ? phases : new ArrayList<>(); }
        public List<String> getPreferredTopologies() { return preferredTopologies; }
        public void setPreferredTopologies(List<String> preferredTopologies) {
            this.preferredTopologies = preferredTopologies != null ? preferredTopologies : new ArrayList<>();
        }
        public List<String> getNarrativeHints() { return narrativeHints; }
        public void setNarrativeHints(List<String> narrativeHints) {
            this.narrativeHints = narrativeHints != null ? narrativeHints : new ArrayList<>();
        }
        public double getTriggerProbability() { return triggerProbability; }
        public void setTriggerProbability(double triggerProbability) { this.triggerProbability = triggerProbability; }
        public int getMinimumNpcCount() { return minimumNpcCount; }
        public void setMinimumNpcCount(int minimumNpcCount) { this.minimumNpcCount = minimumNpcCount; }
        public boolean isRequiresPlayer() { return requiresPlayer; }
        public void setRequiresPlayer(boolean requiresPlayer) { this.requiresPlayer = requiresPlayer; }
        public boolean isReplaceBaseType() { return replaceBaseType; }
        public void setReplaceBaseType(boolean replaceBaseType) { this.replaceBaseType = replaceBaseType; }
        public String getHint() { return hint; }
        public void setHint(String hint) { this.hint = hint == null ? "" : hint; }
    }

    public static class ScenarioRoleDefinition {
        private final String id;
        private final String description;
        private boolean playerRole;
        private boolean optional;
        private List<String> preferredProfessions;
        private List<String> requiredTraits;
        private List<String> preferredTraits;

        public ScenarioRoleDefinition(String id, String description) {
            this.id = id;
            this.description = description;
            this.playerRole = false;
            this.optional = false;
            this.preferredProfessions = new ArrayList<>();
            this.requiredTraits = new ArrayList<>();
            this.preferredTraits = new ArrayList<>();
        }

        public String getId() { return id; }
        public String getDescription() { return description; }
        public boolean isPlayerRole() { return playerRole; }
        public void setPlayerRole(boolean playerRole) { this.playerRole = playerRole; }
        public boolean isOptional() { return optional; }
        public void setOptional(boolean optional) { this.optional = optional; }
        public List<String> getPreferredProfessions() { return preferredProfessions; }
        public void setPreferredProfessions(List<String> preferredProfessions) {
            this.preferredProfessions = preferredProfessions != null ? preferredProfessions : new ArrayList<>();
        }
        public List<String> getRequiredTraits() { return requiredTraits; }
        public void setRequiredTraits(List<String> requiredTraits) {
            this.requiredTraits = requiredTraits != null ? requiredTraits : new ArrayList<>();
        }
        public List<String> getPreferredTraits() { return preferredTraits; }
        public void setPreferredTraits(List<String> preferredTraits) {
            this.preferredTraits = preferredTraits != null ? preferredTraits : new ArrayList<>();
        }
    }

}
