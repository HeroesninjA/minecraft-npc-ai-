package ro.ainpc;

import org.bukkit.plugin.java.JavaPlugin;
import ro.ainpc.ai.OllamaService;
import ro.ainpc.commands.AINPCCommand;
import ro.ainpc.commands.AINPCTabCompleter;
import ro.ainpc.database.DatabaseManager;
import ro.ainpc.engine.DecisionEngine;
import ro.ainpc.engine.DialogueEngine;
import ro.ainpc.engine.FeaturePackLoader;
import ro.ainpc.engine.ScenarioEngine;
import ro.ainpc.listeners.NPCInteractionListener;
import ro.ainpc.listeners.PlayerJoinListener;
import ro.ainpc.managers.EmotionManager;
import ro.ainpc.managers.FamilyManager;
import ro.ainpc.managers.MemoryManager;
import ro.ainpc.managers.NPCManager;
import ro.ainpc.utils.MessageUtils;

import java.util.logging.Level;

public class AINPCPlugin extends JavaPlugin {

    private static AINPCPlugin instance;
    
    private DatabaseManager databaseManager;
    private NPCManager npcManager;
    private MemoryManager memoryManager;
    private EmotionManager emotionManager;
    private FamilyManager familyManager;
    private OllamaService ollamaService;
    private MessageUtils messageUtils;
    
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
        
        // Initializeaza utilitarele
        messageUtils = new MessageUtils(this);
        
        // Initializeaza baza de date
        getLogger().info("Initializare baza de date...");
        databaseManager = new DatabaseManager(this);
        if (!databaseManager.initialize()) {
            getLogger().severe("Nu s-a putut initializa baza de date! Pluginul se opreste.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        
        // Initializeaza serviciul Ollama
        getLogger().info("Initializare serviciu Ollama...");
        ollamaService = new OllamaService(this);
        
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
        
        // Incarca NPC-urile din baza de date
        npcManager.loadAllNPCs();
        
        // Initializeaza motoarele AI
        getLogger().info("Initializare motoare AI...");
        decisionEngine = new DecisionEngine(this);
        dialogueEngine = new DialogueEngine(this, ollamaService, memoryManager);
        scenarioEngine = new ScenarioEngine(this);
        
        // Inregistreaza comenzile
        getLogger().info("Inregistrare comenzi...");
        AINPCCommand command = new AINPCCommand(this);
        getCommand("ainpc").setExecutor(command);
        getCommand("ainpc").setTabCompleter(new AINPCTabCompleter(this));
        
        // Inregistreaza listenerele
        getLogger().info("Inregistrare listenere...");
        getServer().getPluginManager().registerEvents(new NPCInteractionListener(this), this);
        getServer().getPluginManager().registerEvents(new PlayerJoinListener(this), this);
        
        // Porneste task-urile periodice
        startScheduledTasks();
        
        getLogger().info("========================================");
        getLogger().info("AI NPC Plugin v" + getDescription().getVersion() + " activat!");
        getLogger().info("NPC-uri incarcate: " + npcManager.getNPCCount());
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
        
        getLogger().info("AI NPC Plugin dezactivat!");
    }
    
    private void startScheduledTasks() {
        // Task pentru actualizarea emotiilor (la fiecare minut)
        getServer().getScheduler().runTaskTimerAsynchronously(this, () -> {
            emotionManager.decayEmotions();
        }, 20L * 60, 20L * 60);
        
        // Task pentru curatarea amintirilor vechi (la fiecare ora)
        getServer().getScheduler().runTaskTimerAsynchronously(this, () -> {
            memoryManager.cleanOldMemories();
        }, 20L * 60 * 60, 20L * 60 * 60);
        
        // Task pentru salvarea automata (la fiecare 5 minute)
        getServer().getScheduler().runTaskTimerAsynchronously(this, () -> {
            npcManager.saveAllNPCs();
            if (getConfig().getBoolean("debug")) {
                getLogger().info("[Debug] Salvare automata completata.");
            }
        }, 20L * 60 * 5, 20L * 60 * 5);
    }
    
    public void reload() {
        reloadConfig();
        messageUtils = new MessageUtils(this);
        ollamaService = new OllamaService(this);
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

    public OllamaService getOllamaService() {
        return ollamaService;
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
