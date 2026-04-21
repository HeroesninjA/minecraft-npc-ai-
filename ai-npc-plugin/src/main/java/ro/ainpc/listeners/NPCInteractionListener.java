package ro.ainpc.listeners;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import ro.ainpc.AINPCPlugin;
import ro.ainpc.ai.DialogManager;
import ro.ainpc.npc.AINPC;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Listener pentru interactiunile cu NPC-urile
 */
public class NPCInteractionListener implements Listener {

    private final AINPCPlugin plugin;
    private final DialogManager dialogManager;
    
    // Jucatori care vorbesc cu NPC-uri (playerUUID -> npcUUID)
    private final Map<UUID, UUID> activeConversations;
    
    // Timestamp ultima interactiune
    private final Map<UUID, Long> lastInteraction;

    public NPCInteractionListener(AINPCPlugin plugin) {
        this.plugin = plugin;
        this.dialogManager = new DialogManager(plugin);
        this.activeConversations = new ConcurrentHashMap<>();
        this.lastInteraction = new ConcurrentHashMap<>();
    }

    /**
     * Cand un jucator da click dreapta pe un NPC
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerInteractEntity(PlayerInteractEntityEvent event) {
        Entity entity = event.getRightClicked();
        
        // Verifica daca e un Villager (baza pentru NPC-uri)
        if (!(entity instanceof Villager)) return;
        
        AINPC npc = plugin.getNpcManager().getNPCByEntity(entity);
        if (npc == null) return;
        
        // Anuleaza interactiunea default
        event.setCancelled(true);
        
        Player player = event.getPlayer();
        
        // Verifica distanta
        if (!npc.isInRange(player)) {
            plugin.getMessageUtils().sendMessage(player, "too_far");
            return;
        }
        
        // Face NPC-ul sa se uite la jucator
        npc.lookAt(player);
        
        // Activeaza conversatia
        startConversation(player, npc);
    }

    /**
     * Incepe o conversatie cu un NPC
     */
    private void startConversation(Player player, AINPC npc) {
        UUID playerUUID = player.getUniqueId();
        UUID npcUUID = npc.getUuid();
        
        // Verifica daca e prima intalnire
        boolean firstMeeting = !plugin.getMemoryManager().hasMemoriesOf(npc, player);
        
        if (firstMeeting) {
            plugin.getMemoryManager().createFirstMeetingMemory(npc, player);
        }
        
        // Seteaza conversatia activa
        activeConversations.put(playerUUID, npcUUID);
        lastInteraction.put(playerUUID, System.currentTimeMillis());
        
        // Mesaj de inceput
        String greeting;
        if (firstMeeting) {
            greeting = getFirstMeetingGreeting(npc);
        } else {
            greeting = getReturningGreeting(npc, player);
        }
        
        plugin.getMessageUtils().sendNPCMessage(player, npc.getName(), greeting);
        plugin.getMessageUtils().send(player, "&7&o(Scrie in chat pentru a vorbi cu " + npc.getName() + ". Scrie 'pa' pentru a termina conversatia.)");
        
        // Aplica efectul emotional de intalnire
        plugin.getEmotionManager().processEvent(npc, "player_approach", 1.0);
    }

    /**
     * Proceseaza mesajele din chat pentru conversatii active
     */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onAsyncChat(AsyncChatEvent event) {
        Player player = event.getPlayer();
        UUID playerUUID = player.getUniqueId();
        
        // Verifica daca jucatorul are o conversatie activa
        UUID npcUUID = activeConversations.get(playerUUID);
        if (npcUUID == null) return;
        
        AINPC npc = plugin.getNpcManager().getNPCByUuid(npcUUID);
        if (npc == null) {
            activeConversations.remove(playerUUID);
            return;
        }
        
        // Verifica timeout (5 minute)
        Long lastTime = lastInteraction.get(playerUUID);
        if (lastTime != null && System.currentTimeMillis() - lastTime > 300000) {
            endConversation(player, npc);
            return;
        }
        
        // Obtine mesajul
        String message = PlainTextComponentSerializer.plainText().serialize(event.message());
        
        // Verifica daca jucatorul vrea sa termine conversatia
        if (isGoodbye(message)) {
            event.setCancelled(true);
            endConversation(player, npc);
            return;
        }
        
        // Anuleaza mesajul din chat global (conversatia e privata)
        event.setCancelled(true);
        
        // Afiseaza mesajul jucatorului
        plugin.getMessageUtils().send(player, "&7Tu: &f" + message);
        
        // Actualizeaza timestamp
        lastInteraction.put(playerUUID, System.currentTimeMillis());
        
        // Genereaza raspunsul AI
        plugin.getMessageUtils().send(player, "&8" + npc.getName() + " se gandeste...");
        
        dialogManager.processMessage(npc, player, message).thenAccept(response -> {
            // Trimite raspunsul pe main thread
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                if (response != null && !response.isEmpty()) {
                    plugin.getMessageUtils().sendNPCMessage(player, npc.getName(), response);
                } else {
                    plugin.getMessageUtils().sendMessage(player, "ai_error");
                }
            });
        }).exceptionally(ex -> {
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                plugin.getLogger().warning("Eroare la procesarea mesajului: " + ex.getMessage());
                plugin.getMessageUtils().sendMessage(player, "ai_error");
            });
            return null;
        });
    }

    /**
     * Termina conversatia
     */
    private void endConversation(Player player, AINPC npc) {
        activeConversations.remove(player.getUniqueId());
        lastInteraction.remove(player.getUniqueId());
        
        String goodbye = getGoodbyeMessage(npc);
        plugin.getMessageUtils().sendNPCMessage(player, npc.getName(), goodbye);
        plugin.getMessageUtils().send(player, "&7&o(Conversatia cu " + npc.getName() + " s-a incheiat.)");
        
        // Aplica efectul emotional de despartire
        plugin.getEmotionManager().processEvent(npc, "player_leave", 1.0);
    }

    /**
     * Verifica daca jucatorul a terminat conversatia
     */
    public boolean isInConversation(Player player) {
        return activeConversations.containsKey(player.getUniqueId());
    }

    /**
     * Obtine NPC-ul cu care vorbeste jucatorul
     */
    public AINPC getConversationPartner(Player player) {
        UUID npcUUID = activeConversations.get(player.getUniqueId());
        if (npcUUID == null) return null;
        return plugin.getNpcManager().getNPCByUuid(npcUUID);
    }

    // Metode helper pentru mesaje

    private boolean isGoodbye(String message) {
        String lower = message.toLowerCase().trim();
        return lower.equals("pa") || lower.equals("la revedere") || lower.equals("bye") ||
               lower.equals("adio") || lower.equals("exit") || lower.equals("quit") ||
               lower.startsWith("pa ") || lower.equals("gata");
    }

    private String getFirstMeetingGreeting(AINPC npc) {
        double extraversion = npc.getPersonality().getExtraversion();
        
        if (extraversion > 0.7) {
            return "Buna ziua, straiin! Ce te aduce pe aici? Eu sunt " + npc.getName() + "!";
        } else if (extraversion > 0.4) {
            return "Salut. Nu cred ca ne-am mai intalnit. Eu sunt " + npc.getName() + ".";
        } else {
            return "Hm? Ah... salut. Sunt " + npc.getName() + ".";
        }
    }

    private String getReturningGreeting(AINPC npc, Player player) {
        double affection = 0;
        var relationship = dialogManager.getRelationship(npc, player);
        if (relationship != null) {
            affection = relationship.getAffection();
        }

        if (affection > 0.6) {
            return "Ce bucurie sa te vad din nou, " + player.getName() + "! Cum mai esti?";
        } else if (affection > 0.3) {
            return "A, " + player.getName() + "! Bine ai revenit.";
        } else if (affection > 0) {
            return "Te-ai intors, " + player.getName() + ". Ce mai vrei?";
        } else if (affection > -0.3) {
            return "Tu iar? Ce vrei?";
        } else {
            return "*oftaza* Ce vrei de la mine?";
        }
    }

    private String getGoodbyeMessage(AINPC npc) {
        double extraversion = npc.getPersonality().getExtraversion();
        String emotion = npc.getEmotions().getDominantEmotion();

        if (emotion.equals("happiness")) {
            return "La revedere! A fost placut sa vorbim!";
        } else if (emotion.equals("sadness")) {
            return "Pa... ai grija de tine.";
        } else if (emotion.equals("anger")) {
            return "In sfarsit pleci. La revedere.";
        }

        if (extraversion > 0.6) {
            return "Pa pa! Sa ne vedem curand!";
        } else {
            return "La revedere.";
        }
    }
}
