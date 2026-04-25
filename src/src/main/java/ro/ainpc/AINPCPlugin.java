package ro.ainpc;

import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.command.PluginCommand;
import ro.ainpc.ai.DialogManager;
import ro.ainpc.ai.OpenAIService;
import ro.ainpc.commands.AINPCCommand;
import ro.ainpc.commands.AINPCTabCompleter;
import ro.ainpc.database.DatabaseManager;
import ro.ainpc.engine.DecisionEngine;
import ro.ainpc.engine.DialogueEngine;
import ro.ainpc.engine.FeaturePackLoader;
import ro.ainpc.engine.ScenarioEngine;
import ro.ainpc.listeners.ListenerRegistry;
import ro.ainpc.managers.ConversationSessionManager;
import ro.ainpc.managers.EmotionManager;
import ro.ainpc.managers.FamilyManager;
import ro.ainpc.managers.MemoryManager;
import ro.ainpc.managers.NPCManager;
import ro.ainpc.platform.AINPCPlatform;
import ro.ainpc.utils.MessageUtils;

import java.util.logging.Level;

public class AINPCPlugin extends JavaPlugin {

    private static AINPCPlugin instance;
    
    private DatabaseManager databaseManager;
    private NPCManager npcManager;
    private MemoryManager memoryManager;
    private EmotionManager emotionManager;
    private FamilyManager familyManager;
    private ConversationSessionManager conversationSessionManager;
    private DialogManager dialogManager;
    private OpenAIService openAIService;
    private MessageUtils messageUtils;
    private AINPCPlatform platform;
    private ListenerRegistry listenerRegistry;
    
    // Motoare AI
    private DecisionEngine decisionEngine;
    private DialogueEngine dialogueEngine;
    private ScenarioEngine scenarioEngine;
    private FeaturePackLoader featurePackLoader;

    @Override
    public void onEnable() {
        instance = this;
        
        // Salveaza configuratia default
        saveDefaultConfig();
        getConfig().options().copyDefaults(true);
        saveConfig();
        
        // Initializeaza utilitarele
        messageUtils = new MessageUtils(this);

        // Initializeaza platforma core
        platform = new AINPCPlatform(this);
        platform.initialize();
        
        // Initializeaza baza de date
        getLogger().info("Initializare baza de date...");
        databaseManager = new DatabaseManager(this);
        if (!databaseManager.initialize()) {
            getLogger().severe("Nu s-a putut initializa baza de date! Pluginul se opreste.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        
        // Initializeaza serviciul OpenAI
        getLogger().info("Initializare serviciu OpenAI...");
        openAIService = new OpenAIService(this);
        openAIService.runDiagnosticsAsync("startup");
        
        // Incarca Feature Packs
        getLogger().info("Incarcare Feature Packs...");
        featurePackLoader = new FeaturePackLoader(this);
        featurePackLoader.loadAllPacks();
        
        // Initializeaza managerii
        getLogger().info("Initializare manageri...");
        memoryManager = new MemoryManager(this);
        emotionManager = new EmotionManager(this);
        familyManager = new FamilyManager(this);
        npcManager = new NPCManager(this);
        dialogManager = new DialogManager(this);
        conversationSessionManager = new ConversationSessionManager(this);
        
        // Incarca NPC-urile din baza de date
        npcManager.loadAllNPCs();
        npcManager.discoverExistingVillagers();
        int backfilledProfiles = npcManager.ensureAllNPCsHaveProfiles();
        getLogger().info("Profiluri NPC verificate. Profiluri create/backfill: " + backfilledProfiles);
        
        // Initializeaza motoarele AI
        getLogger().info("Initializare motoare AI...");
        decisionEngine = new DecisionEngine(this);
        dialogueEngine = new DialogueEngine(this, openAIService);
        scenarioEngine = new ScenarioEngine(this);
        
        // Inregistreaza comenzile
        getLogger().info("Inregistrare comenzi...");
        AINPCCommand command = new AINPCCommand(this);
        PluginCommand ainpcCommand = getCommand("ainpc");
        if (ainpcCommand == null) {
            getLogger().severe("Comanda 'ainpc' nu a fost gasita in plugin.yml. Pluginul se opreste.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        ainpcCommand.setExecutor(command);
        ainpcCommand.setTabCompleter(new AINPCTabCompleter(this));
        
        // Inregistreaza listenerele
        getLogger().info("Inregistrare listenere...");
        listenerRegistry = new ListenerRegistry(this);
        listenerRegistry.registerAll();

        // Mai intai asociem villagerii existenti, apoi restauram doar NPC-urile care chiar lipsesc.
        getServer().getScheduler().runTaskLater(this, () -> {
            npcManager.discoverExistingVillagers();
            npcManager.restoreMissingNPCsInLoadedChunks();
            int ensuredProfiles = npcManager.ensureAllNPCsHaveProfiles();
            if (ensuredProfiles > 0) {
                getLogger().info("Profiluri NPC create dupa restaurarea villagerilor: " + ensuredProfiles);
            }
            npcManager.rebalanceLoadedVillages();
        }, 20L);
        
        // Porneste task-urile periodice
        startScheduledTasks();
        
        getLogger().info("========================================");
        getLogger().info("AI NPC Plugin v" + getPluginMeta().getVersion() + " activat!");
        getLogger().info("NPC-uri incarcate: " + npcManager.getNPCCount());
        getLogger().info("Addonuri inregistrate: " + platform.getAddonRegistry().size());
        getLogger().info("World admin: " + platform.getWorldAdminService().getRegions().size() + " regiuni / "
            + platform.getWorldAdminService().getNodeCount() + " noduri");
        getLogger().info("========================================");
    }

    @Override
    public void onDisable() {
        // Salveaza toate datele NPC-urilor
        if (npcManager != null) {
            getLogger().info("Salvare date NPC-uri...");
            npcManager.saveAllNPCs();
        }
        
        // Inchide conexiunea la baza de date
        if (databaseManager != null) {
            getLogger().info("Inchidere conexiune baza de date...");
            databaseManager.close();
        }

        if (platform != null) {
            platform.shutdown();
        }
        
        getLogger().info("AI NPC Plugin dezactivat!");
    }
    
    private void startScheduledTasks() {
        // Task Bukkit: updateaza nume/particule si trebuie sa ruleze pe thread-ul principal
        getServer().getScheduler().runTaskTimer(this, () -> {
            emotionManager.decayEmotions();
        }, 20L * 60, 20L * 60);
        
        // Task pentru curatarea amintirilor vechi (la fiecare ora)
        getServer().getScheduler().runTaskTimerAsynchronously(this, () -> {
            memoryManager.cleanOldMemories();
        }, 20L * 60 * 60, 20L * 60 * 60);
        
        // Sincronizeaza mai intai starea entitatilor, apoi persista asincron in DB
        getServer().getScheduler().runTaskTimer(this, () -> {
            npcManager.syncAllNPCEntityState();
            getServer().getScheduler().runTaskAsynchronously(this, () -> {
                npcManager.saveAllNPCs(false);
                if (getConfig().getBoolean("debug")) {
                    getLogger().info("[Debug] Salvare automata completata.");
                }
            });
        }, 20L * 60 * 5, 20L * 60 * 5);

        getServer().getScheduler().runTaskTimer(this, () -> {
            npcManager.rebalanceLoadedVillages();
        }, 20L * 45, 20L * 120);
    }
    
    public void reload() {
        reloadConfig();
        getConfig().options().copyDefaults(true);
        saveConfig();
        messageUtils = new MessageUtils(this);
        if (platform != null) {
            platform.reloadFromConfig();
        }
        openAIService = new OpenAIService(this);
        openAIService.runDiagnosticsAsync("reload");
        if (memoryManager != null) {
            dialogueEngine = new DialogueEngine(this, openAIService);
        }
        if (featurePackLoader != null) {
            featurePackLoader.loadAllPacks();
        }
        if (scenarioEngine != null) {
            scenarioEngine.reloadTemplates();
        }
        if (npcManager != null) {
            npcManager.ensureAllNPCsHaveProfiles();
        }
        getLogger().info("Configuratie reincarcata!");
    }

    // Getters
    public static AINPCPlugin getInstance() {
        return instance;
    }

    public DatabaseManager getDatabaseManager() {
        return databaseManager;
    }

    public NPCManager getNpcManager() {
        return npcManager;
    }

    public MemoryManager getMemoryManager() {
        return memoryManager;
    }

    public EmotionManager getEmotionManager() {
        return emotionManager;
    }

    public FamilyManager getFamilyManager() {
        return familyManager;
    }

    public ConversationSessionManager getConversationSessionManager() {
        return conversationSessionManager;
    }

    public AINPCPlatform getPlatform() {
        return platform;
    }

    public DialogManager getDialogManager() {
        return dialogManager;
    }

    public OpenAIService getOpenAIService() {
        return openAIService;
    }

    public MessageUtils getMessageUtils() {
        return messageUtils;
    }

    public DecisionEngine getDecisionEngine() {
        return decisionEngine;
    }

    public DialogueEngine getDialogueEngine() {
        return dialogueEngine;
    }

    public ScenarioEngine getScenarioEngine() {
        return scenarioEngine;
    }

    public FeaturePackLoader getFeaturePackLoader() {
        return featurePackLoader;
    }
    
    public void debug(String message) {
        if (getConfig().getBoolean("debug")) {
            getLogger().log(Level.INFO, "[Debug] " + message);
        }
    }
}
